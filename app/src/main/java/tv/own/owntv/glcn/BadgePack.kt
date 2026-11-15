package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn

/**
 * glcn: the quality badges as pictures, from a badge pack the person picks — the app ships none. A pack is a
 * `badges.json` in the Fusion style ({"filters": [{id, groupId, name, pattern, imageURL}, …]}) on the web; the app reads
 * it once, keeps it on the box, and for each of its own quality marks ("4K", "Dolby Vision", "TrueHD", "5.1"…) shows
 * the pack's picture whose pattern matches that mark best. A mark the pack has no picture for stays as text.
 */
object BadgePack {
    enum class Mode { TEXT, ELITE, CUSTOM }

    /** Elite Badges by leonevz — offered by name; fetched from the author's own repository. */
    const val ELITE_URL = "https://raw.githubusercontent.com/leonevz/Elite-Badges/main/badges.json"

    class Filter(val id: String, val group: String, val name: String, val pattern: Regex, val image: String)

    var mode by mutableStateOf(Mode.TEXT)
        private set
    var customUrl by mutableStateOf("")
        private set
    /** The pack in use, or null (text badges). */
    var filters by mutableStateOf<List<Filter>>(emptyList())
        private set
    /** Loading, done, or failed — for the settings row's line. */
    var status by mutableStateOf(Status.IDLE)
        private set

    enum class Status { IDLE, LOADING, READY, FAILED }

    private val picked = HashMap<String, String?>()

    fun init(context: Context) {
        val p = prefs(context)
        mode = runCatching { Mode.valueOf(p.getString(KEY_MODE, Mode.TEXT.name)!!) }.getOrDefault(Mode.TEXT)
        customUrl = p.getString(KEY_URL, "").orEmpty()
        if (mode != Mode.TEXT) runCatching { cache(context).takeIf { it.exists() }?.readText()?.let { use(parse(it)) } }
    }

    fun urlFor(m: Mode): String? = when (m) {
        Mode.TEXT -> null
        Mode.ELITE -> ELITE_URL
        Mode.CUSTOM -> customUrl.trim().takeIf { it.startsWith("http") }
    }

    /** Switches to [m] (and, for CUSTOM, [url]), fetching the pack; text badges until it has come. */
    suspend fun choose(context: Context, m: Mode, url: String = customUrl) {
        mode = m
        if (m == Mode.CUSTOM) customUrl = url
        prefs(context).edit().putString(KEY_MODE, m.name).putString(KEY_URL, customUrl).apply()
        val target = urlFor(m)
        if (target == null) { use(emptyList()); status = Status.IDLE; runCatching { cache(context).delete() }; return }
        status = Status.LOADING
        val text = withContext(Dispatchers.IO) { runCatching { fetch(target) }.getOrNull() }
        val list = text?.let { runCatching { parse(it) }.getOrNull() }.orEmpty()
        if (list.isEmpty()) { status = Status.FAILED; return }
        withContext(Dispatchers.IO) { runCatching { cache(context).writeText(text!!) } }
        use(list)
        status = Status.READY
    }

    /** The picture for one of the app's quality marks, or null to keep it as text. */
    fun imageFor(mark: String): String? {
        val list = filters
        if (list.isEmpty() || mark !in MARKS) return null
        return synchronized(picked) {
            picked.getOrPut(mark) {
                // The pattern that takes in most of the mark wins ("DTS-HD MA" is DTS-HD's, not DTS's).
                list.mapNotNull { f -> f.pattern.find(mark)?.let { m -> f to m.value.length } }
                    .maxByOrNull { it.second }?.first?.image
            }
        }
    }

    private fun use(list: List<Filter>) {
        synchronized(picked) { picked.clear() }
        filters = list
        if (list.isNotEmpty()) status = Status.READY
    }

    private fun parse(json: String): List<Filter> {
        val arr = JSONObject(json).optJSONArray("filters") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!o.optBoolean("isEnabled", true)) return@mapNotNull null
            val image = o.optString("imageURL").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val pattern = runCatching { Regex(o.optString("pattern")) }.getOrNull() ?: return@mapNotNull null
            Filter(o.optString("id"), o.optString("groupId"), o.optString("name"), pattern, image)
        }
    }

    private fun fetch(url: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = TIMEOUT_MS; c.readTimeout = TIMEOUT_MS
        try {
            check(c.responseCode == java.net.HttpURLConnection.HTTP_OK)
            return c.inputStream.bufferedReader().use { it.readText() }.also { check(it.length < MAX_BYTES) }
        } finally { c.disconnect() }
    }

    private fun cache(context: Context) = File(context.filesDir, CACHE_FILE)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The marks the app writes — only these become pictures (a genre tag never does). */
    private val MARKS = setOf(
        "8K", "4K", "1080p", "720p", "SD", "Dolby Vision", "DV", "HDR10+", "HDR10", "HDR", "REMUX", "IMAX",
        "BluRay", "WEB-DL", "WEBRip", "ATMOS", "Dolby Atmos", "7.1", "5.1", "7.1 Audio", "5.1 Audio",
        "DTS:X", "TrueHD", "DTS-HD MA", "DTS", "DD+",
    )
    private const val PREFS = "glcn_badges"
    private const val KEY_MODE = "mode"
    private const val KEY_URL = "url"
    private const val CACHE_FILE = "glcn_badge_pack.json"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 2_000_000
}

/**
 * glcn: one picture badge, [height] tall at its own width. Packs draw for dark backgrounds (white letters), so on a
 * light page — and always over a poster — it sits on a small dark plate.
 */
@androidx.compose.runtime.Composable
fun PackBadge(url: String, label: String, height: androidx.compose.ui.unit.Dp, onPoster: Boolean = false) {
    val light = tv.own.owntv.ui.theme.LocalStagePalette.current.isLight
    val plate = onPoster || light
    coil3.compose.AsyncImage(
        model = url,
        contentDescription = label,
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        // A big picture drawn small: smoothed properly, or its fine letters ("ULTRA HD") break up.
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        modifier = androidx.compose.ui.Modifier
            .then(
                if (plate) androidx.compose.ui.Modifier
                    .background(androidx.compose.ui.graphics.Color(PLATE), androidx.compose.foundation.shape.RoundedCornerShape(height / 4))
                    .padding(horizontal = height / 4, vertical = height / 8)
                else androidx.compose.ui.Modifier,
            )
            .height(height)
            .widthIn(max = height * BADGE_MAX_ASPECT),
    )
}

private const val PLATE = 0xD9101418
private const val BADGE_MAX_ASPECT = 6f
