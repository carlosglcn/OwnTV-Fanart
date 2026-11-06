package tv.own.owntv.glcn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the app's own wallpapers. Photos (1080p — the interface is drawn at 1080p, so 4K would only weigh more) live
 * on the repository's `wallpapers` branch and are downloaded once, when picked; the app carries only small previews
 * of them. Animated ones are drawn by the app itself (no video: they cost almost nothing and stand still in the light
 * mode); they are stored as the background "picture" `anim:<kind>`, which the shell draws instead of loading a file.
 */
object Wallpapers {
    const val ANIM_PREFIX = "anim:"
    private const val BASE_URL = "https://raw.githubusercontent.com/carlosglcn/OwnTV-Fanart/wallpapers/"
    val animated = listOf("aurora", "ondas", "brillos")
    val dark = listOf("via_lactea", "aurora_nieve", "aurora_pinos", "puerto", "bosque", "nieve_violeta", "siluetas", "pico_lila")
    val light = listOf("nubes_rosa", "duna", "mar_beige", "muelle", "valle")
    /** Offered for the profile screen first: a picture with more presence, nothing laid over it but names. */
    val gateFirst = listOf("nubes_rosa", "cordillera")

    fun animKind(path: String?): String? = path?.takeIf { it.startsWith(ANIM_PREFIX) }?.removePrefix(ANIM_PREFIX)

    fun thumbUri(name: String) = "file:///android_asset/wallpapers/thumb/$name.jpg"

    fun file(context: android.content.Context, name: String) = java.io.File(java.io.File(context.filesDir, "backgrounds"), "wall_$name.jpg")

    /** The photo a background path is, if it is one of ours. */
    fun nameOf(path: String?): String? = path?.let { java.io.File(it).name }
        ?.takeIf { it.startsWith("wall_") && it.endsWith(".jpg") }?.removePrefix("wall_")?.removeSuffix(".jpg")

    /** What to show of a photo now: the downloaded picture when it is here, else its preview. */
    fun model(context: android.content.Context, name: String): Any = file(context, name).takeIf { it.length() > 0 } ?: thumbUri(name)

    /** Downloads the photo once (to a part file, then renamed) and returns its path; null when it could not. */
    suspend fun download(context: android.content.Context, name: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = file(context, name)
            if (out.length() > 0) return@runCatching out.absolutePath
            out.parentFile?.mkdirs()
            val part = java.io.File(out.parentFile, out.name + ".part")
            val conn = java.net.URL("$BASE_URL$name.jpg").openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = NET_TIMEOUT_MS
            conn.readTimeout = NET_TIMEOUT_MS
            try {
                check(conn.responseCode == java.net.HttpURLConnection.HTTP_OK)
                val total = conn.contentLengthLong
                conn.inputStream.use { inp ->
                    part.outputStream().use { o ->
                        val buf = ByteArray(BUF_BYTES)
                        var done = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            check(part.length() > 0 && part.renameTo(out))
            out.absolutePath
        }.getOrNull()
    }

    private const val NET_TIMEOUT_MS = 15_000
    private const val BUF_BYTES = 16 * 1024
}

/**
 * glcn: Settings › Glass & background › Profile screen background — none (the screen's own aurora and the fanart of
 * what each profile watched), the app's own background, or one of the photos.
 */
object GateWall {
    const val NONE = "none"
    const val APP = "app"

    var choice by mutableStateOf(NONE)
        private set
    private var loaded = false

    fun ensure(context: android.content.Context) {
        if (loaded) return
        loaded = true
        choice = prefs(context).getString(KEY, NONE) ?: NONE
    }

    fun set(context: android.content.Context, value: String) {
        choice = value
        prefs(context).edit().putString(KEY, value).apply()
    }

    /** What the profile screen draws behind: a picture, `anim:<kind>`, or null for its own look. */
    fun model(context: android.content.Context, background: tv.own.owntv.core.theme.BackgroundConfig): Any? = when (choice) {
        NONE -> null
        APP -> background.takeIf { it.showsPicture && it.imagePath.isNotBlank() }?.imagePath
        else -> Wallpapers.model(context, choice)
    }

    private fun prefs(context: android.content.Context) = context.applicationContext.getSharedPreferences("glcn_gate_wall", android.content.Context.MODE_PRIVATE)
    private const val KEY = "choice"
}

/** glcn: a wallpaper full screen — a picture (path, uri or file) or an animated one (`anim:<kind>`). */
@Composable
fun WallLayer(model: Any, modifier: Modifier = Modifier) {
    val anim = (model as? String)?.let { Wallpapers.animKind(it) }
    if (anim != null) AnimatedWallpaper(anim, modifier)
    else coil3.compose.AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** glcn: an animated wallpaper, full screen behind the app. */
@Composable
fun AnimatedWallpaper(kind: String, modifier: Modifier = Modifier) {
    val moving = !LowRam.on && tv.own.owntv.ui.theme.animationsOn
    val t = if (moving) rememberInfiniteTransition(label = "wall").animateFloat(
        0f, 1f, infiniteRepeatable(tween(WALL_ROUND_MS, easing = LinearEasing), RepeatMode.Restart), label = "wallT",
    ).value else 0.2f
    Canvas(modifier) { drawWallpaper(kind, t) }
}

fun DrawScope.drawWallpaper(kind: String, t: Float) {
    when (kind) {
        "ondas" -> drawWaves(t)
        "brillos" -> drawSparkles(t)
        else -> drawLights(listOf(Color(0xFF0F766E), Color(0xFF6D28D9), Color(0xFF1D4ED8)), Color(0xFF070B14), t)
    }
}

/** Slow layered waves in deep blue to violet, each layer on its own phase. */
private fun DrawScope.drawWaves(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF060A18), Color(0xFF0B1230))))
    val cols = listOf(Color(0xFF1B2A6B), Color(0xFF3A2A85), Color(0xFF5B2A86), Color(0xFF7A2E6E))
    val two = (2 * Math.PI).toFloat()
    cols.forEachIndexed { k, c ->
        val path = androidx.compose.ui.graphics.Path()
        val base = h * (0.45f + 0.13f * k)
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            val y = base + h * 0.05f * kotlin.math.sin(x / w * two * (1.1f + 0.25f * k) + (t + k * 0.21f) * two)
            path.lineTo(x, y)
            x += w / 48f
        }
        path.lineTo(w, h); path.close()
        drawPath(path, c.copy(alpha = 0.55f))
    }
    drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)))
}

