package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.hudInk
import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: how the live player shows the programme on air — a fixed choice in Settings → Player.
 * [COMPACT] is the banner as it always was (untouched); the others are drawn here.
 */
object LiveBanner {
    enum class Style { COMPACT, FICHA, CINE, LATERAL }

    var style by androidx.compose.runtime.mutableStateOf(Style.COMPACT)
        private set
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        style = Style.entries.getOrElse(prefs(context).getInt(KEY, 0)) { Style.COMPACT }
    }

    fun set(context: Context, value: Style) {
        style = value
        prefs(context).edit().putInt(KEY, value.ordinal).apply()
    }

    /** True when the banner is drawn over the picture by the player itself, not in the dock beside the channel. */
    val wide: Boolean get() = style != Style.COMPACT

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("glcn_live_banner", Context.MODE_PRIVATE)
    private const val KEY = "style"
}

/** The minute, ticking slowly — enough for "x min left" and the bar. */
@Composable
private fun rememberWallNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    return now
}

@Composable
private fun Label(text: String, color: Color) {
    Text(text.uppercase(), style = stageText(15, 500).copy(letterSpacing = 1.2.sp), color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Bar(progress: Float, modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    val accentInk = stageAccent.ink
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(hudInk.copy(alpha = 0.2f)).drawBehind {
        drawRoundRect(accent, size = Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    })
}

/** A small ring of how much of the programme is left (as on TV en directo's rows). */
@Composable
private fun LeftRing(progress: Float, modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    val accentInk = stageAccent.ink
    val track = hudInk.copy(alpha = 0.25f)
    Box(modifier.size(14.dp).drawBehind {
        val w = 2.dp.toPx()
        drawCircle(track, style = Stroke(w))
        drawArc(accent, -90f, 360f * (1f - progress), false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    })
}

private class Facts(
    val title: String, val meta: String, val progress: Float, val synopsis: String?, val nextLine: String?,
    val start: String, val stop: String, val ring: String, val upcoming: List<Pair<String, String>>,
)

@Composable
private fun facts(epg: EpgNowNext): Facts? {
    val now = epg.now ?: return null
    val wall = rememberWallNow()
    val formatTime = rememberSystemTimeFormatter()
    val remaining = ((now.stopMs - wall) / 60_000L).toInt()
    val span = (now.stopMs - now.startMs).toFloat()
    val progress = if (span > 0f) ((wall - now.startMs) / span).coerceIn(0f, 1f) else 0f
    val time = if (remaining in 1..600) stringResource(R.string.glcn_pi_ends_left, formatTime(now.stopMs), remaining)
    else stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs))
    val meta = listOfNotNull(time, epg.nowDetails?.categoryList?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() }).joinToString("  ·  ")
    val nextLine = epg.next?.let { formatTime(it.startMs) + "  " + it.title }
    // What follows: the next one and those after it, a programme listed twice shown once.
    val upcoming = (listOfNotNull(epg.next) + epg.upcoming)
        .distinctBy { it.startMs to it.title }
        .filter { it.startMs >= now.stopMs - 60_000L }
        .take(6)
        .map { formatTime(it.startMs) to it.title }
    val ring = stringResource(R.string.content_live_time_range, formatTime(now.startMs), formatTime(now.stopMs))
    return Facts(now.title, meta, progress, now.description?.takeIf { it.isNotBlank() }, nextLine, formatTime(now.startMs), formatTime(now.stopMs), ring, upcoming)
}

