package com.example.cashbookbd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The owner's design-system sheet, verbatim. These are the only raw hex values
 * in the app — every palette below is written in terms of them, so a change to
 * the brand sheet is a change to this object alone.
 */
object BrandSheet {
    // Brand · Teal & Blue
    val TealPrimary = Color(0xFF1BAFB3)
    val TealDeep = Color(0xFF178A9E)
    val BlueDeep = Color(0xFF1D5379)

    // Accents & Semantic
    val Orange = Color(0xFFE56A35)
    val Coral = Color(0xFFFF8B7B)
    val PinkTint = Color(0xFFFCEFEE)
    val TealTint = Color(0xFFE1EEEE)
    val Success = Color(0xFF4BAE4F)
    val Danger = Color(0xFFEA4335)

    // Ink & Neutrals
    val Ink = Color(0xFF1F2935)
    val Ink500 = Color(0xFF5A6470)
    val Gray400 = Color(0xFFBDBDBD)
    val Line = Color(0xFFE6E6E6)
    val Cloud = Color(0xFFF3F3F3)
    val Canvas = Color(0xFFF8F9FB)

    val White = Color(0xFFFFFFFF)
}

/**
 * One theme's colours, named by role rather than by shade.
 *
 * This is the single place a colour decision is made: [card] is *the* card
 * colour, [screen] is *the* colour behind the cards, and so on. Both themes are
 * instances of this same class ([LightPalette], [DarkPalette]), and
 * `Theme.kt` maps one instance onto Material's ColorScheme — including the
 * `surfaceContainer*` roles Cards actually draw with. So changing [card] here
 * changes every card, dialog, menu and field in the app at once; no screen ever
 * names a colour of its own.
 */
@Immutable
data class BrandPalette(
    /** Behind the cards — the app's backdrop. */
    val screen: Color,
    /** Text and icons drawn directly on [screen]. */
    val onScreen: Color,
    /** Cards, dialogs, menus, sheets and form fields. */
    val card: Color,
    /** A step up from [card] — table rows and stripes inside a card. */
    val cardRow: Color,
    /** A step above [cardRow] — chips, inputs and selected states on a card. */
    val cardRaised: Color,
    /** Primary text on a [card]. */
    val onCard: Color,
    /** Captions, placeholders and secondary text on a [card]. */
    val onCardMuted: Color,
    /** A quieter fill on a [card] — chips, icon tiles, disabled buttons. */
    val cardMuted: Color,
    /** Filled buttons, table headers, the selected drawer item. Sits on a [card]. */
    val primary: Color,
    /** Text and icons on [primary]. */
    val onPrimary: Color,
    /** A soft [primary]-tinted fill — badges, highlighted rows. */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** The brand's second voice — the deep blue of the signature gradient. */
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    /** The brand's third voice — the light teal of the signature gradient. */
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    /** Borders that must be noticed. */
    val outline: Color,
    /** Dividers and field borders — quiet. */
    val outlineVariant: Color,
    /** The sheet's signature gradient (teal → blue), for the drawer header. */
    val gradient: List<Color>,
    /** Text and icons drawn on [gradient]. */
    val onGradient: Color,
    /** Status and category colours, chosen to be legible on [card]. */
    val accents: AppAccents,
    /**
     * Border colours for the highlight rules ("phrase → coloured box" on report
     * remarks), keyed by the API's palette key. Both themes carry the full set;
     * red stays the pure #FF0000 the owner asked for in either mode.
     */
    val highlight: Map<String, Color>,
)

/**
 * Light: the sheet mapped straight across — Teal·deep backdrop, Teal tint
 * cards, Ink text.
 */
