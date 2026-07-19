package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Height of a form field's box, excluding the caption above it. */
val FormFieldHeight = 44.dp

private val FieldShape = RoundedCornerShape(10.dp)

/**
 * The shared chrome for every form field: a small primary-coloured caption above
 * a short [FormFieldHeight] rounded box with a tinted border.
 *
 * This is the single definition of what a field looks like — the read-only
 * [com.example.cashbookbd.ui.reports.PickerField] and the searchable dropdowns
 * both render through it, so branch, ledger and date fields line up and a change
 * to the height, radius or colour lands everywhere at once.
 *
 * @param onClick when set, the whole box becomes tappable. Read-only fields use
 *   this; editable ones leave it null so the text field owns the taps.
 * @param trailingIcon drawn at the end of the row (chevron, calendar, spinner…).
 * @param content the field's body, laid out in the remaining width.
 */
@Composable
fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FormFieldHeight)
                .clip(FieldShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = FieldShape,
                )
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            trailingIcon?.invoke()
        }
    }
}

/** The text style a field's value is drawn in, shared so every field matches. */
@Composable
fun fieldValueTextStyle() = MaterialTheme.typography.bodyMedium.copy(
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurface,
)

/**
 * The editable body of a [FieldFrame] — a single-line input styled to match the
 * read-only [com.example.cashbookbd.ui.reports.PickerField]'s text exactly.
 *
 * [androidx.compose.material3.OutlinedTextField] is not used here: it carries
 * its own 56dp frame and notched label, which is what made typed fields taller
 * than picked ones. This draws only the text, letting [FieldFrame] own the box.
 */
@Composable
fun RowScope.FieldTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = fieldValueTextStyle(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.weight(1f),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = fieldValueTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            inner()
        },
    )
}
