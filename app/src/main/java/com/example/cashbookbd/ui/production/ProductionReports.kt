package com.example.cashbookbd.ui.production

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.ProductionRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.production.ProductionMenu
import com.example.cashbookbd.ui.components.AppDateField
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.production.model.ExpiryRow
import com.example.cashbookbd.ui.production.model.FreeGoodsReport
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- Free Goods ---------------------------------------------------------

data class FreeGoodsUiState(
    val from: SimpleDate = SimpleDate.today().copy(day = 1),
    val to: SimpleDate = SimpleDate.today(),
    val report: FreeGoodsReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class FreeGoodsViewModel(
    private val repository: ProductionRepository,
    transactionDate: String?,
) : ViewModel() {
    private val today = SimpleDate.fromDisplay(transactionDate) ?: SimpleDate.today()

    private val _uiState = MutableStateFlow(
        FreeGoodsUiState(from = today.copy(day = 1), to = today),
    )
    val uiState: StateFlow<FreeGoodsUiState> = _uiState.asStateFlow()

    init {
        run()
    }

    fun onFrom(date: SimpleDate) = _uiState.update { it.copy(from = date) }
    fun onTo(date: SimpleDate) = _uiState.update { it.copy(to = date) }

    fun run() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchFreeGoods(state.from.toApi(), state.to.toApi())) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, report = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                FreeGoodsViewModel(
                    repository = ServiceLocator.provideProductionRepository(app),
                    transactionDate = ServiceLocator.provideSessionManager(app)
                        .state.value.settings?.transactionDate,
                )
            }
        }
    }
}

@Composable
fun FreeGoodsScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FreeGoodsViewModel = viewModel(
        factory = FreeGoodsViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    com.example.cashbookbd.ui.reports.LogoutOnExpiry(
        state.sessionExpired,
        viewModel::onSessionExpiredHandled,
        onLogout,
    )

    AuthenticatedShell(
        title = "Free Goods",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppDateField(
                    label = "From",
                    date = state.from,
                    onPicked = viewModel::onFrom,
                    modifier = Modifier.weight(1f),
                )
                AppDateField(
                    label = "To",
                    date = state.to,
                    onPicked = viewModel::onTo,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = "Show",
                onClick = viewModel::run,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.appColors.danger, style = MaterialTheme.typography.bodySmall)
            }

            val report = state.report
            when {
                state.isLoading && report == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                report == null || report.rows.isEmpty() -> Text(
                    "No free goods in this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                else -> {
                    report.rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = row.productName + if (row.productCode.isNotBlank()) " (${row.productCode})" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${row.vrNo} · ${row.date} · ${row.schemeName.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.textOnScreenMuted,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${trimQty(row.qty)} × ${AmountFormat.format(row.unitCost)}", style = MaterialTheme.typography.bodySmall)
                                Text(AmountFormat.format(row.value), style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold)
                            }
                        }
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = AppFontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            text = "${trimQty(report.totalQty)} · ${AmountFormat.format(report.totalCost)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = AppFontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = "ফ্রি মালের খরচ আলাদা করে দেখানো হয়েছে — হিসাবের খাতায় এটি বিক্রিত মালের খরচেই ধরা আছে, তাই এখানে আলাদা করে ধরলে দুইবার হবে।",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- Near Expiry --------------------------------------------------------

data class ExpiryUiState(
    val asOf: SimpleDate = SimpleDate.today(),
    val days: String = "30",
    val rows: List<ExpiryRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class ExpiryViewModel(
    private val repository: ProductionRepository,
    transactionDate: String?,
) : ViewModel() {
    private val today = SimpleDate.fromDisplay(transactionDate) ?: SimpleDate.today()

    private val _uiState = MutableStateFlow(ExpiryUiState(asOf = today))
    val uiState: StateFlow<ExpiryUiState> = _uiState.asStateFlow()

    init {
        run()
    }

    fun onAsOf(date: SimpleDate) = _uiState.update { it.copy(asOf = date) }
    fun onDays(v: String) = _uiState.update { it.copy(days = v.filter { c -> c.isDigit() }) }

    fun run() {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.fetchExpiry(state.asOf.toApi(), state.days)) {
                is Resource.Success -> _uiState.update { it.copy(isLoading = false, rows = result.data) }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val app = context.applicationContext
                ExpiryViewModel(
                    repository = ServiceLocator.provideProductionRepository(app),
                    transactionDate = ServiceLocator.provideSessionManager(app)
                        .state.value.settings?.transactionDate,
                )
            }
        }
    }
}

@Composable
fun ProductionExpiryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpiryViewModel = viewModel(
        factory = ExpiryViewModel.provideFactory(LocalContext.current),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    com.example.cashbookbd.ui.reports.LogoutOnExpiry(
        state.sessionExpired,
        viewModel::onSessionExpiredHandled,
        onLogout,
    )

    AuthenticatedShell(
        title = "Near Expiry",
        currentRoute = ProductionMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                AppDateField(
                    label = "As of",
                    date = state.asOf,
                    onPicked = viewModel::onAsOf,
                    modifier = Modifier.weight(1.4f),
                )
                AppTextField(
                    value = state.days,
                    onValueChange = viewModel::onDays,
                    label = "30",
                    caption = "Within (days)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = "Show",
                onClick = viewModel::run,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(it, color = MaterialTheme.appColors.danger, style = MaterialTheme.typography.bodySmall)
            }

            if (state.rows.isEmpty()) {
                Text(
                    text = if (state.isLoading) "Loading…" else "Nothing expires in this window",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            } else {
                state.rows.forEachIndexed { index, row ->
                    val expired = row.daysLeft < 0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.appColors.cardRow, RoundedCornerShape(8.dp))
                            .clickable { navController.navigate(ProductionMenu.costing(row.productionId)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}. ${row.productName}" +
                                    if (row.productCode.isNotBlank()) " (${row.productCode})" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (expired) "Expired ${kotlin.math.abs(row.daysLeft).toInt()} d ago"
                                else "${row.daysLeft.toInt()} d left",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = AppFontWeight.SemiBold,
                                color = if (expired) MaterialTheme.appColors.danger
                                else MaterialTheme.appColors.textOnScreenMuted,
                            )
                        }
                        Text(
                            text = "${row.batchRef.ifBlank { "-" }} · ${row.vrNo.ifBlank { "-" }} · " +
                                "${row.productionDate} → ${row.expiryDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                        Text(
                            text = "Batch made ${trimQty(row.qty)} ${row.unit} · on hand ${trimQty(row.onHand)} ${row.unit}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                text = "\"Product on hand\" পুরো পণ্যের মজুদ, শুধু এই ব্যাচের নয় — বিক্রি কোন ব্যাচ থেকে গেছে তা আলাদা করে রাখা হয় না। সারিতে ক্লিক করলে ব্যাচের Costing খোলে।",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textOnScreenMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun trimQty(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
