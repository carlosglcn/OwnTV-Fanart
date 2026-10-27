package tv.own.owntv.features.live

import tv.own.owntv.ui.theme.stageTone
import tv.own.owntv.ui.theme.stageShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.ProviderName
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.home.timeLeftText
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.shownColor
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StageLiveBadge
import tv.own.owntv.ui.stage.StagePlaylistMark
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageSheetItem
import tv.own.owntv.ui.stage.StageSpecChip
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.stage.stageSelectedBar
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/*
 * Live TV in the Stage design: the header, the channel rows, the stage on the right,
 * and the category list as a sheet (Stage layout) or a column (Separate panels). LiveScreen owns the
 * state and the focus plumbing; these only draw.
 */

private val Tabular = "tnum"

/** "Live TV" 46/800, then the crumb: "‹ **Sky Cinema** · 9 channels" (no ‹ when the column is on screen). */
@Composable
internal fun LiveHeader(category: String, count: Int, showChevron: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.common_nav_live_tv), style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.padding(bottom = 3.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showChevron) OwnTVIcon(OwnTVIcon.CHEVRON_LEFT, StageColors.Muted, Modifier.size(20.mpx))
            Text(
                category, style = stageText(20, 700), color = stageAccent.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "· " + pluralStringResource(R.plurals.content_live_channel_count, count, count),
                style = stageText(20, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `.chrow`: 84 high, 20 padding, 18 gap — number (50, right-aligned, 20/600 dim), white logo plate
 * 76×54, name 22/700, "programme · time left" 17 muted (the time part at 60%), a 300×4 progress line;
 * then the provider tags, ↺ for catch-up, ♥ when a favourite, and the playlist mark. Focused = FX.
 * [trailing] replaces that end group (the player's History sheet shows when each was watched).
 */
@Composable
internal fun LiveStageRow(
    channel: ChannelEntity,
    name: ProviderName,
    now: XtEpgEntry?,
    nowTitle: String?,
    isFavorite: Boolean,
    showNumber: Boolean,
    mark: PlaylistMark?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    StageRow(
        onClick = onClick,
        onLongClick = onLongClick,
        height = 96.mpx,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocus() },
    ) { focused ->
        if (showNumber) {
            Text(
                channel.number?.toString().orEmpty(),
                style = stageText(20, 600).copy(textAlign = TextAlign.End, fontFeatureSettings = Tabular),
                color = if (focused) stageTone(Color(0xFFE6F2EE), StageColors.FxLine) else StageColors.Dim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(50.mpx),
            )
        }
        // glcn: bigger logos — on a 75" screen the old 76×54 read as dots.
        LivePlate(channel.displayLogoUrl, 104.mpx, 74.mpx)
        Column(Modifier.weight(1f)) {
            Text(name.name, style = stageText(22, 700), color = if (focused) StageColors.FxText else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val title = now?.title ?: nowTitle
            if (title != null) {
                val left = now?.let { timeLeftText(minutesLeft(it)) }
                Spacer(Modifier.height(2.mpx))
                Text(
                    buildAnnotatedString {
                        append(title)
                        if (left != null) withStyle(SpanStyle(color = (if (focused) StageColors.FxLine else StageColors.Muted).copy(alpha = 0.6f))) { append(" · $left") }
                    },
                    style = stageText(17, 400), color = if (focused) StageColors.FxLine else StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (now != null) {
                Spacer(Modifier.height(8.mpx))
                StageProgress(progressOf(now), Modifier.width(300.mpx), flat = true)
            }
        }
        if (trailing != null) trailing()
        else Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            name.tags.forEach { StageTag(it) }
            if (channel.catchup) OwnTVIcon(OwnTVIcon.REWIND, StageColors.Dim, Modifier.size(22.mpx))
            if (isFavorite) OwnTVIcon(OwnTVIcon.FAVORITE, stageAccent.accent, Modifier.size(22.mpx), filled = true)
            mark?.let { StagePlaylistMark(it) }
        }
    }
}

/**
 * `.plate`: the logo, radius 12 (the guide's 10). Transparent logos keep their transparency; only one
 * that would be unreadable on the dark UI gets a plate ([ChannelLogoTile]). A white plate under every
 * logo made white-ink logos (Sky) vanish.
 */
@Composable
internal fun LivePlate(
    logoUrl: String?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp = StageRadii.Plate,
) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(radius))) {
        ChannelLogoTile(logoUrl = logoUrl, modifier = Modifier.fillMaxSize(), fill = StageColors.ControlFill) {
            OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.align(Alignment.Center).size(height * 0.5f))
        }
    }
}

