package com.example.cashbookbd.ui.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.reports.model.SimpleDate

/**
 * The app's one date field: a read-only [FieldFrame] that opens the platform
 * [DatePickerDialog] when tapped, so a date picker is the same 52dp box as
 * every typed field and dropdown on the screen.
 *
 * Before this, four form screens and most report filters each carried their own
 * private `showDatePicker` copy; they all resolve to this now, so a change to
 * the picker (or the field's look) lands everywhere at once.
 *
 * [onPicked] is only called when the user confirms; dismissing the dialog leaves
 * the current date untouched.
 */
@Composable
fun AppDateField(
    label: String,
    date: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    FieldFrame(
        label = label,
        modifier = modifier,
        onClick = if (enabled) ({ showDatePicker(context, date, onPicked) }) else null,
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        },
    ) {
        val showPlaceholder = date == null
        Text(
            text = if (showPlaceholder) placeholder else date.toDisplay(),
            style = if (showPlaceholder) fieldPlaceholderTextStyle() else fieldValueTextStyle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Opens the platform date picker, defaulting to [current] (or today) and handing
 * back a 1-based-month [SimpleDate]. Shared so every date field in the app opens
 * the same dialog on the same day it is currently showing.
 */
fun showDatePicker(
    context: Context,
    current: SimpleDate?,
    onPicked: (SimpleDate) -> Unit,
) {
    val start = current ?: SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(SimpleDate(year = year, month = month + 1, day = dayOfMonth))
        },
        start.year,
        start.month - 1,
        start.day,
    ).show()
}
