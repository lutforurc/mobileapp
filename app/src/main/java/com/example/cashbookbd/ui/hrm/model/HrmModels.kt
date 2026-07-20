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
    val shiftName: String,
    val inTime: String,
    val outTime: String,
    val status: String,
    val approvalStatus: String,
    val remarks: String,
    val overtimeMinutes: Double,
) {
    val isPendingApproval: Boolean get() = approvalStatus.equals("pending", ignoreCase = true)
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
