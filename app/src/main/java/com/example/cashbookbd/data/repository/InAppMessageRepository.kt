package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.local.DeviceIdManager
import com.example.cashbookbd.data.local.InAppEventQueue
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.InAppMessageEventDto
import com.example.cashbookbd.data.remote.dto.InAppMessageEventRequest
import com.example.cashbookbd.inappmessage.InAppEvent
import com.example.cashbookbd.inappmessage.InAppLayout
import com.example.cashbookbd.inappmessage.InAppMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Loads pop-up campaigns from `GET /in-app-messages/sync` and reports back what
 * the user did with them.
 *
 * Events go through [InAppEventQueue] first, so an impression recorded while the
 * phone is offline still reaches the server on the next flush — the frequency
 * cap depends on it.
 */
class InAppMessageRepository(
    private val api: ApiService,
    private val deviceIdManager: DeviceIdManager,
    private val queue: InAppEventQueue,
    private val appVersion: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun sync(): Resource<List<InAppMessage>> = withContext(ioDispatcher) {
        try {
            val response = api.getInAppMessages(
                platform = "android",
                deviceId = deviceIdManager.getId(),
                appVersion = appVersion,
            )

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }

            val body = response.body()
            if (!response.isSuccessful || body?.success != true) {
                return@withContext Resource.Error(
                    body?.message ?: "Could not load in-app messages.",
                )
            }

            val messages = body.data?.payload?.messages.orEmpty()
                .mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    InAppMessage(
                        id = id,
                        title = dto.title.orEmpty(),
                        body = dto.body,
                        imageUrl = dto.imageUrl,
                        layout = InAppLayout.from(dto.layout),
                        primaryLabel = dto.primaryLabel,
                        primaryAction = dto.primaryAction,
                        secondaryLabel = dto.secondaryLabel,
                        secondaryAction = dto.secondaryAction,
                        bgColor = dto.bgColor,
                        textColor = dto.textColor,
                        buttonColor = dto.buttonColor,
                        priority = dto.priority ?: 0,
                        requireAck = dto.requireAck == true,
                        shownCount = dto.shownCount ?: 0,
                    )
                }

            Resource.Success(messages)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load in-app messages.")
        }
    }

    /** Queues one event, then tries to send everything pending. */
    suspend fun record(
        messageId: Long,
        event: InAppEvent,
        actionTarget: String? = null,
    ) = withContext(ioDispatcher) {
        queue.add(
            InAppMessageEventDto(
                messageId = messageId,
                event = event.name,
                platform = "android",
                deviceId = deviceIdManager.getId(),
                actionTarget = actionTarget,
                occurredAt = isoFormat.format(Date()),
            )
        )
        flush()
    }

    /** Uploads whatever is queued; keeps it on failure so the next call retries. */
    suspend fun flush() = withContext(ioDispatcher) {
        val pending = queue.peek()
        if (pending.isEmpty()) return@withContext

        try {
            val response = api.postInAppMessageEvents(InAppMessageEventRequest(pending))
            if (response.isSuccessful && response.body()?.success == true) {
                queue.remove(pending)
            }
        } catch (_: Exception) {
            // Stay queued; the next event or app launch tries again.
        }
    }
}
