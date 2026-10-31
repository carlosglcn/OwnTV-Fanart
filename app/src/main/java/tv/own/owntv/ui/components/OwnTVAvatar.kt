package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Fill

/**
 * An object-themed avatar drawn entirely with Canvas — no image assets, fully scalable. There are
 * exactly [OwnTVAvatars.COUNT] presets (lightning, sword, tree, car, football, rocket, crown, heart,
 * music, gamepad); pass an id and the matching icon-on-color tile is drawn deterministically.
 */
object OwnTVAvatars {
    const val COUNT = tv.own.owntv.core.profile.PROFILE_AVATAR_COUNT
}

@Composable
fun OwnTVAvatar(avatarId: Int, modifier: Modifier = Modifier, imagePath: String = "") {
    // avatarId -1 = "no avatar" → show the Rank 1 ProfileIcon as a default silhouette.
    if (avatarId == -1 && imagePath.isBlank()) {
        ProfileIcon(color = Color(0xFF54F2E2), modifier = modifier.padding(4.dp))
        return
    }
    val i = ((avatarId % OwnTVAvatars.COUNT) + OwnTVAvatars.COUNT) % OwnTVAvatars.COUNT
    Box(modifier = modifier) {
        // The drawn tile stays underneath the picture rather than being replaced by it. It costs
        // nothing (a few Canvas paths), it fills the gap while the file is being read, and — the
        // reason it is done this way — it is what the user sees if the picture has gone: a profile
        // whose image was cleaned up with the app's storage falls back to the tile it always had,
        // instead of a blank hole, with no file-existence check on every single composition.
        if (avatarId != -1) {
            Canvas(modifier = Modifier.matchParentSize()) { drawAvatar(i) }
        }
        if (imagePath.isNotBlank()) {
            AsyncImage(
                model = java.io.File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(AvatarShape),
            )
        }
    }
}

/** glcn: the colour of a preset avatar's tile (the default silhouette's teal for none) — the profile gate's glow. */
fun avatarColor(avatarId: Int): Color {
    if (avatarId < 0) return Color(0xFF54F2E2)
    return BG[((avatarId % OwnTVAvatars.COUNT) + OwnTVAvatars.COUNT) % OwnTVAvatars.COUNT % BG.size]
}

/** Matches the rounding the drawn tiles use, so a picture sits in the same shape as a tile. */
private val AvatarShape = RoundedCornerShape(percent = 30)

private val BG = listOf(
    Color(0xFFF59E0B), // 0 lightning — amber
    Color(0xFF64748B), // 1 sword — slate
    Color(0xFF22C55E), // 2 tree — green
    Color(0xFFEF4444), // 3 car — red
    Color(0xFF059669), // 4 football — emerald
    Color(0xFF6366F1), // 5 rocket — indigo
    Color(0xFFD97706), // 6 crown — gold
    Color(0xFFEC4899), // 7 heart — pink
    Color(0xFF8B5CF6), // 8 music — violet
    Color(0xFF06B6D4), // 9 gamepad — cyan
    // glcn: fourteen more.
    Color(0xFFEAB308), // 10 star — yellow
    Color(0xFF1E40AF), // 11 moon — navy
    Color(0xFFF97316), // 12 sun — orange
    Color(0xFFE11D48), // 13 flame — rose
    Color(0xFF14B8A6), // 14 cat — teal
    Color(0xFF92400E), // 15 paw — brown
    Color(0xFFB91C1C), // 16 popcorn — crimson
    Color(0xFF27272A), // 17 clapperboard — charcoal
    Color(0xFFC026D3), // 18 headphones — fuchsia
    Color(0xFF0284C7), // 19 basketball — sky
    Color(0xFF65A30D), // 20 flower — lime
    Color(0xFF3F6212), // 21 mountains — olive
    Color(0xFF4C1D95), // 22 planet — deep purple
    Color(0xFF0F172A), // 23 ghost — midnight
)

