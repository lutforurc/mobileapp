package com.example.cashbookbd.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Every button in the app, in one place.
 *
 * Screens must build their buttons from here and never call Material's
 * [Button]/[OutlinedButton]/[TextButton] directly, so a colour, shape or size
 * change is made once and applies everywhere. When a screen wants a different
 * look, add a parameter — or a new variant — here rather than writing a one-off
 * button locally.
 *
 * The variants:
 *  - [PrimaryButton] — the main action (Save, Apply, Log in).
 *  - [SecondaryButton] — a lower-emphasis action beside a primary one (Reset).
 *  - [LinkButton] — a borderless action (Retry, OK/Cancel, Prev/Next).
 *  - [AddButton] — a list toolbar's "+ Add …".
 *  - [FieldButton] — a button that sits in a form row and must line up with the
 *    text fields next to it (the date pickers).
 */

/** Shared geometry, so every button in the app lines up. */
private val ButtonHeight = 48.dp

/** Matches an OutlinedTextField, for buttons that sit in a form row. */
private val FieldHeight = 56.dp

/**
 * Toolbar sizing. Chrome beside a table — page size, "+ Add …" — reads as heavy
 * at the full [ButtonHeight], so those opt into `compact`.
 *
 * 32dp is Material's chip height, and a chip is what this chrome really is. It
 * is below the 48dp touch-target guidance, which is the deliberate trade: these
 * buttons are wide, infrequent and non-destructive. The main actions stay at
 * [ButtonHeight] — shrinking a Save or Apply target is not the same bet.
 */
private val CompactButtonHeight = 32.dp
private val CompactIconSize = 15.dp
private val CompactPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)

private val CompactShape = RoundedCornerShape(4.dp)

// Gently rounded, not a full pill — 24dp on a 48dp button read as overdone
// next to the 10dp form fields; this keeps the whole family in one radius.
private val ButtonShape = RoundedCornerShape(12.dp)
private val IconSize = 18.dp

/**
 * The primary call to action — Save, Apply, Log in, "+ Add …".
 *
 * Shows a spinner and blocks further taps while [isLoading].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = if (compact) CompactShape else ButtonShape,
        // M3's default disabled colours are onSurface at low alpha, which all
        // but vanishes against the brand-teal screen. A solid surface fill with
        // the muted on-colour keeps a disabled button legible in both themes.
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = if (compact) CompactPadding else ButtonDefaults.ContentPadding,
        modifier = modifier.height(if (compact) CompactButtonHeight else ButtonHeight),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = isLoading,
            // The brand colour, not on-primary white: while loading the button is
            // disabled and greys out, and a white ring would vanish against it.
            // The primary colour stays visible and reads as "working".
            spinnerColor = MaterialTheme.colorScheme.primary,
            compact = compact,
        )
    }
}

/** A lower-emphasis action shown beside a [PrimaryButton] — Reset, page size. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    isLoading: Boolean = false,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = if (compact) CompactShape else ButtonShape,
        // These sit on the screen background (filter bars, list toolbars), not
        // on a card, so label and border take the background's on-colour — the
        // primary would be one brand colour drawn on another.
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)),
        contentPadding = if (compact) CompactPadding else ButtonDefaults.ContentPadding,
        modifier = modifier.height(if (compact) CompactButtonHeight else ButtonHeight),
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            isLoading = isLoading,
            spinnerColor = MaterialTheme.colorScheme.onBackground,
            compact = compact,
        )
        if (trailingIcon != null && !isLoading) {
            Icon(
                trailingIcon,
                contentDescription = trailingIconDescription,
                modifier = Modifier.size(if (compact) CompactIconSize else IconSize),
            )
        }
    }
}

/** A borderless action — Retry, a dialog's OK/Cancel, Prev/Next, Show/Hide. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconDescription: String? = null,
    /** Overrides the default primary link colour (e.g. for a low-contrast bg). */
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = if (color != null) {
        ButtonDefaults.textButtonColors(contentColor = color)
    } else {
        ButtonDefaults.textButtonColors()
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier,
        colors = colors,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(IconSize))
            Spacer(Modifier.width(4.dp))
        }
        Text(text)
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(trailingIcon, contentDescription = iconDescription, modifier = Modifier.size(IconSize))
        }
    }
}

/**
 * The "+ Add …" button a list toolbar shows when its list has a create screen.
 * Wraps [PrimaryButton] so the plus icon and wording stay identical everywhere.
 *
 * It keeps its filled background even when compact: this is the one action on
 * the screen that creates something, and beside the outlined page-size button
 * the fill is what separates the two at a glance.
 */
@Composable
fun AddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    PrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = Icons.Filled.Add,
        compact = compact,
    )
}

/**
 * A button that acts as a form field — the date pickers. Taller than the other
 * variants so it lines up with the OutlinedTextFields beside it.
 */
@Composable
fun FieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.height(FieldHeight),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The Apply/Reset pair every report filter card ends with. Lives here so all the
 * report screens share one layout and one pair of styles; pass a null [onReset]
 * for a filter that has nothing to clear.
 */
@Composable
fun FilterActions(
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    canApply: Boolean = true,
    isLoading: Boolean = false,
    applyText: String = "Apply",
    resetText: String = "Reset",
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(
            text = applyText,
            onClick = onApply,
            enabled = canApply,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        if (onReset != null) {
            SecondaryButton(
                text = resetText,
                onClick = onReset,
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The label/spinner shared by the filled and outlined variants. */
@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    isLoading: Boolean,
    spinnerColor: androidx.compose.ui.graphics.Color,
    compact: Boolean = false,
) {
    val iconSize = if (compact) CompactIconSize else IconSize
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(iconSize),
            strokeWidth = 2.dp,
            color = spinnerColor,
        )
        return
    }
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
    }
    // A button label must never wrap: the fixed height clips a second line. Cap
    // it at one line and ellipsize instead, so a long or high-count label degrades
    // gracefully on a narrow screen rather than losing its bottom half.
    if (compact) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