internal fun minutesLeft(p: XtEpgEntry): Long = ((p.stopMs - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000

internal fun progressOf(p: XtEpgEntry): Float {
    val span = (p.stopMs - p.startMs).coerceAtLeast(1)
    return ((System.currentTimeMillis() - p.startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** "18:45 – 20:30 · Horror · 2026 · FSK 16": the time range, then only the details the feed carries. */
@Composable
internal fun detailsLine(now: XtEpgEntry, details: EpgDetails?): String {
    val formatTime = rememberSystemTimeFormatter()
    val length = details?.lengthMin?.let { stringResource(R.string.player_duration_minutes, it) }
    return (
        listOf(stringResource(R.string.content_live_time_range_plain, formatTime(now.startMs), formatTime(now.stopMs))) +
            details?.categoryList.orEmpty() +
            listOfNotNull(details?.year?.toString(), details?.rating, length)
        ).joinToString(" · ")
}

/**
 * The stage (`cLiveStage`): the preview video (16:9, radius 28) with the LIVE badge, the stream facts
 * and the channel in the lower corner; below it the programme title 42/800, the details line, the
 * progress and time left, the synopsis, and what is on next (accent time) and later. ▶ from the list
 * enters next / later ([scheduleFocus]); OK on one opens it through [onOpenProgramme].
 */
@Composable
internal fun LiveStagePane(
    channel: ChannelEntity?,
    channelName: String?,
    nowNext: EpgNowNext?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    scheduleFocus: FocusRequester,
    onOpenProgramme: (XtEpgEntry) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    val streamChips by previewEngine.streamChips.collectAsStateWithLifecycle()
    val playing = showVideo && previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val loading = showVideo && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING
    val videoR = 28.mpx
    val videoShadow = tv.own.owntv.ui.theme.stageShadow(0.55f)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .drawBehind {
                    drawBoxShadow(videoShadow, 80.mpx.toPx(), videoR.toPx(), dy = 30.mpx.toPx())
                }
                .clip(RoundedCornerShape(videoR))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null && !playing) {
                // glcn: the channel's logo, large and soft toward the right — you can tell which channel it is
                // without it competing with the programme.
                ChannelLogoTile(
                    logoUrl = channel.displayLogoUrl,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.mpx).fillMaxWidth(0.62f).fillMaxHeight(0.62f).graphicsLayer { alpha = 0.34f },
                    fill = Color.Transparent,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(56.mpx)) }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Transparent)))
            }
            // glcn: until the video plays the frame holds the channel's logo alone — the programme's picture is already the screen's backdrop.
            if (playing) tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
            if (loading) OwnTVSpinner(sizeDp = 28)
            // `.shade`: black 72% at the bottom, clear by 42% up — a video-sized gradient, one pass.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.mpx.toPx(), videoR.toPx()) })
            if (channel != null) {
                Box(Modifier.align(Alignment.TopStart).padding(start = 24.mpx, top = 22.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
                if (playing && streamChips.isNotEmpty()) {
                    Row(Modifier.align(Alignment.TopEnd).padding(end = 22.mpx, top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                        // Three facts — quality, frame rate, sound: the
                        // engine's aspect ratio ("16:9") and declared bitrate ("4.2 Mbps") are left out.
                        streamChips.filterNot { it.contains(':') || it.endsWith("Mbps") }.forEach { StageSpecChip(it) }
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 26.mpx, bottom = 22.mpx),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    channel.number?.let { Text(it.toString(), style = stageText(30, 800), color = stageAccent.accent, maxLines = 1) }
                    Text(channelName.orEmpty(), style = stageText(24, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (singleSessionBlocked && !playing) {
                Text(
                    stringResource(R.string.content_preview_single_stream),
                    style = stageText(15, 700), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.mpx)).padding(horizontal = 12.mpx, vertical = 6.mpx),
                )
            }
        }
        val now = nowNext?.now
        Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 4.mpx, end = 4.mpx, top = 34.mpx)) {
        Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            if (now != null) {
                Text(now.title, style = stageText(42, 800, (-0.5).mpxSp), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        detailsLine(now, nowNext.nowDetails).takeIf { it.isNotBlank() },
                        tv.own.owntv.ui.components.rememberTmdbRating(now.title, nowNext.nowDetails?.categories),
                    ).joinToString("  ·  "),
                    style = stageText(19, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 16.mpx),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    StageProgress(progressOf(now), Modifier.weight(1f))
                    Text(timeLeftText(minutesLeft(now)), style = stageText(17, 400).copy(fontFeatureSettings = Tabular), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }
        // glcn: the synopsis runs the full width.
        val resolver: tv.own.owntv.core.metadata.ProgrammeArtResolver = org.koin.compose.koinInject()
        val lentOverview by androidx.compose.runtime.produceState<String?>(null, now?.title) {
            value = null
            val n = now ?: return@produceState
            if (!n.description.isNullOrBlank()) return@produceState
            kotlinx.coroutines.delay(300)
            value = runCatching { resolver.detailsFor(n.title, null) }.getOrNull()?.overview
        }
        (now?.description?.takeIf { it.isNotBlank() } ?: lentOverview)?.let {
            tv.own.owntv.features.shell.components.DriftingSynopsis(
                it,
                modifier = Modifier.padding(top = 14.mpx),
                style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp),
                color = stageTone(Color(0xFFCCD6D2), StageColors.Body),
            )
        }
        val next = nowNext?.next
        // glcn: what follows, several — the next one and the two after it.
        val later = nowNext?.upcoming.orEmpty().take(2)
        if (next != null) {
            Column(
                Modifier
                    .padding(top = 14.mpx)
                    .offset(x = (-12).mpx)
                    .focusGroup(),
            ) {
                // ◀ from next goes back to the channel it belongs to, not to whatever lies to the left.
                LiveScheduleItem(
                    next, accentTime = true, onClick = { onOpenProgramme(next) },
                    modifier = Modifier.focusRequester(scheduleFocus).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onBackToList(); true } else false
                    },
                )
                later.forEach { l ->
                    LiveScheduleItem(
                        l, accentTime = false, onClick = { onOpenProgramme(l) },
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onBackToList(); true } else false
                        },
                    )
                }
            }
        }
        }
        }
    }
}

