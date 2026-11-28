package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * glcn: the focus of a style with a second accent (Brasa). Rows, cards and posters focus as an OUTLINE in the
 * accent's gradient over the faintest wash — the picture under a focused card still shows — and buttons, pills and
 * menu items fill with the gradient. Null [second] = the style has none and focus is drawn as it always was.
 */
object EmberFocus {
    var second by mutableStateOf<Color?>(null)
    val on: Boolean get() = second != null

    /** The theme's own accent and side — what [partner] compares the accent in use with. */
    var themeAccent: Color? = null
    var light = false

    /** The theme's second accent, or null when the style has none (focus then drawn as it always was). */
    fun secondFor(style: tv.own.owntv.core.theme.GlcnStyle?): Color? {
        themeAccent = style?.accentHex?.let { parse(it) }
        light = style?.light == true
        return style?.accent2Hex?.let { parse(it) }
    }

    /**
     * The colour that goes with [accent] in the gradient. The theme's second while the accent is the theme's; with
     * an accent the user chose (Personalise) the theme's second would not go with it — red into Ría's petrol passed
     * through brown — so its partner is made from that accent: turned a little round the wheel, and lightened on a
     * dark page or deepened on a light one.
     */
    fun partner(accent: Color, second: Color): Color {
        val mine = themeAccent
        if (mine == null || close(accent, mine)) return second
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        hsv[0] = (hsv[0] + PARTNER_TURN) % 360f
        hsv[2] = if (light) hsv[2] * 0.82f else (hsv[2] * 1.12f).coerceAtMost(1f)
        hsv[1] = (hsv[1] * 0.92f).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** The gradient's stops for [accent]: with its [partner], round the colour wheel. */
    fun sweepOf(accent: Color, second: Color): List<Color> = stops(accent, partner(accent, second))

    private fun close(a: Color, b: Color): Boolean =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) < 0.06f

    /**
     * The stops of the accent's gradient: the two ends and a middle taken round the colour wheel, not straight
     * across it — a straight mix of two far-apart colours turns grey or brown halfway.
     */
    fun stops(a: Color, b: Color): List<Color> {
        val ha = FloatArray(3); val hb = FloatArray(3)
        android.graphics.Color.colorToHSV(a.toArgb(), ha)
        android.graphics.Color.colorToHSV(b.toArgb(), hb)
        var d = hb[0] - ha[0]
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        val mid = floatArrayOf((ha[0] + d / 2f + 360f) % 360f, (ha[1] + hb[1]) / 2f, maxOf(ha[2], hb[2]) * 0.96f + 0.04f * minOf(ha[2], hb[2]))
        return listOf(a, Color(android.graphics.Color.HSVToColor(mid)), b)
    }

    private fun parse(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    private const val PARTNER_TURN = 28f
}
