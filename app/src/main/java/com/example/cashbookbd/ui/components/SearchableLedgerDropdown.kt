package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.Resource
import kotlinx.coroutines.delay

/**
 * The value a caller receives when a ledger is chosen. Use [id] as the
 * `ledger_id` when calling report APIs.
 */
data class LedgerDropdownItem(
    val id: Int,
    val name: String,
    val mobile: String?,
)

/**
 * A self-contained, reusable searchable dropdown for ledger/account selection.
 *
 * Drop it into any screen — it owns its own search state (query text, debounce,
 * loading / empty / error) and simply asks the caller to run the search and to
 * receive the chosen item. It contains **no** screen-specific logic.
 *
 * Behaviour:
 * - Each keystroke triggers [searchLedgers] after a [debounceMs] pause.
 * - A spinner shows while searching; "No ledger found" when empty; the returned
 *   [Resource.Error.message] when the search fails.
 * - Each result shows the ledger name on line 1 and, when present, the mobile
 *   number on a smaller muted line 2.
 * - After selection the field shows only the ledger name (never the mobile).
 *
 * All colours come from [MaterialTheme.colorScheme], so it follows light/dark.
 *
 * @param selectedLedger the current selection (drives the field text); null when none.
 * @param onLedgerSelected called with the chosen [LedgerDropdownItem].
 * @param searchLedgers suspend search — typically a repository/ViewModel call
 *   that hits `GET /chart_of_accounts/ddl/l4-list?searchName=<query>&acType=`
 *   (auth handled by the network layer) and returns a [Resource].
 * @param label field label. @param placeholder empty-field hint.
 * @param enabled when false the field is read-only and won't open.
 * @param errorMessage an external (e.g. validation) error shown under the field.
 * @param debounceMs keystroke debounce in millis (default 400).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableLedgerDropdown(
    selectedLedger: LedgerDropdownItem?,
    onLedgerSelected: (LedgerDropdownItem) -> Unit,
    searchLedgers: suspend (String) -> Resource<List<LedgerDropdownItem>>,
    modifier: Modifier = Modifier,
    label: String = "Select Ledger",
    placeholder: String = "Type to search ledger…",
    enabled: Boolean = true,
    errorMessage: String? = null,
    debounceMs: Long = 400L,
) {
    // The text shown in the field (typed keyword, or the selected name).
    var query by remember { mutableStateOf(selectedLedger?.name.orEmpty()) }
    // The value we actually debounce/search on — only changed by user typing, so
    // programmatically setting the field on selection never re-triggers a search.
    var searchKey by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LedgerDropdownItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Reflect an externally set / cleared selection in the field text.
    LaunchedEffect(selectedLedger) {
        query = selectedLedger?.name.orEmpty()
        searchKey = ""
        expanded = false
    }

    // Debounced search: LaunchedEffect cancels the previous run whenever the key
    // changes, so rapid typing collapses into a single call after [debounceMs].
    LaunchedEffect(searchKey) {
        val keyword = searchKey.trim()
        if (keyword.isEmpty()) {
            results = emptyList()
            isSearching = false
            searchError = null
            return@LaunchedEffect
        }
        isSearching = true
        searchError = null
        delay(debounceMs)
        when (val outcome = searchLedgers(keyword)) {
            is Resource.Success -> {
                results = outcome.data
                isSearching = false
                searchError = null
            }

            is Resource.Error -> {
                results = emptyList()
                isSearching = false
                searchError = outcome.message
            }

            Resource.Loading -> Unit
        }
    }

    val menuVisible = enabled && expanded && searchKey.isNotBlank()

    ExposedDropdownMenuBox(
        expanded = menuVisible,
        onExpandedChange = { if (enabled) expanded = it && searchKey.isNotBlank() },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                searchKey = it
                expanded = true
            },
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it) } },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = enabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { expanded = false },
        ) {
            when {
                isSearching -> InfoRow {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                }

                searchError != null -> InfoRow {
                    Text(
                        text = searchError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                results.isEmpty() -> InfoRow {
                    Text(
                        text = "No ledger found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> results.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                // Line 1: ledger name.
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Line 2: mobile (smaller + muted); hidden when absent.
                                if (!item.mobile.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.mobile,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            searchKey = ""          // stop searching + hide menu
                            query = item.name       // show only the name
                            results = emptyList()
                            onLedgerSelected(item)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

/** A non-clickable status row (loading / error / empty) inside the menu. */
@Composable
private fun InfoRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
