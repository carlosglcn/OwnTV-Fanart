package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRequester
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.ui.stage.stageGlass
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.features.live.LiveCategories
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.liveCategoryEntries
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx

/**
 * Category sheet that slides in over the playing video when the user presses Left a second time
 * inside the in-player channel list: Live TV's own category sheet ([LiveCategories]), 20 from the
 * screen edge — search, Favorites / Recently watched / Catch-up / All channels with their counts, then
 * the provider groups with their tags (customizations applied: hidden categories excluded, renames
 * shown, manual order respected). OK loads that category's channels into the channel-list overlay;
 * Back or Left returns to the channel list without changing anything. The playing list's category is
 * the selected one and is focused first. The remote's paging keys page through it, as in the channel list.
 */
@Composable
fun CategoryBrowserOverlay(
    railItems: List<LiveRailItem>,
    railCounts: Map<LiveKey, Int>,
    currentKey: LiveKey?,
    onSelect: (LiveKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** glcn: the channels of the focused category, shown beside the sheet; null keeps the sheet alone. */
    peekChannels: (suspend (LiveKey) -> List<tv.own.owntv.core.database.entity.ChannelEntity>)? = null,
    loadNow: (suspend (List<tv.own.owntv.core.database.entity.ChannelEntity>) -> Map<Long, tv.own.owntv.core.database.entity.EpgProgrammeEntity>)? = null,
    onTune: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val (entries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val selectedIndex = remember(entries, currentKey) { entries.indexOfFirst { it.item.key == currentKey }.coerceAtLeast(0) }
    val listState = rememberLazyListState()
    val listFocus = remember { FocusRequester() }
    // Paging moves focus by setting the row to land on; the sheet scrolls to it and reports back.
    var focusRow by remember { mutableStateOf<Int?>(null) }
    var focusedIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    var hasFocus by remember { mutableStateOf(false) }
    // While the search is being typed in, ◀/▶ move the text cursor; OK or Back ends typing first.
    var typing by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()

    // Entering the sheet's list lands on the selected category (its focusProperties.onEnter). Focusing
    // it scrolls it to the top edge, hiding the search and the fixed entries above it; when the
    // selected row fits on the first screen, keep the list at its top as the Live TV sheet shows it.
    LaunchedEffect(Unit) {
        runCatching { listFocus.requestFocus() }
        withFrameNanos { }
        if (selectedIndex < FIRST_SCREEN_ROWS) runCatching { listState.scrollToItem(0) }
    }

    BackHandler { onDismiss() }

    // glcn: what the focused category holds, after the D-pad rests a moment.
    val channelFocus = remember { FocusRequester() }
    var peeked by remember { mutableStateOf<List<tv.own.owntv.core.database.entity.ChannelEntity>>(emptyList()) }
    var peekedNow by remember { mutableStateOf<Map<Long, tv.own.owntv.core.database.entity.EpgProgrammeEntity>>(emptyMap()) }
    val focusedKey = entries.getOrNull(focusedIndex)?.item?.key
    LaunchedEffect(focusedKey) {
        if (peekChannels == null || focusedKey == null) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        val list = runCatching { peekChannels(focusedKey) }.getOrDefault(emptyList())
        peeked = list
        peekedNow = emptyMap()
        if (loadNow != null && list.isNotEmpty()) peekedNow = runCatching { loadNow(list.take(40)) }.getOrDefault(emptyMap())
    }

    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier = modifier
            .fillMaxSize()
            .gradientWash(false, 0f to shade.copy(alpha = 0.72f), 0.38f to shade.copy(alpha = 0.45f), 0.62f to Color.Transparent),
    ) {
        LiveCategories(
            showPlaylists = true,
            entries = entries,
            selectedIndex = selectedIndex,
            groupsHeading = groupsHeading,
            sheet = true,
            listState = listState,
            onSelect = { onSelect(entries[it].item.key) },
            onLongSelect = {},
            onNavigateRight = { if (peeked.isNotEmpty()) runCatching { channelFocus.requestFocus() } },
            focusRequester = listFocus,
            focusRowIndex = focusRow,
            onRowFocused = { focusRow = null },
            onRowFocus = { focusedIndex = it },
            onSearchEditingChange = { typing = it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 20.mpx, vertical = 22.mpx)
                .width(440.mpx)
                .fillMaxHeight()
                .onFocusChanged { hasFocus = it.hasFocus }
                .onPreviewKeyEvent { e ->
                    // Pushing outward from logical Start returns to the channel list.
                    if (!typing && e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START) {
                        onDismiss(); true
                    } else false
                }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { hasFocus },
                    lastIndex = { entries.lastIndex },
                    currentTargetIndex = { focusedIndex },
                    onJumpToIndex = { focusRow = it },
                ),
        )
        if (peekChannels != null && peeked.isNotEmpty()) CategoryChannelsColumn(
            title = entries.getOrNull(focusedIndex)?.label.orEmpty(),
            channels = peeked,
            now = peekedNow,
            onTune = onTune,
            onBack = { runCatching { listFocus.requestFocus() } },
            focus = channelFocus,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (20 + 440 + 12).mpx, top = 22.mpx, bottom = 22.mpx)
                .width(500.mpx)
                .fillMaxHeight(),
        )
    }
}

/** glcn: the focused category's channels with what is on now; OK tunes, ◀ goes back to the categories. */
@Composable
private fun CategoryChannelsColumn(
    title: String,
    channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    now: Map<Long, tv.own.owntv.core.database.entity.EpgProgrammeEntity>,
    onTune: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit,
    onBack: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier
            .stageGlass(tv.own.owntv.ui.theme.StageRadii.Sheet, overContent = true)
            .padding(horizontal = 16.mpx, vertical = 22.mpx)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionLeft) { onBack(); true } else false
            },
    ) {
        androidx.tv.material3.Text(
            title, style = tv.own.owntv.ui.theme.stageText(22, 800), color = tv.own.owntv.ui.theme.StageColors.Text,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.mpx, end = 12.mpx, bottom = 12.mpx),
        )
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxHeight().focusGroup(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.mpx),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                tv.own.owntv.ui.stage.StageSurface(
                    onClick = { onTune(ch) },
                    radius = 14.mpx,
                    modifier = (if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth(),
                ) { focused ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(horizontal = 12.mpx, vertical = 9.mpx),
                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tv.own.owntv.ui.components.ChannelLogoTile(
                            logoUrl = ch.displayLogoUrl,
                            modifier = Modifier.width(64.mpx).height(46.mpx),
                            fill = Color.Transparent,
                        ) { }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            androidx.tv.material3.Text(
                                tv.own.owntv.core.live.ProviderTags.parse(ch.name).name,
                                style = tv.own.owntv.ui.theme.stageText(19, 700),
                                color = if (focused) tv.own.owntv.ui.theme.stageAccent.onAccent else tv.own.owntv.ui.theme.StageColors.Text,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            now[ch.id]?.title?.let {
                                androidx.tv.material3.Text(
                                    it, style = tv.own.owntv.ui.theme.stageText(16, 400),
                                    color = if (focused) tv.own.owntv.ui.theme.stageAccent.onAccent.copy(alpha = 0.8f) else tv.own.owntv.ui.theme.StageColors.Muted,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Rows that fit on the sheet's first screen below the search field, the fixed entries included. */
private const val FIRST_SCREEN_ROWS = 10
