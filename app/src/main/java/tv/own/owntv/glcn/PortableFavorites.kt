package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.SyncActivityTracker
import tv.own.owntv.ui.theme.mpx

/**
 * glcn: favourite channels that outlive their playlist.
 *
 * A favourite is tied to a channel of one playlist; replace the playlist and the favourite goes with it. Here each
 * favourite channel's portable identity — its guide id (tvg-id) and its clean name — is kept in a small file beside
 * the database (nothing in the database changes). When a favourite's channel is gone and a channel with the same
 * identity exists in the playlists there are now, the app asks once whether to mark those as favourites again.
 *
 * Never by itself, and exact matches only: the same guide id (with the same name when several share it), or else
 * the same clean name. A favourite the viewer removed by hand (its channel is still there) is forgotten, not
 * offered back.
 */
object PortableFavorites {
    private const val FILE = "glcn_portable_favourites.json"
    private const val ENTRIES = "entries"
    private const val ASKED = "asked"
    private const val K_ID = "id"
    private const val K_GUIDE = "guide"
    private const val K_NAME = "name"
    private const val K_AT = "at"
    private const val IN_CHUNK = 500

    /** A remembered favourite: the channel it was on, and what identifies that channel anywhere. */
    private data class Entry(val channelId: Long, val guide: String, val name: String, val addedAt: Long) {
        val identity: String get() = guide + IDENTITY_GAP + name
    }

    /** What can be recovered now: [found] lost favourites (each with the channel that takes its place) out of [lost]. */
    class Offer internal constructor(val profileId: Long, internal val found: List<Pair<Long, Long>>, val lost: Int, internal val signature: String) {
        val count: Int get() = found.size
    }

    val offer = MutableStateFlow<Offer?>(null)
    private val lock = Mutex()

    private fun clean(name: String): String = ProviderTags.parse(name).name.trim().lowercase()
    private fun guideOf(id: String?): String = id.orEmpty().trim().lowercase()

    private fun file(context: Context) = java.io.File(context.filesDir, FILE)

    private fun read(context: Context): JSONObject =
        runCatching { file(context).takeIf { it.isFile }?.readText()?.let(::JSONObject) }.getOrNull() ?: JSONObject()

