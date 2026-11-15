package tv.own.owntv.glcn

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * glcn: a window that opens from [from] (a card's place on screen) to the whole of what it clips, at [t] (0 → 1),
 * its corners easing from a card's to square — a page growing out of the poster it was opened from.
 */
class RevealShape(private val from: Rect, private val t: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = Rect(0f, 0f, size.width, size.height)
        val r = lerp(from, full, t.coerceIn(0f, 1f))
        val corner = with(density) { CARD_CORNER_DP * this.density } * (1f - t).coerceIn(0f, 1f)
        return Outline.Generic(Path().apply { addRoundRect(RoundRect(r, CornerRadius(corner, corner))) })
    }

    private companion object {
        const val CARD_CORNER_DP = 16f
    }
}
