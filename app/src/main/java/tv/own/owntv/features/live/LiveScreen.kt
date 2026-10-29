package tv.own.owntv.features.live

import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.focus.FocusRequester as RowFocus
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.glcn.staggerAt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.owntv.R
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import tv.own.owntv.core.customize.categoryRestoreSummary
import tv.own.owntv.core.customize.CategoryRestorePlan
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.features.customize.MoveToCategoryDialog
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.features.settings.data.computePanelWidths
import tv.own.owntv.features.settings.rememberPanelShares
import tv.own.owntv.features.shell.components.CategoryContextMenu
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.gridFocusTarget
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags

/** Live TV: the channel list, the stage beside it and the categories as a sheet or a column. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Pins the list to one folder and takes the category rail away — how More → Favourites and
     * More → History show channels without a second copy of this list existing.
     */
    lockedKey: LiveKey? = null,
    /** Guide view (Live TV tool row): opens the TV Guide until an in-place guide exists. */
    onOpenGuide: () -> Unit = {},
) {
    val vm: LiveViewModel = koinViewModel()
    // Locking and unlocking are one pair: the pin belongs to this screen's lifetime, not to the view
    // model's. On the television that view model is a single instance shared with the browse section,
    // so a pin left behind froze its category rail. `DisposableEffect` (not `LaunchedEffect`) also
    // means the pin is in place before the first frame, so the list never flashes the wrong folder.
    if (lockedKey != null) {
        val pinned = lockedKey
        DisposableEffect(pinned) {
            vm.lock(pinned)
            onDispose { vm.unlock() }
        }
    }
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val railFocus = remember { FocusRequester() }
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val showChannelNumbers by vm.showChannelNumbers.collectAsStateWithLifecycle()
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val catchupPlayer by vm.catchupPlayer.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val previewCategoryName by vm.previewCategoryName.collectAsStateWithLifecycle()
    val previewArmed by vm.previewArmed.collectAsStateWithLifecycle()
    val previewBlockedSingleSession by vm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    // glcn: which channel [nowNext] belongs to. The preview's channel changes the moment the cursor
    // moves, its guide a moment later — in between, the newly focused row showed the previous channel's
    // programme. Only a guide that arrived while this channel was previewed is its own.
    var nowNextFor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(nowNext) { nowNextFor = previewChannel?.id }
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    /** The search field's keyboard is open — ◀ then moves the text cursor, not to Categories. */
    var searchEditing by remember { mutableStateOf(false) }
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val livePreviewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    val categoryMoveState by vm.categoryMoveState.collectAsStateWithLifecycle()

    // Current programme title for each loaded channel (id → title), against the stored guide. Drives
    // the small "now playing" subtitle on each channel row. Channels with no guide are absent from the
    // map → their row shows no second line.
    //
    // An appended page asks only about the channels it added; the view model keeps the rest. This
    // used to re-query every loaded channel on every append and again every 60 seconds, which deep
    // in a large category was a dozen chunked queries a minute to learn nothing new.
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val loadedChannels = channels.itemSnapshotList.items.filterNotNull()
    LaunchedEffect(loadedChannels.size, loadedChannels.firstOrNull()?.id, loadedChannels.lastOrNull()?.id) {
        vm.ensureNowPlaying(loadedChannels)
    }
    // Turnover happens on the minute, so wait for the next one rather than 60s from mount — otherwise
    // rows change late and at different instants from each other.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000 - (now % 60_000))
            vm.refreshNowPlaying(channels.itemSnapshotList.items.filterNotNull())
        }
    }
    // Preview runs only when the player isn't busy (previewEnabled) AND the user hasn't turned it off.
    val effectivePreview = previewEnabled && livePreviewSetting

    // NOTE: do NOT stop the player when LiveScreen leaves composition — going fullscreen disposes
    // this screen, and stopping here would abort the stream that was just started. Playback is
    // stopped on fullscreen exit (shell BackHandler) instead.

    // In-pane preview: play the focused channel after the focus settles (700ms). Disabled while the
    // fullscreen/mini player owns the surface (previewEnabled=false) to avoid two surfaces fighting.
    // glcn: "Preview with OK" — nothing plays while walking the list; the first OK starts the preview of the
    // focused channel, a second OK on it goes full screen. Moving on does not stop it: it stays on the channel
    // that got the OK until OK is pressed on another one (which takes its place).
    val okOnly = tv.own.owntv.glcn.LowRam.previewOnOk
    var okChannelId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(okOnly) { if (!okOnly) okChannelId = null }
    LaunchedEffect(previewChannel?.id, effectivePreview, previewArmed, okChannelId) {
        // previewArmed gates the case where the last channel was restored on startup — we don't auto-preview
        // it until the user actually focuses a channel (then it plays normally).
        if (!effectivePreview || !previewArmed) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        if (okOnly && okChannelId != ch.id) return@LaunchedEffect
        delay(if (okOnly) 0 else 700)
        vm.playPreview(ch)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    // Right from the rail on an empty list: the list's search box, so a search with no results can be cleared.
    val listSearchFocus = remember { FocusRequester() }

    // CH+- key paging: shared settings + a hoisted rail state so the same modifier can page both the
    // category rail and this channel list. Channel-list pane focus is tracked separately from rail
    // focus so chNavPaging only consumes the keys for whichever pane is active.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position via a per-category
    // state map (so A→B→A lands back where you were in A). OFF → reset the shared state to the top whenever
    // the category changes (fixes the cross-category scroll-leak bug).
    val perCategoryStates = remember { mutableStateMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    val perCategoryChannelIds = remember { mutableStateMapOf<LiveKey, Long>() }
    // glcn: a channel tuned from elsewhere (Home, the Guide, CH±) becomes the remembered row, so the list
    // comes back on what is playing — not on the last row walked to in it.
    LaunchedEffect(previewChannel?.id) {
        val id = previewChannel?.id ?: return@LaunchedEffect
        if (rememberLive) perCategoryChannelIds[selectedKey] = id
    }
    val effectiveListState =
        if (rememberLive) perCategoryStates.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() }
        else listState
    LaunchedEffect(selectedKey, rememberLive) {
        if (!rememberLive) runCatching { listState.scrollToItem(0) }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val categoryQuery by vm.categoryQuery.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var channelPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var offsettingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var catchupChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // The channel the "Schedule recording…" dialog is open for (#2).
    var schedulingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Programme picked in the catch-up dialog, awaiting the "Watch from start / Watch channel" choice.
    // The Live picker used to start the archive straight from the pick, so the same programme opened
    // from the Guide (which asks) and from here behaved differently — this makes the two match.
    var catchupDetail by remember {
        mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(null)
    }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    var synopsisChannel by remember { mutableStateOf<ChannelEntity?>(null) } // glcn: full synopsis popup
    // glcn: multi-select. null = not selecting; otherwise the marked channels, in the order they were marked.
    var selecting by remember { mutableStateOf<List<ChannelEntity>?>(null) }
    var batchActions by remember { mutableStateOf(false) }
    var batchMove by remember { mutableStateOf(false) }
    fun toggleSelected(ch: ChannelEntity) {
        val cur = selecting ?: return
        selecting = if (cur.any { it.id == ch.id }) cur.filterNot { it.id == ch.id } else cur + ch
    }
    androidx.activity.compose.BackHandler(enabled = selecting != null && !batchActions && !batchMove) { selecting = null }
    // Multiview: whether the menu offers it at all, how many tiles the grid has, and the confirmation
    // after a channel is kept (null = nothing to say).
    val liveSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val multiviewEnabled by liveSettings.multiviewEnabled.collectAsStateWithLifecycle(false)
    val multiviewTiles by liveSettings.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    // Also says where a scheduled recording landed ("Will record Sat 21:00").
    val multiviewToast = tv.own.owntv.ui.components.rememberInAppToast()
    val recordingChannelIds by vm.recordingChannelIds.collectAsStateWithLifecycle()
    // Resolved through resources rather than stringResource: the count is only known inside the click.
    val multiviewRes = androidx.compose.ui.platform.LocalContext.current.resources
    var contextCategory by remember { mutableStateOf<LiveRailItem?>(null) }
    // glcn: groups inside Favourites.
    var groupingChannels by remember { mutableStateOf<List<ChannelEntity>?>(null) }
    var namingGroupFor by remember { mutableStateOf<List<ChannelEntity>?>(null) }
    var groupMenuFor by remember { mutableStateOf<String?>(null) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }
    // The rail row a category menu was opened from, kept after the menu closes so the cursor can go
    // back to that exact row. Held as a key, not an index: a Move changes the row's position.
    var contextCategoryKey by remember { mutableStateOf<LiveKey?>(null) }
    var railFocusRow by remember { mutableStateOf<Int?>(null) }
    // The channel the "Move to category…" flow is moving, with the origin captured at
    // menu-open time (the rail can't change under the modal, but capturing is still safer).
    var moveItem by remember { mutableStateOf<ChannelEntity?>(null) }
    var moveOriginKey by remember { mutableStateOf<String?>(null) }
    var moveOriginName by remember { mutableStateOf<String?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    // When the long-press menu closes (Cancel, Favourite, Hide) WITHOUT opening another dialog, return focus
    // to the channel it was opened from — otherwise focus falls back to the nav panel.
    var contextMenuOpen by remember { mutableStateOf(false) }
    // Id of the channel the context menu was opened on, plus a dedicated requester bound to that row.
    // The previous restore was racy (delay(60) + selFocus bound to the *previewed* channel): when the
    // menu scrim disposed the focused menu button, Compose auto-restored focus and the category rail's
    // entry-redirect pinned it to the rail before selFocus.requestFocus() ran. Tracking the long-press
    // target by id and binding a dedicated requester makes the restore deterministic.
    var contextChannelId by remember { mutableStateOf<Long?>(null) }
    val contextFocus = remember { FocusRequester() }
    var enteringMoveMode by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) { if (moveState != null) enteringMoveMode = false }
    // Bring row [idx] on screen only when it is not already fully there: returning focus to a row
    // that is visible must not yank the list so that row jumps to the top.
    suspend fun revealRow(idx: Int) {
        val info = effectiveListState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == idx }
        val fullyVisible = row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset
        if (!fullyVisible) runCatching { effectiveListState.scrollToItem(idx) }
    }
    // One requester per row position, for ▲/▼ stepping. Only composed rows are attached.
    val rowRequesters = remember { HashMap<Int, RowFocus>() }
    fun rowFocus(i: Int): RowFocus = rowRequesters.getOrPut(i) { RowFocus() }
    // Move to row [i]: scroll just enough to show it with one row of room (the edge-scroll rule), wait for
    // it to be laid out, then focus it. Works for a held key: nothing depends on focus search.
    // The row a burst of presses is heading for: every press counts from there, not from the row that
    // still has focus, and the newest press cancels the step still in flight.
    var stepTarget by remember { mutableStateOf<Int?>(null) }
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun stepTo(i: Int) {
        stepTarget = i
        stepJob?.cancel()
        stepJob = scope.launch {
            val info = effectiveListState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == i }
            val step = ((info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing).toFloat()
            val delta = when {
                row == null -> if (i > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step else -step
                row.offset - step < info.viewportStartOffset -> row.offset - step - info.viewportStartOffset
                row.offset + row.size + step > info.viewportEndOffset -> row.offset + row.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { effectiveListState.scrollBy(delta) }
            if (effectiveListState.layoutInfo.visibleItemsInfo.none { it.index == i }) runCatching { effectiveListState.scrollToItem(i) }
            withFrameNanos { }
            runCatching { rowFocus(i).requestFocus() }
            stepTarget = null
        }
    }
    // Land focus back on the long-pressed channel's row (or a sensible fallback if it's gone).
    suspend fun restoreToContextRow() {
        val targetId = contextChannelId
        if (targetId == null) { runCatching { selFocus.requestFocus() }; return }

        val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            revealRow(idx)
            withFrameNanos { } // wait one frame so the row is laid out and contextFocus is attached
            runCatching { contextFocus.requestFocus() }
        } else {
            // Row is gone (e.g. "Hide channel" removed it) — clear the anchor and land on the first row.
            contextChannelId = null
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(contextChannel) {
        val opened = contextChannel != null
        if (opened) { contextMenuOpen = true; return@LaunchedEffect }
        if (!contextMenuOpen) return@LaunchedEffect
        contextMenuOpen = false
        // A follow-up dialog (rename / match EPG / catch-up / move) grabs focus itself — only restore
        // for plain closes (Cancel, Favourite, Hide, Close). Those dialogs restore on their own close.
        if (renaming != null || matchingEpg != null || offsettingEpg != null || catchupChannel != null || schedulingChannel != null || enteringMoveMode ||
            moveItem != null || creatingCategory
        ) return@LaunchedEffect
        restoreToContextRow()
    }
    // The context menu closes before the shared Move-to-category dialog opens. Treat both the move
    // picker and its nested New-category prompt as one focus-owning flow, then restore the original
    // row only after the whole flow closes. Otherwise the menu-close effect can steal focus behind
    // the new dialog.
    var moveCategoryWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveItem, creatingCategory) {
        if (moveItem != null || creatingCategory) {
            moveCategoryWasOpen = true
        } else if (moveCategoryWasOpen) {
            moveCategoryWasOpen = false
            restoreToContextRow()
        }
    }
    // Rename restoration: re-assert row focus after dialog closes.
    var renameWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(renaming) {
        if (renaming != null) { renameWasOpen = true; return@LaunchedEffect }
        if (!renameWasOpen) return@LaunchedEffect
        renameWasOpen = false
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Catch-up restoration.
    var catchupWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(catchupChannel, catchupDetail) {
        if (catchupChannel != null || catchupDetail != null) { catchupWasOpen = true; return@LaunchedEffect }
        if (!catchupWasOpen) return@LaunchedEffect
        catchupWasOpen = false
        restoreToContextRow()
    }
    // Schedule-recording restoration.
    var schedulingWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(schedulingChannel) {
        if (schedulingChannel != null) { schedulingWasOpen = true; return@LaunchedEffect }
        if (!schedulingWasOpen) return@LaunchedEffect
        schedulingWasOpen = false
        restoreToContextRow()
    }
    // Channel reorder restoration.
    var reorderWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) {
        if (moveState != null) { reorderWasOpen = true; return@LaunchedEffect }
        if (!reorderWasOpen) return@LaunchedEffect
        reorderWasOpen = false
        restoreToContextRow()
    }
    // The Match EPG dialog grabbed focus while open — when it closes (pick/clear/dismiss), put focus
    // back on the channel it was opened for instead of letting it fall to the nav panel.
    var matchEpgWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(matchingEpg) {
        if (matchingEpg != null) { matchEpgWasOpen = true; return@LaunchedEffect }
        if (!matchEpgWasOpen) return@LaunchedEffect
        matchEpgWasOpen = false
        restoreToContextRow()
        // Picking a match rewrites customizations, which recreates the pager on its own schedule —
        // the rebuilt rows land a moment later and yank focus off the row we just restored, and the
        // exact timing varies with list size. Re-assert the target row briefly instead of racing a
        // single load-state transition.
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Same for the EPG-offset dialog: it owns focus while open, so hand it back to the channel row.
    var epgOffsetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(offsettingEpg) {
        if (offsettingEpg != null) { epgOffsetWasOpen = true; return@LaunchedEffect }
        if (!epgOffsetWasOpen) return@LaunchedEffect
        epgOffsetWasOpen = false
        restoreToContextRow()
    }
    // Category rail focus restoration.
    var contextCategoryWasOpen by remember { mutableStateOf(false) }
    var categoryMoveWasOpen by remember { mutableStateOf(false) }
    // Land on the row the menu was opened from; fall back to the column if that row is gone (Hide).
    fun restoreToContextCategory() {
        val row = contextCategoryKey?.let { k -> railItems.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        contextCategoryKey = null
        if (row != null) railFocusRow = row else runCatching { railFocus.requestFocus() }
    }
    LaunchedEffect(contextCategory, categoryMoveState) {
        if (contextCategory != null) contextCategoryWasOpen = true
        if (categoryMoveState != null) categoryMoveWasOpen = true

        if (contextCategory == null && contextCategoryWasOpen && categoryMoveState == null) {
            contextCategoryWasOpen = false
            if (!categoryMoveWasOpen) {
                kotlinx.coroutines.delay(60)
                restoreToContextCategory()
            }
        }
        if (categoryMoveState == null && categoryMoveWasOpen) {
            categoryMoveWasOpen = false
            kotlinx.coroutines.delay(60)
            restoreToContextCategory()
        }
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            revealRow(idx)
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }
    // Search's "Go to channel": scroll to where the channel sits so that part of the list loads, find
    // its row by id and focus it. The list can still be the old category's for a moment, so focus is
    // re-asserted for a short while; nothing found after ~5 s lands on the first row.
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, selectedKey) {
        val r = reveal ?: return@LaunchedEffect
        if (selectedKey != r.key) return@LaunchedEffect
        if (rememberLive) perCategoryChannelIds[selectedKey] = r.id
        var found = false
        for (attempt in 0 until 50) {
            if (channels.itemCount > 0) {
                val idx = channels.itemSnapshotList.indexOfFirst { it?.id == r.id }
                if (idx >= 0) {
                    revealRow(idx)
                    withFrameNanos { }
                    if (runCatching { selFocus.requestFocus() }.getOrDefault(false)) found = true
                    if (found && attempt >= 8) break
                } else {
                    runCatching { effectiveListState.scrollToItem(r.position.coerceAtMost(channels.itemCount - 1)) }
                }
            }
            delay(100)
        }
        if (!found) runCatching { firstItemFocus.requestFocus() }
        vm.revealDone()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel() ?: stringResource(R.string.content_category_all_channels)

    val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
    val nowProgrammes by vm.nowProgrammes.collectAsStateWithLifecycle()
    val playlistMarks by vm.playlistMarks.collectAsStateWithLifecycle()
    // Separate panels keeps the categories on screen; the Stage layout opens them as a sheet on Left.
    val separate = liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.SEPARATE && lockedKey == null
    // glcn: the Cinematic layout — hero with the programme's picture over a row of channel cards.
    val cinematic = liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.CINEMATIC && lockedKey == null
    var categoriesOpen by remember { mutableStateOf(false) }
    var toolRowHasFocus by remember { mutableStateOf(false) }
    // glcn: the "Sports now" overlay.
    var sportsOpen by remember { mutableStateOf(false) }
    var sheetHadFocus by remember { mutableStateOf(false) }
    // The current channel's row. After a long-press that row carries contextFocus instead of selFocus
    // (gridFocusTarget prefers it), so both are tried before falling back to the first row.
    fun focusCurrentRow(): Boolean =
        runCatching { selFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { contextFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
    val categoriesVisible = lockedKey == null && (separate || categoriesOpen)
    val railCounts by (if (categoriesVisible) vm.railCounts else remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<LiveKey, Int>()) })
        .collectAsStateWithLifecycle()
    val scheduleFocus = remember { FocusRequester() }
    var openProgramme by remember { mutableStateOf<Pair<ChannelEntity, tv.own.owntv.core.parser.XtEpgEntry>?>(null) }
    // Back closes the sheet: one level out.
    androidx.activity.compose.BackHandler(enabled = categoriesOpen) { categoriesOpen = false; focusCurrentRow() }
    LaunchedEffect(categoriesOpen) {
        if (categoriesOpen) { withFrameNanos { }; runCatching { railFocus.requestFocus() } }
        // However the sheet closed (Back, a pick, ◀ to the rail), the next one starts fresh.
        else sheetHadFocus = false
    }

    // Focus from the categories to the channel list: the remembered channel, else the first row.
    fun focusChannelList() {
        val targetId = if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
        scope.launch {
            if (channels.itemCount > 0) {
                val targetIdx = if (targetId != null) {
                    channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } else 0
                revealRow(targetIdx)
                withFrameNanos { }
                repeat(3) {
                    if (targetId != null && focusCurrentRow()) return@launch
                    if (runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)) return@launch
                    withFrameNanos { }
                }
            } else {
                runCatching { listSearchFocus.requestFocus() }
            }
        }
    }

    val (categoryEntries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val headerLabel = if (selectedItem?.key is LiveKey.Folder || selectedItem?.key is LiveKey.Custom) ProviderTags.parse(selectedLabel).name else selectedLabel

    // CH± in the categories moves the highlight only; OK picks.
    var catFocusIndex by remember { mutableStateOf<Int?>(null) }
    val categoriesModifier = Modifier
        .onFocusChanged {
            railPaneFocused = it.hasFocus
            if (it.hasFocus && previewEnabled) vm.stopPreview()
            // The sheet closes when focus leaves it (◀ to the rail) — not while its own menus are open,
            // and not on the "unfocused" report every node gets when it first attaches.
            if (it.hasFocus) sheetHadFocus = true
            else if (sheetHadFocus && categoriesOpen && contextCategory == null && categoryMoveState == null) {
                sheetHadFocus = false
                categoriesOpen = false
            }
        }
        .chNavPaging(
            enabled = chNavEnabled,
            upSkip = chNavUpSkip,
            downSkip = chNavDownSkip,
            isFocused = { railPaneFocused },
            lastIndex = { railItems.size - 1 },
            currentTargetIndex = { catFocusIndex ?: selectedIndex },
            onJumpToIndex = { idx -> catFocusIndex = idx; railFocusRow = idx },
        )
    val onCategorySelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { vm.select(it.key) }
        if (categoriesOpen) { categoriesOpen = false; focusChannelList() }
    }
    val onCategoryLongSelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { item ->
            // glcn: a group inside Favourites has its own menu (rename, delete).
            if (item.nested) groupMenuFor = item.title
            else if (item.key is LiveKey.Folder || item.key is LiveKey.Custom) {
                contextCategory = item
                contextCategoryKey = item.key
            }
        }
    }

    // Manual panel widths (Settings → Layout → Panel widths), mapped onto Stage: Category = the sheet
    // (Stage) or the column (Separate), List and Preview split the rest; Preview 0% = full-width list.
    val panelShares = rememberPanelShares(PanelSection.LIVE, settingsVm)
    // The Stage layout has its own set: the sheet on its own scale, list + preview = 100.
    val panelOn by settingsVm.panelWidthEnabled.getValue(PanelSection.LIVE).collectAsStateWithLifecycle()
    val stageSaved by settingsVm.liveStageWidths.collectAsStateWithLifecycle()
    val stageWidths = stageSaved.takeIf { panelOn && !separate }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        // Horizontal geometry is a fraction of the 1920 design width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val margin = fx(64)
        val row = screenW - margin * 2
        val previewShown = if (separate) panelShares?.preview != 0 else stageWidths?.preview != 0
        val liveHints = listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
            "◀" to stringResource(R.string.content_category_browser_title),
            "▶" to stringResource(R.string.content_key_schedule),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options),
        )
        val gapCat = fx(26)
        val gapStage = if (separate) fx(30) else fx(34)
        val colW: Dp
        val listW: Dp
        val stageW: Dp
        val sheetW: Dp
        if (separate && panelShares != null) {
            val p = computePanelWidths(panelShares, row, gapCat + if (previewShown) gapStage else 0.dp)
            colW = p.category; listW = p.list; stageW = p.preview; sheetW = 0.dp
        } else if (!separate && stageWidths != null) {
            // The sheet is a share of the screen; the row between the margins is list + preview.
            val rest = (row - if (previewShown) gapStage else 0.dp).coerceAtLeast(1.dp)
            colW = 0.dp
            sheetW = screenW * (stageWidths.sheet / 100f)
            listW = rest * (stageWidths.list / 100f)
            stageW = rest - listW
        } else {
            // The design's geometry.
            colW = fx(350); sheetW = fx(450)
            listW = if (separate) fx(620) else fx(846)
            stageW = if (separate) fx(766) else fx(912)
        }
        val listX = if (separate) margin + colW + gapCat else margin
        val listWidth = if (previewShown) listW else row - (listX - margin)

        // glcn: the programme's picture is the screen's own backdrop, from the bottom right corner —
        // nothing frames it: it fades into the page towards the left and the top, behind the text.
        if (!cinematic && lockedKey == null && previewShown) {
            val backdrop = nowNext?.takeIf { it.now != null }?.nowDetails?.imageUrl?.takeIf { it.isNotBlank() }
            if (backdrop != null) {
                val page = tv.own.owntv.ui.theme.LocalStagePalette.current.page
                // glcn: the whole picture, in the room under the preview (not a crop running up behind it):
                // as tall as that room, 16:9, against the right edge.
                val roomTop = 120.mpx + stageW * 9f / 16f
                val roomH = (maxHeight - roomTop).coerceAtLeast(1.dp)
                val artW = minOf(stageW + margin, roomH * 16f / 9f)
                Box(Modifier.align(Alignment.BottomEnd).width(artW).height(artW * 9f / 16f)) {
                    // glcn: dark text on a light page needs a calmer picture behind it (title, synopsis, key hints).
                    val light = page.luminance() > 0.5f
                    tv.own.owntv.ui.components.ProgrammeArt(url = backdrop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (light) 0.5f else 0.85f })
                    if (light) Box(Modifier.fillMaxSize().background(page.copy(alpha = 0.32f)))
                    // Soft at its left and top edges only, so it melts into the page and stays whole.
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to page, 0.3f to page.copy(alpha = 0f))))
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to page, 0.22f to page.copy(alpha = 0f))))
                    // …and, on a light page, towards the bottom where the key hints sit.
                    if (light) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.72f to page.copy(alpha = 0f), 1f to page.copy(alpha = 0.8f))))
                }
            }
        }

        if (separate) {
            LiveCategories(
                showPlaylists = true,
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = false,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = margin, top = 128.mpx, bottom = 24.mpx)
                    .width(colW)
                    .fillMaxHeight(),
            )
        }

        val targetChannelIdState = remember {
            androidx.compose.runtime.derivedStateOf {
                if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
            }
        }
        val targetChannelId by targetChannelIdState

        if (cinematic) {
            val toolsFocus = remember { FocusRequester() }
            LiveCinematicPane(
                channels = channels,
                listState = effectiveListState,
                previewChannel = previewChannel,
                nowNext = nowNext,
                previewEngine = vm.previewEngine,
                showVideo = effectivePreview,
                singleSessionBlocked = previewBlockedSingleSession,
                loadProgrammes = { list, from, to -> vm.cardProgrammesFor(list, from, to) },
                loadSimilar = { t, c, y, id, n -> vm.similarTo(t, c, y, id, n) },
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.mpx)) {
                        LiveHeader(category = headerLabel, count = count, showChevron = true, modifier = Modifier.weight(1f, fill = false))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.mpx),
                            modifier = Modifier.focusGroup().onPreviewKeyEvent { e ->
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                    e.key == androidx.compose.ui.input.key.Key.DirectionDown
                                ) { focusChannelList(); true } else false
                            },
                        ) {
                            if (lockedKey == null) tv.own.owntv.ui.stage.StageTool(
                                text = stringResource(R.string.content_category_browser_title),
                                icon = OwnTVIcon.FOLDER,
                                onClick = { categoriesOpen = true },
                                modifier = Modifier.focusRequester(toolsFocus),
                            )
                            tv.own.owntv.ui.stage.StageTool(
                                text = stringResource(R.string.content_live_guide_view),
                                icon = OwnTVIcon.GRID,
                                onClick = onOpenGuide,
                                modifier = if (lockedKey != null) Modifier.focusRequester(toolsFocus) else Modifier,
                            )
                            tv.own.owntv.ui.stage.StageTool(
                                text = null,
                                icon = OwnTVIcon.SORT,
                                value = if (sortMode == tv.own.owntv.core.settings.SettingsRepository.SortMode.ALPHA) stringResource(R.string.settings_sort_alpha) else stringResource(R.string.content_epg_sort_provider),
                                onClick = vm::toggleSort,
                            )
                            tv.own.owntv.ui.stage.StageTool(
                                text = stringResource(R.string.glcn_sports_now),
                                icon = OwnTVIcon.SPORTS,
                                onClick = { sportsOpen = true },
                            )
                        }
                    }
                },
                cardModifier = { index, channel ->
                    val isTarget = targetChannelId == channel.id
                    Modifier.gridFocusTarget(
                        itemId = channel.id, index = index,
                        contextId = contextChannelId, contextFocus = contextFocus,
                        selectedId = if (isTarget) channel.id else null, selectedFocus = selFocus,
                        firstItemFocus = firstItemFocus,
                    ).focusRequester(rowFocus(index)).onPreviewKeyEvent { e ->
                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            androidx.compose.ui.input.key.Key.Info -> { synopsisChannel = channel; true }
                            androidx.compose.ui.input.key.Key.Menu -> { if (selecting != null) batchActions = true else { contextChannel = channel; contextChannelId = channel.id }; true }
                            // ◀ one card back; from the first card on to the side menu — the categories open from the "Categories" button above.
                            androidx.compose.ui.input.key.Key.DirectionLeft -> (stepTarget ?: index).let { from ->
                                if (from > 0) { stepTo(from - 1); true } else false
                            }
                            androidx.compose.ui.input.key.Key.DirectionRight -> (stepTarget ?: index).let { from ->
                                from + 1 < channels.itemCount && run { stepTo(from + 1); true }
                            }
                            androidx.compose.ui.input.key.Key.DirectionUp -> { runCatching { toolsFocus.requestFocus() }; true }
                            androidx.compose.ui.input.key.Key.DirectionDown -> true
                            else -> false
                        }
                    }
                },
                onFocus = { channel ->
                    vm.onChannelFocused(channel)
                    if (rememberLive) perCategoryChannelIds[selectedKey] = channel.id
                },
                onClick = { channel ->
                    if (selecting != null) toggleSelected(channel)
                    else if (okOnly && effectivePreview && okChannelId != channel.id) okChannelId = channel.id
                    else {
                        vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                        if (!externalPlayerOn) onFullscreen()
                    }
                },
                onLongClick = { channel -> if (selecting != null) batchActions = true else { contextChannel = channel; contextChannelId = channel.id } },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (categoriesOpen) Modifier.graphicsLayer { alpha = 0.4f } else Modifier),
            )
        } else
        // The list side, dimmed to 40% while the sheet is over it (P3-02).
        Box(
            Modifier
                .fillMaxSize()
                .then(if (categoriesOpen) Modifier.graphicsLayer { alpha = 0.4f } else Modifier),
        ) {
            LiveHeader(
                category = headerLabel,
                count = count,
                showChevron = !separate && lockedKey == null,
                modifier = Modifier.padding(start = fx(84), top = 52.mpx).width(listX - fx(84) + listWidth),
            )
            // Header tools + channel list: one focus group.
            Column(
                modifier = Modifier
                    .padding(start = listX, top = 118.mpx)
                    .width(listWidth)
                    .fillMaxHeight()
                    .onFocusChanged { channelPaneFocused = it.hasFocus }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { channelPaneFocused },
                        longPressEnabled = { selectedKey != LiveKey.All },
                        lastIndex = { channels.itemCount - 1 },
                        currentTargetIndex = {
                            val pc = previewChannel
                            if (pc != null) {
                                val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == pc.id }
                                if (idx >= 0) idx else effectiveListState.firstVisibleItemIndex
                            } else {
                                effectiveListState.firstVisibleItemIndex
                            }
                        },
                        onJumpToIndex = { idx ->
                            val target = channels.itemSnapshotList.items.getOrNull(idx)?.id
                            scope.launch {
                                runCatching { effectiveListState.scrollToItem(idx) }
                                withFrameNanos { }
                                if (target != null) {
                                    val item = channels.itemSnapshotList.items.firstOrNull { it.id == target }
                                    if (item != null) {
                                        // The remembered channel is the focus target, so it moves with the jump;
                                        // focus by row number, which holds whatever the row's other requesters are.
                                        vm.onChannelFocused(item)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = item.id
                                        withFrameNanos { }
                                        runCatching { rowFocus(idx).requestFocus() }
                                    }
                                } else {
                                    runCatching { firstItemFocus.requestFocus() }
                                }
                            }
                        },
                    )
                    // Entering this pane lands on a channel row, never the search field.
                    .focusProperties {
                        onEnter = {
                            if (targetChannelId == null || !focusCurrentRow()) runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                    // Held Up/Down never escape the list; pinned (More) keeps Up for its tabs.
                    .focusProperties {
                        onExit = {
                            when (requestedFocusDirection) {
                                // The rows handle ◀ and ▶ themselves; from the tool row neither leaves the pane.
                                FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                                // glcn: ▲ from the tool row (search, categories, view, order…) is a deliberate move to the menu.
                                FocusDirection.Up, FocusDirection.Down ->
                                    if (lockedKey == null && !(requestedFocusDirection == FocusDirection.Up && toolRowHasFocus)) cancelFocusChange()
                                else -> Unit
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    modifier = Modifier.padding(start = 4.mpx).onFocusChanged { toolRowHasFocus = it.hasFocus }.focusGroup(),
                ) {
                    tv.own.owntv.ui.stage.StageSearchField(
                        query = searchQuery,
                        onQueryChange = vm::setSearchQuery,
                        onEditingChange = { searchEditing = it },
                        placeholder = if (separate) stringResource(R.string.common_search) else stringResource(R.string.content_search_in, headerLabel),
                        // The design's share of the list width, but never more than the two tools leave:
                        // a narrow custom list shrinks the field, not "Guide view".
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .width(if (separate) listWidth * (230f / 620f) else listWidth * (456f / 846f))
                            .focusRequester(listSearchFocus)
                            .onFocusChanged { if (it.hasFocus && previewEnabled) vm.stopPreview() }
                            // ◀ Categories from the search field too, as from the rows (not while typing — a search that found nothing here may be in another category).
                            .onPreviewKeyEvent { e ->
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                                    e.key == androidx.compose.ui.input.key.Key.DirectionLeft &&
                                    !searchEditing && lockedKey == null
                                ) {
                                    if (separate) { runCatching { railFocus.requestFocus() }; true } else false
                                } else false
                            },
                    )
                    tv.own.owntv.ui.stage.StageTool(
                        text = null,
                        icon = OwnTVIcon.SORT,
                        value = if (sortMode == tv.own.owntv.core.settings.SettingsRepository.SortMode.ALPHA) stringResource(R.string.settings_sort_alpha) else stringResource(R.string.content_epg_sort_provider),
                        onClick = vm::toggleSort,
                    )
                    if (!separate && lockedKey == null) tv.own.owntv.ui.stage.StageTool(
                        text = stringResource(R.string.content_category_browser_title),
                        icon = OwnTVIcon.FOLDER,
                        onClick = { categoriesOpen = true },
                    )
                    tv.own.owntv.ui.stage.StageTool(
                        text = stringResource(R.string.content_live_guide_view),
                        icon = OwnTVIcon.GRID,
                        onClick = onOpenGuide,
                    )
                    tv.own.owntv.ui.stage.StageTool(
                        text = stringResource(R.string.glcn_sports_now),
                        icon = OwnTVIcon.SPORTS,
                        onClick = { sportsOpen = true },
                    )
                }
                Spacer(Modifier.height(20.mpx))
                if (channels.itemCount == 0) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank()) stringResource(R.string.content_no_channels_found, searchQuery.trim()) else stringResource(R.string.content_no_channels_here),
                            style = tv.own.owntv.ui.theme.stageText(20, 500),
                            color = tv.own.owntv.ui.theme.StageColors.Muted,
                        )
                    }
                } else {
                    // The list clips whatever is drawn outside it, which cut the focused row's 44 px glow.
                    // It is laid out [glowRoom] wider at each side and padded back by the same amount,
                    // so the rows sit exactly where they did and the glow has room to show.
                    val glowRoom = 24.mpx
                    // Scroll only as far as the focused row needs to be on screen, as Home does: the TV
                    // default pins focus a third of the way down, so each ▼ moved the highlight a row
                    // and then slid the whole list back under it — a double step.
                    // glcn: the first rows of a category arrive one after another; later ones are just there.
                    val rowsGate = tv.own.owntv.glcn.rememberStaggerGate(selectedKey)
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyColumn(
                        state = effectiveListState,
                        verticalArrangement = Arrangement.spacedBy(8.mpx),
                        modifier = Modifier.fillMaxWidth().weight(1f).layout { measurable, constraints ->
                            val extra = glowRoom.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = glowRoom, end = glowRoom, bottom = 16.mpx),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            contentType = channels.itemContentType { "channel" },
                        ) { index ->
                            val channel = channels[index]
                            if (channel != null) {
                                val isPreviewed by remember(channel.id) {
                                    androidx.compose.runtime.derivedStateOf { previewChannel?.id == channel.id }
                                }
                                val isTarget by remember(channel.id) {
                                    androidx.compose.runtime.derivedStateOf { targetChannelIdState.value == channel.id }
                                }
                                val parsed = remember(channel.name) { ProviderTags.parse(channel.name) }
                                LiveStageRow(
                                    channel = channel,
                                    name = parsed,
                                    // The channel under the cursor answers from the preview itself, so the row
                                    // and the stage beside it can never disagree.
                                    now = if (isPreviewed && nowNextFor == channel.id) nowNext?.now ?: nowProgrammes[channel.id] else nowProgrammes[channel.id],
                                    nowTitle = nowPlaying[channel.id],
                                    isFavorite = favoriteIds.contains(channel.id),
                                    showNumber = showChannelNumbers,
                                    mark = playlistMarks[channel.sourceId],
                                    modifier = Modifier.staggerAt(index, rowsGate, step = 40, cap = 9).gridFocusTarget(
                                        itemId = channel.id, index = index,
                                        contextId = contextChannelId, contextFocus = contextFocus,
                                        selectedId = if (isTarget) channel.id else null, selectedFocus = selFocus,
                                        firstItemFocus = firstItemFocus,
                                    ).focusRequester(rowFocus(index)).onPreviewKeyEvent { e ->
                                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (e.key) {
                                            // The remote's Menu key opens the same menu as holding OK.
                                            androidx.compose.ui.input.key.Key.Info -> { synopsisChannel = channel; true }
                            androidx.compose.ui.input.key.Key.Menu -> { if (selecting != null) batchActions = true else { contextChannel = channel; contextChannelId = channel.id }; true }
                                            // ◀ Categories: the sheet (Stage) or the column (Separate); pinned lists have none.
                                            androidx.compose.ui.input.key.Key.DirectionLeft -> when {
                                                lockedKey != null -> false
                                                separate -> { runCatching { railFocus.requestFocus() }; true }
                                                else -> false // glcn: categories open from their button in the header; ◀ goes on to the side menu
                                            }
                                            // ▶ Schedule: next / later under the stage; nothing else to the right.
                                            androidx.compose.ui.input.key.Key.DirectionRight -> { runCatching { scheduleFocus.requestFocus() }; true }
                                            // ▲/▼ step by row number, not by focus search: a held key outran the
                                            // lazy list at the edge or a page boundary and stopped.
                                            androidx.compose.ui.input.key.Key.DirectionDown -> (stepTarget ?: index).let { from -> from + 1 < channels.itemCount && run { stepTo(from + 1); true } }
                                            androidx.compose.ui.input.key.Key.DirectionUp -> (stepTarget ?: index).let { from -> from > 0 && run { stepTo(from - 1); true } }
                                            else -> false
                                        }
                                    },
                                    onFocus = {
                                        vm.onChannelFocused(channel)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = channel.id
                                    },
                                    onClick = {
                                        if (selecting != null) toggleSelected(channel)
                                        else if (okOnly && effectivePreview && okChannelId != channel.id) okChannelId = channel.id
                                        else {
                                            vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                            if (!externalPlayerOn) onFullscreen()
                                        }
                                    },
                                    onLongClick = { if (selecting != null) batchActions = true else { contextChannel = channel; contextChannelId = channel.id } },
                                    trailing = selecting?.let { sel ->
                                        {
                                            androidx.compose.foundation.layout.Box(Modifier.size(26.mpx), contentAlignment = Alignment.Center) {
                                                if (sel.any { it.id == channel.id }) OwnTVIcon(OwnTVIcon.CHECK, tv.own.owntv.ui.theme.stageAccent.accent, Modifier.size(24.mpx))
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    }
                }
                // With the preview off the hints stay under the list, where there is nothing else to hold them.
                if (lockedKey == null && !previewShown) {
                    tv.own.owntv.ui.stage.StageKeyHints(
                        liveHints,
                        // A clear gap under the list, and 40 from the bottom rather than the design's 26:
                        // TV panels overscan the very edge away.
                        modifier = Modifier.padding(start = 16.mpx, top = 22.mpx, bottom = 40.mpx),
                    )
                }
            }

            // Under the preview, bottom right, so the list runs down to the bottom edge.
            if (lockedKey == null && previewShown) {
                tv.own.owntv.ui.stage.StageKeyHints(
                    liveHints,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = margin, bottom = 40.mpx),
                )
            }

            if (previewShown) {
                LiveStagePane(
                    channel = previewChannel,
                    channelName = previewChannel?.let { ProviderTags.parse(it.name).name },
                    nowNext = nowNext,
                    previewEngine = vm.previewEngine,
                    showVideo = effectivePreview,
                    singleSessionBlocked = previewBlockedSingleSession,
                    scheduleFocus = scheduleFocus,
                    onOpenProgramme = { p -> previewChannel?.let { openProgramme = it to p } },
                    onBackToList = { focusCurrentRow() },
                    modifier = Modifier
                        .padding(start = screenW - margin - stageW, top = 120.mpx)
                        .width(stageW),
                )
            }
        }

        if (sportsOpen) {
            SportsNowDialog(
                load = { vm.sportsNow() },
                onPick = { channel, list ->
                    vm.watchFullscreen(channel, list)
                    if (!externalPlayerOn) onFullscreen()
                },
                onDismiss = { sportsOpen = false },
            )
        }

        if (categoriesOpen) {
            LiveCategories(
                showPlaylists = true,
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = true,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { categoriesOpen = false; focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = fx(24), top = 24.mpx, bottom = 24.mpx)
                    .width(sheetW)
                    .fillMaxHeight(),
            )
        }
    }

    // A next / later programme opened from the schedule under the stage (▶ Schedule): the TV Guide's
    // programme menu, acting through the guide's view model so both screens do the same thing.
    openProgramme?.let { (ch, xt) ->
        val epgVm: tv.own.owntv.features.epg.EpgViewModel = koinViewModel()
        val leadMinutes by epgVm.reminderLeadMinutes.collectAsStateWithLifecycle()
        val p = remember(ch.id, xt.startMs) {
            tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                sourceId = ch.sourceId, epgChannelId = ch.epgChannelId.orEmpty(),
                startMs = xt.startMs, stopMs = xt.stopMs, title = xt.title, description = xt.description,
            )
        }
        val formatTime = rememberSystemTimeFormatter()
        tv.own.owntv.features.epg.GuideProgrammeMenu(
            title = p.title,
            subtitle = listOf(
                stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)),
                listOfNotNull(ch.number?.toString(), ProviderTags.parse(ch.name).name).joinToString(" "),
            ).joinToString(" · "),
            actions = tv.own.owntv.features.epg.guideProgrammeActions(
                epgVm, ch, p, System.currentTimeMillis(),
                onWatch = { vm.watchFullscreen(ch, emptyList()); if (!externalPlayerOn) onFullscreen() },
                onPlayFromStart = { vm.playCatchupProgramme(ch, p); if (!externalPlayerOn) onFullscreen() },
                onPickEpg = { contextChannelId = ch.id; matchingEpg = ch },
                onEpgOffset = { contextChannelId = ch.id; offsettingEpg = ch },
                autoMatch = false,
            ),
            leadMinutes = leadMinutes,
            onDismiss = { openProgramme = null; runCatching { scheduleFocus.requestFocus() } },
        )
    }
    catchupChannel?.let { ch ->
        CatchupDialog(
            channelName = ch.name,
            loadProgrammes = { vm.catchupProgrammes(ch) },
            onPick = { prog -> catchupChannel = null; catchupDetail = ch to prog },
            jumpOffsetsSec = remember(ch.id) { vm.catchupJumpOptions(ch) },
            jumpWindowSec = remember(ch.id) { vm.catchupWindowOf(ch) },
            onJump = { offset ->
                catchupChannel = null
                vm.playCatchupAt(ch, offset)
                if (!externalPlayerOn) onFullscreen()
            },
            onDismiss = { catchupChannel = null },
        )
    }

    // Same dialog the Guide shows for a programme, so both routes offer the identical choice:
    // replay from the start, tune the channel live, favourite it, or back out.
    catchupDetail?.let { (ch, prog) ->
        tv.own.owntv.features.epg.ProgrammeDetailDialog(
            channelName = ch.name,
            programme = prog,
            loadDescription = { vm.programmeDescription(it) },
            canCatchup = true, // only reachable from the catch-up picker, which already gated on this
            isFavorite = favoriteIds.contains(ch.id),
            onToggleFavorite = { vm.toggleFavorite(ch) },
            onWatch = { catchupDetail = null; vm.watchFullscreen(ch, emptyList()); if (!externalPlayerOn) onFullscreen() },
            onPlayCatchup = { catchupDetail = null; vm.playCatchupProgramme(ch, prog); onFullscreen() },
            // External: the archive went to another app, so don't mount the fullscreen player over it.
            onPlayCatchupExternal = { catchupDetail = null; vm.playCatchupExternal(ch, prog) },
            catchupPlayer = catchupPlayer,
            onDismiss = { catchupDetail = null },
            compact = true,
        )
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = stringResource(R.string.content_rename_channel),
            initial = ch.name,
            hint = stringResource(R.string.content_rename_hint),
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    matchingEpg?.let { ch ->
        EpgMatchDialog(
            channelName = ch.name,
            currentMatch = vm.currentEpgMatch(ch),
            loadChannels = { q -> vm.availableEpgChannels(ch.name, q) },
            onPick = { epgId -> vm.setEpgMatch(ch, epgId); matchingEpg = null },
            onClear = { vm.setEpgMatch(ch, null); matchingEpg = null },
            onDismiss = { matchingEpg = null },
        )
    }

    offsettingEpg?.let { ch ->
        EpgOffsetDialog(
            channelName = ch.name,
            currentMinutes = vm.currentEpgShift(ch),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(ch, it) },
            onDismiss = { offsettingEpg = null },
        )
    }

    synopsisChannel?.let { ch -> SynopsisPopup(ch, load = { vm.nowFull(it) }, onDismiss = { synopsisChannel = null }) }

    // glcn: groups inside Favourites — "Move to group", a new group's name, a group's own menu.
    val inFavoritesArea = selectedKey == LiveKey.Favorites ||
        (selectedKey as? LiveKey.Custom)?.id?.let { tv.own.owntv.core.live.FavoriteGroups.isGroup(it) } == true
    groupingChannels?.let { chs ->
        val groups by vm.favoriteGroupNames.collectAsStateWithLifecycle()
        val current by produceState<String?>(null, chs) { value = chs.singleOrNull()?.let { vm.favoriteGroupOf(it) } }
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { groupingChannels = null },
            title = stringResource(R.string.glcn_favgroup_move),
            body = chs.singleOrNull()?.name,
            width = 620.mpx,
        ) {
            groups.forEach { g ->
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = g, chosen = g == current,
                    onClick = { vm.moveToFavoriteGroup(chs, g); groupingChannels = null; selecting = null },
                )
            }
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.glcn_favgroup_new),
                onClick = { namingGroupFor = chs; groupingChannels = null },
            )
            if (current != null || chs.size > 1) tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.glcn_favgroup_none),
                onClick = { vm.moveToFavoriteGroup(chs, null); groupingChannels = null; selecting = null },
            )
        }
    }
    namingGroupFor?.let { chs ->
        TextInputDialog(
            title = stringResource(R.string.glcn_favgroup_new),
            hint = stringResource(R.string.glcn_favgroup_name_hint),
            onConfirm = { name -> if (name.isNotBlank()) vm.moveToFavoriteGroup(chs, name.trim()); namingGroupFor = null; selecting = null },
            onDismiss = { namingGroupFor = null },
        )
    }
    groupMenuFor?.let { g ->
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { groupMenuFor = null },
            title = g,
            body = stringResource(R.string.glcn_favgroup_delete_note),
            width = 620.mpx,
        ) {
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.glcn_favgroup_rename),
                onClick = { renamingGroup = g; groupMenuFor = null },
            )
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.glcn_favgroup_delete), danger = true,
                onClick = { vm.deleteFavoriteGroup(g); groupMenuFor = null },
            )
        }
    }
    renamingGroup?.let { g ->
        TextInputDialog(
            title = stringResource(R.string.glcn_favgroup_rename),
            initial = g,
            hint = stringResource(R.string.glcn_favgroup_name_hint),
            onConfirm = { name -> if (name.isNotBlank()) vm.renameFavoriteGroup(g, name.trim()); renamingGroup = null },
            onDismiss = { renamingGroup = null },
        )
    }

    // Long-press a channel → quick actions.
    contextChannel?.let { ch ->
        ChannelContextMenu(
            channel = ch,
            title = ch.name,
            subtitle = listOfNotNull(headerLabel, vm.sourceNameOf(ch.sourceId)).joinToString(" · "),
            isFavorite = favoriteIds.contains(ch.id),
            epgMatchManual = vm.currentEpgMatch(ch) != null,
            epgShiftMinutes = vm.currentEpgShift(ch),
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            onSynopsis = { synopsisChannel = ch; contextChannel = null },
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onMatchEpg = { matchingEpg = ch; contextChannel = null },
            onEpgOffset = { offsettingEpg = ch; contextChannel = null },
            onCatchup = { catchupChannel = ch; contextChannel = null },
            isRecording = ch.id in recordingChannelIds,
            onRecord = { if (ch.id in recordingChannelIds) vm.stopRecordingOn(ch) else vm.recordNow(ch); contextChannel = null },
            onScheduleRecord = { schedulingChannel = ch; contextChannel = null },
            onPlayExternal = { vm.playExternal(ch); contextChannel = null },
            // Only offered once Multiview is switched on. Adding is silent apart from the toast: the
            // grid opens when the user plays a channel, which is the gesture that says "now".
            onAddToMultiview = if (multiviewEnabled) {
                {
                    vm.addToMultiview(ch, multiviewTiles)
                    multiviewToast.show(
                        multiviewRes.getString(
                            R.string.multiview_added,
                            vm.multiviewSelection.value.size,
                            multiviewTiles,
                        ),
                    )
                    contextChannel = null
                }
            } else {
                null
            },
            onMove = { contextChannel = null; enteringMoveMode = true; vm.enterMoveMode(ch, selectedKey) },
            onMoveToCategory = {
                moveOriginKey = when (val k = selectedKey) {
                    is LiveKey.Folder -> vm.folderKey(k.id)
                    is LiveKey.Custom -> k.id
                    LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                    else -> null
                }
                moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                moveItem = ch
                contextChannel = null
            },
            onRemoveFromHistory = { vm.removeFromHistory(ch.id); contextChannel = null },
            onSelectMany = if (cinematic) null else { { selecting = listOf(ch); contextChannel = null } },
            onRemoveFromCategory = (selectedKey as? LiveKey.Custom)?.let { k -> { vm.removeFromCustomCategory(ch, k); contextChannel = null } },
            onDismiss = { contextChannel = null },
            onMoveToGroup = if (inFavoritesArea) ({ groupingChannels = listOf(ch); contextChannel = null }) else null,
        )
    }

    // glcn: actions for the marked channels.
    if (batchActions) {
        val sel = selecting.orEmpty()
        val inFavs = selectedKey == LiveKey.Favorites
        val inCustom = selectedKey as? LiveKey.Custom
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { batchActions = false },
            title = stringResource(R.string.glcn_selected_count, sel.size),
            width = 620.mpx,
        ) {
            if (sel.isNotEmpty()) {
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.content_move_to_category),
                    onClick = {
                        moveOriginKey = when (val k = selectedKey) {
                            is LiveKey.Folder -> vm.folderKey(k.id)
                            is LiveKey.Custom -> k.id
                            LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                            else -> null
                        }
                        moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                        batchActions = false
                        batchMove = true
                    },
                )
                if (inFavoritesArea) tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.glcn_favgroup_move),
                    onClick = { groupingChannels = sel; batchActions = false },
                )
                if (!inFavs) tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.content_add_favourite),
                    onClick = { vm.setFavorites(sel, true); batchActions = false; selecting = null },
                )
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.content_remove_favourite),
                    onClick = { vm.setFavorites(sel, false); batchActions = false; selecting = null },
                )
                if (inCustom != null) tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.content_remove_from_category),
                    onClick = { vm.removeManyFromCustomCategory(sel, inCustom); batchActions = false; selecting = null },
                )
            }
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.glcn_select_done),
                onClick = { batchActions = false; selecting = null },
            )
        }
    }

    val scheduledDay = tv.own.owntv.ui.format.rememberBestDateFormatter("EEEMMMd")
    val scheduledTime = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    schedulingChannel?.let { ch ->
        ScheduleRecordingDialog(
            channelName = ch.name,
            clashesFor = { start, stop -> vm.recordingClashes(ch, start, stop) },
            onSchedule = { start, stop ->
                schedulingChannel = null
                vm.scheduleRecording(ch, start, stop) { at ->
                    multiviewToast.show(multiviewRes.getString(R.string.recording_scheduled_for, scheduledDay(at) + " " + scheduledTime(at)))
                }
            },
            onDismiss = { schedulingChannel = null },
        )
    }

    tv.own.owntv.ui.components.InAppToast(multiviewToast)

    // Move to… a combined category, incl. the "＋ New category…" name prompt.
    val moveTargets by vm.moveTargets.collectAsStateWithLifecycle()
    if (creatingCategory) {
        TextInputDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            hint = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    } else {
        if (batchMove) {
            val originKey = moveOriginKey
            val sel = selecting.orEmpty()
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveManyToCategory(sel, originKey, targetId, keepInOrigin)
                        batchMove = false
                        selecting = null
                    },
                    onDismiss = { batchMove = false; batchActions = true },
                )
            } else batchMove = false
        }
        moveItem?.let { ch ->
            val originKey = moveOriginKey
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveToCategory(CustomizeKeys.channel(ch), ch.id, originKey, targetId, keepInOrigin)
                        moveItem = null
                    },
                    onDismiss = { moveItem = null },
                )
            }
        }
    }

    // Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_reorder_channel),
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMove = vm::move,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }

    // Category Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    categoryMoveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_move),
            itemNames = ms.items,
            activeIndex = ms.activeIndex,
            onMove = vm::moveCategory,
            onCommit = vm::commitCategoryMove,
            onCancel = vm::cancelCategoryMove,
        )
    }

    // Gone in the same frame the move list opens, so two popups never hold focus at once.
    LaunchedEffect(categoryMoveState != null) { if (categoryMoveState != null) contextCategory = null }
    contextCategory?.takeIf { categoryMoveState == null }?.let { item ->
        val restorePlan by produceState<CategoryRestorePlan?>(null, item.key) { value = vm.categoryRestorePlan(item.key) }
        CategoryContextMenu(
            categoryName = item.displayLabel(),
            canHide = item.key is LiveKey.Folder || item.key is LiveKey.Custom,
            canMove = item.key is LiveKey.Folder || item.key is LiveKey.Custom,
            onHide = { vm.hideCategory(item.key); contextCategory = null },
            // The menu stays up until the move list is loaded, then hands over to it.
            onMove = { vm.enterCategoryMoveMode(item.key) },
            // Always offered: the plan is read in the background and only fills in the confirmation.
            resetLabel = stringResource(if (item.key is LiveKey.Custom) R.string.content_category_restore_custom else R.string.content_category_restore_playlist),
            resetLines = restorePlan?.let { categoryRestoreSummary(it, androidx.compose.ui.platform.LocalContext.current.resources) },
            onReset = { vm.restoreCategory(item.key); contextCategory = null },
            onDismiss = { contextCategory = null }
        )
    }
}

