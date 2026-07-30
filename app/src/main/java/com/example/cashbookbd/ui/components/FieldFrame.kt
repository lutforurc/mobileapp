package com.example.cashbookbd.ui.components

import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.AppFontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.AppShape

/** Height of a form field's box, excluding the floating label's overhang. */
val FormFieldHeight = 52.dp

/**
 * One corner shape for every form control, aliased to the app-wide [AppShape]
 * token — fields, the buttons in [AppButtons], menus and cards all resolve to
 * that single radius, so they can never drift apart.
 */
val FormFieldShape = AppShape

/** Where the floating label starts, from the field's left edge. */
private val LabelStartInset = 16.dp

/** Breathing room inside the label's pill, left and right of the text. */
private val LabelPillPadding = 5.dp

/**
 * The shared chrome for every form field: a rounded outlined box with the
 * field's name floating on the top border — the label sits on the border line,
 * like a Material "outlined" text field's notched label.
 *
 * This is the single definition of what a field looks like — the read-only
 * [com.example.cashbookbd.ui.reports.PickerField] and the searchable dropdowns
 * both render through it, so branch, ledger and date fields line up and a change
 * to the height, radius or colour lands everywhere at once.
 *
 * @param onClick when set, the whole box becomes tappable. Read-only fields use
 *   this; editable ones leave it null so the text field owns the taps.
 * @param multiline when true the box grows with its content ([FormFieldHeight]
 *   stays the minimum) and the body is top-aligned — for textarea-style fields.
 * @param trailingIcon drawn at the end of the row (chevron, calendar, spinner…).
 * @param content the field's body, laid out in the remaining width.
 */
@Composable
fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    multiline: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val hasLabel = label.isNotBlank()
    val density = LocalDensity.current
    // Measured so the box can be pushed down by exactly half the label height,
    // whatever the font scale.
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier) {
        // The box's top border passes through the label's vertical centre.
        val labelOverhang = with(density) { labelSize.height.toDp() / 2 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (hasLabel) labelOverhang else 0.dp)
                .then(
                    if (multiline) Modifier.heightIn(min = FormFieldHeight)
                    else Modifier.height(FormFieldHeight)
                )
                .clip(FormFieldShape)
                // Solid surface, not a translucent tint: the screen behind is
                // the brand teal, and a see-through box melts into it.
                .background(MaterialTheme.colorScheme.surface)
                // The floating label's opaque pill covers the border line where
                // they overlap, so a plain border needs no gap cut out of it.
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = FormFieldShape,
                )
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
                .padding(horizontal = 14.dp)
                .then(if (multiline) Modifier.padding(vertical = 12.dp) else Modifier),
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
        ) {
            content()
            trailingIcon?.invoke()
        }
        // The label rides the border line, straddling the screen behind and the
        // field's fill — two different colours, so no single text colour reads
        // on both. A pill of the field's own surface colour under the text
        // gives it one constant backdrop on any theme.
        if (hasLabel) Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .padding(start = LabelStartInset)
                .onSizeChanged { labelSize = it }
                .background(MaterialTheme.colorScheme.surface, FormFieldShape)
                .padding(horizontal = LabelPillPadding),
        )
    }
}

/** The text style a field's value is drawn in, shared so every field matches. */
@Composable
fun fieldValueTextStyle() = MaterialTheme.typography.bodyMedium.copy(
    fontWeight = AppFontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurface,
)

/**
 * The style of a field's empty-state hint. Same size and metrics as
 * [fieldValueTextStyle] so the text does not shift when the user types, but
 * normal weight in the muted ink — a hint must not read as an entered value.
 */
@Composable
fun fieldPlaceholderTextStyle() = fieldValueTextStyle().copy(
    fontWeight = AppFontWeight.Normal,
    color = MaterialTheme.appColors.textMuted,
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
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = fieldValueTextStyle(),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.weight(1f),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = fieldPlaceholderTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            inner()
        },
    )
}
