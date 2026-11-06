package tv.own.owntv.glcn

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn (test feature): the TV's other apps, the SHIELD launcher's way — "Your apps" (picked, in order) over "All apps",
 * every banner the same size, the focused one lifting, and the page tinted with its colours. Hold OK: add or take off,
 * move, the app's own Android settings. Shown in More while the Labs switch is on.
 */
@Composable
fun AppsHub(entry: FocusRequester, modifier: Modifier = Modifier, onLeftEdge: () -> Unit = {}) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppShortcuts.ensure(context) }
    val all by produceState(emptyList<AppShortcuts.App>()) { value = withContext(Dispatchers.IO) { AppShortcuts.installed(context) } }
    val byPkg = remember(all) { all.associateBy { it.pkg } }
    val mine = AppShortcuts.chosen.mapNotNull { byPkg[it] }
    // glcn: an app in "Your apps" is not listed again below.
    val rest = all.filter { it.pkg !in AppShortcuts.chosen }
    val leftEdge: (Boolean) -> Modifier = { edge -> if (!edge) Modifier else Modifier.onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionLeft) { onLeftEdge(); true } else false } }
    var focusedTint by remember { mutableStateOf(Color.Transparent) }
    val tint by animateColorAsState(focusedTint, tv.own.owntv.ui.theme.ownTvTween(500), label = "appsTint")
    var menuFor by remember { mutableStateOf<AppShortcuts.App?>(null) }

    Box(modifier.fillMaxSize()) {
        // The focused banner's colour as a soft glow from the top only, darkened first: a white banner (Kodi, Play
        // Store) used to wash the whole page pale.
        val glow = androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.55f).copy(alpha = if (tint.alpha > 0f) 0.45f else 0f)
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.mpx)).background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to glow, 0.55f to Color.Transparent)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = HPAD.mpx, end = HPAD.mpx, top = 6.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (mine.isNotEmpty()) R.string.glcn_apps_yours else R.string.glcn_apps_all), style = stageText(24, 800), color = StageColors.Text, modifier = Modifier.weight(1f))
            }
            if (mine.isNotEmpty()) {
                // Room on every side for the focused banner's lift and ring, so it is never clipped.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP.mpx), contentPadding = PaddingValues(horizontal = HPAD.mpx, vertical = 16.mpx), modifier = Modifier.focusGroup()) {
                    itemsIndexed(mine, key = { _, a -> "m" + a.pkg }) { i, a ->
                        AppBanner(
                            a, width = BANNER_W.mpx,
                            onClick = { AppShortcuts.open(context, a.pkg) },
                            onLongClick = { menuFor = a },
                            onFocus = { focusedTint = it },
                            modifier = (if (i == 0) Modifier.focusRequester(entry) else Modifier).then(leftEdge(i == 0)),
                        )
                    }
                }
            }
            // glcn: the rest in one grid — "Recently used" as a row, then the apps grouped by kind (Video, Music,
            // Games, Other), each group under its heading and starting on a new line.
            val recentApps = AppShortcuts.recent.mapNotNull { byPkg[it] }.filter { it.pkg !in AppShortcuts.chosen }.take(GRID_COLUMNS)
            val groups = listOf(
                AppShortcuts.GROUP_VIDEO to R.string.glcn_apps_group_video,
                AppShortcuts.GROUP_MUSIC to R.string.glcn_apps_group_music,
                AppShortcuts.GROUP_GAMES to R.string.glcn_apps_group_games,
                AppShortcuts.GROUP_OTHER to R.string.glcn_apps_group_other,
            ).map { (g, title) -> title to rest.filter { it.group == g } }.filter { it.second.isNotEmpty() }
            // With a single group (no app declares a kind) there is no point in its heading.
            val headed = groups.size > 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(GAP.mpx),
                verticalArrangement = Arrangement.spacedBy(GAP.mpx),
                contentPadding = PaddingValues(start = HPAD.mpx, end = HPAD.mpx, top = 16.mpx, bottom = 48.mpx),
                modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
            ) {
                if (recentApps.isNotEmpty()) {
                    item(key = "hRecent", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.glcn_apps_recent), style = stageText(22, 800), color = StageColors.Text, modifier = Modifier.padding(top = 4.mpx))
                    }
                    itemsIndexed(recentApps, key = { _, a -> "r" + a.pkg }) { i, a ->
                        AppBanner(
                            a, width = Dp.Unspecified,
                            onClick = { AppShortcuts.open(context, a.pkg) },
                            onLongClick = { menuFor = a },
                            onFocus = { focusedTint = it },
                            modifier = (if (i == 0 && mine.isEmpty()) Modifier.focusRequester(entry) else Modifier).then(leftEdge(i == 0)),
                        )
                    }
                }
                groups.forEachIndexed { gi, (title, apps) ->
                    item(key = "h$title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(if (headed) title else R.string.glcn_apps_all), style = stageText(22, 800), color = StageColors.Text,
                            modifier = Modifier.padding(top = if (gi == 0 && recentApps.isEmpty()) 4.mpx else 14.mpx),
                        )
                    }
                    itemsIndexed(apps, key = { _, a -> "a" + a.pkg }) { i, a ->
                        AppBanner(
                            a, width = Dp.Unspecified,
                            onClick = { AppShortcuts.open(context, a.pkg) },
                            onLongClick = { menuFor = a },
                            onFocus = { focusedTint = it },
                            modifier = (if (gi == 0 && i == 0 && mine.isEmpty() && recentApps.isEmpty()) Modifier.focusRequester(entry) else Modifier)
                                .then(leftEdge(i % GRID_COLUMNS == 0)),
                        )
                    }
                }
            }
        }
        // glcn: the grid fades out at the foot instead of being cut, the hold-OK hint over it.
        val pg = tv.own.owntv.ui.theme.LocalStagePalette.current.page
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(90.mpx).background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 1f to pg.copy(alpha = 0.92f))))
        tv.own.owntv.ui.stage.StageKeyHints(
            listOf(stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options)),
            Modifier.align(Alignment.BottomEnd).padding(end = HPAD.mpx, bottom = 14.mpx), textSize = 15,
        )
    }
    menuFor?.let { a -> AppMenu(a) { menuFor = null } }
}

