package tv.own.owntv.glcn

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * glcn: a true soft blur for the small backdrops behind the text — a 160 × 90 picture stretched over half the
 * screen showed its pixels as blocks. Blurring the small copy (a few box passes, cheap at this size) makes the
 * stretch a smooth wash. No RenderEffect: the SHIELD's Android has none.
 */
class SoftBlur(private val radius: Int = 4, private val passes: Int = 3) : Transformation() {
    override val cacheKey: String = "glcn.softblur.$radius.$passes"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 3 || h < 3) return input
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(passes) {
            boxPass(px, tmp, w, h, radius, horizontal = true)
            boxPass(tmp, px, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            for (i in -r..r) { val p = at(i); a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255 }
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val pOut = at(i - r); val pIn = at(i + r + 1)
                a += (pIn ushr 24) - (pOut ushr 24)
                rr += ((pIn shr 16) and 255) - ((pOut shr 16) and 255)
                g += ((pIn shr 8) and 255) - ((pOut shr 8) and 255)
                b += (pIn and 255) - (pOut and 255)
            }
        }
    }
}
