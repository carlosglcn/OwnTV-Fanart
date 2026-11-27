package tv.own.owntv.features.shell.components

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import tv.own.owntv.ui.theme.mpxSp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository.NavLength
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.Wordmark
import tv.own.owntv.ui.components.rememberAppliedIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.text.NumberFormat

/** What the Stage rail shows right now: the Navigation setting plus where focus is (see OwnTVShell). */
enum class RailState {
    /** A 7 px accent glow at the screen edge; the items stay focusable so ◀ can still reach them. */
    HIDDEN,
    /** The 84 px icon capsule: Floating at rest, or Size = Compact. */
    CAPSULE,
    /** The chosen Size over a scrim: the floating rail while it holds focus. */
    OPEN,
    /** The chosen Size without the scrim: Navigation = Docked. */
    PINNED,
}

/**
 * What the rail's "Now Playing" item shows: the docked channel's logo, or the equalizer while Audio
 * Mode is running (there is no picture to stand for then).
 */
data class NowPlayingRail(val logoUrl: String?, val audioMode: Boolean, val playing: Boolean)

/** The rail order: Search first, Sports then the Guide right after Live TV, More last. */
private val StageRailOrder = listOf(
    MainSection.SEARCH, MainSection.HOME, MainSection.LIVE_TV, MainSection.SPORTS, MainSection.EPG,
    MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS,
)

/** The rail's width with names: Normal 280 · Wide 340 · Extra wide 400; Compact is the 84 capsule. */
private val NavSize.openWidth get() = when (this) {
    NavSize.COMPACT -> 84
    NavSize.NORMAL -> 280
    NavSize.WIDE -> 340
    NavSize.EXTRA_WIDE -> 400
}

/** The space a docked rail takes from the content, from the screen edge: 28 + 84 capsule, 24 + width with names. */
fun railDockedReserve(size: NavSize) =
    if (size == NavSize.COMPACT || tv.own.owntv.glcn.RailPref.shape == tv.own.owntv.glcn.RailShape.LABEL) (28 + 84 + 16).mpx else (24 + size.openWidth + 16).mpx

/**
 * Where content starts below the top-right cluster (34 + its 68 height + a gap) on screens that have not
 * been redrawn for Stage yet. Each screen's own phase takes this away as it gets the full canvas.
 */
val StageContentTop = 110.mpx

/**
 * The Stage navigation rail: a floating glass capsule, the rail at its
 * [size] over a scrim or docked, or only the accent glow at the edge. [count] is the number beside an
 * item (Wide and up) and [detail] the line under its name (Extra wide), null for none. [length] Full
 * runs the rail the height of the screen. Entering the rail from the content always lands on the
 * selected section, and ▶ out of it returns to exactly where the content was ([contentFocusRequester]).
 */
