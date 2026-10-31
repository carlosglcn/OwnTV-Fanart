package tv.own.owntv.glcn

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.live.NowArt
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/**
 * glcn: the SHIELD's screensaver. While the TV sits idle it shows, one after another, the pictures of
 * what is on now on the favourite channels and of the films and series most recently added to the
 * library (Emby), each with its title — the house's own gallery instead of stock photos.
 *
 * Plain Views (a dream is not a Compose host), two image layers crossfading every [SLIDE_MS].
 */
class FanartDream : DreamService() {

    /** glcn (cartelera): a film or series also carries its poster, shown beside the picture, and its TMDB title logo. */
    private data class Slide(
        val url: String,
        val title: String,
        val subtitle: String,
        val poster: String? = null,
        val movie: tv.own.owntv.core.database.entity.MovieEntity? = null,
        val series: tv.own.owntv.core.database.entity.SeriesEntity? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var front: ImageView
    private lateinit var back: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var poster: ImageView
    private lateinit var logo: ImageView
    private var drift = 1f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        fun image() = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            root.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        back = image()
        front = image()
        root.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf(Color.argb(230, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.TRANSPARENT),
                )
            },
            FrameLayout.LayoutParams(-1, -1),
        )
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 48).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val dp = resources.displayMetrics.density
        // glcn (cartelera): the title's own lettering, when TMDB has it, stands in for the words.
        logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
            maxHeight = (dp * 110).toInt()
            maxWidth = (resources.displayMetrics.widthPixels * 0.42f).toInt()
            visibility = View.GONE
        }
        text.addView(logo, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = (dp * 10).toInt() })
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(12f, 0f, 2f, Color.argb(160, 0, 0, 0))
            maxLines = 2
        }
        subtitle = TextView(this).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 18f
            maxLines = 1
        }
        text.addView(title)
        text.addView(subtitle)
        root.addView(text, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START))
        // glcn (cartelera): the film's poster, standing on the right like a cinema's billboard.
        val posterW = (resources.displayMetrics.widthPixels * POSTER_WIDTH).toInt()
        poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            background = GradientDrawable().apply { cornerRadius = dp * 14; setColor(Color.argb(60, 255, 255, 255)) }
            clipToOutline = true
            elevation = dp * 18
        }
        root.addView(
            poster,
            FrameLayout.LayoutParams(posterW, (posterW * 1.5f).toInt(), Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = (dp * 72).toInt() },
        )
        // The time in the corner, as on an Apple TV's aerial screensaver.
        val pad = (resources.displayMetrics.density * 40).toInt()
        root.addView(
            android.widget.TextClock(this).apply {
                setTextColor(Color.argb(220, 255, 255, 255))
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(12f, 0f, 2f, Color.argb(160, 0, 0, 0))
                setPadding(pad, pad, pad, pad)
            },
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END),
        )
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scope.launch {
            val slides = runCatching { withContext(Dispatchers.IO) { loadSlides() } }.getOrDefault(emptyList())
            if (slides.isEmpty()) return@launch
            var i = 0
            while (true) {
                val s = slides[i % slides.size]
                val bmp = withContext(Dispatchers.IO) { load(s.url) }
                if (bmp != null) {
                    val extra = withContext(Dispatchers.IO) { extras(s) }
                    show(bmp, s, extra.first, extra.second)
                }
                i++
                delay(SLIDE_MS)
            }
        }
    }

    override fun onDreamingStopped() {
        scope.cancel()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun show(bmp: Bitmap, s: Slide, posterBmp: Bitmap?, logoBmp: Bitmap?) {
        back.setImageBitmap(bmp)
        back.animate().alpha(1f).setDuration(FADE_MS).start()
        front.animate().alpha(0f).setDuration(FADE_MS).start()
        // glcn: Ken Burns — a slow, deeper push-in that drifts to one side, the side changing every slide.
        drift = -drift
        back.scaleX = 1.0f; back.scaleY = 1.0f; back.translationX = 0f
        back.animate().scaleX(1f + ZOOM).scaleY(1f + ZOOM)
            .translationX(drift * resources.displayMetrics.widthPixels * ZOOM * 0.4f)
            .setDuration(SLIDE_MS + FADE_MS).setInterpolator(android.view.animation.LinearInterpolator()).start()
        // The words leave and come back with the picture.
        val words = listOf(logo, title, subtitle)
        words.forEach { it.animate().alpha(0f).setDuration(FADE_MS / 3).start() }
        title.postDelayed({
            if (logoBmp != null) { logo.setImageBitmap(logoBmp); logo.visibility = View.VISIBLE; title.visibility = View.GONE }
            else { logo.visibility = View.GONE; title.visibility = View.VISIBLE; title.text = s.title }
            subtitle.text = s.subtitle
            words.forEachIndexed { k, v ->
                v.translationY = 18f * resources.displayMetrics.density
                v.animate().alpha(1f).translationY(0f).setStartDelay(k * 140L).setDuration(FADE_MS / 2).start()
            }
        }, FADE_MS / 3)
        // The billboard poster: slides in with its film, away for a channel's programme.
        if (posterBmp != null) {
            poster.animate().alpha(0f).setDuration(FADE_MS / 3).withEndAction {
                poster.setImageBitmap(posterBmp)
                poster.translationX = 40f * resources.displayMetrics.density
                poster.animate().alpha(1f).translationX(0f).setDuration(FADE_MS).start()
            }.start()
        } else {
            poster.animate().alpha(0f).setDuration(FADE_MS / 2).start()
        }
        val t = front; front = back; back = t
    }

    /** glcn (cartelera): a film's or series' poster and TMDB title logo, read when its slide comes up. */
    private suspend fun extras(s: Slide): Pair<Bitmap?, Bitmap?> {
        if (s.movie == null && s.series == null) return null to null
        val meta = kotlinx.coroutines.withTimeoutOrNull(META_MS) {
            runCatching {
                val repo = GlobalContext.get().get<tv.own.owntv.core.metadata.MetadataRepository>()
                s.movie?.let { repo.resolveMovie(it) } ?: s.series?.let { repo.resolveSeries(it) }
            }.getOrNull()
        }
        val posterUrl = s.poster ?: tv.own.owntv.core.metadata.MetadataImages.poster(meta?.posterPath)
        val logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath)
        return posterUrl?.let { load(it) } to logoUrl?.let { load(it) }
    }

    private suspend fun load(url: String): Bitmap? = runCatching {
        val request = coil3.request.ImageRequest.Builder(this).data(url).build()
        val result = coil3.SingletonImageLoader.get(this).execute(request)
        ((result as? coil3.request.SuccessResult)?.image as? coil3.BitmapImage)?.bitmap
    }.getOrNull()

    private suspend fun loadSlides(): List<Slide> {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val sourceDao = koin.get<SourceDao>()
        val channelDao = koin.get<ChannelDao>()
        val movieDao = koin.get<MovieDao>()
        val seriesDao = koin.get<SeriesDao>()
        val nowArt = koin.get<NowArt>()
        val pid = settings.activeProfileId.first()
        if (pid < 0) return emptyList()
        val out = ArrayList<Slide>()
        val fmt = android.text.format.DateFormat.getTimeFormat(this)
        // What is on now on the favourite channels.
        val liveIds = activeSourceIds(settings, sourceDao, pid, MediaType.LIVE).toSet()
        val favourites = channelDao.favoritesListAlpha(pid).first().filter { it.sourceId in liveIds }.take(60)
        val now = nowArt.many(favourites)
        favourites.forEach { ch ->
            val p = now[ch.id] ?: return@forEach
            val url = p.imageUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, p.title, ch.name + " · " + fmt.format(java.util.Date(p.startMs)) + " – " + fmt.format(java.util.Date(p.stopMs)))
        }
        // The newest films and series in the library.
        val movieIds = activeSourceIds(settings, sourceDao, pid, MediaType.MOVIE)
        if (movieIds.isNotEmpty()) movieDao.recentlyAdded(movieIds, 25).forEach { m ->
            val url = m.backdropUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, m.name, listOfNotNull(m.year?.toString(), m.rating?.let { "★ %.1f".format(it) }).joinToString(" · "), m.posterUrl?.takeIf { it.isNotBlank() }, movie = m)
        }
        val seriesIds = activeSourceIds(settings, sourceDao, pid, MediaType.SERIES)
        if (seriesIds.isNotEmpty()) seriesDao.recentlyAdded(seriesIds, 15).forEach { s ->
            val url = s.backdropUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, s.name, listOfNotNull(s.year?.toString(), s.rating?.let { "★ %.1f".format(it) }).joinToString(" · "), s.posterUrl?.takeIf { it.isNotBlank() }, series = s)
        }
        return out.shuffled()
    }

    private companion object {
        const val SLIDE_MS = 12_000L
        const val FADE_MS = 1_200L
        const val ZOOM = 0.12f
        const val META_MS = 2_500L
        const val POSTER_WIDTH = 0.17f
    }
}
