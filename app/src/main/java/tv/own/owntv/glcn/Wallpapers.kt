package tv.own.owntv.glcn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the app's own wallpapers. Pictures (made for the app, 1080p — the interface is drawn at 1080p, so 4K would
 * only weigh more) are copied from the app into its files and set as the background picture. Animated ones are
 * drawn by the app itself (no video: they cost almost nothing and stand still in the light mode); they are stored as
 * the background "picture" `anim:<kind>`, which the shell draws instead of loading a file.
 */
object Wallpapers {
    const val ANIM_PREFIX = "anim:"
    val pictures = listOf("nebulosa", "brasa", "glaciar", "dunas")
    val animated = listOf("aurora", "ondas", "brillos")

    fun animKind(path: String?): String? = path?.takeIf { it.startsWith(ANIM_PREFIX) }?.removePrefix(ANIM_PREFIX)

    fun assetUri(name: String) = "file:///android_asset/wallpapers/$name.jpg"

    /** Copies the picture out of the app into its files (the background picture is a file) and returns its path. */
    fun install(context: android.content.Context, name: String): String? = runCatching {
        val dir = java.io.File(context.filesDir, "backgrounds").apply { mkdirs() }
        val out = java.io.File(dir, "wall_$name.jpg")
        if (!out.exists()) context.assets.open("wallpapers/$name.jpg").use { inp -> out.outputStream().use { inp.copyTo(it) } }
        out.absolutePath
    }.getOrNull()
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

/** glcn: the gallery — animated wallpapers and pictures, each shown as it really looks; OK sets it. */
@Composable
fun WallpaperGalleryPopup(current: String?, onPicked: (path: String, picture: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.glcn_wall_title), body = stringResource(R.string.glcn_wall_desc), width = 1500.mpx) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            tv.own.owntv.ui.stage.StageGroupLabel(stringResource(R.string.glcn_wall_animated), sheet = true)
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                Wallpapers.animated.forEachIndexed { i, k ->
                    WallTile(
                        label = wallLabel(k), picked = Wallpapers.animKind(current) == k,
                        modifier = Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        onClick = { onPicked(Wallpapers.ANIM_PREFIX + k, false); onDismiss() },
                    ) { AnimatedWallpaper(k, Modifier.fillMaxSize()) }
                }
                Box(Modifier.weight(1f))
            }
            tv.own.owntv.ui.stage.StageGroupLabel(stringResource(R.string.glcn_wall_pictures), sheet = true)
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                Wallpapers.pictures.forEach { n ->
                    WallTile(
                        label = wallLabel(n), picked = current?.endsWith("wall_$n.jpg") == true, modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                val path = withContext(Dispatchers.IO) { Wallpapers.install(context, n) } ?: return@launch
                                onPicked(path, true); onDismiss()
                            }
                        },
                    ) {
                        coil3.compose.AsyncImage(model = Wallpapers.assetUri(n), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(label: String, picked: Boolean, modifier: Modifier, onClick: () -> Unit, preview: @Composable () -> Unit) {
    StageSurface(onClick = onClick, radius = 16.mpx, modifier = modifier) { focused ->
        Column(Modifier.padding(8.mpx), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.mpx))) { preview() }
            Text(label, style = stageText(17, 800), maxLines = 1, color = if (focused) stageAccent.onAccent else if (picked) stageAccent.accent else StageColors.Text)
        }
    }
}

@Composable
private fun wallLabel(key: String): String = stringResource(
    when (key) {
        "ondas" -> R.string.glcn_wall_ondas
        "brillos" -> R.string.glcn_wall_brillos
        "nebulosa" -> R.string.glcn_wall_nebulosa
        "brasa" -> R.string.glcn_wall_brasa
        "glaciar" -> R.string.glcn_wall_glaciar
        "dunas" -> R.string.glcn_wall_dunas
        else -> R.string.glcn_style_aurora
    },
)

private const val WALL_ROUND_MS = 45_000
