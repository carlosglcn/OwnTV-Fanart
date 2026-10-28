package tv.own.owntv.glcn

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the fluency meter (Settings › Fluency meter). Android reports how long every frame the app actually drew
 * took; a frame over the screen's budget (16.7 ms at 60 Hz) is a slow one — what the eye sees as a stutter when
 * a row glides or a picture fades. The meter counts them per screen, from the moment the screen opened, so a
 * figure can be read off each one ("Home · slow 1.8 % · worst 62 ms") and compared before and after a change.
 *
 * It costs next to nothing: the counting runs on its own thread, and the corner box redraws twice a second.
 */
private class Counts {
    var frames = 0L
    var slow = 0L
    var stutters = 0L
    var worstNs = 0L
    var recentFrames = 0
    var windowStart = System.nanoTime()
    var fps = 0
}

@Composable
fun FrameMeter(screen: String, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val counts = remember { Counts() }
    val lock = remember { Any() }
    var line by remember { mutableStateOf("") }
    var memLine by remember { mutableStateOf("") }
    // A new screen starts its own count.
    LaunchedEffect(screen) {
        synchronized(lock) {
            counts.frames = 0; counts.slow = 0; counts.stutters = 0; counts.worstNs = 0
            counts.recentFrames = 0; counts.windowStart = System.nanoTime()
        }
    }
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val refresh = view.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        val budgetNs = (1_000_000_000L / refresh).toLong()
        val thread = HandlerThread("frame-meter").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            synchronized(lock) {
                counts.frames++
                counts.recentFrames++
                if (total > budgetNs) counts.slow++
                if (total > budgetNs * 3) counts.stutters++
                if (total > counts.worstNs) counts.worstNs = total
                val now = System.nanoTime()
                if (now - counts.windowStart >= 1_000_000_000L) {
                    counts.fps = (counts.recentFrames * 1_000_000_000L / (now - counts.windowStart)).toInt()
                    counts.recentFrames = 0
                    counts.windowStart = now
                }
            }
        }
        runCatching { window?.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        onDispose {
            runCatching { window?.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }
    val slowLabel = stringResource(R.string.glcn_fps_line)
    val memLabel = stringResource(R.string.glcn_fps_memory)
    LaunchedEffect(screen, slowLabel) {
        var tick = 0
        while (true) {
            val (frames, slow, stutters, worst, fps) = synchronized(lock) {
                listOf(counts.frames, counts.slow, counts.stutters, counts.worstNs / 1_000_000L, counts.fps.toLong())
            }
            val pct = if (frames > 0) slow * 100.0 / frames else 0.0
            line = slowLabel.format(screen, fps, pct, worst, stutters)
            if (tick++ % 2 == 0) {
                val rt = Runtime.getRuntime()
                val heap = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
                val native = android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576L
                memLine = memLabel.format(heap, native)
            }
            delay(500)
        }
    }
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.mpx))
            .padding(horizontal = 14.mpx, vertical = 8.mpx),
    ) {
        Text(line, style = stageText(15, 700), color = Color.White, maxLines = 1)
        Text(memLine, style = stageText(13, 500), color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