private fun DrawScope.drawAvatar(i: Int) {
    val s = size.minDimension
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val w = Color.White
    val ink = Color(0xFF10181C)
    val bg = BG[i]

    // A lit gradient rather than one flat colour, with a soft sheen across the top corner and a
    // hairline rim. The shapes are unchanged — what made these read as placeholders next to the rest
    // of the app was the flat fill, not the drawings.
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(bg.lighten(TILE_TOP_LIGHTEN), bg, bg.darken(TILE_BOTTOM_DARKEN)),
            start = Offset(0f, 0f),
            end = Offset(s, s),
        ),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.30f),
    )
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            endY = s * 0.55f,
        ),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.30f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.14f),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.30f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.012f),
    )

    when (i) {
        0 -> { // Lightning bolt
            drawPath(poly(listOf(p(0.57f, 0.13f), p(0.31f, 0.55f), p(0.47f, 0.55f), p(0.42f, 0.87f), p(0.71f, 0.41f), p(0.53f, 0.41f), p(0.61f, 0.13f))), w, style = Fill)
        }
        1 -> { // Sword
            drawPath(poly(listOf(p(0.5f, 0.12f), p(0.57f, 0.22f), p(0.57f, 0.60f), p(0.43f, 0.60f), p(0.43f, 0.22f))), w, style = Fill)
            drawRoundRect(w, topLeft = p(0.33f, 0.59f), size = Size(0.34f * s, 0.06f * s), cornerRadius = CornerRadius(0.02f * s))
            drawRoundRect(w, topLeft = p(0.455f, 0.65f), size = Size(0.09f * s, 0.18f * s), cornerRadius = CornerRadius(0.02f * s))
            drawCircle(w, 0.045f * s, p(0.5f, 0.86f))
        }
        2 -> { // Tree
            drawRoundRect(w, topLeft = p(0.45f, 0.54f), size = Size(0.10f * s, 0.31f * s), cornerRadius = CornerRadius(0.02f * s))
            drawCircle(w, 0.20f * s, p(0.5f, 0.38f))
            drawCircle(w, 0.145f * s, p(0.33f, 0.47f))
            drawCircle(w, 0.145f * s, p(0.67f, 0.47f))
        }
        3 -> { // Car
            drawRoundRect(w, topLeft = p(0.32f, 0.35f), size = Size(0.36f * s, 0.18f * s), cornerRadius = CornerRadius(0.05f * s))
            drawRoundRect(w, topLeft = p(0.15f, 0.49f), size = Size(0.70f * s, 0.16f * s), cornerRadius = CornerRadius(0.07f * s))
            drawCircle(ink, 0.075f * s, p(0.34f, 0.66f)); drawCircle(w, 0.034f * s, p(0.34f, 0.66f))
            drawCircle(ink, 0.075f * s, p(0.66f, 0.66f)); drawCircle(w, 0.034f * s, p(0.66f, 0.66f))
        }
        4 -> { // Football (soccer ball)
            val c = p(0.5f, 0.5f)
            drawCircle(w, 0.30f * s, c, style = Fill)
            drawPath(pentagon(c, 0.10f * s), ink, style = Fill)
            for (k in 0 until 5) {
                val a = -Math.PI / 2 + k * 2 * Math.PI / 5
                val v = Offset(c.x + (0.10f * s * Math.cos(a)).toFloat(), c.y + (0.10f * s * Math.sin(a)).toFloat())
                val outer = Offset(c.x + (0.27f * s * Math.cos(a)).toFloat(), c.y + (0.27f * s * Math.sin(a)).toFloat())
                drawLine(ink, v, outer, strokeWidth = 0.035f * s, cap = StrokeCap.Round)
            }
        }
        5 -> { // Rocket
            drawRoundRect(w, topLeft = p(0.40f, 0.32f), size = Size(0.20f * s, 0.34f * s), cornerRadius = CornerRadius(0.10f * s))
            drawPath(poly(listOf(p(0.40f, 0.34f), p(0.50f, 0.14f), p(0.60f, 0.34f))), w, style = Fill)
            drawCircle(bg, 0.06f * s, p(0.50f, 0.41f))
            drawPath(poly(listOf(p(0.40f, 0.54f), p(0.29f, 0.68f), p(0.40f, 0.63f))), w, style = Fill)
            drawPath(poly(listOf(p(0.60f, 0.54f), p(0.71f, 0.68f), p(0.60f, 0.63f))), w, style = Fill)
            drawPath(poly(listOf(p(0.44f, 0.66f), p(0.50f, 0.84f), p(0.56f, 0.66f))), Color(0xFFFBBF24), style = Fill)
        }
        6 -> { // Crown
            drawPath(poly(listOf(p(0.26f, 0.64f), p(0.32f, 0.40f), p(0.42f, 0.56f), p(0.50f, 0.34f), p(0.58f, 0.56f), p(0.68f, 0.40f), p(0.74f, 0.64f))), w, style = Fill)
            drawRoundRect(w, topLeft = p(0.26f, 0.62f), size = Size(0.48f * s, 0.10f * s), cornerRadius = CornerRadius(0.02f * s))
        }
        7 -> { // Heart
            drawCircle(w, 0.155f * s, p(0.37f, 0.41f))
            drawCircle(w, 0.155f * s, p(0.63f, 0.41f))
            drawPath(poly(listOf(p(0.225f, 0.45f), p(0.775f, 0.45f), p(0.50f, 0.82f))), w, style = Fill)
        }
        8 -> { // Music note
            drawCircle(w, 0.085f * s, p(0.36f, 0.66f))
            drawCircle(w, 0.085f * s, p(0.64f, 0.60f))
            drawRoundRect(w, topLeft = p(0.435f, 0.30f), size = Size(0.035f * s, 0.36f * s), cornerRadius = CornerRadius(0.01f * s))
            drawRoundRect(w, topLeft = p(0.715f, 0.26f), size = Size(0.035f * s, 0.34f * s), cornerRadius = CornerRadius(0.01f * s))
            drawPath(poly(listOf(p(0.435f, 0.27f), p(0.75f, 0.23f), p(0.75f, 0.33f), p(0.435f, 0.37f))), w, style = Fill)
        }
        9 -> { // Gamepad
            drawRoundRect(w, topLeft = p(0.16f, 0.41f), size = Size(0.68f * s, 0.22f * s), cornerRadius = CornerRadius(0.11f * s))
            drawRoundRect(bg, topLeft = p(0.26f, 0.50f), size = Size(0.13f * s, 0.04f * s), cornerRadius = CornerRadius(0.01f * s))
            drawRoundRect(bg, topLeft = p(0.305f, 0.455f), size = Size(0.04f * s, 0.13f * s), cornerRadius = CornerRadius(0.01f * s))
            drawCircle(bg, 0.03f * s, p(0.64f, 0.49f))
            drawCircle(bg, 0.03f * s, p(0.71f, 0.55f))
        }
        // glcn: fourteen more, drawn the same way (white figure on the tile's own colour).
        10 -> { // Star
            drawPath(poly(listOf(p(.50f, .14f), p(.59f, .38f), p(.85f, .39f), p(.64f, .55f), p(.72f, .81f), p(.50f, .66f), p(.28f, .81f), p(.36f, .55f), p(.15f, .39f), p(.41f, .38f))), w, style = Fill)
        }
        11 -> { // Moon
            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(.47f, .53f), .32f * s)) }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(.61f, .43f), .25f * s)) }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, bite), w, style = Fill)
            drawCircle(w, .03f * s, p(.72f, .26f))
            drawCircle(w, .02f * s, p(.80f, .40f))
        }
        12 -> { // Sun
            drawCircle(w, .15f * s, p(.5f, .5f))
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val c = Math.cos(a).toFloat(); val n = Math.sin(a).toFloat()
                drawLine(w, p(.5f + .25f * c, .5f + .25f * n), p(.5f + .35f * c, .5f + .35f * n), strokeWidth = .06f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        13 -> { // Flame
            val outer = Path().apply {
                moveTo(.50f * s, .12f * s)
                cubicTo(.64f * s, .30f * s, .76f * s, .42f * s, .76f * s, .60f * s)
                cubicTo(.76f * s, .75f * s, .64f * s, .86f * s, .50f * s, .86f * s)
                cubicTo(.36f * s, .86f * s, .24f * s, .75f * s, .24f * s, .60f * s)
                cubicTo(.24f * s, .48f * s, .31f * s, .40f * s, .36f * s, .30f * s)
                cubicTo(.40f * s, .40f * s, .44f * s, .45f * s, .50f * s, .47f * s)
                cubicTo(.47f * s, .34f * s, .45f * s, .24f * s, .50f * s, .12f * s)
                close()
            }
            val inner = Path().apply {
                moveTo(.50f * s, .58f * s)
                cubicTo(.56f * s, .64f * s, .60f * s, .68f * s, .60f * s, .74f * s)
                cubicTo(.60f * s, .80f * s, .55f * s, .84f * s, .50f * s, .84f * s)
                cubicTo(.45f * s, .84f * s, .40f * s, .80f * s, .40f * s, .74f * s)
                cubicTo(.40f * s, .68f * s, .45f * s, .64f * s, .50f * s, .58f * s)
                close()
            }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outer, inner), w, style = Fill)
        }
        14 -> { // Cat
            drawPath(poly(listOf(p(.26f, .48f), p(.28f, .18f), p(.46f, .36f))), w, style = Fill)
            drawPath(poly(listOf(p(.74f, .48f), p(.72f, .18f), p(.54f, .36f))), w, style = Fill)
            drawOval(w, topLeft = p(.23f, .32f), size = Size(.54f * s, .48f * s))
            drawOval(bg, topLeft = p(.365f, .49f), size = Size(.07f * s, .10f * s))
            drawOval(bg, topLeft = p(.565f, .49f), size = Size(.07f * s, .10f * s))
            drawPath(poly(listOf(p(.46f, .63f), p(.54f, .63f), p(.50f, .67f))), bg, style = Fill)
        }
        15 -> { // Paw
            drawOval(w, topLeft = p(.33f, .50f), size = Size(.34f * s, .28f * s))
            drawOval(w, topLeft = p(.23f, .37f), size = Size(.14f * s, .18f * s))
            drawOval(w, topLeft = p(.36f, .23f), size = Size(.14f * s, .18f * s))
            drawOval(w, topLeft = p(.50f, .23f), size = Size(.14f * s, .18f * s))
            drawOval(w, topLeft = p(.63f, .37f), size = Size(.14f * s, .18f * s))
        }
        16 -> { // Popcorn
            drawCircle(w, .10f * s, p(.36f, .36f))
            drawCircle(w, .11f * s, p(.50f, .28f))
            drawCircle(w, .10f * s, p(.64f, .36f))
            drawPath(poly(listOf(p(.26f, .42f), p(.74f, .42f), p(.67f, .86f), p(.33f, .86f))), w, style = Fill)
            drawRect(bg, topLeft = p(.40f, .44f), size = Size(.06f * s, .40f * s))
            drawRect(bg, topLeft = p(.54f, .44f), size = Size(.06f * s, .40f * s))
        }
        17 -> { // Clapperboard
            drawRoundRect(w, topLeft = p(.20f, .44f), size = Size(.60f * s, .38f * s), cornerRadius = CornerRadius(.04f * s))
            drawPath(poly(listOf(p(.18f, .40f), p(.76f, .24f), p(.79f, .34f), p(.21f, .50f))), w, style = Fill)
            drawPath(poly(listOf(p(.30f, .37f), p(.38f, .35f), p(.34f, .46f), p(.26f, .48f))), bg, style = Fill)
            drawPath(poly(listOf(p(.46f, .33f), p(.54f, .31f), p(.50f, .42f), p(.42f, .44f))), bg, style = Fill)
            drawPath(poly(listOf(p(.62f, .29f), p(.70f, .27f), p(.66f, .38f), p(.58f, .40f))), bg, style = Fill)
        }
        18 -> { // Headphones
            drawArc(
                w, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = p(.24f, .24f), size = Size(.52f * s, .52f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = .07f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            drawLine(w, p(.24f, .50f), p(.24f, .60f), strokeWidth = .07f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(w, p(.76f, .50f), p(.76f, .60f), strokeWidth = .07f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawRoundRect(w, topLeft = p(.18f, .54f), size = Size(.16f * s, .28f * s), cornerRadius = CornerRadius(.06f * s))
            drawRoundRect(w, topLeft = p(.66f, .54f), size = Size(.16f * s, .28f * s), cornerRadius = CornerRadius(.06f * s))
        }
        19 -> { // Basketball
            drawCircle(w, .31f * s, p(.5f, .5f))
            val line = androidx.compose.ui.graphics.drawscope.Stroke(width = .035f * s)
            drawLine(bg, p(.19f, .5f), p(.81f, .5f), strokeWidth = .035f * s)
            drawLine(bg, p(.5f, .19f), p(.5f, .81f), strokeWidth = .035f * s)
            drawPath(Path().apply { moveTo(.28f * s, .28f * s); cubicTo(.40f * s, .40f * s, .40f * s, .60f * s, .28f * s, .72f * s) }, bg, style = line)
            drawPath(Path().apply { moveTo(.72f * s, .28f * s); cubicTo(.60f * s, .40f * s, .60f * s, .60f * s, .72f * s, .72f * s) }, bg, style = line)
        }
        20 -> { // Flower
            listOf(.50f to .30f, .69f to .44f, .62f to .66f, .38f to .66f, .31f to .44f).forEach { (x, y) -> drawCircle(w, .13f * s, p(x, y)) }
            drawCircle(Color(0xFFFDE047), .10f * s, p(.5f, .5f))
        }
        21 -> { // Mountains
            drawPath(poly(listOf(p(.12f, .80f), p(.40f, .30f), p(.68f, .80f))), w, style = Fill)
            drawPath(poly(listOf(p(.44f, .80f), p(.66f, .44f), p(.90f, .80f))), w, style = Fill)
            drawPath(poly(listOf(p(.40f, .30f), p(.48f, .44f), p(.43f, .41f), p(.38f, .46f), p(.32f, .44f))), bg, style = Fill)
        }
        22 -> { // Planet
            drawCircle(w, .20f * s, p(.5f, .5f))
            rotate(-18f, pivot = p(.5f, .52f)) {
                drawOval(w, topLeft = p(.14f, .41f), size = Size(.72f * s, .22f * s), style = androidx.compose.ui.graphics.drawscope.Stroke(width = .05f * s))
            }
            drawArc(w, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = p(.30f, .30f), size = Size(.40f * s, .40f * s))
            drawCircle(w, .03f * s, p(.80f, .22f))
        }
        23 -> { // Ghost
            drawPath(
                Path().apply {
                    moveTo(.26f * s, .82f * s)
                    lineTo(.26f * s, .46f * s)
                    arcTo(androidx.compose.ui.geometry.Rect(p(.26f, .22f), Size(.48f * s, .48f * s)), 180f, 180f, false)
                    lineTo(.74f * s, .82f * s)
                    lineTo(.66f * s, .76f * s); lineTo(.58f * s, .82f * s); lineTo(.50f * s, .76f * s)
                    lineTo(.42f * s, .82f * s); lineTo(.34f * s, .76f * s)
                    close()
                },
                w, style = Fill,
            )
            drawOval(bg, topLeft = p(.365f, .40f), size = Size(.09f * s, .12f * s))
            drawOval(bg, topLeft = p(.545f, .40f), size = Size(.09f * s, .12f * s))
        }
    }
}

private fun poly(pts: List<Offset>): Path = Path().apply {
    pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

private fun pentagon(center: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 5) {
        val a = -Math.PI / 2 + i * 2 * Math.PI / 5
        val x = center.x + (r * Math.cos(a)).toFloat()
        val y = center.y + (r * Math.sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** How much lighter the top-left corner of a tile is than its base colour, and darker the bottom. */
private const val TILE_TOP_LIGHTEN = 0.22f
private const val TILE_BOTTOM_DARKEN = 0.14f

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
