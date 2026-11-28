package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: "Skip intro" for series. Each series learns where its intro ends: the first time the viewer
 * jumps forward early in an episode (with this button or by fast-forwarding) and keeps watching from
 * there, that moment is remembered for the series, and later episodes offer — or, with Auto-skip on,
 * make — the same jump. With nothing known the button stays away — a blind jump landed in shows without an
 * intro, or mid-scene.
 *
 * Since the merge with upstream's own skip (intro, recap, credits and preview from TheIntroDB, in the player's core)
 * this is only the fallback: it acts when that has no times for what is playing — a file whose chapters name its
 * intro or credits, a series with a learned jump — and follows the same choices (Off / Button / Automatic).
 */
object IntroSkip {
    /** How far the very first "Skip intro" of a series jumps — a typical opening. */
    const val DEFAULT_JUMP_MS = 80_000L
    /** Early in an episode is where an intro can be. */
    const val WINDOW_MS = 8 * 60_000L
    /** Before anything is learned the button shows from here… */
    const val SHOW_FROM_MS = 10_000L
    /** …until here. */
    const val SHOW_UNTIL_MS = 4 * 60_000L
    /** A jump forward of more than this counts as skipping. */
    private const val JUMP_MS = 15_000L
    /** The fallback button waits this long into the episode, for the core's times to have arrived. */
    private const val FALLBACK_FROM_MS = 5_000L
    /** Watching on this long after a jump confirms where the intro ended. */
    private const val SETTLE_MS = 12_000L

    /** glcn: the end of the playing file's intro chapter (the next chapter's start), or -1. Set by [Chip]. */
    @Volatile var chapterEnd: Long = -1L
        private set
    /**
     * glcn: where the end credits start in the episode playing (a chapter named so), or -1 — only while fallback.
     * The player shows the next-episode card from there. Set by [Chip].
     */
    val creditsAt = kotlinx.coroutines.flow.MutableStateFlow(-1L)

    /** A chapter named as the end credits in the last third of the file, or -1. */
    fun creditsStart(chapters: List<tv.own.owntv.player.PlayerChapter>, durationMs: Long): Long =
        chapters.firstOrNull { c -> c.startMs > durationMs * 2 / 3 && c.title?.let { CREDITS_NAME.containsMatchIn(it) } == true }
            ?.startMs ?: -1L

    /** The end of a chapter named as an intro and starting in the first minutes, from the file's chapters. */
    fun introEnd(chapters: List<tv.own.owntv.player.PlayerChapter>): Long {
        val i = chapters.indexOfFirst { c -> c.startMs < WINDOW_MS && c.title?.let { INTRO_NAME.containsMatchIn(it) } == true }
        if (i < 0) return -1L
        return chapters.getOrNull(i + 1)?.startMs?.takeIf { it > chapters[i].startMs } ?: -1L
    }

    private val CREDITS_NAME = Regex("""(?i)\b(credits|end credits|ending|outro|cr[ée]ditos|cr[ée]dits|abspann|ed)\b""")
    /** Credits closer to the end than this are left to the usual last-seconds card. */
    private const val CREDITS_TAIL_MS = 20_000L
    private val INTRO_NAME = Regex("""(?i)\b(intro|introduction|opening|openings|op|apertura|cabecera|generique|générique|vorspann)\b""")

    private fun prefs(c: Context) = c.getSharedPreferences("glcn_intro", Context.MODE_PRIVATE)
    fun key(series: String) = series.trim().lowercase()
    fun learned(c: Context, series: String): Long = prefs(c).getLong(key(series), -1L)
    fun learn(c: Context, series: String, endMs: Long) { prefs(c).edit().putLong(key(series), endMs).apply() }