/**
 * glcn: the channel options menu over the player (hold OK on a card in the picture strip): the same menu as Live TV,
 * with its dialogs, minus what only makes sense in a list (move, categories, multi-select, Multiview). Catch-up plays
 * in the player already on screen.
 */
@Composable
fun PlayerChannelOptions(vm: LiveViewModel, channel: ChannelEntity, onDismiss: () -> Unit) {
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val recordingChannelIds by vm.recordingChannelIds.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(true) }
    var synopsis by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var matching by remember { mutableStateOf(false) }
    var offsetting by remember { mutableStateOf(false) }
    var catchup by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    val toast = tv.own.owntv.ui.components.rememberInAppToast()
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val day = tv.own.owntv.ui.format.rememberBestDateFormatter("EEEMMMd")
    val time = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    val ch = channel
    // A scheduled recording says so for a moment before the menu's host goes.
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(confirmed) { if (confirmed) { kotlinx.coroutines.delay(2_500); onDismiss() } }
    // A dialog closing ends it all: back to the strip, not to the menu.
    fun done() { onDismiss() }
    val onAir by androidx.compose.runtime.produceState<tv.own.owntv.core.database.entity.EpgProgrammeEntity?>(null, ch.id) {
        value = runCatching { vm.nowFull(ch).first }.getOrNull()
    }
    if (menu) ChannelContextMenu(
        overPlayer = true,
        onAir = onAir,
        channel = ch,
        title = ch.name,
        subtitle = vm.sourceNameOf(ch.sourceId),
        isFavorite = favoriteIds.contains(ch.id),
        canMove = false,
        isHistory = false,
        epgMatchManual = vm.currentEpgMatch(ch) != null,
        epgShiftMinutes = vm.currentEpgShift(ch),
        onSynopsis = { menu = false; synopsis = true },
        onToggleFavorite = { vm.toggleFavorite(ch); done() },
        onRename = { menu = false; renaming = true },
        onHide = { vm.hideChannel(ch); done() },
        onMatchEpg = { menu = false; matching = true },
        onEpgOffset = { menu = false; offsetting = true },
        onCatchup = { menu = false; catchup = true },
        isRecording = ch.id in recordingChannelIds,
        onRecord = { if (ch.id in recordingChannelIds) vm.stopRecordingOn(ch) else vm.recordNow(ch); done() },
        onScheduleRecord = { menu = false; scheduling = true },
        onPlayExternal = { vm.playExternal(ch); done() },
        onAddToMultiview = null,
        onMove = {},
        onMoveToCategory = {},
        onRemoveFromHistory = {},
        onSelectMany = null,
        onRemoveFromCategory = null,
        onDismiss = { done() },
    )
    if (synopsis) SynopsisPopup(ch, load = { vm.nowFull(it) }, onDismiss = { done() })
    if (renaming) TextInputDialog(
        title = stringResource(R.string.content_rename_channel),
        initial = ch.name,
        hint = stringResource(R.string.content_rename_hint),
        onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); done() },
        onDismiss = { done() },
    )
    if (matching) EpgMatchDialog(
        channelName = ch.name,
        currentMatch = vm.currentEpgMatch(ch),
        loadChannels = { q -> vm.availableEpgChannels(ch.name, q) },
        onPick = { epgId -> vm.setEpgMatch(ch, epgId); done() },
        onClear = { vm.setEpgMatch(ch, null); done() },
        onDismiss = { done() },
    )
    if (offsetting) EpgOffsetDialog(
        channelName = ch.name,
        currentMinutes = vm.currentEpgShift(ch),
        globalMinutes = vm.globalEpgShift(),
        onSet = { vm.setEpgShift(ch, it) },
        onDismiss = { done() },
    )
    if (catchup) CatchupDialog(
        channelName = ch.name,
        loadProgrammes = { vm.catchupProgrammes(ch) },
        onPick = { prog -> vm.playCatchupProgramme(ch, prog); done() },
        jumpOffsetsSec = remember(ch.id) { vm.catchupJumpOptions(ch) },
        jumpWindowSec = remember(ch.id) { vm.catchupWindowOf(ch) },
        onJump = { offset -> vm.playCatchupAt(ch, offset); done() },
        onDismiss = { done() },
    )
    if (scheduling) ScheduleRecordingDialog(
        channelName = ch.name,
        clashesFor = { start, stop -> vm.recordingClashes(ch, start, stop) },
        onSchedule = { start, stop ->
            scheduling = false
            confirmed = true
            vm.scheduleRecording(ch, start, stop) { at -> toast.show(res.getString(R.string.recording_scheduled_for, day(at) + " " + time(at))) }
        },
        onDismiss = { done() },
    )
    tv.own.owntv.ui.components.InAppToast(toast)
}

