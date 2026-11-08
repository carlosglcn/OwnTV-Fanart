package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * glcn: the player's channel list (the zap deck) as a squeeze-back, as sports channels do: while it is open the
 * picture shrinks into the free space to the right of the list, still playing and never covered, and the list
 * stands on a plain page beside it. The deck says where the picture goes ([frame], measured); the shell moves the
 * picture there and reports how far it has got ([progress]).
 */
object ZapStage {
    var liveWindow by mutableStateOf(false)
    /** How far the picture has shrunk into [frame] (0 full screen → 1 in place), set by the shell. */
    var progress by mutableFloatStateOf(0f)
    /** Where the picture goes, in window coordinates; null until the deck has laid itself out. */
    var frame by mutableStateOf<Rect?>(null)

    /** The space right of the deck, in window coordinates: a 16:9 picture as wide as it allows, centred in it. */
    fun fit(left: Float, right: Float, top: Float, bottom: Float): Rect? {
        val w = right - left
        val h = bottom - top
        if (w <= 1f || h <= 1f) return null
        val pw = minOf(w, h * 16f / 9f)
        val ph = pw * 9f / 16f
        val x = left + (w - pw) / 2f
        val y = top + (h - ph) / 2f
        return Rect(x, y, x + pw, y + ph)
    }

    /** Where the shell should put the picture in a box at [origin]: [frame] when the deck has one. */
    fun rect(origin: Offset): Rect? = frame?.takeIf { liveWindow }?.translate(-origin.x, -origin.y)
}
