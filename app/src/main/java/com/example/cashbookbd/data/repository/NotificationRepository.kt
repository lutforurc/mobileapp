package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.NotificationItemDto
import com.example.cashbookbd.notifications.AppNotification
import com.example.cashbookbd.notifications.NotificationPreviewRow
import com.example.cashbookbd.notifications.NotificationTone
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Loads the in-app notification center from `GET /notifications/summary` and
 * marks items read via `POST /notifications/dismiss`. Already-dismissed items and
 * ones the user has no permission to act on are filtered out here, mirroring the
 * web's DropdownNotification.
 */
class NotificationRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getNotifications(permissions: List<Permission>): Resource<List<AppNotification>> =
        withContext(ioDispatcher) {
            try {
                val response = api.getNotificationSummary()
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Resource.Error("Server error (${response.code()}).")
                }
                val items = response.body()?.data?.payload?.notifications.orEmpty()
                    .asSequence()
                    .filter { it.dismissed != true }
                    .filter { canSee(it.id, permissions) }
                    .mapNotNull { it.toDomain() }
                    .toList()
                Resource.Success(items)
            } catch (e: IOException) {
                Resource.Error("No internet connection.")
            } catch (e: HttpException) {
                if (e.code() == HTTP_UNAUTHORIZED) {
                    Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
                } else {
                    Resource.Error("Server error (${e.code()}).")
                }
            } catch (e: Exception) {
                Resource.Error("Could not load notifications.")
            }
        }

    suspend fun dismiss(key: String, id: String): Resource<Unit> = withContext(ioDispatcher) {
        try {
            val body = buildMap {
                put("notification_key", key)
                if (id.isNotBlank()) put("notification_id", id)
            }
            val response = api.dismissNotification(body)
            if (response.isSuccessful) Resource.Success(Unit)
            else Resource.Error("Server error (${response.code()}).")
        } catch (e: IOException) {
            Resource.Error("No internet connection.")
        } catch (e: Exception) {
            Resource.Error("Could not dismiss the notification.")
        }
    }

    /**
     * The web's permission gating: a notification is hidden when the user cannot
     * act on it. Types with no rule here (subscription reminders, admin
     * broadcasts) are always shown — their targeting is server-side.
     */
    private fun canSee(id: String?, permissions: List<Permission>): Boolean = when (id) {
        "low_stock", "negative_stock" ->
            Permissions.hasAny(permissions, listOf("products.view", "product.stock.view"))
        "due_installments" -> Permissions.has(permissions, "installment.create")
        "pending_voucher_approval" -> Permissions.has(permissions, "voucher.approval")
        "pending_subscription_payments" ->
            Permissions.hasAny(permissions, listOf("subscription.admin", "subscription.payment.approve"))
        else -> true
    }

    private fun NotificationItemDto.toDomain(): AppNotification? {
        val key = notificationKey?.takeIf { it.isNotBlank() } ?: return null
        return AppNotification(
            id = id.orEmpty(),
            key = key,
            title = title.orEmpty().ifBlank { "Notification" },
            message = message.orEmpty(),
            count = count ?: 0,
            tone = NotificationTone.from(tone),
            to = to.orEmpty(),
            preview = preview.orEmpty().mapNotNull { row ->
                val label = row.label?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NotificationPreviewRow(
                    label = label,
                    meta = row.meta.orEmpty(),
                    value = row.value.orEmpty(),
                )
            },
        )
    }
}
