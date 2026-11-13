package tv.own.owntv.glcn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * glcn: a person's Wikipedia summary — the first paragraph, the one-line description and the article's address — in
 * the app's language, else in English. The name is tried as a title first; when that is not a person's article (a
 * disambiguation page, nothing), the search is asked for "name + actor" and its first hit naming the person is used.
 */
object Wiki {

    class Summary(val title: String, val description: String?, val extract: String, val url: String, val photo: String?, val qid: String?)

    /** From Wikidata: born and died (dates), where born (in the app's language), and the films the person is in. */
    class Facts(val born: java.time.LocalDate?, val died: java.time.LocalDate?, val place: String?, val films: List<Film>)

    class Film(val tmdb: Int?, val titles: List<String>, val year: Int?)

    private val facts = ConcurrentHashMap<String, Facts>()

    suspend fun facts(qid: String): Facts? {
        facts[qid]?.let { return it }
        val lang = Locale.getDefault().language.ifBlank { EN }
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val who = sparql(
                    "SELECT ?b ?d ?pLabel WHERE { OPTIONAL { wd:$qid wdt:P569 ?b } OPTIONAL { wd:$qid wdt:P570 ?d } " +
                        "OPTIONAL { wd:$qid wdt:P19 ?p } SERVICE wikibase:label { bd:serviceParam wikibase:language \"$lang,$EN\". } } LIMIT 1",
                ).firstOrNull()
                val films = runCatching {
                    sparql(
                        "SELECT ?f ?fLabel ?en ?t (MIN(?date) AS ?d) WHERE { ?f wdt:P161 wd:$qid; wdt:P31 wd:Q11424. " +
                            "OPTIONAL { ?f wdt:P4947 ?t } OPTIONAL { ?f wdt:P577 ?date } OPTIONAL { ?f rdfs:label ?en FILTER(LANG(?en) = \"$EN\") } " +
                            "SERVICE wikibase:label { bd:serviceParam wikibase:language \"$lang,$EN\". } } GROUP BY ?f ?fLabel ?en ?t LIMIT $MAX_FILMS",
                    ).mapNotNull { r ->
                        val titles = listOfNotNull(r["fLabel"], r["en"]).filter { it.isNotBlank() && !QID.matches(it) }.distinct()
                        if (titles.isEmpty()) null else Film(r["t"]?.toIntOrNull(), titles, r["d"]?.take(4)?.toIntOrNull())
                    }.distinctBy { it.titles.first() to it.year }
                }.getOrDefault(emptyList())
                Facts(day(who?.get("b")), day(who?.get("d")), who?.get("pLabel")?.takeUnless { QID.matches(it) }, films)
            }.getOrNull()
        }
        if (f != null) facts[qid] = f
        return f
    }

    private fun day(iso: String?): java.time.LocalDate? =
        iso?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }

    /** A SPARQL query's rows, each as variable → value. */
    private fun sparql(q: String): List<Map<String, String>> {
        val j = JSONObject(get("https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(q, UTF8)))
        val rows = j.optJSONObject("results")?.optJSONArray("bindings") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            o.keys().asSequence().associateWith { k -> o.optJSONObject(k)?.optString("value").orEmpty() }
        }
    }

    private val cache = ConcurrentHashMap<String, Summary>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun person(name: String): Summary? {
        val key = name.trim().lowercase(Locale.ROOT)
        cache[key]?.let { return it }
        if (key in missing) return null
        val found = withContext(Dispatchers.IO) {
            val langs = listOf(Locale.getDefault().language.ifBlank { EN }, EN).distinct()
            langs.firstNotNullOfOrNull { lang -> runCatching { find(lang, name.trim()) }.getOrNull() }
        }
        if (found != null) cache[key] = found else missing += key
        return found
    }

    private fun find(lang: String, name: String): Summary? {
        summary(lang, name)?.let { return it }
        val q = URLEncoder.encode("$name $ACTOR_HINT", UTF8)
        val search = JSONObject(get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srlimit=5&format=json&srsearch=$q"))
        val hits = search.optJSONObject("query")?.optJSONArray("search") ?: return null
        val last = name.substringAfterLast(' ').lowercase(Locale.ROOT)
        for (i in 0 until hits.length()) {
            val title = hits.optJSONObject(i)?.optString("title").orEmpty()
            if (title.lowercase(Locale.ROOT).contains(last)) summary(lang, title)?.let { return it }
        }
        return null
    }

    private fun summary(lang: String, title: String): Summary? {
        val path = URLEncoder.encode(title.replace(' ', '_'), UTF8).replace("+", "%20")
        val j = runCatching { JSONObject(get("https://$lang.wikipedia.org/api/rest_v1/page/summary/$path")) }.getOrNull() ?: return null
        if (j.optString("type") != STANDARD) return null
        val extract = j.optString("extract").trim()
        if (extract.length < MIN_EXTRACT) return null
        val urls = j.optJSONObject("content_urls")
        val url = urls?.optJSONObject("mobile")?.optString("page")?.takeIf { it.isNotBlank() }
            ?: urls?.optJSONObject("desktop")?.optString("page")?.takeIf { it.isNotBlank() } ?: return null
        return Summary(
            j.optString("title"), j.optString("description").takeIf { it.isNotBlank() }, extract, url,
            j.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() },
            j.optString("wikibase_item").takeIf { QID.matches(it) },
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS; c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            check(c.responseCode == HttpURLConnection.HTTP_OK)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private const val EN = "en"
    private const val MAX_FILMS = 300
    private val QID = Regex("Q\\d+")
    private const val UTF8 = "UTF-8"
    private const val STANDARD = "standard"
    private const val ACTOR_HINT = "actor"
    private const val USER_AGENT = "OwnTV-Fanart (https://github.com/carlosglcn/OwnTV-Fanart)"
    private const val TIMEOUT_MS = 8_000
    private const val MIN_EXTRACT = 40
}
