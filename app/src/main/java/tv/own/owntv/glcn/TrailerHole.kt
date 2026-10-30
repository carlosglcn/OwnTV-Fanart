package tv.own.owntv.glcn

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * glcn: a trailer plays UNDER the still picture, and once it runs a soft hole opens in the picture where it is. The
 * window's edges are then always the picture itself, melting into whatever is around it (the page, its tint and
 * light, another picture) — a fade painted in one flat colour over the video left a hard box wherever the
 * surroundings were not exactly that colour. The mask sits on the still layer: no video frame redraws it.
 */
object TrailerHole {

    /**
     * An alpha mask (small, drawn stretched): 1 inside the rectangle [left]..[right] × [top]..[bottom] (shares of the
     * layer), easing to 0 over [blendX] / [blendY] towards each side that is inside the layer. A side on the layer's
     * own border stays open.
     */
    fun mask(left: Float, top: Float, right: Float, bottom: Float, blendX: Float, blendY: Float): ImageBitmap {
        val w = MASK_W
        val h = MASK_H
        fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val fy = (y + 0.5f) / h
            val vy = (if (top > 0f) smooth((fy - top) / blendY) else if (fy >= top) 1f else 0f) *
                (if (bottom < 1f) smooth((bottom - fy) / blendY) else 1f)
            for (x in 0 until w) {
                val fx = (x + 0.5f) / w
                val vx = (if (left > 0f) smooth((fx - left) / blendX) else 1f) *
                    (if (right < 1f) smooth((right - fx) / blendX) else 1f)
                px[y * w + x] = ((vx * vy * 255f).toInt().coerceIn(0, 255)) shl 24
            }
        }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val MASK_W = 192
    private const val MASK_H = 108
}

/**
 * glcn: on a full-size picture, the hole for a trailer window at the top right ([widthFraction] of the width, [height]
 * tall), opening by [amount] (0 = none). The layer goes offscreen only while the hole is open.
 */
fun Modifier.topRightTrailerHole(widthFraction: Float, height: Dp, blend: Dp, amount: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = if (amount() > 0.001f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithCache {
        val left = 1f - widthFraction
        val bottom = (height.toPx() / size.height).coerceIn(0.05f, 1f)
        val mask = TrailerHole.mask(left, 0f, 1f, bottom, blend.toPx() / size.width, blend.toPx() / size.height)
        val dst = IntSize(kotlin.math.ceil(size.width).toInt(), kotlin.math.ceil(size.height).toInt())
        onDrawWithContent {
            drawContent()
            val a = amount()
            if (a > 0.001f) drawImage(mask, dstSize = dst, alpha = a, blendMode = BlendMode.DstOut)
        }
    }

/**
 * glcn: the same hole inside a picture that already melts at its left and bottom ([start] / [bottom] shares), as on
 * classic Home: the hole starts where the picture is solid. Needs an offscreen layer above it (the melting mask's).
 */
fun Modifier.innerTrailerHole(start: Float, bottom: Float, blend: Float, amount: () -> Float): Modifier = this
    .drawWithCache {
        val mask = TrailerHole.mask(start, 0f, 1f, 1f - bottom, blend, blend * size.width / size.height)
        val dst = IntSize(kotlin.math.ceil(size.width).toInt(), kotlin.math.ceil(size.height).toInt())
        onDrawWithContent {
            drawContent()
            val a = amount()
            if (a > 0.001f) drawImage(mask, dstSize = dst, alpha = a, blendMode = BlendMode.DstOut)
        }
    }

/** glcn: lays the content out in its box minus a [start] share on the left and a [bottom] share below. */
fun Modifier.insetShare(start: Float, bottom: Float): Modifier = this.layout { m, c ->
    val w = c.maxWidth
    val h = c.maxHeight
    val l = (w * start).toInt()
    val b = (h * bottom).toInt()
    val p = m.measure(Constraints.fixed((w - l).coerceAtLeast(1), (h - b).coerceAtLeast(1)))
    layout(w, h) { p.placeRelative(l, 0) }
}
