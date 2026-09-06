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
    /**
     * Generic dropdown loaded once from [CrudField.sourcePath] — a GET whose
     * foundData rows carry id/name (or value/label). For the option lists no
     * dedicated kind exists for (labour categories, product units).
     */
    DDL,
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
    /** [CrudFieldKind.DDL] only: the GET path its options come from. */
    val sourcePath: String? = null,
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

/** The web forms' Active switch, as the dropdown this engine renders. */
private val ACTIVE_INACTIVE = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
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
        // ---- Labour Items (not HRM, but the same engine) ----
        // The web's LabourCategoryAdd/LabourItemAdd (react b1cfc84): one page
        // for New and Edit, prefilled from the list row — the API has no edit
        // endpoint, exactly the FROM_LIST setup tabs' shape. Duplicate names
        // and refused deletes come back as success:false at HTTP 422 with the
        // reason. Units come from the product unit list rather than a second
        // list of the same units.
        HrmCrudSpec(
            key = "labourCategories",
            title = "Labour Category",
            anyOf = listOf("labour.category.edit"),
            listPath = "labour-setup/categories",
            storePath = "labour-setup/categories/store",
            updatePath = "labour-setup/categories/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("name", "Category Name", CrudFieldKind.TEXT, required = true),
                CrudField("description", "Category Description", CrudFieldKind.TEXT),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "labourItems",
            title = "Labour Item",
            anyOf = listOf("labour.item.edit"),
            listPath = "labour-setup/items",
            storePath = "labour-setup/items/store",
            updatePath = "labour-setup/items/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                // Category first, like the web form — the item belongs to it.
                CrudField(
                    "lab_cat_id", "Category", CrudFieldKind.DDL, required = true,
                    sourcePath = "labour-setup/categories/ddl",
                ),
                CrudField("name", "Item Name", CrudFieldKind.TEXT, required = true),
                CrudField("description", "Item Description", CrudFieldKind.TEXT),
                CrudField(
                    "unit_id", "Unit", CrudFieldKind.DDL, required = true,
                    sourcePath = "product/unit/ddl",
                ),
                CrudField("purchase_price", "Rate", CrudFieldKind.NUMBER),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),

        // ---- Hotel setup ----------------------------------------------------
        // The master data the booking screens hang off. Kept on this engine
        // rather than a hotel one of its own: the four forms are plain fields
        // over plain endpoints, which is exactly what it is for.
        HrmCrudSpec(
            key = "hotelBuildings",
            title = "Building",
            anyOf = listOf("hotel.building.view"),
            listPath = "hotel-setup/buildings",
            storePath = "hotel-setup/buildings/store",
            updatePath = "hotel-setup/buildings/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hotel-setup/buildings/edit",
            fields = listOf(
                CrudField("name", "Building Name", CrudFieldKind.TEXT, required = true),
                CrudField("code", "Code", CrudFieldKind.TEXT),
                CrudField("address", "Address", CrudFieldKind.TEXT),
                CrudField("notes", "Notes", CrudFieldKind.TEXT),
                CrudField("sort_order", "Sort Order", CrudFieldKind.NUMBER, default = "1"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "hotelFloors",
            title = "Floor",
            anyOf = listOf("hotel.floor.view"),
            listPath = "hotel-setup/floors",
            storePath = "hotel-setup/floors/store",
            updatePath = "hotel-setup/floors/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hotel-setup/floors/edit",
            fields = listOf(
                // The building a floor sits in cannot be changed after the fact
                // — the update endpoint does not accept it — but it has to be
                // asked on the way in, so the field stands and the server
                // simply ignores it on an edit.
                CrudField(
                    "building_id", "Building", CrudFieldKind.DDL, required = true,
                    sourcePath = "hotel-setup/buildings/ddl",
                ),
                CrudField("name", "Floor Name", CrudFieldKind.TEXT, required = true),
                CrudField("floor_no", "Floor Number", CrudFieldKind.NUMBER),
                CrudField("notes", "Notes", CrudFieldKind.TEXT),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "hotelRoomTypes",
            title = "Room Type",
            anyOf = listOf("hotel.room.type.view"),
            listPath = "hotel-setup/room-types",
            storePath = "hotel-setup/room-types/store",
            updatePath = "hotel-setup/room-types/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hotel-setup/room-types/edit",
            fields = listOf(
                CrudField("name", "Room Type Name", CrudFieldKind.TEXT, required = true),
                CrudField("code", "Code", CrudFieldKind.TEXT),
                // What a room of this type is sold as. "Both" leaves the choice
                // to the booking; a dormitory is sold by the seat.
                CrudField(
                    "default_sale_mode", "Sold As", CrudFieldKind.CHOICE, default = "whole",
                    choices = listOf(
                        SelectorOption("whole", "Whole room"),
                        SelectorOption("seat", "By the seat"),
                        SelectorOption("both", "Either"),
                    ),
                ),
                CrudField("capacity", "Capacity (guests)", CrudFieldKind.NUMBER),
                CrudField("default_seat_count", "Seats Per Room", CrudFieldKind.NUMBER),
                CrudField("default_whole_rent", "Whole Room Rent", CrudFieldKind.NUMBER),
                CrudField("default_seat_rent", "Per Seat Rent", CrudFieldKind.NUMBER),
                CrudField("description", "Description", CrudFieldKind.TEXT),
                CrudField("sort_order", "Sort Order", CrudFieldKind.NUMBER, default = "1"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "hotelFacilities",
            title = "Facility",
            anyOf = listOf("hotel.resource.view"),
            listPath = "hotel-setup/facilities",
            storePath = "hotel-setup/facilities/store",
            updatePath = "hotel-setup/facilities/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hotel-setup/facilities/edit",
            fields = listOf(
                CrudField("name", "Facility", CrudFieldKind.TEXT, required = true),
                // Slugged from the name when left blank; it is what the
                // standard-list seed matches on, so "Wi-Fi" and "wi_fi" are
                // one code, not two.
                CrudField("code", "Short Code", CrudFieldKind.TEXT),
                // "Either" first: most facilities are neither a bedroom's nor
                // a hall's alone.
                CrudField(
                    "applies_to", "Offered On", CrudFieldKind.CHOICE, default = "both",
                    choices = listOf(
                        SelectorOption("both", "Either — a room or a hall"),
                        SelectorOption("room", "Rooms only"),
                        SelectorOption("hall", "Halls only"),
                    ),
                ),
                CrudField("sort_order", "Order", CrudFieldKind.NUMBER, default = "0"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "hotelSlots",
            title = "Sitting",
            anyOf = listOf("hotel.resource.view"),
            listPath = "hotel-setup/slots",
            storePath = "hotel-setup/slots/store",
            updatePath = "hotel-setup/slots/update",
            updateStyle = CrudUpdateStyle.PATH_ID,
            editFetch = CrudEditFetch.ENDPOINT,
            editPath = "hotel-setup/slots/edit",
            fields = listOf(
                CrudField("name", "Sitting", CrudFieldKind.TEXT, required = true),
                // Lower-cased by the server; two of one name are two rows a
                // clerk cannot tell apart on a dropdown.
                CrudField("code", "Code", CrudFieldKind.TEXT, required = true),
                // An evening sitting by default — the one every community
                // centre sells first, and a form that opens on 00:00-00:00 is
                // one nobody can save.
                CrudField("start_time", "Starts", CrudFieldKind.TIME, required = true, default = "18:00"),
                CrudField("end_time", "Ends", CrudFieldKind.TIME, required = true, default = "23:00"),
                // A sitting that runs past midnight ends after it starts only
                // with this ticked; the server says so in its own words.
                CrudField(
                    "ends_next_day", "Ends Next Day", CrudFieldKind.CHOICE, default = "0",
                    choices = YES_NO,
                ),
                CrudField("sort_order", "Order", CrudFieldKind.NUMBER, default = "0"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
        HrmCrudSpec(
            key = "hotelChargeTypes",
            title = "Charge Type",
            anyOf = listOf("hotel.charge.type.view"),
            listPath = "hotel-setup/charge-types",
            // UPSERT on the code: there is no update endpoint, and store()
            // matches on (company, code) — so editing a shipped type writes
            // this company's own row over it, exactly as on the web.
            storePath = "hotel-setup/charge-types/store",
            updatePath = "hotel-setup/charge-types/store",
            updateStyle = CrudUpdateStyle.BODY_ID,
            editFetch = CrudEditFetch.FROM_LIST,
            fields = listOf(
                CrudField("name", "Charge Name", CrudFieldKind.TEXT, required = true),
                // Normalised server-side into a slug, because this string is
                // what a folio line and a tax rate are matched on.
                CrudField("code", "Code", CrudFieldKind.TEXT, required = true),
                CrudField("default_rate", "Default Rate", CrudFieldKind.NUMBER),
                // The income head a charge earns into is deliberately NOT
                // offered here. The server accepts only this company's income
                // heads, and the list of them rides inside the charge-type
                // response rather than a dropdown endpoint of its own — so a
                // picker here would have to offer every level-4 account and let
                // the server refuse most of them. It is an accounts decision
                // made once, on the web, where the heads are shown under their
                // groups. Left unset, a charge earns into Hotel Other Income,
                // which is what every install does today. An edit from here
                // keeps whatever the web nominated: the key is simply not sent.
                CrudField(
                    "by_hand", "Can Be Added By Hand", CrudFieldKind.CHOICE, default = "1",
                    choices = YES_NO,
                ),
                CrudField("sort_order", "Sort Order", CrudFieldKind.NUMBER, default = "50"),
                CrudField("status", "Status", CrudFieldKind.CHOICE, default = "1", choices = ACTIVE_INACTIVE),
            ),
        ),
    )

    private val byKey: Map<String, HrmCrudSpec> = all.associateBy { it.key }

    fun byKey(key: String?): HrmCrudSpec? = key?.let { byKey[it] }
}