/**
 * glcn: the channel's options over the player. A sheet slides in from the right on a share of the stage glass, so
 * the picture stays in view; a soft shade on that side keeps the words readable over any frame. At its head, what
 * the channel shows now: its picture, title, times and progress, then the channel; under it the menu's groups.
 */
@Composable
private fun PlayerOptionsSheet(
    channel: ChannelEntity,
    subtitle: String?,
    onAir: tv.own.owntv.core.database.entity.EpgProgrammeEntity?,
    onDismiss: () -> Unit,
    items: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(320)
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    val shade = tv.own.owntv.ui.theme.stageTone(androidx.compose.ui.graphics.Color(2, 5, 6), androidx.compose.ui.graphics.Color(244, 247, 245))
    val accent = tv.own.owntv.ui.theme.stageAccent.accent
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize().longPressMenuGuard()
                .graphicsLayer { alpha = enter.value }
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.45f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to shade.copy(alpha = 0.45f),
                    ),
                ),
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 48.mpx, top = 40.mpx, bottom = 40.mpx)
                    .width(600.mpx).fillMaxHeight()
                    .graphicsLayer { translationX = (1f - enter.value) * 80.mpx.toPx() },
            ) {
                // A share of the glass: the picture shows through, the list still reads — as see-through as the quick
                // view's panels (ZapDeck), so the two read as one family.
                Box(Modifier.matchParentSize().graphicsLayer { alpha = PLAYER_MENU_GLASS }.stageGlass(tv.own.owntv.ui.theme.StageRadii.Sheet, overContent = true))
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).trapAllFocusExit().focusGroup()
                        .padding(start = 18.mpx, end = 18.mpx, top = 18.mpx, bottom = 16.mpx),
                ) {
                    val art = onAir?.imageUrl?.takeIf { it.isNotBlank() }
                    if (art != null && onAir != null) Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.mpx)),
                    ) {
                        coil3.compose.AsyncImage(model = art, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.45f to androidx.compose.ui.graphics.Color.Transparent,
                                    1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                                ),
                            ),
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(16.mpx)) {
                            Text(onAir.title, style = stageText(24, 800), color = androidx.compose.ui.graphics.Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            OnAirLine(onAir, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f), accent)
                        }
                    } else if (onAir != null) Column(Modifier.padding(horizontal = 12.mpx)) {
                        Text(onAir.title, style = stageText(24, 800), color = tv.own.owntv.ui.theme.StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        OnAirLine(onAir, tv.own.owntv.ui.theme.StageColors.Muted, accent)
                    }
                    Spacer(Modifier.height(16.mpx))
                    tv.own.owntv.ui.stage.StageMenuHeader(
                        title = listOfNotNull(channel.number?.toString(), ProviderTags.parse(channel.name).name).joinToString(" · "),
                        subtitle = subtitle,
                        leading = { LivePlate(channel.displayLogoUrl, 70.mpx, 50.mpx) },
                    )
                    items()
                }
            }
        }
    }
}

