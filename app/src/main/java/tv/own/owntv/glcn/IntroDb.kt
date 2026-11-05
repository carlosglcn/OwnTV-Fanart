package tv.own.owntv.glcn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * glcn: TheIntroDB (theintrodb.org) — community timestamps for an episode's intro, recap and credits, looked up
 * by the show's TMDB id, season and episode (`GET https://api.theintrodb.org/v4/media`). Public, no key.
 * Answers are kept for the run, misses too, so an episode is asked for once.
 */
object IntroDb {
    /** One episode's segments, in ms; null where the database has none. */
    data class Segments(val introStart: Long?, val introEnd: Long?, val recapStart: Long?, val recapEnd: Long?, val creditsStart: Long?)

    private const val BASE = "https://api.theintrodb.org/v4/media"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Segments>()
    private val NONE = Segments(null, null, null, null, null)

    suspend fun segments(tmdbId: Int, season: Int, episode: Int): Segments? {
        val key = "$tmdbId/$season/$episode"
        cache[key]?.let { return it.takeIf { s -> s !== NONE } }
        val got = withContext(Dispatchers.IO) {
            runCatching {
                val c = java.net.URL("$BASE?tmdb_id=$tmdbId&season=$season&episode=$episode").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 6_000; c.readTimeout = 6_000
                c.setRequestProperty("Accept", "application/json")
                try {
                    if (c.responseCode != 200) null else parse(JSONObject(c.inputStream.bufferedReader().use { it.readText() }))
                } finally { c.disconnect() }
            }.getOrNull()
        }
        cache[key] = got ?: NONE
        return got
    }

    private fun parse(o: JSONObject): Segments {
        // A segment whose start is null begins at the very start; an end of null runs to the end.
        fun first(name: String): Pair<Long?, Long?>? {
            val a: JSONArray = o.optJSONArray(name) ?: return null
            val s = a.optJSONObject(0) ?: return null
            val start = if (s.isNull("start_ms")) 0L else s.optLong("start_ms")
            val end = if (s.isNull("end_ms")) null else s.optLong("end_ms")
            return start to end
        }
        val intro = first("intro")
        val recap = first("recap")
        val credits = first("credits")
        return Segments(intro?.first, intro?.second, recap?.first, recap?.second, credits?.first)
    }
}
