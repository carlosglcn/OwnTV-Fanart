package tv.own.owntv.glcn

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/**
 * glcn: the app's pace — every menu, focus glide, scroll and transition runs at [APP] of its written
 * length, the brisk feel of a debloated SHIELD with its animation scale at 0.5x, whatever the box is
 * set to. Compose times all of its animations by one factor (its MotionDurationScale), which it
 * normally takes from Android's "Animator duration scale"; here the activity's compositions get a
 * factor of their own:
 *
 * - Android at 1x (the default) → [APP]; set faster than that (0.5x) → the box's own, never slower.
 * - Android at 0 (animations off, an accessibility choice) → 0: still off.
 * - The slow, ambient motion (Ken Burns, backdrop drifts) is written through [slow], so it keeps its
 *   calm and only the things people steer get quicker.
 *
 * Installed by the activity before `setContent`: the window's composition then runs on this
 * recomposer instead of the one Compose would make (same frame clock, paused while the app is not
 * on screen, cancelled with the activity).
 */
object Pace {
    const val APP = 0.6f

    /** The factor in force; read by [slow]. */
    @Volatile var factor: Float = APP
        private set

    /** An ambient duration, lengthened back so it plays at its written speed under [APP]. */
    fun slow(ms: Int): Int = if (factor <= 0f) ms else (ms / factor.coerceAtMost(1f)).toInt()

    private fun effective(system: Float): Float = when {
        system <= 0f -> 0f
        else -> minOf(system, APP)
    }

    private fun systemScale(activity: ComponentActivity): Float = runCatching {
        Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    private class Scale(@Volatile override var scaleFactor: Float) : MotionDurationScale

    fun install(activity: ComponentActivity): Recomposer {
        val scale = Scale(effective(systemScale(activity))).also { factor = it.scaleFactor }
        val base = AndroidUiDispatcher.CurrentThread
        val clock = base[MonotonicFrameClock]?.let { PausableMonotonicFrameClock(it).apply { pause() } }
        val context = base + (clock ?: EmptyCoroutineContext) + scale
        val recomposer = Recomposer(context)
        val scope = CoroutineScope(context)
        activity.window.decorView.compositionContext = recomposer

        // The box's setting can change while the app runs (developer options, a debloat script).
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale.scaleFactor = effective(systemScale(activity))
                factor = scale.scaleFactor
            }
        }
        runCatching { activity.contentResolver.registerContentObserver(uri, false, observer) }

        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
                    }
                    Lifecycle.Event.ON_START -> clock?.resume()
                    Lifecycle.Event.ON_STOP -> clock?.pause()
                    Lifecycle.Event.ON_DESTROY -> {
                        recomposer.cancel()
                        source.lifecycle.removeObserver(this)
                        runCatching { activity.contentResolver.unregisterContentObserver(observer) }
                    }
                    else -> Unit
                }
            }
        })
        return recomposer
    }
}