@Composable
fun StageRail(
    state: RailState,
    size: NavSize,
    length: NavLength,
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    count: (MainSection) -> Int?,
    detail: @Composable (MainSection) -> String?,
    /** glcn: the picture for a section's focused row. */
    detailArt: @Composable (MainSection) -> String? = { null },
    profileName: String,
    profileLine: String,
    onSwitchProfile: () -> Unit,
    onPickAvatar: () -> Unit,
    /** The profile's drawn avatar (-1 = none) and its own picture (blank = none). */
    avatarId: Int = -1,
    avatarPath: String = "",
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    /** A screen's own way in, when it has one (Home returns to the exact control left from). */
    enterContent: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // glcn: how the menu is drawn (Settings › Layout › Side menu shape) — see RailShape. Label keeps it closed.
    val shape = tv.own.owntv.glcn.RailPref.shape
    val labelled = shape == tv.own.owntv.glcn.RailShape.LABEL
    val veiled = shape == tv.own.owntv.glcn.RailShape.FADE || shape == tv.own.owntv.glcn.RailShape.COLUMN
    val size = if (labelled) NavSize.COMPACT else size
    var rootAt by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var railLabel by remember { mutableStateOf<RailLabel?>(null) }
    val open = (state == RailState.OPEN || state == RailState.PINNED) && size != NavSize.COMPACT
    val counts = size >= NavSize.WIDE && shape != tv.own.owntv.glcn.RailShape.COLUMN
    val details = size == NavSize.EXTRA_WIDE && shape != tv.own.owntv.glcn.RailShape.COLUMN
    val full = length == NavLength.FULL
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    // Search, Settings and More all land on a rail item: Search has its own, Settings lives behind More.
    val focusSection = when {
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        selected == MainSection.SEARCH || selected in visibleSections -> selected
        else -> StageRailOrder.firstOrNull { it in visibleSections } ?: MainSection.MORE
    }
    // glcn: the family profile has no More (and so no Settings): the menu is the places to watch.
    val family by org.koin.compose.koinInject<tv.own.owntv.core.nav.NavVisibility>().familyMode.collectAsStateWithLifecycle(false)
    val items = StageRailOrder.filter { it == MainSection.SEARCH || it in visibleSections } + listOfNotNull(MainSection.MORE.takeUnless { family })

    // Slide in from the edge (320 ms, or a snap with animations off) when the rail leaves HIDDEN. A rail
    // that opens from the capsule already on screen widens in place instead: sliding it in from off-screen
    // made the capsule vanish and come back, a visible double take.
    val slide = remember { Animatable(0f) }
    val slideSpec: androidx.compose.animation.core.AnimationSpec<Float> = ownTvTween(320)
    val capsuleWidth = 84.mpx
    val openWidth = size.openWidth.mpx
    // The width the names are laid out at. A compact rail that widens on focus is handed Compact again the
    // moment it starts to close, and its rows measured at 84 squeezed the icons for the whole narrowing; the
    // last width with names stays until the rail is back in its capsule.
    var namesWidth by remember { mutableStateOf(maxOf(openWidth, NavSize.NORMAL.openWidth.mpx)) }
    if (openWidth > capsuleWidth) namesWidth = openWidth
    val hidden = state == RailState.HIDDEN
    val railWidth = remember { Animatable(if (open) openWidth else capsuleWidth, Dp.VectorConverter) }
    val widthSpec: androidx.compose.animation.core.AnimationSpec<Dp> = ownTvTween(320)
    var wasHidden by remember { mutableStateOf(hidden) }
    LaunchedEffect(open, hidden, openWidth) {
        val target = if (open) openWidth else capsuleWidth
        // In or out of HIDDEN the rail only slides, at its final width, exactly as before: growing while
        // it slides in would be two motions at once.
        if (hidden || wasHidden) railWidth.snapTo(target) else launch { railWidth.animateTo(target, widthSpec) }
        wasHidden = hidden
        slide.animateTo(if (hidden) -1f else 0f, slideSpec)
    }
    // Names stay laid out until the width has closed back to the capsule, so collapsing mirrors opening
    // instead of re-centring the icons in a rail that is still wide. Derived, so the rail recomposes when
    // this flips, not on every frame of the widening.
    val shown by remember(open, capsuleWidth) { derivedStateOf { open || railWidth.value > capsuleWidth } }
    // Names fade in over the second half of the widening (and out over the first half of the narrowing),
    // so a rail too narrow for them never shows them squeezed down to an ellipsis. A lambda, read in
    // each label's draw layer only.
    val namesAlpha: () -> Float = {
        ((railWidth.value - capsuleWidth) / (namesWidth - capsuleWidth).coerceAtLeast(1.mpx) * 2f - 1f).coerceIn(0f, 1f)
    }

    val page = tv.own.owntv.ui.theme.LocalStagePalette.current.page
    Box(modifier.fillMaxSize().onGloballyPositioned { rootAt = it.positionInWindow() }) {
        if (state == RailState.OPEN && open && !veiled) {
            Box(Modifier.fillMaxSize().background(StageColors.Scrim))
        }
        // glcn: Fade and Column have no block of glass — a veil that thins out into the picture, there with the names.
        if (veiled) Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().width(namesWidth * RAIL_VEIL_REACH)
                .graphicsLayer { alpha = namesAlpha() * (1f + slide.value).coerceIn(0f, 1f); if (rtl) scaleX = -1f }
                .background(Brush.horizontalGradient(0f to page.copy(alpha = 0.94f), 0.55f to page.copy(alpha = 0.8f), 1f to page.copy(alpha = 0f))),
        )
        if (state == RailState.HIDDEN) RailEdgeGlow(Modifier.align(Alignment.CenterStart))

        val edge = if (shown) 24.mpx else 28.mpx
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = edge)
                .then(if (full) Modifier.padding(vertical = 24.mpx).fillMaxHeight() else Modifier)
                // The animated width is read here, in layout, so each frame of the widening re-lays the
                // rail out without recomposing it.
                .layout { measurable, constraints ->
                    val w = railWidth.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(w, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val travel = (railWidth.value + edge).toPx()
                    translationX = slide.value * travel * (if (rtl) -1f else 1f)
                }
                // Fades as it slides out, so its shadow never lingers at the edge once hidden.
                .alpha(if (state == RailState.HIDDEN) (1f + slide.value).coerceIn(0f, 1f) else 1f)
                .onFocusChanged {
                    // Every entry from the content lands on the selected section, not on whatever item
                    // happens to line up; moves inside the rail do not re-trigger this.
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused()
                    if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
                .focusProperties {
                    onExit = {
                        val out = if (rtl) FocusDirection.Left else FocusDirection.Right
                        if (requestedFocusDirection == out && enterContent != null) {
                            // Focus cannot be moved from inside a focus change: stop this one, and let
                            // the screen place focus itself on the next frame.
                            cancelFocusChange()
                            scope.launch {
                                withFrameNanos { }
                                if (!enterContent()) runCatching { contentFocusRequester.requestFocus() }
                            }
                        } else if (requestedFocusDirection == out && runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)) {
                            cancelFocusChange()
                        }
                    }
                }
                // ▶ always leaves for the content. The open rail can cover the content's left
                // column (More's sheet), and then the D-pad search finds nothing to its right and focus
                // simply stayed in the rail.
                .onKeyEvent { e ->
                    val out = if (rtl) Key.DirectionLeft else Key.DirectionRight
                    if (e.key != out) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) scope.launch {
                        if (enterContent?.invoke() != true) runCatching { contentFocusRequester.requestFocus() }
                    }
                    true
                }
                .focusGroup()
                .then(if (veiled && shown) Modifier else Modifier.stageGlass(if (shown) 34.mpx else 42.mpx, overContent = true))
                // The open rail keeps the capsule's vertical sizes (16 padding, 58 rows, the same gaps), so
                // opening and closing only change the width: the rail never grows or shrinks in height.
                .padding(horizontal = if (shown) 16.mpx else 0.mpx, vertical = 16.mpx)
                // While it widens, the rows are laid out at the open rail's width and clipped to the
                // current one, so the icons are never squeezed in a rail still too narrow for them.
                .clipToBounds()
                .layout { measurable, constraints ->
                    val innerWidth = if (shown) (namesWidth - 32.mpx).roundToPx().coerceAtLeast(0) else null
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = innerWidth ?: constraints.minWidth, maxWidth = innerWidth ?: constraints.maxWidth),
                    )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                },
            horizontalAlignment = if (shown) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.mpx),
        ) {
            // glcn: Fade — the profile rides beside the logo, so the menu is one row shorter.
            val profileUp = shape == tv.own.owntv.glcn.RailShape.FADE && shown
            RailBrand(shown, namesAlpha, wordmark = shape != tv.own.owntv.glcn.RailShape.COLUMN) {
                if (profileUp) StageSurface(
                    onClick = onSwitchProfile, onLongClick = onPickAvatar, radius = 23.mpx,
                    modifier = Modifier.size(46.mpx), focusStyle = StageFocus.POSTER, contentAlignment = Alignment.Center,
                ) { RailAvatar(profileName.trim().take(1).uppercase().ifEmpty { "?" }, avatarId = avatarId, imagePath = avatarPath) }
            }
            if (nowPlaying != null) {
                RailItem(
                    icon = if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY,
                    label = stringResource(R.string.shell_now_playing),
                    trailing = if (nowPlaying.audioMode && counts) stringResource(R.string.shell_now_playing_audio) else null,
                    detail = null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = false,
                    accentIcon = true,
                    onClick = onNowPlaying,
                    shape = shape,
                    onLabel = if (labelled) ({ railLabel = it }) else null,
                )
                RailSeparator(shown)
            }
            items.forEach { section ->
                RailItem(
                    icon = section.stageIcon,
                    label = stringResource(section.labelRes),
                    trailing = if (counts) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(it) } else null,
                    detail = if (details) detail(section) else null,
                    detailArt = if (shown) detailArt(section) else null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = section == selected ||
                        (section == MainSection.MORE && selected == MainSection.SETTINGS),
                    onClick = { onSelect(section) },
                    modifier = (if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
                        .selectOnRest(section.opensOnRest() && section != selected) { onSelect(section) },
                    shape = shape,
                    restDetail = if (shape == tv.own.owntv.glcn.RailShape.FADE && shown) detail(section) else null,
                    labelNote = if (labelled) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(it) } else null,
                    onLabel = if (labelled) ({ railLabel = it }) else null,
                )
            }
            // Full height: the profile sits at the bottom, after the last separator.
            if (full) Spacer(Modifier.weight(1f))
            if (!profileUp) {
                RailSeparator(shown)
                RailProfile(shown, namesAlpha, profileName, profileLine.takeIf { counts }, avatarId, avatarPath, onSwitchProfile, onPickAvatar, plain = shape != tv.own.owntv.glcn.RailShape.GLASS)
            }
        }
        // glcn: Label — the focused icon's name (and its count) in a chip beside the closed menu.
        val at = railLabel
        if (labelled && hasFocus && at != null) Row(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((at.bounds.right - rootAt.x + RAIL_LABEL_GAP.mpx.toPx()).toInt(), (at.bounds.center.y - rootAt.y - RAIL_LABEL_H.mpx.toPx() / 2f).toInt()) }
                .height(RAIL_LABEL_H.mpx)
                .stageGlass(18.mpx, overContent = true)
                .padding(horizontal = 20.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(at.name, style = stageText(21, 700), color = StageColors.Text, maxLines = 1)
            if (at.note != null) Text(at.note, style = stageText(16, 500), color = StageColors.Dim, maxLines = 1)
        }
    }
}