/**
 * glcn: one app as its TV banner — every one the same 16:9 card on a dark plate with a hairline, so the white
 * banners (Kodi, Play Store) sit framed like the rest; [checked] marks one in "Your apps".
 */
@Composable
fun AppBanner(
    app: AppShortcuts.App,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    checked: Boolean = false,
    onFocus: (Color) -> Unit = {},
) {
    val img: ImageBitmap? = remember(app.pkg) { app.banner?.let { d -> runCatching { d.toBitmap(BANNER_PX_W, BANNER_PX_H).asImageBitmap() }.getOrNull() } }
    // glcn: no banner — the app's icon on a plate of its own colour, the name under it, so every card reads alike.
    val iconImg: ImageBitmap? = remember(app.pkg) { if (img != null) null else app.icon?.let { d -> runCatching { d.toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull() } }
    val iconColour = remember(app.pkg) { if (img != null) null else app.icon?.let { d -> runCatching { dominant(d.toBitmap(12, 12)) }.getOrNull() } }
    val tint = remember(app.pkg) { app.banner?.let { d -> runCatching { Color(d.toBitmap(1, 1).getPixel(0, 0)) }.getOrNull() } ?: iconColour ?: Color.Transparent }
    var isFocused by remember { mutableStateOf(false) }
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 16.mpx,
        focusStyle = StageFocus.POSTER,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width).aspectRatio(BANNER_RATIO) else Modifier.fillMaxWidth().aspectRatio(BANNER_RATIO))
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocus(tint) },
    ) { _ ->
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(16.mpx)).background(Color(0xFF1B2024))
                .border(1.mpx, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.mpx)),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) {
                Image(img, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                val plate = iconColour ?: Color(0xFF1B2024)
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(lighten(plate, 0.12f), darken(plate, 0.28f)))))
                Column(Modifier.fillMaxSize().padding(10.mpx), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (iconImg != null) Image(iconImg, contentDescription = null, modifier = Modifier.fillMaxHeight(0.5f).aspectRatio(1f))
                    Text(app.label, style = stageText(15, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                }
            }
            // glcn: the focused app's name, always — over a banner as a strip along its foot.
            if (img != null && isFocused) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))).padding(start = 10.mpx, end = 10.mpx, top = 18.mpx, bottom = 6.mpx)) {
                    Text(app.label, style = stageText(14, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (checked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.mpx).size(30.mpx).clip(CircleShape).background(stageAccent.accent),
                    contentAlignment = Alignment.Center,
                ) { tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CHECK, stageAccent.onAccent, Modifier.size(18.mpx)) }
            }
        }
    }
}