/**
 * One "20:30  Kingdom of the Planet of the Apes" entry under the stage, 18 px. Next has the accent
 * time and full-colour title, later is muted. Focused = FILLED.
 */
@Composable
private fun LiveScheduleItem(p: XtEpgEntry, accentTime: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formatTime = rememberSystemTimeFormatter()
    val a = stageAccent
    StageSurface(onClick = onClick, radius = 12.mpx, modifier = modifier) { focused ->
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = Tabular, color = if (focused) a.onAccent else if (accentTime) a.accent else StageColors.Muted)) {
                    append(formatTime(p.startMs))
                }
                append("  ")
                append(p.title)
            },
            style = stageText(18, 400), color = if (focused) a.onAccent else if (accentTime) StageColors.Text else StageColors.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.mpx, vertical = 6.mpx),
        )
    }
}

/** Scroll only as far as the focused item, plus one row of room, needs (the TV default pins it a third down). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal val edgeScrollSpec = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> get() = tv.own.owntv.glcn.SmoothScroll.animation
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // One row of room kept ahead of the highlight, so the next row is already laid out: a held
        // ▲/▼ otherwise outran the list at the edge and stopped.
        val room = size.coerceAtMost((containerSize - size).coerceAtLeast(0f) / 2f)
        val lead = offset - room
        val trailing = offset + size + room - containerSize
        return when {
            lead >= 0f && trailing <= 0f -> 0f
            lead < 0f || size > containerSize -> lead
            else -> trailing
        }
    }
}

/**
 * glcn: the playlists the category sheet offers above the categories (several playlists only) and how
 * to switch to one. Provided once by the shell, read by [LiveCategories] where `showPlaylists` is set.
 */
