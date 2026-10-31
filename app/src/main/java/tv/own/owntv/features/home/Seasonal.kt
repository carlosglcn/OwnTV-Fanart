package tv.own.owntv.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.Season
import tv.own.owntv.core.theme.Seasons
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * glcn: the season's touches over every menu (Settings → Appearance → Seasonal themes), never over
 * the player. Drawn as a few shapes and emoji — no pictures to load, a few dozen dots a frame at most
 * — so a SHIELD does not notice. Nothing here can take focus or a key press. Still with animations off.
 */
@Composable
internal fun SeasonalOverlay(modifier: Modifier = Modifier) {
    val settings = koinInject<SettingsRepository>()
    val mode by settings.seasonMode.collectAsStateWithLifecycle(initialValue = 0)
    if (mode == 0) return
    // Re-read every so often, so a TV left on through midnight on 30 September turns to October.
    var season by remember { mutableStateOf(Seasons.current()) }
    LaunchedEffect(Unit) { while (true) { delay(15 * 60_000L); season = Seasons.current() } }
    Seasonal.Layer(season, animated = mode == 2, modifier = modifier)
}

internal object Seasonal {

    @Composable
    fun Layer(season: Season, animated: Boolean, modifier: Modifier = Modifier) {
        if (season == Season.NONE) return
        val animate = animated && tv.own.owntv.ui.theme.animationsOn
        // glcn: its own layer, so each frame of the leaves, bats or snow re-records this overlay alone — drawn straight
        // into the shell's layer, every particle frame re-recorded the screen around it too, scroll or not.
        Box(modifier.fillMaxSize().graphicsLayer()) {
            when (season) {
                Season.HALLOWEEN -> {
                    Cobweb(Modifier.align(Alignment.TopEnd))
                    if (animate) {
                        Particles(Kind.LEAF, count = 9)
                        BatFlock()
                        Flyer("👻", size = 34, everyMs = 95_000, firstMs = 40_000, durationMs = 16_000, yFraction = 0.42f, wobble = 0.04f, alpha = 0.45f, reverse = true)
                        Spider()
                        Cats()
                    }
                }
                Season.CHRISTMAS -> {
                    Lights(animate, Modifier.align(Alignment.TopCenter))
                    if (animate) {
                        Particles(Kind.SNOW, count = 42)
                        Flyer("🦌🦌🛷🎅", size = 28, everyMs = 120_000, firstMs = 20_000, durationMs = 15_000, yFraction = 0.07f, wobble = 0.02f, alpha = 0.85f, reverse = true)
                    }
                }
                Season.NEW_YEAR -> if (animate) Fireworks()
                Season.CARNIVAL -> if (animate) Particles(Kind.CONFETTI, count = 28)
                Season.VALENTINE -> if (animate) Particles(Kind.HEART, count = 14)
                Season.NONE -> Unit
            }
        }
    }

    // --- Halloween --------------------------------------------------------------------------------

    /** Two or three bats crossing the top together, one way then the other. */
    @Composable
    private fun BoxScope.BatFlock() {
        Flyer("🦇", size = 36, everyMs = 38_000, firstMs = 5_000, durationMs = 9_000, yFraction = 0.07f, wobble = 0.05f)
        Flyer("🦇", size = 26, everyMs = 38_000, firstMs = 5_900, durationMs = 9_500, yFraction = 0.13f, wobble = 0.06f, alpha = 0.75f)
        Flyer("🦇", size = 30, everyMs = 52_000, firstMs = 24_000, durationMs = 10_000, yFraction = 0.10f, wobble = 0.05f, reverse = true)
    }

