package com.example.cashbookbd.ui.subscription

import com.example.cashbookbd.ui.subscription.model.CurrentSubscription
import com.example.cashbookbd.ui.subscription.model.SubscriptionPlan

/** Backs both custom subscription screens; each loads only the field it needs. */
data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Pricing screen. */
    val plans: List<SubscriptionPlan> = emptyList(),
    /** My Plan screen; null when the company has no active subscription. */
    val current: CurrentSubscription? = null,
    val hasCurrent: Boolean = false,
    val sessionExpired: Boolean = false,
)
