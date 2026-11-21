package tv.own.owntv.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * glcn: a logo made for dark backgrounds, readied for a light one. Only its white parts that stand on the
 * transparent background turn grey — the white "por M+" beside a red "Estrenos", Telecinco's white ring, AXN's white
 * letters — found by spreading from the transparent edge through near-white pixels. Every coloured part keeps its
 * own colour, untouched, and white enclosed by colour (La 2's white "2" on its blue disc) stays white, since there
 * it already reads.
 */
object LogoOnLight : Transformation() {
    override val cacheKey: String = "glcn-logo-on-light-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        val w = input.width; val h = input.height
        if (w < 4 || h < 4) return input
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        val pale = BooleanArray(px.size) { isPale(px[it]) }
        val clear = BooleanArray(px.size) { (px[it] ushr 24) < CLEAR_ALPHA }
        val grey = BooleanArray(px.size)
        val queue = IntArray(px.size)
        var head = 0; var tail = 0
        // Seeds: pale pixels touching the transparent background (or the picture's edge).
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!pale[i]) continue
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                clear[i - 1] || clear[i + 1] || clear[i - w] || clear[i + w]
            if (edge) { grey[i] = true; queue[tail++] = i }
        }
        if (tail == 0) return input
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            if (x > 0) tail = visit(i - 1, pale, grey, queue, tail)
            if (x < w - 1) tail = visit(i + 1, pale, grey, queue, tail)
            if (y > 0) tail = visit(i - w, pale, grey, queue, tail)
            if (y < h - 1) tail = visit(i + w, pale, grey, queue, tail)
        }
        for (i in px.indices) {
            if (!grey[i]) continue
            val c = px[i]
            val a = c ushr 24
            val v = maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            val g = (v * SHADE).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (g shl 16) or (g shl 8) or g
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun visit(j: Int, pale: BooleanArray, grey: BooleanArray, queue: IntArray, tail: Int): Int {
        if (!pale[j] || grey[j]) return tail
        grey[j] = true
        queue[tail] = j
        return tail + 1
    }

    /** Near white with almost no colour, and not invisible. */
    private fun isPale(c: Int): Boolean {
        if ((c ushr 24) < CLEAR_ALPHA) return false
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        return max >= PALE_MIN_VALUE && (max - min) <= PALE_MAX_SPREAD
    }

    private const val CLEAR_ALPHA = 40
    private const val PALE_MIN_VALUE = 170
    private const val PALE_MAX_SPREAD = 40
    /** White lands on this fraction of itself: a mid grey that reads on a light page. */
    private const val SHADE = 0.55f
}
