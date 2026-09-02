package com.example.cashbookbd.ui.asset

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.appColors

/**
 * The pieces every Fixed Asset screen is built from.
 *
 * Seven screens draw the same four things — a branch chooser, a calendar box, a
 * coloured notice, and a line with a label at one end and a figure at the other
 * — so each is written once here. A colour or a corner changes in one place, per
 * the project's shared-component rule, and no screen names a colour of its own.
 */

/** How a notice or a figure is meant to read. */
internal enum class AssetTone { Plain, Muted, Info, Success, Warning, Danger }

@Composable
internal fun toneInk(tone: AssetTone): Color = when (tone) {
    AssetTone.Plain -> MaterialTheme.colorScheme.onSurface
    AssetTone.Muted -> MaterialTheme.appColors.textMuted
    AssetTone.Info -> MaterialTheme.appColors.info
    AssetTone.Success -> MaterialTheme.appColors.success
    AssetTone.Warning -> MaterialTheme.appColors.warning
    AssetTone.Danger -> MaterialTheme.appColors.danger
}

@Composable
private fun toneTint(tone: AssetTone): Color = when (tone) {
    AssetTone.Info -> MaterialTheme.appColors.infoTint
    AssetTone.Success -> MaterialTheme.appColors.successTint
    AssetTone.Warning -> MaterialTheme.appColors.warningTint
    AssetTone.Danger -> MaterialTheme.appColors.dangerTint
    else -> MaterialTheme.appColors.cardRaised
}

/**
 * The backend's yyyy-MM-dd as the dd/MM/yyyy this product shows everywhere.
 * An em dash for nothing, as the web has it — a blank cell reads as a bug.
 */
internal fun onTheDay(value: String?): String {
    val parts = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(value.orEmpty()) ?: return "—"
    val (y, m, d) = parts.destructured
    return "$d/$m/$y"
}

/** A rate as it is spoken: "20%", never "20.00%". */
internal fun percentText(rate: Double): String =
    if (rate == rate.toLong().toDouble()) "${rate.toLong()}%" else "$rate%"

/** Today, in the format the API takes. */
internal fun todayApi(): String {
    val c = java.util.Calendar.getInstance()
    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

/**
 * A calendar box holding a yyyy-MM-dd string. Shows dd/MM/yyyy; hands back the
 * API's format, so a screen never converts a date itself.
 */
@Composable
internal fun AssetDateField(
    label: String,
    value: String,
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val context = LocalContext.current
    PickerField(
        label = label,
        value = if (value.isBlank()) "" else onTheDay(value),
        placeholder = placeholder,
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = {
            val start = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(value)
            val calendar = java.util.Calendar.getInstance()
            val year = start?.groupValues?.get(1)?.toIntOrNull() ?: calendar.get(java.util.Calendar.YEAR)
            val month = start?.groupValues?.get(2)?.toIntOrNull() ?: (calendar.get(java.util.Calendar.MONTH) + 1)
            val day = start?.groupValues?.get(3)?.toIntOrNull() ?: calendar.get(java.util.Calendar.DAY_OF_MONTH)
            DatePickerDialog(
                context,
                { _, pickedYear, pickedMonth, pickedDay ->
                    onPicked(
                        String.format(
                            java.util.Locale.US, "%04d-%02d-%02d",
                            pickedYear, pickedMonth + 1, pickedDay,
                        ),
                    )
                },
                year,
                month - 1,
                day,
            ).show()
        },
    )
}

/**
 * The property chooser.
 *
 * ⚠️ On every asset screen but Categories, and that is not an oversight: a
 * CATEGORY is the company's ("vehicles at 20%" is one bookkeeping decision),
 * while an ASSET stands somewhere and is depreciated into that branch's books.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AssetBranchField(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        PickerField(
            label = "Property",
            value = selected?.name ?: if (isLoading) "Loading branches…" else "",
            placeholder = "Select Branch",
            trailingIcon = Icons.Filled.ArrowDropDown,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (branches.isNotEmpty()) expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = { onSelected(branch); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** A sentence in a tinted box — the amber warnings, the green confirmations. */
@Composable
internal fun AssetNotice(
    text: String,
    tone: AssetTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape,
        color = toneTint(tone),
        contentColor = toneInk(tone),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** One figure of a summary bar: a word, and the tone it is meant to read in. */
internal data class AssetSummaryPart(
    val text: String,
    val tone: AssetTone = AssetTone.Plain,
    val strong: Boolean = false,
)

/**
 * The strip of figures above a table. What is out, what is left to look at —
 * the number the screen was opened for comes first and in words.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AssetSummaryBar(parts: List<AssetSummaryPart>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            parts.forEach { part ->
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (part.strong) AppFontWeight.Bold else AppFontWeight.Normal,
                    color = toneInk(part.tone),
                )
            }
        }
    }
}

/** A box with a heading — "What the voucher will say", the disposal legs. */
@Composable
internal fun AssetPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape,
        color = MaterialTheme.appColors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.appColors.border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = AppFontWeight.Bold,
            )
            content()
        }
    }
}

