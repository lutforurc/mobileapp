package com.example.cashbookbd.hrm

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** What a CRUD form field is and how it renders/serializes. */
enum class CrudFieldKind {
    TEXT,
    NUMBER,
    /** yyyy-MM-dd, optional unless required. */
    DATE,
    /** HH:mm. */
    TIME,
    /** Static single-select from [CrudField.choices]. */
    CHOICE,
    /** Protected-branch dropdown (optional "Select Branch" blank entry). */
    BRANCH,
    /** Shift dropdown (`hrms/attendance/shifts`). */
    SHIFT,
    /** Designation-level dropdown (`hrms/designation-levels/ddl`). */
    LEVEL,
    /** Employee typeahead (`hrms/employee/ddl/list`). */
    EMPLOYEE,
}

/** One field of a CRUD form, in web-form order. */
data class CrudField(
    val key: String,
    val label: String,
    val kind: CrudFieldKind,
    val required: Boolean = false,
    /** Initial value (choice id, number text, "HH:mm" …), as on the web form. */
    val default: String = "",
    val choices: List<SelectorOption> = emptyList(),
)

/** Where the update call carries the row id. */
enum class CrudUpdateStyle {
    /** POST `updatePath/{id}` (the attendance-setup endpoints). */
    PATH_ID,

    /** POST `updatePath` with `id` in the body (designations/levels). */
    BODY_ID,
}

/** How an edit form gets its current values. */
enum class CrudEditFetch {
    /** GET `editPath/{id}` (designations/levels have real edit endpoints). */
    ENDPOINT,

    /** Find the row in the list response — the setup tabs prefill from the row. */
    FROM_LIST,
}

/**
 * One add/edit form over an [com.example.cashbookbd.applist.AppLists] list,
 * mirroring the web's HRM pages exactly: same fields, same visible subset, same
 * payload keys. Values the web form keeps but doesn't show (e.g. a shift's
 * minimum_work_minutes) ride along via [hiddenDefaults] on create and are
 * round-tripped from the fetched row on edit.
 */
data class HrmCrudSpec(
    /** Matches the AppLists key, so the list's +Add/pencil route here. */
    val key: String,
    /** Singular title — "Shift" renders as "Add Shift" / "Edit Shift". */
    val title: String,
    val anyOf: List<String>,
    val listPath: String,
    val storePath: String,
    val updatePath: String,
    val updateStyle: CrudUpdateStyle,
    val editFetch: CrudEditFetch,
    /** ENDPOINT only: GET `editPath/{id}`. */
    val editPath: String? = null,
    val fields: List<CrudField>,
    /** Keys the web form holds but doesn't display, sent with these defaults. */
    val hiddenDefaults: Map<String, String> = emptyMap(),
)

private val YES_NO = listOf(
    SelectorOption("0", "No"),
    SelectorOption("1", "Yes"),
)

private val SETUP_ANY = listOf("attendance.view", "employee.view")

/** Registry of the HRM add/edit forms (only the ones the web actually has). */
object HrmCrudForms {