/** A coming programme as a small glass chip: its time in the accent, then its title. */
@Composable
private fun NextChip(time: String, title: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(hudInk.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(time, style = stageText(14, 700), color = if (first) stageAccent.accent else hudInk.copy(alpha = 0.7f), maxLines = 1)
        Text(title, style = stageText(14, 500), color = hudInk.copy(alpha = if (first) 1f else 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Ficha — a wide glass band just above the controls, tinted with the programme's colour: the picture large on the
 * left; the title with its ring and times, a 3-line synopsis and what follows as three chips on the right. Everything
 * at a glance, the top of the picture left clear.
 */
@Composable
fun LiveBannerFicha(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    // glcn: drawn inside the bar's own glass panel (the player wraps the channel, this and the controls in one).
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top,
    ) {
        if (!art.isNullOrBlank()) tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Label(stringResource(R.string.content_live_now), stageAccent.accent)
            Text(f.title, style = stageText(21, 700), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeftRing(f.progress, Modifier.size(14.dp))
                Text(f.meta, style = stageText(15, 400), color = hudInk.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            f.synopsis?.let {
                tv.own.owntv.features.shell.components.DriftingSynopsis(
                    it, modifier = Modifier.padding(top = 2.dp),
                    style = stageText(14, 400), color = hudInk.copy(alpha = 0.88f), lines = 4,
                )
            }

        }
        // What follows, as a short list in the room on the right.
        if (f.upcoming.isNotEmpty()) {
            Box(Modifier.width(1.dp).height(96.dp).background(hudInk.copy(alpha = 0.12f)))
            Column(Modifier.width(280.dp)) {
                Label(stringResource(R.string.content_live_next), hudInk.copy(alpha = 0.6f))
                f.upcoming.take(5).forEachIndexed { i, (t, title) ->
                    Row(Modifier.padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t, style = stageText(14, 700), color = if (i == 0) stageAccent.accent else hudInk.copy(alpha = 0.65f), maxLines = 1, modifier = Modifier.width(50.dp))
                        Text(title, style = stageText(14, if (i == 0) 600 else 500), color = hudInk.copy(alpha = if (i == 0) 1f else 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Cine — words only, no boxes: a soft dark shade over the lower third of the picture (dark in both themes, the words
 * white over it), a very large title, its times and kind, a 2-line synopsis, what follows on the right, and the
 * programme's line with its start and end at the two ends, like a film's timeline.
 */
private val CINE_ART_W = 300.dp

@Composable
fun LiveBannerCine(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    val ink = Color.White
    val accent = stageAccent.accent
    val accentInk = stageAccent.ink
    Column(
        modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.42f), 1f to Color.Black.copy(alpha = 0.62f)))
            .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // glcn: the programme's picture beside the title, so Cine no longer looks empty without it.
            if (!art.isNullOrBlank()) {
                tv.own.owntv.ui.components.ProgrammeArt(
                    url = art,
                    modifier = Modifier.width(CINE_ART_W).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.content_live_now).uppercase(), style = stageText(15, 500).copy(letterSpacing = 1.4.sp), color = accentInk, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(f.title, style = stageText(52, 800), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.meta, style = stageText(19, 500), color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                f.synopsis?.let {
                    tv.own.owntv.features.shell.components.DriftingSynopsis(
                        it, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.8f),
                        style = stageText(18, 400), color = ink.copy(alpha = 0.88f), lines = 2,
                    )
                }
            }
            f.upcoming.firstOrNull()?.let { (t, title) ->
                Spacer(Modifier.width(32.dp))
                Column(Modifier.width(280.dp), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.content_live_next).uppercase(), style = stageText(14, 500).copy(letterSpacing = 1.2.sp), color = ink.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(t + "  " + title, style = stageText(18, 600), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(f.start, style = stageText(15, 600), color = ink.copy(alpha = 0.75f), maxLines = 1)
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.25f)).drawBehind {
                drawRoundRect(accent, size = Size(size.width * f.progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            })
            Text(f.stop, style = stageText(15, 600), color = ink.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

/**
 * Lateral — a full-height glass column on the left, like a small guide: the picture, the title, ring and times, a
 * long synopsis that never runs under the rest, and the next hours' programmes with their times.
 */
@Composable
fun LiveBannerLateral(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    // glcn: inside the player's full-height glass column, under the channel.
    Column(modifier) {
        if (!art.isNullOrBlank()) tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(14.dp))
        Label(stringResource(R.string.content_live_now), stageAccent.accent)
        Text(f.title, style = stageText(28, 700), color = hudInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeftRing(f.progress, Modifier.size(16.dp))
            Text(f.meta, style = stageText(16, 400), color = hudInk.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        f.synopsis?.let {
            tv.own.owntv.features.shell.components.DriftingSynopsis(
                it, modifier = Modifier.padding(top = 10.dp).weight(1f, fill = false),
                style = stageText(16, 400), color = hudInk.copy(alpha = 0.88f), lines = 8,
            )
        }
        if (f.upcoming.isNotEmpty()) {
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(hudInk.copy(alpha = 0.12f)))
            Label(stringResource(R.string.content_live_next), hudInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            f.upcoming.take(5).forEachIndexed { i, (t, title) ->
                Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t, style = stageText(16, 700), color = if (i == 0) stageAccent.accent else hudInk.copy(alpha = 0.65f), maxLines = 1, modifier = Modifier.width(56.dp))
                    Text(title, style = stageText(16, if (i == 0) 600 else 500), color = hudInk.copy(alpha = if (i == 0) 1f else 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
