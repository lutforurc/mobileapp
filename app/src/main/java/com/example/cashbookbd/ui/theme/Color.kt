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

    /**
     * The action teal — the colour every button carries, in both themes. A tone
     * below [TealDeep] so white sits on it legibly; it is what the dark theme
     * already used, and the owner asked for that same button colour in light.
     */
    val TealAction = Color(0xFF178090)

    // Accents & Semantic
    val Orange = Color(0xFFE56A35)
    val Coral = Color(0xFFFF8B7B)
    val PinkTint = Color(0xFFFCEFEE)
    val TealTint = Color(0xFFE1EEEE)
    val Success = Color(0xFF4BAE4F)
    val Danger = Color(0xFFEA4335)

    /**
     * The success green every theme shares — paid amounts, the Paid pill, a
     * present/approved status. Deeper and more saturated than [Success], which
     * was too pale to read as text on the light theme's white page (2.8:1);
     * this clears 4.5:1 on the dark card and carries far better on white, and
     * it is the green the web already uses for the same states.
     */
    val SuccessStrong = Color(0xFF16A34A)

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
    /**
     * Cycling outline colours for per-customer blocks in grouped reports (Sold
     * Units): each buyer's rows read as one coloured group, colour by position —
     * the web report's CUSTOMER_COLORS cycle.
     */
    val customerCycle: List<Color>,
    /**
     * The Building Layout's unit-tile gradients by status (1 Available emerald,
     * 2 Under Dev amber, 3 Completed sky, 4 Sold violet, 0 unknown slate) —
     * the web viewer's tile colours, identical in both themes; tiles always
     * carry [onGradient] ink.
     */
    val unitStatus: Map<Int, List<Color>>,
    /**
     * The receivable-ageing bucket ramp (current → watch → chase → overdue),
     * the web's `--c-age-*` tokens. Not green/amber/orange/red on purpose:
     * saturated amber beside a dark card glares under the eye all day, so the
     * first three buckets are muted — ordered enough to read as a scale — and
     * only "overdue" carries a real colour, the one thing on the card worth
     * interrupting somebody for. Each theme dims the ramp to its own surfaces.
     */
    val ageing: List<Color>,
    /**
     * The categorical chart set (`--c-chart-1…8`), ordered so the first few
     * are the furthest apart — what a two-series chart needs. One deliberate
     * set rather than whatever each chart reached for; identical in both
     * themes, only the chrome around a chart changes.
     */
    val chartSeries: List<Color>,
    /** The dashboard's "Today at a glance" tile hues — the web KpiRow's own. */
    val glance: GlanceHues,
)

/**
 * The four KPI tiles' sparkline hues (the web's teal-500 / amber-500 /
 * cyan-500, and the body grey for vouchers). One value in both modes, exactly
 * as the web's tokens hold them.
 */
@Immutable
data class GlanceHues(
    val sales: Color,
    val purchase: Color,
    val customers: Color,
    val vouchers: Color,
)

/** Shared by both palettes — the web keeps these constant across modes. */
private val GlanceHueSet = GlanceHues(
    sales = Color(0xFF14B8A6),
    purchase = Color(0xFFF59E0B),
    customers = Color(0xFF06B6D4),
    vouchers = Color(0xFF59636F),
)

/** The web's categorical chart colours, constant across modes. */
private val ChartSeriesColors = listOf(
    Color(0xFF2B5FD9),
    Color(0xFF12A66E),
    Color(0xFFE08A0C),
    Color(0xFFCB3A4C),
    Color(0xFF7C5CE0),
    Color(0xFF12A2C4),
    Color(0xFFD9527E),
    Color(0xFF6E7885),
)

/** The web FlatLayout's tile gradients, shared by both palettes. */
private val UnitStatusGradients: Map<Int, List<Color>> = mapOf(
    1 to listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF0F766E)),
    2 to listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFB45309)),
    3 to listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF1D4ED8)),
    4 to listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF4C1D95)),
    0 to listOf(Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFF334155)),
)

