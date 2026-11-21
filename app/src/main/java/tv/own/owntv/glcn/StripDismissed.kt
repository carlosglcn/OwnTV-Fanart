package tv.own.owntv.glcn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * glcn: what was taken out of "Keep watching" for good. A title is remembered by what does not change when a list or
 * an Emby library is loaded again (its list and its name — the ids do change) together with where it stood: the
 * episode and the minute. Loading the library again brings the same point back, and the card stays away; watching
 * on moves the point, and the title is under way again, so it returns.
 */
object StripDismissed {
    private val state = MutableStateFlow<Set<String>>(emptySet())
    val marks: StateFlow<Set<String>> get() = state
    @Volatile private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY, null).orEmpty()
        state.value = saved.split(LINE).filter { it.isNotBlank() }.toCollection(LinkedHashSet())
    }

    fun add(context: Context, mark: String) {
        ensure(context)
        val next = LinkedHashSet(state.value).apply { remove(mark); add(mark) }
        // The newest are kept: an old mark only matters while its title still stands where it did.
        val kept = next.toList().takeLast(MAX).toCollection(LinkedHashSet())
        state.value = kept
        prefs(context).edit().putString(KEY, kept.joinToString(LINE.toString())).apply()
    }

    /** A show's mark: where its card stands (the episode shown and its minute). */
    fun show(profileId: Long, sourceId: Long, name: String, season: Int, episode: Int, positionMs: Long): String =
        listOf(SHOW, profileId, sourceId, name.trim().lowercase(), season, episode, positionMs / MINUTE_MS).joinToString(SEP)

    /** A film's mark: the minute it was left at. */
    fun film(profileId: Long, sourceId: Long, name: String, positionMs: Long): String =
        listOf(FILM, profileId, sourceId, name.trim().lowercase(), positionMs / MINUTE_MS).joinToString(SEP)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "glcn_strip_dismissed"
    private const val KEY = "marks"
    private const val LINE = '\n'
    private const val SEP = "|"
    private const val SHOW = "s"
    private const val FILM = "m"
    private const val MAX = 400
    private const val MINUTE_MS = 60_000L
}