    val all: List<HrmCrudSpec> = listOf(
        HrmCrudSpec(
            key = "hrmDesignationLevels",
            title = "Designation Level",
            anyOf = listOf("employee.view"),
            listPath = "hrms/designation-levels/list",
            storePath = "hrms/designation-levels/store",
            updatePath = "hrms/designation-levels/update",
            updateStyle = CrudUpdateStyle.BODY_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hrms/designation-levels/edit",
            fields = listOf(
                CrudField("name", "Level Name", CrudFieldKind.TEXT, required = true),
                CrudField("description", "Description", CrudFieldKind.TEXT),
            ),
        ),
        HrmCrudSpec(
            key = "hrmDesignations",
            title = "Designation",
            anyOf = listOf("employee.view"),
            listPath = "hrms/designations/list",
            storePath = "hrms/designations/store",
            updatePath = "hrms/designations/update",
            updateStyle = CrudUpdateStyle.BODY_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hrms/designations/edit",
            fields = listOf(
                CrudField("level_id", "Designation Level", CrudFieldKind.LEVEL, required = true),
                CrudField("name", "Designation", CrudFieldKind.TEXT, required = true),
                CrudField("post_sequence", "Post Sequence", CrudFieldKind.NUMBER, required = true),
                CrudField("description", "Description", CrudFieldKind.TEXT),
            ),
        ),
        HrmCrudSpec(
            key = "hrmShifts",
            title = "Shift",
            anyOf = SETUP_ANY + "shift.view",
            listPath = "hrms/attendance/shifts",
            storePath = "hrms/attendance/shifts/store",
            updatePath = "hrms/attendance/shifts/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("name", "Shift Name", CrudFieldKind.TEXT, required = true),
                CrudField("code", "Code", CrudFieldKind.TEXT),
                CrudField("start_time", "Start Time", CrudFieldKind.TIME, required = true, default = "09:00"),
                CrudField("end_time", "End Time", CrudFieldKind.TIME, required = true, default = "17:00"),
                CrudField("grace_minutes", "Grace Minutes", CrudFieldKind.NUMBER, default = "15"),
                CrudField("half_day_minutes", "Half Day Minutes", CrudFieldKind.NUMBER, default = "240"),
                CrudField("early_out_minutes", "Early Out Minutes", CrudFieldKind.NUMBER, default = "60"),
                CrudField("is_night_shift", "Night Shift", CrudFieldKind.CHOICE, default = "0", choices = YES_NO),
            ),
            hiddenDefaults = mapOf(
                "minimum_work_minutes" to "240",
                "late_deduction_after_count" to "3",
                "early_out_deduction_after_count" to "3",
                "status" to "1",
            ),
        ),
        HrmCrudSpec(
            key = "hrmPolicies",
            title = "Attendance Policy",
            anyOf = SETUP_ANY,
            listPath = "hrms/attendance/policies",
            storePath = "hrms/attendance/policies/store",
            updatePath = "hrms/attendance/policies/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("name", "Policy Name", CrudFieldKind.TEXT, required = true),
                CrudField(
                    "employment_type", "Employee Type", CrudFieldKind.CHOICE,
                    required = true, default = "monthly",
                    choices = listOf(
                        SelectorOption("monthly", "Monthly Employee"),
                        SelectorOption("daily", "Daily Labour"),
                        SelectorOption("shifting", "Shift Based Employee"),
                    ),
                ),
                CrudField("branch_id", "Branch", CrudFieldKind.BRANCH),
                CrudField("default_shift_id", "Default Shift", CrudFieldKind.SHIFT),
                CrudField("standard_work_minutes", "Standard Work Minutes", CrudFieldKind.NUMBER, default = "480"),
                CrudField("minimum_work_minutes", "Minimum Work Minutes", CrudFieldKind.NUMBER, default = "240"),
                CrudField("half_day_minutes", "Half Day Minutes", CrudFieldKind.NUMBER, default = "240"),
                CrudField("grace_minutes", "Grace Minutes", CrudFieldKind.NUMBER, default = "15"),
                CrudField("early_out_minutes", "Early Out Minutes", CrudFieldKind.NUMBER, default = "60"),
                CrudField("overtime_enabled", "Overtime", CrudFieldKind.CHOICE, default = "0", choices = YES_NO),
                CrudField("overtime_after_minutes", "OT After Minutes", CrudFieldKind.NUMBER, default = "480"),
                CrudField("late_deduction_after_count", "Late Deduction Count", CrudFieldKind.NUMBER, default = "3"),
                CrudField("early_out_deduction_after_count", "Early Out Deduction Count", CrudFieldKind.NUMBER, default = "3"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
            ),
        ),
        HrmCrudSpec(
            key = "hrmRosters",
            title = "Shift Roster",
            anyOf = SETUP_ANY + "shift.view",
            listPath = "hrms/attendance/shift-rosters",
            storePath = "hrms/attendance/shift-rosters/store",
            updatePath = "hrms/attendance/shift-rosters/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("employee_id", "Employee", CrudFieldKind.EMPLOYEE, required = true),
                CrudField("branch_id", "Branch", CrudFieldKind.BRANCH),
                CrudField("shift_id", "Shift", CrudFieldKind.SHIFT, required = true),
                CrudField("duty_date", "Duty Date", CrudFieldKind.DATE, required = true),
                CrudField(
                    "status", "Status", CrudFieldKind.CHOICE, default = "scheduled",
                    choices = listOf(
                        SelectorOption("scheduled", "Scheduled"),
                        SelectorOption("completed", "Completed"),
                        SelectorOption("cancelled", "Cancelled"),
                    ),
                ),
                CrudField("remarks", "Remarks", CrudFieldKind.TEXT),
            ),
        ),
        HrmCrudSpec(
            key = "hrmWeeklyHolidays",
            title = "Weekly Holiday",
            anyOf = SETUP_ANY + "holiday.view",
            listPath = "hrms/attendance/weekly-holidays",
            storePath = "hrms/attendance/weekly-holidays/store",
            updatePath = "hrms/attendance/weekly-holidays/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("branch_id", "Branch", CrudFieldKind.BRANCH),
                CrudField(
                    "day_of_week", "Day", CrudFieldKind.CHOICE, required = true,
                    choices = listOf(
                        SelectorOption("0", "Sunday"),
                        SelectorOption("1", "Monday"),
                        SelectorOption("2", "Tuesday"),
                        SelectorOption("3", "Wednesday"),
                        SelectorOption("4", "Thursday"),
                        SelectorOption("5", "Friday"),
                        SelectorOption("6", "Saturday"),
                    ),
                ),
                CrudField("is_enabled", "Enabled", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
                CrudField("effective_from", "Effective From", CrudFieldKind.DATE),
                CrudField("effective_to", "Effective To", CrudFieldKind.DATE),
                CrudField("remarks", "Remarks", CrudFieldKind.TEXT),
            ),
        ),
        HrmCrudSpec(
            key = "hrmHolidaysList",
            title = "Holiday",
            anyOf = SETUP_ANY + "holiday.view",
            listPath = "hrms/attendance/holidays",
            storePath = "hrms/attendance/holidays/store",
            updatePath = "hrms/attendance/holidays/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("branch_id", "Branch", CrudFieldKind.BRANCH),
                CrudField("holiday_date", "Holiday Date", CrudFieldKind.DATE, required = true),
                CrudField("holiday_name", "Holiday Name", CrudFieldKind.TEXT, required = true),
                CrudField(
                    "holiday_type", "Holiday Type", CrudFieldKind.CHOICE,
                    required = true, default = "company",
                    choices = listOf(
                        SelectorOption("government", "Government"),
                        SelectorOption("festival", "Festival"),
                        SelectorOption("company", "Company"),
                        SelectorOption("optional", "Optional"),
                        SelectorOption("project", "Project"),
                        SelectorOption("weekly", "Weekly"),
                        SelectorOption("other", "Other"),
                    ),
                ),
                CrudField("is_paid", "Paid", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
                CrudField("is_optional", "Optional", CrudFieldKind.CHOICE, default = "0", choices = YES_NO),
                CrudField("remarks", "Remarks", CrudFieldKind.TEXT),
            ),
            hiddenDefaults = mapOf("status" to "1"),
        ),
        HrmCrudSpec(
            key = "hrmLeaveTypes",
            title = "Leave Type",
            anyOf = SETUP_ANY + "leave.view",
            listPath = "hrms/attendance/leave-types",
            storePath = "hrms/attendance/leave-types/store",
            updatePath = "hrms/attendance/leave-types/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("name", "Leave Type", CrudFieldKind.TEXT, required = true),
                CrudField("code", "Code", CrudFieldKind.TEXT, required = true),
                CrudField("yearly_quota", "Yearly Quota", CrudFieldKind.NUMBER, default = "0"),
                CrudField("sort_order", "Sort Order", CrudFieldKind.NUMBER, default = "0"),
                CrudField("is_paid", "Paid", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
                CrudField("allow_half_day", "Allow Half Day", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
                CrudField("allow_backdated", "Allow Backdated", CrudFieldKind.CHOICE, default = "1", choices = YES_NO),
                CrudField("requires_attachment", "Attachment", CrudFieldKind.CHOICE, default = "0", choices = YES_NO),
            ),
            hiddenDefaults = mapOf("status" to "1"),
        ),
    )

    private val byKey: Map<String, HrmCrudSpec> = all.associateBy { it.key }

    fun byKey(key: String?): HrmCrudSpec? = key?.let { byKey[it] }
}
