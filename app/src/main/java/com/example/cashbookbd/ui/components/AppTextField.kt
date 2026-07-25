package com.example.cashbookbd.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation

/**
 * The app's standard editable field: a [FieldFrame] (the same 44dp box every
 * dropdown and picker uses) with a [FieldTextInput] body, so typed fields,
 * dropdowns and date pickers all share one height and one look. Change
 * [FieldFrame] to restyle every field at once.
 *
 * @param label shown inside the box while empty (the field's name).
 * @param caption optional small caption above the box, like the dropdowns'
 *   "Select Supplier"; blank means no caption row at all.
 * @param multiline textarea-style: the box grows with its lines (e.g. the
 *   Product List's IMEI/serial entry, one serial per line).
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    caption: String = "",
    enabled: Boolean = true,
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    FieldFrame(label = caption, modifier = modifier, multiline = multiline, trailingIcon = trailingIcon) {
        FieldTextInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            enabled = enabled,
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
        )
    }
}
