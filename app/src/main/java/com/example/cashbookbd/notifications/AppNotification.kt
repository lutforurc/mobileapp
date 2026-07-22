package com.example.cashbookbd.notifications

/** The accent a notification carries — mirrors the web's `tone`. */
enum class NotificationTone {
    DANGER, WARNING, INFO, SUCCESS;

    companion object {
        fun from(value: String?): NotificationTone = when (value?.lowercase()) {
            "danger" -> DANGER
            "warning" -> WARNING
            "success" -> SUCCESS
            else -> INFO
        }
    }
}

/** One sample row under a notification (label / meta / value), like the web preview. */
data class NotificationPreviewRow(
    val label: String,
    val meta: String,
    val value: String,
)

/**
 * A display-ready notification for the in-app notification center. The same
 * shape covers the backend's derived business alerts and, later, admin-authored
 * broadcasts.
 */
data class AppNotification(
    /** Type id, e.g. "low_stock" or "admin_12". */
    val id: String,
    /** Key the dismiss endpoint expects — what marks this read. */
    val key: String,
    val title: String,
    val message: String,
    val count: Int,
    val tone: NotificationTone,
    /** Web target path; blank when there is no screen to open. */
    val to: String,
    val preview: List<NotificationPreviewRow>,
)
