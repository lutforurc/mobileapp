package com.example.cashbookbd.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.data.repository.GuarantorRow
import com.example.cashbookbd.data.repository.NomineeRow
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight

private val NOMINEE_STATUS = listOf(
    SelectorOption("active", "Active"),
    SelectorOption("inactive", "Inactive"),
)

/**
 * The web form's Guarantor panel: repeating rows of name / father's name /
 * mobile / national id / address, shared by Add and Edit.
 */
@Composable
fun GuarantorPanel(
    rows: List<GuarantorRow>,
    onChange: (Int, GuarantorRow) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Guarantors",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
        )
        rows.forEachIndexed { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Guarantor ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove guarantor",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    AppTextField(row.name, { onChange(index, row.copy(name = it)) }, "Name *", Modifier.fillMaxWidth())
                    AppTextField(row.fatherName, { onChange(index, row.copy(fatherName = it)) }, "Father's Name *", Modifier.fillMaxWidth())
                    AppTextField(
                        row.mobile, { onChange(index, row.copy(mobile = it)) }, "Mobile *",
                        Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone,
                    )
                    AppTextField(row.nationalId, { onChange(index, row.copy(nationalId = it)) }, "National ID", Modifier.fillMaxWidth())
                    AppTextField(row.address, { onChange(index, row.copy(address = it)) }, "Address *", Modifier.fillMaxWidth())
                }
            }
        }
        SecondaryButton(text = "+ Add Guarantor", onClick = onAdd, compact = true)
    }
}

/**
 * The web form's Nominee panel — the full row set, shared by Add and Edit.
 * A row keeps its server id (upsert) and its stored photo path when the
 * photo is untouched.
 */
@Composable
fun NomineePanel(
    rows: List<NomineeRow>,
    onChange: (Int, NomineeRow) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    showPhoto: Boolean,
    onPickPhoto: (Int) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Nominees",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = AppFontWeight.Bold,
        )
        rows.forEachIndexed { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nominee ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove nominee",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    AppTextField(row.name, { onChange(index, row.copy(name = it)) }, "Name *", Modifier.fillMaxWidth())
                    AppSelectDropdown(
                        label = "Relation",
                        options = RELATION_TYPES,
                        selected = RELATION_TYPES.firstOrNull { it.id == row.relation },
                        onSelected = { onChange(index, row.copy(relation = it.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(row.occupation, { onChange(index, row.copy(occupation = it)) }, "Occupation", Modifier.fillMaxWidth())
                    AppTextField(
                        value = row.dateOfBirth,
                        onValueChange = {},
                        label = "Date of Birth",
                        enabled = false,
                        trailingIcon = {
                            IconButton(onClick = {
                                pickCustomerDate(context, row.dateOfBirth) {
                                    onChange(index, row.copy(dateOfBirth = it))
                                }
                            }) {
                                Icon(
                                    Icons.Filled.DateRange,
                                    contentDescription = "Pick date of birth",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(row.motherName, { onChange(index, row.copy(motherName = it)) }, "Mother's Name", Modifier.fillMaxWidth())
                    AppTextField(
                        row.mobile, { onChange(index, row.copy(mobile = it)) }, "Mobile",
                        Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone,
                    )
                    AppTextField(row.presentAddress, { onChange(index, row.copy(presentAddress = it)) }, "Present Address", Modifier.fillMaxWidth())
                    AppTextField(row.permanentAddress, { onChange(index, row.copy(permanentAddress = it)) }, "Permanent Address", Modifier.fillMaxWidth())
                    AppTextField(row.nationalId, { onChange(index, row.copy(nationalId = it)) }, "National ID", Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(
                            row.sharePercentage, { onChange(index, row.copy(sharePercentage = it)) },
                            "Share %", Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                        )
                        AppTextField(
                            row.priorityOrder, { onChange(index, row.copy(priorityOrder = it)) },
                            "Priority", Modifier.weight(1f), keyboardType = KeyboardType.Number,
                        )
                    }
                    AppTextField(row.guardianName, { onChange(index, row.copy(guardianName = it)) }, "Guardian's Name", Modifier.fillMaxWidth())
                    AppTextField(
                        row.guardianMobile, { onChange(index, row.copy(guardianMobile = it)) }, "Guardian's Mobile",
                        Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone,
                    )
                    AppSelectDropdown(
                        label = "Status",
                        options = NOMINEE_STATUS,
                        selected = NOMINEE_STATUS.firstOrNull { it.id == row.status } ?: NOMINEE_STATUS.first(),
                        onSelected = { onChange(index, row.copy(status = it.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showPhoto) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    row.photo.startsWith("data:") -> "New photo attached"
                                    row.photo.isNotBlank() -> "Photo on file"
                                    else -> "No photo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = "Choose Photo",
                                onClick = { onPickPhoto(index) },
                                compact = true,
                            )
                        }
                    }
                    AppTextField(row.remarks, { onChange(index, row.copy(remarks = it)) }, "Remarks", Modifier.fillMaxWidth())
                }
            }
        }
        SecondaryButton(text = "+ Add Nominee", onClick = onAdd, compact = true)
    }
}
