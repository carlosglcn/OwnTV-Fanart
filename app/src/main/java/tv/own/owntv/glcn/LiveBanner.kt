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
    val wide: Boolean get() = style == Style.CINE || style == Style.LATERAL

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
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(hudInk.copy(alpha = 0.2f)).drawBehind {
        drawRoundRect(accent, size = Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    })
}

/** A small ring of how much of the programme is left (as on TV en directo's rows). */
@Composable
private fun LeftRing(progress: Float, modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    val track = hudInk.copy(alpha = 0.25f)
    Box(modifier.size(14.dp).drawBehind {
        val w = 2.dp.toPx()
        drawCircle(track, style = Stroke(w))
        drawArc(accent, -90f, 360f * (1f - progress), false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    })
}

private class Facts(val title: String, val meta: String, val progress: Float, val synopsis: String?, val nextLine: String?)

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
    return Facts(now.title, meta, progress, now.description?.takeIf { it.isNotBlank() }, nextLine)
}

/** Ficha — in the dock beside the channel: a larger picture, the title, a ring of time left, the synopsis, what follows. */
@Composable
fun LiveBannerFicha(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(stageAccent.accent.copy(alpha = 0.10f)).background(hudShade(0.35f)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!art.isNullOrBlank()) tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.size(width = 168.dp, height = 94.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.width(330.dp)) {
            Label(stringResource(R.string.content_live_now), stageAccent.accent)
            Text(f.title, style = stageText(24, 700), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LeftRing(f.progress)
                Text(f.meta, style = stageText(16, 400), color = hudInk.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            f.synopsis?.let {
                tv.own.owntv.features.shell.components.DriftingSynopsis(
                    it, modifier = Modifier.padding(top = 4.dp),
                    style = stageText(15, 400), color = hudInk.copy(alpha = 0.85f), lines = 3,
                )
            }
            f.nextLine?.let {
                Row(Modifier.padding(top = 5.dp).clip(RoundedCornerShape(6.dp)).background(hudInk.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(it, style = stageText(15, 500), color = hudInk.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Cine — no panel: large text straight over the darkened foot of the picture, what follows on the right. */
@Composable
fun LiveBannerCine(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    // Its own soft shade from the picture down to the dock, so the large text reads over any picture.
    Column(
        modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.4f to hudShade(0.55f), 1f to hudShade(0.74f)))
            .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label(stringResource(R.string.content_live_now), stageAccent.accent)
                Text(f.title, style = stageText(40, 700), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.meta, style = stageText(18, 400), color = hudInk.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                f.synopsis?.let {
                    tv.own.owntv.features.shell.components.DriftingSynopsis(
                        it, modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.85f),
                        style = stageText(17, 400), color = hudInk.copy(alpha = 0.85f), lines = 2,
                    )
                }
            }
            if (f.nextLine != null) {
                Spacer(Modifier.width(24.dp))
                Column(Modifier.width(260.dp).clip(RoundedCornerShape(10.dp)).background(hudInk.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Label(stringResource(R.string.content_live_next), hudInk.copy(alpha = 0.6f))
                    Text(f.nextLine, style = stageText(16, 500), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Bar(f.progress, Modifier.padding(top = 10.dp))
    }
}

/** Lateral — a column on the left: the picture, the title, a longer synopsis and what follows; the video stays in view. */
@Composable
fun LiveBannerLateral(epg: EpgNowNext?, art: String?, modifier: Modifier = Modifier) {
    val f = epg?.let { facts(it) } ?: return
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(hudShade(0.78f)).padding(18.dp)) {
        if (!art.isNullOrBlank()) tv.own.owntv.ui.components.ProgrammeArt(url = art, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(12.dp))
        Label(stringResource(R.string.content_live_now), stageAccent.accent)
        Text(f.title, style = stageText(28, 700), color = hudInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(f.meta, style = stageText(16, 400), color = hudInk.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Bar(f.progress, Modifier.padding(vertical = 8.dp))
        f.synopsis?.let {
            tv.own.owntv.features.shell.components.DriftingSynopsis(
                it, modifier = Modifier.weight(1f, fill = false),
                style = stageText(16, 400), color = hudInk.copy(alpha = 0.85f), lines = 8,
            )
        }
        f.nextLine?.let {
            Spacer(Modifier.height(10.dp))
            Label(stringResource(R.string.content_live_next), hudInk.copy(alpha = 0.6f))
            Text(it, style = stageText(17, 500), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
