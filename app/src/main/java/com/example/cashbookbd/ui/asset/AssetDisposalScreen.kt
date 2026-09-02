package com.example.cashbookbd.ui.asset

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.AssetDisposalPlan
import com.example.cashbookbd.data.repository.AssetRepository
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.appColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDisposalUiState(
    val isLoading: Boolean = true,
    val isPlanLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val assetName: String = "",
    val assetCode: String = "",
    /** disposed (sold) / written_off. */
    val status: String = "disposed",
    val disposedOn: String = todayApi(),
    val proceeds: String = "",
    val tillCoa4Id: String = "",
    val note: String = "",
    val plan: AssetDisposalPlan? = null,
    val confirming: Boolean = false,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isWriteOff: Boolean get() = status == "written_off"
}

class AssetDisposalViewModel(
    private val repository: AssetRepository,
    private val assetId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDisposalUiState())
    val uiState: StateFlow<AssetDisposalUiState> = _uiState.asStateFlow()

    /** The pending re-ask, so typing in the money box does not fire one a key. */
    private var planJob: Job? = null

    init {
        viewModelScope.launch {
            when (val result = repository.fetchAsset(assetId)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        assetName = result.data.asset?.name.orEmpty(),
                        assetCode = result.data.asset?.code.orEmpty(),
                    )
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
        askPlan()
    }

    /**
     * ⚠️ Asked BEFORE anything is typed, and asked again whenever the day or the
     * money changes: the depreciation owed up to the day it goes is part of the
     * entry, and it moves with the date. A read only — nothing is posted here.
     */
    fun askPlan(afterDelay: Long = 0L) {
        planJob?.cancel()
        planJob = viewModelScope.launch {
            if (afterDelay > 0) delay(afterDelay)
            val state = _uiState.value
            _uiState.update { it.copy(isPlanLoading = true) }
            val result = repository.fetchDisposalPlan(
                assetId = assetId,
                disposedOn = state.disposedOn,
                proceeds = if (state.isWriteOff) "0" else state.proceeds,
                tillCoa4Id = state.tillCoa4Id,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isPlanLoading = false, plan = result.data, error = null)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isPlanLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onStatus(value: String) {
        _uiState.update { it.copy(status = value) }
        askPlan()
    }

    fun onDate(value: String) {
        _uiState.update { it.copy(disposedOn = value) }
        askPlan()
    }

    fun onTill(value: String) {
        _uiState.update { it.copy(tillCoa4Id = value) }
        askPlan()
    }

    fun onProceeds(value: String) {
        _uiState.update { it.copy(proceeds = value) }
        askPlan(afterDelay = 600L)
    }

    fun onNote(value: String) = _uiState.update { it.copy(note = value) }

    fun ask() = _uiState.update { it.copy(confirming = true) }

    fun cancel() = _uiState.update { it.copy(confirming = false) }

    /** The real thing: a voucher, written once somebody has said so. */
    fun confirm() {
        val state = _uiState.value
        if (state.disposedOn.isBlank()) {
            _uiState.update { it.copy(confirming = false, error = "Which day did it go?") }
            return
        }
        _uiState.update { it.copy(isSaving = true, confirming = false) }
        viewModelScope.launch {
            val result = repository.storeDisposal(
                assetId = assetId,
                disposedOn = state.disposedOn,
                proceeds = if (state.isWriteOff) "0" else state.proceeds,
                tillCoa4Id = state.tillCoa4Id,
                status = state.status,
                note = state.note,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
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
        fun provideFactory(context: Context, assetId: Long) = viewModelFactory {
            initializer {
                AssetDisposalViewModel(AssetRepository.get(context.applicationContext), assetId)
            }
        }
    }
}

/**
 * Selling an asset, or writing it off.
 *
 * ⚠️ SHOWN LEG BY LEG BEFORE IT IS DONE. This writes off a cost that has stood
 * in the balance sheet for years, charges the depreciation owed up to the day it
 * went, and puts whatever is left through the profit and loss. None of that is
 * something to discover afterwards — so the entry is on screen first, in the
 * server's own figures, and the button asks once more before it posts.
 */
@Composable
fun AssetDisposalScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    assetId: Long,
    modifier: Modifier = Modifier,
    viewModel: AssetDisposalViewModel = viewModel(
        factory = AssetDisposalViewModel.provideFactory(LocalContext.current, assetId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            navController.reportAssetSaved(it)
            navController.popBackStack()
        }
    }

    val statusOptions = remember {
        listOf(
            SelectorOption("disposed", "Sold"),
            SelectorOption("written_off", "Written off / scrapped"),
        )
    }
    val tillOptions = remember(state.plan) {
        listOf(SelectorOption("", "Not chosen")) +
            state.plan?.tills.orEmpty().map {
                SelectorOption(it.id.toString(), "${it.name} (${it.groupName})")
            }
    }

    AuthenticatedShell(
        title = if (state.isWriteOff) "Write off — ${state.assetName}" else "Sell — ${state.assetName}",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        if (state.isLoading) {
            AssetLoading()
            return@AuthenticatedShell
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.assetCode.isNotBlank()) {
                Text(
                    text = state.assetCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            state.error?.let { AssetNotice(text = it, tone = AssetTone.Danger) }

            AppSelectDropdown(
                label = "What happened",
                options = statusOptions,
                selected = statusOptions.firstOrNull { it.id == state.status } ?: statusOptions.first(),
                onSelected = { viewModel.onStatus(it.id) },
            )

            AssetDateField(
                label = "On",
                value = state.disposedOn,
                onPicked = viewModel::onDate,
                modifier = Modifier.fillMaxWidth(),
            )
            DisposalNote("Depreciation is charged to this day.")

            AppTextField(
                value = if (state.isWriteOff) "" else state.proceeds,
                onValueChange = viewModel::onProceeds,
                label = "0",
                caption = "Money received",
                enabled = !state.isWriteOff,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            DisposalNote(
                if (state.isWriteOff) {
                    "A write-off fetches nothing."
                } else {
                    "Leave empty if nothing was received."
                },
            )

            AppSelectDropdown(
                label = "Into which account",
                options = tillOptions,
                selected = tillOptions.firstOrNull { it.id == state.tillCoa4Id } ?: tillOptions.first(),
                onSelected = { viewModel.onTill(it.id) },
                enabled = !state.isWriteOff,
            )
            DisposalNote("Where the money went.")

            AppTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = "Sold to Karim Traders, receipt 4471",
                caption = "Note",
                modifier = Modifier.fillMaxWidth(),
            )

            state.plan?.let { plan -> DisposalPlanPanel(plan) }

            PrimaryButton(
                text = if (state.isWriteOff) "Write it off" else "Sell it",
                onClick = viewModel::ask,
                enabled = state.plan?.readyToDispose == true && !state.isPlanLoading,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Back to the register",
                onClick = { navController.popBackStack() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(if (state.isWriteOff) "Write it off?" else "Sell it?") },
            text = {
                Text(
                    "A voucher will be written: the cost comes out of the balance sheet, the " +
                        "depreciation owed up to ${onTheDay(state.disposedOn)} is charged, and " +
                        "whatever is left goes through the profit and loss.",
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = if (state.isWriteOff) "Write it off" else "Sell it",
                    onClick = viewModel::confirm,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Leave it", onClick = viewModel::cancel) },
        )
    }
}

/** The entry itself — somebody signing this off reads legs, not a summary. */
@Composable
private fun DisposalPlanPanel(plan: AssetDisposalPlan) {
    AssetPanel(title = "What the voucher will say") {
        AssetSummaryBar(
            parts = buildList {
                add(AssetSummaryPart("Cost ${AmountFormat.format(plan.cost)}"))
                add(AssetSummaryPart("Depreciation so far ${AmountFormat.format(plan.accumulated)}"))
                if (plan.catchUpAmount > 0) {
                    add(
                        AssetSummaryPart(
                            "plus ${plan.catchUpDays} day(s) to the day it went " +
                                AmountFormat.format(plan.catchUpAmount),
                            tone = AssetTone.Info,
                        ),
                    )
                }
                add(
                    AssetSummaryPart(
                        "Worth on the day ${AmountFormat.format(plan.writtenDownValue)}",
                        strong = true,
                    ),
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        plan.legs.forEach { leg ->
            AssetLine(
                label = leg.head,
                sublabel = leg.note,
                value = if (leg.debit > 0) {
                    "Dr ${AmountFormat.format(leg.debit)}"
                } else {
                    "Cr ${AmountFormat.format(leg.credit)}"
                },
            )
        }

        AssetLine(
            label = when {
                plan.gain > 0 -> "Gain"
                plan.loss > 0 -> "Loss"
                else -> "No gain or loss"
            },
            value = when {
                plan.gain > 0 -> AmountFormat.format(plan.gain)
                plan.loss > 0 -> AmountFormat.format(plan.loss)
                else -> "-"
            },
            valueTone = when {
                plan.gain > 0 -> AssetTone.Success
                plan.loss > 0 -> AssetTone.Danger
                else -> AssetTone.Muted
            },
            strong = true,
            divider = false,
        )

        if (!plan.readyToDispose) {
            AssetNotice(
                text = "This category has no gain-or-loss head yet, so the entry cannot be " +
                    "written. Choose it on the Categories screen.",
                tone = AssetTone.Warning,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DisposalNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textOnScreenMuted,
    )
}
