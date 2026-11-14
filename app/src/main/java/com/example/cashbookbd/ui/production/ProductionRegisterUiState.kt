package com.example.cashbookbd.ui.production

import com.example.cashbookbd.ui.production.model.ProductionRegisterReport
import com.example.cashbookbd.ui.reports.model.SimpleDate

data class ProductionRegisterUiState(
    val fromDate: SimpleDate? = null,
    val toDate: SimpleDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: ProductionRegisterReport? = null,
    val appliedRange: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canRun: Boolean get() = fromDate != null && toDate != null && !isLoading
    val isEmptyResult: Boolean get() = report != null && report.isEmpty
}

data class ProductionCostingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: com.example.cashbookbd.ui.production.model.ProductionCosting? = null,
    val sessionExpired: Boolean = false,
)
