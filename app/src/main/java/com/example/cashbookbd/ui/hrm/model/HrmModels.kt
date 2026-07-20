package com.example.cashbookbd.ui.hrm.model

/** One id + name pair from an hrms lookup (shift, leave type). */
data class HrmOption(
    val id: String,
    val name: String,
)

/** One manual-attendance entry row (branch + date list under the form). */
data class AttendanceEntry(
    val id: String,
    val date: String,
    val employeeName: String,
    val employeeSerial: String,
    val employmentType: String,
    val shiftName: String,
    val inTime: String,
    val outTime: String,
    val status: String,
    val approvalStatus: String,
    val remarks: String,
    val overtimeMinutes: Double,
) {
    val isPendingApproval: Boolean get() = approvalStatus.equals("pending", ignoreCase = true)

    val isApproved: Boolean get() = approvalStatus.equals("approved", ignoreCase = true)
}

/** One leave application row. */
data class LeaveApplication(
    val id: String,
    val fromDate: String,
    val toDate: String,
    val employeeName: String,
    val leaveTypeName: String,
    val requestedDays: String,
    val approvalStatus: String,
    val reason: String,
) {
    val isPendingApproval: Boolean get() = approvalStatus.equals("pending", ignoreCase = true)
}

/** One eligible employee as returned by `hrms/salary-view`. */
data class SalaryViewEmployee(
    val id: Long,
    val name: String,
    val designationName: String,
    val employmentType: String,
    val basicSalary: Double,
    val othersAllowance: Double,
    val loanBalance: Double,
    val othersDeduction: Double,
    val otRate: Double,
)

/** One employee's month figures from `hrms/attendance/monthly-summary`. */
data class AttendanceSummary(
    /** Null when the server didn't send it — the caller derives from deduction. */
    val payableDays: Double?,
    val deductionDays: Double,
    val absentDays: Double,
    val unpaidLeaveDays: Double,
    val halfDays: Double,
    val lateCount: Double,
    val lateDeductionDays: Double,
    val earlyOutCount: Double,
    val earlyOutDeductionDays: Double,
    val overtimeMinutes: Double,
    val overtimeAmount: Double,
)

/** One eligible employee as returned by `hrms/festival-bonus-view`. */
data class BonusEmployee(
    val id: Long,
    val name: String,
    val designationName: String,
    val basicSalary: Double,
)

/** One employee's month totals from `hrms/attendance/monthly-summary` (with names). */
data class MonthlySummaryRow(
    val employeeId: Long,
    val employeeName: String,
    val employeeSerial: String,
    val presentDays: Double,
    val paidLeaveDays: Double,
    val unpaidLeaveDays: Double,
    val absentDays: Double,
    val lateCount: Double,
    val earlyOutCount: Double,
    val halfDays: Double,
    val payableDays: Double,
    val deductionDays: Double,
)

/** One (employee, day) attendance cell from the month's entry report. */
data class AttendanceDayRow(
    val employeeId: Long,
    val employeeName: String,
    val employeeSerial: String,
    /** Day of month, 1-based. */
    val day: Int,
    val status: String,
    val approvalStatus: String,
)

/**
 * One voucher-level row of the salary sheet list (`hrms/salary-sheet`). [raw]
 * keeps the server row untouched — the payment call posts it back whole, with
 * its hashed `main_trx.mtmId`, exactly as the web does.
 */
data class SalarySheetSummary(
    val serial: Int,
    /** Raw `payment_month` ("MMYYYY"). */
    val paymentMonth: String,
    /** Branch name from the row's main_trx relation (blank when own-branch). */
    val branchName: String,
    val employees: Int,
    val gross: Double,
    val net: Double,
    val loanDeduction: Double,
    val payment: Double,
    val raw: com.google.gson.JsonObject,
) {
    val due: Double get() = net - payment

    /** The web shows the Paid badge when nothing is due. */
    val isPaid: Boolean get() = due <= 0.005
}

/** One employee line of a salary sheet's detail (the web's print view). */
data class SalaryDetailRow(
    val sl: String,
    val name: String,
    val designation: String,
    val monthDays: String,
    val workingDays: String,
    val monthlyBasic: Double,
    val salary: Double,
    val mobileAllowance: Double,
    val total: Double,
    val loanDeduction: Double,
    val attendanceDeduction: Double,
    val netSalary: Double,
    val payment: Double,
    val vrNo: String,
)

/** A salary sheet's detail: header info + per-employee rows + grand totals. */
data class SalarySheetDetail(
    val vrNo: String,
    val vrDate: String,
    /** Raw month id ("MM-YYYY" / "MMYYYY") from the meta. */
    val monthId: String,
    val levelName: String,
    val rows: List<SalaryDetailRow>,
) {
    val totalMonthlyBasic: Double get() = rows.sumOf { it.monthlyBasic }
    val totalSalary: Double get() = rows.sumOf { it.salary }
    val totalMobile: Double get() = rows.sumOf { it.mobileAllowance }
    val totalGross: Double get() = rows.sumOf { it.total }
    val totalLoan: Double get() = rows.sumOf { it.loanDeduction }
    val totalAttendance: Double get() = rows.sumOf { it.attendanceDeduction }
    val totalNet: Double get() = rows.sumOf { it.netSalary }
    val totalPayment: Double get() = rows.sumOf { it.payment }
}

/** The employee form's lookup bundle from `hrms/employee/settings`. */
data class EmployeeSettings(
    val branches: List<HrmOption>,
    val designations: List<HrmOption>,
    val sexes: List<HrmOption>,
)

/**
 * One employee's editable record from `hrms/employee/edit/{id}`. Everything is
 * kept as the raw string so the form can round-trip values it doesn't touch.
 */
data class EmployeeDetail(
    val name: String,
    val fatherName: String,
    val nid: String,
    val mobile: String,
    val dateOfBirth: String,
    val joiningDate: String,
    val designationId: String,
    val qualification: String,
    val status: String,
    val sexId: String,
    val projectId: String,
    /** The employee's own branch name (its id may be outside the user's list). */
    val branchName: String,
    val presentAddress: String,
    val permanentAddress: String,
    val basicSalary: String,
    val houseRent: String,
    val medicalAllowance: String,
    val othersAllowance: String,
    val loanDeduction: String,
    val othersDeduction: String,
    val salaryPayable: String,
    val employmentType: String,
    val attendancePolicyId: String,
    val defaultShiftId: String,
    val overtimeEligible: String,
    val dailyWage: String,
    val otRate: String,
    val standardWorkMinutes: String,
    val employeeSerial: String,
)
