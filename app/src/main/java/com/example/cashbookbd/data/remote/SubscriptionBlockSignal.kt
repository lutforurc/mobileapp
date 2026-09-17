package com.example.cashbookbd.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the server said when it shut a lapsed company out.
 *
 * @param message the server's own sentence -- shown as-is, so the wording lives
 *   in one place and a change to it does not need an app release.
 */
data class SubscriptionBlock(
    val message: String,
    val planName: String? = null,
    val endDate: String? = null,
    val gracePeriodEndAt: String? = null,
)

/**
 * A single flag saying "this company's subscription is over", raised by
 * [SubscriptionBlockInterceptor] the first time the API refuses a call with
 * error code 10031.
 *
 * ⚠️ A global object rather than a field on Resource, and that is the whole
 * design decision here. Resource.isUnauthorized is the app's other
 * stop-everything signal, and paying for it meant threading `sessionExpired =
 * it.sessionExpired || result.isUnauthorized` through more than a hundred
 * screens. A lapsed subscription refuses EVERY call on EVERY screen at once, so
 * copying that pattern would mean editing all of them and still missing the
 * ones added next month. One flag, raised in the one place every request
 * already passes through, and read once at the top of the UI.
 *
 * Cleared on login and on logout: the next company to sign in on this phone has
 * nothing to do with the last one's bill.
 */
object SubscriptionBlockSignal {

    private val _blocked = MutableStateFlow<SubscriptionBlock?>(null)

    val blocked: StateFlow<SubscriptionBlock?> = _blocked.asStateFlow()

    fun raise(block: SubscriptionBlock) {
        // First refusal wins. A dashboard fires a dozen calls at once and they
        // all come back 403; re-publishing would restart the screen's animation
        // a dozen times for no new information.
        if (_blocked.value == null) {
            _blocked.value = block
        }
    }

    fun clear() {
        _blocked.value = null
    }
}
