package com.example.cashbookbd.inappmessage

/** How a campaign is presented. Unknown values from the server fall back to MODAL. */
enum class InAppLayout {
    MODAL, BANNER_TOP, BANNER_BOTTOM, CARD, IMAGE_ONLY;

    companion object {
        fun from(value: String?): InAppLayout = when (value?.uppercase()) {
            "BANNER_TOP" -> BANNER_TOP
            "BANNER_BOTTOM" -> BANNER_BOTTOM
            "CARD" -> CARD
            "IMAGE_ONLY" -> IMAGE_ONLY
            else -> MODAL
        }
    }
}

/** What the user did with a campaign; mirrors the backend's event enum. */
enum class InAppEvent {
    IMPRESSION, CLICK, DISMISS, ACK
}

/**
 * A display-ready pop-up campaign. The twin of the web's `InAppMessage` type —
 * the backend hands both clients the same shape, so a campaign composed once
 * looks the same on the phone and in the browser.
 */
data class InAppMessage(
    val id: Long,
    val title: String,
    val body: String?,
    val imageUrl: String?,
    val layout: InAppLayout,
    val primaryLabel: String?,
    val primaryAction: String?,
    val secondaryLabel: String?,
    val secondaryAction: String?,
    /** Hex strings straight from the campaign; null means "use the app theme". */
    val bgColor: String?,
    val textColor: String?,
    val buttonColor: String?,
    val priority: Int,
    /** True when the message may only be closed through its own button. */
    val requireAck: Boolean,
    val shownCount: Int,
)