    /** A spider lets itself down on its thread somewhere along the top, sways, and climbs back up. */
    @Composable
    private fun BoxScope.Spider() {
        val drop = remember { Animatable(0f) }
        var x by remember { mutableStateOf(0.7f) }
        val rnd = remember { Random(7) }
        LaunchedEffect(Unit) {
            delay(15_000)
            while (true) {
                x = 0.35f + rnd.nextFloat() * 0.55f
                drop.animateTo(1f, tween(3_000, easing = FastOutSlowInEasing))
                delay(4_000)
                drop.animateTo(0f, tween(2_500))
                delay(60_000)
            }
        }
        var box by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.matchParentSize().onSizeChanged { box = it }) {
            Canvas(Modifier.fillMaxSize()) {
                val d = drop.value
                if (d <= 0.01f) return@Canvas
                val px = size.width * x
                val len = 220.mpx.toPx() * d
                drawLine(Color.White.copy(alpha = 0.35f), Offset(px, 0f), Offset(px, len), strokeWidth = 1.2f)
            }
            Text(
                "🕷️",
                fontSize = 30.mpxSp,
                modifier = Modifier.graphicsLayer {
                    val d = drop.value
                    alpha = if (d <= 0.01f) 0f else 0.9f
                    translationX = box.width * x - 15.mpx.toPx()
                    translationY = 220.mpx.toPx() * d - 8.mpx.toPx()
                    rotationZ = sin(d * 9f) * 8f
                },
            )
        }
    }

    /**
     * A black cat looks up from the bottom right; later a white Turkish Angora with blue eyes from the
     * bottom left. Each blinks once and goes.
     */
    @Composable
    private fun BoxScope.Cats() {
        CatPeek(
            fur = Color(0xFF121216), rim = Color.White.copy(alpha = 0.28f), eyes = Color(0xFFD9E04A),
            inner = Color(0xFF2A2026), firstMs = 20_000, everyMs = 75_000,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 120.mpx),
        )
        CatPeek(
            fur = Color(0xFFF7F6F2), rim = Color(0xFFB9B4AE).copy(alpha = 0.7f), eyes = Color(0xFF3FA2FF),
            inner = Color(0xFFF2B8C2), firstMs = 57_000, everyMs = 75_000,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 260.mpx),
        )
    }

    @Composable
    private fun CatPeek(fur: Color, rim: Color, eyes: Color, inner: Color, firstMs: Long, everyMs: Long, modifier: Modifier) {
        val peek = remember { Animatable(0f) }
        val blink = remember { Animatable(1f) }
        LaunchedEffect(Unit) {
            delay(firstMs)
            while (true) {
                peek.animateTo(1f, tween(1_000, easing = FastOutSlowInEasing))
                delay(1_400)
                blink.animateTo(0.08f, tween(110)); blink.animateTo(1f, tween(160))
                delay(2_200)
                peek.animateTo(0f, tween(800))
                delay(everyMs)
            }
        }
        Canvas(
            modifier.size(150.mpx, 120.mpx).graphicsLayer {
                translationY = (1f - peek.value) * size.height
                alpha = if (peek.value <= 0.01f) 0f else 1f
            },
        ) { drawCat(fur, rim, eyes, inner, blink.value) }
    }

    /** A cat's head and front paws over the bottom edge. */
    private fun DrawScope.drawCat(fur: Color, rim: Color, eyes: Color, inner: Color, blink: Float) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headTop = h * 0.22f
        val headW = w * 0.62f
        val headH = h * 0.62f
        // Ears.
        fun ear(dir: Float) = Path().apply {
            moveTo(cx + dir * headW * 0.42f, headTop + headH * 0.30f)
            lineTo(cx + dir * headW * 0.40f, headTop - h * 0.16f)
            lineTo(cx + dir * headW * 0.10f, headTop + headH * 0.10f)
            close()
        }
        fun earIn(dir: Float) = Path().apply {
            moveTo(cx + dir * headW * 0.36f, headTop + headH * 0.22f)
            lineTo(cx + dir * headW * 0.36f, headTop - h * 0.07f)
            lineTo(cx + dir * headW * 0.17f, headTop + headH * 0.12f)
            close()
        }
        for (d in listOf(-1f, 1f)) {
            drawPath(ear(d), fur)
            drawPath(ear(d), rim, style = Stroke(width = 2f))
            drawPath(earIn(d), inner)
        }
        // Head.
        val headTopLeft = Offset(cx - headW / 2f, headTop)
        drawOval(fur, topLeft = headTopLeft, size = Size(headW, headH))
        drawOval(rim, topLeft = headTopLeft, size = Size(headW, headH), style = Stroke(width = 2f))
        // Eyes: almond shapes with a slit pupil; [blink] closes them.
        val eyeW = headW * 0.2f
        val eyeH = headH * 0.24f * blink
        val eyeY = headTop + headH * 0.42f
        for (d in listOf(-1f, 1f)) {
            val ex = cx + d * headW * 0.2f
            drawOval(eyes, topLeft = Offset(ex - eyeW / 2f, eyeY - eyeH / 2f), size = Size(eyeW, eyeH))
            drawOval(Color.Black, topLeft = Offset(ex - eyeW * 0.09f, eyeY - eyeH / 2f), size = Size(eyeW * 0.18f, eyeH))
            if (blink > 0.5f) drawCircle(Color.White.copy(alpha = 0.8f), radius = eyeW * 0.09f, center = Offset(ex + eyeW * 0.18f, eyeY - eyeH * 0.18f))
        }
        // Nose and whiskers.
        val noseY = headTop + headH * 0.62f
        drawPath(
            Path().apply {
                moveTo(cx - headW * 0.05f, noseY); lineTo(cx + headW * 0.05f, noseY); lineTo(cx, noseY + headH * 0.06f); close()
            },
            Color(0xFFE88A9A),
        )
        val whisker = rim.copy(alpha = 0.8f)
        for (d in listOf(-1f, 1f)) for (k in listOf(-1f, 0f, 1f)) {
            drawLine(
                whisker,
                Offset(cx + d * headW * 0.12f, noseY + headH * 0.04f),
                Offset(cx + d * headW * 0.62f, noseY + headH * (0.02f + k * 0.07f)),
                strokeWidth = 1.3f,
            )
        }
        // Paws over the edge.
        for (d in listOf(-1f, 1f)) {
            val pw = headW * 0.3f
            val tl = Offset(cx + d * headW * 0.24f - pw / 2f, h * 0.84f)
            drawOval(fur, topLeft = tl, size = Size(pw, h * 0.2f))
            drawOval(rim, topLeft = tl, size = Size(pw, h * 0.2f), style = Stroke(width = 2f))
        }
    }

    /** Static: a fine cobweb in the top corner. */
    @Composable
    private fun Cobweb(modifier: Modifier) {
        Canvas(modifier.size(190.mpx)) {
            val c = Color.White.copy(alpha = 0.16f)
            val origin = Offset(size.width, 0f)
            val r = size.width
            val spokes = 6
            for (i in 0..spokes) {
                val a = (PI / 2 * i / spokes + PI / 2).toFloat()
                drawLine(c, origin, Offset(origin.x + cos(a) * r, origin.y + sin(a) * r), strokeWidth = 1.4f)
            }
            for (ring in 1..4) {
                val rr = r * ring / 4.5f
                val path = Path()
                for (i in 0..spokes) {
                    val a = (PI / 2 * i / spokes + PI / 2).toFloat()
                    val pt = Offset(origin.x + cos(a) * rr, origin.y + sin(a) * rr)
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.quadraticTo(
                        origin.x + cos(a - 0.13f) * rr * 0.9f, origin.y + sin(a - 0.13f) * rr * 0.9f, pt.x, pt.y,
                    )
                }
                drawPath(path, c, style = Stroke(width = 1.4f))
            }
        }
    }

    // --- Shared pieces ---------------------------------------------------------------------------

    /** Something that crosses the screen now and then — a bat, a ghost, the sleigh. */
    @Composable
    private fun BoxScope.Flyer(
        glyph: String,
        size: Int,
        everyMs: Long,
        firstMs: Long,
        durationMs: Int,
        yFraction: Float,
        wobble: Float,
        alpha: Float = 0.9f,
        reverse: Boolean = false,
    ) {
        var box by remember { mutableStateOf(IntSize.Zero) }
        val p = remember { Animatable(-1f) }
        LaunchedEffect(Unit) {
            delay(firstMs)
            while (true) {
                p.snapTo(0f)
                p.animateTo(1f, tween(durationMs, easing = LinearEasing))
                p.snapTo(-1f)
                delay(everyMs)
            }
        }
        Text(
            glyph,
            fontSize = size.mpxSp,
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { box = it }
                .graphicsLayer {
                    val v = p.value
                    if (v < 0f || box.width == 0) {
                        this.alpha = 0f
                        return@graphicsLayer
                    }
                    val w = box.width.toFloat()
                    val h = box.height.toFloat()
                    translationX = if (reverse) w - v * (w + 240f) else -240f + v * (w + 240f)
                    translationY = h * yFraction + sin(v * PI.toFloat() * 6f) * h * wobble
                    // Fades in and out at the edges so nothing pops.
                    this.alpha = alpha * (minOf(v, 1f - v) * 8f).coerceIn(0f, 1f)
                },
        )
    }

    /** Christmas: a string of small coloured lights along the top edge, twinkling when animated. */
    @Composable
    private fun Lights(animate: Boolean, modifier: Modifier) {
        var tick by remember { mutableIntStateOf(0) }
        if (animate) LaunchedEffect(Unit) { while (true) { delay(900); tick++ } }
        val colours = remember { listOf(Color(0xFFFF5A5A), Color(0xFFFFD25A), Color(0xFF5AD27A), Color(0xFF5AA8FF)) }
        Canvas(modifier.fillMaxWidth().height(26.mpx)) {
            val n = 34
            val step = size.width / n
            val sag = size.height * 0.45f
            var prev: Offset? = null
            for (i in 0..n) {
                val pt = Offset(i * step, 4f + sag * sin((i % 6) / 6f * PI.toFloat()))
                prev?.let { drawLine(Color.White.copy(alpha = 0.12f), it, pt, strokeWidth = 1.2f) }
                prev = pt
                if (i % 2 == 0) {
                    val col = colours[(i / 2) % colours.size]
                    val on = !animate || (i / 2 + tick) % 3 != 0
                    drawCircle(col.copy(alpha = if (on) 0.85f else 0.25f), radius = 4.5f, center = pt.copy(y = pt.y + 5f))
                    if (on) drawCircle(col.copy(alpha = 0.18f), radius = 11f, center = pt.copy(y = pt.y + 5f))
                }
            }
        }
    }

    private enum class Kind { SNOW, CONFETTI, HEART, LEAF }

    private class Bit(var x: Float, var y: Float, val speed: Float, val drift: Float, val size: Float, val phase: Float, val colour: Color)

    /** Falling snow, confetti or leaves, or hearts rising — one canvas, redrawn by itself. */
    @Composable
    private fun Particles(kind: Kind, count: Int) {
        val rnd = remember { Random(kind.ordinal) }
        val palette = remember {
            when (kind) {
                Kind.SNOW -> listOf(Color.White)
                Kind.CONFETTI -> listOf(Color(0xFFFF5A8A), Color(0xFFFFD25A), Color(0xFF5AD2C8), Color(0xFF9A7BFF))
                Kind.HEART -> listOf(Color(0xFFFF4D6D), Color(0xFFFF8FA3))
                Kind.LEAF -> listOf(Color(0xFFE07A2E), Color(0xFFC2512B), Color(0xFFB8862F))
            }
        }
        val bits = remember {
            List(count) {
                Bit(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    speed = (if (kind == Kind.LEAF) 0.025f else 0.018f) + rnd.nextFloat() * 0.03f,
                    drift = if (kind == Kind.LEAF) 0.04f + rnd.nextFloat() * 0.03f else rnd.nextFloat() * 0.02f,
                    size = when (kind) {
                        Kind.HEART -> 9f + rnd.nextFloat() * 7f
                        Kind.LEAF -> 9f + rnd.nextFloat() * 6f
                        else -> 2.2f + rnd.nextFloat() * 3.2f
                    },
                    phase = rnd.nextFloat() * 6.28f, colour = palette[rnd.nextInt(palette.size)],
                )
            }
        }
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            var last = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val dt = ((now - last).coerceAtMost(64L)) / 1000f
                    last = now
                    for (b in bits) {
                        val dir = if (kind == Kind.HEART) -1f else 1f
                        b.y += dir * b.speed * dt * (if (kind == Kind.HEART) 0.6f else 1f)
                        if (b.y > 1.05f) { b.y = -0.05f; b.x = rnd.nextFloat() }
                        if (b.y < -0.05f) { b.y = 1.05f; b.x = rnd.nextFloat() }
                    }
                    frame = now
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = frame / 1000f
            for (b in bits) {
                val x = (b.x + sin(t * 0.6f + b.phase) * b.drift) * size.width
                val y = b.y * size.height
                when (kind) {
                    Kind.SNOW -> drawCircle(b.colour.copy(alpha = 0.55f), radius = b.size, center = Offset(x, y))
                    Kind.CONFETTI -> rotate(degrees = (t * 90f + b.phase * 57f) % 360f, pivot = Offset(x, y)) {
                        drawRect(b.colour.copy(alpha = 0.7f), topLeft = Offset(x - b.size, y - b.size * 0.5f), size = Size(b.size * 2f, b.size))
                    }
                    Kind.HEART -> drawPath(heart(x, y, b.size), b.colour.copy(alpha = 0.35f))
                    Kind.LEAF -> rotate(degrees = sin(t * 1.3f + b.phase) * 50f + b.phase * 40f, pivot = Offset(x, y)) {
                        drawPath(leaf(x, y, b.size), b.colour.copy(alpha = 0.55f))
                        drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, y - b.size), Offset(x, y + b.size), strokeWidth = 1f)
                    }
                }
            }
        }
    }

    private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.6f, cy - s * 0.2f, cx - s * 0.6f, cy - s * 1.3f, cx, cy - s * 0.4f)
        cubicTo(cx + s * 0.6f, cy - s * 1.3f, cx + s * 1.6f, cy - s * 0.2f, cx, cy + s * 0.9f)
        close()
    }

    private fun leaf(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy - s)
        quadraticTo(cx + s * 0.8f, cy, cx, cy + s)
        quadraticTo(cx - s * 0.8f, cy, cx, cy - s)
        close()
    }

    private class Burst(val x: Float, val y: Float, val start: Long, val colour: Color)

    /** New Year's Eve: small fireworks bursting high up, one every few seconds. */
    @Composable
    private fun Fireworks() {
        val rnd = remember { Random(31) }
        val colours = remember { listOf(Color(0xFFFFD25A), Color(0xFFFF5A8A), Color(0xFF5AD2C8), Color(0xFFFFFFFF)) }
        val bursts = remember { ArrayDeque<Burst>() }
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            var next = 0L
            while (true) {
                withFrameMillis { now ->
                    if (now >= next) {
                        bursts.addLast(Burst(0.15f + rnd.nextFloat() * 0.7f, 0.08f + rnd.nextFloat() * 0.22f, now, colours[rnd.nextInt(colours.size)]))
                        if (bursts.size > 4) bursts.removeFirst()
                        next = now + 2_200L + rnd.nextLong(2_500L)
                    }
                    frame = now
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            for (b in bursts) {
                val age = (frame - b.start) / 1600f
                if (age !in 0f..1f) continue
                val cx = b.x * size.width
                val cy = b.y * size.height
                val r = 120f * (1f - (1f - age) * (1f - age))
                val a = (1f - age) * 0.8f
                for (i in 0 until 22) {
                    val ang = i / 22f * 2f * PI.toFloat()
                    drawCircle(b.colour.copy(alpha = a), radius = 2.6f, center = Offset(cx + cos(ang) * r, cy + sin(ang) * r + age * age * 40f))
                }
            }
        }
    }
}