val LightPalette = BrandPalette(
    screen = BrandSheet.TealDeep,
    onScreen = BrandSheet.White,
    card = BrandSheet.TealTint,
    cardRow = Color(0xFFD9E8E8),
    cardRaised = Color(0xFFD0E2E2),
    onCard = BrandSheet.Ink,
    onCardMuted = BrandSheet.Ink500,
    cardMuted = Color(0xFFD3E4E4),
    // Blue·deep, not Teal·deep: the screen behind is Teal·deep, so a teal fill
    // would make every button and table header vanish into the backdrop. The
    // blue end of the signature gradient reads on both the screen and a card.
    primary = BrandSheet.BlueDeep,
    onPrimary = BrandSheet.White,
    primaryContainer = BrandSheet.TealTint,
    onPrimaryContainer = Color(0xFF05343C),
    secondary = BrandSheet.TealDeep,
    onSecondary = BrandSheet.White,
    secondaryContainer = Color(0xFFD4E4EF),
    onSecondaryContainer = Color(0xFF0C1D27),
    tertiary = BrandSheet.TealPrimary,
    onTertiary = BrandSheet.White,
    tertiaryContainer = Color(0xFFC5EDEE),
    onTertiaryContainer = Color(0xFF003436),
    outline = Color(0xFF8B939C),
    outlineVariant = BrandSheet.Gray400,
    gradient = listOf(BrandSheet.TealPrimary, BrandSheet.BlueDeep),
    onGradient = BrandSheet.White,
    accents = AppAccents(
        blue = BrandSheet.BlueDeep,
        green = BrandSheet.Success,
        red = BrandSheet.Danger,
        purple = Color(0xFF7048E8),
        amber = BrandSheet.Orange,
        rose = Color(0xFFC94F3E),
    ),
    highlight = mapOf(
        "red" to Color(0xFFFF0000),
        "amber" to Color(0xFFF59E0B),
        "green" to Color(0xFF22C55E),
        "blue" to Color(0xFF3B82F6),
        "purple" to Color(0xFFA855F7),
        "pink" to Color(0xFFEC4899),
        "gray" to Color(0xFF6B7280),
    ),
)

/**
 * Dark: the brand teal rebuilt as a low-chroma tonal ramp.
 *
 * The mistake worth not repeating: the first attempt used Teal·primary
 * #1BAFB3 — an *accent* tone — as the card surface, and everything drawn on it
 * had to fight it (white text on that teal is only 2.7:1). Here the surfaces
 * step screen → card → row → chip in lightness alone, and saturated teal comes
 * back only as [primary], so it reads as deliberate emphasis. Contrast on the
 * card: body text 12.8:1, captions 7.1:1, every accent ≥ 4.5:1.
 */
val DarkPalette = BrandPalette(
    screen = Color(0xFF06181E),
    onScreen = Color(0xFFE8F1F3),
    card = Color(0xFF102C35),
    cardRow = Color(0xFF173945),
    cardRaised = Color(0xFF1E4855),
    onCard = Color(0xFFE8F1F3),
    onCardMuted = Color(0xFFA3B8BF),
    cardMuted = Color(0xFF173945),
    primary = Color(0xFF178090),
    onPrimary = BrandSheet.White,
    primaryContainer = Color(0xFF1E4855),
    onPrimaryContainer = BrandSheet.TealTint,
    secondary = Color(0xFF8FB8D6),
    onSecondary = Color(0xFF0E2C42),
    secondaryContainer = BrandSheet.BlueDeep,
    onSecondaryContainer = Color(0xFFD4E4EF),
    tertiary = Color(0xFF6FD3D6),
    onTertiary = Color(0xFF00393B),
    tertiaryContainer = Color(0xFF0C5254),
    onTertiaryContainer = Color(0xFFC5EDEE),
    outline = Color(0xFF4E7B89),
    outlineVariant = Color(0xFF2A5764),
    gradient = listOf(Color(0xFF17808F), BrandSheet.BlueDeep),
    onGradient = BrandSheet.White,
    accents = AppAccents(
        blue = Color(0xFF5AA9E6),
        green = Color(0xFF5FCB72),
        red = Color(0xFFFF7A6B),
        purple = Color(0xFFB58BE8),
        amber = Color(0xFFF0A050),
        rose = Color(0xFFF58BAE),
    ),
    highlight = mapOf(
        "red" to Color(0xFFFF0000),
        "amber" to Color(0xFFFBBF24),
        "green" to Color(0xFF4ADE80),
        "blue" to Color(0xFF60A5FA),
        "purple" to Color(0xFFC084FC),
        "pink" to Color(0xFFF472B6),
        "gray" to Color(0xFF9CA3AF),
    ),
)

/**
 * Brand accent colours used by the dashboard cards and status text. These sit
 * outside the M3 [androidx.compose.material3.ColorScheme], so each
 * [BrandPalette] carries its own set, picked to stay legible on that palette's
 * card colour.
 *
 * Provided via [LocalAppAccents] from `CashBookbdTheme`; read as
 * `MaterialTheme.accents`.
 */
@Immutable
data class AppAccents(
    val blue: Color,
    val green: Color,
    val red: Color,
    val purple: Color,
    val amber: Color,
    /** H/O panel title. */
    val rose: Color,
)

val LocalBrandPalette = staticCompositionLocalOf { LightPalette }

/**
 * The current theme's [BrandPalette] — for the few things Material's
 * ColorScheme has no role for (the signature gradient, the row/chip steps).
 * Everything else should keep reading `MaterialTheme.colorScheme`.
 */
val MaterialTheme.brand: BrandPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandPalette.current

/** Brand accents for the current theme. */
val MaterialTheme.accents: AppAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandPalette.current.accents
