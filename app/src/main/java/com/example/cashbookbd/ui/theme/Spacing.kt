package com.example.cashbookbd.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The left/right margin between a screen's content and the phone's edge.
 * One value for every screen — lists, reports, forms — so content does not
 * shift sideways as the user moves between them.
 */
val ScreenGutter = 16.dp

/**
 * The vertical rhythm, as a named scale rather than a pile of literals.
 *
 * Screens used to pick 8, 10, 12, 14 or 16 dp by eye, so the gap between two
 * fields could differ from field to field on the same screen. Naming the steps
 * makes the choice a decision ("a small gap" → [sm]) instead of a guess, and
 * keeps a screen's own spacing in step with the shared components, which use
 * the same scale. The four steps cover what the app actually draws: [xs]
 * inside a chip, [sm] between tightly related rows, [md] between fields, [lg]
 * as the screen gutter / between sections, [xl] around a centred state.
 */
object Spacing {
    /** Hairline gaps — inside a chip, between an icon and its label. */
    val xs = 4.dp
    /** Between tightly related rows — a label and its field. */
    val sm = 8.dp
    /** Between fields and list rows. */
    val md = 12.dp
    /** The screen gutter and the space between sections. */
    val lg = 16.dp
    /** Around a centred empty/loading/error state. */
    val xl = 24.dp
}
