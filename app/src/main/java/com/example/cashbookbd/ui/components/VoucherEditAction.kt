package com.example.cashbookbd.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.VoucherEdit
import com.example.cashbookbd.navigation.VoucherEditRow
import com.example.cashbookbd.ui.invoice.CurrentBranchCache
import com.example.cashbookbd.ui.invoice.TilesBranch

/** Who may edit which report row, and what Edit does — the web's Action column. */
class VoucherRowEditor(
    val canEdit: (VoucherEditRow) -> Boolean,
    val onEdit: (VoucherEditRow) -> Unit,
)

/**
 * The report-row editor for this session: each kind's own edit permission,
 * and the branch's trade picking the Tiles invoice screens (web Ledger.tsx
 * handleEditVoucher).
 */
@Composable
fun rememberVoucherRowEditor(navController: NavHostController): VoucherRowEditor {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val session by sessionManager.state.collectAsStateWithLifecycle()
    val branchApi = remember { ServiceLocator.provideWebApi(context.applicationContext) }
    var branchInfo by remember { mutableStateOf(CurrentBranchCache.value) }
    LaunchedEffect(session.settings?.branchId) {
        branchInfo = CurrentBranchCache.load(branchApi)
    }
    return remember(session.permissions, session.settings, branchInfo) {
        VoucherRowEditor(
            canEdit = { row -> VoucherEdit.canEdit(session.permissions, row) },
            onEdit = { row ->
                val isTiles = TilesBranch.isTiles(session.settings, branchInfo)
                VoucherEdit.open(navController, row, isTiles)?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/** The pencil in a report's ACTION cell. */
@Composable
fun VoucherEditButton(row: VoucherEditRow, editor: VoucherRowEditor) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        IconButton(onClick = { editor.onEdit(row) }) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit ${row.vrNo}",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
