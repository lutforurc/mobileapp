package com.example.cashbookbd.realestate

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** What a Real Estate CRUD form field is and how it renders/serializes. */
enum class ReCrudFieldKind {
    TEXT,
    NUMBER,
    /** yyyy-MM-dd, optional unless required. */
    DATE,
    /** Static single-select from [ReCrudField.choices]. */
    STATIC_SELECT,
    /** Protected-branch dropdown (`ReportRepository.getBranches`), first preselected. */
    BRANCH,
    /** Server-side typeahead against [ReCrudField.source]'s DDL endpoint. */
    ASYNC_PICKER,
}

/**
 * Where an [ReCrudFieldKind.ASYNC_PICKER] searches. Every real-estate DDL takes
 * `?q=` and answers `{success, data: {data: [{value,label,label_2,…}]}}`;
 * [CUSTOMER] is the shared chart-of-accounts ledger search instead ([path] null,
 * routed through `LedgerRepository.searchLedgers`).
 */
enum class ReDdlSource(val path: String?) {
    AREA("real-estate/project-areas/ddl"),
    PROJECT("real-estate/projects/ddl"),
    BUILDING("real-estate/buildings/ddl"),
    FLAT("real-estate/flats/ddl"),
    CUSTOMER(null),
}

/** One field of a Real Estate CRUD form, in web-form order. */
data class ReCrudField(
    val key: String,
    val label: String,
    val kind: ReCrudFieldKind,
    val required: Boolean = false,
    /** Initial value (choice id, number text, …), as on the web form. */
    val default: String = "",
    val placeholder: String = "",
    /** Server-side `max:` rule, enforced while typing. */
    val maxLength: Int? = null,
    /** STATIC_SELECT only. */
    val choices: List<SelectorOption> = emptyList(),
    /** ASYNC_PICKER only. */
    val source: ReDdlSource? = null,
    /**
     * ASYNC_PICKER only: dotted path in the edit record that names the selected
     * row (e.g. `"area.name"` for the loaded relation, `"customer_name"` for a
     * computed top-level column), so the picker prefills a readable label.
     */
    val labelPath: String? = null,
)

/**
 * One add/edit form over a Real Estate master-data list, mirroring the web's
 * pages exactly: same fields, same payload keys, same endpoints.
 *
 * All ids are raw ints, dates are yyyy-MM-dd, and the backend answers its
 * notFound envelope (`{"success":false,"message"}`) at HTTP 200/201 — the
 * repository branches on the `success` field, not the status code.
 */
data class ReCrudSpec(
    /** Route key — the list's +Add/pencil name this form.  */
    val key: String,
    /** Singular title — "Unit" renders as "Add Unit" / "Edit Unit". */
    val title: String,
    /** POST target on create (and on update too when [updatePath] is null). */
    val storePath: String,
    /**
     * POST `updatePath/{id}` on update; null marks an UPSERT endpoint (charge
     * types) where update also POSTs [storePath] with `id` in the body.
     */
    val updatePath: String?,
    /** GET `editPath/{id}` — the record arrives at `data.data`. */
    val editPath: String,
    val fields: List<ReCrudField>,
    /**
     * Create only: stay on the form after a successful save (snackbar in place
     * instead of popping back) and reset every field except [keepOnCreate] —
     * rapid multi-unit entry. Updates always pop back.
     */
    val stayAfterCreate: Boolean = false,
    /** Field keys whose value survives the [stayAfterCreate] reset. */
    val keepOnCreate: Set<String> = emptySet(),
)

/** The shared status column: sent as an int-string, server requires in:0,1,2,3. */
private val STATUS_OPTIONS = listOf(
    SelectorOption("1", "Active"),
    SelectorOption("0", "Inactive"),
    SelectorOption("2", "Pending"),
    SelectorOption("3", "Deleted"),
)

private fun statusField(required: Boolean = false) = ReCrudField(
    "status", "Status", ReCrudFieldKind.STATIC_SELECT,
    required = required, default = "1", choices = STATUS_OPTIONS,
)

