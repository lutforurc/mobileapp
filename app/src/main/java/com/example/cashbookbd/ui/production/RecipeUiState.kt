package com.example.cashbookbd.ui.production

import com.example.cashbookbd.ui.production.model.RecipeDetail
import com.example.cashbookbd.ui.production.model.RecipeLine
import com.example.cashbookbd.ui.production.model.RecipeListRow
import com.example.cashbookbd.ui.reports.model.SimpleDate

enum class RecipeTab { RECIPE, LIST }

/** Drives the Recipe screen: the version being written and the saved list. */
data class RecipeUiState(
    val tab: RecipeTab = RecipeTab.RECIPE,
    val recipes: List<RecipeListRow> = emptyList(),
    val listLoading: Boolean = false,
    val listError: String? = null,
    val form: RecipeDetail = emptyForm(),
    val lines: List<RecipeLine> = emptyList(),
    val lineDraft: RecipeLine = RecipeLine(productId = "", productName = "", unit = "", qty = ""),
    /** Index into [lines] while a line is being corrected; null = adding. */
    val editingLineIndex: Int? = null,
    val isSaving: Boolean = false,
    val isOpening: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val canWrite: Boolean = false,
    val confirmNewVersion: Boolean = false,
    val creatingVersion: Boolean = false,
    val removeTarget: RecipeListRow? = null,
    val removing: Boolean = false,
) {
    val isEditing: Boolean get() = form.id != null

    val effectiveDate: SimpleDate?
        get() = SimpleDate.fromApi(form.effectiveDate)

    val saveLabel: String get() = if (isEditing) "Update" else "Save"

    val headerLabel: String
        get() = if (isEditing) {
            buildString {
                append("Editing ")
                append(form.name.ifBlank { "#${form.id}" })
                if (form.version.isNotBlank()) append(" version ${form.version}")
            }
        } else {
            "New recipe"
        }

    companion object {
        fun emptyForm(effectiveDate: String = "") = RecipeDetail(
            id = null,
            name = "",
            outputProductId = "",
            outputProductName = "",
            unit = "",
            yieldQty = "",
            lossBasis = "kg",
            lossTolerancePct = "",
            suggestedCost = "",
            version = "",
            effectiveDate = effectiveDate,
            note = "",
        )
    }
}

/** A zero quantity is no line at all; the form's own rule for both adding and saving. */
internal fun String.positiveOrNull(): Double? = trim().toDoubleOrNull()?.takeIf { it > 0 }
