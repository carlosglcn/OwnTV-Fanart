package tv.own.owntv.glcn

import android.content.Context
import android.net.ConnectivityManager
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.epg.displayLogoUrl

/**
 * glcn: every channel's logo fetched into the disk cache in the background, a while after start and again
 * after each sync, so the channel list and the guide show their picons from the first look instead of
 * filling in as they scroll. Same files and quality as on screen — only fetched earlier. Logos already in
 * the cache are skipped; a few at a time; never on a metered connection.
 */
object PiconPrefetch {
    private const val START_DELAY_MS = 20_000L
    private const val MAX_CHANNELS = 20_000
    private const val PARALLEL = 1
    /** A pause between logos, so the logo server never sees a burst (some answer one with errors). */
    private const val GAP_MS = 120L

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            delay(START_DELAY_MS)
            val koin = GlobalContext.get()
            val sources = koin.get<SourceDao>()
            val channels = koin.get<ChannelDao>()
            sources.observeAll()
                .map { list -> list.map { it.id to it.lastSyncAt } }
                .distinctUntilChanged()
                .collect { list ->
                    // glcn: not in light mode — it is for spare memory and network, which a light box has neither of.
                    if (list.isEmpty() || metered(app) || LowRam.on) return@collect
                    runCatching { fetch(app, channels, list.map { it.first }) }
                }
        }
    }

    /** Only on Wi-Fi or Ethernet: a phone on 5G can report itself unmetered, and that is still its data. */
    private fun metered(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        cm.isActiveNetworkMetered || !(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
    }.getOrDefault(true)

    private suspend fun fetch(app: Context, dao: ChannelDao, sourceIds: List<Long>) = coroutineScope {
        val loader = SingletonImageLoader.get(app)
        val disk = loader.diskCache ?: return@coroutineScope
        val urls = dao.allForSources(sourceIds, MAX_CHANNELS)
            .mapNotNull { it.displayLogoUrl?.takeIf { u -> u.startsWith("http") } }
            .distinct()
            .filter { url -> disk.openSnapshot(url)?.use { false } ?: true }
        // glcn: a few workers taking the next address in turn — one coroutine per logo (up to 20 000 waiting on a gate)
        // was a burst of memory after every sync.
        val queue = java.util.concurrent.ConcurrentLinkedQueue(urls)
        repeat(PARALLEL) {
            launch {
                while (true) {
                    val url = queue.poll() ?: break
                    val request = ImageRequest.Builder(app)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .size(64, 64)
                        .build()
                    runCatching { loader.execute(request) }
                    delay(GAP_MS)
                }
            }
        }
    }
}
