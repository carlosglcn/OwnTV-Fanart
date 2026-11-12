package tv.own.owntv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens of the Stage design.
 *
 * The design is drawn at 1920×1080 and is matched at the default UI Zoom of 90%. The TV runs
 * 320 dpi with a 1920×1080 override, so density = 2.0 × zoom = 1.8 px per dp there, and one
 * design pixel is 1 / 1.8 dp. Every value is then written in design pixels: an 84 px row
 * is `84.mpx`. At other zooms Stage scales with the setting like everything else.
 */
private const val DP_PER_MPX = 1f / 1.8f

/** A design pixel as dp (see the file comment). */
val Number.mpx: Dp get() = (toFloat() * DP_PER_MPX).dp

/** A design pixel as sp, so the user's font size still scales Stage type. */
val Number.mpxSp: TextUnit get() = (toFloat() * DP_PER_MPX).sp

/**
 * Stage's neutrals for one theme (`.mine` / `.mine.light`). Accent and on-accent stay the user's
 * own, from [OwnTVTheme.colors]; in light the focus ring is the accent too (see [stageAccent]).
 */
@Immutable
data class StagePalette(
    val isLight: Boolean,
    val text: Color,
    val muted: Color,
    val dim: Color,
    /** Idle text of menu items, sheet items and category rows (`--item`). */
    val itemText: Color,
    val menuItemText: Color,
    val tagText: Color,
    /** `--ink-rgb`: the base of every flat surface, used at the alphas the dark theme uses for white. */
    val ink: Color,
    /** `--page`: the page colour, also the gap between a focused item and its accent ring in light. */
    val page: Color,
    val glassTint: Color,
    val glassOff: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** `--body`: plots, descriptions and panel text. */
    val body: Color,
    /** `--navtxt`: idle rail labels. */
    val navText: Color,
    /** Title and second line of a focused FX row (on the accent sweep). */
    val fxText: Color,
    val fxLine: Color,
    /** `.scrim`: the wash behind a sheet or popup. */
    val scrim: Color,
    /** `--wash-rgb`: what a backdrop photo fades into. */
    val wash: Color,
    /** The tint of every drop shadow on the page; light shadows are a third as strong ([shadow]). */
    val shadowTint: Color,
    val shadowScale: Float,
    /** The chosen part of a segmented control. */
    val segmentOn: Color,
    /** A switch's knob when off. */
    val knobOff: Color,
    /** Key caps in the key hints. */
    val keycap: Color,
    /** The amber "score" chip (auto-match). */
    val chipWarnText: Color,
    val chipWarnFill: Color,
) {
    /** The one "idle control fill" everywhere: tools, search fields, segmented. */
    val controlFill: Color get() = ink.copy(alpha = 0.06f)

    /** A drop shadow of the dark theme's [alpha], in this palette. */
    fun shadow(alpha: Float): Color = shadowTint.copy(alpha = alpha * shadowScale)

    companion object {
        val Dark = StagePalette(
            isLight = false,
            text = Color(0xFFF1F5F3),
            muted = Color(0xFFA7B3AE),
            dim = Color(0xFF6D7A75),
            itemText = Color(0xFFD5DFDB),
            menuItemText = Color(0xFFDBE4E0),
            tagText = Color(0xFFC8D3CE),
            ink = Color.White,
            page = Color(0xFF05080A),
            glassTint = Color(0xFF121A1E),
            glassOff = Color(0xFF121A1C),
            ok = Color(0xFF7BE3A4),
            warn = Color(0xFFFFB86B),
            danger = Color(0xFFFF8A7A),
            body = Color(0xFFC9D3CF),
            navText = Color(0xFFC4CFCA),
            fxText = Color.White,
            fxLine = Color(0xFFD9E6E1),
            scrim = Color(2, 5, 6).copy(alpha = 0.55f),
            wash = Color(5, 8, 10),
            shadowTint = Color.Black,
            shadowScale = 1f,
            segmentOn = Color.White.copy(alpha = 0.13f),
            knobOff = Color(0xFFCFD8D4),
            keycap = Color.White.copy(alpha = 0.08f),
            chipWarnText = Color(0xFFFFD27A),
            chipWarnFill = Color(255, 200, 80).copy(alpha = 0.14f),
        )
        val Light = StagePalette(
            isLight = true,
            text = Color(0xFF0F1A16),
            muted = Color(0xFF4C5A54),
            dim = Color(0xFF77847F),
            itemText = Color(0xFF1C2824),
            menuItemText = Color(0xFF1C2824),
            tagText = Color(0xFF3D4B45),
            ink = Color(0xFF0C1814),
            page = Color(0xFFF4F7F5),
            glassTint = Color.White,
            glassOff = Color.White,
            ok = Color(0xFF2B8A3E),
            // The light amber (`.rv .sc`); the design names no separate warn colour.
            warn = Color(0xFF9A5A08),
            danger = Color(0xFFD23B2E),
            body = Color(0xFF2D3A35),
            navText = Color(0xFF55635D),
            fxText = Color(0xFF0F1A16),
            fxLine = Color(0xFF2D3A35),
            scrim = Color(20, 32, 28).copy(alpha = 0.30f),
            wash = Color(244, 247, 245),
            // 0 24 60 rgb(20,40,32) 16% where dark has black 45%.
            shadowTint = Color(20, 40, 32),
            shadowScale = 0.36f,
            segmentOn = Color.White,
            knobOff = Color.White,
            keycap = Color.White,
            chipWarnText = Color(0xFF9A5A08),
            chipWarnFill = Color(184, 100, 10).copy(alpha = 0.12f),
        )
    }
}

