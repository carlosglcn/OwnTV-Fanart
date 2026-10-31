package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.drawRadialGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Setup-wizard-style radiance for the solid shell. It is a single lightweight Canvas overlay: no
 * blur or per-card work; every glow is a small cached gradient image (see GradientTextures.kt). Glass
 * mode already gets depth from its wallpaper/frost and skips this layer entirely.
 *
 * glcn: with the effect on, the light is an aurora instead of a pulsing ring — three soft fields of
 * colour, the user's accent and two companions drawn from it (one a little warmer, one deeper), drifting
 * on slow, unhurried paths across the back of the screen and swelling a touch as they pass, so the page
 * seems lit from behind and never quite still. One loop takes a minute and a half and closes on itself
 * without a seam. It moves only in the draw phase (nothing is recomposed), and light mode or Animations
 * off keep it as a still glow.
 */
@Composable
fun SolidAmbientBackdrop(
    glowEnabled: Boolean,
    pulseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    if (!glowEnabled || glass.enabled || tv.own.owntv.ui.theme.LocalStagePalette.current.isLight) return

    val primary = tv.own.owntv.ui.theme.stageAccent.accent
    val (warm, deep) = remember(primary) { companions(primary) }
    val moving = pulseEnabled && !tv.own.owntv.glcn.LowRam.on
    val phase = if (moving) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = AURORA_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "auroraPhase",
        )
    } else null

    Canvas(modifier = modifier) {
        if (phase == null) {
            // Still: the wizard's single glow, as before.
            drawRadialGlow(
                colors = listOf(primary.copy(alpha = 0.14f), primary.copy(alpha = 0.052f), Color.Transparent),
                center = Offset(size.width * 0.54f, size.height * 0.45f),
                radius = size.minDimension * 0.46f,
            )
            return@Canvas
        }
        // Read here, in drawing, so the motion redraws this layer only.
        val a = (phase.value * 2.0 * PI).toFloat()
        val w = size.width
        val h = size.height
        val r = size.minDimension
        // Whole-number frequencies only, so the loop closes on itself.
        val c1 = Offset(w * (0.30f + 0.11f * sin(a)), h * (0.34f + 0.09f * sin(2f * a + 1.1f)))
        val c2 = Offset(w * (0.70f + 0.10f * cos(a + 2.0f)), h * (0.58f + 0.10f * sin(a + 0.4f)))
        val c3 = Offset(w * (0.50f + 0.16f * sin(2f * a + 2.6f)), h * (0.86f + 0.05f * cos(a + 1.7f)))
        drawRadialGlow(
            colors = listOf(deep.copy(alpha = 0.16f), deep.copy(alpha = 0.06f), Color.Transparent),
            center = c3,
            radius = r * (0.62f + 0.04f * sin(a + 0.8f)),
        )
        drawRadialGlow(
            colors = listOf(primary.copy(alpha = 0.15f), primary.copy(alpha = 0.055f), Color.Transparent),
            center = c1,
            radius = r * (0.52f + 0.05f * sin(2f * a)),
        )
        drawRadialGlow(
            colors = listOf(warm.copy(alpha = 0.12f), warm.copy(alpha = 0.045f), Color.Transparent),
            center = c2,
            radius = r * (0.46f + 0.05f * cos(2f * a + 0.6f)),
        )
    }
}

/** A minute and a half: slow enough that the eye feels it more than it sees it. */
private const val AURORA_LOOP_MS = 90_000

/** The accent's two companions: one turned a little towards warm and brightened, one turned the other way and deepened. */
private fun companions(accent: Color): Pair<Color, Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    fun shifted(dh: Float, ds: Float, dv: Float) = Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf((hsv[0] + dh + 360f) % 360f, (hsv[1] + ds).coerceIn(0f, 1f), (hsv[2] + dv).coerceIn(0f, 1f)),
        ),
    )
    return shifted(38f, 0.05f, 0.08f) to shifted(-46f, 0.10f, -0.18f)
}
