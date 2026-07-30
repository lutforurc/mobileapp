package com.example.cashbookbd.ui.vrsettings

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.VoucherHistoryItem
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption

/**
 * Voucher History — a port of the web's /vr-settings/voucher-history: a branch
 * + voucher-number filter over the audit trail, with one card per change and
 * the `changed_only` diff rendered as "field: old → new" lines.
 */
@Composable
fun VoucherHistoryScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoucherHistoryViewModel = viewModel(
        factory = VoucherHistoryViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // AppSelectDropdown speaks SelectorOption; the branches map through it.
    val branchOptions = remember(state.branches) {
        state.branches.map { SelectorOption(it.id.toString(), it.name) }
    }
    val selectedBranchOption = state.selectedBranch?.let { SelectorOption(it.id.toString(), it.name) }

    AuthenticatedShell(
        title = "History",
        currentRoute = Routes.VR_SETTINGS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppSelectDropdown(
                label = "Select Branch",
                options = branchOptions,
                selected = selectedBranchOption,
                onSelected = { option ->
                    state.branches.firstOrNull { it.id.toString() == option.id }
                        ?.let(viewModel::onBranchSelected)
                },
                placeholder = if (state.isBranchesLoading) "Loading branches…" else "Select Branch",
            )
            state.branchesError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            AppTextField(
                value = state.voucherNo,
                onValueChange = viewModel::onVoucherNo,
                label = "Enter voucher number",
                caption = "Voucher Number",
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "View History",
                onClick = viewModel::apply,
                enabled = state.canApply,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            HistoryResults(state = state, onRetry = viewModel::apply)
        }
    }
}

@Composable
private fun HistoryResults(state: VoucherHistoryUiState, onRetry: () -> Unit) {
    when {
        state.isLoading -> ResultsMessage {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }

        state.error != null -> ResultsMessage {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        state.result == null -> ResultsMessage {
            Text(
                text = "Choose a branch, enter a voucher number, then tap View History.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        state.result.isEmpty() -> ResultsMessage {
            Text(
                text = "No history found for this voucher.",
                color = MaterialTheme.appColors.textOnScreenMuted,
                textAlign = TextAlign.Center,
            )
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val branchName = state.result.firstOrNull()?.branchName.orEmpty()
            if (branchName.isNotBlank()) {
                Text(
                    text = "Branch: $branchName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            state.result.forEach { item -> HistoryCard(item) }
        }
    }
}

@Composable
private fun ResultsMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** One audit entry: title, action, who/when, then the "field: old → new" diff. */
@Composable
private fun HistoryCard(item: VoucherHistoryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (item.action.isNotBlank()) {
            Text(
                text = item.action,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = listOf(item.actionBy, item.createdAt)
                .filter { it.isNotBlank() }
                .joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.changes.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            item.changes.forEach { change ->
                Text(
                    text = "${change.path}: ${change.oldValue} → ${change.newValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