    private fun entriesOf(profile: JSONObject?): List<Entry> {
        val array = profile?.optJSONArray(ENTRIES) ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optLong(K_ID), o.optString(K_GUIDE), o.optString(K_NAME), o.optLong(K_AT))
        }
    }

    private fun write(context: Context, store: JSONObject, profileId: Long, entries: Collection<Entry>, asked: String?) {
        val array = JSONArray()
        entries.forEach { e -> array.put(JSONObject().put(K_ID, e.channelId).put(K_GUIDE, e.guide).put(K_NAME, e.name).put(K_AT, e.addedAt)) }
        val profile = JSONObject().put(ENTRIES, array)
        if (asked != null) profile.put(ASKED, asked)
        store.put(profileId.toString(), profile)
        runCatching { file(context).writeText(store.toString()) }
    }

    /**
     * Brings the file up to date with the profile's favourites and, if some lost ones can be found again and the
     * viewer has not been asked about exactly those, raises the [offer].
     */
    suspend fun refresh(context: Context, profileId: Long, favoriteDao: FavoriteDao, channelDao: ChannelDao) = lock.withLock {
        withContext(Dispatchers.IO) {
            val store = read(context)
            val profile = store.optJSONObject(profileId.toString())
            val asked = profile?.optString(ASKED).orEmpty()
            val known = entriesOf(profile).associateByTo(LinkedHashMap()) { it.channelId }
            val favourites = favoriteDao.getForProfileType(profileId, MediaType.LIVE)
            val favouriteIds = favourites.mapTo(HashSet()) { it.itemId }

            // Today's favourites, with the identity their channel has now (a re-sync may have renamed it).
            for (f in favourites) {
                val ch = channelDao.getById(f.itemId) ?: continue
                known[ch.id] = Entry(ch.id, guideOf(ch.epgChannelId), clean(ch.name), known[ch.id]?.addedAt ?: f.addedAt)
            }
            // No longer a favourite: removed by hand if its channel is still there (forgotten), lost if it is gone.
            val notFavourite = known.keys.filter { it !in favouriteIds }
            val stillThere = notFavourite.chunked(IN_CHUNK).flatMapTo(HashSet()) { channelDao.glcnExisting(it) }
            stillThere.forEach { known.remove(it) }
            // A lost one whose identity is a favourite again (recovered, or marked by hand) is settled.
            val current = known.values.filter { it.channelId in favouriteIds }.mapTo(HashSet()) { it.identity }
            known.values.removeAll { it.channelId !in favouriteIds && it.identity in current }
            val lost = known.values.filter { it.channelId !in favouriteIds }

            var raised: Offer? = null
            if (lost.isNotEmpty()) {
                val channels = channelDao.glcnIdentities().filter { it.id !in favouriteIds }
                val byGuide = channels.filter { !it.epgChannelId.isNullOrBlank() }.groupBy { guideOf(it.epgChannelId) }
                val byName by lazy { channels.groupBy { clean(it.name) } }
                val taken = HashSet<Long>()
                val found = lost.mapNotNull { e ->
                    val sameGuide = if (e.guide.isNotEmpty()) byGuide[e.guide].orEmpty() else emptyList()
                    val pick = sameGuide.firstOrNull { clean(it.name) == e.name && it.id !in taken }
                        ?: sameGuide.singleOrNull()?.takeIf { it.id !in taken }
                        ?: byName[e.name].orEmpty().filter { it.id !in taken }.minWithOrNull(compareBy({ it.sourceId }, { it.number ?: Int.MAX_VALUE }))
                    pick?.let { taken += it.id; e.channelId to it.id }
                }
                val signature = found.map { (old, _) -> known[old]?.identity.orEmpty() }.sorted().joinToString(SIGNATURE_GAP.toString()).hashCode().toString() + SIGNATURE_GAP + found.size
                if (found.isNotEmpty() && signature != asked) raised = Offer(profileId, found, lost.size, signature)
            }
            write(context, store, profileId, known.values, asked.takeIf { it.isNotEmpty() })
            offer.value = raised
        }
    }

    /** The viewer's answer: [recover] marks the found channels as favourites; either way those are not asked about again. */
    suspend fun answer(context: Context, offered: Offer, recover: Boolean, favoriteDao: FavoriteDao, channelDao: ChannelDao) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val store = read(context)
                val known = entriesOf(store.optJSONObject(offered.profileId.toString())).associateByTo(LinkedHashMap()) { it.channelId }
                if (recover) {
                    for ((old, new) in offered.found) {
                        val entry = known.remove(old)
                        runCatching {
                            favoriteDao.add(FavoriteEntity(profileId = offered.profileId, mediaType = MediaType.LIVE, itemId = new, addedAt = entry?.addedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()))
                        }
                        if (entry != null) known[new] = entry.copy(channelId = new)
                    }
                }
                write(context, store, offered.profileId, known.values, offered.signature)
                offer.value = null
            }
        }
        if (recover) refresh(context, offered.profileId, favoriteDao, channelDao)
    }

    private const val IDENTITY_GAP = '\u0001'
    private const val SIGNATURE_GAP = '|'

    /** How long favourites and playlists must have been quiet before looking: a sync writes for a while. */
    private const val QUIET_MS = 6_000L

    /** Watches the profile's favourites and the playlists, and shows the question when there is one. In the shell. */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Composable
    fun Host() {
        val context = LocalContext.current.applicationContext
        val favoriteDao = koinInject<FavoriteDao>()
        val channelDao = koinInject<ChannelDao>()
        val profileDao = koinInject<ProfileDao>()
        val sourceDao = koinInject<SourceDao>()
        val settings = koinInject<SettingsRepository>()
        val syncs = koinInject<SyncActivityTracker>()
        LaunchedEffect(Unit) {
            settings.activeProfileId
                .map { preferred -> if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null }
                .distinctUntilChanged()
                .flatMapLatest { pid ->
                    if (pid == null) flowOf<Long?>(null) else combine(
                        favoriteDao.observeFavoriteIds(pid, MediaType.LIVE),
                        sourceDao.observeAll().map { list -> list.map { it.id to it.lastSyncAt } },
                        syncs.active.map { it.isEmpty() },
                    ) { _, _, idle -> pid.takeIf { idle } }
                }
                .debounce(QUIET_MS)
                .collectLatest { pid -> if (pid != null) runCatching { refresh(context, pid, favoriteDao, channelDao) } }
        }
        val offered by offer.collectAsState()
        val o = offered ?: return
        val scope = rememberCoroutineScope()
        val yesFocus = remember(o) { FocusRequester() }
        LaunchedEffect(o) { delay(80); runCatching { yesFocus.requestFocus() } }
        val decline = { scope.launch { answer(context, o, recover = false, favoriteDao, channelDao) }; Unit }
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = decline,
            title = stringResource(R.string.glcn_favs_recover_title),
            body = stringResource(R.string.glcn_favs_recover_body, o.count, o.lost),
            buttons = {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.glcn_favs_recover_no), onClick = decline, height = 56.mpx, textSize = 19)
                tv.own.owntv.ui.stage.StageButton(
                    stringResource(R.string.glcn_favs_recover_yes),
                    onClick = { scope.launch { answer(context, o, recover = true, favoriteDao, channelDao) } },
                    height = 56.mpx, textSize = 19, tinted = true,
                    modifier = Modifier.focusRequester(yesFocus),
                )
            },
        )
    }
}
