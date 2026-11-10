package tv.own.owntv.glcn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import tv.own.owntv.R
import tv.own.owntv.player.SleepTimer
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: the sleep timer on a television.
 *
 * - **The preview pane and Multiview count as playing** ([elsewhere]). The timer drops itself when
 *   nothing plays, and it used to read only the player: closing the player back to the list, with the
 *   channel carrying on in the preview, cancelled it three seconds later — the "it does not work" reports.
 * - **The last minute dims the picture** and says so; any key gives [EXTEND_MS] more.
 * - **When it fires**, a black screen ([ended]) stays until a key: nothing bright is left on, and with
 *   playback stopped the window no longer keeps the screen awake, so the TV's own sleep takes over even
 *   where the "turn the screen off" grant is not available.
 */
object SleepWatch {
    /** Something plays outside the player: the preview pane or a Multiview grid. */
    val elsewhere = MutableStateFlow(false)

    /** The timer stopped playback; the black screen shows until a key. */
    val ended = MutableStateFlow(false)

    /** Set by the activity, so a key can give more time. */
    @Volatile var timer: SleepTimer? = null

    const val WARN_MS = 60_000L
    const val EXTEND_MS = 15 * 60_000L
    private const val DIM_MAX = 0.55f

    /** Called from the activity's key dispatch, before anything else: true = the key was used here. */
    fun onKey(e: android.view.KeyEvent): Boolean {
        if (ended.value) {
            if (e.action == android.view.KeyEvent.ACTION_UP) ended.value = false
            return true
        }
        val t = timer ?: return false
        val left = t.remainingMs.value ?: return false
        if (left !in 1..WARN_MS) return false
        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) t.start(EXTEND_MS)
        return true
    }

    /** The dimming and notice of the last minute, and the black screen after. Drawn over everything. */
    @Composable
    fun Overlay(timer: SleepTimer) {
        val remaining by timer.remainingMs.collectAsStateWithLifecycle()
        val done by ended.collectAsStateWithLifecycle()
        val left = remaining
        val warning = left != null && left in 1..WARN_MS
        if (warning) {
            val dim = DIM_MAX * (1f - left!!.toFloat() / WARN_MS)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))
        }
        AnimatedVisibility(warning, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(top = 48.mpx), contentAlignment = Alignment.TopCenter) {
                val secs = ((left ?: 0L) + 999L) / 1000L
                Row(
                    Modifier.background(Color(0xE6101722), RoundedCornerShape(22.mpx)).padding(horizontal = 26.mpx, vertical = 16.mpx),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(OwnTVIcon.CLOCK, Color.White, Modifier.size(26.mpx))
                    Column {
                        androidx.tv.material3.Text(
                            stringResource(R.string.glcn_sleep_warn_title, secs / 60, (secs % 60).toInt()),
                            style = stageText(21, 700), color = Color.White, maxLines = 1,
                        )
                        androidx.tv.material3.Text(
                            stringResource(R.string.glcn_sleep_warn_hint),
                            style = stageText(16, 500), color = Color.White.copy(alpha = 0.75f), maxLines = 1,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(done, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    OwnTVIcon(OwnTVIcon.CLOCK, Color.White.copy(alpha = 0.35f), Modifier.size(40.mpx))
                    androidx.tv.material3.Text(
                        stringResource(R.string.glcn_sleep_ended_title),
                        style = stageText(22, 600), color = Color.White.copy(alpha = 0.45f), maxLines = 1,
                    )
                    androidx.tv.material3.Text(
                        stringResource(R.string.glcn_sleep_ended_hint),
                        style = stageText(16, 500), color = Color.White.copy(alpha = 0.3f), maxLines = 1,
                    )
                }
            }
        }
    }
}
