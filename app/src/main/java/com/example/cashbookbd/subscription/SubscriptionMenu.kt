package com.example.cashbookbd.subscription

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry in the Subscription menu, mirroring the web's subscription screens. */
data class SubscriptionItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/**
 * The Subscription menu registry. My Plan / Pricing / Billing are available to any
 * logged-in user (the web gates them by login only); Subscription Plans is the
 * admin plan list, gated by `subscription.plans`.
 */
object SubscriptionMenu {

    val all: List<SubscriptionItem> = listOf(
        SubscriptionItem("myPlan", "My Plan", emptyList()),
        SubscriptionItem("pricing", "Pricing", emptyList()),
        SubscriptionItem("subscriptionBilling", "Billing History", emptyList()),
        SubscriptionItem("subscriptionAdminPlans", "Subscription Plans", listOf("subscription.plans")),
    )

    private val byKey: Map<String, SubscriptionItem> = all.associateBy { it.key }

    fun byKey(key: String?): SubscriptionItem? = key?.let { byKey[it] }

    /** The Subscription section shows for any authenticated user (My Plan is universal). */
    fun hasParentAccess(@Suppress("UNUSED_PARAMETER") permissions: List<Permission>?): Boolean = true

    /** Entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<SubscriptionItem> =
        all.filter { it.anyOf.isEmpty() || Permissions.hasAny(permissions, it.anyOf) }
}