/** glcn: "20:00 – 21:00" and a thin progress line, for the programme at the sheet's head. */
@Composable
private fun OnAirLine(p: tv.own.owntv.core.database.entity.EpgProgrammeEntity, color: androidx.compose.ui.graphics.Color, accent: androidx.compose.ui.graphics.Color) {
    val time = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    Text(
        stringResource(R.string.content_live_time_range, time(p.startMs), time(p.stopMs)),
        style = stageText(16, 600), color = color, maxLines = 1,
        modifier = Modifier.padding(top = 4.mpx, bottom = 8.mpx),
    )
    val now = System.currentTimeMillis()
    val f = ((now - p.startMs).toFloat() / (p.stopMs - p.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    Box(Modifier.width(260.mpx).height(4.mpx).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.mpx)).background(color.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(f).height(4.mpx).background(accent))
    }
}

/**
 * ☰ Channel options: the Stage options menu, 540 wide over the stage, above the scrim. Header
 * = the logo plate, "256 · Sky Cinema Premieren" and "category · playlist"; the actions in the groups
 * WATCH · CHANNEL · GUIDE DATA · ORGANISE with their values (Catch-up "7 days", Match EPG "Auto",
 * EPG time offset "Global"). The order and visibility are the user's (Settings › Long-press menus ›
 * Live TV); a group label is drawn wherever the group changes.
 */
