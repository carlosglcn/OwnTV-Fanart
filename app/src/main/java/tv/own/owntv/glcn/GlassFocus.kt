package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** glcn: with Glass on, row and card focus is drawn as tinted glass (see drawStageFocus). */
object GlassFocus {
    var on by mutableStateOf(false)
    /** glcn: a sheet over the video (the player's channel list) is up — its rows focus as glass too, Glass on or not. */
    var overVideo by mutableStateOf(0)
    val active: Boolean get() = on || overVideo > 0
}
