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
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.delay

/**
 * A self-contained searchable dropdown over [SelectorOption] — the generic twin
 * of [SearchableLedgerDropdown], used by report filters whose source searches
 * server-side (product, labour). It owns its own query/debounce/loading state and
 * simply asks the caller to run [search] and receive the chosen option.
 *
 * @param selected the current selection (drives the field text); null when none.
 * @param onSelected called with the chosen [SelectorOption].
 * @param search suspend search — typically a ViewModel call that hits the source's
 *   DDL endpoint with the typed keyword and returns a [Resource].
 * @param emptyText message shown when a search returns no rows.
 * @param minSearchChars characters required before searching (default [MIN_SEARCH_CHARS]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableSelectDropdown(
    selected: SelectorOption?,
    onSelected: (SelectorOption) -> Unit,
    search: suspend (String) -> Resource<List<SelectorOption>>,
    modifier: Modifier = Modifier,
    label: String = "Search",
    placeholder: String = "Type to search…",
    emptyText: String = "No match found",
    debounceMs: Long = 400L,
    minSearchChars: Int = MIN_SEARCH_CHARS,
) {
    var query by remember { mutableStateOf(selected?.label.orEmpty()) }
    var searchKey by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SelectorOption>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        query = selected?.label.orEmpty()
        searchKey = ""
        expanded = false
    }

    LaunchedEffect(searchKey) {
        val keyword = searchKey.trim()
        // Below the threshold there is nothing to do — and crucially isSearching
        // stays false, so the field shows the search icon rather than a spinner
        // that would never resolve.
        if (keyword.length < minSearchChars) {
            results = emptyList()
            isSearching = false
            searchError = null
            return@LaunchedEffect
        }
        isSearching = true
        searchError = null
        delay(debounceMs)
        when (val outcome = search(keyword)) {
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

    val menuVisible = expanded && searchKey.isNotBlank()

    ExposedDropdownMenuBox(
        expanded = menuVisible,
        onExpandedChange = { expanded = it && searchKey.isNotBlank() },
        modifier = modifier,
    ) {
        FieldFrame(
            label = label,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth(),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        ) {
            FieldTextInput(
                value = query,
                onValueChange = {
                    query = it
                    searchKey = it
                    expanded = true
                },
                placeholder = placeholder,
            )
        }

        ExposedDropdownMenu(expanded = menuVisible, onDismissRequest = { expanded = false }) {
            when {
                // Ahead of the empty branch: 1–2 characters mean "keep typing",
                // not "no match found".
                searchKey.trim().length < minSearchChars -> InfoRow {
                    Text(
                        text = "Type at least $minSearchChars characters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                isSearching -> InfoRow {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> results.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!item.sublabel.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.sublabel,
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
                            searchKey = ""
                            query = item.label
                            results = emptyList()
                            onSelected(item)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

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
