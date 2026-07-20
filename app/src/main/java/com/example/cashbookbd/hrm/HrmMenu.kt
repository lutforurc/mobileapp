package com.example.cashbookbd.hrm

import com.example.cashbookbd.session.MenuPermissions
import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the HRM menu, mirroring the web sidebar's "HRM" group. Item keys
 * double as registry keys: a key found in [com.example.cashbookbd.applist.AppLists]
 * opens that list, one found in [com.example.cashbookbd.report.ReportMenu] opens
 * that report, and the rest route to the HRM form screens.
 */
data class HrmItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/** Any of these opens the HRM attendance pages, mirroring the web sidebar. */
private val ATTENDANCE_ANY = listOf("attendance.view", "employee.view")

/** The HRM menu registry and its permission rules. */
object HrmMenu {

    val all: List<HrmItem> = listOf(
        HrmItem("hrmEmployees", "Employees", listOf("employee.view")),
        HrmItem("hrmDesignationLevels", "Designation Levels", listOf("employee.view")),
        HrmItem("hrmDesignations", "Designations", listOf("employee.view")),
        HrmItem("manualAttendance", "Manual Attendance", ATTENDANCE_ANY),
        HrmItem("hrmAttendanceReport", "Attendance Report", ATTENDANCE_ANY),
        HrmItem("hrmAuditHistory", "Audit History", ATTENDANCE_ANY),
        HrmItem("hrmOvertimeReport", "Overtime Report", ATTENDANCE_ANY),
        HrmItem("hrmMonthlyAttendance", "Monthly Attendance", ATTENDANCE_ANY),
        HrmItem("hrmAttendanceAlerts", "Attendance Alerts", ATTENDANCE_ANY),
        HrmItem("hrmEmployeeAttendance", "Employee Attendance", ATTENDANCE_ANY),
        HrmItem("hrmBranchAttendance", "Branch Attendance", ATTENDANCE_ANY),
        HrmItem("hrmHolidayCalendar", "Holiday Calendar", ATTENDANCE_ANY),
        HrmItem(
            "leaveApplications",
            "Leave Applications",
            listOf("leave.view", "attendance.view", "employee.view"),
        ),
        HrmItem("attendanceSetup", "Attendance Setup", ATTENDANCE_ANY),
        HrmItem("salaryGenerate", "Salary Generate", listOf("salary.generate")),
        HrmItem("bonusGenerate", "Bonus Generate", listOf("salary.generate")),
        HrmItem("hrmLoanBalance", "Loan Balance", listOf("hrm.loan.create")),
        HrmItem("hrmLoanLedger", "Loan Ledger", listOf("employee.loan.ledger.view")),
        HrmItem("hrmSalarySheet", "Salary Reports", listOf("salary.sheet.view")),
    )

    private val byKey: Map<String, HrmItem> = all.associateBy { it.key }

    fun byKey(key: String?): HrmItem? = key?.let { byKey[it] }

    /** True when the user can see the HRM parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        MenuPermissions.hasMenu(permissions, "hrm")

    /** HRM entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<HrmItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