/** Soft points of light rising very slowly over a dark plum page. */
private fun DrawScope.drawSparkles(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(listOf(Color(0xFF120A1E), Color(0xFF0A1022)), Offset.Zero, Offset(w, h)))
    val rnd = java.util.Random(11)
    repeat(46) {
        val bx = rnd.nextFloat()
        val by = rnd.nextFloat()
        val r = (6f + rnd.nextFloat() * 22f) * (w / 1920f)
        val sp = 0.4f + rnd.nextFloat() * 0.8f
        val y = ((by - t * sp) % 1f + 1f) % 1f
        val warm = rnd.nextBoolean()
        val c = if (warm) Color(0xFFFFB37A) else Color(0xFF8FB8FF)
        val a = 0.10f + rnd.nextFloat() * 0.22f
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(bx * w, y * h), r * 3), r * 3, Offset(bx * w, y * h))
    }
}

/** One choice in the gallery. */
private sealed interface WallItem {
    data class Anim(val kind: String) : WallItem
    data class Photo(val name: String) : WallItem
    data object None : WallItem
    data object AppBackground : WallItem
}

/**
 * glcn: the gallery for the app's background. The panel sits at the foot and the screen behind it shows the
 * wallpaper in focus, live (its preview while browsing, the full picture once it is here); OK sets it — a photo is
 * downloaded once — and Back leaves the one you had.
 */
@Composable
fun WallpaperGalleryPopup(current: String?, onPicked: (path: String, picture: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sections = listOf(
        stringResource(R.string.glcn_wall_animated) to Wallpapers.animated.map { WallItem.Anim(it) },
        stringResource(R.string.glcn_wall_dark) to Wallpapers.dark.map { WallItem.Photo(it) },
        stringResource(R.string.glcn_wall_light) to Wallpapers.light.map { WallItem.Photo(it) },
    )
    val currentItem: WallItem? = Wallpapers.animKind(current)?.let { WallItem.Anim(it) } ?: Wallpapers.nameOf(current)?.let { WallItem.Photo(it) }
    WallGallery(
        sections = sections,
        picked = currentItem,
        gate = false,
        onChoose = { item, path ->
            when (item) {
                is WallItem.Anim -> onPicked(Wallpapers.ANIM_PREFIX + item.kind, false)
                is WallItem.Photo -> if (path != null) onPicked(path, true)
                else -> Unit
            }
        },
        onDismiss = onDismiss,
        modelOf = { item -> itemModel(context, item, null) },
    )
}

/** glcn: the same gallery for the profile screen: none, the app's background, or a photo. */
@Composable
fun GateWallGalleryPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    GateWall.ensure(context)
    val background = tv.own.owntv.ui.theme.LocalBackground.current
    val photos = (Wallpapers.gateFirst + Wallpapers.dark + Wallpapers.light).distinct().map { WallItem.Photo(it) }
    val sections = listOf(
        stringResource(R.string.glcn_gate_wall_basic) to listOf(WallItem.None, WallItem.AppBackground),
        stringResource(R.string.glcn_wall_photos) to photos,
    )
    val picked = when (val c = GateWall.choice) {
        GateWall.NONE -> WallItem.None
        GateWall.APP -> WallItem.AppBackground
        else -> WallItem.Photo(c)
    }
    WallGallery(
        sections = sections,
        picked = picked,
        gate = true,
        onChoose = { item, path ->
            when (item) {
                WallItem.None -> GateWall.set(context, GateWall.NONE)
                WallItem.AppBackground -> GateWall.set(context, GateWall.APP)
                is WallItem.Photo -> if (path != null) GateWall.set(context, item.name)
                is WallItem.Anim -> Unit
            }
        },
        onDismiss = onDismiss,
        modelOf = { item -> itemModel(context, item, background) },
    )
}