@Composable
private fun ChannelContextMenu(
    channel: ChannelEntity,
    title: String,
    subtitle: String?,
    isFavorite: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    epgMatchManual: Boolean,
    epgShiftMinutes: Int?,
    onSynopsis: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
    onEpgOffset: () -> Unit,
    onCatchup: () -> Unit,
    // A recording of this channel is running: Record becomes Stop recording.
    isRecording: Boolean,
    onRecord: () -> Unit,
    onScheduleRecord: () -> Unit,
    onPlayExternal: () -> Unit,
    // Null unless Multiview is switched on: keep this channel for the grid.
    onAddToMultiview: (() -> Unit)?,
    onMove: () -> Unit,
    // "Move to category…": send this channel into a user's combined category.
    onMoveToCategory: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    // glcn: start multi-select (null where the layout can't show marks).
    onSelectMany: (() -> Unit)?,
    // Only inside a custom category: take the channel out of that category alone.
    onRemoveFromCategory: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** glcn: in Favourites or one of its groups — put the channel in a group. */
    onMoveToGroup: (() -> Unit)? = null,
    /** glcn: over the player — a see-through sheet on the right with the programme on air at its head, the picture left free. */
    overPlayer: Boolean = false,
    onAir: tv.own.owntv.core.database.entity.EpgProgrammeEntity? = null,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    val catchupValue = channel.catchupDays.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.settings_epg_guide_days_value, it, it) }
    val matchValue = if (epgMatchManual) stringResource(R.string.settings_manual) else stringResource(R.string.settings_auto)
    val offsetValue = epgShiftMinutes?.let { liveEpgShiftLabel(it) } ?: stringResource(R.string.content_epg_offset_global_short)
    // Group ids = the four groups, in their order.
    val watch = 0; val chan = 1; val guide = 2; val organise = 3
    val actions = buildList {
        add(MenuAction("synopsis", stringResource(R.string.glcn_synopsis_full), OwnTVIcon.INFO, group = watch, onClick = onSynopsis))
        if (channel.catchup) add(MenuAction("catchup", stringResource(R.string.content_catchup), OwnTVIcon.REWIND, group = watch, onClick = onCatchup))
        // Record this channel from now. The guide's Record needs a programme, so a channel
        // the provider publishes no guide for can only be recorded from here. While it records, the
        // same row stops it — pressing Record again would start a second copy on another connection.
        add(MenuAction("record", stringResource(if (isRecording) R.string.recording_stop else R.string.recording_record), OwnTVIcon.REC, group = watch, onClick = onRecord))
        add(MenuAction("schedule_record", stringResource(R.string.recording_schedule), OwnTVIcon.CLOCK, group = watch, onClick = onScheduleRecord))
        if (onAddToMultiview != null) {
            add(MenuAction("add_to_multiview", stringResource(R.string.multiview_add_to), OwnTVIcon.MULTIVIEW, group = watch, onClick = onAddToMultiview))
        }
        // Always offered, regardless of the Live TV external-player default — this is the per-channel
        // escape hatch for a stream neither in-app engine can open (same as Movies/Series/Downloads).
        add(MenuAction("play_external", stringResource(R.string.content_play_external_short), OwnTVIcon.EXTERNAL, group = watch, onClick = onPlayExternal))
        add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), OwnTVIcon.FAVORITE, group = chan, onClick = onToggleFavorite))
        add(MenuAction("rename", stringResource(R.string.content_rename), OwnTVIcon.PENCIL, group = chan, onClick = onRename))
        add(MenuAction("hide", stringResource(R.string.content_hide_channel), OwnTVIcon.EYE_OFF, group = chan, onClick = onHide))
        if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), OwnTVIcon.HISTORY, group = chan, onClick = onRemoveFromHistory))
        add(MenuAction("match_epg", stringResource(R.string.content_match_epg), OwnTVIcon.EPG, group = guide, onClick = onMatchEpg))
        add(MenuAction("epg_offset", stringResource(R.string.content_epg_time_offset), OwnTVIcon.CLOCK, group = guide, onClick = onEpgOffset))
        if (canMove) {
            add(MenuAction("move", stringResource(R.string.content_move), OwnTVIcon.MOVE, group = organise, onClick = onMove))
            add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), OwnTVIcon.FOLDER, group = organise, onClick = onMoveToCategory))
        }
        if (canMove && onSelectMany != null) {
            add(MenuAction("select_many", stringResource(R.string.glcn_select_many), OwnTVIcon.CHECK, group = organise, onClick = onSelectMany))
        }
        onMoveToGroup?.let { add(MenuAction("move_to_group", stringResource(R.string.glcn_favgroup_move), OwnTVIcon.FOLDER, group = organise, onClick = it)) }
        if (onRemoveFromCategory != null) {
            add(MenuAction("remove_from_category", stringResource(R.string.content_remove_from_category), OwnTVIcon.CLOSE, group = organise, onClick = onRemoveFromCategory))
        }
    }
    val values = mapOf("catchup" to catchupValue, "match_epg" to matchValue, "epg_offset" to offsetValue)
    val groupLabels = listOf(
        stringResource(R.string.content_menu_group_watch),
        stringResource(R.string.content_menu_group_channel),
        stringResource(R.string.content_menu_group_guide_data),
        stringResource(R.string.content_menu_group_organise),
    ).map { it.uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale) }
    if (overPlayer) {
        PlayerOptionsSheet(
            channel = channel, subtitle = subtitle, onAir = onAir, onDismiss = onDismiss,
        ) {
            var previousGroup: Int? = null
            arranged(ContentMenu.LIVE, actions).forEachIndexed { index, action ->
                if (action.group != previousGroup) tv.own.owntv.ui.stage.StageGroupLabel(groupLabels[action.group])
                previousGroup = action.group
                tv.own.owntv.ui.stage.StageMenuItem(
                    text = action.label,
                    icon = action.icon,
                    iconFilled = action.icon == OwnTVIcon.REC,
                    value = values[action.key],
                    onClick = action.onClick,
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
        return
    }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
        ) {
            val w = maxWidth
            tv.own.owntv.ui.stage.StageMenu(
                Modifier
                    .padding(start = w * (930f / 1920f), top = 118.mpx, bottom = 24.mpx)
                    .width(540.mpx)
                    .verticalScroll(rememberScrollState())
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                tv.own.owntv.ui.stage.StageMenuHeader(
                    title = listOfNotNull(channel.number?.toString(), ProviderTags.parse(channel.name).name).joinToString(" · "),
                    subtitle = subtitle,
                    leading = {
                        LivePlate(channel.displayLogoUrl, 70.mpx, 50.mpx)
                    },
                )
                var previousGroup: Int? = null
                arranged(ContentMenu.LIVE, actions).forEachIndexed { index, action ->
                    if (action.group != previousGroup) tv.own.owntv.ui.stage.StageGroupLabel(groupLabels[action.group])
                    previousGroup = action.group
                    tv.own.owntv.ui.stage.StageMenuItem(
                        text = action.label,
                        icon = action.icon,
                        iconFilled = action.icon == OwnTVIcon.REC,
                        value = values[action.key],
                        onClick = action.onClick,
                        modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun formatCatchupTime(
    startMs: Long,
    stopMs: Long,
    formatTime: (Long) -> String,
): String {
    val formatDay = rememberBestDateFormatter("EEE")
    val day = formatDay(startMs)
    return stringResource(R.string.content_live_day_time_range, day, formatTime(startMs), formatTime(stopMs))
}

/** Live TV catch-up: pick a recent (already-aired) programme on a catch-up channel to replay from start. */
@Composable
private fun CatchupDialog(
    channelName: String,
    loadProgrammes: suspend () -> List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>,
    onPick: (tv.own.owntv.core.database.entity.EpgProgrammeEntity) -> Unit,
    // Fallback for a channel with an archive but no guide: there are no programmes to name, but the
    // archive is still there, so offer times instead of a dead-end "go match your EPG" message.
    jumpOffsetsSec: List<Int>,
    jumpWindowSec: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatTime = rememberSystemTimeFormatter()
    val list by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>?>(initialValue = null) {
        value = runCatching { loadProgrammes() }.getOrDefault(emptyList())
    }
    // "Choose exact time…" opens on top of this dialog, same as the player's route into it.
    var manualTime by remember { mutableStateOf(false) }
    if (manualTime) {
        CatchupManualTimeDialog(
            windowSec = jumpWindowSec,
            onPick = { manualTime = false; onJump(it) },
            onDismiss = { manualTime = false },
        )
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        // Still loading, or nothing focusable at all (no programmes AND no archive to jump into).
        if (list == null || (list!!.isEmpty() && jumpOffsetsSec.isEmpty())) return@LaunchedEffect
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    val noGuide = list?.isEmpty() == true && jumpOffsetsSec.isNotEmpty()
    // A Stage popup: its own window, so nothing behind can take focus (as EpgMatchDialog / the menus).
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_catchup_title, channelName),
        body = stringResource(if (noGuide) R.string.content_catchup_jump_prompt else R.string.content_catchup_prompt),
        eyebrow = null,
        scroll = false,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        when (val progs = list) {
            null -> Box(Modifier.fillMaxWidth().height(100.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
            else -> if (progs.isEmpty()) {
                // No guide for this channel. The archive still exists, so offer times to jump to; only
                // fall back to the "match your EPG" note when there is no archive window either.
                if (jumpOffsetsSec.isNotEmpty()) {
                    CatchupJumpRows(
                        offsetsSec = jumpOffsetsSec,
                        firstFocus = firstFocus,
                        onPick = onJump,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        onChooseExact = { manualTime = true },
                    )
                } else {
                    Text(stringResource(R.string.content_catchup_empty), style = tv.own.owntv.ui.theme.stageText(18, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                    items(progs, key = { it.id }) { p ->
                        tv.own.owntv.ui.stage.StagePopupOption(
                            title = p.title, subtitle = formatCatchupTime(p.startMs, p.stopMs, formatTime), onClick = { onPick(p) },
                            modifier = if (p == progs.first()) Modifier.focusRequester(firstFocus) else Modifier,
                            leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.CATCHUP) },
                        )
                    }
                }
            }
        }
    }
}

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.epg.GuideCandidate>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.epg.GuideCandidate>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    // The Stage picker: a menu panel over the scrim, focus trapped inside, the list capped so Close stays reachable.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.StageColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(Modifier.width(640.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.owntv.ui.stage.StageMenuHeader(
                    title = stringResource(R.string.content_match_epg),
                    subtitle = if (currentMatch != null) {
                        stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
                    } else {
                        stringResource(R.string.content_epg_match_prompt, channelName)
                    },
                )
                tv.own.owntv.ui.stage.StageSearchField(
                    query = query, onQueryChange = { query = it },
                    placeholder = stringResource(R.string.content_search_guide_channels).trimEnd('…'),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.mpx).focusRequester(searchFocus),
                )
                Spacer(Modifier.height(12.mpx))
                val list = results
                when {
                    list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 24) }
                    list.isEmpty() -> Text(
                        if (query.isBlank()) stringResource(R.string.content_no_epg_data) else stringResource(R.string.content_no_guide_channels, query),
                        style = tv.own.owntv.ui.theme.stageText(18, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
                        modifier = Modifier.padding(horizontal = 14.mpx, vertical = 12.mpx),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().height(400.mpx)) {
                        items(list, key = { it.epgChannelId }) { epg ->
                            tv.own.owntv.ui.stage.StageMenuItem(
                                text = epg.displayName ?: epg.epgChannelId,
                                value = epg.epgChannelId.takeIf { epg.displayName != null },
                                checked = epg.epgChannelId == currentMatch,
                                onClick = { onPick(epg.epgChannelId) },
                                modifier = if (epg == list.first()) Modifier.focusRequester(firstItemFocus) else Modifier,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.mpx),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End),
                ) {
                    if (currentMatch != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_clear_match), onClick = onClear, height = 56.mpx, textSize = 19)
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true)
                }
            }
        }
    }
}

