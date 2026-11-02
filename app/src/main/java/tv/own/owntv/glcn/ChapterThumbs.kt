package tv.own.owntv.glcn

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.own.owntv.player.PlayerMediaSource
import java.io.File
import java.security.MessageDigest

/**
 * glcn: a picture for each chapter of a film, for the Chapters tab.
 *
 * 1. Emby / Jellyfin keep one image per chapter when the library has "chapter image extraction" on: asked for by the
 *    item's id, straight away.
 * 2. Otherwise a frame is read from the film itself ([Frames]) — one at a time, only while the tab is open — and kept
 *    in the app's cache, so the next time it is there at once. Works for web addresses and local files; a network
 *    share (SMB) cannot be read this way.
 * 3. When neither gives a picture the card shows the film's backdrop with the chapter's number.
 */
object ChapterThumbs {
    /** "Chapter 01", "Capítulo 3", "Chapitre 2", "05", "00:12:30.000" — names that say nothing but the number. */
    private val GENERIC = Regex(
        """^\s*(chapter|chapitre|cap[ií]tulo|kapitel|capitolo|hoofdstuk|kapittel|rozdział|cap\.?|ch\.?)?\s*#?\s*\d{1,3}\s*$|^\s*\d{1,2}:\d{2}:\d{2}([.,]\d+)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun isGeneric(title: String?): Boolean = title.isNullOrBlank() || GENERIC.matches(title)

    /** Emby / Jellyfin play from `…/Videos/{id}/stream…?…api_key=…`. */
    private val SERVER = Regex("""^(https?://.+?)/Videos/([0-9A-Za-z-]+)/stream[^?]*\?(.*)$""")

    private fun serverImage(url: String, index: Int): String? {
        val m = SERVER.find(url) ?: return null
        val (base, id, query) = m.destructured
        val token = query.split('&').firstNotNullOfOrNull { p ->
            val k = p.substringBefore('=')
            if (k.equals("api_key", true) || k.equals("ApiKey", true)) p.substringAfter('=', "") else null
        }?.takeIf { it.isNotEmpty() } ?: return null
        return "$base/Items/$id/Images/Chapter/$index?maxWidth=$THUMB_W&api_key=$token"
    }

    private val serverAnswers = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** The server's chapter pictures for [src], one per chapter, when it has them; null otherwise. */
    suspend fun serverImages(src: PlayerMediaSource?, count: Int): List<String>? = withContext(Dispatchers.IO) {
        val url = src?.url ?: return@withContext null
        val first = serverImage(url, 0) ?: return@withContext null
        val has = serverAnswers[url] ?: runCatching {
            val c = java.net.URL(first).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 4_000; c.readTimeout = 4_000
            try { c.responseCode == 200 && c.contentType.orEmpty().startsWith("image") } finally { c.disconnect() }
        }.getOrDefault(false).also { serverAnswers[url] = it }
        if (!has) null else (0 until count).mapNotNull { serverImage(url, it) }
    }

    /** Whether frames can be read from [src] at all (a web address or a local file). */
    fun canReadFrames(src: PlayerMediaSource?): Boolean {
        val u = src?.url ?: return false
        return u.startsWith("http://", true) || u.startsWith("https://", true) || u.startsWith("/") || u.startsWith("file://", true)
    }

    /** The cached frame for [src] at [ms], if one was read before. */
    fun cached(context: Context, src: PlayerMediaSource, ms: Long): File? = fileFor(context, src, ms).takeIf { it.length() > 0 }

    private fun fileFor(context: Context, src: PlayerMediaSource, ms: Long): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File(dir, "${hash(src.url)}_$ms.jpg")
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(10).joinToString("") { "%02x".format(it) }

    /** One opened film to read frames from; [close] when the tab goes. Reads are one at a time across the app. */
    class Frames(private val context: Context, private val src: PlayerMediaSource) {
        private var mmr: MediaMetadataRetriever? = null
        private var broken = false

        suspend fun at(ms: Long): File? = withContext(Dispatchers.IO) {
            cached(context, src, ms)?.let { return@withContext it }
            if (broken) return@withContext null
            lock.withLock {
                runCatching {
                    val r = mmr ?: MediaMetadataRetriever().also { m ->
                        val u = src.url
                        if (u.startsWith("http", true)) m.setDataSource(u, src.headers) else m.setDataSource(u.removePrefix("file://"))
                        mmr = m
                    }
                    val us = ms * 1000
                    val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 27) {
                        r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_W, THUMB_H)
                    } else {
                        r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                            Bitmap.createScaledBitmap(full, THUMB_W, THUMB_H, true).also { if (it !== full) full.recycle() }
                        }
                    }
                    bmp ?: return@runCatching null
                    val out = fileFor(context, src, ms)
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    bmp.recycle()
                    out
                }.onFailure { broken = true }.getOrNull()
            }
        }

        fun close() {
            runCatching { mmr?.release() }
            mmr = null
        }
    }

    private val lock = Mutex()
    private const val DIR = "chapter_thumbs"
    private const val THUMB_W = 480
    private const val THUMB_H = 270
}