/** The palette of the resolved Theme, provided by [OwnTVTheme]. */
val LocalStagePalette = staticCompositionLocalOf { StagePalette.Dark }

/** Stage's neutrals, read from the current [StagePalette]. */
object StageColors {
    val Text: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.muted
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.dim
    val ItemText: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.itemText
    val MenuItemText: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.menuItemText
    val TagText: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.tagText
    val ControlFill: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.controlFill
    val GlassOff: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.glassOff
    /** Rating chips are unchanged in light. */
    val RatingStar = Color(0xFFFFCC4D)
    /** `.ok`: "✓ Ready to watch". */
    val Ok: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.ok
    /** The amber of "⚠ Never" and the Never-backed-up card. */
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.warn
    /** `.danger`: Clear watch history, Clear log. */
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.danger
    val Body: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.body
    val NavText: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.navText
    val FxText: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.fxText
    val FxLine: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.fxLine
    /** Flat surfaces: the dark theme's white, at the same alphas. */
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.ink
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.scrim
    val Wash: Color @Composable @ReadOnlyComposable get() = LocalStagePalette.current.wash
}

/*
 * The player's chrome over video. Dark: white ink on black shades, exactly as before. Light: the text
 * colour on paper-white shades, a little stronger than the black ones so dark text reads over any picture.
 * Text drawn straight on the video with no panel behind it (status, subtitles) stays white in both.
 */

/** Text and icons of the player chrome. */
val hudInk: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalStagePalette.current.isLight) LocalStagePalette.current.text else Color.White

/** A player panel or scrim of the dark theme's black [alpha]. */
@Composable
@ReadOnlyComposable
fun hudShade(alpha: Float): Color =
    if (LocalStagePalette.current.isLight) Color(244, 247, 245).copy(alpha = (alpha * 1.15f).coerceAtMost(0.92f)) else Color.Black.copy(alpha = alpha)

/** The accent on player chrome: dark keeps the brightened on-video accent, light uses the accent itself. */
val hudAccent: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalStagePalette.current.isLight) OwnTVTheme.colors.primary else OwnTVTheme.colors.accentOnVideo
val hudOnAccent: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalStagePalette.current.isLight) OwnTVTheme.colors.onPrimary else OwnTVTheme.colors.onAccentOnVideo

/** A one-off dark shade kept exactly as it is, with its [light] counterpart: dark stays pixel-identical. */
@Composable
@ReadOnlyComposable
fun stageTone(dark: Color, light: Color): Color = if (LocalStagePalette.current.isLight) light else dark

/** A drop shadow of the dark theme's [alpha] in the current palette (black → a soft green-grey in light). */
@Composable
@ReadOnlyComposable
fun stageShadow(alpha: Float): Color = LocalStagePalette.current.shadow(alpha)

/** Corner radii, in design pixels (rail, video, guide cell…). */
object StageRadii {
    val Sheet = 32.mpx
    val Menu = 28.mpx
    val Button = 22.mpx
    val Row = 20.mpx
    val Pill = 17.mpx
    val Poster = 16.mpx
    val SheetItem = 16.mpx
    val Tool = 15.mpx
    val MenuItem = 14.mpx
    val Plate = 12.mpx
    val Tag = 6.mpx
}

/** The accent triple every Stage treatment is built from. */
data class StageAccent(
    val accent: Color,
    val onAccent: Color,
    val focus: Color,
    /**
     * glcn: the accent as text or an icon on the page — the theme's own text colour instead when the accent would
     * vanish there (a white or pale accent under the light theme, a near-black one under the dark).
     */
    val ink: Color = accent,
)

val stageAccent: StageAccent
    @Composable
    @ReadOnlyComposable
    // In light the pale focus colour vanishes on white, so every ring is the accent itself.
    get() = OwnTVTheme.colors.let {
        val pal = LocalStagePalette.current
        val l = it.primary.luminance()
        val ink = if ((pal.isLight && l > INK_LIGHT_MAX) || (!pal.isLight && l < INK_DARK_MIN)) pal.text else it.primary
        StageAccent(it.primary, it.onPrimary, if (pal.isLight) it.primary else it.focusBorder, ink)
    }

/** glcn: past these the accent no longer reads as text on the light / dark page. */
private const val INK_LIGHT_MAX = 0.55f
private const val INK_DARK_MIN = 0.06f

/**
 * Stage type: a design size and weight in the user's main font (Plus Jakarta Sans by default).
 * Line height is left to the font, as the design's `line-height: normal` leaves it to the browser.
 */
@Composable
@ReadOnlyComposable
fun stageText(size: Number, weight: Int, letterSpacing: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = LocalMainFontFamily.current,
    fontSize = size.mpxSp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacing,
)