/** glcn: what the Label menu shows beside the focused icon, and where that icon is (in the window). */
private class RailLabel(val name: String, val note: String?, val bounds: androidx.compose.ui.geometry.Rect)

private const val RAIL_VEIL_REACH = 1.7f
private const val RAIL_LABEL_GAP = 26
private const val RAIL_LABEL_H = 56
private const val RAIL_REST_MS = 380L
private val RAIL_REST_W = 112.mpx
private val RAIL_REST_H = 63.mpx

private val MainSection.stageIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SPORTS -> OwnTVIcon.SPORTS
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.MORE, MainSection.SETTINGS -> OwnTVIcon.TILES
    }

/** `.edge`: 7 × 150 at the screen edge, accent, glowing 22 px at 80% and 60 px at 35%, 90% opaque. */
@Composable
private fun RailEdgeGlow(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(7.mpx, 150.mpx)
            .alpha(0.9f)
            .drawBehind {
                val r = 7.mpx.toPx()
                drawBoxShadow(accent.copy(alpha = 0.35f), 60.mpx.toPx(), r)
                drawBoxShadow(accent.copy(alpha = 0.8f), 22.mpx.toPx(), r)
                // border-radius: 0 7 7 0 — square against the screen edge, round on the inside.
                drawRoundRect(accent, cornerRadius = CornerRadius(r))
                drawRect(accent, size = size.copy(width = size.width / 2f))
            },
    )
}

