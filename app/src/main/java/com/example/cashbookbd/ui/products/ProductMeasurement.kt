package com.example.cashbookbd.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.reports.model.SelectorOption

/** The web's warrantyType constant, verbatim. */
internal val WARRANTY_TYPES = listOf(
    SelectorOption("0", "Not Applicable"),
    SelectorOption("1", "Warranty"),
    SelectorOption("2", "Guarantee"),
    SelectorOption("3", "Custom"),
)

/** The leading blank that lets a measurement/package pair be left empty. */
private val NOT_APPLICABLE = SelectorOption("", "Not applicable")

/**
 * A product's measurement and packaging — additional information only; left
 * empty they simply do not print. Each pair goes together. The two columns are
 * different scales: a measurement keeps three decimals (0.667, DECIMAL(18,3)),
 * a package size two (web bcf31f8f).
 */
data class MeasurementFields(
    val measurement: String = "",
    val measurementUnitId: String = "",
    val packageSize: String = "",
    val packageUnitId: String = "",
) {
    /** The API's own rules (ItemController::measurementValidationError). */
    val error: String?
        get() {
            decimalError(measurement, "Measurement", MEASUREMENT, "three")?.let { return it }
            decimalError(packageSize, "Package size", PACKAGE_SIZE, "two")?.let { return it }
            if (measurement.isNotBlank() != measurementUnitId.isNotBlank()) {
                return "Measurement and measurement unit must be given together."
            }
            if (packageSize.isNotBlank() != packageUnitId.isNotBlank()) {
                return "Package size and package type must be given together."
            }
            return null
        }

    /** ".67" is accepted on screen but sent as "0.67"; empty stays empty (NULL). */
    fun measurementForApi(): String = withLeadingZero(measurement)
    fun packageSizeForApi(): String = withLeadingZero(packageSize)

    private companion object {
        val MEASUREMENT = Regex("^(?:\\d+(?:\\.\\d{1,3})?|\\.\\d{1,3})$")
        val PACKAGE_SIZE = Regex("^(?:\\d+(?:\\.\\d{1,2})?|\\.\\d{1,2})$")

        fun decimalError(value: String, label: String, pattern: Regex, maxDecimals: String): String? {
            val text = value.trim()
            if (text.isEmpty() || pattern.matches(text)) return null
            return "$label must be a number with at most $maxDecimals decimals."
        }

        fun withLeadingZero(value: String): String {
            val text = value.trim()
            return if (text.startsWith(".")) "0$text" else text
        }
    }
}

/** Keeps what a decimal box may hold while typing: digits and one point. */
private fun decimalOnly(value: String): String =
    value.filterIndexed { i, c -> c.isDigit() || (c == '.' && !value.take(i).contains('.')) }

/**
 * The Measurement / Measurement Unit and Package Size / Package Type rows. The
 * unit pickers read the same unit list the Unit field uses, with a leading
 * "Not applicable".
 */
@Composable
fun MeasurementFieldsBlock(
    fields: MeasurementFields,
    units: List<SelectorOption>,
    onChange: (MeasurementFields) -> Unit,
) {
    val options = listOf(NOT_APPLICABLE) + units
    fun pick(id: String) = options.firstOrNull { it.id == id } ?: NOT_APPLICABLE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
            value = fields.measurement,
            onValueChange = { onChange(fields.copy(measurement = decimalOnly(it))) },
            label = "e.g. 0.667",
            caption = "Measurement",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppSelectDropdown(
            label = "Measurement Unit",
            options = options,
            selected = pick(fields.measurementUnitId),
            onSelected = { onChange(fields.copy(measurementUnitId = it.id)) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTextField(
            value = fields.packageSize,
            onValueChange = { onChange(fields.copy(packageSize = decimalOnly(it))) },
            label = "e.g. 10",
            caption = "Package Size",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        AppSelectDropdown(
            label = "Package Type",
            options = options,
            selected = pick(fields.packageUnitId),
            onSelected = { onChange(fields.copy(packageUnitId = it.id)) },
            modifier = Modifier.weight(1f),
        )
    }
}