@androidx.compose.runtime.Immutable
internal data class CategoryPlaylists(
    val playlists: List<tv.own.owntv.core.database.entity.SourceEntity>,
    /** The active playlist's id; 0 or below means all playlists. */
    val activeId: Long,
    val onSelect: (Long) -> Unit,
)

internal val LocalCategoryPlaylists = androidx.compose.runtime.staticCompositionLocalOf<CategoryPlaylists?> { null }

/** A category as the Stage sheet / column shows it: the rail item, its parsed name and its count. */
internal data class LiveCategoryEntry(
    val item: LiveRailItem,
    val label: String,
    val name: ProviderName?,
    val icon: OwnTVIcon?,
    val count: Int?,
    val mark: PlaylistMark?,
)

/**
 * Live TV's category list as the sheet and column draw it — the fixed entries with their icons, the
 * provider groups parsed into name and tags — and the "GROUPS · DE" heading above the groups. The TV
 * Guide's category sheet is this same list.
 */
@Composable
internal fun liveCategoryEntries(railItems: List<LiveRailItem>, railCounts: Map<tv.own.owntv.core.live.LiveKey, Int>): Pair<List<LiveCategoryEntry>, String> {
    val recentLabel = stringResource(R.string.content_category_recently_watched)
    val entries = railItems.map { item ->
        val label = if (item.key == tv.own.owntv.core.live.LiveKey.History) recentLabel else item.displayLabel()
        val parsed = if (item.key is tv.own.owntv.core.live.LiveKey.Folder || item.key is tv.own.owntv.core.live.LiveKey.Custom) ProviderTags.parse(label) else null
        LiveCategoryEntry(
            item = item,
            label = parsed?.name ?: label,
            name = parsed,
            icon = when (item.key) {
                tv.own.owntv.core.live.LiveKey.Favorites -> OwnTVIcon.FAVORITE
                tv.own.owntv.core.live.LiveKey.History -> OwnTVIcon.HISTORY
                tv.own.owntv.core.live.LiveKey.Catchup -> OwnTVIcon.REWIND
                tv.own.owntv.core.live.LiveKey.All -> OwnTVIcon.LIVE_TV
                else -> null
            },
            count = railCounts[item.key],
            mark = null,
        )
    }
    val groupCountry = ProviderTags.sharedCountry(entries.mapNotNull { it.name })
    // A group keeps its "UK|" unless the heading below says it for every group.
    val labelled = entries.map { e -> e.name?.let { e.copy(label = ProviderTags.groupLabel(it, groupCountry)) } ?: e }
    val heading = (if (groupCountry != null) stringResource(R.string.content_live_groups_country, groupCountry) else stringResource(R.string.content_live_groups))
        .uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale)
    return labelled to heading
}

