package tv.own.owntv.glcn

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.math.floor

/**
 * glcn: how a row of cards follows the focus so it always rests with whole cards — the first one exactly
 * where the row starts ([start], the row's left padding), never a sliver of the card before it. The cards
 * stand on slots start, start + one card and gap, start + two… ; the focus may roam between the second
 * slot and the last-but-one (a card of room on each side, so the next one is always laid out for a held
 * key), and when it goes past either end the row glides by whole cards to put it back.
 */
class WholeCardScroll(private val start: Float, private val end: Float, private val gap: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> get() = SmoothScroll.animation

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size <= 0f || size > containerSize) return offset - start
        val step = size + gap
        val slots = floor((containerSize - end - start - size) / step).toInt().coerceAtLeast(0)
        val room = slots >= 2
        val leftPos = start + if (room) step else 0f
        val rightPos = start + (if (room) slots - 1 else slots) * step
        return when {
            offset < leftPos - 0.5f -> offset - leftPos
            offset > rightPos + 0.5f -> offset - rightPos
            else -> 0f
        }
    }
}
