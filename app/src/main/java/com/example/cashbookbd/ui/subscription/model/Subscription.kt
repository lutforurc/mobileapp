package com.example.cashbookbd.ui.subscription.model

/** One feature flag on a plan / subscription (`feature_key`, `feature_name`, `feature_value`). */
data class PlanFeature(
    val name: String,
    /** "1" when enabled. */
    val value: String,
) {
    val enabled: Boolean get() = value == "1"
}

/** A purchasable plan (web `formatPlan`), shown as a Pricing card. */
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val price: Double,
    val currency: String,
    val billingInterval: String,
    val trialDays: Int,
    /** null quota = Unlimited. */
    val maxEmployees: Int?,
    val maxCustomers: Int?,
    val maxProducts: Int?,
    val maxUsers: Int?,
    val maxBranches: Int?,
    val maxTransactionsPerMonth: Int?,
    val supportTime: String,
    val description: String,
    val features: List<PlanFeature>,
)

/** The current company's subscription (web `formatSubscription`), shown on My Plan. */
data class CurrentSubscription(
    val planName: String,
    val status: String,
    val accessStatus: String,
    val startDate: String,
    val endDate: String,
    val trialEndAt: String,
    val nextBillingDate: String,
    val features: List<PlanFeature>,
)
