package com.example.cashbookbd.ui.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.admin.AdminMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.ReportColWidth
import com.example.cashbookbd.ui.reports.ReportColumn
import com.example.cashbookbd.ui.reports.ReportTable
import com.example.cashbookbd.ui.reports.cellText

/**
 * A read-only list screen (Recycle Bin, Log Changes, Branch/User/Order lists, …).
 * Fetches its [AppListViewModel] rows and renders them through the shared table.
 */
@Composable
fun AppListScreen(
    listKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = viewModel(
        factory = AppListViewModel.provideFactory(LocalContext.current, listKey)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    // Keep the right drawer section highlighted.
    val parentRoute = if (AdminMenu.byKey(listKey) != null) Routes.ADMIN else Routes.VR_SETTINGS

    AuthenticatedShell(
        title = state.title,
        currentRoute = parentRoute,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The menu name, shown as a heading above the list.
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                ListBody(state = state, onRetry = { viewModel.load() })
            }
            if (state.showPagination) {
                PaginationBar(state = state, onPrev = viewModel::prevPage, onNext = viewModel::nextPage)
            }
        }
    }
}

@Composable
private fun PaginationBar(state: AppListUiState, onPrev: () -> Unit, onNext: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev, enabled = state.canPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            Text("Prev")
        }
        Text(
            text = "Page ${state.currentPage} of ${state.lastPage}  •  ${state.total} total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNext, enabled = state.canNext) {
            Text("Next")
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
        }
    }
}

@Composable
private fun ListBody(state: AppListUiState, onRetry: () -> Unit) {
    when {
        state.isLoading -> Center { CircularProgressIndicator() }

        state.error != null -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }

        state.rows.isEmpty() -> Center {
            Text(
                text = "No records found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            val columns = remember(state.columns) { buildColumns(state) }
            ReportTable(columns = columns, data = state.rows)
        }
    }
}

private val COL_SL = 48.dp

private fun buildColumns(state: AppListUiState): List<ReportColumn<List<String>>> = buildList {
    add(
        ReportColumn("#", ReportColWidth.Fixed(COL_SL), TextAlign.Center) { _, index ->
            cellText((index + 1).toString(), align = TextAlign.Center)
        },
    )
    state.columns.forEachIndexed { ci, col ->
        val align = if (col.numeric) TextAlign.End else TextAlign.Start
        add(
            ReportColumn(
                header = col.label,
                width = ReportColWidth.Fixed(if (col.numeric) 112.dp else 168.dp),
                align = align,
            ) { row, _ -> cellText(row.getOrNull(ci).orEmpty(), align = align, maxLines = 2) },
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
