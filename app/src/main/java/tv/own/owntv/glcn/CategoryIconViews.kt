package tv.own.owntv.glcn

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.withTransform
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

/** glcn: a group's picture at the size of the list's line icons: the line icon in [tint], or the posters' chip. */
@Composable
internal fun CategoryGlyphView(
    glyph: CategoryIcons.Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.mpx,
    /** The group's name, read out when the logo stands in for it. */
    name: String? = null,
) {
    when (glyph) {
        is CategoryIcons.Glyph.Icon -> CategoryIconShape(glyph.key, tint, modifier.size(size))
        // The platform's logo in the row's own colour (no plate): light on the list, dark on the focused row.
        // Alone (no name beside it) it is drawn larger, in the name's place.
        is CategoryIcons.Glyph.Logo -> {
            val alone = CategoryIcons.hidesName(glyph)
            Box(
                modifier.size(if (alone) size * 6f else size * 2.6f, if (alone) size * 1.5f else size * 1.1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                coil3.compose.AsyncImage(
                    model = glyph.url, contentDescription = if (alone) name else null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.SrcIn),
                    modifier = Modifier.matchParentSize(),
                )
            }
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

/** glcn: one of [CategoryGlyphPaths] drawn to fit, parsed once per key. */
@Composable
internal fun CategoryIconShape(key: String, tint: Color, modifier: Modifier) {
    val path = androidx.compose.runtime.remember(key) { CategoryIconCache.path(key) } ?: return
    androidx.compose.foundation.Canvas(modifier) {
        val scale = size.minDimension / ICON_BOX
        withTransform({
            translate(left = (size.width - ICON_BOX * scale) / 2f, top = (size.height - ICON_BOX * scale) / 2f + ICON_BOX * scale)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) { drawPath(path, tint) }
    }
}

private object CategoryIconCache {
    private val parsed = HashMap<String, androidx.compose.ui.graphics.Path>()
    fun path(key: String): androidx.compose.ui.graphics.Path? = synchronized(parsed) {
        parsed[key] ?: CategoryGlyphPaths.paths[key]?.let { d ->
            runCatching { androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }.getOrNull()?.also { parsed[key] = it }
        }
    }
}

/** Material Symbols draw in a 960-unit box. */
private const val ICON_BOX = 960f

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
                                modifier = Modifier.size(72.mpx, 60.mpx).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { focused -> CategoryIconShape(code, if (focused) tv.own.owntv.ui.theme.stageAccent.onAccent else tv.own.owntv.ui.theme.StageColors.Text, Modifier.size(34.mpx)) }
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

private const val PICKER_COLUMNS = 7
