package com.example.cashbookbd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app needs, named by the job it does.
 *
 * [BrandPalette] holds the raw theme roles; this is the vocabulary screens
 * actually write — `MaterialTheme.appColors.textMuted` rather than
 * `colorScheme.onSurfaceVariant.copy(alpha = 0.8f)`. Both themes derive their
 * set from the same factory ([appColorsOf]), so "muted text" or "grid line" is
 * decided once and can never drift between screens.
 *
 * Colours that depend on their backdrop (a summary row's ink, a status chip's
 * own hue) can't be a fixed token — those use the [muted]/[asDivider]/
 * [asGridLine]/[asTint] helpers below, which apply the same single alpha to
 * whatever base colour the caller is drawing on.
 */
@Immutable
data class AppColors(
    // ——— Text: the four kinds, in colour form (see AppFontWeight) ———
    /** Primary ink on a card, field or menu. */
    val text: Color,
    /** The grey — secondary text on a card: captions, placeholders, hints. */
    val textMuted: Color,
    /** Ink on the screen backdrop, outside any card. */
    val textOnScreen: Color,
    /** Secondary ink on the screen backdrop. */
    val textOnScreenMuted: Color,
    /** Ink on a filled primary surface — buttons, table headers. */
    val textOnPrimary: Color,
    /** Ink on a filled status colour — an unread-count badge, a solid chip. */
    val textOnAccent: Color,
    /** Tappable text: links, "Show more", inline actions. */
    val textLink: Color,

    /**
     * The colour every enabled button carries — a filled Save's fill, an
     * outlined Add New's edge and label. Deliberately the same tone in both
     * themes, so an action looks like an action wherever it appears.
     */
    val action: Color,
    /** Ink on a filled [action] surface. */
    val onAction: Color,

    // ——— Surfaces ———
    /** The app's backdrop, behind every card. */
    val screen: Color,
    /** Cards, dialogs, menus, sheets and form fields. */
    val card: Color,
    /** A step up from [card] — table rows and stripes. */
    val cardRow: Color,
    /** A step above [cardRow] — chips, inputs, selected states. */
    val cardRaised: Color,
    /** A quieter fill on a card — icon tiles, disabled buttons. */
    val cardMuted: Color,

    // ——— Lines ———
    /** Field and card borders — quiet. */
    val border: Color,
    /** Borders that must be noticed. */
    val borderStrong: Color,
    /** Separators between rows and sections. */
    val divider: Color,
    /** Report-table grid lines on the screen backdrop. */
    val gridLine: Color,
    /** Grid lines inside a filled primary band (a table's header). */
    val gridLineOnPrimary: Color,

    // ——— Status ———
    /** Paid, approved, present, in-stock. */
    val success: Color,
    /** Due, rejected, absent, delete. */
    val danger: Color,
    /** Pending, late, partial, needs attention. */
    val warning: Color,
    /** Neutral information, counts, links in tables. */
    val info: Color,

    /**
     * A dark wash laid over arbitrary content — a close button sitting on a
     * campaign image. Fixed in both themes: what it covers is not ours to
     * theme, so the wash and its ink stay constant.
     */
    val scrim: Color,
    /** Ink on a [scrim]. */
    val textOnScrim: Color,

    // ——— Soft fills: a status or brand colour as a background band ———
    val primaryTint: Color,
    val successTint: Color,
    val dangerTint: Color,
    val warningTint: Color,
    val infoTint: Color,
)

/**
 * The one alpha that turns any ink into its secondary form. Applied by [muted]
 * wherever the base colour depends on what is being drawn on (a report row's
 * ink changes between normal and summary bands), and baked into
 * [AppColors.textMuted]/[AppColors.textOnScreenMuted] for the fixed cases.
 */
private const val MutedInkAlpha = 0.8f

/** Separators are quieter than muted text — just enough to read as a line. */
private const val DividerAlpha = 0.2f

/** Table grid lines: heavier than a divider, since they carry a whole grid. */
private const val GridLineAlpha = 0.3f

/** A colour used as a background band behind its own ink. */
private const val TintAlpha = 0.12f

/** Ink pushed further back than [MutedInkAlpha] — a hint, a disabled value. */
private const val FaintInkAlpha = 0.4f

/** A band that must register as a band without reading as a colour. */
private const val StripeAlpha = 0.06f

/** How dark the wash over foreign content is — enough to carry white ink. */
private const val ScrimAlpha = 0.35f

/** This colour as secondary ink. */
fun Color.muted(): Color = copy(alpha = MutedInkAlpha)

/** This colour as a separator line. */
fun Color.asDivider(): Color = copy(alpha = DividerAlpha)

/** This colour as a table grid line. */
fun Color.asGridLine(): Color = copy(alpha = GridLineAlpha)

/** This colour as a soft background band. */
fun Color.asTint(): Color = copy(alpha = TintAlpha)

/** This colour as a hint — quieter than [muted]. */
fun Color.faint(): Color = copy(alpha = FaintInkAlpha)

/** This colour as a barely-there band — a table header or zebra stripe. */
fun Color.asStripe(): Color = copy(alpha = StripeAlpha)

/**
 * The attendance report's KPI card colours, keyed by the backend summary field
 * — the web's palette verbatim. Identical in both themes: these are mid-tone
 * hues chosen to read on a light and a dark card alike.
 */
val KpiColors: Map<String, Color> = mapOf(
    "active_employees" to Color(0xFF6366F1), // indigo
    "total_entries" to Color(0xFF0EA5E9),    // sky
    "absent" to Color(0xFFF43F5E),           // rose
    "present" to Color(0xFF10B981),          // emerald
    "half_day" to Color(0xFF3B82F6),         // blue
    "leave" to Color(0xFF8B5CF6),            // violet
    "late" to Color(0xFFF59E0B),             // amber
    "early_out" to Color(0xFFF97316),        // orange
    "pending_approval" to Color(0xFFF59E0B),
    "approved" to Color(0xFF10B981),
    "rejected" to Color(0xFFF43F5E),
)

/** Builds one theme's vocabulary from its [BrandPalette]. */
fun appColorsOf(p: BrandPalette) = AppColors(
    text = p.onCard,
    textMuted = p.onCardMuted,
    textOnScreen = p.onScreen,
    textOnScreenMuted = p.onScreen.muted(),
    textOnPrimary = p.onPrimary,
    textOnAccent = BrandSheet.White,
    textLink = p.primary,

    action = BrandSheet.TealAction,
    onAction = BrandSheet.White,

    scrim = Color.Black.copy(alpha = ScrimAlpha),
    textOnScrim = BrandSheet.White,

    screen = p.screen,
    card = p.card,
    cardRow = p.cardRow,
    cardRaised = p.cardRaised,
    cardMuted = p.cardMuted,

    border = p.outlineVariant,
    borderStrong = p.outline,
    divider = p.onScreen.asDivider(),
    gridLine = p.onScreen.asGridLine(),
    gridLineOnPrimary = p.onPrimary.asGridLine(),

    success = p.accents.green,
    danger = p.accents.red,
    warning = p.accents.amber,
    info = p.accents.blue,

    primaryTint = p.primary.asTint(),
    successTint = p.accents.green.asTint(),
    dangerTint = p.accents.red.asTint(),
    warningTint = p.accents.amber.asTint(),
    infoTint = p.accents.blue.asTint(),
)

val LocalAppColors = staticCompositionLocalOf { appColorsOf(LightPalette) }

/** The current theme's colour vocabulary — `MaterialTheme.appColors.textMuted`. */
val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
