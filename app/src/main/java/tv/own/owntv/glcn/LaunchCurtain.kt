package tv.own.owntv.glcn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp

/** The launch backdrop's night sky, for the window behind it before the first frame. */
internal val LAUNCH_SKY = Color(0xFF0B0A1E)

/**
 * glcn: the launch screen — the icon's sunset as a wide landscape (original artwork, see
 * Core tools/glcn/render_launch.py), the mark in the middle and the name under it. It covers the app
 * while the profile and the start-up screen load, then fades away; Android's own icon-only splash
 * shows just for the first frame, on the same night colour.
 */
@Composable
fun LaunchCurtain(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(380)),
    ) {
        // glcn: the very picture the starting window shows (glcn_launch_window), so the hand-off is seamless.
        Box(Modifier.fillMaxSize().background(LAUNCH_SKY)) {
            Image(
                painterResource(R.drawable.glcn_launch_window_art), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
            )
        }
    }
}