/**
 * A label at one end and a figure at the other, ruled off underneath — the
 * shape every list in this module takes: a voucher line, a leg, a year charged,
 * a handover, a visit.
 */
@Composable
internal fun AssetLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sublabel: String = "",
    valueTone: AssetTone = AssetTone.Plain,
    strong: Boolean = false,
    divider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (strong) AppFontWeight.Bold else AppFontWeight.Normal,
                    color = if (strong) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.appColors.textMuted
                    },
                )
                if (sublabel.isNotBlank()) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textMuted,
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (strong) AppFontWeight.Bold else AppFontWeight.Normal,
                color = toneInk(valueTone),
                textAlign = TextAlign.End,
            )
        }
        if (divider) HorizontalDivider(color = MaterialTheme.appColors.border)
    }
}

/**
 * One of a small set of answers, chosen by tapping. Used for the count's
 * There / Damaged / Not there, and for the section switch on the care screen.
 * Tapping the chosen one again is the caller's business — the count uses it to
 * take a tick back.
 */
@Composable
internal fun AssetChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AssetTone = AssetTone.Info,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = toneInk(tone),
            selectedLabelColor = MaterialTheme.appColors.onAction,
        ),
        modifier = modifier,
    )
}

/** A row of chips that scrolls sideways rather than wrapping under a table. */
@Composable
internal fun AssetChoiceRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The whole screen while the first read is in flight. */
@Composable
internal fun AssetLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/**
 * How a form tells the list behind it that something landed.
 *
 * The message is the SERVER'S sentence, carried back rather than reinvented:
 * where a rate has been edited on a category with years already charged the
 * server answers in a paragraph, and that paragraph is the answer to the
 * question the person editing was really asking.
 */
internal const val ASSET_SAVED_KEY = "assetSaved"

/** Called by a form that saved, just before it goes back. */
internal fun androidx.navigation.NavHostController.reportAssetSaved(message: String) {
    previousBackStackEntry?.savedStateHandle?.set(ASSET_SAVED_KEY, message)
}

/** Reloads a list when a form it opened comes back having saved. */
@Composable
internal fun OnAssetSaved(
    navController: androidx.navigation.NavHostController,
    onSaved: (String) -> Unit,
) {
    val handle = navController.currentBackStackEntry?.savedStateHandle
    val flow = remember(handle) {
        handle?.getStateFlow(ASSET_SAVED_KEY, "")
            ?: kotlinx.coroutines.flow.MutableStateFlow("")
    }
    val signal by flow.collectAsStateWithLifecycle()
    LaunchedEffect(signal) {
        if (signal.isNotEmpty()) {
            handle?.set(ASSET_SAVED_KEY, "")
            onSaved(signal)
        }
    }
}

/** A refusal, in the server's own words, with a way to ask again. */
@Composable
internal fun AssetError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        LinkButton(text = "Retry", onClick = onRetry)
    }
}
