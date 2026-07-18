package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.subscription.model.CurrentSubscription
import com.example.cashbookbd.ui.subscription.model.PlanFeature
import com.example.cashbookbd.ui.subscription.model.SubscriptionPlan
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * The read-only Subscription screens' data: the available plans (Pricing) and the
 * current company's subscription (My Plan). Billing history and the admin plan
 * list are plain tables served by the shared list engine instead.
 */
class SubscriptionRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    /** The purchasable plans (`subscription/plans`). */
    suspend fun getPlans(): Resource<List<SubscriptionPlan>> = withContext(ioDispatcher) {
        call("subscription/plans") { body ->
            locateArray(body).map { parsePlan(it) }
        }
    }

    /** The current company's subscription (`subscription/current`); null when none. */
    suspend fun getCurrent(): Resource<CurrentSubscription?> = withContext(ioDispatcher) {
        call("subscription/current") { body ->
            val obj = unwrapObject(body) ?: return@call null
            // An empty subscription has no plan_id/status — treat as "none".
            if (obj.str("plan_id") == null && obj.str("status") == null) return@call null
            CurrentSubscription(
                planName = obj.str("plan_name").orEmpty(),
                status = obj.str("status").orEmpty(),
                accessStatus = obj.str("access_status").orEmpty(),
                startDate = obj.str("start_date").orEmpty(),
                endDate = obj.str("end_date").orEmpty(),
                trialEndAt = obj.str("trial_end_at").orEmpty(),
                nextBillingDate = obj.str("next_billing_date").orEmpty(),
                features = parseFeatures(obj),
            )
        }
    }

    private suspend fun <T> call(path: String, transform: (JsonElement?) -> T): Resource<T> = try {
        val response = api.get(path, emptyMap())
        when {
            response.code() == HTTP_UNAUTHORIZED ->
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            !response.isSuccessful && response.code() != 201 ->
                Resource.Error("Server error (${response.code()}). Please try again later.")
            else -> {
                val body = response.body()
                if (body?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false
                ) {
                    Resource.Error("Couldn't load subscription information.")
                } else {
                    Resource.Success(transform(body))
                }
            }
        }
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    private fun parsePlan(obj: JsonObject): SubscriptionPlan = SubscriptionPlan(
        id = obj.str("id").orEmpty(),
        name = obj.str("name").orEmpty(),
        price = obj.str("price")?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
        currency = obj.str("currency").orEmpty(),
        billingInterval = obj.str("billing_interval").orEmpty(),
        trialDays = obj.int("trial_days") ?: 0,
        maxEmployees = obj.int("max_employees"),
        maxCustomers = obj.int("max_customers"),
        maxProducts = obj.int("max_products"),
        maxUsers = obj.int("max_users"),
        maxBranches = obj.int("max_branches"),
        maxTransactionsPerMonth = obj.int("max_transactions_per_month"),
        supportTime = obj.str("support_time").orEmpty(),
        description = obj.str("description").orEmpty(),
        features = parseFeatures(obj),
    )

    private fun parseFeatures(obj: JsonObject): List<PlanFeature> {
        val arr = obj.get("features")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val f = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = f.str("feature_name") ?: f.str("feature_key") ?: return@mapNotNull null
            PlanFeature(name = name, value = f.str("feature_value").orEmpty())
        }
    }

    /** The array under `foundData`'s `data` / `data.data` envelope. */
    private fun locateArray(root: JsonElement?): List<JsonObject> {
        if (root == null) return emptyList()
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null && !payload.isJsonArray) payload = inner
        }
        val arr: JsonArray = payload.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
    }

    /** The object under `data` / `data.data`. */
    private fun unwrapObject(root: JsonElement?): JsonObject? {
        var payload: JsonElement = root ?: return null
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.str(key: String): String? {
        val el = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }

    private fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
}
