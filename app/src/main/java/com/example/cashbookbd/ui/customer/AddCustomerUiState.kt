package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** Party types, mirroring the web's ClientType constant. */
val CUSTOMER_TYPES = listOf(
    SelectorOption("1", "Customer"),
    SelectorOption("2", "Supplier"),
    SelectorOption("3", "Supplier & Customer"),
    SelectorOption("4", "Advance"),
)

/** The web's sexType constant — values travel as the lowercase ids. */
val CUSTOMER_SEX_OPTIONS = listOf(
    SelectorOption("male", "Male"),
    SelectorOption("female", "Female"),
    SelectorOption("other", "Other"),
)

data class AddCustomerUiState(
    /**
     * Customer by default — it is what nearly every contact added here is, and
     * the form opened with the one required field already needing a tap.
     * Changing it is still a tap away for the rarer types.
     */
    val type: SelectorOption? = CUSTOMER_TYPES.first(),
    val name: String = "",
    // Bangla name, gated on the branch's use_bangla column — the branch keeps
    // its ledgers in Bangla, so the name is written twice.
    val showBangla: Boolean = false,
    val bangla: String = "",
    val address: String = "",
    val mobile: String = "",
    val ledgerPage: String = "",
    val nationalId: String = "",
    // Branch-gated extras (need_customer_sex / need_customer_area metas).
    val showSex: Boolean = false,
    val sex: SelectorOption? = null,
    val showArea: Boolean = false,
    val areas: List<SelectorOption> = emptyList(),
    val isAreasLoading: Boolean = false,
    val area: SelectorOption? = null,
    // Opening balance, gated on the branch's is_opening flag — the same switch
    // the customer list reads before showing its Opening column.
    val showOpening: Boolean = false,
    val openingBalance: String = "",

    // The web form's remaining branch-gated fields.
    val showRelation: Boolean = false,
    val relation: SelectorOption? = null,
    val father: String = "",
    val showMotherName: Boolean = false,
    val motherName: String = "",
    val showDateOfBirth: Boolean = false,
    /** yyyy-MM-dd, or blank. */
    val dateOfBirth: String = "",
    val showOccupation: Boolean = false,
    val occupation: String = "",
    val showContactPerson: Boolean = false,
    val contactPerson: String = "",
    val contactNumber: String = "",
    val showPermanentAddress: Boolean = false,
    val permanentAddress: String = "",
    val showIdfrCode: Boolean = false,
    val idfrCode: String = "",
    val customerLogin: Boolean = false,
    val password: String = "",
    /** The on-type duplicate-mobile warning — informational, never a gate. */
    val mobileWarning: String? = null,
    val showPhoto: Boolean = false,
    /** Branch meta `need_customer_national_id` — the field prints only when asked for. */
    val showNationalId: Boolean = false,
    /** The encoded `data:image/…` URI, or blank when none picked. */
    val photo: String = "",
    // The branch-gated Guarantor / Nominee panels.
    val showGuarantors: Boolean = false,
    val guarantors: List<com.example.cashbookbd.data.repository.GuarantorRow> = emptyList(),
    val showNominees: Boolean = false,
    val nominees: List<com.example.cashbookbd.data.repository.NomineeRow> = emptyList(),
    val showNomineePhoto: Boolean = false,

    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web requires type, name, address and mobile; the rest are optional. */
    val canSave: Boolean
        get() = !isSaving &&
            type != null &&
            name.isNotBlank() &&
            address.isNotBlank() &&
            mobile.isNotBlank() &&
            // The server wants min 8 when a portal password rides along.
            (!customerLogin || password.isBlank() || password.length >= 8)
}
