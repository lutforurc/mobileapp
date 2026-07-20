package com.example.cashbookbd.ui.hrm

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * Routes an HRM form key to its hand-built screen. List and report items never
 * land here — [HrmHomeScreen] sends those to the shared engines directly.
 */
@Composable
fun HrmFormScreen(
    hrmKey: String,
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (hrmKey) {
        "manualAttendance" -> ManualAttendanceScreen(navController, onLogout, modifier)
        "leaveApplications" -> LeaveApplicationsScreen(navController, onLogout, modifier)
        "attendanceSetup" -> AttendanceSetupScreen(navController, onLogout, modifier)
        "hrmMonthlyAttendance" -> MonthlyAttendanceScreen(navController, onLogout, modifier)
        "salaryGenerate" -> SalaryGenerateScreen(navController, onLogout, modifier)
        "bonusGenerate" -> BonusGenerateScreen(navController, onLogout, modifier)
        else -> AuthenticatedShell(
            title = "HRM",
            currentRoute = Routes.HRM,
            navController = navController,
            onLogout = onLogout,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "This screen is not available yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One entry of the Attendance Setup submenu: the setup list it opens. */
private data class SetupEntry(val listKey: String, val title: String, val subtitle: String)

/**
 * "Attendance Setup" — the web page's six tabs, as a submenu of read-only lists
 * (shift, policy, roster, weekly holiday, holiday, leave type).
 */
@Composable
fun AttendanceSetupScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        SetupEntry("hrmShifts", "Shifts", "Working shifts and timings"),
        SetupEntry("hrmPolicies", "Attendance Policies", "Rules per employment type"),
        SetupEntry("hrmRosters", "Shift Rosters", "Per-day shift assignments"),
        SetupEntry("hrmWeeklyHolidays", "Weekly Holidays", "Weekly off-day policies"),
        SetupEntry("hrmHolidaysList", "Holidays", "Holiday calendar entries"),
        SetupEntry("hrmLeaveTypes", "Leave Types", "Leave categories and quotas"),
    )

    AuthenticatedShell(
        title = "Attendance Setup",
        currentRoute = Routes.HRM,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.appListView(entry.listKey)) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = entry.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- Field pieces shared by the HRM form screens ----

/** The branch picker every HRM form shows first, in the shared field chrome. */
@Composable
internal fun HrmBranchDropdown(
    branches: List<BranchOption>,
    selected: BranchOption?,
    isLoading: Boolean,
    onSelected: (BranchOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        PickerField(
            label = "Select Branch",
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
                    onClick = {
                        onSelected(branch)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A date field opening the platform date picker, in the shared field chrome. */
@Composable
internal fun HrmDateField(
    label: String,
    value: SimpleDate,
    context: Context,
    onSelected: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerField(
        label = label,
        value = value.toDisplay(),
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onSelected(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
                },
                value.year,
                value.month - 1, // DatePicker months are 0-based.
                value.day,
            ).show()
        },
    )
}

/** A month + year field for salary/bonus months, in the shared field chrome. */
@Composable
internal fun HrmMonthField(
    label: String,
    value: com.example.cashbookbd.ui.reports.model.MonthYear,
    onSelected: (com.example.cashbookbd.ui.reports.model.MonthYear) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = value.toDisplay(),
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        var year by remember { mutableStateOf(value.year) }
        var month by remember { mutableStateOf(value.month) }
        val monthLabels = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                com.example.cashbookbd.ui.components.LinkButton(
                    text = "OK",
                    onClick = {
                        onSelected(
                            com.example.cashbookbd.ui.reports.model.MonthYear(year = year, month = month),
                        )
                        showDialog = false
                    },
                )
            },
            dismissButton = {
                com.example.cashbookbd.ui.components.LinkButton(
                    text = "Cancel",
                    onClick = { showDialog = false },
                )
            },
            title = { Text("Select Month & Year") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.cashbookbd.ui.components.LinkButton(text = "◀", onClick = { year-- })
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        com.example.cashbookbd.ui.components.LinkButton(text = "▶", onClick = { year++ })
                    }
                    for (rowStart in 0 until 12 step 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (offset in 0 until 3) {
                                val m = rowStart + offset + 1
                                Box(modifier = Modifier.weight(1f)) {
                                    if (m == month) {
                                        com.example.cashbookbd.ui.components.PrimaryButton(
                                            text = monthLabels[m - 1],
                                            onClick = { month = m },
                                            compact = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        com.example.cashbookbd.ui.components.SecondaryButton(
                                            text = monthLabels[m - 1],
                                            onClick = { month = m },
                                            compact = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

/** A time field ("HH:mm", blank allowed) opening the platform time picker. */
@Composable
internal fun HrmTimeField(
    label: String,
    value: String,
    context: Context,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerField(
        label = label,
        value = value,
        placeholder = "--:--",
        trailingIcon = Icons.Filled.ArrowDropDown,
        modifier = modifier,
        onClick = {
            val parts = value.split(":").mapNotNull { it.toIntOrNull() }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(String.format(java.util.Locale.US, "%02d:%02d", hour, minute))
                },
                parts.getOrNull(0) ?: 9,
                parts.getOrNull(1) ?: 0,
                true,
            ).show()
        },
    )
}
