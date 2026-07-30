package com.example.cashbookbd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The app's one corner radius. Inputs, buttons, menus, cards, dialogs, tiles,
 * thumbnails — every rectangular element draws with [AppShape], and Material's
 * own components get it through the `shapes` mapping in `Theme.kt`. Change the
 * number here and the whole app's corners change together.
 *
 * Deliberately round things (badges, pills, avatars) are the one exception —
 * they use `RoundedCornerShape(50)`/`CircleShape` because being round is their
 * meaning, not a styling choice.
 */
val AppCornerRadius = 2.dp

/** The shape every rectangular element clips, fills and borders with. */
val AppShape = RoundedCornerShape(AppCornerRadius)

/**
 * The exception: things whose meaning is "round" — status pills, count badges,
 * progress bars, avatars. Named here so even the exception is one decision
 * rather than a `RoundedCornerShape(50)` written from memory in each screen.
 */
val PillShape = RoundedCornerShape(percent = 50)
