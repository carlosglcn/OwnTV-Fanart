package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * glcn: one pace for every text that drifts (a long synopsis, a long title under the focus): it waits a
 * moment so the first words can be read, then moves slowly enough to be read as it goes.
 */
object DriftPace {
    const val START_MS = 3_200
    const val MS_PER_PX = 65

    /** The same speed for sideways drift, as a velocity in dp per second. */
    @Composable
    fun velocity(): Dp = with(LocalDensity.current) { (1000f / MS_PER_PX).toDp() }
}
