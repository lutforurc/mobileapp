package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.data.repository.BankOpeningRow

/** One level-3 section of the Bank Opening screen (Cash / Bank Account / …). */
data class BankOpeningGroup(
    val id: Int,
    val name: String,
    val rows: List<BankOpeningRow>,
) {
    val subtotal: Double get() = rows.sumOf { it.openingBalance }
}

data class BankOpeningUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<BankOpeningRow> = emptyList(),

    val searchQuery: String = "",
    /**
     * The search the list currently shows — set only by the Search button, so a
     * refresh or post-save reload never silently re-filters by text still being
     * typed. The web keeps the same two variables apart.
     */
    val submittedSearch: String = "",

    /** The row currently being edited (dialog open), or null. */
    val editing: BankOpeningRow? = null,
    val editAmount: String = "",
    /**
     * A save failure shown inside the dialog. The snackbar sits behind the
     * dialog's scrim, where a refusal is easy to miss with the dialog open.
     */
    val editError: String? = null,
    /** True while the edit is being saved. */
    val isSaving: Boolean = false,

    /** The row whose opening-balance delete is awaiting confirmation. */
    val deleteRow: BankOpeningRow? = null,
    /** True while a confirmed opening delete is in flight. */
    val isDeleting: Boolean = false,

    /** One-shot snackbar text (save outcome / info). */
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /**
     * The rows bucketed by their level-3 group, in the order the server sent
     * them — it lists group by group already, so first appearance is the order.
     */
    val groups: List<BankOpeningGroup>
        get() = rows.groupBy { it.groupId }.map { (id, grouped) ->
            BankOpeningGroup(id = id, name = grouped.first().groupName, rows = grouped)
        }

    /** The figure the "Total opening" line carries — every group summed. */
    val totalOpening: Double get() = rows.sumOf { it.openingBalance }
}