/**
 * The categories: the glass sheet (450 wide, radius 32, "Categories · Live TV") or the flat
 * column (the Movies column). Search, the fixed entries with icons and counts, then
 * "GROUPS · DE" and the provider groups with their tags, counts and playlist marks. The selected
 * category is accent text plus the dot (sheet) or the 4 px bar (column).
 *
 * Focus rules: entering lands on the open category, never a row only browsed;
 * vertical exits are trapped; Right hands focus to the channel list ([onNavigateRight]).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LiveCategories(
    entries: List<LiveCategoryEntry>,
    selectedIndex: Int,
    groupsHeading: String,
    sheet: Boolean,
    listState: LazyListState,
    onSelect: (Int) -> Unit,
    onLongSelect: (Int) -> Unit,
    onNavigateRight: () -> Unit,
    /** glcn: whether the cursor rests on the playlist chips, where ◀ moves between them. */
    onChipsFocused: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusRowIndex: Int? = null,
    onRowFocused: () -> Unit = {},
    /** Which row has focus as the user moves (Movies / Series: CH± steps from it without selecting). */
    onRowFocus: (Int) -> Unit = {},
    /** The sheet's heading and the line beside it; the TV Guide says "Guide category · Same list as Live TV". */
    sheetTitle: String = stringResource(R.string.content_category_browser_title),
    sheetHint: String = stringResource(R.string.common_nav_live_tv),
    /** The category search, held by the screen's view model so it survives the player (issue: a
     *  search for one of 100 categories was gone on return). Null = kept here, for this composition. */
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    /** The category search is being typed in (the player's sheet then leaves ◀ to the text cursor). */
    onSearchEditingChange: (Boolean) -> Unit = {},
    /** glcn: list the playlists (when there are several) with the active one opened onto its categories. */
    showPlaylists: Boolean = false,
) {
    var localQuery by remember { mutableStateOf("") }
    val query = searchQuery ?: localQuery
    val setQuery: (String) -> Unit = onSearchQueryChange ?: { localQuery = it }
    // glcn: entries hidden with a long press (Recent, Favourites, a playlist) — only the list, never the channels.
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    androidx.compose.runtime.remember { tv.own.owntv.glcn.HiddenCategories.ensure(appCtx) }
    val hiddenKeys by tv.own.owntv.glcn.HiddenCategories.keys.collectAsStateWithLifecycle()
    val hideable = remember { setOf("Favorites", "History", "Catchup") }
    val hiddenEntries = remember(entries, hiddenKeys) { entries.indices.count { entries[it].item.key.toString().let { k -> k in hideable && k in hiddenKeys } } }
    val visible = remember(entries, query, hiddenKeys) {
        val q = query.trim()
        entries.indices.filter { i ->
            val k = entries[i].item.key.toString()
            !(k in hideable && k in hiddenKeys) && (q.isEmpty() || entries[i].label.contains(q, ignoreCase = true))
        }
    }
    val rowFocusers = remember(visible.size) { List(visible.size) { FocusRequester() } }
    val searchFocus = remember { FocusRequester() }
    // glcn: playlist rows — "All playlists" then each playlist; the active one sits open above its categories.
    val switcher = if (showPlaylists) LocalCategoryPlaylists.current?.takeIf { it.playlists.size > 1 } else null
    val allPlaylistsText = stringResource(R.string.content_all_playlists)
    val activePlaylist = if (switcher != null && switcher.activeId > 0L) switcher.activeId else -1L
    val allPlaylistRows = remember(switcher?.playlists, allPlaylistsText) {
        switcher?.let { listOf(-1L to allPlaylistsText) + it.playlists.map { src -> src.id to src.name } }.orEmpty()
    }
    // glcn: playlists are a row of chips right under the search, never buried below hundreds of groups.
    val playlistRows = remember(allPlaylistRows, hiddenKeys, activePlaylist) {
        allPlaylistRows.filter { (id, _) -> id <= 0L || id == activePlaylist || tv.own.owntv.glcn.HiddenCategories.playlistKey(id) !in hiddenKeys }
    }
    val hiddenPlaylists = allPlaylistRows.size - playlistRows.size
    // Items above the first category: the search field and the playlist chips.
    val headerCount = if (playlistRows.isEmpty()) 1 else 2
    val scope = rememberCoroutineScope()
    val entryLong: (LiveCategoryEntry, Int) -> Unit = { entry, index ->
        val k = entry.item.key.toString()
        if (k in hideable) {
            tv.own.owntv.glcn.HiddenCategories.hide(appCtx, k)
            android.widget.Toast.makeText(appCtx, R.string.glcn_category_hidden_toast, android.widget.Toast.LENGTH_SHORT).show()
        } else onLongSelect(index)
    }
    // CH+/− in the categories changes the selection; the highlight follows it there.
    var hasFocus by remember { mutableStateOf(false) }
    // glcn: while the cursor rests on the playlist chips, switching list must not pull it down to a row.
    var chipsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(chipsFocused) { onChipsFocused(chipsFocused) }
    // The chip the cursor last rested on; cleared when it moves to the search or a row. Switching list rebuilds
    // the rows below, and the cursor must not be dragged down with them.
    var lastChipId by remember { mutableStateOf<Long?>(null) }
    val chipFocusers = remember(playlistRows) { playlistRows.associate { it.first to FocusRequester() } }
    LaunchedEffect(entries) {
        val id = lastChipId ?: return@LaunchedEffect
        val target = chipFocusers[id] ?: return@LaunchedEffect
        withFrameNanos { }
        if (!chipsFocused) {
            runCatching { listState.scrollToItem(0) }
            withFrameNanos { }
            runCatching { target.requestFocus() }
        }
    }
    // glcn: ▲ ▼ by row number, as the channel list does. A held key outran the lazy list: the row it was heading for
    // was not composed yet, focus was lost, and coming back in landed on the selected category far away — the list
    // went off to a group and returned. Scroll just enough, wait a frame, then focus the row; the newest press wins.
    var stepTarget by remember { mutableStateOf<Int?>(null) }
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun lazyIndexOf(pos: Int): Int {
        val groupsHeading = query.isBlank() && firstGroup >= 0 && visible.indexOf(firstGroup).let { it in 0..pos }
        return headerCount + pos + if (groupsHeading) 1 else 0
    }
    fun stepTo(pos: Int) {
        stepTarget = pos
        stepJob?.cancel()
        stepJob = scope.launch {
            val li = lazyIndexOf(pos)
            val info = listState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == li }
            val step = (info.visibleItemsInfo.lastOrNull { it.index >= headerCount }?.size ?: 56).toFloat()
            val delta = when {
                row == null -> if (li > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step * 2 else -step * 2
                row.offset - step < info.viewportStartOffset -> row.offset - step - info.viewportStartOffset
                row.offset + row.size + step > info.viewportEndOffset -> row.offset + row.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { listState.scrollBy(delta) }
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == li }) runCatching { listState.scrollToItem(li) }
            withFrameNanos { }
            runCatching { rowFocusers[pos].requestFocus() }
            stepTarget = null
        }
    }
    LaunchedEffect(selectedIndex) {
        val pos = visible.indexOf(selectedIndex)
        if (!hasFocus || chipsFocused || pos < 0) return@LaunchedEffect
        runCatching { listState.scrollToItem(pos + headerCount) } // the search field and playlist rows come first
        withFrameNanos { }
        runCatching { rowFocusers[pos].requestFocus() }
    }
    LaunchedEffect(focusRowIndex, visible) {
        val target = focusRowIndex ?: return@LaunchedEffect
        if (chipsFocused) return@LaunchedEffect
        visible.indexOf(target).takeIf { it >= 0 }?.let { pos ->
            // A row further than the screen (a CH± jump) is not composed yet: scroll to it first.
            if (!runCatching { rowFocusers[pos].requestFocus() }.getOrDefault(false)) {
                runCatching { listState.scrollToItem(pos + headerCount) } // the search field and playlist rows come first
                withFrameNanos { }
                runCatching { rowFocusers[pos].requestFocus() }
            }
        }
        onRowFocused()
    }
    // The search stays until it is cleared: Back inside the list clears it and returns to the field,
    // so a long filtered list is one press from the top. With no search, Back does what it always did.
    androidx.activity.compose.BackHandler(enabled = hasFocus && query.isNotEmpty()) {
        setQuery("")
        scope.launch {
            runCatching { listState.scrollToItem(0) }
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }
    // Where the fixed entries end and the provider groups begin (the "GROUPS · DE" heading).
    val firstGroup = entries.indexOfFirst { it.item.key is tv.own.owntv.core.live.LiveKey.Folder || it.item.key is tv.own.owntv.core.live.LiveKey.Custom }
    val rightKey = Modifier.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onNavigateRight(); true } else false
    }
    val a = stageAccent
    Column(
        modifier
            .then(if (sheet) Modifier.stageGlass(StageRadii.Sheet, overContent = true).padding(horizontal = 16.mpx, vertical = 26.mpx) else Modifier),
    ) {
        if (sheet) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx, bottom = 16.mpx),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sheetTitle, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(sheetHint, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.mpx))
            }
        }
        // Edge-only scrolling, as the channel list (no double step on ▼).
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
        tv.own.owntv.glcn.SlideFocusHost(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusProperties {
                    onEnter = {
                        val pos = visible.indexOf(selectedIndex)
                        val target = if (pos in rowFocusers.indices) rowFocusers[pos] else searchFocus
                        if (!runCatching { target.requestFocus() }.getOrDefault(false)) {
                            scope.launch {
                                runCatching { listState.scrollToItem(if (pos >= 0) pos + headerCount else 0) }
                                withFrameNanos { }
                                runCatching { target.requestFocus() }
                            }
                        }
                    }
                }
                .trapVerticalFocusExit()
                .focusGroup(),
            // Room for the 2 px outer focus ring, which the list would otherwise clip.
            contentPadding = PaddingValues(start = 4.mpx, end = 4.mpx, top = 4.mpx, bottom = 8.mpx),
        ) {
            item(key = "__search__") {
                StageSearchField(
                    query = query,
                    onQueryChange = setQuery,
                    placeholder = stringResource(R.string.content_search_categories).trimEnd('…'),
                    onEditingChange = onSearchEditingChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (sheet) 8.mpx else 0.mpx, end = if (sheet) 8.mpx else 0.mpx, bottom = if (sheet) 14.mpx else 16.mpx)
                        .focusRequester(searchFocus)
                        .then(if (query.isEmpty()) rightKey else Modifier),
                    height = if (sheet) 54.mpx else 48.mpx,
                    radius = if (sheet) 16.mpx else 15.mpx,
                    horizontalPadding = if (sheet) 16.mpx else 18.mpx,
                )
            }
            if (playlistRows.isNotEmpty()) item(key = "__playlists__") {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().onFocusChanged { chipsFocused = it.hasFocus }
                        // ▲/▼ is the user leaving the chips on purpose; anything else that moves the cursor is undone.
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionDown || e.key == Key.DirectionUp)) lastChipId = null
                            false
                        }.padding(start = if (sheet) 8.mpx else 0.mpx, end = if (sheet) 8.mpx else 0.mpx, bottom = if (sheet) 14.mpx else 12.mpx),
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    contentPadding = PaddingValues(horizontal = 4.mpx, vertical = 4.mpx),
                ) {
                    items(playlistRows.size, key = { playlistRows[it].first }) { row ->
                        val (id, name) = playlistRows[row]
                        val open = id == activePlaylist
                        // glcn: the lists listed here are the active ones — resting on one shows it, no OK needed.
                        var chipFocused by remember { mutableStateOf(false) }
                        LaunchedEffect(chipFocused, open) {
                            if (chipFocused && !open) {
                                kotlinx.coroutines.delay(350)
                                switcher?.onSelect?.invoke(id)
                            }
                        }
                        StageSurface(
                            onClick = { if (!open) switcher?.onSelect?.invoke(id) },
                            onLongClick = if (id > 0L && !open) ({
                                tv.own.owntv.glcn.HiddenCategories.hide(appCtx, tv.own.owntv.glcn.HiddenCategories.playlistKey(id))
                                android.widget.Toast.makeText(appCtx, R.string.glcn_category_hidden_toast, android.widget.Toast.LENGTH_SHORT).show()
                            }) else null,
                            radius = 22.mpx,
                            modifier = Modifier.height(46.mpx)
                                .then(chipFocusers[id]?.let { Modifier.focusRequester(it) } ?: Modifier)
                                .onFocusChanged { chipFocused = it.hasFocus; if (it.hasFocus) lastChipId = id }
                                // glcn: ◀ on the first playlist stops there — it must not leave the sheet for the quick guide.
                                .onPreviewKeyEvent { e -> row == 0 && e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft },
                        ) { focused ->
                            Row(Modifier.padding(horizontal = 18.mpx).height(46.mpx), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name, style = stageText(18, 700), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (focused) a.onAccent else if (open) a.accent else StageColors.Muted,
                                    modifier = Modifier.widthIn(max = 220.mpx),
                                )
                            }
                        }
                    }
                }
            }
            visible.forEachIndexed { pos, index ->
                val entry = entries[index]
                if (index == firstGroup && query.isBlank()) {
                    item(key = "__groups__") {
                        if (sheet) StageGroupLabel(groupsHeading, sheet = true)
                        else Text(
                            groupsHeading, style = stageText(12.5f, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.mpx, top = 8.mpx, bottom = 8.mpx),
                        )
                    }
                }
                item(key = entry.item.key.toString()) {
                    val rowModifier = Modifier.focusRequester(rowFocusers[pos]).then(rightKey)
                        .onFocusChanged { if (it.hasFocus) onRowFocus(index) }
                        .onPreviewKeyEvent { e ->
                            val down = e.key == Key.DirectionDown
                            if (e.type != KeyEventType.KeyDown || (!down && e.key != Key.DirectionUp)) return@onPreviewKeyEvent false
                            val to = (stepTarget ?: pos) + if (down) 1 else -1
                            when {
                                to < 0 -> false // ▲ from the first row goes on to the playlists or the search
                                to >= visible.size -> true
                                else -> { stepTo(to); true }
                            }
                        }
                    val selected = index == selectedIndex
                    if (sheet) {
                        StageSheetItem(
                            text = entry.label,
                            onClick = { onSelect(index) },
                            icon = entry.icon,
                            count = entry.count?.let { tv.own.owntv.ui.components.formatCount(it) },
                            selected = selected,
                            maxLines = 2,
                            tags = if (entry.name?.tags.isNullOrEmpty()) null else { focused ->
                                entry.name.tags.forEach { StageTag(it, onAccent = if (focused) a.onAccent else null) }
                            },
                            trailing = entry.mark?.let { m -> { _ -> StagePlaylistMark(m) } },
                            onLongClick = { entryLong(entry, index) },
                            modifier = rowModifier,
                        )
                    } else {
                        LiveColumnItem(entry, selected, onClick = { onSelect(index) }, onLongClick = { entryLong(entry, index) }, modifier = rowModifier)
                    }
                }
            }
            if (query.isBlank() && (hiddenEntries > 0 || hiddenPlaylists > 0)) item(key = "__show_hidden__") {
                StageSheetItem(
                    text = stringResource(R.string.glcn_show_hidden_categories, hiddenEntries + hiddenPlaylists),
                    onClick = { tv.own.owntv.glcn.HiddenCategories.showAll(appCtx) },
                    icon = OwnTVIcon.LAYERS,
                    modifier = Modifier.padding(top = 6.mpx).then(rightKey),
                )
            }
        }
        }
        }
    }
}

