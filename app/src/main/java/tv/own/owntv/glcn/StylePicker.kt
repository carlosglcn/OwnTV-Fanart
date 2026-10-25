package tv.own.owntv.glcn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.theme.GlcnStyle
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StagePopupRadio
import tv.own.owntv.ui.theme.mpx

/** glcn: a style's name and what kind it is ("Dark", "Glass · stronger devices"). */
@Composable
fun GlcnStyle.label(): String = stringResource(
    when (this) {
        GlcnStyle.LUMEN_NOCHE -> R.string.glcn_style_lumen_noche
        GlcnStyle.LUMEN_DIA -> R.string.glcn_style_lumen_dia
        GlcnStyle.ATARDECER -> R.string.glcn_style_atardecer
        GlcnStyle.ESTUDIO -> R.string.glcn_style_estudio
        GlcnStyle.VITRINA -> R.string.glcn_style_vitrina
        GlcnStyle.PULSO -> R.string.glcn_style_pulso
        GlcnStyle.NEON -> R.string.glcn_style_neon
        GlcnStyle.NIEVE -> R.string.glcn_style_nieve
        GlcnStyle.PAPEL -> R.string.glcn_style_papel
        GlcnStyle.ARENA -> R.string.glcn_style_arena
    },
)

@Composable
fun GlcnStyle.kind(): String = stringResource(
    when {
        glass -> R.string.glcn_style_kind_glass
        light -> R.string.glcn_style_kind_light
        else -> R.string.glcn_style_kind_dark
    },
)

/** The style's swatch: its page with its accent on top. */
@Composable
fun StyleSwatch(style: GlcnStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.mpx)
            .background(Color(style.page), RoundedCornerShape(12.mpx))
            .border(1.mpx, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.mpx)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.mpx).background(Color(android.graphics.Color.parseColor(style.accentHex)), CircleShape))
    }
}

