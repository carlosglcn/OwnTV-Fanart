package tv.own.owntv.glcn

import android.app.ActivityManager
import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * glcn: pictures fetched a step ahead. When focus settles on a title, the backdrops of the titles around it are
 * downloaded and decoded into the memory cache, so moving on shows the next picture at once instead of
 * waiting for the network and the decoder. Only a few, only once each (a short memory of what was asked), and
 * fewer still in light mode.
 */
object ArtPrefetch {
    private val asked = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 300
    }

    /** How many titles on each side get their backdrop ahead of time. */
    val reach: Int get() = if (LowRam.on) 1 else 3

    /**
     * Wide pictures ([urls]) at their own size, at most 1280 wide — what the backdrops ask for, so the screen's
     * own request finds them in the memory cache.
     */
    fun backdrops(context: Context, urls: List<String?>) = enqueue(context, urls, Size(1280, 720))

    private fun enqueue(context: Context, urls: List<String?>, size: Size) {
        val loader = SingletonImageLoader.get(context)
        val fresh = synchronized(asked) {
            urls.filterNotNull().filter { it.startsWith("http") && asked.put(it, Unit) == null }
        }
        fresh.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(size)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
    }

    /** The share of memory the picture cache may use: more on a box with room, less in light mode. */
    fun memoryShare(context: Context): Double {
        if (LowRam.on) return 0.06
        val total = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        return when {
            total >= 2_800_000_000L -> 0.20
            total >= 1_800_000_000L -> 0.13
            else -> 0.10
        }
    }

    /**
     * glcn: the most the picture cache may hold, whatever its share works out to: pictures are drawn by the GPU, so
     * a large cache is graphics memory the system counts against us (the SHIELD reached ~570 MB and a critical
     * memory warning). Enough for a few screens of fanart and backdrops; the disk cache makes the rest quick.
     */
    fun memoryCapBytes(context: Context): Long {
        if (LowRam.on) return 24L * MB
        val total = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        return when {
            total >= 5_500_000_000L -> 128L * MB
            total >= 2_800_000_000L -> 72L * MB
            else -> 40L * MB
        }
    }
}

private const val MB = 1024L * 1024L
