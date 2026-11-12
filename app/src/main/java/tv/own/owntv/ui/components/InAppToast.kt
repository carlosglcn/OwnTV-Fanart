package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.mpx
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * A small in-app toast: a transient, themed message pinned to the bottom-center of the screen — nicer than a
 * system [android.widget.Toast] on a TV (legible app font, OwnTV colors). Auto-dismisses after ~2.2s, or
 * after the duration [InAppToastState.show] was given for a message too long to read in that time.
 *
 * Usage: `val toast = rememberInAppToast()` near the top of a screen; call `toast.show("…")` from a click
 * lambda; render [InAppToast] once as a sibling overlay (it stacks like the long-press menus). Emits nothing
 * while idle, so it costs nothing and never intercepts D-pad focus when there's no message.
 */
@Stable
class InAppToastState {
    internal var message by mutableStateOf<String?>(null)
        private set
    internal var tick by mutableStateOf(0L)
        private set
    internal var durationMs by mutableStateOf(DEFAULT_DURATION_MS)
        private set

    fun show(text: String, durationMs: Long = DEFAULT_DURATION_MS) {
        message = text
        this.durationMs = durationMs
        tick++
    }

    internal fun clear() {
        message = null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 2_200L
    }
}

@Composable
fun rememberInAppToast(): InAppToastState = remember { InAppToastState() }

@Composable
fun InAppToast(state: InAppToastState) {
    val msg = state.message ?: return
    // Re-run the timer every time show() bumps the tick (so repeated identical messages re-trigger).
    LaunchedEffect(state.tick) {
        delay(state.durationMs)
        state.clear()
    }
    // A Stage glass pill at the bottom centre, as the sync status pill.
    Box(
        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.BottomCenter).padding(bottom = 90.mpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            msg,
            modifier = Modifier
                .stageGlass(30.mpx, overContent = true)
                .padding(horizontal = 30.mpx, vertical = 16.mpx),
            style = tv.own.owntv.ui.theme.stageText(19, 700),
            color = tv.own.owntv.ui.theme.StageColors.Text,
        )
    }
}

/**
 * glcn: the app-wide toast, for messages raised outside a screen (a view model, a sync, a dialog's failure): the
 * same glass pill as [InAppToast], shown over everything by MainActivity — instead of Android's grey system toast.
 */
object AppToast {
    val state = InAppToastState()
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /** Safe from any thread. [long] keeps it up for a message that takes longer to read. */
    fun show(text: String, long: Boolean = false) {
        main.post { state.show(text, if (long) LONG_MS else InAppToastState.DEFAULT_DURATION_MS) }
    }

    fun show(context: android.content.Context, res: Int, long: Boolean = false) = show(context.getString(res), long)

    private const val LONG_MS = 4_000L
}