/**
 * `.fcat` (the Separate column): 52 high, radius 15, 18.5/600, gap 12, 2 px between rows; the
 * selected one is accent text with the 4 px glowing bar at its left edge; one tag, the count 15/700
 * dim, and the playlist's 7 px dot. Focused = FILLED.
 */
@Composable
private fun LiveColumnItem(
    entry: LiveCategoryEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 15.mpx,
        modifier = modifier.fillMaxWidth().padding(bottom = 2.mpx).heightIn(min = 52.mpx)
            .then(if (selected) Modifier.stageSelectedBar(a.accent, 14.mpx) else Modifier),
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 7.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = if (focused) a.onAccent else null
            entry.icon?.let { OwnTVIcon(it, on ?: StageColors.Muted, Modifier.size(20.mpx)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                Text(
                    entry.label, style = stageText(18.5f, 600),
                    color = on ?: if (selected) a.accent else stageTone(Color(0xFFCDD7D3), StageColors.ItemText),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                // Tags on their own line, so a long name is never cut for them.
                val tags = entry.name?.tags.orEmpty()
                if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.mpx)) { tags.forEach { StageTag(it, onAccent = on) } }
            }
            entry.count?.let { Text(tv.own.owntv.ui.components.formatCount(it), style = stageText(15, 700), color = on ?: StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            entry.mark?.let { m -> Box(Modifier.size(7.mpx).background(m.shownColor, RoundedCornerShape(50))) }
        }
    }
}