/** The mark (50), with the wordmark beside it when the rail is open (`.brandrow`, 0 10 18 padding). */
@Composable
private fun ColumnScope.RailBrand(open: Boolean, namesAlpha: () -> Float, wordmark: Boolean = true, trailing: @Composable () -> Unit = {}) {
    val icon = rememberAppliedIcon()
    if (!open) {
        BrandMark(icon, 50.mpx, Modifier.padding(bottom = 12.mpx))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 10.mpx, end = 10.mpx, bottom = 12.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(icon, 50.mpx)
        // The wordmark, 130 wide (`.brandrow`, `wordmarkC()`).
        if (wordmark) Box(Modifier.graphicsLayer { alpha = namesAlpha() }) { Wordmark(130.mpx) }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RailSeparator(open: Boolean) {
    Box(
        Modifier
            .then(if (open) Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 8.mpx) else Modifier.padding(vertical = 8.mpx).width(34.mpx))
            .height(1.mpx)
            .background(StageColors.Ink.copy(alpha = 0.12f)),
    )
}

/**
 * `.frail .it`: a 58 × 58 icon tile in the capsule, a 60 high row with its label, [detail] line under it
 * and count when open.
 * Active = accent icon on accent 14%, plus the glowing dot outside the capsule; focused = FILLED.
 */
@Composable
private fun RailItem(
    icon: OwnTVIcon,
    label: String,
    trailing: String?,
    detail: String?,
    open: Boolean,
    namesAlpha: () -> Float,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentIcon: Boolean = false,
    /** glcn: the picture of what waits there (last channel, film, series), shown on the focused row. */
    detailArt: String? = null,
    /** glcn: how the menu is drawn; [restDetail] is Fade's line beside the picture, [onLabel] / [labelNote] Label's chip. */
    shape: tv.own.owntv.glcn.RailShape = tv.own.owntv.glcn.RailShape.GLASS,
    restDetail: String? = null,
    labelNote: String? = null,
    onLabel: ((RailLabel?) -> Unit)? = null,
) {
    val a = stageAccent
    val r = 20.mpx
    val palette = tv.own.owntv.ui.theme.LocalStagePalette.current
    val plain = shape != tv.own.owntv.glcn.RailShape.GLASS
    val column = shape == tv.own.owntv.glcn.RailShape.COLUMN && open
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    StageSurface(
        onClick = onClick,
        radius = r,
        focusStyle = if (plain) StageFocus.FX else StageFocus.FILLED,
        focusDecor = !column,
        // At least 60, not exactly: with a large font size the name and its detail line need more, and a
        // fixed height cut the detail line off (#7.2).
        modifier = modifier.then(if (open) Modifier.fillMaxWidth().heightIn(min = 58.mpx) else Modifier.size(58.mpx))
            .then(if (onLabel != null) Modifier.onGloballyPositioned { bounds = it.boundsInWindow() } else Modifier),
        idle = if (active && !plain) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(r)) else Modifier,
        contentAlignment = if (open) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        if (onLabel != null) androidx.compose.runtime.LaunchedEffect(focused, bounds, label, labelNote) {
            if (focused && bounds != androidx.compose.ui.geometry.Rect.Zero) onLabel(RailLabel(label, labelNote, bounds))
        }
        if (onLabel != null) androidx.compose.runtime.DisposableEffect(focused) { onDispose { if (focused) onLabel(null) } }
        // Fade: what waits there comes once the focus rests on the row, not while it passes by.
        var rested by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(focused) {
            if (focused) { kotlinx.coroutines.delay(RAIL_REST_MS); rested = true } else rested = false
        }
        val nameTint = when {
            column -> if (focused) a.accent else if (active) palette.text else StageColors.NavText
            plain -> if (focused) palette.fxText else if (active) palette.text else StageColors.NavText
            else -> if (focused) a.onAccent else if (active) a.accent else StageColors.Text
        }
        if (plain && active && !(column && focused)) RailActiveBar(Modifier.align(Alignment.CenterStart).offset(x = (if (open) -10 else -8).mpx))
        if (column) {
            Row(
                Modifier.padding(horizontal = 18.mpx).graphicsLayer { alpha = namesAlpha() },
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = stageText(29, if (active || focused) 700 else 500), color = nameTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (focused) Box(Modifier.size(34.mpx, 4.mpx).background(palette.text.copy(alpha = 0.55f), RoundedCornerShape(2.mpx)))
            }
            return@StageSurface
        }
        val iconTint = when {
            plain -> if (accentIcon && !focused) a.accent else nameTint
            focused -> a.onAccent
            active || accentIcon -> a.accent
            else -> StageColors.NavText
        }
        // glcn: the icon gives a little springy hop as the focus reaches it (as the cards pop).
        val hop by androidx.compose.animation.core.animateFloatAsState(
            if (focused && tv.own.owntv.ui.theme.animationsOn) RAIL_ICON_POP else 1f,
            androidx.compose.animation.core.spring(dampingRatio = RAIL_ICON_DAMPING, stiffness = RAIL_ICON_STIFFNESS),
            label = "railIconHop",
        )
        val iconHop = Modifier.graphicsLayer { scaleX = hop; scaleY = hop }
        if (!open) {
            OwnTVIcon(icon, iconTint, Modifier.size(27.mpx).then(iconHop))
            if (active && !focused && !plain) RailActiveDot(Modifier.align(Alignment.CenterEnd).offset(x = 9.mpx))
            return@StageSurface
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, iconTint, Modifier.size(26.mpx).then(iconHop))
            Column(Modifier.weight(1f).graphicsLayer { alpha = namesAlpha() }) {
                Text(
                    label,
                    style = stageText(21, if (!plain) 600 else if (active || focused) 700 else 500),
                    color = nameTint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (shape == tv.own.owntv.glcn.RailShape.FADE && focused && rested && detailArt != null) Row(
                    Modifier.padding(top = 10.mpx, bottom = 8.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coil3.compose.AsyncImage(
                        detailArt, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(RAIL_REST_W, RAIL_REST_H).clip(RoundedCornerShape(8.mpx)),
                    )
                    if (restDetail != null) Text(restDetail, style = stageText(15, 500), color = palette.fxText.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (detail != null) {
                    // `.dt`: 14/500 dim, 2 px under the name; on the focused (filled) row on-accent at 80%.
                    Text(
                        detail,
                        style = stageText(14, 500),
                        color = if (focused && !plain) a.onAccent.copy(alpha = 0.8f) else StageColors.Dim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.mpx),
                    )
                }
            }
            // glcn: on the focused row, the picture of what waits there takes the count's place.
            if (focused && detailArt != null && !plain) {
                coil3.compose.AsyncImage(
                    detailArt, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(RAIL_ART_W, RAIL_ART_H).clip(RoundedCornerShape(8.mpx)).graphicsLayer { alpha = namesAlpha() },
                )
            } else if (trailing != null) {
                Text(trailing, style = stageText(16, 600), color = if (focused && !plain) a.onAccent else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = namesAlpha() })
            }
        }
    }
}

private val RAIL_ART_W = 72.mpx
private val RAIL_ART_H = 40.mpx
private const val RAIL_ICON_POP = 1.16f
private const val RAIL_ICON_DAMPING = 0.45f
private const val RAIL_ICON_STIFFNESS = 520f

/** glcn: the open section's mark in the newer shapes — a short bar in the accent's gradient at the row's start. */
@Composable
private fun RailActiveBar(modifier: Modifier) {
    val accent = stageAccent.accent
    val second = tv.own.owntv.glcn.EmberFocus.second?.let { tv.own.owntv.glcn.EmberFocus.partner(accent, it) } ?: accent
    Box(modifier.size(5.mpx, 30.mpx).background(Brush.verticalGradient(listOf(accent, second)), RoundedCornerShape(3.mpx)))
}

/** `.it.on::after`: 6 px, 9 px outside the capsule's item, glowing 10 px. */
@Composable
private fun RailActiveDot(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(6.mpx)
            .drawBehind {
                drawBoxShadow(accent, 10.mpx.toPx(), size.minDimension / 2f)
                drawCircle(accent)
            },
    )
}