/** Registry of the Real Estate master-data add/edit forms. */
object RealEstateCrudForms {

    val all: List<ReCrudSpec> = listOf(
        ReCrudSpec(
            key = "reAreas",
            title = "Location",
            storePath = "real-estate/area/store",
            updatePath = "real-estate/area/update",
            editPath = "real-estate/area/edit",
            fields = listOf(
                ReCrudField("name", "Location Name", ReCrudFieldKind.TEXT, required = true, maxLength = 255),
                ReCrudField("latitude", "Latitude", ReCrudFieldKind.TEXT),
                ReCrudField("longitude", "Longitude", ReCrudFieldKind.TEXT),
                ReCrudField("description", "Description", ReCrudFieldKind.TEXT),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                statusField(required = true),
            ),
        ),
        ReCrudSpec(
            key = "reProjects",
            title = "Project",
            storePath = "real-estate/projects",
            updatePath = "real-estate/projects/update",
            editPath = "real-estate/projects/edit",
            fields = listOf(
                ReCrudField("branch_id", "Branch", ReCrudFieldKind.BRANCH, required = true),
                ReCrudField(
                    "area_id", "Location", ReCrudFieldKind.ASYNC_PICKER, required = true,
                    source = ReDdlSource.AREA, labelPath = "area.name",
                ),
                ReCrudField(
                    "customer_id", "Land Owner", ReCrudFieldKind.ASYNC_PICKER,
                    source = ReDdlSource.CUSTOMER, labelPath = "customer_name",
                ),
                ReCrudField("name", "Project Name", ReCrudFieldKind.TEXT, required = true),
                ReCrudField("location_details", "Location Details", ReCrudFieldKind.TEXT),
                ReCrudField("area_sqft", "Area (sqft)", ReCrudFieldKind.NUMBER, required = true),
                ReCrudField("purchase_price", "Purchase Price", ReCrudFieldKind.NUMBER),
                ReCrudField("sale_price", "Sale Price", ReCrudFieldKind.NUMBER),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                ReCrudField("purchase_date", "Purchase Date", ReCrudFieldKind.DATE, required = true),
                ReCrudField("sale_date", "Sale Date", ReCrudFieldKind.DATE),
                statusField(),
            ),
        ),
        ReCrudSpec(
            key = "reBuildings",
            title = "Building",
            storePath = "real-estate/buildings",
            updatePath = "real-estate/buildings/update",
            editPath = "real-estate/buildings/edit",
            fields = listOf(
                ReCrudField(
                    "project_id", "Project", ReCrudFieldKind.ASYNC_PICKER, required = true,
                    source = ReDdlSource.PROJECT, labelPath = "project.name",
                ),
                ReCrudField("name", "Building Name", ReCrudFieldKind.TEXT, required = true, maxLength = 255),
                ReCrudField("floors_count", "Floors Count", ReCrudFieldKind.NUMBER, required = true),
                ReCrudField("construction_cost", "Construction Cost", ReCrudFieldKind.NUMBER),
                ReCrudField("total_cost", "Total Cost", ReCrudFieldKind.NUMBER),
                ReCrudField("sale_price", "Sale Price", ReCrudFieldKind.NUMBER),
                ReCrudField("start_date", "Start Date", ReCrudFieldKind.DATE, required = true),
                ReCrudField("completion_date", "Completion Date", ReCrudFieldKind.DATE),
                ReCrudField("sale_date", "Sale Date", ReCrudFieldKind.DATE),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                statusField(),
            ),
        ),
        ReCrudSpec(
            key = "reFloors",
            title = "Floor",
            storePath = "real-estate/flats",
            updatePath = "real-estate/flats/update",
            editPath = "real-estate/flats/edit",
            fields = listOf(
                ReCrudField(
                    "building_id", "Building", ReCrudFieldKind.ASYNC_PICKER, required = true,
                    source = ReDdlSource.BUILDING, labelPath = "building.name",
                ),
                ReCrudField(
                    "flat_name", "Floor Name", ReCrudFieldKind.TEXT, required = true,
                    maxLength = 50, placeholder = "Floor # 01 / First Floor",
                ),
                ReCrudField("floor_no", "Floor No", ReCrudFieldKind.NUMBER, required = true),
                ReCrudField("total_units", "Total Units", ReCrudFieldKind.NUMBER, default = "1"),
                ReCrudField("allocated_cost", "Allocated Cost", ReCrudFieldKind.NUMBER, default = "0"),
                ReCrudField("sale_price", "Sale Price", ReCrudFieldKind.NUMBER),
                ReCrudField("sale_date", "Sale Date", ReCrudFieldKind.DATE),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                statusField(),
            ),
        ),
        ReCrudSpec(
            key = "reUnits",
            title = "Unit",
            storePath = "real-estate/units",
            updatePath = "real-estate/units/update",
            editPath = "real-estate/units/edit",
            // The web form keeps the chosen floor after each save so several
            // units of the same floor can be entered back to back.
            stayAfterCreate = true,
            keepOnCreate = setOf("flat_id"),
            fields = listOf(
                ReCrudField(
                    "flat_id", "Select Floor", ReCrudFieldKind.ASYNC_PICKER, required = true,
                    source = ReDdlSource.FLAT, labelPath = "flat.flat_name",
                ),
                ReCrudField(
                    "unit_no", "Unit No", ReCrudFieldKind.TEXT, required = true,
                    maxLength = 50, placeholder = "Unit A / 101",
                ),
                ReCrudField("size_sqft", "Size (sqft)", ReCrudFieldKind.NUMBER, required = true),
                ReCrudField("allocated_cost", "Allocated Cost", ReCrudFieldKind.NUMBER, default = "0"),
                ReCrudField("sale_price", "Unit Rate (per sqft)", ReCrudFieldKind.NUMBER),
                ReCrudField("sale_date", "Sale Date", ReCrudFieldKind.DATE),
                ReCrudField(
                    "unit_type", "Unit Type", ReCrudFieldKind.STATIC_SELECT,
                    required = true, default = "unit",
                    choices = listOf(
                        SelectorOption("unit", "Unit"),
                        SelectorOption("parking", "Parking"),
                    ),
                ),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                statusField(),
            ),
        ),
        ReCrudSpec(
            key = "reChargeTypes",
            title = "Charge Type",
            // UPSERT endpoint: update POSTs the same path with `id` in the body.
            storePath = "real-estate/units/unit-charge-types",
            updatePath = null,
            editPath = "real-estate/units/unit-charge-types/edit",
            fields = listOf(
                ReCrudField("name", "Charge Type Name", ReCrudFieldKind.TEXT, required = true, maxLength = 255),
                ReCrudField(
                    "effect", "Effect", ReCrudFieldKind.STATIC_SELECT,
                    required = true, default = "+",
                    choices = listOf(
                        SelectorOption("+", "(+) Add"),
                        SelectorOption("-", "(-) Subtract"),
                    ),
                ),
                ReCrudField("notes", "Notes", ReCrudFieldKind.TEXT),
                ReCrudField("sort_order", "Sort Order", ReCrudFieldKind.NUMBER, required = true, default = "1"),
                // List responses return this as boolean true/false; the edit
                // prefill normalizes either shape to the "1"/"0" ids here.
                ReCrudField(
                    "is_active", "Status", ReCrudFieldKind.STATIC_SELECT, default = "1",
                    choices = listOf(
                        SelectorOption("1", "Active"),
                        SelectorOption("0", "Inactive"),
                    ),
                ),
            ),
        ),
    )

    private val byKey: Map<String, ReCrudSpec> = all.associateBy { it.key }

    fun byKey(key: String?): ReCrudSpec? = key?.let { byKey[it] }
}
