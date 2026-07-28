package com.example.cashbookbd.inappmessage

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.InAppMessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide queue of pop-up campaigns — the Kotlin twin of the web's
 * InAppMessageHost, and a sibling of [com.example.cashbookbd.notifications.NotificationCenter].
 *
 * Campaigns are fetched once per process (app open == session start on a phone),
 * then played one at a time, highest priority first. Nothing is pushed from the
 * server, so this behaves identically on a flaky connection.
 */
class InAppMessageManager(
    private val repository: InAppMessageRepository,
) {

    data class State(
        val queue: List<InAppMessage> = emptyList(),
        val loadedOnce: Boolean = false,
    ) {
        /** The campaign to display right now, if any. */
        val current: InAppMessage? get() = queue.firstOrNull()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Tracks which campaign already had its impression recorded. */
    private var impressionFor: Long? = null

    /** Fetches once per process; later calls are no-ops. */
    fun ensureLoaded() {
        if (_state.value.loadedOnce) return
        _state.update { it.copy(loadedOnce = true) }

        scope.launch {
            // Send anything the last run could not deliver first, so the cap the
            // server applies below is based on the complete history.
            repository.flush()

            when (val result = repository.sync()) {
                is Resource.Success ->
                    _state.update { it.copy(queue = result.data.sortedByDescending { m -> m.priority }) }
                // A failed sync simply means no pop-ups this launch.
                else -> Unit
            }
        }
    }

    /** Records the impression for the campaign now on screen, exactly once. */
    fun onShown(message: InAppMessage) {
        if (impressionFor == message.id) return
        impressionFor = message.id
        scope.launch { repository.record(message.id, InAppEvent.IMPRESSION) }
    }

    /**
     * The user pressed the primary button. Acknowledgement is what stops a
     * require_ack campaign from coming back, so it is recorded before the click.
     */
    fun onPrimary(message: InAppMessage) {
        scope.launch {
            if (message.requireAck) repository.record(message.id, InAppEvent.ACK)
            message.primaryAction?.takeIf { it.isNotBlank() }?.let {
                repository.record(message.id, InAppEvent.CLICK, it)
            }
        }
        advance()
    }

    fun onSecondary(message: InAppMessage) {
        scope.launch {
            message.secondaryAction?.takeIf { it.isNotBlank() }?.let {
                repository.record(message.id, InAppEvent.CLICK, it)
            }
        }
        advance()
    }

    fun onDismiss(message: InAppMessage) {
        scope.launch { repository.record(message.id, InAppEvent.DISMISS) }
        advance()
    }

    private fun advance() {
        impressionFor = null
        _state.update { it.copy(queue = it.queue.drop(1)) }
    }

    /** Clears the queue on logout so the next user does not inherit it. */
    fun clear() {
        impressionFor = null
        _state.value = State()
    }
}