    /**
     * Watches [engine] while an episode plays: learns jumps, and returns whether the button is up.
     * [controlsHidden] — the button stands in for the controls, never over them.
     */
    @Composable
    fun Chip(
        engine: PlaybackEngine, controlsHidden: Boolean, autoSkip: Boolean, onVisible: (Boolean) -> Unit, modifier: Modifier = Modifier,
        /** False while the core's own skip has times for this item, or intros are switched off: no button, no jump. */
        introOn: Boolean = true,
        /** The same for the end credits: false leaves the next-episode card to the core. */
        creditsOn: Boolean = true,
    ) {
        val context = LocalContext.current
        val meta by engine.currentMeta.collectAsStateWithLifecycle()
        val series = meta.subtitle?.takeIf { meta.episodeNumber != null && it.isNotBlank() }
        val position by engine.position.collectAsStateWithLifecycle()
        // glcn: a file whose chapters name its intro ("Intro", "Opening") says exactly where it ends — films too.
        val duration by engine.duration.collectAsStateWithLifecycle()
        var creditsChapter by remember(meta) { mutableLongStateOf(-1L) }
        val introChapterEnd by androidx.compose.runtime.produceState(-1L, meta, duration > 0) {
            val chapters = if (duration > 0) runCatching { engine.chapters() }.getOrDefault(emptyList()) else emptyList()
            value = if (duration > 0) introEnd(chapters) else -1L
            chapterEnd = if (introOn) value else -1L
            creditsChapter = if (duration > 0) creditsStart(chapters, duration) else -1L
        }
        LaunchedEffect(introOn, introChapterEnd) { chapterEnd = if (introOn) introChapterEnd else -1L }
        // glcn: the end credits from the file's own chapter; only where it can be credits.
        LaunchedEffect(meta, creditsChapter, duration, creditsOn) {
            val at = creditsChapter.takeIf { creditsOn } ?: -1L
            creditsAt.value = at.takeIf { duration > 0 && it > duration / 2 && it < duration - CREDITS_TAIL_MS } ?: -1L
        }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { creditsAt.value = -1L; chapterEnd = -1L } }
        var learnedEnd by remember(series) { mutableLongStateOf(series?.let { learned(context, it) } ?: -1L) }
        var autoDone by remember(meta) { mutableStateOf(false) }

        // Learning: a forward jump early in the episode, then watching on, marks the intro's end.
        var last by remember(meta) { mutableLongStateOf(-1L) }
        var candidate by remember(meta) { mutableLongStateOf(-1L) }
        // Once the viewer has jumped past the start (with the button or by seeking), the button is done
        // for this episode — a first skip of a typical length could otherwise land still inside its window.
        var jumped by remember(meta) { mutableStateOf(false) }
        LaunchedEffect(position, series) {
            if (series == null) return@LaunchedEffect
            val prev = last
            last = position
            if (prev > 3_000L && position - prev > JUMP_MS && prev < WINDOW_MS && position < WINDOW_MS + DEFAULT_JUMP_MS) {
                candidate = position
                jumped = true
            }
        }
        LaunchedEffect(candidate) {
            if (candidate < 0 || series == null) return@LaunchedEffect
            val at = candidate
            delay(SETTLE_MS)
            if (candidate == at && position >= at) {
                learn(context, series, at)
                learnedEnd = at
            }
        }

        // From FALLBACK_FROM_MS: the core's times for the episode arrive in its first seconds, and this must not
        // show and then give way to the core's own button.
        val inWindow = when {
            !introOn -> false
            introChapterEnd > 0 -> position in FALLBACK_FROM_MS until (introChapterEnd - 3_000L)
            series == null -> false
            learnedEnd > 0 -> position in FALLBACK_FROM_MS until (learnedEnd - 3_000L)
            else -> false // glcn: nothing known — no blind jump
        }
        val exactEnd = when {
            introChapterEnd > 0 -> introChapterEnd
            else -> learnedEnd
        }
        // Auto-skip: once per episode, only to a learned end.
        LaunchedEffect(inWindow, autoSkip, exactEnd) {
            if (autoSkip && inWindow && exactEnd > 0 && !autoDone) {
                autoDone = true
                engine.seekBy(exactEnd - position)
            }
        }
        val visible = inWindow && !jumped && controlsHidden && !(autoSkip && exactEnd > 0)
        LaunchedEffect(visible) { onVisible(visible) }
        Box(modifier) {
            AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd)) {
                Row(
                    Modifier
                        .padding(end = 64.mpx, bottom = 72.mpx)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.mpx))
                        .border(2.mpx, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.mpx))
                        .padding(horizontal = 26.mpx, vertical = 14.mpx),
                ) {
                    Text(stringResource(R.string.glcn_skip_intro) + "   OK", style = stageText(22, 800), color = Color.White)
                }
            }
        }
    }

    /** The button's action: to the learned end, or a typical intro length on. */
    fun skip(context: Context, engine: PlaybackEngine) {
        val pos = engine.position.value
        // glcn: the intro chapter's end, when the file names one.
        chapterEnd.takeIf { it > pos }?.let { engine.seekBy(it - pos); return }
        val series = engine.currentMeta.value.subtitle ?: return
        val end = learned(context, series)
        if (end > pos) engine.seekBy(end - pos)
    }
}
