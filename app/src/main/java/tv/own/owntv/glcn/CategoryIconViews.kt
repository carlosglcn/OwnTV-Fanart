package tv.own.owntv.glcn

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusRequester
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Text
import tv.own.owntv.features.live.LiveCategoryEntry
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** glcn: the picture of a provider group in a category list ([CategoryIcons]), or null for a fixed entry. */
internal fun LiveCategoryEntry.groupGlyph(): CategoryIcons.Glyph? {
    if (icon != null || item.nested) return null
    val n = name ?: return null
    return CategoryIcons.glyph(n.name, n.tags, n.country)
}

/** glcn: a group's picture at the size of the list's line icons: the emoji, or the posters' quality chip. */
@Composable
internal fun CategoryGlyphView(glyph: CategoryIcons.Glyph, modifier: Modifier = Modifier) {
    when (glyph) {
        is CategoryIcons.Glyph.Emoji -> Box(modifier.size(24.mpx), contentAlignment = Alignment.Center) {
            Text(glyph.text, style = stageText(18, 400), textAlign = TextAlign.Center, maxLines = 1)
        }
        is CategoryIcons.Glyph.Badge -> Text(
            glyph.text,
            style = stageText(if (glyph.text.length > 3) 10.5f else 11.5f, 800),
            color = if (glyph.gold) Color(0xFFF2C14E) else Color.White,
            maxLines = 1,
            modifier = modifier
                .background(Color(8, 12, 14).copy(alpha = 0.82f), RoundedCornerShape(6.mpx))
                .padding(horizontal = 5.mpx, vertical = 2.mpx),
        )
    }
}

/**
 * glcn: choose a group's icon — a grid of pictures, then Automatic (back to the one read from the name) and No icon.
 * Hold OK on a group › Icon.
 */
@Composable
internal fun CategoryIconPicker(groupName: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    val pick: (String?) -> Unit = { code -> CategoryIcons.choose(context, groupName, code); onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(
                Modifier.width(640.mpx).trapAllFocusExit().focusGroup(),
            ) {
                tv.own.owntv.ui.stage.StageMenuHeader(title = groupName, subtitle = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_category_icon))
                CategoryIcons.CHOICES.chunked(PICKER_COLUMNS).forEachIndexed { r, row ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(horizontal = 8.mpx, vertical = 4.mpx),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.mpx),
                    ) {
                        row.forEachIndexed { c, code ->
                            tv.own.owntv.ui.stage.StageSurface(
                                onClick = { pick(code) },
                                radius = 14.mpx,
                                modifier = Modifier.size(84.mpx, 64.mpx).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Text(code, style = stageText(28, 400), maxLines = 1) }
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.mpx))
                tv.own.owntv.ui.stage.StageMenuItem(
                    androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_category_icon_auto),
                    onClick = { pick(null) }, icon = tv.own.owntv.ui.components.OwnTVIcon.SPARKLE,
                )
                tv.own.owntv.ui.stage.StageMenuItem(
                    androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.glcn_category_icon_none),
                    onClick = { pick(CategoryIcons.NONE) }, icon = tv.own.owntv.ui.components.OwnTVIcon.CLOSE,
                )
            }
        }
    }
}

private const val PICKER_COLUMNS = 6
