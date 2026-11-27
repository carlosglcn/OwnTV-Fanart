package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import tv.own.owntv.core.parser.GuideText
import tv.own.owntv.core.repository.GuideColours
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.stageAccent

/**
 * glcn: a guide's text with its pieces tinted — the season mark and the episode title of "Show T3 E70 · Episode",
 * the labels of a synopsis ("· País:"). A guide written for Kodi brings its own colours ([GuideColours]) and those
 * are used, nudged only where one would not read on the page; any other guide gets the theme's.
 */
@Immutable
class GuideInk(val season: Color, val episode: Color, val label: Color) {

    /** [text] tinted, or as it is when it is only a name. [alpha] dims the tints with the rest of a faded title. */
    fun title(text: String, alpha: Float = 1f): AnnotatedString {
        val parts = GuideText.title(text) ?: return AnnotatedString(text)
        val spans = ArrayList<AnnotatedString.Range<SpanStyle>>(2)
        if (parts.seasonFrom >= 0) spans += AnnotatedString.Range(SpanStyle(color = season.copy(alpha = alpha)), parts.seasonFrom, parts.seasonTo)
        if (parts.episodeFrom >= 0) spans += AnnotatedString.Range(SpanStyle(color = episode.copy(alpha = alpha)), parts.episodeFrom, text.length)
        return AnnotatedString(text, spans)
    }

    fun synopsis(text: String): AnnotatedString {
        val at = GuideText.labels(text)
        if (at.isEmpty()) return AnnotatedString(text)
        val style = SpanStyle(color = label)
        return AnnotatedString(text, List(at.size / 2) { AnnotatedString.Range(style, at[it * 2], at[it * 2 + 1]) })
    }
}

/** The tints for the guide [sourceId] (0 or unknown: the theme's). */
@Composable
fun rememberGuideInk(sourceId: Long): GuideInk {
    val version by GuideColours.version.collectAsState()
    val palette = LocalStagePalette.current
    val accent = stageAccent.ink
    return remember(sourceId, version, palette, accent) {
        val own = GuideColours.of(sourceId)
        fun pick(argb: Int?, theme: Color) = if (argb == null || argb == 0) theme else readable(Color(argb), palette.isLight)
        GuideInk(pick(own?.season, accent), pick(own?.episode, palette.muted), pick(own?.label, accent))
    }
}

@Composable
fun guideTitle(title: String, sourceId: Long): AnnotatedString {
    val ink = rememberGuideInk(sourceId)
    return remember(title, ink) { ink.title(title) }
}

@Composable
fun guideSynopsis(text: String, sourceId: Long): AnnotatedString {
    val ink = rememberGuideInk(sourceId)
    return remember(text, ink) { ink.synopsis(text) }
}

/** A guide's own colour, kept unless it would sink into the page: a pale one darkened by day, a dark one lifted by night. */
private fun readable(c: Color, light: Boolean): Color {
    val l = c.luminance()
    return when {
        light && l > DAY_MAX -> lerp(c, Color.Black, ((l - DAY_MAX) * DAY_PULL).coerceIn(DAY_MIN_PULL, DAY_MAX_PULL))
        !light && l < NIGHT_MIN -> lerp(c, Color.White, NIGHT_PULL)
        else -> c
    }
}

private const val DAY_MAX = 0.28f
private const val DAY_PULL = 1.1f
private const val DAY_MIN_PULL = 0.18f
private const val DAY_MAX_PULL = 0.55f
private const val NIGHT_MIN = 0.1f
private const val NIGHT_PULL = 0.4f