/**
 * Light: a white-canvas theme — near-white backdrop, white cards and fields
 * with quiet grey outlines, Ink text, and the brand colours kept to accents
 * (labels, buttons, headers) rather than painted across the screen.
 */
val LightPalette = BrandPalette(
    // Canvas, not pure white: cards and fields are white, so the barely-grey
    // backdrop is what lets them read as raised at all.
    screen = BrandSheet.Canvas,
    onScreen = BrandSheet.Ink,
    card = BrandSheet.White,
    cardRow = BrandSheet.Canvas,
    cardRaised = BrandSheet.Cloud,
    onCard = BrandSheet.Ink,
    onCardMuted = BrandSheet.Ink500,
    cardMuted = BrandSheet.Cloud,
    // Blue·deep, not a teal: filled buttons and table headers carry white text,
    // and the teals are too light for that (white on Teal·primary is 2.7:1).
    // The blue end of the signature gradient is the accent that reads on white.
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
    // The web report's cycle verbatim: blue, emerald, amber, violet, pink,
    // cyan, lime, rose.
    customerCycle = listOf(
        Color(0xFF2563EB),
        Color(0xFF059669),
        Color(0xFFD97706),
        Color(0xFF7C3AED),
        Color(0xFFDB2777),
        Color(0xFF0891B2),
        Color(0xFF65A30D),
        Color(0xFFE11D48),
    ),
    unitStatus = UnitStatusGradients,
    ageing = listOf(
        Color(0xFF9AA6B4),
        Color(0xFFB8A06A),
        Color(0xFFC08457),
        Color(0xFFCB3A4C),
    ),
    chartSeries = ChartSeriesColors,
    glance = GlanceHueSet,
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
    // The same cycle, one step lighter so the outlines carry on dark surfaces.
    customerCycle = listOf(
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFFFBBF24),
        Color(0xFFA78BFA),
        Color(0xFFF472B6),
        Color(0xFF22D3EE),
        Color(0xFFA3E635),
        Color(0xFFFB7185),
    ),
    unitStatus = UnitStatusGradients,
    // The web's dark-mode `--c-age-*` overrides: a colour that reads as muted
    // on white is still bright against a dark card, so the ramp dims with it.
    ageing = listOf(
        Color(0xFF6B7684),
        Color(0xFF968259),
        Color(0xFFA3714D),
        Color(0xFFD6606E),
    ),
    chartSeries = ChartSeriesColors,
    glance = GlanceHueSet,
)

/**
 * Dark V2 — the reworked dark theme being trialled on the Dashboard only.
 *
 * What changes from [DarkPalette]: the *surfaces*. The current dark paints
 * every backdrop and card in deep teal, so the whole app reads as one
 * blue-green wash; V2 rebuilds the surfaces as neutral graphite (a barely-blue
 * near-black behind slate cards) and lets the brand teal come back only where
 * it means something — buttons, headers, links, the signature gradient. The
 * accents, highlight rules and status gradients are inherited unchanged.
 * Contrast on the card: body text 13.4:1, captions 6.9:1.
 *
 * Applied by [CashBookbdTheme]'s trial wrapper in Theme.kt; promote it by
 * replacing [DarkPalette]'s values once approved.
 */
val DarkPaletteV2 = DarkPalette.copy(
    screen = Color(0xFF0D1117),
    onScreen = Color(0xFFE6EDF3),
    card = Color(0xFF161D24),
    cardRow = Color(0xFF1D252E),
    cardRaised = Color(0xFF242E39),
    onCard = Color(0xFFE6EDF3),
    onCardMuted = Color(0xFF9AA7B4),
    cardMuted = Color(0xFF1D252E),
    primaryContainer = Color(0xFF173C46),
    onPrimaryContainer = BrandSheet.TealTint,
    secondaryContainer = Color(0xFF25384C),
    onSecondaryContainer = Color(0xFFD9E6F2),
    tertiaryContainer = Color(0xFF124A4D),
    outline = Color(0xFF465361),
    outlineVariant = Color(0xFF2B3540),
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
