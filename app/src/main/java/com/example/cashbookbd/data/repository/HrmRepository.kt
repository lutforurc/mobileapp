package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.HrmApiService
import com.example.cashbookbd.ui.hrm.model.AttendanceEntry
import com.example.cashbookbd.ui.hrm.model.AttendanceSummary
import com.example.cashbookbd.ui.hrm.model.BonusEmployee
import com.example.cashbookbd.ui.hrm.model.HrmOption
import com.example.cashbookbd.ui.hrm.model.LeaveApplication
import com.example.cashbookbd.ui.hrm.model.SalaryViewEmployee
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Backs every HRM form screen (manual attendance, leave applications, salary and
 * bonus generation), mirroring the web app's hrms calls exactly.
 *
 * The Laravel side wraps responses in the `foundData`/`notFound` envelope
 * (payload at `data.data`, paginated rows one level deeper), except the salary
 * and bonus generate routes which mix raw json shapes — every parse here is
 * defensive for that reason. A `success:false` body carries its reason in
 * `message`, sometimes under HTTP 201.
 */
class HrmRepository(
    private val api: HrmApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---- Lookups ----

    /** Active shifts for the attendance form's dropdown. */
    suspend fun getShiftOptions(): Resource<List<HrmOption>> =
        fetchOptions("hrms/attendance/shifts", mapOf("per_page" to "100", "status" to "1"))

    /** Leave types for the leave form's dropdown. */
    suspend fun getLeaveTypeOptions(): Resource<List<HrmOption>> =
        fetchOptions("hrms/attendance/leave-types", mapOf("per_page" to "100", "status" to "1"))

    private suspend fun fetchOptions(
        path: String,
        params: Map<String, String>,
    ): Resource<List<HrmOption>> = request {
        val response = api.get(path, params)
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val id = obj.text("id") ?: return@mapNotNull null
                HrmOption(id = id, name = obj.text("name").orEmpty())
            }
        }
    }

    // ---- Manual attendance ----

    /** Entries for one branch + date, newest form of the web's list panel. */
    suspend fun getAttendanceEntries(
        branchId: Long,
        date: String,
    ): Resource<List<AttendanceEntry>> = request {
        val response = api.get(
            "hrms/attendance/entries",
            mapOf(
                "branch_id" to branchId.toString(),
                "date_from" to date,
                "date_to" to date,
                "per_page" to "100",
            ),
        )
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                AttendanceEntry(
                    id = obj.text("id").orEmpty(),
                    date = obj.text("attendance_date").orEmpty(),
                    employeeName = obj.text("employee_name").orEmpty(),
                    employeeSerial = obj.text("employee_serial").orEmpty(),
                    shiftName = obj.text("shift_name").orEmpty(),
                    inTime = obj.text("in_time").orEmpty(),
                    outTime = obj.text("out_time").orEmpty(),
                    status = obj.text("status").orEmpty(),
                    approvalStatus = obj.text("approval_status").orEmpty(),
                    remarks = obj.text("remarks").orEmpty(),
                    overtimeMinutes = obj.number("overtime_minutes"),
                )
            }
        }
    }

    /**
     * Saves one manual attendance entry (the web's single-entry form). Blank
     * optionals are sent as null, matching the web's `'' → null` conversion.
     */
    suspend fun saveAttendance(
        branchId: Long,
        employeeId: Long,
        shiftId: Long?,
        date: String,
        inTime: String?,
        outTime: String?,
        status: String,
        remarks: String?,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("employee_id", employeeId)
            addNullable("shift_id", shiftId?.toString())
            addProperty("attendance_date", date)
            addNullable("in_time", inTime)
            addNullable("out_time", outTime)
            addProperty("status", status)
            addNullable("remarks", remarks)
            addProperty("update_existing", true)
        }
        val response = api.post("hrms/attendance/entries/store", body)
        parseMessage(response, fallback = "Attendance saved successfully")
    }

    /** Approves or rejects one entry (POST `.../approve/{id}`). */
    suspend fun approveAttendance(
        entryId: String,
        approve: Boolean,
        remarks: String?,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("approval_status", if (approve) "approved" else "rejected")
            addNullable("remarks", remarks)
        }
        val response = api.post("hrms/attendance/entries/approve/$entryId", body)
        parseMessage(response, fallback = "Attendance approval updated")
    }

    // ---- Leave applications ----

    suspend fun getLeaveApplications(
        branchId: Long,
        dateFrom: String,
        dateTo: String,
    ): Resource<List<LeaveApplication>> = request {
        val response = api.get(
            "hrms/attendance/leave-applications",
            mapOf(
                "branch_id" to branchId.toString(),
                "date_from" to dateFrom,
                "date_to" to dateTo,
                "per_page" to "100",
            ),
        )
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                LeaveApplication(
                    id = obj.text("id").orEmpty(),
                    fromDate = obj.text("from_date").orEmpty(),
                    toDate = obj.text("to_date").orEmpty(),
                    employeeName = obj.text("employee_name").orEmpty(),
                    leaveTypeName = obj.text("leave_type_name").orEmpty(),
                    requestedDays = obj.text("requested_days").orEmpty(),
                    approvalStatus = obj.text("approval_status").orEmpty(),
                    reason = obj.text("reason").orEmpty(),
                )
            }
        }
    }

    suspend fun saveLeaveApplication(
        branchId: Long,
        employeeId: Long,
        leaveTypeId: String,
        fromDate: String,
        toDate: String,
        reason: String?,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("employee_id", employeeId)
            addProperty("leave_type_id", leaveTypeId)
            addProperty("from_date", fromDate)
            addProperty("to_date", toDate)
            addNullable("reason", reason)
        }
        val response = api.post("hrms/attendance/leave-applications/store", body)
        parseMessage(response, fallback = "Leave application saved")
    }

    suspend fun approveLeave(
        applicationId: String,
        approve: Boolean,
        remarks: String?,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("approval_status", if (approve) "approved" else "rejected")
            addNullable("remarks", remarks)
        }
        val response = api.post("hrms/attendance/leave-applications/approve/$applicationId", body)
        parseMessage(response, fallback = "Leave approval updated")
    }

    // ---- Salary generate ----

    /** Unpaid, eligible employees for the month (the web's Search step). */
    suspend fun salaryView(
        branchId: Long,
        monthId: String,
        sheetType: String,
    ): Resource<List<SalaryViewEmployee>> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("month_id", monthId)
            addProperty("salary_sheet_type", sheetType)
            if (sheetType == "monthly") {
                addProperty("employment_type", "monthly")
            } else {
                addProperty("overtime_eligible", 1)
            }
            add("level_ids", JsonArray())
        }
        val response = api.post("hrms/salary-view", body)
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val id = obj.text("id")?.toLongOrNull() ?: return@mapNotNull null
                SalaryViewEmployee(
                    id = id,
                    name = obj.text("name").orEmpty(),
                    designationName = obj.text("designation_name").orEmpty(),
                    employmentType = obj.text("employment_type").orEmpty(),
                    basicSalary = obj.number("basic_salary"),
                    othersAllowance = obj.number("others_allowance"),
                    loanBalance = obj.number("loan_balance"),
                    othersDeduction = obj.number("others_deduction"),
                    otRate = obj.number("ot_rate"),
                )
            }
        }
    }

    /**
     * Per-employee monthly attendance summary, used to prorate the salary rows
     * exactly like the web (working days = payable days, capped to the month).
     */
    suspend fun monthlySummary(
        branchId: Long,
        month: Int,
        year: Int,
        monthlyOnly: Boolean,
    ): Resource<Map<Long, AttendanceSummary>> = request {
        val params = buildMap {
            put("branch_id", branchId.toString())
            put("month", month.toString())
            put("year", year.toString())
            if (monthlyOnly) put("employment_type", "monthly")
        }
        val response = api.get("hrms/attendance/monthly-summary", params)
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val employeeId = obj.text("employee_id")?.toLongOrNull() ?: return@mapNotNull null
                employeeId to AttendanceSummary(
                    payableDays = obj.numberOrNull("payable_days"),
                    deductionDays = obj.number("deduction_days"),
                    absentDays = obj.number("absent_days"),
                    unpaidLeaveDays = obj.number("unpaid_leave_days"),
                    halfDays = obj.number("half_days"),
                    lateCount = obj.number("late_count"),
                    lateDeductionDays = obj.number("late_deduction_days"),
                    earlyOutCount = obj.number("early_out_count"),
                    earlyOutDeductionDays = obj.number("early_out_deduction_days"),
                    overtimeMinutes = obj.number("overtime_minutes"),
                    overtimeAmount = obj.number("overtime_amount"),
                )
            }.toMap()
        }
    }

    /**
     * Generates the salary sheet. The `employees` array must carry the same
     * client-computed rows the web sends — the server trusts them as given.
     */
    suspend fun salaryGenerate(
        branchId: Long,
        monthId: String,
        sheetType: String,
        employees: JsonArray,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("month_id", monthId)
            addProperty("salary_sheet_type", sheetType)
            add("level_ids", JsonArray())
            add("employees", employees)
        }
        val response = api.post("hrms/salary-generate", body)
        parseMessage(response, fallback = "Salary generated successfully")
    }

    // ---- Festival bonus ----

    suspend fun bonusView(
        branchId: Long,
        monthId: String,
        bonusTitle: String,
    ): Resource<List<BonusEmployee>> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("month_id", monthId)
            addProperty("bonus_title", bonusTitle)
            add("level_ids", JsonArray())
        }
        val response = api.post("hrms/festival-bonus-view", body)
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val id = obj.text("id")?.toLongOrNull() ?: return@mapNotNull null
                BonusEmployee(
                    id = id,
                    name = obj.text("name").orEmpty(),
                    designationName = obj.text("designation_name").orEmpty(),
                    basicSalary = obj.number("basic_salary"),
                )
            }
        }
    }

    suspend fun bonusGenerate(
        branchId: Long,
        monthId: String,
        bonusTitle: String,
        bonusPercent: Double,
        employees: JsonArray,
    ): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("month_id", monthId)
            addProperty("bonus_title", bonusTitle)
            addProperty("bonus_percent", bonusPercent)
            add("level_ids", JsonArray())
            add("employees", employees)
        }
        val response = api.post("hrms/festival-bonus-generate", body)
        parseMessage(response, fallback = "Festival bonus generated successfully")
    }

    // ---- Shared plumbing ----

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403

        /** Row-array keys used by the hrms payloads. */
        val ROW_ARRAY_KEYS = listOf("rows", "data", "items", "list")
    }

    /** Runs [block] on IO with the shared error mapping every repository uses. */
    private suspend fun <T> request(block: suspend () -> Resource<T>): Resource<T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                when (e.code()) {
                    HTTP_UNAUTHORIZED -> Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> Resource.Error("You do not have permission for this action.")
                    else -> Resource.Error("Server error (${e.code()}). Please try again later.")
                }
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /**
     * Standard read parse: HTTP errors first, then the success flag (a blank
     * not-found message means "no rows"), then [transform] on the unwrapped
     * payload.
     */
    private fun <T> parseEnvelope(
        response: Response<JsonElement>,
        transform: (JsonElement) -> T,
    ): Resource<T> {
        checkHttp(response)?.let { return it }
        val root = response.body() ?: return Resource.Error("Invalid response from server.")
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            // The backend's notFound() helper marks an empty result set the same
            // way as a real failure ("No employees found", HTTP 200/201), so on a
            // read a false success simply means "no rows".
            if (success == false) return Resource.Success(transform(JsonArray()))
        }
        return Resource.Success(transform(unwrap(root)))
    }

    /**
     * Write parse: the salary/bonus routes mix `foundData` envelopes with raw
     * `{success|error, message}` bodies and meaningful non-2xx statuses, so read
     * the message from wherever it is and let the HTTP code decide the outcome.
     */
    private fun parseMessage(response: Response<JsonElement>, fallback: String): Resource<String> {
        val root = response.body() ?: response.errorBody()?.charStream()?.let { reader ->
            runCatching { com.google.gson.JsonParser.parseReader(reader) }.getOrNull()
        }
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
        val message = obj?.text("message")
            ?: obj?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            ?: obj?.getAsJsonObject("error")?.text("message")

        checkHttp(response)?.let { httpError ->
            // Prefer the server's own reason (e.g. "Salary already generated…").
            return if (!message.isNullOrBlank()) Resource.Error(message) else httpError
        }

        val success = obj?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        return if (success == false) {
            Resource.Error(message ?: "Something went wrong. Please try again.")
        } else {
            Resource.Success(message?.takeIf { it.isNotBlank() } ?: fallback)
        }
    }

    /** Maps 401/403/5xx to a [Resource.Error]; null when the status is fine. */
    private fun checkHttp(response: Response<JsonElement>): Resource.Error? = when {
        response.code() == HTTP_UNAUTHORIZED -> Resource.Error(
            "Your session has expired. Please log in again.",
            isUnauthorized = true,
        )
        response.code() == HTTP_FORBIDDEN ->
            Resource.Error("You do not have permission for this action.")
        !response.isSuccessful ->
            Resource.Error("Server error (${response.code()}). Please try again later.")
        else -> null
    }

    /** Peels the `data` / `data.data` envelope produced by the backend helpers. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** The row array of [payload]: itself, or under a known key (paginators too). */
    private fun rowsOf(payload: JsonElement): List<JsonElement> {
        if (payload.isJsonArray) return payload.asJsonArray.toList()
        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            for (key in ROW_ARRAY_KEYS) {
                val value = obj.get(key)?.takeUnless { it.isJsonNull }
                if (value != null && value.isJsonArray) return value.asJsonArray.toList()
            }
        }
        return emptyList()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.number(key: String): Double =
        text(key)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.numberOrNull(key: String): Double? =
        text(key)?.replace(",", "")?.toDoubleOrNull()

    private fun JsonObject.addNullable(key: String, value: String?) {
        if (value.isNullOrBlank()) add(key, com.google.gson.JsonNull.INSTANCE)
        else addProperty(key, value)
    }
}