/**
 * The profile: the 46 px avatar alone in the capsule, avatar + name when open, with "All playlists ·
 * switch ›" under it from Size Wide up ([line]). OK switches profile; a long press changes the avatar.
 */
@Composable
private fun RailProfile(open: Boolean, namesAlpha: () -> Float, name: String, line: String?, avatarId: Int, avatarPath: String, onSwitchProfile: () -> Unit, onPickAvatar: () -> Unit, plain: Boolean = false) {
    val a = stageAccent
    val fx = tv.own.owntv.ui.theme.LocalStagePalette.current.fxText
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    StageSurface(
        onClick = onSwitchProfile,
        onLongClick = onPickAvatar,
        radius = if (open) 20.mpx else 23.mpx,
        modifier = if (open) Modifier.padding(top = 4.mpx).fillMaxWidth().heightIn(min = 46.mpx) else Modifier.padding(top = 4.mpx).size(46.mpx),
        // Alone in the capsule the avatar covers a fill, so it takes the ring instead.
        focusStyle = if (!open) StageFocus.POSTER else if (plain) StageFocus.FX else StageFocus.FILLED,
        contentAlignment = if (open) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        if (!open) {
            RailAvatar(initial, avatarId = avatarId, imagePath = avatarPath)
            return@StageSurface
        }
        Row(
            Modifier.padding(horizontal = 12.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailAvatar(initial, avatarId = avatarId, imagePath = avatarPath)
            Column(Modifier.graphicsLayer { alpha = namesAlpha() }) {
                Text(name, style = stageText(19, 700), color = if (focused) (if (plain) fx else a.onAccent) else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) {
                    Text("$line ›", style = stageText(15, 400), color = if (focused) (if (plain) fx else a.onAccent) else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * `.frail .av`: the profile's avatar or own picture in a circle; with neither, its initial on the orange
 * gradient, 20/800 in #2A0D12. More's profile row draws it 58 / 24.
 */
@Composable
internal fun RailAvatar(initial: String, size: Int = 46, textSize: Int = 20, avatarId: Int = -1, imagePath: String = "") {
    if (avatarId != -1 || imagePath.isNotBlank()) {
        tv.own.owntv.ui.components.OwnTVAvatar(avatarId = avatarId, imagePath = imagePath, modifier = Modifier.size(size.mpx).clip(CircleShape))
        return
    }
    Box(
        Modifier
            .size(size.mpx)
            .background(Brush.linearGradient(listOf(Color(0xFFFFB35C), Color(0xFFFF5F7A))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = stageText(textSize, 800), color = Color(0xFF2A0D12), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * glcn: Navigation = Top bar. The rail's sections as one glass bar at the top left, level with the clock
 * cluster (34 down, 68 high), so the content keeps its whole width. Names as text pills; Search and More
 * as icons at the ends. Entering it lands on the selected section; ▼ returns to the content, exactly where
 * it was left, the way ▶ leaves the rail.
 */
@Composable
fun StageTopBar(
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    profileName: String,
    onSwitchProfile: () -> Unit,
    onPickAvatar: () -> Unit,
    avatarId: Int = -1,
    avatarPath: String = "",
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    enterContent: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: where the bar ends on screen, so the status pills keep clear of it. */
    onBarBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    /** glcn: the bar's size (Compact = icons only, the open section keeps its name) and its Floating state. */
    size: NavSize = NavSize.NORMAL,
    hidden: Boolean = false,
    /** glcn: where the free width ends on the right (the status pills' left edge, in the window), for a centred bar. */
    freeRight: Float? = null,
    /** glcn: focus has settled in the content — the bar folds to the open section until it is reached again. */
    folded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    val fold = folded && !hasFocus
    // glcn: how the bar is drawn (Settings › Layout › Top bar shape) — see TopBarShape.
    val shape = tv.own.owntv.glcn.TopBarPref.shape
    val capsule = shape == tv.own.owntv.glcn.TopBarShape.CAPSULE
    var rootW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var barW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val compact = size == NavSize.COMPACT
    // 1.0 at Normal; Wide and Extra wide grow the bar and its type.
    val k = when (size) { NavSize.WIDE -> 1.14f; NavSize.EXTRA_WIDE -> 1.28f; else -> 1f }
    fun sc(v: Int) = (v * k).toInt()
    // Floating: slides up and fades once idle, but stays where focus can reach it with ▲.
    val shown by androidx.compose.animation.core.animateFloatAsState(
        if (hidden && !hasFocus) 0f else 1f, label = "topBarShown",
    )
    val focusSection = when {
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        selected == MainSection.SEARCH || selected in visibleSections -> selected
        else -> StageRailOrder.firstOrNull { it in visibleSections } ?: MainSection.MORE
    }
    val named = StageRailOrder.filter { it != MainSection.SEARCH && it in visibleSections }
    val a = stageAccent
    val toContent = {
        scope.launch {
            withFrameNanos { }
            if (enterContent?.invoke() != true) runCatching { contentFocusRequester.requestFocus() }
        }
        Unit
    }
    val palette = tv.own.owntv.ui.theme.LocalStagePalette.current
    // The island's glass comes with the focus; tabs and pills have none of their own.
    val glassIn by androidx.compose.animation.core.animateFloatAsState(
        when (shape) {
            tv.own.owntv.glcn.TopBarShape.CAPSULE, tv.own.owntv.glcn.TopBarShape.ICONS -> 1f
            tv.own.owntv.glcn.TopBarShape.ISLAND -> if (hasFocus) 1f else 0f
            else -> 0f
        },
        label = "topBarGlass",
    )
    val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.mpx.toPx() }
    val topPx = with(androidx.compose.ui.platform.LocalDensity.current) { 34.mpx.toPx() }
    val logoRoomPx = with(androidx.compose.ui.platform.LocalDensity.current) { (sc(44) + 20).mpx.toPx() }
    Box(modifier.fillMaxSize().onSizeChanged { rootW = it.width }) {
        // Tabs: bare names need something behind them over a bright picture — a veil that fades down from the top.
        if (shape == tv.own.owntv.glcn.TopBarShape.TABS) Box(
            Modifier.fillMaxWidth().height(TOP_BAR_VEIL.mpx).graphicsLayer { alpha = shown }
                .background(Brush.verticalGradient(listOf(palette.page.copy(alpha = 0.74f), palette.page.copy(alpha = 0f)))),
        )
        if (shape.centred) BrandMark(
            rememberAppliedIcon(), sc(44).mpx,
            Modifier.align(Alignment.TopStart).offset(x = 40.mpx, y = (34 + (sc(68) - sc(44)) / 2).mpx).graphicsLayer { alpha = shown },
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val x = if (!shape.centred) edgePx else {
                        val left = edgePx + logoRoomPx
                        val right = freeRight ?: (rootW - edgePx)
                        ((left + right - barW) / 2f).coerceAtLeast(left)
                    }
                    androidx.compose.ui.unit.IntOffset(x.toInt(), topPx.toInt())
                }
                .onSizeChanged { barW = it.width }
                .graphicsLayer { alpha = shown; translationY = -(1f - shown) * 60.dp.toPx() }
                .height(sc(68).mpx)
                .onGloballyPositioned { onBarBounds(it.boundsInWindow()) }
                .onFocusChanged {
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused()
                    if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
                .focusProperties {
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Down) {
                            cancelFocusChange()
                            toContent()
                        }
                    }
                }
                .onKeyEvent { e ->
                    if (e.key != Key.DirectionDown) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) toContent()
                    true
                }
                .focusGroup(),
        ) {
            if (glassIn > 0.01f) Box(Modifier.matchParentSize().graphicsLayer { alpha = glassIn }.stageGlass(34.mpx, overContent = true))
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = if (shape == tv.own.owntv.glcn.TopBarShape.TABS || shape == tv.own.owntv.glcn.TopBarShape.PILLS) 0.mpx else 10.mpx),
                horizontalArrangement = Arrangement.spacedBy(4.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // glcn: one focus pill for the whole bar, gliding (and stretching like a drop) from entry to entry.
            tv.own.owntv.glcn.SlideFocusHost(stiffness = TOP_BAR_GLIDE, stretch = true) {
                if (!capsule) Row(
                    horizontalArrangement = Arrangement.spacedBy((if (shape == tv.own.owntv.glcn.TopBarShape.PILLS) 10 else if (shape == tv.own.owntv.glcn.TopBarShape.TABS) 8 else 4).mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tabs = shape == tv.own.owntv.glcn.TopBarShape.TABS
                    val lit = hasFocus || shape != tv.own.owntv.glcn.TopBarShape.ISLAND
                    if (!shape.centred) BrandMark(rememberAppliedIcon(), sc(44).mpx, Modifier.padding(end = 14.mpx))
                    val search: @Composable () -> Unit = {
                        TopEntry(shape, null, OwnTVIcon.SEARCH, selected == MainSection.SEARCH, true, k, lit, { onSelect(MainSection.SEARCH) },
                            if (focusSection == MainSection.SEARCH) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
                    }
                    val searchKept = !fold || focusSection == MainSection.SEARCH
                    if (!tabs) TopFold(searchKept) { search() }
                    if (nowPlaying != null) TopFold(!fold) { TopEntry(shape, null, if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY, false, true, k, lit, onNowPlaying, accentIcon = true) }
                    named.forEach { section ->
                        val active = section == selected
                        val focusMod = (if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
                            .selectOnRest(section.opensOnRest() && !active) { onSelect(section) }
                        TopFold(!fold || active || section == focusSection) {
                            TopEntry(shape, stringResource(section.labelRes), section.stageIcon, active, compact, k, lit, { onSelect(section) }, focusMod)
                        }
                    }
                    if (tabs) TopFold(searchKept) { search() }
                    val family by org.koin.compose.koinInject<tv.own.owntv.core.nav.NavVisibility>().familyMode.collectAsStateWithLifecycle(false)
                    if (!family) TopFold(!fold || focusSection == MainSection.MORE) {
                        TopEntry(shape, null, OwnTVIcon.TILES, selected == MainSection.MORE || selected == MainSection.SETTINGS, true, k, lit, { onSelect(MainSection.MORE) },
                            if (focusSection == MainSection.MORE) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
                    }
                    TopFold(!fold) {
                        StageSurface(
                            onClick = onSwitchProfile,
                            onLongClick = onPickAvatar,
                            radius = sc(23).mpx,
                            modifier = Modifier.padding(start = 6.mpx).size(sc(46).mpx),
                            focusStyle = StageFocus.POSTER,
                            contentAlignment = Alignment.Center,
                        ) { RailAvatar(profileName.trim().take(1).uppercase().ifEmpty { "?" }, size = sc(46), avatarId = avatarId, imagePath = avatarPath) }
                    }
                } else
                Row(horizontalArrangement = Arrangement.spacedBy(4.mpx), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(rememberAppliedIcon(), sc(44).mpx, Modifier.padding(start = 6.mpx, end = 10.mpx))
                TopFold(!fold || focusSection == MainSection.SEARCH) {
                    TopIcon(OwnTVIcon.SEARCH, selected == MainSection.SEARCH, { onSelect(MainSection.SEARCH) },
                        if (focusSection == MainSection.SEARCH) Modifier.focusRequester(selectedItemFocusRequester) else Modifier, k = k)
                }
                if (nowPlaying != null) TopFold(!fold) { TopIcon(if (nowPlaying.audioMode) OwnTVIcon.EQ else OwnTVIcon.PLAY, false, onNowPlaying, accentIcon = true, k = k) }
                named.forEach { section ->
                    val active = section == selected
                    val focusMod = (if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier)
                        .selectOnRest(section.opensOnRest() && !active) { onSelect(section) }
                    TopFold(!fold || active || section == focusSection) {
                    if (compact && !active) {
                        // Compact: the section is its icon; only the open one shows its name.
                        TopIcon(section.stageIcon, false, { onSelect(section) }, focusMod, k = k)
                    } else {
                        StageSurface(
                            onClick = { onSelect(section) },
                            radius = sc(24).mpx,
                            modifier = Modifier.height(sc(50).mpx).then(focusMod),
                            idle = if (active) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(sc(24).mpx)) else Modifier,
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            val tint = when { focused -> a.onAccent; active -> a.accent; else -> StageColors.NavText }
                            Row(
                                Modifier.padding(horizontal = sc(if (compact) 14 else 18).mpx),
                                horizontalArrangement = Arrangement.spacedBy(sc(8).mpx),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (compact) OwnTVIcon(section.stageIcon, tint, Modifier.size(sc(25).mpx))
                                Text(
                                    stringResource(section.labelRes),
                                    style = stageText(sc(19), if (active) 700 else 600),
                                    color = tint,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    }
                }
                val family by org.koin.compose.koinInject<tv.own.owntv.core.nav.NavVisibility>().familyMode.collectAsStateWithLifecycle(false)
                if (!family) TopFold(!fold || focusSection == MainSection.MORE) {
                    TopIcon(OwnTVIcon.TILES, selected == MainSection.MORE || selected == MainSection.SETTINGS, { onSelect(MainSection.MORE) },
                        if (focusSection == MainSection.MORE) Modifier.focusRequester(selectedItemFocusRequester) else Modifier, k = k)
                }
                TopFold(!fold) {
                    StageSurface(
                        onClick = onSwitchProfile,
                        onLongClick = onPickAvatar,
                        radius = sc(23).mpx,
                        modifier = Modifier.padding(start = 4.mpx).size(sc(46).mpx),
                        focusStyle = StageFocus.POSTER,
                        contentAlignment = Alignment.Center,
                    ) { RailAvatar(profileName.trim().take(1).uppercase().ifEmpty { "?" }, size = sc(46), avatarId = avatarId, imagePath = avatarPath) }
                }
                }
            }
            }
        }
    }
}

/**
 * glcn: one entry of the top bar in the newer shapes (see TopBarShape). [label] null is an icon of the bar (Search,
 * More, what plays); [lit] false is the island at rest, bare over the picture, where the names carry a soft shadow.
 */
@Composable
private fun TopEntry(
    shape: tv.own.owntv.glcn.TopBarShape,
    label: String?,
    icon: OwnTVIcon,
    active: Boolean,
    compact: Boolean,
    k: Float,
    lit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentIcon: Boolean = false,
) {
    fun sc(v: Int) = (v * k).toInt()
    val a = stageAccent
    val palette = tv.own.owntv.ui.theme.LocalStagePalette.current
    val second = tv.own.owntv.glcn.EmberFocus.second?.let { tv.own.owntv.glcn.EmberFocus.partner(a.accent, it) } ?: a.accent
    val tabs = shape == tv.own.owntv.glcn.TopBarShape.TABS
    val pills = shape == tv.own.owntv.glcn.TopBarShape.PILLS
    val icons = shape == tv.own.owntv.glcn.TopBarShape.ICONS
    val h = sc(if (tabs) 56 else if (pills) 54 else 52)
    val r = if (tabs) 10 else h / 2
    val chip = if (pills) Modifier.stageGlass(r.mpx, overContent = true) else Modifier
    val idle = when {
        pills && active -> Modifier.background(palette.text.copy(alpha = 0.92f), RoundedCornerShape(r.mpx))
        pills -> chip
        // glcn: the open section stands out in the theme's colours — a pill filled with the accent's gradient.
        (icons || shape == tv.own.owntv.glcn.TopBarShape.CAPSULE) && active ->
            Modifier.background(Brush.horizontalGradient(tv.own.owntv.glcn.EmberFocus.sweepOf(a.accent, second)), RoundedCornerShape(r.mpx))
        else -> Modifier
    }
    StageSurface(
        onClick = onClick,
        radius = r.mpx,
        modifier = modifier.height(h.mpx),
        focusStyle = if (pills) StageFocus.FILLED else StageFocus.FX,
        idle = idle,
        focusDecor = !tabs,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val tint = when {
            tabs -> if (focused) a.accent else if (active) palette.text else StageColors.NavText
            pills -> if (focused) a.onAccent else if (active) palette.page else palette.text
            icons -> if (focused) palette.fxText else if (active) a.onAccent else if (accentIcon) a.accent else StageColors.NavText
            shape == tv.own.owntv.glcn.TopBarShape.CAPSULE && active && !focused -> a.onAccent
            else -> if (focused) palette.fxText else if (active) palette.text else if (accentIcon) a.accent else StageColors.NavText
        }
        // Which parts show: the island and the pills name every section and give the open one its icon; the icons
        // name only the open and the focused one; the tabs are names alone.
        val named = label != null && when {
            icons -> active || focused
            compact -> active
            else -> true
        }
        val drawn = label == null || when {
            tabs -> compact && !active
            icons -> true
            else -> active || compact
        }
        // The open section's line (and the tabs' focus): along the foot, in the accent's gradient.
        val line = when {
            label == null && !(tabs && focused) -> null
            tabs && active -> Brush.horizontalGradient(tv.own.owntv.glcn.EmberFocus.sweepOf(a.accent, second))
            tabs && focused -> androidx.compose.ui.graphics.SolidColor(palette.text.copy(alpha = 0.55f))
            shape == tv.own.owntv.glcn.TopBarShape.ISLAND && active && !focused -> Brush.horizontalGradient(tv.own.owntv.glcn.EmberFocus.sweepOf(a.accent, second))
            else -> null
        }
        Row(
            Modifier
                // glcn: an entry that gains or loses its name (the focused one in Icons, the open one when compact) and
                // the bolder weight of the focused name changed its width in one frame, and the whole bar jumped with
                // it. The width now glides, the name uncovered as the entry opens; the neighbours follow along.
                .then(
                    if (tv.own.owntv.ui.theme.animationsOn && !tv.own.owntv.glcn.LowRam.on) Modifier.animateContentSize(
                        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = TOP_ENTRY_WIDTH_STIFFNESS),
                    ) else Modifier,
                )
                .padding(horizontal = sc(if (!named) 14 else if (tabs) 12 else 18).mpx)
                .fillMaxHeight()
                .drawBehind {
                    if (line != null) {
                        val th = TOP_BAR_LINE.mpx.toPx()
                        drawRoundRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - th - TOP_BAR_LINE_LIFT.mpx.toPx()), size = androidx.compose.ui.geometry.Size(size.width, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(th / 2))
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(sc(9).mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (drawn) OwnTVIcon(icon, tint, Modifier.size(sc(if (tabs) 28 else 25).mpx))
            if (named && label != null) {
                val base = if (tabs) stageText(sc(25), if (active || focused) 700 else 500)
                else if (shape == tv.own.owntv.glcn.TopBarShape.ISLAND) stageText(sc(18), if (active || focused) 700 else 600, letterSpacing = 0.9.mpxSp)
                else stageText(sc(20), if (active || focused) 700 else 600)
                // Bare over the picture (tabs, the island at rest): a soft shadow keeps the names readable.
                val bare = (tabs || !lit) && !palette.isLight
                Text(
                    if (shape == tv.own.owntv.glcn.TopBarShape.ISLAND) label.uppercase() else label,
                    style = if (bare) base.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = TOP_BAR_SHADOW)) else base,
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

/** glcn: how briskly a top-bar entry's width follows its name coming and going. */
private const val TOP_ENTRY_WIDTH_STIFFNESS = 520f
private const val REST_OPEN_MS = 420L

/**
 * glcn: one part of the top bar that steps aside while the bar is folded: it narrows and fades, so the bar draws in
 * round the open section like iOS's tab bar does on scroll, and opens out again the same way.
 */
@Composable
private fun TopFold(shown: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = shown,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(TOP_FOLD_ANIM_MS)) +
            androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.tween(TOP_FOLD_ANIM_MS), expandFrom = Alignment.Start, clip = false),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(TOP_FOLD_ANIM_MS)) +
            androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.tween(TOP_FOLD_ANIM_MS), shrinkTowards = Alignment.Start, clip = false),
    ) { content() }
}

private const val TOP_FOLD_ANIM_MS = 320
private const val TOP_BAR_VEIL = 190
private const val TOP_BAR_LINE = 4
private const val TOP_BAR_LINE_LIFT = 2
private const val TOP_BAR_SHADOW = 10f

@Composable
private fun TopIcon(icon: OwnTVIcon, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accentIcon: Boolean = false, k: Float = 1f) {
    val a = stageAccent
    val box = (50 * k).toInt()
    StageSurface(
        onClick = onClick,
        radius = (box / 2).mpx,
        modifier = modifier.size(box.mpx),
        idle = if (active) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape((box / 2).mpx)) else Modifier,
        contentAlignment = Alignment.Center,
    ) { focused ->
        OwnTVIcon(icon, when { focused -> a.onAccent; active || accentIcon -> a.accent; else -> StageColors.NavText }, Modifier.size((25 * k).toInt().mpx))
    }
}

/** glcn: sections that open by resting on them in the menu; Search and the More hub still wait for OK. */
private fun MainSection.opensOnRest() = this != MainSection.SEARCH && this != MainSection.MORE && this != MainSection.SETTINGS

/**
 * glcn: resting the cursor on a menu entry opens it, no OK needed. The short wait keeps a cursor passing
 * over entries from opening each one in turn.
 */
@Composable
private fun Modifier.selectOnRest(enabled: Boolean, onRest: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(onRest)
    androidx.compose.runtime.LaunchedEffect(focused, enabled) {
        if (focused && enabled) {
            kotlinx.coroutines.delay(tv.own.owntv.glcn.PacePref.ms(REST_OPEN_MS))
            latest()
        }
    }
    return onFocusChanged { focused = it.hasFocus }
}

/** glcn: how briskly the top bar's focus pill glides (spring stiffness). */
private const val TOP_BAR_GLIDE = 420f
