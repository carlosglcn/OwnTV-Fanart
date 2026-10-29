package tv.own.owntv.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * glcn: a channel logo drawn on a wide transparent canvas (the provider's "Bajo Demanda" banner, a small mark in the
 * middle of a big empty picture) shrank to a speck when fitted into its tile. This crops the empty margin away first,
 * leaving a little air, so every logo fills its tile the same way. A logo without transparency is left as it is.
 */
object LogoTrim : Transformation() {
    override val cacheKey: String = "glcn-logo-trim-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha() || input.width < 8 || input.height < 8) return input
        val w = input.width; val h = input.height
        val row = IntArray(w)
        var top = -1; var bottom = -1; var left = w; var right = -1
        for (y in 0 until h) {
            input.getPixels(row, 0, w, 0, y, w, 1)
            var any = false
            for (x in 0 until w) {
                if ((row[x] ushr 24) > ALPHA_MIN) {
                    any = true
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            if (any) { if (top < 0) top = y; bottom = y }
        }
        if (top < 0 || right < left) return input
        val cw = right - left + 1; val ch = bottom - top + 1
        // Nothing worth trimming: the artwork already fills the picture.
        if (cw * ch >= w * h * KEEP_SHARE) return input
        val pad = (maxOf(cw, ch) * PAD).toInt()
        val l = (left - pad).coerceAtLeast(0); val t = (top - pad).coerceAtLeast(0)
        val r = (right + pad).coerceAtMost(w - 1); val b = (bottom + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(input, l, t, r - l + 1, b - t + 1)
    }

    private const val ALPHA_MIN = 24
    private const val KEEP_SHARE = 0.8f
    private const val PAD = 0.06f
}
