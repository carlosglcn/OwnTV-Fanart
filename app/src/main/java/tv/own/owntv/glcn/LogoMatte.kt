package tv.own.owntv.glcn

import android.graphics.Bitmap
import android.graphics.Color
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation

/**
 * glcn: a channel logo that arrives on a plain white square (a profile picture, a quick picon) sits on
 * the tile like a sticker. This takes that backdrop away: only when all four corners are white, and only
 * the white reachable from the edges, so white inside the mark itself stays. Logos only — see
 * [LogoMatteInterceptor] and tv.own.owntv.core.epg.ChannelLogoUrls.
 */
object WhiteMatte : Transformation() {
    override val cacheKey: String = "glcn.whitematte.1"

    private const val MAX_PIXELS = 1_200_000
    private const val SOLID = 242   // at or above: backdrop, fully clear
    private const val EDGE = 214    // between EDGE and SOLID: the soft rim, partly clear
    private const val TINT = 22     // max spread between channels for a pixel to count as white

    private fun lum(c: Int): Int = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
    private fun whiteish(c: Int, floor: Int): Boolean {
        if (Color.alpha(c) < 250) return false
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return minOf(r, g, b) >= floor && maxOf(r, g, b) - minOf(r, g, b) <= TINT
    }

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 8 || h < 8 || w * h > MAX_PIXELS) return input
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        // All four corners white, or this logo has a backdrop of its own on purpose: leave it.
        val corners = intArrayOf(0, w - 1, (h - 1) * w, (h - 1) * w + w - 1)
        if (corners.any { !whiteish(px[it], SOLID) }) return input
        // Flood from every edge pixel through white: the backdrop, never the mark's own white inside.
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun push(i: Int) { if (!seen[i] && whiteish(px[i], EDGE)) { seen[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (i >= w) push(i - w)
            if (i < (h - 1) * w) push(i + w)
        }
        // Nearly all of it white: the logo is white on white, or blank — better kept as it came.
        if (tail > w * h * 0.97) return input
        for (k in 0 until tail) {
            val i = queue[k]
            val l = lum(px[i])
            val a = if (l >= SOLID) 0 else ((SOLID - l) * 255 / (SOLID - EDGE)).coerceIn(0, 255)
            px[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}

/** glcn: hands [WhiteMatte] to every request for an address the app gave out as a channel logo. */
class LogoMatteInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed()
        if (!tv.own.owntv.core.epg.ChannelLogoUrls.has(url) || request.transformations.any { it.cacheKey == WhiteMatte.cacheKey }) {
            return chain.proceed()
        }
        val matte = request.newBuilder()
            .transformations(request.transformations + WhiteMatte)
            .allowHardware(false)
            .build()
        return chain.withRequest(matte).proceed()
    }
}