/** glcn: hold OK on an app — add to / take off "Your apps", move it, or open its Android settings. */
@Composable
private fun AppMenu(app: AppShortcuts.App, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mine = app.pkg in AppShortcuts.chosen
    androidx.activity.compose.BackHandler { onDismiss() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { first.requestFocus() } }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        // glcn: the menu opens on a held OK; the release of that same press reached the menu and chose its first
        // entry at once (the menu vanished). OK counts here only after a press that started inside the menu.
        var armed by remember { mutableStateOf(false) }
        Box(
            Modifier.fillMaxSize().modalScrim()
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!ok) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                    !armed
                }
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(Modifier.width(560.mpx)) {
                tv.own.owntv.ui.stage.StageMenuHeader(title = app.label, subtitle = null)
                tv.own.owntv.ui.stage.StageMenuItem(
                    stringResource(if (mine) R.string.glcn_apps_remove else R.string.glcn_apps_add_one),
                    onClick = { AppShortcuts.toggle(context, app.pkg); onDismiss() },
                    icon = if (mine) OwnTVIcon.CLOSE else OwnTVIcon.ADD,
                    modifier = Modifier.focusRequester(first),
                )
                if (mine) {
                    tv.own.owntv.ui.stage.StageMenuItem(stringResource(R.string.glcn_apps_move_left), onClick = { AppShortcuts.move(context, app.pkg, -1); onDismiss() }, icon = OwnTVIcon.MOVE)
                    tv.own.owntv.ui.stage.StageMenuItem(stringResource(R.string.glcn_apps_move_right), onClick = { AppShortcuts.move(context, app.pkg, 1); onDismiss() }, icon = OwnTVIcon.MOVE)
                }
                tv.own.owntv.ui.stage.StageMenuItem(
                    stringResource(R.string.glcn_apps_info),
                    onClick = { AppShortcuts.openInfo(context, app.pkg); onDismiss() },
                    icon = OwnTVIcon.INFO,
                )
            }
        }
    }
}

/**
 * glcn: the picker behind "Add apps" on TV Home — every app as its banner on a dark panel, a ✓ on the ones added, the
 * added ones first. OK adds or takes off.
 */
@Composable
fun AppsPickerGrid(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all by produceState(emptyList<AppShortcuts.App>()) { value = withContext(Dispatchers.IO) { AppShortcuts.installed(context) } }
    // Added first (in their order), then the rest by name — fixed when the picker opens, so ticking does not reshuffle.
    val ordered = remember(all) { all.sortedBy { a -> AppShortcuts.chosen.indexOf(a.pkg).let { if (it < 0) Int.MAX_VALUE else it } } }
    androidx.activity.compose.BackHandler { onDismiss() }
    val first = remember { FocusRequester() }
    LaunchedEffect(ordered.isNotEmpty()) { if (ordered.isNotEmpty()) { androidx.compose.runtime.withFrameNanos { }; runCatching { first.requestFocus() } } }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(Color(0xF00E1113)).focusGroup()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 96.mpx, vertical = 56.mpx)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    Text(stringResource(R.string.glcn_apps_pick_title), style = stageText(40, 800), color = Color.White)
                    val n = AppShortcuts.chosen.size
                    Text(pluralStringResource(R.plurals.glcn_apps_added, n, n), style = stageText(22, 700), color = stageAccent.accent, modifier = Modifier.padding(bottom = 6.mpx))
                }
                Text(stringResource(R.string.glcn_apps_hint), style = stageText(19, 500), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 6.mpx, bottom = 22.mpx))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PICKER_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(20.mpx),
                    verticalArrangement = Arrangement.spacedBy(20.mpx),
                    contentPadding = PaddingValues(12.mpx),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(ordered, key = { _, a -> a.pkg }) { i, a ->
                        AppBanner(
                            a, width = Dp.Unspecified,
                            checked = a.pkg in AppShortcuts.chosen,
                            onClick = { AppShortcuts.toggle(context, a.pkg) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 7
private const val PICKER_COLUMNS = 5
/** Side room for the focused banner's lift and ring, and the gap between banners. */
private const val HPAD = 18
private const val GAP = 22
private const val BANNER_W = 240
private const val BANNER_RATIO = 16f / 9f
private const val BANNER_PX_W = 320
private const val BANNER_PX_H = 180
private const val ICON_PX = 128

/** glcn: the icon's main colour — the average of its solid, not-too-pale pixels — made deep enough for white text. */
private fun dominant(b: android.graphics.Bitmap): Color? {
    var r = 0L; var g = 0L; var bl = 0L; var n = 0
    for (y in 0 until b.height) for (x in 0 until b.width) {
        val p = b.getPixel(x, y)
        if (android.graphics.Color.alpha(p) < 200) continue
        val pr = android.graphics.Color.red(p); val pg = android.graphics.Color.green(p); val pb = android.graphics.Color.blue(p)
        if (pr > 235 && pg > 235 && pb > 235) continue
        r += pr; g += pg; bl += pb; n++
    }
    if (n == 0) return null
    return darken(Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt()), 0.25f)
}
private fun darken(c: Color, by: Float) = androidx.compose.ui.graphics.lerp(c, Color.Black, by)
private fun lighten(c: Color, by: Float) = androidx.compose.ui.graphics.lerp(c, Color.White, by)