private fun itemModel(context: android.content.Context, item: WallItem, background: tv.own.owntv.core.theme.BackgroundConfig?): Any? = when (item) {
    is WallItem.Anim -> Wallpapers.ANIM_PREFIX + item.kind
    is WallItem.Photo -> Wallpapers.model(context, item.name)
    WallItem.None -> null
    WallItem.AppBackground -> background?.takeIf { it.showsPicture && it.imagePath.isNotBlank() }?.imagePath
}

@Composable
private fun WallGallery(
    sections: List<Pair<String, List<WallItem>>>,
    picked: WallItem?,
    gate: Boolean,
    onChoose: (WallItem, String?) -> Unit,
    onDismiss: () -> Unit,
    modelOf: (WallItem) -> Any?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var focusedItem by remember { mutableStateOf(picked ?: sections.first().second.first()) }
    var loading by remember { mutableStateOf<WallItem?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }

    fun choose(item: WallItem) {
        if (loading != null) return
        if (item !is WallItem.Photo) { onChoose(item, null); onDismiss(); return }
        failed = false
        loading = item
        progress = 0f
        scope.launch {
            val path = Wallpapers.download(context, item.name) { progress = it }
            loading = null
            if (path == null) { failed = true; return@launch }
            onChoose(item, path)
            onDismiss()
        }
    }

    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // The wallpaper in focus, full screen: how it will really look.
            Crossfade(targetState = focusedItem, animationSpec = tween(PREVIEW_FADE_MS), label = "wallPreview", modifier = Modifier.fillMaxSize()) { item ->
                val model = modelOf(item)
                if (model != null) WallLayer(model, Modifier.fillMaxSize())
                else if (gate) GatePlainPreview(Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page))
            }
            if (gate) GateMock(Modifier.align(Alignment.TopCenter).padding(top = 90.mpx))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.mpx, vertical = 30.mpx)
                    .stageGlass(30.mpx, overContent = true).trapAllFocusExit().focusGroup().padding(vertical = 22.mpx),
                verticalArrangement = Arrangement.spacedBy(10.mpx),
            ) {
                Row(Modifier.padding(horizontal = 28.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    Text(stringResource(if (gate) R.string.glcn_gate_wall_title else R.string.glcn_wall_title), style = stageText(28, 800), color = StageColors.Text)
                    val note = when {
                        loading != null -> stringResource(R.string.glcn_wall_downloading, (progress * 100).toInt())
                        failed -> stringResource(R.string.glcn_wall_failed)
                        else -> stringResource(R.string.glcn_wall_desc)
                    }
                    Text(note, style = stageText(17, 500), color = if (failed) StageColors.Danger else StageColors.Muted, maxLines = 1)
                }
                val firstItem = picked?.takeIf { p -> sections.any { it.second.contains(p) } } ?: sections.first().second.first()
                sections.forEach { (label, items) ->
                    Text(label, style = stageText(16, 800), color = StageColors.Dim, modifier = Modifier.padding(start = 28.mpx, top = 4.mpx))
                    // The row holding the current one starts on it, so focus can land there.
                    val rowState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(firstItem).coerceAtLeast(0))
                    LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 28.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                        itemsIndexed(items) { _, item ->
                            val isFirst = item == firstItem
                            WallTile(
                                item = item,
                                label = wallLabel(item),
                                picked = item == picked,
                                loading = loading == item,
                                progress = progress,
                                model = when (item) {
                                    is WallItem.Photo -> Wallpapers.thumbUri(item.name)
                                    else -> modelOf(item)
                                },
                                gate = gate,
                                modifier = Modifier.onFocusChanged { if (it.hasFocus || it.isFocused) focusedItem = item }
                                    .then(if (isFirst) Modifier.focusRequester(first) else Modifier),
                                onClick = { choose(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(
    item: WallItem,
    label: String,
    picked: Boolean,
    loading: Boolean,
    progress: Float,
    model: Any?,
    gate: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    StageSurface(onClick = onClick, radius = 14.mpx, modifier = modifier.width(TILE_W.mpx)) { focused ->
        Column(Modifier.padding(6.mpx), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
            Box(Modifier.fillMaxWidth().height((TILE_W * 9 / 16).mpx).clip(RoundedCornerShape(9.mpx))) {
                if (model != null) WallLayer(model, Modifier.fillMaxSize())
                else if (item == WallItem.None && gate) GatePlainPreview(Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(StageColors.ControlFill))
                if (loading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0.03f, 1f)).height(5.mpx).background(stageAccent.accent))
                }
                if (picked) Box(
                    Modifier.align(Alignment.TopEnd).padding(6.mpx).size(22.mpx).clip(CircleShape).background(stageAccent.accent),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", style = stageText(13, 900), color = stageAccent.onAccent) }
            }
            Text(label, style = stageText(15, 800), maxLines = 1, color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text)
        }
    }
}

/** The profile screen's own look, roughly: its dark page and a soft light. */
@Composable
private fun GatePlainPreview(modifier: Modifier) {
    Box(modifier.background(Brush.radialGradient(listOf(Color(0xFF1C3B3A), Color(0xFF05080A)), radius = 1400f)))
}

/** A sketch of the profile screen over the preview — the greeting and four round avatars — so you see how it reads. */
@Composable
private fun GateMock(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.mpx)) {
        Box(Modifier.width(360.mpx).height(26.mpx).clip(RoundedCornerShape(13.mpx)).background(Color.White.copy(alpha = 0.85f)))
        Row(horizontalArrangement = Arrangement.spacedBy(36.mpx)) {
            listOf(0xFF4FD1C5, 0xFFFF8A5B, 0xFF8AB4F8, 0xFFFF2E9A).forEach { c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    Box(Modifier.size(110.mpx).clip(CircleShape).background(Color(c)))
                    Box(Modifier.width(80.mpx).height(14.mpx).clip(RoundedCornerShape(7.mpx)).background(Color.White.copy(alpha = 0.75f)))
                }
            }
        }
    }
}

@Composable
private fun wallLabel(item: WallItem): String = when (item) {
    WallItem.None -> stringResource(R.string.glcn_gate_wall_none)
    WallItem.AppBackground -> stringResource(R.string.glcn_gate_wall_app)
    is WallItem.Anim -> stringResource(
        when (item.kind) {
            "ondas" -> R.string.glcn_wall_ondas
            "brillos" -> R.string.glcn_wall_brillos
            else -> R.string.glcn_style_aurora
        },
    )
    is WallItem.Photo -> stringResource(
        when (item.name) {
            "via_lactea" -> R.string.glcn_wall_via_lactea
            "aurora_nieve" -> R.string.glcn_wall_aurora_nieve
            "aurora_pinos" -> R.string.glcn_wall_aurora_pinos
            "puerto" -> R.string.glcn_wall_puerto
            "bosque" -> R.string.glcn_wall_bosque
            "nieve_violeta" -> R.string.glcn_wall_nieve_violeta
            "siluetas" -> R.string.glcn_wall_siluetas
            "pico_lila" -> R.string.glcn_wall_pico_lila
            "nubes_rosa" -> R.string.glcn_wall_nubes_rosa
            "duna" -> R.string.glcn_wall_duna
            "mar_beige" -> R.string.glcn_wall_mar_beige
            "muelle" -> R.string.glcn_wall_muelle
            "valle" -> R.string.glcn_wall_valle
            else -> R.string.glcn_wall_cordillera
        },
    )
}

private const val WALL_ROUND_MS = 45_000
private const val PREVIEW_FADE_MS = 260
private const val TILE_W = 216

/** glcn: the profile screen background's current choice, as the Settings row shows it. */
@Composable
fun gateWallLabel(): String {
    GateWall.ensure(LocalContext.current)
    return wallLabel(
        when (val c = GateWall.choice) {
            GateWall.NONE -> WallItem.None
            GateWall.APP -> WallItem.AppBackground
            else -> WallItem.Photo(c)
        },
    )
}