/**
 * Per-channel EPG time offset. Providers often hang both the East and the West stream of a network
 * off ONE guide, so one of them runs hours out; this moves that channel's guide only. Shared with the
 * Guide screen (long-press a channel → EPG offset). The change is written on "Done", so stepping
 * through a few hours is a single edit.
 */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { valueFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_epg_time_offset),
        body = stringResource(R.string.content_epg_offset_channel_description, channelName),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            if (currentMinutes != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_epg_offset_use_global), onClick = { onSet(null); onDismiss() }, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = { onSet(minutes); onDismiss() }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        // ◀ ▶ shift by half an hour, -12 h … +14 h; Done keeps it.
        tv.own.owntv.ui.stage.StageSurface(
            onClick = { onSet(minutes); onDismiss() },
            radius = 18.mpx,
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            modifier = Modifier.fillMaxWidth().height(84.mpx).focusRequester(valueFocus).onPreviewKeyEvent { e ->
                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (d != 0 && e.type == KeyEventType.KeyDown) minutes = (minutes + d * 30).coerceIn(-12 * 60, 14 * 60)
                d != 0
            },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx).graphicsLayer { rotationZ = 180f })
                Text(liveEpgShiftLabel(minutes), style = tv.own.owntv.ui.theme.stageText(36, 800), color = tv.own.owntv.ui.theme.stageAccent.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx))
            }
        }
        Text(
            stringResource(if (currentMinutes == null) R.string.content_epg_offset_following_global else R.string.content_epg_offset_channel_only, liveEpgShiftLabel(globalMinutes)),
            style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 12.mpx),
        )
    }
}

/** Remind me's value: "5 min before", or "At the start" when the reminder comes at the start. */
@Composable
internal fun reminderLeadText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.settings_reminder_at_start)
    else stringResource(R.string.content_remind_before, stringResource(R.string.player_duration_minutes, minutes))

@Composable
internal fun liveEpgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}

/** glcn: how much glass the channel menu over the player has — the quick view's list panel's share. */
private const val PLAYER_MENU_GLASS = 0.66f
