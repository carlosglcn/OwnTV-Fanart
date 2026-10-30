package tv.own.owntv.features.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import tv.own.owntv.core.epg.displayLogoUrl
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.launcher.LauncherLaunch
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.update.UpdateStatusToast
import tv.own.owntv.features.categorylock.rememberDueReminder
import tv.own.owntv.features.downloads.DownloadsScreen
import tv.own.owntv.features.epg.EpgScreen
import tv.own.owntv.features.home.HomeScreen
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.features.live.LiveScreen
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.search.SearchScreen
import tv.own.owntv.features.search.SearchViewModel
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.features.series.SeriesScreen
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.owntv.player.MiniPlayer
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlayerHud
import tv.own.owntv.features.shell.components.AvatarPickerDialog
import tv.own.owntv.features.shell.components.ExitDialog
import tv.own.owntv.features.shell.components.IncompleteRestoreDialog
import tv.own.owntv.features.shell.components.SettingsScreen
import tv.own.owntv.features.shell.components.SolidAmbientBackdrop
import androidx.compose.ui.res.stringResource
import tv.own.owntv.player.alignment
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.LocalRemoteShortcuts
import tv.own.owntv.ui.components.RemoteShortcutEnvironment
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.live.LiveKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Which layer currently holds focus (drives Back navigation). */
private enum class ShellLayer { SIDEBAR, CONTENT }

/** Player presentation: hidden, fullscreen, docked mini-player, or audio-only now-playing bar. */
internal enum class PlayerMode { NONE, FULLSCREEN, MINI, AUDIO }

/**
 * The MD3 shell: a fixed navigation panel (Layer 1) plus the active destination. Settings is a
 * single-pane sectioned screen; browse sections keep the Folder Rail → Content → Preview layout.
 */
@Composable
fun OwnTVShell(
    selectedSection: MainSection,
    visibleSections: Set<MainSection>,
    onSelectSection: (MainSection) -> Unit,
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    fontCustomization: tv.own.owntv.core.theme.FontCustomization,
    onSetFontCustomization: (tv.own.owntv.core.theme.FontCustomization) -> Unit,
    avatarId: Int,
    onSetAvatar: (Int) -> Unit,
    // The active profile's own picture, and the two things that can be done to it. Blank = none.
    avatarPath: String = "",
    onSetCustomAvatar: (java.io.File) -> Unit = {},
    onClearCustomAvatar: () -> Unit = {},
    profileName: String,
    sourceSummary: String?,
    playlists: List<tv.own.owntv.core.database.entity.SourceEntity> = emptyList(),
    activePlaylistId: Long = -1L,
    onSelectPlaylist: (Long) -> Unit = {},
    activePlaylistSet: Set<Long> = emptySet(),
    onSelectPlaylists: (Set<Long>) -> Unit = {},
    weatherInfo: tv.own.owntv.core.weather.WeatherInfo? = null,
    weatherFahrenheit: Boolean = false,
    activeProfileId: Long?,
    pendingDeepLink: LauncherDeepLink?,
    onDeepLinkConsumed: () -> Unit,
    isOffline: Boolean = false,
    onExitApp: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
    // Called once the profile's start-up choice has been applied; the splash waits for it.
    onStartupSettled: () -> Unit = {},
) {
    val noSourceLabel = stringResource(R.string.shell_no_source)
    val subtitleLoadFailed = stringResource(R.string.content_subtitle_load_failed)

    val scope = rememberCoroutineScope()
    val sidebarFocus = remember { FocusRequester() }
    // When focus was last sent to the rail on purpose: ◀ / ▶ from the content, or the app itself
    // ([focusRail]). A rail visit that follows neither does not open the floating rail (#7.1).
    var railKeyAt by remember { mutableLongStateOf(0L) }
    val focusRail = {
        railKeyAt = android.os.SystemClock.uptimeMillis()
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    val contentFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val contentLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr
    val homeFirstRowFocus = remember { FocusRequester() }
    var focusedLayer by remember { mutableStateOf(ShellLayer.SIDEBAR) }
    var showExit by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showAvatarChooser by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    // Held by the Activity-scoped ShellViewModel, so a recreation (a script-family language switch)
    // does not close a film that is playing.
    val shellVm = org.koin.androidx.compose.koinViewModel<ShellViewModel>()
    var playerMode by shellVm.playerMode
    // glcn: the programme-reminder card takes no focus; while it shows, this accepts it (set where the card is drawn).
    var reminderAccept by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Deep-link: the Guide's "Add EPG" button switches to Settings and opens EPG Sources → add.
    var openEpgAdd by remember { mutableStateOf(false) }
    // Setup's "Add a TV guide" lands on the same page once the shell is up.
    LaunchedEffect(Unit) {
        if (PendingShellRequest.addEpg) {
            PendingShellRequest.addEpg = false
            openEpgAdd = true
            onSelectSection(MainSection.SETTINGS)
        }
    }
    // One-shot: set when leaving the player so the returning browse screen re-focuses the item you played.
    var restoreFocus by remember { mutableStateOf(false) }
    var searchReturn by remember { mutableStateOf<MainSection?>(null) }
    // glcn: a film's page opened from Home, and a series opened from Home (its Back comes home).
    var homeFilm by remember { mutableStateOf<tv.own.owntv.core.database.entity.MovieEntity?>(null) }
    var seriesFromHome by remember { mutableStateOf(false) }
    var restoreTrendingSearchFocus by remember { mutableStateOf(false) }
    var trendingSearchActive by remember { mutableStateOf(false) }
    val player = koinInject<OwnTVPlayer>()
    // Docked mini-player size (% of screen width) + position, configurable in Settings and from the
    // mini-player's own controls. Read straight from settings so both entry points stay in sync.
    val settingsRepo = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val frameMeterOn by settingsRepo.frameMeter.collectAsStateWithLifecycle(initialValue = false)
    val remoteShortcutsEnabled by settingsRepo.chNavEnabled.collectAsStateWithLifecycle(initialValue = true)
    val clockColors by settingsRepo.clockColors.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.ClockColors())
    val remoteShortcutBindings by settingsRepo.remoteShortcutBindings.collectAsStateWithLifecycle(
        initialValue = RemoteShortcutBindings.defaults,
    )
    val miniSizePct by settingsRepo.miniPlayerSizePct.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.player.MiniPlayerSize.DEFAULT)
    val miniPosName by settingsRepo.miniPlayerPosition.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.player.MiniPlayerPosition.DEFAULT.name)
    val ambientGlowEnabled by settingsRepo.ambientGlowEnabled.collectAsStateWithLifecycle(initialValue = false)
    // glcn: the animation style, and menu sounds silent while anything plays.
    val glcnAnimStyle by settingsRepo.animStyle.collectAsStateWithLifecycle(initialValue = 0)
    val glcnZapPanelSaved by settingsRepo.zapPanel.collectAsStateWithLifecycle(initialValue = 0)
    val glcnZapViews by settingsRepo.zapViews.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.ZapViews.DEFAULT)
    // The saved view may have been switched off in Settings; show the next enabled one instead.
    val glcnZapPanel = tv.own.owntv.core.settings.ZapViews.effective(glcnZapPanelSaved, glcnZapViews)
    // glcn: accessible mode keeps motion calm, whatever the style.
    LaunchedEffect(glcnAnimStyle, tv.own.owntv.glcn.Accessible.on) { tv.own.owntv.ui.stage.GlcnMotion.subtle = glcnAnimStyle == 1 || tv.own.owntv.glcn.Accessible.on }
    val glcnLogoPlate by settingsRepo.logoPlate.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(glcnLogoPlate) { tv.own.owntv.ui.components.LogoPlate.mode = glcnLogoPlate }
    LaunchedEffect(playerMode) { tv.own.owntv.glcn.UiSounds.suppressed = playerMode != PlayerMode.NONE }
    val ambientGlowPulse by settingsRepo.ambientGlowPulse.collectAsStateWithLifecycle(initialValue = true)
    val shellAnimationLevel by settingsRepo.animationLevel.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.theme.AnimationLevel.FULL)
    val miniPos = tv.own.owntv.core.player.MiniPlayerPosition.fromName(miniPosName)
    // Reaching the mini player: the mini window floats above the content panel, so D-pad focus
    // search has no spatial path into it from most screens. These are the three deliberate ways in:
    // the rail's Now Playing item, long-press Back, and the media keys.
    val miniEntryFocus = remember { FocusRequester() }
    val audioEntryFocus = remember { FocusRequester() }
    // The browse UI is one focus group with a restorer, so handing focus back to it after the mini
    // player returns to the exact control the user left rather than to the top of the screen.
    val shellContentFocus = remember { FocusRequester() }
    // Stage navigation (P1, P1b): where the rail sits, what it shows, its length, the floating hide delay.
    val backToWindow by settingsRepo.backToWindow.collectAsStateWithLifecycle(initialValue = false)
    val autoSkipIntro by settingsRepo.autoSkipIntro.collectAsStateWithLifecycle(initialValue = false)
    var hudControlsVisible by remember { mutableStateOf(true) }
    var introSkipVisible by remember { mutableStateOf(false) }
    val introContext = androidx.compose.ui.platform.LocalContext.current
    val navStyle by settingsRepo.navStyle.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED)
    val homeLayout by settingsRepo.homeBackdropStyle.collectAsStateWithLifecycle(initialValue = 0)
    val navSize by settingsRepo.navSize.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT)
    val navWiden by settingsRepo.navWiden.collectAsStateWithLifecycle(initialValue = null)
    val navLength by settingsRepo.navLength.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavLength.FIT)
    val topSize by settingsRepo.topSize.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavSize.NORMAL)
    val topFloating by settingsRepo.topFloating.collectAsStateWithLifecycle(initialValue = false)
    val navHideAfterMs by settingsRepo.navHideAfterMs.collectAsStateWithLifecycle(
        initialValue = tv.own.owntv.core.settings.SettingsRepository.NavHideAfter.DEFAULT_MS,
    )
    val railVm = org.koin.androidx.compose.koinViewModel<RailCountsViewModel>()
    val railCounts by railVm.counts.collectAsStateWithLifecycle()
    val railDetails by railVm.details.collectAsStateWithLifecycle()
    // Where ▶ out of the Stage rail returns to: the content, exactly as it was left.
    val contentAreaFocus = remember { FocusRequester() }
    var homeEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    // The guide's entry hook: the TV Guide, or Live TV in Guide view (null while Live TV shows its list).
    var guideEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var vodEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var downloadsEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var moreEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var sportsEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    // Home's Live sports row opens a match page through the same request the player's Match button uses.
    val sportsMatchRequests = koinInject<tv.own.owntv.features.sports.SportsPlayback>()
    // More › Settings opens Settings at a group, or with its search open.
    var settingsStart by remember { mutableStateOf<tv.own.owntv.features.shell.components.SettingsStart?>(null) }
    // The playlist pill's place on screen, where the playlist menu redraws it above its scrim.
    var playlistPillBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // glcn: where the top bar ends, so the "Last channel" pill never runs under it.
    var topBarBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // glcn: when a bigger bar would reach the playlist pill and the clock, it drops to Compact by itself.
    // glcn (TV profile): the menu keeps to TV — no Movies, Series or Downloads.
    androidx.compose.runtime.SideEffect { tv.own.owntv.glcn.TvProfiles.active = activeProfileId }
    // glcn: a channel or film already playing (full screen or in its window) keeps the trailers off — two videos at
    // once is heavy on a TV box, and two soundtracks clash.
    androidx.compose.runtime.SideEffect {
        tv.own.owntv.features.home.CinemaTrailer.blocked = playerMode != PlayerMode.NONE
        // glcn: something plays — the trailer player is let go, its memory with it.
        if (playerMode != PlayerMode.NONE) tv.own.owntv.ui.components.TrailerPool.free()
    }
    val navSections = if (tv.own.owntv.glcn.TvProfiles.isTv(activeProfileId)) visibleSections - setOf(MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS) else visibleSections
    var topCompactForced by remember(topSize, visibleSections) { mutableStateOf(false) }
    val topGapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.mpx.toPx() }
    LaunchedEffect(topBarBounds, playlistPillBounds, navStyle, topCompactForced) {
        val bar = topBarBounds
        val pl = playlistPillBounds
        if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP && bar != null && pl != null &&
            !topCompactForced && topSize != tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT &&
            bar.right > pl.left - topGapPx
        ) topCompactForced = true
    }
    var miniHasFocus by remember { mutableStateOf(false) }
    val subtitleController = koinInject<tv.own.owntv.core.subtitles.SubtitleController>()
    val subtitleContext by subtitleController.current.collectAsStateWithLifecycle()
    var showSubtitleSearch by remember { mutableStateOf(false) }
    // Local subtitle-file picker — the same TV-safe in-app browser local M3U import uses.
    var showLocalSubPicker by remember { mutableStateOf(false) }
    val localSubToast = tv.own.owntv.ui.components.rememberInAppToast()
    // Metadata allowance: tell the user ONCE per app start that their daily share of the shared
    // metadata service is gone, rather than letting posters and plots quietly stop appearing. Lives in
    // the shell because it can happen on any screen, and the shell is the one toast that is always
    // composed. `remember` (not rememberSaveable) is the once-per-launch scope we want.
    val metadataBudget = koinInject<tv.own.owntv.core.metadata.MetadataBudget>()
    val budgetRefusedAt by metadataBudget.refusedAt.collectAsStateWithLifecycle()
    var budgetNoticeShown by remember { mutableStateOf(false) }
    val budgetNotice = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.settings_metadata_limit_reached)
    LaunchedEffect(budgetRefusedAt) {
        if (budgetRefusedAt > 0L && !budgetNoticeShown) {
            budgetNoticeShown = true
            localSubToast.show(budgetNotice)
        }
    }
    val mpvEngine = remember(player) { tv.own.owntv.player.MpvPlaybackEngine(player) }
    // Audio focus + MediaSession. This is the only place that knows which engine currently owns
    // the speaker, so it hands that engine over and takes it back when the player closes.
    val playbackSession = koinInject<tv.own.owntv.player.PlaybackSession>()
    val launcherIntegrationRepository = koinInject<LauncherIntegrationRepository>()
    val homeVm = org.koin.androidx.compose.koinViewModel<HomeViewModel>()
    val movieVm = org.koin.androidx.compose.koinViewModel<MovieViewModel>()
    val seriesVm = org.koin.androidx.compose.koinViewModel<SeriesViewModel>()
    val searchVm = org.koin.androidx.compose.koinViewModel<SearchViewModel>()
    // Same activity-scoped instances the Live/Guide screens use — lets the fullscreen HUD zap channels
    // up/down (CH+/CH-). Guide tunes start through LiveViewModel too (they set zapSource = LIVE_TV), so
    // there is exactly ONE zap path: liveVm's. The Guide keeps its own EpgViewModel only for the grid.
    val liveVm = org.koin.androidx.compose.koinViewModel<LiveViewModel>()
    // "Previous" from the system media controls on a live channel goes back a channel.
    DisposableEffect(playbackSession, liveVm) {
        playbackSession.livePrevious = { liveVm.previousChannel() }
        onDispose { playbackSession.livePrevious = null }
    }
    val epgVm = org.koin.androidx.compose.koinViewModel<tv.own.owntv.features.epg.EpgViewModel>()
    val liveCanZap by liveVm.canZap.collectAsStateWithLifecycle()
    // Full-screen is running on the ExoPlayer engine (a promoted Live preview) rather than mpv.
    val liveOnExo by liveVm.liveOnExo.collectAsStateWithLifecycle()
    val previousLiveChannel by liveVm.previousChannelTarget.collectAsStateWithLifecycle()
    // A catch-up archive programme is playing (Guide "Watch from start" or the Live TV catch-up picker)
    // rather than the live stream — the HUD swaps live-only controls for the VOD ones.
    val catchupActive by liveVm.catchupActive.collectAsStateWithLifecycle()
    val vodExoActive by player.exoActiveState.collectAsStateWithLifecycle()
    // Auto frame rate: only ever applied to the FULL-SCREEN surface (never the mini-player or the
    // in-pane Live preview) — see FrameRateController.
    val autoFrameRate by settingsRepo.autoFrameRate.collectAsStateWithLifecycle(initialValue = false)
    val afrMatchResolution by settingsRepo.afrMatchResolution.collectAsStateWithLifecycle(initialValue = false)
    val afrPauseSecs by settingsRepo.afrPauseSecs.collectAsStateWithLifecycle(initialValue = 0)
    // ...and the one-time suggestion to turn it on, for the 25-fps-on-60-Hz judder the direct render path
    // cannot fix by itself. `true` until the flag is read, so it can never flash on first frame.
    val afrPrompted by settingsRepo.autoFrameRatePrompted.collectAsStateWithLifecycle(initialValue = true)
    // Direct tune (type a channel number on the remote). Settings › Live TV › Channel numbers; default on.
    val directTuneEnabled by settingsRepo.directTune.collectAsStateWithLifecycle(initialValue = true)
    val playerRemoteOptions by settingsRepo.playerRemoteOptions.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.PlayerRemoteOptions())
    // "Prefer EPG logos": start following the setting once, here rather than in Application.onCreate —
    // the store queries nothing at all while the toggle is off, so cold start stays free of EPG reads.
    val epgDaoForLogos = koinInject<tv.own.owntv.core.database.dao.EpgDao>()
    val customizeForLogos = koinInject<tv.own.owntv.core.customize.CustomizationStore>()
    val logoScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        tv.own.owntv.core.epg.EpgLogoStore.start(logoScope, settingsRepo, epgDaoForLogos, customizeForLogos)
    }
    // Live rewind / timeshift: whether the live channel supports catch-up, and how far behind live we are.
    val canRewindLive by liveVm.canRewindLive.collectAsStateWithLifecycle()
    // The offset itself ticks once a second while an archive plays. Held as State and deliberately NOT
    // read here — reading it in the shell's own scope invalidated the whole shell body every second.
    // It is handed to the HUD as a lambda, so only the HUD reads the ticking value; the shell needs
    // just the on/off fact, which changes when the user enters or leaves the rewind and no oftener.
    val timeshiftOffsetState = liveVm.timeshiftOffsetSec.collectAsStateWithLifecycle()
    val watchingWallState = liveVm.watchingWallMs.collectAsStateWithLifecycle()
    val onLocalCopy by liveVm.onLocalCopy.collectAsStateWithLifecycle()
    val timeshifted by remember(liveVm) {
        liveVm.timeshiftOffsetSec.map { it != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false)
    // Which section armed the current fullscreen stream — picks whose channel list CH+/CH- step through.
    var zapSource by shellVm.zapSource
    // The multi-day guide for the rewind timeline is read only while it can be shown (the same test
    // the HUD applies below); otherwise its query would re-run on every channel-list focus step.
    val wantTimeline = playerMode == PlayerMode.FULLSCREEN && zapSource == MainSection.LIVE_TV && !catchupActive && canRewindLive
    val timelineProgrammes by remember(liveVm, wantTimeline) {
        if (wantTimeline) liveVm.timelineProgrammes else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    // In-player channel-list overlay (Left while controls hidden, live only).
    var showChannelList by remember { mutableStateOf(false) }
    // glcn: held OK — the quick guide (list, picture, schedule, posters; ▶ steps the view and it is remembered).
    var showQuickGuide by remember { mutableStateOf(false) }
    // glcn: hold OK on a card of the picture strip → that channel's options over the player.
    var zapOptionsChannel by remember { mutableStateOf<tv.own.owntv.core.database.entity.ChannelEntity?>(null) }
    // glcn: the zap deck's posters view, until ▶ steps back to the deck; every opening starts on the deck.
    var deckPosters by remember { mutableStateOf(false) }
    LaunchedEffect(showChannelList) { if (!showChannelList) deckPosters = false }
    // In-player watch-history list (Right while controls hidden, live only).
    var showHistoryList by remember { mutableStateOf(false) }
    // glcn: ▼ on the "Last channel" pill — the recent channels drop-down.
    var showRecentDrop by remember { mutableStateOf(false) }
    // glcn (TV profile): the TV strip over the full-screen player.
    var showTvStrip by remember { mutableStateOf(false) }
    // Multiview: the grid, while it is up, and which tile is waiting for a channel to be picked.
    val multiviewEnabled by settingsRepo.multiviewEnabled.collectAsStateWithLifecycle(false)
    val recordWatchingEnabled by settingsRepo.recordWhatImWatching.collectAsStateWithLifecycle(false)
    val liveLeftRightRewinds by settingsRepo.liveLeftRightRewinds.collectAsStateWithLifecycle(false)
    val multiviewTileCount by settingsRepo.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    val enginePool = koinInject<tv.own.owntv.player.LiveEnginePool>()
    val streamRegistry = koinInject<tv.own.owntv.core.live.OpenStreamRegistry>()
    var multiview by remember { mutableStateOf<tv.own.owntv.features.multiview.MultiviewState?>(null) }
    var multiviewPickFor by remember { mutableStateOf<Int?>(null) }
    // Publish the active engine to the system (audio focus + MediaSession), and detach when the player
    // is closed — an inactive session must not keep answering the TV's transport keys or the Assistant.
    // During Multiview that is the tile with the sound; the preview engine it used to stay on is stopped.
    val multiviewAudibleEngine = multiview?.let { it.pool.peek(it.audible) }
    LaunchedEffect(liveOnExo, playerMode, multiviewAudibleEngine) {
        playbackSession.attach(
            when {
                multiview != null -> multiviewAudibleEngine
                playerMode == PlayerMode.NONE -> null
                liveOnExo -> liveVm.previewEngine
                else -> mpvEngine
            },
        )
    }
    // The pool outlives this composable (it is app-wide), the grid state does not. If the shell is torn
    // down with the grid up, release its engines and stream claims, or they play on as ghosts that
    // refuse later tiles and recordings.
    val currentMultiview by rememberUpdatedState(multiview)
    DisposableEffect(Unit) {
        onDispose { currentMultiview?.releaseAll() }
    }
    // Channels kept from the Live list (the second way into the grid). Pressing play on any channel is
    // what says "now": the grid opens with them already in it, and the selection is spent.
    val multiviewSelection by liveVm.multiviewSelection.collectAsStateWithLifecycle()
    val zapChannels by liveVm.zapChannels.collectAsStateWithLifecycle()
    val zapListTitle by liveVm.zapListTitle.collectAsStateWithLifecycle()
    val zapListKey by liveVm.zapListKey.collectAsStateWithLifecycle()
    // Favorites and History have no provider name to show — their labels are UI strings, so the overlay
    // used to head both of them "All channels".
    val zapOverlayTitle = zapListTitle ?: when (zapListKey) {
        LiveKey.Favorites -> stringResource(R.string.content_category_favorites)
        LiveKey.History -> stringResource(R.string.content_category_history)
        LiveKey.Catchup -> stringResource(R.string.content_catchup)
        else -> stringResource(R.string.content_category_all_channels)
    }
    val showCategoryBrowser by liveVm.showCategoryBrowser.collectAsStateWithLifecycle()
    val browserRailItems = liveVm.railItems.collectAsStateWithLifecycle().value.let { all -> remember(all) { all.filterNot { it.sealed } } }
    val browserRailCounts by liveVm.railCounts.collectAsStateWithLifecycle()
    val previewChannel by liveVm.previewChannel.collectAsStateWithLifecycle()
    // glcn: kept in step with every way the player opens, docks or closes (see LiveViewModel.onChannelFocused).
    LaunchedEffect(playerMode) {
        liveVm.playerFullscreen = playerMode == PlayerMode.FULLSCREEN
        // glcn: the light-mode question waits until the person is back in the menus.
        tv.own.owntv.glcn.LowRam.holdPrompt = playerMode == PlayerMode.FULLSCREEN
    }
    // Home / screensaver with a live channel on mpv: mpv frees the stream and keeps no restore for live
    // (ExoPlayer's live engine does), so the return found a black, paused player. Tune the same channel
    // again once the relock check is done; the tune reopens its saved copy and offers Resume / Go live.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val currentPlayerMode by rememberUpdatedState(playerMode)
    DisposableEffect(lifecycle, liveVm, player) {
        var mpvLiveChannel: tv.own.owntv.core.database.entity.ChannelEntity? = null
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mpvLiveChannel = liveVm.previewChannel.value?.takeIf {
                    currentPlayerMode != PlayerMode.NONE && !liveVm.liveOnExo.value && player.isLiveContent
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> mpvLiveChannel?.let { channel ->
                    mpvLiveChannel = null
                    shellVm.afterSealCheck {
                        // A relock that sealed it cleared the channel; a stream already back needs nothing.
                        if (liveVm.previewChannel.value?.id == channel.id && player.currentMediaUrl == null) {
                            liveVm.ensurePlaying(channel)
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val playerRecording by liveVm.playerRecording.collectAsStateWithLifecycle()
    // glcn: the playing programme's picture is fetched and loaded as soon as the channel opens — the
    // player's card then has it on screen the first time it is shown, with no late pop-in.
    run {
        val artContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(previewChannel?.id) {
            val ch = previewChannel ?: return@LaunchedEffect
            liveVm.nowNext.map { it?.now?.startMs }.distinctUntilChanged().collect { start ->
                val url = liveVm.nowArtUrl(ch, start) ?: return@collect
                coil3.SingletonImageLoader.get(artContext).enqueue(coil3.request.ImageRequest.Builder(artContext).data(url).build())
            }
        }
    }
    val overlayProgrammes by liveVm.nowProgrammes.collectAsStateWithLifecycle()
    val overlayFavoriteIds by liveVm.favoriteIds.collectAsStateWithLifecycle()
    val overlayPlaylistMarks by liveVm.playlistMarks.collectAsStateWithLifecycle()
    // Favorite state for the player HUD's in-stream favorite toggle (live channel / movie / series).
    val liveFavoriteIds by liveVm.favoriteIds.collectAsStateWithLifecycle()
    val playingMovie by movieVm.playingMovie.collectAsStateWithLifecycle()
    val movieFavoriteIds by movieVm.favoriteIds.collectAsStateWithLifecycle()
    val playingSeries by seriesVm.playingSeries.collectAsStateWithLifecycle()
    val seriesFavoriteIds by seriesVm.favoriteIds.collectAsStateWithLifecycle()
    // Favorite toggle for whatever is playing: the live channel, the movie, or the series (episodes
    // favorite their parent series). Picked by the section that armed the stream. Hoisted here because
    // the audio capsule in the top bar carries the same control as the fullscreen HUD.
    val favToggle: (() -> Unit)? = when (zapSource) {
        MainSection.LIVE_TV -> previewChannel?.let { ch -> { liveVm.toggleFavorite(ch) } }
        MainSection.MOVIES -> playingMovie?.let { m -> { movieVm.toggleFavorite(m) } }
        MainSection.SERIES -> playingSeries?.let { s -> { seriesVm.toggleFavorite(s) } }
        else -> null
    }
    val favActive = when (zapSource) {
        MainSection.LIVE_TV -> previewChannel?.let { liveFavoriteIds.contains(it.id) } ?: false
        MainSection.MOVIES -> playingMovie?.let { movieFavoriteIds.contains(it.id) } ?: false
        MainSection.SERIES -> playingSeries?.let { seriesFavoriteIds.contains(it.id) } ?: false
        else -> false
    }
    // glcn: what the film player's cinema chrome shows — the film or episode, its lettering and rating (TMDB, cached).
    val cinemaMeta = koinInject<tv.own.owntv.core.metadata.MetadataRepository>()
    val cinemaQueue by seriesVm.cinemaQueue.collectAsStateWithLifecycle()
    val cinemaEpisodeNow = cinemaQueue?.let { (eps, i) -> eps.getOrNull(i) }
    val cinemaInfo by androidx.compose.runtime.produceState<tv.own.owntv.glcn.CinemaInfo?>(
        null, zapSource, playingMovie?.id, playingSeries?.id, cinemaEpisodeNow?.id,
    ) {
        value = when (zapSource) {
            MainSection.MOVIES -> playingMovie?.let { m ->
                val meta = runCatching { cinemaMeta.resolveMovie(m) }.getOrNull()
                tv.own.owntv.glcn.CinemaInfo(
                    title = m.name,
                    logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
                    year = m.year ?: meta?.year,
                    durationMin = m.durationSecs?.let { it / 60 }?.takeIf { it > 0 },
                    rating = meta?.rating?.takeIf { it > 0 } ?: m.rating?.takeIf { it > 0 },
                    genres = cinemaJsonList(meta?.genresJson),
                    overview = meta?.overview?.takeIf { it.isNotBlank() } ?: m.plot,
                    cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson).map { it.name },
                    capabilities = m.advertisedCapabilities,
                    qualityRank = m.qualityRank,
                )
            }
            MainSection.SERIES -> playingSeries?.let { show ->
                val meta = runCatching { cinemaMeta.resolveSeries(show) }.getOrNull()
                val ep = cinemaEpisodeNow
                tv.own.owntv.glcn.CinemaInfo(
                    title = ep?.name?.takeIf { it.isNotBlank() } ?: show.name,
                    logoUrl = tv.own.owntv.core.metadata.MetadataImages.logo(meta?.logoPath),
                    year = show.year ?: meta?.year,
                    durationMin = ep?.durationSecs?.let { it / 60 }?.takeIf { it > 0 },
                    rating = meta?.rating?.takeIf { it > 0 } ?: show.rating?.takeIf { it > 0 },
                    genres = cinemaJsonList(meta?.genresJson),
                    overview = ep?.plot?.takeIf { it.isNotBlank() } ?: meta?.overview ?: show.plot,
                    cast = tv.own.owntv.core.metadata.MetadataCast.parse(meta?.castJson).map { it.name },
                    show = show.name,
                    season = ep?.seasonNumber,
                    episode = ep?.episodeNumber,
                    capabilities = show.advertisedCapabilities,
                    qualityRank = show.qualityRank,
                )
            }
            else -> null
        }
    }
    val cinemaEpisodes = remember(cinemaQueue?.first) {
        cinemaQueue?.first.orEmpty().map {
            tv.own.owntv.glcn.CinemaEpisode(it.seasonNumber, it.episodeNumber, it.name, it.plot, it.durationSecs?.let { s -> s / 60 }?.takeIf { m -> m > 0 })
        }
    }
    // Tell core which playlist is on screen, so its background catalogue drain steps aside while the
    // user is watching. Core cannot work this out alone: the player engines are handed
    // a URL and have no notion of a sourceId, and fullscreen playback deliberately never claims a
    // connection in OpenStreamRegistry — that register is the Multiview/recording budget. This screen
    // holds the row, so this is the only place the answer exists.
    val watchSession = koinInject<tv.own.owntv.core.live.WatchSession>()
    val watchingSourceId = if (playerMode == PlayerMode.NONE) {
        null
    } else {
        when (zapSource) {
            MainSection.LIVE_TV -> previewChannel?.sourceId
            MainSection.MOVIES -> playingMovie?.sourceId
            MainSection.SERIES -> playingSeries?.sourceId
            else -> null
        }
    }
    // DisposableEffect, not LaunchedEffect: zapping to another playlist has to close the old session
    // before opening the new one, and leaving the shell has to close the last one — a leaked session
    // would hold the drain off for good.
    DisposableEffect(watchingSourceId) {
        watchingSourceId?.let { watchSession.open(it) }
        onDispose { watchingSourceId?.let { watchSession.close(it) } }
    }
    // Current programme per channel for the in-player channel list overlay (small subtitle under each row).
    // Only resolved while the overlay is actually open. Keyed on the channel set so a zap-list change re-resolves.
    // Shares the Live list's resolved titles, so opening the overlay over a list already on screen
    // asks for nothing, and only genuinely new channels cost a query.
    val overlayNowPlaying by liveVm.nowPlaying.collectAsStateWithLifecycle()
    LaunchedEffect(showChannelList, showQuickGuide, zapChannels) {
        if ((showChannelList || showQuickGuide) && zapChannels.size > 1) liveVm.ensureNowPlaying(zapChannels)
    }
    // Recently-watched channels for the right-hand history overlay — re-read each time it opens (and
    // after a zap, since tuning writes a new history row) so the newest channel is always on top.
    val historyEntries by produceState(emptyList<tv.own.owntv.core.database.dao.ChannelWithWatchedAt>(), showHistoryList, previewChannel?.id) {
        if (!showHistoryList) { value = emptyList(); return@produceState }
        value = runCatching { liveVm.historyChannels() }.getOrDefault(emptyList())
    }
    val historyChannels = remember(historyEntries) { historyEntries.map { it.channel } }
    val historyWatchedAt = remember(historyEntries) { historyEntries.associate { it.channel.id to it.watchedAt } }
    val historyNowPlaying = overlayNowPlaying // one shared map; the history rail adds to it below
    LaunchedEffect(historyChannels) { liveVm.ensureNowPlaying(historyChannels) }
    // The single most-recent resumable item, surfaced as a shared top-bar "Continue" chip.
    val continueTarget by homeVm.continueTarget.collectAsStateWithLifecycle()

    // glcn (TV profile): switching into a TV profile goes straight to its last channel, full screen, as launching does.
    val tvSwitchSeen = remember { longArrayOf(Long.MIN_VALUE) }
    LaunchedEffect(activeProfileId) {
        val id = activeProfileId ?: return@LaunchedEffect
        val first = tvSwitchSeen[0] == Long.MIN_VALUE
        tvSwitchSeen[0] = id
        if (first || !tv.own.owntv.glcn.TvProfiles.isTv(id) || playerMode != PlayerMode.NONE) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        val ch = liveVm.lastWatchedLiveChannel() ?: return@LaunchedEffect
        if (playerMode == PlayerMode.NONE) {
            zapSource = MainSection.LIVE_TV
            liveVm.watchFullscreen(ch, emptyList())
            playerMode = PlayerMode.FULLSCREEN
        }
    }
    // Per-profile startup action runs once when the authenticated shell first appears. With one unlocked
    // profile that is immediately; profile/PIN gates keep the shell out of composition until authorized.
    val resumeSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val startupChannelUnavailable = androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.settings_startup_channel_unavailable)
    LaunchedEffect(Unit) {
        if (playerMode != PlayerMode.NONE) { onStartupSettled(); return@LaunchedEffect }
        val pid = resumeSettings.activeProfileId.first()
        // glcn: a TV profile always opens on its last channel, full screen, whatever its saved start-up says.
        val tvStart = tv.own.owntv.glcn.TvProfiles.isTv(pid)
        when (val startupMode = if (tvStart) tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL else resumeSettings.startupMode(pid).first()) {
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL -> {
                val ch = liveVm.lastWatchedLiveChannel()
                if (ch != null && playerMode == PlayerMode.NONE) {
                    zapSource = MainSection.LIVE_TV
                    // There is no caller-owned browse rail here. Let LiveViewModel build the channel's
                    // provider context instead of permanently arming a one-item list that disables CH+/CH-.
                    liveVm.watchFullscreen(ch, emptyList())
                    playerMode = PlayerMode.FULLSCREEN
                }
            }
            // The TV Guide or Live TV on the last channel's category, the channel focused and in the preview,
            // as TiviMate opens. Live TV's list scrolls to it (revealChannel); a guide focuses its row on load.
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_GUIDE,
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_LIVE -> {
                val ch = liveVm.lastWatchedLiveChannel()?.takeIf { liveVm.isVisibleToActiveProfile(it) }
                val toGuide = startupMode == tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_GUIDE
                if (ch != null && playerMode == PlayerMode.NONE) {
                    if (!toGuide && resumeSettings.liveView.first() == tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST) {
                        liveVm.revealChannel(ch)
                    } else {
                        val key = liveVm.railKeyOf(ch)
                        if (toGuide) epgVm.setCategoryFilter(key) else liveVm.select(key)
                        epgVm.noteChannelTuned(ch)
                        liveVm.onChannelFocused(ch)
                        restoreFocus = true
                    }
                    onSelectSection(if (toGuide) MainSection.EPG else MainSection.LIVE_TV)
                }
            }
            // Open straight to Live TV on the Favorites folder, with focus landing inside the channel list
            // (restoreFocus drives LiveScreen to focus the first/last channel, not the nav panel).
            tv.own.owntv.core.settings.StartupMode.FAVORITES -> {
                onSelectSection(MainSection.LIVE_TV)
                liveVm.select(tv.own.owntv.core.live.LiveKey.Favorites)
                restoreFocus = true
            }
            tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL -> {
                val ref = resumeSettings.startupChannel(pid).first()
                var launch: LauncherLaunch? = null
                if (ref != null) {
                    if (!ref.remoteId.isNullOrBlank()) {
                        launch = launcherIntegrationRepository.resolveLaunch(
                            pid,
                            LauncherDeepLink.Live(sourceId = ref.sourceId, remoteId = ref.remoteId),
                        )
                    }
                    if (launch == null) {
                        launch = launcherIntegrationRepository.resolveLaunch(
                            pid,
                            LauncherDeepLink.Live(sourceId = ref.sourceId, name = ref.name),
                        )
                    }
                    if (launch == null && ref.itemId > 0L) {
                        launch = launcherIntegrationRepository.resolveLaunch(
                            pid,
                            LauncherDeepLink.Live(sourceId = ref.sourceId, itemId = ref.itemId),
                        )
                    }
                }
                val channel = (launch as? LauncherLaunch.Live)?.channel
                if (channel != null && liveVm.isVisibleToActiveProfile(channel) && playerMode == PlayerMode.NONE) {
                    zapSource = MainSection.LIVE_TV
                    liveVm.watchFullscreen(channel, emptyList())
                    playerMode = PlayerMode.FULLSCREEN
                } else {
                    onSelectSection(MainSection.HOME)
                    localSubToast.show(startupChannelUnavailable)
                }
            }
            tv.own.owntv.core.settings.StartupMode.HOME -> Unit
        }
        tv.own.owntv.core.util.Perf.stamp("startup-settled")
        onStartupSettled()
    }

    // Movies/Series/Live load on first open via their reactive Paging flows — their indexed first page is
    // cheap, so they need NO preloading (a Live-TV-only user pays nothing for them). The TV Guide is the ONE
    // exception: load() pulls every guide channel + a programme window, which is heavy enough that doing it on
    // open felt slow. So warm EPG in the background shortly after the shell renders — opening the Guide is then
    // instant, matching how it behaved before. (EpgScreen also calls load() on mount, so this is a pure pre-warm
    // and is skipped if the user is already on EPG.)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_200)
        if (selectedSection != MainSection.EPG) { tv.own.owntv.core.util.Perf.stamp("epg-preload"); epgVm.load() }
    }

    // Opening content from a browse screen goes fullscreen — UNLESS the player is already docked as a
    // mini-player, in which case it stays docked and just swaps to the newly-selected stream (the VM
    // already started it), so picking a channel updates the PiP window in place (#6).
    fun openFullscreen(source: MainSection = selectedSection) {
        restoreFocus = false
        zapSource = source
        homeVm.stopPreview()
        // Only Live TV promotes a channel to the ExoPlayer engine. Movies/Series/Search/EPG/Downloads all
        // play on mpv — clear any stale live-on-ExoPlayer flag so the shell renders mpv, not the old channel.
        if (source != MainSection.LIVE_TV) liveVm.clearLiveOnExo()
        // Only Movies/Series/Downloads carry an external-subtitle item context (set by their play
        // paths). Anything else (Live/EPG/Search-channel) clears it so ADD SUBTITLES never shows stale.
        if (source != MainSection.MOVIES && source != MainSection.SERIES && source != MainSection.DOWNLOADS) {
            subtitleController.clear()
        }
        // A new stream is opening — make sure any Audio Mode video-off state is cleared, else mpv keeps
        // `vid=no` and the new item would play with no picture.
        player.exitAudioOnly(); runCatching { liveVm.previewEngine.exitAudioOnly() }
        if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
        liveVm.playerFullscreen = playerMode == PlayerMode.FULLSCREEN
    }
    // Restore video output on both engines (no-op unless we were in Audio Mode). mpv `vid=auto` /
    // ExoPlayer surface is re-attached by the surface remount right after.
    val resumeVideo = {
        player.exitAudioOnly()
        runCatching { liveVm.previewEngine.exitAudioOnly() }
    }
    // The mini-player's own expand button always maximizes.
    val expandPlayer = { resumeVideo(); restoreFocus = false; playerMode = PlayerMode.FULLSCREEN }
    val exitPlayer = {
        // Flush the resume position BEFORE the stream is torn down — stop() drops the loaded item's
        // identity, after which neither view model can tell the position was theirs. Both calls are
        // no-ops unless the player is on that section's item.
        movieVm.saveProgressNow()
        seriesVm.saveEpisodeProgressNow()
        resumeVideo() // restore mpv `vid=auto` before stop so the next played item isn't left video-less
        playerMode = PlayerMode.NONE
        liveVm.playerFullscreen = false
        showChannelList = false
        showQuickGuide = false
        showHistoryList = false
        liveVm.hideCategoryBrowser()
        liveVm.onFullscreenExited() // no longer full-screen on ExoPlayer → let the preview re-take the engine
        player.stop()
        subtitleController.clear() // leaving the player drops the OpenSubtitles item context
        if (selectedSection != MainSection.LIVE_TV) liveVm.clearLiveOnExo()
        // Closing back into a guide: it focuses the channel that was playing (zapped-to included).
        epgVm.playerClosed = selectedSection == MainSection.EPG ||
            (selectedSection == MainSection.LIVE_TV && liveVm.liveView.value == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE)
        restoreFocus = true
        focusRail()
        Unit
    }
    SealedPlaybackEffect(shellVm, liveVm, movieVm, seriesVm, player, { currentMultiview }, exitPlayer)
    // A one-stream playlist that a download was using: core paused the download; this asks what next.
    tv.own.owntv.features.downloads.DownloadWatchPrompt(onClosePlayer = { if (playerMode != PlayerMode.NONE) exitPlayer() })
    /**
     * Turn the channel on screen into tile 1 of the grid, with [extra] filling the tiles after it.
     *
     * The fullscreen stream is stopped first, always: tile 1 opens an engine of its own, and a
     * playlist that allows two streams would otherwise be asked for three.
     */
    val openMultiview = { first: tv.own.owntv.core.database.entity.ChannelEntity?,
                          extra: List<tv.own.owntv.core.database.entity.ChannelEntity> ->
        liveVm.previewEngine.stop()
        player.stop()
        val state = tv.own.owntv.features.multiview.MultiviewState(
            pool = enginePool,
            registry = streamRegistry,
            live = liveVm,
            maxTiles = multiviewTileCount,
        )
        var tile = 0
        first?.let { state.fill(tile++, it) }
        // Channels kept with "Add to Multiview" grow the grid as they are placed, up to the ceiling —
        // the user asked for these by name, so they are the one case that sizes the grid itself.
        extra.filter { it.id != first?.id }.forEach { channel ->
            if (tile >= state.tiles.size) state.addTile()
            if (tile < state.tiles.size) state.fill(tile++, channel)
        }
        liveVm.clearMultiviewSelection()
        state
    }
    // The sleep timer's "stop" on a television. Closing the player alone is not a stop here: a
    // live channel carries on in the preview pane, and a Multiview grid is not the player at all. So
    // all three go, in the order Multiview's own Back uses, and the preview last.
    val sleepTimer = koinInject<tv.own.owntv.player.SleepTimer>()
    val stopForSleep by rememberUpdatedState {
        multiview?.let { grid ->
            grid.releaseAll()
            multiview = null
            multiviewPickFor = null
        }
        if (playerMode != PlayerMode.NONE) exitPlayer()
        liveVm.stopPreview()
    }
    DisposableEffect(sleepTimer) {
        sleepTimer.stopPlayback = { stopForSleep() }
        onDispose { sleepTimer.stopPlayback = null }
    }
    // The kept selection is spent the moment a channel actually starts playing full screen: that
    // press is the "and now open it" the plan describes, and nothing else in the app claims it.
    LaunchedEffect(playerMode, previewChannel?.id, multiviewSelection.size) {
        if (multiviewEnabled &&
            multiview == null &&
            multiviewSelection.isNotEmpty() &&
            playerMode == PlayerMode.FULLSCREEN &&
            zapSource == MainSection.LIVE_TV &&
            previewChannel != null
        ) {
            multiview = openMultiview(previewChannel, multiviewSelection)
        }
    }

    val dockPlayer = {
        resumeVideo()
        playerMode = PlayerMode.MINI
        restoreFocus = true
        focusRail()
        Unit
    }
    // Switch the current stream to audio-only and surface the now-playing bar in the top bar. Stop the
    // video decoder FIRST, then drop the video surface by leaving FULLSCREEN/MINI.
    val toAudioMode = {
        (if (liveOnExo) liveVm.previewEngine else mpvEngine).enterAudioOnly()
        playerMode = PlayerMode.AUDIO
        restoreFocus = true
        focusRail()
        Unit
    }

    // Whichever engine is on the speaker right now — what the rail item pictures and what the media
    // keys act on while the window is docked.
    val dockedEngine = if (liveOnExo) liveVm.previewEngine else mpvEngine
    val dockedPlaying by dockedEngine.isPlaying.collectAsStateWithLifecycle()
    // Leaving the app while video plays can keep it going in a picture-in-picture window, which shows
    // the video alone. Coming back from the window always lands on full screen.
    val (inPip, pipSource) = tv.own.owntv.features.pip.rememberShellPictureInPicture(
        playerShown = playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI,
        multiview = multiview != null,
        engine = dockedEngine,
        videoAspect = if (liveOnExo) liveVm.previewEngine.videoAspect else player.videoAspect,
        onReturnToApp = { if (playerMode == PlayerMode.MINI) expandPlayer() },
    )
    val nowPlayingRail = when (playerMode) {
        PlayerMode.MINI, PlayerMode.AUDIO -> tv.own.owntv.features.shell.components.NowPlayingRail(
            // Only a live channel has a logo to show; a movie or an episode falls back to the play mark.
            logoUrl = if (zapSource == MainSection.LIVE_TV) previewChannel?.displayLogoUrl else null,
            audioMode = playerMode == PlayerMode.AUDIO,
            playing = dockedPlaying,
        )
        else -> null
    }
    // Auto-hide: once focus has been in the content for the chosen delay, the capsule slides away.
    var railIdleHidden by remember { mutableStateOf(false) }
    // Focus on a top-right pill (reached from the rail) is not "in the rail": the rail closes, so its
    // scrim never covers the pill that holds focus.
    var clusterFocused by remember { mutableStateOf(false) }
    val railHasFocus = focusedLayer == ShellLayer.SIDEBAR && !clusterFocused
    // Whether the rail *looks* focused. Focus also lands on the rail by itself — for a moment while a
    // page swaps, or for as long as a series is still loading and has nothing to focus — and showing
    // those visits opened the floating rail and restarted its hide delay, so it popped up on every
    // page (#7.1). So a visit opens it only when it was meant: within [RAIL_KEY_WINDOW_MS] of ◀ / ▶ or
    // of [focusRail], or once a key is pressed while the rail holds focus.
    var railFocused by remember { mutableStateOf(false) }
    // Keyed on [railKeyAt] too: at start-up focus already counts as on the rail before [focusRail] runs.
    LaunchedEffect(railHasFocus, railKeyAt) {
        railFocused = railHasFocus && android.os.SystemClock.uptimeMillis() - railKeyAt <= RAIL_KEY_WINDOW_MS
    }
    // glcn: the top bar floats too when asked: it hides after the same delay once focus is out of it.
    val topBarFocused = railHasFocus
    LaunchedEffect(railFocused, navStyle, navHideAfterMs, topFloating, topBarFocused) {
        railIdleHidden = false
        if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP && topFloating && !topBarFocused) {
            kotlinx.coroutines.delay(navHideAfterMs.toLong())
            railIdleHidden = true
        } else if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING && !railFocused) {
            kotlinx.coroutines.delay(navHideAfterMs.toLong())
            railIdleHidden = true
        }
    }
    // Docked always shows its Size; Floating rests as the capsule, opens at its Size while it holds
    // focus and hides once focus has been in the content for the delay. Compact stays the capsule —
    // except Docked + Compact with Widen on focus, which opens at that size over the content (the content
    // keeps the compact room, so nothing behind it moves).
    val widenDocked = navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED && navSize == tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT && navWiden != null && railFocused
    val railSize = if (widenDocked) navWiden ?: navSize else navSize
    val railState = when {
        widenDocked -> tv.own.owntv.features.shell.components.RailState.OPEN
        navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED -> tv.own.owntv.features.shell.components.RailState.PINNED
        railFocused -> tv.own.owntv.features.shell.components.RailState.OPEN
        railIdleHidden -> tv.own.owntv.features.shell.components.RailState.HIDDEN
        else -> tv.own.owntv.features.shell.components.RailState.CAPSULE
    }
    // The playlist chip reflects the active filter: "All playlists" when none is chosen (id <= 0), the
    // chosen playlist's name otherwise. With a single playlist there's nothing to switch, so just its name.
    val playlistLabel = when {
        playlists.size <= 1 -> sourceSummary ?: noSourceLabel
        activePlaylistSet.isNotEmpty() -> playlists.filter { it.id in activePlaylistSet }.joinToString(", ") { it.name }
        activePlaylistId <= 0L -> stringResource(R.string.content_all_playlists)
        else -> playlists.firstOrNull { it.id == activePlaylistId }?.name ?: (sourceSummary ?: noSourceLabel)
    }
    // OK on the rail item moves focus INTO the window (it is not a section and navigates nowhere).
    val enterNowPlaying = {
        when (playerMode) {
            PlayerMode.MINI -> runCatching { miniEntryFocus.requestFocus() }
            PlayerMode.AUDIO -> runCatching { audioEntryFocus.requestFocus() }
            else -> Unit
        }
        Unit
    }
    // Long-press Back toggles: into the window, and back out to the control you came from.
    val toggleNowPlayingFocus = {
        if (miniHasFocus) runCatching { shellContentFocus.requestFocus() } else enterNowPlaying()
        Unit
    }

    val continueLastWatched = {
        continueTarget?.let { target ->
            scope.launch {
                when (target.kind) {
                    tv.own.owntv.features.home.ContinueKind.LIVE ->
                        if (liveVm.ensurePlayingByIdAsync(target.channelId)) openFullscreen(MainSection.LIVE_TV)
                    tv.own.owntv.features.home.ContinueKind.MOVIE ->
                        if (movieVm.playByIdAsync(target.movieId, target.positionMs) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES)
                    tv.own.owntv.features.home.ContinueKind.EPISODE ->
                        if (seriesVm.playFromHomeAsync(target.seriesId, target.episodeId, target.positionMs) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES)
                }
            }
        }
        Unit
    }
    val openShortcutSection: (MainSection) -> Unit = { section ->
        if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
        if (section == MainSection.SEARCH) {
            searchVm.setQuery("")
            trendingSearchActive = false
            restoreTrendingSearchFocus = false
        }
        onSelectSection(section)
        // A shortcut may be fired by a control inside the destination being replaced (most notably
        // Settings). Once that screen leaves composition its focused node disappears, so hand focus
        // to the persistent sidebar after the new destination has rendered.
        scope.launch {
            withFrameNanos { }
            focusRail()
        }
    }
    val allPlaylistsLabel = stringResource(R.string.content_all_playlists)
    val dispatchRemoteShortcut: (RemoteShortcutAction) -> Unit = { action ->
        when (action) {
            RemoteShortcutAction.OPEN_HOME -> openShortcutSection(MainSection.HOME)
            RemoteShortcutAction.OPEN_LIVE_TV -> openShortcutSection(MainSection.LIVE_TV)
            RemoteShortcutAction.OPEN_MOVIES -> openShortcutSection(MainSection.MOVIES)
            RemoteShortcutAction.OPEN_SERIES -> openShortcutSection(MainSection.SERIES)
            RemoteShortcutAction.OPEN_DOWNLOADS -> openShortcutSection(MainSection.DOWNLOADS)
            RemoteShortcutAction.OPEN_GUIDE -> openShortcutSection(MainSection.EPG)
            RemoteShortcutAction.OPEN_SEARCH -> openShortcutSection(MainSection.SEARCH)
            RemoteShortcutAction.OPEN_SETTINGS -> openShortcutSection(MainSection.SETTINGS)
            RemoteShortcutAction.OPEN_PROFILE_SWITCHER -> {
                if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
                onSwitchProfile()
            }
            RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> {
                if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
                if (playlists.size > 1) showPlaylistPicker = true
            }
            RemoteShortcutAction.CONTINUE_LAST_WATCHED -> continueLastWatched()
            RemoteShortcutAction.NEXT_PLAYLIST -> if (playlists.size > 1) {
                // All → each playlist in turn → All again; the name is said, so a blind press is understood.
                val ids = listOf(-1L) + playlists.map { it.id }
                val next = ids[(ids.indexOf(if (activePlaylistId > 0L) activePlaylistId else -1L) + 1).mod(ids.size)]
                onSelectPlaylist(next)
                localSubToast.show(if (next < 0L) allPlaylistsLabel else playlists.firstOrNull { it.id == next }?.name.orEmpty())
            }
            RemoteShortcutAction.FOCUS_NOW_PLAYING -> enterNowPlaying()
            RemoteShortcutAction.EXPAND_NOW_PLAYING -> if (playerMode == PlayerMode.MINI || playerMode == PlayerMode.AUDIO) expandPlayer()
            RemoteShortcutAction.ENTER_MINI_PLAYER -> if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
            RemoteShortcutAction.ENTER_AUDIO_MODE -> if (playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI) toAudioMode()
            RemoteShortcutAction.PLAY_PAUSE -> if (playerMode != PlayerMode.NONE) dockedEngine.togglePlayPause()
            RemoteShortcutAction.RETURN_TO_LIVE -> if (playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV && timeshifted) liveVm.goToLive()
            RemoteShortcutAction.PREVIOUS_CHANNEL -> if (playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV) liveVm.previousChannel()
            // glcn: the channel list over live TV — from the mini player too; elsewhere, Live TV.
            RemoteShortcutAction.OPEN_CHANNEL_LIST -> when {
                playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV -> {
                    if (playerMode != PlayerMode.FULLSCREEN) expandPlayer()
                    showChannelList = true
                }
                else -> openShortcutSection(MainSection.LIVE_TV)
            }
            RemoteShortcutAction.TOGGLE_FAVORITE -> if (playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV) previewChannel?.let { liveVm.toggleFavorite(it) }
            // glcn: the player's own actions, from the mini player or Audio Mode, bring the player up.
            RemoteShortcutAction.CHANNEL_NEXT,
            RemoteShortcutAction.CHANNEL_PREVIOUS,
            RemoteShortcutAction.RECORD,
            RemoteShortcutAction.SLEEP_TIMER,
            RemoteShortcutAction.TOGGLE_ENGINE,
            RemoteShortcutAction.NEXT_AUDIO_TRACK,
            RemoteShortcutAction.TOGGLE_SUBTITLES,
            RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS,
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS,
            RemoteShortcutAction.OPEN_ASPECT_CONTROLS,
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO,
            -> if (playerMode == PlayerMode.MINI || playerMode == PlayerMode.AUDIO) expandPlayer()
            RemoteShortcutAction.PAGE_TOWARD_FIRST,
            RemoteShortcutAction.PAGE_TOWARD_LAST,
            RemoteShortcutAction.JUMP_TO_FIRST,
            RemoteShortcutAction.JUMP_TO_LAST,
            -> Unit
        }
    }
    val latestRemoteShortcutDispatch by rememberUpdatedState(dispatchRemoteShortcut)
    val glcnPlayerActions = remember {
        setOf(
            RemoteShortcutAction.CHANNEL_NEXT, RemoteShortcutAction.CHANNEL_PREVIOUS, RemoteShortcutAction.RECORD,
            RemoteShortcutAction.SLEEP_TIMER, RemoteShortcutAction.TOGGLE_ENGINE, RemoteShortcutAction.NEXT_AUDIO_TRACK,
            RemoteShortcutAction.TOGGLE_SUBTITLES, RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS,
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS, RemoteShortcutAction.OPEN_ASPECT_CONTROLS,
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO, RemoteShortcutAction.TOGGLE_FAVORITE,
            RemoteShortcutAction.RETURN_TO_LIVE, RemoteShortcutAction.PREVIOUS_CHANNEL, RemoteShortcutAction.PLAY_PAUSE,
        )
    }
    val remoteShortcutEnvironment = remember(remoteShortcutsEnabled, remoteShortcutBindings) {
        RemoteShortcutEnvironment(
            enabled = remoteShortcutsEnabled,
            bindings = remoteShortcutBindings,
            dispatch = { action -> latestRemoteShortcutDispatch(action) },
        )
    }

    LaunchedEffect(sidebarFocus) {
        tv.own.owntv.core.util.Perf.stamp("shell-composed")
        withFrameNanos { }
        focusRail()
    }

    LaunchedEffect(pendingDeepLink, activeProfileId) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        val pid = activeProfileId ?: return@LaunchedEffect
        if (pid < 0) return@LaunchedEffect
        when (deepLink) {
            LauncherDeepLink.OpenLiveSection -> {
                onSelectSection(MainSection.LIVE_TV)
                onDeepLinkConsumed()
            }
            else -> when (val launch = launcherIntegrationRepository.resolveLaunch(pid, deepLink)) {
                is LauncherLaunch.Movie -> {
                    onSelectSection(MainSection.MOVIES)
                    movieVm.play(launch.movie, launch.startPositionMs)
                    openFullscreen(MainSection.MOVIES)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Episode -> {
                    onSelectSection(MainSection.SERIES)
                    seriesVm.playEpisodeQueue(launch.show, launch.queue, launch.episode, launch.startPositionMs)
                    openFullscreen(MainSection.SERIES)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Live -> {
                    onSelectSection(MainSection.LIVE_TV)
                    // A launcher tile has no browse rail, so arm the channel's provider context before
                    // opening the player. A bare ensurePlaying() leaves CH+/CH- with no list to step through.
                    liveVm.watchFullscreen(launch.channel, emptyList())
                    openFullscreen(MainSection.LIVE_TV)
                    onDeepLinkConsumed()
                }
                is LauncherLaunch.Series -> {
                    onSelectSection(MainSection.SERIES)
                    seriesVm.openSeries(launch.show)
                    onDeepLinkConsumed()
                }
                null -> {
                    onDeepLinkConsumed()
                }
            }
        }
    }

    // Stop a leftover live preview when you leave the Live section or the Guide, which previews through it
    // too (but never while fullscreen/mini plays).
    // The preview runs on the ExoPlayer live engine, not mpv: stopping only mpv here (as when the preview
    // was mpv) left it decoding and holding a provider connection after a shortcut or deep link out.
    LaunchedEffect(selectedSection, playerMode) {
        if (selectedSection != MainSection.LIVE_TV && selectedSection != MainSection.EPG && playerMode == PlayerMode.NONE) liveVm.stopPreview()
        if (selectedSection != MainSection.HOME || playerMode != PlayerMode.NONE) homeVm.stopPreview()
    }

    // glcn: Home rebuilds its whole feed only when something may have changed — after playback, another
    // profile or playlist, or a minute away; moving between sections just shows what it already has.
    val homeRefresh = remember { object { var at = 0L; var ctx: List<Any?>? = null; var stale = true } }
    // glcn: a channel just written to the watch history shows in Home's Recent channels straight away (the
    // write can land after Home has already re-read on the way back from the player).
    val sectionNow by androidx.compose.runtime.rememberUpdatedState(selectedSection)
    val profileNow by androidx.compose.runtime.rememberUpdatedState(activeProfileId)
    LaunchedEffect(Unit) {
        liveVm.liveHistoryChanged.collect {
            if (sectionNow == MainSection.HOME && playerMode == PlayerMode.NONE) homeVm.refresh(profileNow)
            else homeRefresh.stale = true
        }
    }
    LaunchedEffect(selectedSection, playerMode, activeProfileId, activePlaylistId, activePlaylistSet) {
        if (playerMode != PlayerMode.NONE) homeRefresh.stale = true
        if (selectedSection == MainSection.HOME && playerMode == PlayerMode.NONE && (activeProfileId?.let { it >= 0 } == true)) {
            val ctx = listOf(activeProfileId, activePlaylistId, activePlaylistSet)
            val t = System.currentTimeMillis()
            if (homeRefresh.stale || ctx != homeRefresh.ctx || t - homeRefresh.at > 60_000L) {
                homeRefresh.stale = false
                homeRefresh.ctx = ctx
                homeRefresh.at = t
                homeVm.refresh(activeProfileId)
            }
        }
    }

    // glcn: what the viewer usually watches becomes reminders for its coming showings — on start and every two hours.
    val habitTracker = koinInject<tv.own.owntv.core.reminder.HabitTracker>()
    LaunchedEffect(activeProfileId) {
        val pid = activeProfileId?.takeIf { it >= 0 } ?: return@LaunchedEffect
        while (true) {
            runCatching { habitTracker.refresh(pid) }
            kotlinx.coroutines.delay(2 * 3_600_000L)
        }
    }

    // The section a Search result opened ("Go to"), so the first Back there returns to Search. Leaving
    // that section any other way forgets it. The series page closes itself on Back (its own handler),
    // so for a series the return follows the show closing.
    fun returnToSearch() {
        searchReturn = null
        onSelectSection(MainSection.SEARCH)
    }
    LaunchedEffect(selectedSection) {
        if (selectedSection != MainSection.SEARCH && selectedSection != searchReturn) searchReturn = null
    }
    val openedSeries by seriesVm.openedSeries.collectAsStateWithLifecycle()
    LaunchedEffect(openedSeries == null) {
        if (openedSeries == null && searchReturn == MainSection.SERIES && selectedSection == MainSection.SERIES) returnToSearch()
        // glcn: a series opened from Home goes back to Home when it closes, not to the Series grid.
        if (openedSeries == null && seriesFromHome) {
            seriesFromHome = false
            if (selectedSection == MainSection.SERIES) { restoreFocus = true; onSelectSection(MainSection.HOME) }
        }
    }
    LaunchedEffect(selectedSection) { if (selectedSection != MainSection.SERIES) seriesFromHome = false }
    // glcn: a film airing on the channel being watched has its full details read a moment after tuning, so its
    // info screen opens with them at once.
    val filmResolver = koinInject<tv.own.owntv.core.metadata.ProgrammeArtResolver>()
    LaunchedEffect(previewChannel?.id, playerMode == PlayerMode.FULLSCREEN) {
        val ch = previewChannel ?: return@LaunchedEffect
        if (playerMode != PlayerMode.FULLSCREEN || zapSource != MainSection.LIVE_TV) return@LaunchedEffect
        kotlinx.coroutines.delay(FILM_WARM_MS)
        val p = runCatching { liveVm.nowFull(ch).first }.getOrNull() ?: return@LaunchedEffect
        runCatching { filmResolver.filmFor(p.title, p.categories) }
    }
    // glcn: a film chosen on Home — its page over Home, played from there; Back returns to Home.
    homeFilm?.let { film ->
        val metaRepo = koinInject<tv.own.owntv.core.metadata.MetadataRepository>()
        val meta by androidx.compose.runtime.produceState<tv.own.owntv.core.database.entity.MetadataCacheEntity?>(null, film.id) {
            value = runCatching { metaRepo.resolveMovie(film) }.getOrNull()
        }
        val mode by movieVm.metadataMode.collectAsStateWithLifecycle()
        val overridden by movieVm.overriddenMovies.collectAsStateWithLifecycle()
        val favs by movieVm.favoriteIds.collectAsStateWithLifecycle()
        androidx.compose.runtime.key(film.id) {
            tv.own.owntv.features.movies.MovieTitlePage(
                movie = film,
                meta = meta,
                info = tv.own.owntv.features.movies.movieTitleInfo(film, meta, mode.tmdbWins || film.id in overridden),
                favorite = film.id in favs,
                resumeMs = { movieVm.savedPositionMs(film) },
                onPlay = { pos ->
                    homeFilm = null
                    scope.launch { if (movieVm.playByIdAsync(film.id, pos) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) }
                },
                loadVersions = { movieVm.versionsOf(film) },
                onPlayVersion = { v ->
                    homeFilm = null
                    scope.launch { if (movieVm.playByIdAsync(v.id, 0L) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) }
                },
                onToggleFavorite = { movieVm.toggleFavorite(film) },
                onDismiss = { homeFilm = null },
                loadRelated = { movieVm.relatedMoviesOf(film) },
                onOpenRelated = { r -> homeFilm = r },
            )
        }
    }

    // glcn: with "Back in the player: Window" on, holding Back in the full-screen player closes it outright (back to the
    // menu, no window) — for when you have finished watching. Watched at the window ([tv.own.owntv.glcn.BackHold]).
    val holdArmed = playerMode == PlayerMode.FULLSCREEN && backToWindow && zapSource == MainSection.LIVE_TV &&
        !showChannelList && !showQuickGuide && !showHistoryList && zapOptionsChannel == null && !showTvStrip
    val holdArmedNow by androidx.compose.runtime.rememberUpdatedState(holdArmed)
    // glcn: the live channel in its window after a Back — a second Back right away closes it (see BackHold).
    val windowOpenNow by androidx.compose.runtime.rememberUpdatedState(playerMode == PlayerMode.MINI && zapSource == MainSection.LIVE_TV)
    val exitNow by androidx.compose.runtime.rememberUpdatedState(exitPlayer)
    DisposableEffect(Unit) {
        tv.own.owntv.glcn.BackHold.armed = { holdArmedNow }
        tv.own.owntv.glcn.BackHold.windowOpen = { windowOpenNow }
        // A full stop: closing the player alone leaves a live channel playing on in the preview (the "window").
        tv.own.owntv.glcn.BackHold.onHold = {
            exitNow(); liveVm.stopForGood()
            // Whatever the closing set going again a moment later (the preview pane re-taking the engine) is stopped too.
            scope.launch { kotlinx.coroutines.delay(HOLD_SETTLE_MS); if (playerMode == PlayerMode.NONE) liveVm.stopForGood() }
        }
        onDispose { tv.own.owntv.glcn.BackHold.armed = { false } }
    }

    BackHandler {
        when {
            // glcn: "Back in the player: Window" — a live channel goes to the mini player instead of stopping.
            playerMode == PlayerMode.FULLSCREEN ->
                if (backToWindow && zapSource == MainSection.LIVE_TV && !catchupActive) dockPlayer() else exitPlayer()
            showAvatarPicker -> showAvatarPicker = false
            showPlaylistPicker -> showPlaylistPicker = false
            showExit -> showExit = false
            // Settings is reached through More now, so Back out of its root goes back there — one
            // level out, not two. `SettingsScreen` passes its own `onBack` for the same purpose, but
            // its root handler does not always win against this one, which left Back looking dead on
            // the first press: the fallback below simply moved focus to the rail and nothing else
            // happened. Handled here as well so the answer is the same whichever handler fires; a
            // sub-screen of Settings still wins, because its handler is composed deeper than this.
            selectedSection == MainSection.SETTINGS -> { restoreFocus = true; onSelectSection(MainSection.MORE) }
            // Reached from a Search result: Back returns to Search once, its query, tab and row kept.
            searchReturn == selectedSection && focusedLayer != ShellLayer.SIDEBAR -> returnToSearch()
            // glcn: a live channel still playing in its little window: Back on the menu closes it first (before any
            // "exit the app?"). Back from the player docks it and puts the focus on the menu, so Back, Back closes a
            // channel for good with any remote — a TV's own over HDMI cannot hold Back.
            focusedLayer == ShellLayer.SIDEBAR && playerMode == PlayerMode.MINI && zapSource == MainSection.LIVE_TV -> {
                exitPlayer(); liveVm.stopForGood()
            }
            focusedLayer == ShellLayer.SIDEBAR -> showExit = true
            else -> focusRail()
        }
    }

    // With Glass on, the shell's own base paints must be transparent so the background (rendered in
    // MainActivity behind this shell) shows through the gaps between and around panels. Solid otherwise.
    val shellBase = if (LocalGlass.current.enabled) Color.Transparent else tv.own.owntv.ui.theme.LocalStagePalette.current.page

    // Long-press Back (the second way in). Deliberately timed from the FIRST KeyDown rather than counted from
    // key repeats: several TVs re-fire KeyDown while a key is held, so a repeat-counting version fires
    // the long-press the instant Back is touched. Short Back is never consumed, so every existing Back
    // handler — HUD hide, search clear, dialog dismiss, direct-tune cancel — behaves exactly as before.
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var backHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var backLongFired by remember { mutableStateOf(false) }
    var shortcutKeyCode by remember { mutableStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var shortcutLongFired by remember { mutableStateOf(false) }
    var shortcutHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val longPressBackArmed = playerMode == PlayerMode.MINI && !showExit && !showAvatarPicker && !showPlaylistPicker
    // Media keys (the third way in) act on the docked window wherever focus is — but only if nothing nearer
    // claimed them first, which is why this is a bubbling handler: a focused browse list still gets its
    // CH+/CH− paging, and the full-screen HUD still owns zapping.
    val dockedZap: ((Int) -> Unit)? = when {
        playerMode != PlayerMode.MINI -> null
        zapSource == MainSection.LIVE_TV && liveCanZap && (liveOnExo || player.isLiveContent) -> liveVm::zap
        else -> null
    }

    // glcn: the Live category sheets list the playlists that carry channels inside the app-wide scope.
    val liveSheetPlaylists = remember(playlists, activePlaylistId, activePlaylistSet) {
        playlists.filter { it.syncLive && (if (activePlaylistSet.isNotEmpty()) it.id in activePlaylistSet else (activePlaylistId <= 0L || it.id == activePlaylistId)) }
    }
    val liveOnlyId by tv.own.owntv.glcn.LiveListFilter.id.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        // glcn: every row, grid and list glides to the focus with one eased slide.
        tv.own.owntv.glcn.SmoothScroll.provided(),
        LocalRemoteShortcuts provides remoteShortcutEnvironment,
        // glcn: Settings › Live TV › Channel numbers, read by every place that prints one.
        tv.own.owntv.glcn.LocalShowChannelNumbers provides directTuneEnabled,
        tv.own.owntv.features.shell.components.LocalClockWeather provides (weatherInfo to weatherFahrenheit),
        // glcn: the category sheets list the playlists when there are several.
        tv.own.owntv.features.live.LocalCategoryPlaylists provides (
            liveSheetPlaylists.takeIf { it.size > 1 }?.let { tv.own.owntv.features.live.CategoryPlaylists(it, liveOnlyId.takeIf { id -> it.any { p -> p.id == id } } ?: -1L, tv.own.owntv.glcn.LiveListFilter::set) }
        ),
    ) {
    Box(
        modifier = modifier.fillMaxSize().background(shellBase)
            .onPreviewKeyEvent { e ->
                // glcn: a trailer playing behind a page — Play/Pause turns its sound on or off; OK hands it to the page it opens.
                if (tv.own.owntv.features.home.CinemaTrailer.onKey(e)) return@onPreviewKeyEvent true
                // glcn: Play/Pause accepts the reminder card; OK does too while the player is full screen (there it only
                // opens the controls). Anywhere else OK keeps working the screen underneath.
                reminderAccept?.let { accept ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if ((e.key == Key.MediaPlayPause || (ok && playerMode == PlayerMode.FULLSCREEN)) && e.type == KeyEventType.KeyDown) {
                        accept()
                        return@onPreviewKeyEvent true
                    }
                }
                if (e.type == KeyEventType.KeyDown) {
                    if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) railKeyAt = android.os.SystemClock.uptimeMillis()
                    // A key pressed on the rail is the user using it, however focus got there.
                    if (railHasFocus) railFocused = true
                }
                // glcn: a remote's microphone button (the Search key on most remotes but the SHIELD's): Search, listening.
                if (e.key == Key.Search || e.key == Key.VoiceAssist) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        if (tv.own.owntv.features.search.VoiceTrigger.typed.value == null) tv.own.owntv.features.search.VoiceTrigger.request()
                        if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
                        onSelectSection(MainSection.SEARCH)
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.key != Key.Back) return@onPreviewKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        // glcn: with "Back in the player: Window" on, holding Back in the full-screen player closes it
                        // outright instead (back to the menu, no window), for when you have finished watching.
                        // (Holding Back to close the full-screen player is watched at the window: see BackHold below.)
                        if (longPressBackArmed && backHoldJob == null) {
                            backLongFired = false
                            backHoldJob = scope.launch {
                                kotlinx.coroutines.delay(600)
                                backLongFired = true
                                toggleNowPlayingFocus()
                            }
                        }
                        false
                    }
                    KeyEventType.KeyUp -> {
                        backHoldJob?.cancel()
                        backHoldJob = null
                        val fired = backLongFired
                        backLongFired = false
                        fired // consume ONLY when the long-press already acted, so Back isn't run twice
                    }
                    else -> false
                }
            }
            .onKeyEvent { e ->
                val shortcutKey = e.nativeKeyEvent.keyCode
                val shellBindings = remoteShortcutBindings.filter {
                    it.keyCode == shortcutKey &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_FIRST &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_LAST &&
                        it.action != RemoteShortcutAction.JUMP_TO_FIRST &&
                        it.action != RemoteShortcutAction.JUMP_TO_LAST &&
                        // glcn: a player action with no player leaves the button free (it was swallowed).
                        (it.action !in glcnPlayerActions || playerMode != PlayerMode.NONE) &&
                        // glcn: "enlarge" only takes its button while there is something to enlarge.
                        (it.action != RemoteShortcutAction.EXPAND_NOW_PLAYING ||
                            playerMode == PlayerMode.MINI || playerMode == PlayerMode.AUDIO)
                }
                if (remoteShortcutsEnabled && shellBindings.isNotEmpty()) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (shortcutKeyCode != shortcutKey) {
                                shortcutHoldJob?.cancel()
                                shortcutKeyCode = shortcutKey
                                shortcutLongFired = false
                                shellBindings.firstOrNull { it.press == tv.own.owntv.core.settings.RemoteShortcutPress.LONG }
                                    ?.let { binding ->
                                        shortcutHoldJob = scope.launch {
                                            kotlinx.coroutines.delay(600)
                                            if (shortcutKeyCode == shortcutKey) {
                                                shortcutLongFired = true
                                                dispatchRemoteShortcut(binding.action)
                                            }
                                        }
                                    }
                            }
                            return@onKeyEvent true
                        }
                        KeyEventType.KeyUp -> if (shortcutKeyCode == shortcutKey) {
                            shortcutHoldJob?.cancel()
                            shortcutHoldJob = null
                            if (!shortcutLongFired) {
                                shellBindings.firstOrNull { it.press == tv.own.owntv.core.settings.RemoteShortcutPress.SHORT }
                                    ?.let { dispatchRemoteShortcut(it.action) }
                            }
                            shortcutKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
                            shortcutLongFired = false
                            return@onKeyEvent true
                        }
                    }
                }
                // Back that bubbled all the way up here is a Back nothing nearer wanted as a key, so it
                // is ours to turn into a back-press. Compose would otherwise map it to
                // FocusDirection.Exit and spend it moving focus one level out of whatever focus target
                // holds it — silently, because the highlight usually does not visibly move. That
                // consumed the KeyDown before Activity.onKeyDown could startTracking(), so the KeyUp
                // was discarded as untracked and NO BackHandler ran: Back out of Settings looked dead
                // until a second press, by which time the exit had nowhere left to go. Running the
                // dispatcher here keeps the answer the same on the first press, everywhere.
                // Deeper key handlers (a text field's Back-to-close, the direct-tune cancel) still win,
                // because this is a bubbling handler and they have already declined by the time it runs.
                if (e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyUp && !e.nativeKeyEvent.isCanceled) backDispatcher?.onBackPressed()
                    return@onKeyEvent backDispatcher != null
                }
                if (e.type != KeyEventType.KeyDown || playerMode != PlayerMode.MINI) return@onKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { dockedEngine.togglePlayPause(); true }
                    Key.ChannelUp -> dockedZap?.let { it(tv.own.owntv.player.zapDirection(up = true, playerRemoteOptions)); true } ?: false
                    Key.ChannelDown -> dockedZap?.let { it(tv.own.owntv.player.zapDirection(up = false, playerRemoteOptions)); true } ?: false
                    else -> false
                }
            },
    ) {
      // glcn: the fluency meter (Settings), above everything, in the top-right corner.
      if (frameMeterOn) {
          val meterScreen = stringResource(selectedSection.labelRes) +
              if (playerMode == PlayerMode.FULLSCREEN) " · " + stringResource(R.string.glcn_fps_player) else ""
          tv.own.owntv.glcn.FrameMeter(meterScreen, Modifier.align(Alignment.TopEnd).padding(top = 12.mpx, end = 24.mpx).then(Modifier.zIndex(1000f)))
      }
      // Browse UI — hidden while the player is fullscreen (stays visible behind the docked mini-player).
      if (playerMode != PlayerMode.FULLSCREEN) {
        Column(
            modifier = Modifier.fillMaxSize()
                // One group with a restorer: leaving for the mini player and coming back lands on the
                // exact control that was focused, without every screen having to remember its own.
                .focusRequester(shellContentFocus)
                .focusRestorer()
                .focusGroup(),
        ) {
          if (isOffline) OfflineBanner()
          val selectFromNav: (MainSection) -> Unit = { section ->
              if (trendingSearchActive || section == MainSection.SEARCH) {
                  searchVm.setQuery("")
                  trendingSearchActive = false
                  restoreTrendingSearchFocus = false
              }
              onSelectSection(section)
          }
          // Audio Mode: the now-playing bar — in the top bar left of the weather chip, or in the Stage
          // cluster in the Continue pill's place. Present only while PlayerMode.AUDIO.
          val audioBarContent: (@Composable () -> Unit)? = if (playerMode == PlayerMode.AUDIO) {
              {
                  val isLiveStream = liveOnExo || player.isLiveContent
                  val zapFn: ((Int) -> Unit)? = when {
                      !isLiveStream -> null
                      zapSource == MainSection.LIVE_TV && liveCanZap -> liveVm::zap
                      else -> null
                  }
                  val audioEngine = if (liveOnExo) liveVm.previewEngine else mpvEngine
                  val vodNav by audioEngine.nav.collectAsStateWithLifecycle()
                  tv.own.owntv.player.AudioNowPlayingBar(
                      player = audioEngine,
                      isLive = isLiveStream,
                      canPrev = if (isLiveStream) zapFn != null else vodNav.hasPrev,
                      canNext = if (isLiveStream) zapFn != null else vodNav.hasNext,
                      onPrev = { if (isLiveStream) zapFn?.invoke(-1) else mpvEngine.previous() },
                      onNext = { if (isLiveStream) zapFn?.invoke(1) else mpvEngine.next() },
                      onExpand = expandPlayer,
                      onClose = exitPlayer,
                      // Always reachable while Audio Mode is active (from the Search/Continue
                      // pills on the left or the playlist chip on the right) — not gated on the
                      // nav panel like the other chips, because its own D-pad trap keeps focus
                      // inside once entered and Back is the only way out.
                      focusable = true,
                      entryFocusRequester = audioEntryFocus,
                      favorite = favActive,
                      onToggleFavorite = favToggle,
                  )
              }
          } else null
          Box(Modifier.weight(1f).fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    // Transparent while Glass is on, so the picture or the Stage page shows through the gaps
                    // between the panels; the page colour otherwise.
                    .background(shellBase)
                    // The Stage page, across the whole width — under a docked rail's reserve too.
                    // Over a picture it draws only the Accent light; the picture shows through.
                    .stageBackground(tv.own.owntv.ui.theme.stageAccent.accent),
            ) {
                // Live TV's view (List / Guide), and how a guide — the TV Guide or Live TV's Guide view —
                // starts a channel or an archive programme: through LiveViewModel, the one live path.
                val liveView by liveVm.liveView.collectAsStateWithLifecycle()
                val guidePlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit = { ch, _ ->
                    restoreFocus = false
                    liveVm.watchFromGuide(ch)
                    zapSource = MainSection.LIVE_TV
                    homeVm.stopPreview()
                    // Live TV set to play externally → the channel went to another app;
                    // don't mount the fullscreen player over it.
                    if (playerMode != PlayerMode.MINI && !liveVm.externalPlayerOn.value) playerMode = PlayerMode.FULLSCREEN
                }
                val guidePlayCatchup: (ChannelEntity, tv.own.owntv.core.database.entity.EpgProgrammeEntity) -> Unit = { ch, prog ->
                    restoreFocus = false
                    liveVm.playCatchupProgramme(ch, prog)
                    zapSource = MainSection.LIVE_TV
                    homeVm.stopPreview()
                    if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
                }
                // glcn: films and series chosen on Home open their page (Keep watching and Trending too) when "Page before
                // watching" is on; the page's own Play / Resume plays.
                val homeMovieDao: tv.own.owntv.core.database.dao.MovieDao = org.koin.compose.koinInject()
                val openContinueMovie: ((Long) -> Unit)? = if (!tv.own.owntv.glcn.DockPrefs.titlePage) null else { id ->
                    scope.launch {
                        val m = runCatching { homeMovieDao.getById(id) }.getOrNull()
                        if (m != null) homeFilm = m
                        else if (movieVm.playByIdAsync(id, 0L) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES)
                    }
                }
                val openContinueSeries: ((Long) -> Unit)? = if (!tv.own.owntv.glcn.DockPrefs.titlePage) null else { id ->
                    seriesVm.openSeriesById(id)
                    restoreFocus = true
                    seriesFromHome = true
                    onSelectSection(MainSection.SERIES)
                }
                // glcn: a new section comes in with a slight slide — no hard cut between screens.
                val sectionIn = remember { androidx.compose.animation.core.Animatable(1f) }
                val sectionMotion = tv.own.owntv.ui.theme.animationsOn && !tv.own.owntv.glcn.LowRam.on && !tv.own.owntv.glcn.Accessible.on
                val sectionSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(SECTION_IN_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                LaunchedEffect(selectedSection) {
                    if (!sectionMotion) return@LaunchedEffect
                    sectionIn.snapTo(0f)
                    sectionIn.animateTo(1f, sectionSpec)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .graphicsLayer {
                            val t = sectionIn.value
                            // A slide only: no transparency, so the whole screen is never drawn twice while it loads.
                            if (t < 1f) translationX = (1f - t) * SECTION_IN_SLIDE.toPx()
                        }
                        .then(
                            // The floating rail sits over the content, so the content starts at the screen
                            // edge (or beside a docked rail).
                            Modifier
                                .padding(
                                    start = when {
                                        navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED ->
                                            tv.own.owntv.features.shell.components.railDockedReserve(navSize)
                                        else -> 0.dp
                                    },
                                    top = when {
                                        // glcn: the top bar takes the band the clock sits in; Home already starts below it.
                                        navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP && selectedSection != MainSection.HOME -> 96.mpx
                                        else -> 0.dp
                                    },
                                )
                                // ◀ with nothing further left in the content goes back to the rail, however the
                                // content was entered. The D-pad search alone missed it whenever the open rail
                                // overlapped the content's left edge, which left Back as the only way out.
                                .onKeyEvent { e ->
                                    val back = if (contentLtr) Key.DirectionLeft else Key.DirectionRight
                                    if (e.key != back || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    if (!contentFocusManager.moveFocus(if (contentLtr) androidx.compose.ui.focus.FocusDirection.Left else androidx.compose.ui.focus.FocusDirection.Right)) {
                                        focusRail()
                                    }
                                    true
                                }
                                .focusRequester(contentAreaFocus)
                                .focusRestorer()
                                .focusGroup(),
                        ),
                ) {
                    when {
                        // More — the hub the rail's last item opens. Settings is a row in it.
                        selectedSection == MainSection.MORE -> tv.own.owntv.features.more.MoreScreen(
                            profileName = profileName.ifBlank { stringResource(R.string.common_own_tv_user) },
                            playlistLabel = playlistLabel,
                            avatarId = avatarId,
                            avatarPath = avatarPath,
                            onSwitchProfile = onSwitchProfile,
                            onPickAvatar = { showAvatarPicker = true },
                            onOpenSettings = { group, search ->
                                settingsStart = tv.own.owntv.features.shell.components.SettingsStart(group, search)
                                onSelectSection(MainSection.SETTINGS)
                            },
                            onOpenLanguage = {
                                settingsStart = tv.own.owntv.features.shell.components.SettingsStart(
                                    tv.own.owntv.features.shell.components.SettingsGroup.APP.ordinal, false, language = true,
                                )
                                onSelectSection(MainSection.SETTINGS)
                            },
                            // The same paths Home uses, so the engine ladder, resume and Continue all apply.
                            onPlayChannel = { id -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id)) openFullscreen(MainSection.LIVE_TV) } },
                            onPlayMovie = { id, pos -> scope.launch { if (movieVm.playByIdAsync(id, pos) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) } },
                            onPlayEpisode = { seriesId, epId, pos -> scope.launch { if (seriesVm.playFromHomeAsync(seriesId, epId, pos) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES) } },
                            onOpenSeries = { id -> seriesVm.openSeriesById(id); restoreFocus = true; onSelectSection(MainSection.SERIES) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            onEntryHook = { moreEntry = it },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.SETTINGS -> tv.own.owntv.features.settings.lock.SettingsPinGate(onDismiss = { settingsStart = null; openEpgAdd = false; restoreFocus = true; onSelectSection(shellVm.sectionToLeaveLockedSettings()) }) { SettingsScreen(
                            themeMode = themeMode,
                            uiZoomPercent = uiZoomPercent,
                            onSetZoom = onSetZoom,
                            fontCustomization = fontCustomization,
                            onSetFontCustomization = onSetFontCustomization,
                            onOpenPlaylist = {},
                            // Settings is reached through More now, so Back out of its root goes
                            // back there rather than to the rail — one level out, not two.
                            onBack = { restoreFocus = true; onSelectSection(MainSection.MORE) },
                            openEpgAdd = openEpgAdd,
                            onEpgAddConsumed = { openEpgAdd = false },
                            start = settingsStart,
                            onStartConsumed = { settingsStart = null },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        ) }

                        // glcn: the Dock layout — the focused title over its picture and a glass dock with tabs.
                        // glcn (TV profile): a Home of its own, only TV — the programme's picture filling the screen.
                        selectedSection == MainSection.HOME && tv.own.owntv.glcn.TvProfiles.isTv(activeProfileId) -> tv.own.owntv.features.home.TvHome(
                            load = { liveVm.tvStripGroups() },
                            loadNow = { liveVm.nowFull(it) },
                            onTune = { ch, list -> scope.launch { if (liveVm.ensurePlayingByIdAsync(ch.id, list)) openFullscreen(MainSection.LIVE_TV) } },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING) 150.mpx else 64.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )
                        selectedSection == MainSection.HOME && homeLayout == 3 -> tv.own.owntv.features.home.LumenHome(
                            vm = homeVm,
                            onHideRow = { homeVm.hideRow(it) },
                            onPlayMovie = { id, pos -> scope.launch { if (movieVm.playByIdAsync(id, pos) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) } },
                            onPlayEpisode = { seriesId, epId, pos -> scope.launch { if (seriesVm.playFromHomeAsync(seriesId, epId, pos) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES) } },
                            onPlayChannel = { id, zap -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id, zap)) openFullscreen(MainSection.LIVE_TV) } },
                            onActivateTrending = { selected, onUnavailable ->
                                scope.launch {
                                    when (val current = homeVm.revalidateTrendingItem(selected)) {
                                        is TrendingHomeItem.Movie -> {
                                            if (tv.own.owntv.glcn.DockPrefs.titlePage) {
                                                homeFilm = current.movie
                                            } else {
                                                val played = movieVm.playByIdAsync(current.movie.id)
                                                if (!played) onUnavailable()
                                                else if (!movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES)
                                            }
                                        }
                                        is TrendingHomeItem.Series -> {
                                            seriesVm.openSeries(current.series)
                                            restoreFocus = true
                                            seriesFromHome = true
                                            onSelectSection(MainSection.SERIES)
                                        }
                                        null -> onUnavailable()
                                    }
                                }
                            },
                            onOpenMovie = { movie ->
                                // glcn: with "Page before watching" on, a film chosen on Home opens its page right here.
                                if (tv.own.owntv.glcn.DockPrefs.titlePage) homeFilm = movie
                                else {
                                    restoreFocus = false
                                    movieVm.revealMovie(movie)
                                    onSelectSection(MainSection.MOVIES)
                                }
                            },
                            onOpenSeries = { series ->
                                seriesVm.openSeries(series)
                                restoreFocus = true
                                seriesFromHome = true
                                onSelectSection(MainSection.SERIES)
                            },
                            onOpenContinueMovie = openContinueMovie,
                            onOpenContinueSeries = openContinueSeries,
                            onOpenMatch = { m ->
                                sportsMatchRequests.pendingMatch.value = m to null
                                onSelectSection(MainSection.SPORTS)
                            },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false; restoreTrendingSearchFocus = false },
                            firstRowFocusRequester = homeFirstRowFocus,
                            onEntryHook = { homeEntry = it },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING) 150.mpx else 64.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.HOME -> HomeScreen(
                            vm = homeVm,
                            seasonActive = playerMode != PlayerMode.FULLSCREEN,
                            // Skip the fullscreen player when the global external-player toggle is on
                            // (mounting it spins up mpv even though playback went to the external app).
                            onPlayMovie = { id, pos -> scope.launch { if (movieVm.playByIdAsync(id, pos) && !movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES) } },
                            onPlayEpisode = { seriesId, epId, pos -> scope.launch { if (seriesVm.playFromHomeAsync(seriesId, epId, pos) && !seriesVm.externalPlayerOn.value) openFullscreen(MainSection.SERIES) } },
                            onPlayChannel = { id, zap -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id, zap)) openFullscreen(MainSection.LIVE_TV) } },
                            onActivateTrending = { selected, onUnavailable ->
                                scope.launch {
                                    when (val current = homeVm.revalidateTrendingItem(selected)) {
                                        is TrendingHomeItem.Movie -> {
                                            if (tv.own.owntv.glcn.DockPrefs.titlePage) {
                                                homeFilm = current.movie
                                            } else {
                                                val played = movieVm.playByIdAsync(current.movie.id)
                                                if (!played) onUnavailable()
                                                else if (!movieVm.externalPlayerOn.value) openFullscreen(MainSection.MOVIES)
                                            }
                                        }
                                        is TrendingHomeItem.Series -> {
                                            seriesVm.openSeries(current.series)
                                            restoreFocus = true
                                            seriesFromHome = true
                                            onSelectSection(MainSection.SERIES)
                                        }
                                        null -> onUnavailable()
                                    }
                                }
                            },
                            onOpenMovie = { movie ->
                                // glcn: with "Page before watching" on, a film chosen on Home opens its page right here.
                                if (tv.own.owntv.glcn.DockPrefs.titlePage) homeFilm = movie
                                else {
                                    restoreFocus = false
                                    movieVm.revealMovie(movie)
                                    onSelectSection(MainSection.MOVIES)
                                }
                            },
                            onOpenSeries = { series ->
                                seriesVm.openSeries(series)
                                restoreFocus = true
                                seriesFromHome = true
                                onSelectSection(MainSection.SERIES)
                            },
                            onOpenContinueMovie = openContinueMovie,
                            onOpenContinueSeries = openContinueSeries,
                            onOpenTrendingSearch = { query ->
                                searchVm.setQuery(query)
                                trendingSearchActive = true
                                restoreTrendingSearchFocus = false
                                onSelectSection(MainSection.SEARCH)
                            },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            restoreTrendingSearchFocus = restoreTrendingSearchFocus,
                            onRestored = {
                                restoreFocus = false
                                restoreTrendingSearchFocus = false
                            },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            firstRowFocusRequester = homeFirstRowFocus,
                            // Beside the resting capsule; a docked rail already reserves its own width.
                            onEntryHook = { homeEntry = it },
                            onOpenMatch = { m ->
                                sportsMatchRequests.pendingMatch.value = m to null
                                onSelectSection(MainSection.SPORTS)
                            },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING) 150.mpx else 64.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SEARCH -> SearchScreen(
                            vm = searchVm,
                            liveVm = liveVm,
                            previewEnabled = playerMode == PlayerMode.NONE,
                            // OK on a result goes to it and Back comes back here once
                            // (searchReturn). The series page is the same SeriesViewModel the Series
                            // section shows; a channel or movie is revealed in its own category.
                            onOpenSeries = { series ->
                                seriesVm.openSeries(series)
                                searchReturn = MainSection.SERIES
                                onSelectSection(MainSection.SERIES)
                            },
                            onGoToChannel = { ch ->
                                restoreFocus = false
                                if (liveView == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE) {
                                    liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST)
                                }
                                liveVm.revealChannel(ch)
                                searchReturn = MainSection.LIVE_TV
                                onSelectSection(MainSection.LIVE_TV)
                            },
                            onGoToMovie = { movie ->
                                restoreFocus = false
                                movieVm.revealMovie(movie)
                                searchReturn = MainSection.MOVIES
                                onSelectSection(MainSection.MOVIES)
                            },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            returnToHomeOnBack = trendingSearchActive,
                            onReturnToHome = {
                                trendingSearchActive = false
                                restoreTrendingSearchFocus = true
                                restoreFocus = false
                                onSelectSection(MainSection.HOME)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.LIVE_TV && liveView == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE -> EpgScreen(
                            onPlayChannel = guidePlayChannel,
                            onPlayCatchup = guidePlayCatchup,
                            onAddEpg = { openEpgAdd = true; onSelectSection(MainSection.SETTINGS) },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            liveMode = true,
                            onEntryHook = { guideEntry = it },
                            onListView = { liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.LIVE_TV -> LiveScreen(
                            onFullscreen = { openFullscreen() },
                            onOpenGuide = { liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.MOVIES -> MoviesScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            onEntryHook = { vodEntry = it },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SERIES -> SeriesScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            onEntryHook = { vodEntry = it },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.DOWNLOADS -> DownloadsScreen(
                            onFullscreen = { openFullscreen() },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            onOpenGuide = { onSelectSection(MainSection.EPG) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            onEntryHook = { downloadsEntry = it },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING) 150.mpx else 64.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SPORTS -> tv.own.owntv.features.sports.SportsScreen(
                            onPlayChannel = { ch, zap -> scope.launch { if (liveVm.ensurePlayingByIdAsync(ch.id, zap)) openFullscreen(MainSection.LIVE_TV) } },
                            onAddPlaylist = { onSelectSection(MainSection.SETTINGS) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            onEntryHook = { sportsEntry = it },
                            active = playerMode == PlayerMode.NONE,
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED) 64.mpx else 150.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.EPG -> EpgScreen(
                            onFullscreen = { openFullscreen() },
                            onPlayChannel = guidePlayChannel,
                            onPlayCatchup = guidePlayCatchup,
                            previewEnabled = playerMode == PlayerMode.NONE,
                            onEntryHook = { guideEntry = it },
                            onAddEpg = { openEpgAdd = true; onSelectSection(MainSection.SETTINGS) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                    }
                }
            }
          }
            val clusterDensity = androidx.compose.ui.platform.LocalDensity.current
            val continueMax: androidx.compose.ui.unit.Dp? = run {
                val bar = topBarBounds
                val pl = playlistPillBounds
                if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP && bar != null && pl != null) {
                    with(clusterDensity) { ((pl.left - bar.right) - 28.mpx.toPx()).toDp() }
                } else null
            }
            if (showRecentDrop) tv.own.owntv.features.shell.components.RecentChannelsDrop(
                load = { liveVm.historyChannels() },
                loadNow = { liveVm.nowFull(it) },
                onPlay = { ch -> showRecentDrop = false; scope.launch { if (liveVm.ensurePlayingByIdAsync(ch.id)) openFullscreen(MainSection.LIVE_TV) } },
                onDismiss = { showRecentDrop = false },
            )
            tv.own.owntv.features.shell.components.StageStatusCluster(
                continueMaxWidth = continueMax,
                continuePill = continueTarget?.let { target ->
                    val live = target.kind == tv.own.owntv.features.home.ContinueKind.LIVE
                    tv.own.owntv.features.shell.components.ContinuePill(
                        icon = if (live) OwnTVIcon.LIVE_TV else OwnTVIcon.PLAY,
                        iconFilled = !live,
                        action = stringResource(
                            when (target.action) {
                                tv.own.owntv.features.home.ContinueAction.RESUME -> R.string.content_action_resume
                                tv.own.owntv.features.home.ContinueAction.PLAY -> R.string.content_action_play
                                tv.own.owntv.features.home.ContinueAction.NEXT_UP -> R.string.content_action_next_episode
                                tv.own.owntv.features.home.ContinueAction.LAST_CHANNEL -> R.string.content_action_last_channel
                            },
                        ),
                        title = target.name,
                        onClick = continueLastWatched,
                        onDown = if (live) ({ showRecentDrop = true }) else null,
                    )
                },
                audioBar = audioBarContent,
                playlistLabel = playlistLabel,
                playlistInteractive = playlists.size > 1,
                onPlaylistClick = { showPlaylistPicker = true },
                // Out of reach from deep in a list: from the rail or on Home only.
                pillsFocusable = focusedLayer == ShellLayer.SIDEBAR || clusterFocused || selectedSection == MainSection.HOME,
                weatherInfo = weatherInfo,
                weatherFahrenheit = weatherFahrenheit,
                clockColors = clockColors,
                playlistDownFocusRequester = homeFirstRowFocus.takeIf { selectedSection == MainSection.HOME },
                onPlaylistPillBounds = { playlistPillBounds = it },
                modifier = Modifier.align(Alignment.TopEnd).onFocusChanged { clusterFocused = it.hasFocus }.focusGroup(),
            )
            if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP) tv.own.owntv.features.shell.components.StageTopBar(
                selected = selectedSection,
                visibleSections = navSections,
                onSelect = selectFromNav,
                profileName = profileName.ifBlank { stringResource(R.string.common_own_tv_user) },
                onSwitchProfile = onSwitchProfile,
                onPickAvatar = { showAvatarPicker = true },
                avatarId = avatarId,
                avatarPath = avatarPath,
                selectedItemFocusRequester = sidebarFocus,
                contentFocusRequester = contentAreaFocus,
                enterContent = when (selectedSection) {
                    MainSection.HOME -> homeEntry
                    MainSection.EPG, MainSection.LIVE_TV -> guideEntry
                    MainSection.MOVIES, MainSection.SERIES -> vodEntry
                    MainSection.DOWNLOADS -> downloadsEntry
                    MainSection.MORE -> moreEntry
                    else -> null
                },
                onFocused = { focusedLayer = ShellLayer.SIDEBAR },
                nowPlaying = nowPlayingRail,
                onNowPlaying = enterNowPlaying,
                onBarBounds = { topBarBounds = it },
                size = if (topCompactForced) tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT else topSize,
                hidden = railIdleHidden,
            ) else tv.own.owntv.features.shell.components.StageRail(
                state = railState,
                size = railSize,
                length = navLength,
                selected = selectedSection,
                visibleSections = navSections,
                onSelect = selectFromNav,
                count = { section -> railCounts[section]?.takeIf { it > 0 } },
                detail = { section -> railDetail(section, railDetails) },
                profileName = profileName.ifBlank { stringResource(R.string.common_own_tv_user) },
                profileLine = stringResource(R.string.shell_rail_profile_line, playlistLabel),
                onSwitchProfile = onSwitchProfile,
                onPickAvatar = { showAvatarPicker = true },
                avatarId = avatarId,
                avatarPath = avatarPath,
                selectedItemFocusRequester = sidebarFocus,
                contentFocusRequester = contentAreaFocus,
                enterContent = when (selectedSection) {
                    MainSection.HOME -> homeEntry
                    MainSection.EPG, MainSection.LIVE_TV -> guideEntry
                    MainSection.MOVIES, MainSection.SERIES -> vodEntry
                    MainSection.DOWNLOADS -> downloadsEntry
                    MainSection.MORE -> moreEntry
                    MainSection.SPORTS -> sportsEntry
                    else -> null
                },
                onFocused = { focusedLayer = ShellLayer.SIDEBAR },
                nowPlaying = nowPlayingRail,
                onNowPlaying = enterNowPlaying,
            )
          }
        }
        // The solid-mode wizard aura is deliberately drawn after the opaque browse surfaces so it
        // remains visible, exactly like the approved concept. It never intercepts input; fullscreen
        // video and glass mode skip it entirely. Global Animations Off also freezes the slow pulse.
        SolidAmbientBackdrop(
            glowEnabled = ambientGlowEnabled,
            pulseEnabled = ambientGlowPulse && shellAnimationLevel != tv.own.owntv.core.theme.AnimationLevel.OFF,
            modifier = Modifier.fillMaxSize(),
        )
      }

      // Unobtrusive background-sync pill (bottom middle): visible while any catalog sync runs —
      // backgrounded first import, remainder worker, auto refresh — but never over fullscreen video.
      if (playerMode != PlayerMode.FULLSCREEN) {
          tv.own.owntv.features.shell.components.SyncStatusPill(modifier = Modifier.align(Alignment.BottomCenter))
      }
      // glcn: seasonal touches over every menu — never over the full-screen player.
      if (playerMode != PlayerMode.FULLSCREEN && multiview == null) {
          tv.own.owntv.features.home.SeasonalOverlay(Modifier.fillMaxSize())
      }

      // Player surface — hoisted so it persists across fullscreen <-> mini (same call site = the
      // SurfaceView isn't recreated when docking/expanding, so playback never blips). NOT composed in
      // AUDIO mode: there's no video surface — audio plays and the top-bar now-playing bar drives it.
      // Multiview draws over everything, including the player it grew out of, and owns its own
      // engines. The player surface below is not composed while it is up: its stream was stopped when
      // the grid opened, so there is nothing behind the tiles to keep alive.
      multiview?.let { grid ->
          tv.own.owntv.features.multiview.MultiviewScreen(
              state = grid,
              // Categories first, then that category's channels. The player's overlay starts at the
              // channels because it already has one playing and its category is the obvious place to
              // look; an empty tile has no such context, and a flat list of every channel across
              // every playlist is tens of thousands of rows to scroll.
              onPickChannel = { tile -> multiviewPickFor = tile; liveVm.showCategories() },
              onFullscreen = { channel ->
                  grid.releaseAll()
                  multiview = null
                  multiviewPickFor = null
                  liveVm.ensurePlaying(channel)
                  playerMode = PlayerMode.FULLSCREEN
              },
              // Leaving the grid leaves *playback*, by design.
              //
              // It used to promote the focused tile to full screen, which is what the plan asked for.
              // In use that is wrong twice over: a grid of four is put away by someone who has
              // finished watching, and being handed one of the four full screen means a stream is
              // still running and still costing a connection when the user believes they closed
              // everything. Back now lands on the Live TV list, with nothing playing.
              onExit = {
                  grid.releaseAll()
                  multiview = null
                  multiviewPickFor = null
                  exitPlayer()
              },
              modifier = Modifier.fillMaxSize(),
          )
          // Filling a tile reuses the very list the Live screen and the in-player overlay use, so the
          // categories, ordering and hidden channels can never disagree between them.
          multiviewPickFor?.let { tile ->
              if (showCategoryBrowser) {
                  // Step one, and where the picker always starts: every Live TV category across every
                  // playlist, so a tile can be filled from a playlist the user was not browsing —
                  // which is the whole point of the grid. Back here leaves the picker entirely.
                  tv.own.owntv.features.shell.components.CategoryBrowserOverlay(
                      railItems = browserRailItems,
                      railCounts = browserRailCounts,
                      currentKey = grid.tiles.getOrNull(tile)?.channel?.categoryId?.let { tv.own.owntv.core.live.LiveKey.Folder(it) },
                      onSelect = { key -> liveVm.loadChannelsForCategory(key) },
                      onDismiss = { liveVm.hideCategoryBrowser(); multiviewPickFor = null },
                      modifier = Modifier.fillMaxSize(),
                  )
              } else if (zapChannels.isNotEmpty()) {
                  tv.own.owntv.features.shell.components.ChannelListOverlay(
                      channels = zapChannels,
                      currentId = grid.tiles.getOrNull(tile)?.channel?.id,
                      title = zapOverlayTitle,
                      showNumbers = directTuneEnabled,
                      nowProgrammes = overlayProgrammes,
                      favoriteIds = overlayFavoriteIds,
                      playlistMarks = overlayPlaylistMarks,
                      alignEnd = true,
                      // Step two: that category's channels. Back goes up to the categories rather than
                      // out, so a wrong turn costs one press instead of starting again.
                      onOpenCategories = { liveVm.showCategories() },
                      onSelect = { grid.fill(tile, it); multiviewPickFor = null },
                      onDismiss = { liveVm.showCategories() },
                      modifier = Modifier.fillMaxSize(),
                  )
              } else {
                  // A category that turns out to be empty must not leave the picker with nothing
                  // drawn: Back would fall past it to the grid's own handler and close Multiview
                  // altogether. Go back to the categories instead, which is where the user was.
                  LaunchedEffect(Unit) { liveVm.showCategories() }
              }
          }
      }

      if (multiview == null && (playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI)) {
        val isFull = playerMode == PlayerMode.FULLSCREEN
        Box(
            modifier = (if (isFull || inPip) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                // Dynamic docked size/position: a screen-width fraction at the chosen corner/edge, so it
                // scales with the panel + UI zoom (never a fixed-size box).
                // glcn: at a top spot the window sits below the menu bar, so the bar stays in view behind it.
                val belowBar = navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.TOP &&
                    (miniPos == tv.own.owntv.core.player.MiniPlayerPosition.TOP_LEFT ||
                        miniPos == tv.own.owntv.core.player.MiniPlayerPosition.TOP_CENTER ||
                        miniPos == tv.own.owntv.core.player.MiniPlayerPosition.TOP_RIGHT)
                Modifier.align(miniPos.alignment)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = if (belowBar) 108.mpx else 24.dp)
                    .fillMaxWidth(tv.own.owntv.core.player.MiniPlayerSize.fraction(miniSizePct)).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
            }).then(pipSource),
        ) {
            // "Promote Preview": a Live channel playing on ExoPlayer renders the ExoPlayer surface — in BOTH
            // full-screen AND the docked mini-player (same call site = the surface persists across dock/
            // expand, so playback never blips). Everything else (mpv) renders mpv's surface.
            if (liveOnExo) {
                tv.own.owntv.player.ExoPreviewSurface(
                    engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize(),
                    keepAwake = true, autoFrameRate = isFull && !inPip && autoFrameRate, holdLastFrame = isFull && !inPip,
                )
            } else {
                // glcn: the cinema side panel slides the film to the left, still playing.
                val cinemaShift by androidx.compose.animation.core.animateFloatAsState(
                    if (isFull && !inPip && tv.own.owntv.glcn.CinemaStage.panelOpen) 1f else 0f,
                    androidx.compose.animation.core.tween(if (tv.own.owntv.glcn.DeviceClass.richMotion) CINEMA_SHIFT_MS else 0),
                    label = "cinemaShift",
                )
                MpvVideoSurface(
                    player = player, modifier = Modifier.fillMaxSize().cinemaInset(cinemaShift), autoFrameRate = isFull && !inPip && autoFrameRate,
                    afrMatchResolution = afrMatchResolution, afrHoldSecs = afrPauseSecs,
                )
            }
            // The item has no video track of its own (a radio channel, a music-only "movie"). Playing it is
            // correct — but a black screen with sound reads as a broken player, so name what is happening.
            // Read from whichever engine is on screen; only ever composed when there is no video to lose.
            val audioOnlyMedia by if (liveOnExo) {
                liveVm.previewEngine.audioOnlyMedia.collectAsStateWithLifecycle()
            } else {
                player.audioOnlyMedia.collectAsStateWithLifecycle()
            }
            if (audioOnlyMedia && !inPip) {
                tv.own.owntv.player.AudioOnlyBadge(modifier = Modifier.fillMaxSize(), compact = !isFull)
            }
            // Direct render mode: mpv can't draw subtitles on the decoder-owned surface — the app does.
            // Also drawn docked: the mini-player is a real watching mode for a subtitled film, and
            // dropping the only line of dialogue there made subtitles look broken. Scaled to the box.
            if (!liveOnExo && !inPip) {
                tv.own.owntv.player.SubtitleOverlay(
                    player = player, modifier = Modifier.fillMaxSize().cinemaInset(if (tv.own.owntv.glcn.CinemaStage.panelOpen && isFull) 1f else 0f),
                    // Tied to the chosen mini size, but nudged up and floored: a strictly proportional
                    // line would be unreadable in the smallest box.
                    sizeScale = if (isFull) 1f else {
                        (tv.own.owntv.core.player.MiniPlayerSize.fraction(miniSizePct) * 1.5f).coerceIn(0.35f, 0.7f)
                    },
                )
            }
            if (isFull && !inPip && !autoFrameRate && !afrPrompted) {
                // Frame rate of whichever engine is on screen. On the mpv side this is what the direct
                // path judders on; on Exo it now survives "Measured stream stats" being off.
                val activeFps by if (liveOnExo) {
                    liveVm.previewEngine.videoFps.collectAsStateWithLifecycle()
                } else {
                    player.videoFps.collectAsStateWithLifecycle()
                }
                tv.own.owntv.player.AutoFrameRatePrompt(
                    fps = activeFps,
                    afrEnabled = autoFrameRate,
                    alreadyPrompted = afrPrompted,
                    // Mark it answered on BOTH paths. Enabling only set the setting, so a user who later
                    // turned Auto frame rate back off was offered the "once ever" suggestion all over again.
                    onEnable = {
                        scope.launch {
                            settingsRepo.setAutoFrameRate(true)
                            settingsRepo.setAutoFrameRatePrompted()
                        }
                    },
                    onDismiss = { scope.launch { settingsRepo.setAutoFrameRatePrompted() } },
                )
            }
            if (isFull && !inPip) {
                // Engine state still distinguishes the live edge from catch-up/archive playback for
                // controls such as direct number tuning. It must not gate the physical CH keys below.
                val isLiveStream = liveOnExo || player.isLiveContent
                // Dedicated CH+/CH- belong to a Live TV/catch-up player for its whole lifetime. Do not
                // derive this callback from the active engine or current list readiness: both legitimately
                // go through short false/empty windows during startup and ExoPlayer/mpv handoffs. Browse
                // panels keep their configurable paging because PlayerHud exists only in full-screen here.
                val zap: ((Int) -> Unit)? =
                    if (zapSource == MainSection.LIVE_TV) liveVm::zap else null
                // Live rewind controls apply to a Live-TV channel (live OR its timeshift archive).
                val isLiveChannel = zapSource == MainSection.LIVE_TV
                // ...but NOT to a catch-up archive programme. That's VOD-style playback of a past
                // programme, so it gets the VOD engine toggle (reloads the same archive URL at the same
                // position on the other engine) rather than Live TV's compatibility toggle, which would
                // re-tune the live stream and jump the user to the current programme.
                val isTunedLive = isLiveChannel && !catchupActive
                // Sports Hub: the extras exist only while the playing channel is one of the match's.
                val sportsPlayback = koinInject<tv.own.owntv.features.sports.SportsPlayback>()
                val sportsSession by sportsPlayback.session.collectAsStateWithLifecycle()
                val sportsConfig by settingsRepo.activeSportsConfig.collectAsStateWithLifecycle(tv.own.owntv.core.sports.SportsConfig())
                val matchSession = sportsSession?.takeIf { s -> isTunedLive && s.options.any { it.channel.id == previewChannel?.id } }
                var showOtherChannels by remember { mutableStateOf(false) }
                // glcn: the copies of the channel on screen, looked up once the channel is tuned.
                var showCopies by remember { mutableStateOf(false) }
                var showProgramInfo by remember { mutableStateOf(false) }
                val channelCopies by androidx.compose.runtime.produceState(emptyList<tv.own.owntv.core.database.entity.ChannelEntity>(), previewChannel?.id, isTunedLive) {
                    value = emptyList()
                    val ch = previewChannel ?: return@produceState
                    if (!isTunedLive) return@produceState
                    value = runCatching { liveVm.copiesOf(ch) }.getOrDefault(emptyList())
                }
                var goalToast by remember { mutableStateOf<tv.own.owntv.core.sports.MatchAlert?>(null) }
                LaunchedEffect(isTunedLive) { if (isTunedLive) sportsPlayback.goals().collect { goalToast = it } }
                LaunchedEffect(goalToast) { if (goalToast != null) { kotlinx.coroutines.delay(GOAL_TOAST_MS); goalToast = null } }
                // A goal elsewhere: play that match's best channel if Find channels already found one,
                // else open its match page, where they can be found.
                val switchToMatch: (tv.own.owntv.core.sports.model.Match) -> Unit = { m ->
                    goalToast = null
                    scope.launch {
                        val options = sportsPlayback.channelsFor(m)
                        val best = options.firstOrNull()
                        if (best == null) {
                            sportsPlayback.pendingMatch.value = m to previewChannel
                            exitPlayer()
                            onSelectSection(MainSection.SPORTS)
                        } else if (liveVm.ensurePlayingByIdAsync(best.channel.id, options.map { it.channel })) {
                            sportsPlayback.start(m, options)
                        }
                    }
                }
                if (showTvStrip && isTunedLive) tv.own.owntv.features.shell.components.TvStrip(
                    load = { liveVm.tvStripGroups() },
                    loadNow = { liveVm.nowFull(it) },
                    currentId = previewChannel?.id,
                    onTune = { ch, _ -> liveVm.ensurePlaying(ch); showTvStrip = false },
                    onDismiss = { showTvStrip = false },
                )
                androidx.compose.runtime.CompositionLocalProvider(
                    // glcn: a film is watched with the lights down — the player is dark whatever the app's style.
                    tv.own.owntv.ui.theme.LocalStagePalette provides
                        if (!isLiveStream && !isLiveChannel) tv.own.owntv.ui.theme.StagePalette.Dark else tv.own.owntv.ui.theme.LocalStagePalette.current,
                ) {
                PlayerHud(
                    player = if (liveOnExo) liveVm.previewEngine else mpvEngine, // HUD drives the active engine
                    onOpenStrip = if (isTunedLive && tv.own.owntv.glcn.TvProfiles.isTv(activeProfileId)) ({ showTvStrip = true }) else null,
                    // glcn: Settings → Back in the player = Window keeps a live channel playing small.
                    onBack = if (isTunedLive && backToWindow) dockPlayer else exitPlayer,
                    onPip = dockPlayer, // PiP/dock works for live on either engine now
                    onAudioMode = toAudioMode,
                    // The channel-list overlay draws ABOVE the HUD; while it's open the HUD goes inert so
                    // its hide/error focus grabs can't yank D-pad focus off the overlay.
                    inert = showChannelList || showQuickGuide || showHistoryList || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker || showOtherChannels || showCopies || showProgramInfo,
                    onChannelUp = zap?.let { z -> { z(-1) } },
                    onChannelDown = zap?.let { z -> { z(1) } },
                    onOpenChannelList = if (isTunedLive && liveCanZap) { { showChannelList = true } } else null,
                    onOpenQuickGuide = if (isTunedLive && liveCanZap) { { showQuickGuide = true } } else null,
                    // Live channels only, and only once Multiview is switched on: the channel on screen
                    // becomes tile 1 and the grid takes over. Everything it needs is already tuned.
                    onMultiview = if (multiviewEnabled && isTunedLive && previewChannel != null) {
                        { multiview = openMultiview(previewChannel, emptyList()) }
                    } else {
                        null
                    },
                    // The button exists only once the setting is on, and only on a live
                    // channel: there is nothing to record off a film that is already a file.
                    // The engine does not come into it. Recording fetches the channel itself rather
                    // than copying the open stream, so it works the same whichever engine is playing.
                    onRecordThis = previewChannel
                        ?.takeIf { recordWatchingEnabled && isTunedLive }
                        ?.let { channel -> { liveVm.togglePlayerRecording(channel) } },
                    recordingThis = playerRecording != null,
                    onOpenHistoryList = if (isTunedLive) { { showHistoryList = true } } else null,
                    // glcn: the guide with the channel still playing in the mini window.
                    onOpenGuide = if (isTunedLive) { { openShortcutSection(MainSection.EPG) } } else null,
                    onRewindLive = if (isTunedLive && canRewindLive) liveVm::rewindLive else null,
                    onForwardLive = if (isTunedLive) liveVm::forwardLive else null,
                    onGoToLive = if (isTunedLive) liveVm::goToLive else null,
                    onScrubLive = if (isTunedLive && canRewindLive) liveVm::scrubLive else null,
                    // Only collected where there is a timeline to draw them on.
                    liveProgrammes = if (isTunedLive && canRewindLive) timelineProgrammes else emptyList(),
                    liveGaps = liveVm::timeshiftGaps,
                    // "Go back to…" is the provider archive's: a copy saved on this device rewinds with the
                    // bar and the buttons, and must not show a catch-up control on a channel without catch-up.
                    jumpBackOptions = if (isTunedLive && previewChannel?.catchup == true) liveVm::currentJumpOptions else null,
                    onJumpBack = if (isTunedLive && previewChannel?.catchup == true) liveVm::jumpBackTo else null,
                    onPreviousChannel = if (isTunedLive && previousLiveChannel != null) liveVm::previousChannel else null,
                    jumpBackWindowSec = if (isTunedLive && previewChannel?.catchup == true) liveVm::currentCatchupWindowSec else null,
                    // Non-null only while an archive is on screen, so movies, episodes and live TV get
                    // the single real clock and catch-up gets the pair.
                    watchingWallMs = { watchingWallState.value },
                    // "End of programme" only at the live edge: rewound, the programme on air is not the one being watched.
                    sleepProgrammeEndMs = if (isTunedLive && !timeshifted) { { liveVm.nowNext.value?.now?.stopMs } } else null,
                    liveLeftRightRewinds = liveLeftRightRewinds,
                    onControlsVisible = { hudControlsVisible = it },
                    timeshiftOffsetSec = if (isTunedLive) { { timeshiftOffsetState.value } } else null,
                    onTuneToNumber = if (tv.own.owntv.player.numberKeysTune(directTuneEnabled, playerRemoteOptions) && isTunedLive && isLiveStream && !timeshifted && previewChannel != null) liveVm::tuneByNumber else null,
                    directTuneContextKey = previewChannel?.id ?: 0L,
                    remoteOptions = playerRemoteOptions,
                    // Show the ACTUAL running engine (mpv when pinned OR auto-fallen-back), not just the pin —
                    // otherwise an auto-fallback to mpv still read "EXO". true = on mpv (pill shows MPV, teal).
                    compatMode = if (isTunedLive) !liveOnExo else null,
                    // Hidden while rewound into the archive (same `!timeshifted` rule direct
                    // tune follows above): switching engine restarts the channel at the live edge, which
                    // threw the user out of the rewind with the HUD still counting "behind live".
                    // Also hidden for a protected channel: only ExoPlayer can license it, so the
                    // toggle's other position is not a compatibility choice but a guaranteed failure.
                    // A copy saved on this device is the exception: the other engine re-opens the same copy
                    // at the same moment, so the rewind survives the switch.
                    onToggleCompatMode = if (isTunedLive && (!timeshifted || onLocalCopy) && previewChannel?.drmConfig == null) liveVm::toggleForceMpv else null,
                    // VOD engine toggle (movies/series only — live and catch-up channels keep their own
                    // engine handling above): flip the current item between mpv and ExoPlayer.
                    vodOnExo = if (!isLiveStream && !isTunedLive) vodExoActive else null,
                    onToggleVodEngine = if (!isLiveStream && !isTunedLive) player::toggleVodEngine else null,
                    // ADD SUBTITLES entry: movies/episodes only, and only when the play path set an
                    // item context. Opens the OpenSubtitles search overlay below.
                    onSearchSubtitles = if (!isLiveStream && !isLiveChannel && subtitleContext != null) {
                        { showSubtitleSearch = true }
                    } else null,
                    // Local subtitle file: same movie/episode gating, no account needed.
                    onSelectLocalSubtitle = if (!isLiveStream && !isLiveChannel && subtitleContext != null) {
                        { showLocalSubPicker = true }
                    } else null,
                    // In-stream favorite toggle for the current channel/movie/series.
                    favorite = favActive,
                    onToggleFavorite = favToggle,
                    // The zap banner's now/next. Collected inside the slot, so only the banner redraws.
                    zapGuide = if (isLiveChannel) {
                        {
                            val guide by liveVm.zapGuide.collectAsStateWithLifecycle()
                            // glcn: the programme's picture beside now/next — the banner as a TV operator draws it.
                            val zapChannel = previewChannel
                            val art by androidx.compose.runtime.produceState<String?>(liveVm.peekArt(zapChannel?.id, guide?.now?.startMs), zapChannel?.id, guide?.now?.startMs) {
                                value = zapChannel?.let { liveVm.nowArtUrl(it, guide?.now?.startMs) }
                            }
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                            ) {
                                art?.takeIf { it.isNotBlank() }?.let { url ->
                                    tv.own.owntv.ui.components.ProgrammeArt(
                                        url = url,
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .size(width = 192.dp, height = 108.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                                    )
                                }
                                tv.own.owntv.features.shell.components.ZapGuideLines(guide, Modifier.weight(1f, fill = false))
                            }
                        }
                    } else null,
                    zapChannel = if (isLiveChannel) {
                        previewChannel?.let { tv.own.owntv.player.DirectTuneChannelInfo(it.number, it.name, it.logoUrl) }
                    } else null,
                    // Guide card for the playing channel (nowNext follows previewChannel = what's playing).
                    liveEpgCard = if (isLiveChannel) {
                        {
                            val epg by liveVm.nowNext.collectAsStateWithLifecycle()
                            val archiveEpg by liveVm.archiveNowNext.collectAsStateWithLifecycle()
                            val watching by liveVm.watchingWallMs.collectAsStateWithLifecycle()
                            // Stacked: what was on air at the replayed moment, then what is on air now.
                            // The live row is dimmed while an archive plays — it is context, not the
                            // thing being watched — and returns to full strength back at the live edge.
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.End,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                            ) {
                                if (watching != null) {
                                    tv.own.owntv.features.shell.components.LiveEpgCard(
                                        epg = archiveEpg,
                                        variant = tv.own.owntv.features.shell.components.EpgCardVariant.ARCHIVE,
                                        atMs = watching,
                                    )
                                }
                                if (watching == null) {
                                    // glcn: the programme's picture beside it, as on the channel-change banner.
                                    val artChannel = previewChannel
                                    val art by androidx.compose.runtime.produceState<String?>(liveVm.peekArt(artChannel?.id, epg?.now?.startMs), artChannel?.id, epg?.now?.startMs) {
                                        value = artChannel?.let { liveVm.nowArtUrl(it, epg?.now?.startMs) }
                                    }
                                    tv.own.owntv.features.shell.components.LiveEpgCardWithArt(epg = epg, art = art)
                                } else {
                                    tv.own.owntv.features.shell.components.LiveEpgCard(
                                        epg = epg,
                                        // glcn: while rewound, live is context — one line under the replayed programme.
                                        compact = true,
                                        modifier = Modifier.alpha(0.85f),
                                    )
                                }
                            }
                        }
                    } else null,
                    matchLine = matchSession?.takeIf { sportsConfig.scoreInInfoBar }?.let { s ->
                        { tv.own.owntv.features.sports.MatchScoreLine(sportsPlayback, s.match, sportsConfig.spoilerFree) }
                    },
                    onOtherChannels = matchSession?.takeIf { it.options.size > 1 }?.let { { showOtherChannels = true } },
                    otherChannelsCount = (matchSession?.options?.size ?: 1) - 1,
                    onChannelCopies = channelCopies.takeIf { it.size > 1 }?.let { { showCopies = true } },
                    // glcn: the Info button opens the programme's own screen on a live channel.
                    onProgrammeInfo = if (isTunedLive && previewChannel != null) { { showProgramInfo = true } } else null,
                    onToastOk = goalToast?.let { a -> { switchToMatch(a.match) } },
                    // glcn: films and episodes get the cinema chrome.
                    cinema = if (!isLiveStream && !isLiveChannel) cinemaInfo else null,
                    cinemaEpisodes = if (zapSource == MainSection.SERIES) cinemaEpisodes else emptyList(),
                    cinemaEpisodeIndex = cinemaQueue?.second ?: -1,
                    onPlayEpisode = if (zapSource == MainSection.SERIES) {
                        { i ->
                            val q = cinemaQueue
                            val show = playingSeries
                            if (q != null && show != null) q.first.getOrNull(i)?.let { seriesVm.playEpisodeQueue(show, q.first, it) }
                        }
                    } else null,
                    modifier = Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                        // glcn: OK while "Skip intro" shows jumps the intro.
                        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (!introSkipVisible || !ok) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyUp) tv.own.owntv.glcn.IntroSkip.skip(introContext, if (liveOnExo) liveVm.previewEngine else mpvEngine)
                        true
                    },
                )
                }
                // glcn: "Skip intro" on series episodes — OK while it shows (controls hidden) jumps it.
                if (!isLiveChannel) tv.own.owntv.glcn.IntroSkip.Chip(
                    engine = if (liveOnExo) liveVm.previewEngine else mpvEngine,
                    controlsHidden = !hudControlsVisible,
                    autoSkip = autoSkipIntro,
                    onVisible = { introSkipVisible = it },
                    modifier = Modifier.fillMaxSize(),
                )
                goalToast?.let { a ->
                    tv.own.owntv.features.sports.GoalToast(a, Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 45.mpx, end = 60.mpx))
                }
                if (showProgramInfo) {
                    val infoChannel = previewChannel
                    if (infoChannel != null) tv.own.owntv.glcn.ProgrammeInfoScreen(
                        channel = infoChannel,
                        player = if (liveOnExo) liveVm.previewEngine else mpvEngine,
                        loadSchedule = { liveVm.scheduleFor(it) },
                        loadWeek = { liveVm.weekFor(it) },
                        onDismiss = { showProgramInfo = false },
                        modifier = Modifier.fillMaxSize(),
                    ) else showProgramInfo = false
                }
                if (showCopies) {
                    tv.own.owntv.features.shell.components.ChannelCopiesPopup(
                        copies = channelCopies,
                        playingId = previewChannel?.id,
                        onPick = { ch -> liveVm.ensurePlaying(ch); showCopies = false },
                        onDismiss = { showCopies = false },
                    )
                }
                if (showOtherChannels) {
                    val s = matchSession
                    if (s == null) showOtherChannels = false
                    else tv.own.owntv.features.sports.OtherChannelsPanel(
                        session = s,
                        playingId = previewChannel?.id,
                        onSwitch = { session, i -> liveVm.ensurePlaying(session.options[i].channel); showOtherChannels = false },
                        onDismiss = { showOtherChannels = false },
                    )
                }
                // OpenSubtitles search overlay (movies/episodes) — drawn above the HUD; the HUD is inert
                // while it's open so the D-pad stays on the overlay.
                if (showSubtitleSearch) {
                    tv.own.owntv.features.subtitles.SubtitleSearchScreen(
                        onDismiss = { showSubtitleSearch = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Local subtitle-file picker — hosted in a Dialog window, so D-pad focus
                // can't fall through to the HUD behind; picking imports a managed UTF-8 copy and
                // attaches it live to whichever engine is playing.
                if (showLocalSubPicker) {
                    tv.own.owntv.ui.components.StorageBrowser(
                        title = stringResource(R.string.content_subtitle_select_file),
                        mode = tv.own.owntv.ui.components.BrowseMode.FILE,
                        fileExtensions = setOf("srt", "ass", "ssa", "vtt", "webvtt"),
                        onPick = { file ->
                            showLocalSubPicker = false
                            scope.launch {
                                runCatching { subtitleController.applyLocal(file) }
                                    .onFailure { e ->
                                        localSubToast.show(e.message ?: subtitleLoadFailed)
                                    }
                            }
                        },
                        onDismiss = { showLocalSubPicker = false },
                    )
                }
                tv.own.owntv.ui.components.InAppToast(localSubToast)
                // Programme reminders: when one comes up, ask to switch, switch, or only say so (the
                // Programme reminders setting) — over whatever is on screen, the player included.
                val reminderManager = koinInject<tv.own.owntv.core.reminder.ReminderManager>()
                val reminderMode by settingsRepo.reminderMode.collectAsStateWithLifecycle(tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK)
                val dueReminder = rememberDueReminder(activeProfileId)
                val watchReminder: (tv.own.owntv.core.database.entity.ReminderEntity) -> Unit = { r ->
                    scope.launch {
                        reminderManager.dismiss(r.id)
                        if (liveVm.ensurePlayingByIdAsync(r.channelId)) openFullscreen(MainSection.LIVE_TV)
                    }
                }
                val reminderText = dueReminder?.let { listOf(it.title, tv.own.owntv.features.epg.reminderEyebrow(it)).joinToString(" · ") }
                // glcn: already watching that channel (in any quality) — nothing to announce.
                val alreadyWatching = dueReminder != null && playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV &&
                    tv.own.owntv.glcn.isReminderChannel(dueReminder, previewChannel)
                LaunchedEffect(dueReminder?.id, reminderMode, alreadyWatching) {
                    val r = dueReminder ?: return@LaunchedEffect
                    if (alreadyWatching) { reminderManager.dismiss(r.id); return@LaunchedEffect }
                    when (reminderMode) {
                        tv.own.owntv.core.settings.SettingsRepository.ReminderMode.SWITCH -> watchReminder(r)
                        tv.own.owntv.core.settings.SettingsRepository.ReminderMode.NOTIFY -> {
                            reminderText?.let(localSubToast::show)
                            reminderManager.dismiss(r.id)
                        }
                        tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK -> Unit
                    }
                }
                val showCard = dueReminder != null && !alreadyWatching &&
                    reminderMode == tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK
                DisposableEffect(showCard, dueReminder?.id) {
                    reminderAccept = if (showCard && dueReminder != null) ({ watchReminder(dueReminder) }) else null
                    onDispose { reminderAccept = null }
                }
                if (showCard && dueReminder != null) {
                    tv.own.owntv.glcn.ReminderCard(
                        reminder = dueReminder,
                        eyebrow = tv.own.owntv.features.epg.reminderEyebrow(dueReminder),
                        keyHint = if (playerMode == PlayerMode.FULLSCREEN) "OK" else "\u23EF",
                        onDismiss = { scope.launch { reminderManager.dismiss(dueReminder.id) } },
                    )
                }
                // A catch-up pick that could not be resolved. Raised from Live TV wherever an archive
                // URL comes back null — "Watch from start", "Go back to…" and the external hand-off —
                // all of which used to fail in complete silence.
                val catchupUnavailable = stringResource(R.string.content_epg_catchup_unavailable)
                LaunchedEffect(liveVm) {
                    liveVm.catchupUnavailable.collect { localSubToast.show(catchupUnavailable) }
                }
                // glcn: a channel that failed was swapped for the same one elsewhere in its playlist.
                val failoverFmt = stringResource(R.string.glcn_failover_toast, "\u0000")
                LaunchedEffect(liveVm) {
                    liveVm.failoverNotice.collect { name -> localSubToast.show(failoverFmt.replace("\u0000", name)) }
                }
                // Back on a channel whose saved copy was kept: continue from there, or stay live.
                val timeshiftResumeAt by liveVm.timeshiftResumeAt.collectAsStateWithLifecycle()
                if (isFull && timeshiftResumeAt != null) {
                    tv.own.owntv.ui.components.TimeshiftResumeDialog(
                        onResume = liveVm::resumeTimeshift,
                        onGoLive = liveVm::dismissTimeshiftResume,
                    )
                }
                // Left — the playing channel's own provider category.
                if (showChannelList && isLiveChannel) {
                    if (showCategoryBrowser) {
                        // Second Left — every Live TV category.
                        tv.own.owntv.features.shell.components.CategoryBrowserOverlay(
                            railItems = browserRailItems,
                            railCounts = browserRailCounts,
                            // The list the player zaps in: a built-in rail or custom category when it came from one.
                            currentKey = zapListKey ?: previewChannel?.categoryId?.let { tv.own.owntv.core.live.LiveKey.Folder(it) },
                            onSelect = { key -> liveVm.loadChannelsForCategory(key) },
                            peekChannels = { liveVm.peekCategoryChannels(it) },
                            loadNow = { liveVm.nowMany(it) },
                            onTune = { liveVm.ensurePlaying(it); liveVm.hideCategoryBrowser(); showChannelList = false },
                            onDismiss = { liveVm.hideCategoryBrowser() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (zapChannels.isNotEmpty()) {
                        // First Left — the channels of the current category. A browsed-to category may
                        // hold a single channel, so this renders for any non-empty list.
                        // glcn: the poster view of the same list (▶ in the list switches views; ▲ comes back).
                        // glcn: the zap deck — groups, channels and the focused programme in one sheet (TiviMate's idea, our
                        // look). Its right column shows the picture or the schedule (▶ switches when both views are on).
                        val deckDetail = if (glcnZapPanel == 1 || (glcnZapPanel == 2 && tv.own.owntv.core.settings.ZapViews.has(glcnZapViews, 1))) 1 else 0
                        if (deckPosters) tv.own.owntv.features.shell.components.ZapPosterOverlay(
                            channels = zapChannels,
                            currentId = previewChannel?.id,
                            title = zapOverlayTitle,
                            loadNow = { liveVm.nowMany(it) },
                            loadSchedule = { liveVm.scheduleFor(it) },
                            onSelect = { liveVm.ensurePlaying(it); showChannelList = false },
                            onListView = { deckPosters = false },
                            onDismiss = { showChannelList = false },
                            modifier = Modifier.fillMaxSize(),
                            onLongPress = { zapOptionsChannel = it },
                            loadDescription = { liveVm.programmeDescription(it) },
                        ) else tv.own.owntv.features.shell.components.ZapDeck(
                            railItems = browserRailItems,
                            railCounts = browserRailCounts,
                            channels = zapChannels,
                            currentKey = zapListKey ?: previewChannel?.categoryId?.let { tv.own.owntv.core.live.LiveKey.Folder(it) },
                            // The zap list is the group's own only when it holds the whole group: a channel opened from
                            // a Home row zaps in a short window of it, which must not be shown as the group.
                            listIsGroup = zapListKey != null || (zapListKey ?: previewChannel?.categoryId?.let { tv.own.owntv.core.live.LiveKey.Folder(it) })
                                ?.let { browserRailCounts[it] }?.let { zapChannels.size >= it } == true,
                            currentId = previewChannel?.id,
                            showNumbers = directTuneEnabled,
                            detail = deckDetail,
                            onCycleDetail = null, // glcn: ▶ closes; the views are the quick guide's (held OK)
                            peekChannels = { liveVm.deckChannels(it) },
                            loadNowMany = { liveVm.nowMany(it) },
                            loadNow = { liveVm.nowFull(it) },
                            loadSchedule = { liveVm.scheduleFor(it) },
                            onArmCategory = { liveVm.loadChannelsForCategory(it) },
                            onTune = { liveVm.ensurePlaying(it); showChannelList = false },
                            onDismiss = { showChannelList = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // glcn: held OK — the quick guide as it was: the playing list with the picture beside it, its schedule,
                // or posters; ▶ steps the view (remembered), ◀ opens every category.
                if (showQuickGuide && isLiveChannel) {
                    if (showCategoryBrowser) {
                        tv.own.owntv.features.shell.components.CategoryBrowserOverlay(
                            railItems = browserRailItems,
                            railCounts = browserRailCounts,
                            currentKey = zapListKey ?: previewChannel?.categoryId?.let { tv.own.owntv.core.live.LiveKey.Folder(it) },
                            onSelect = { key -> liveVm.loadChannelsForCategory(key) },
                            peekChannels = { liveVm.peekCategoryChannels(it) },
                            loadNow = { liveVm.nowMany(it) },
                            onTune = { liveVm.ensurePlaying(it); liveVm.hideCategoryBrowser(); showQuickGuide = false },
                            onDismiss = { liveVm.hideCategoryBrowser() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (zapChannels.isNotEmpty()) {
                        if (glcnZapPanel == 2) tv.own.owntv.features.shell.components.ZapPosterOverlay(
                            channels = zapChannels,
                            currentId = previewChannel?.id,
                            title = zapOverlayTitle,
                            loadNow = { liveVm.nowMany(it) },
                            loadSchedule = { liveVm.scheduleFor(it) },
                            onSelect = { liveVm.ensurePlaying(it); showQuickGuide = false },
                            onListView = { scope.launch { settingsRepo.setZapPanel(tv.own.owntv.core.settings.ZapViews.next(2, glcnZapViews)) } },
                            onDismiss = { showQuickGuide = false },
                            modifier = Modifier.fillMaxSize(),
                            onLongPress = { zapOptionsChannel = it },
                            loadDescription = { liveVm.programmeDescription(it) },
                        ) else tv.own.owntv.features.shell.components.ChannelListOverlay(
                            channels = zapChannels,
                            currentId = previewChannel?.id,
                            nowPlaying = overlayNowPlaying,
                            nowProgrammes = overlayProgrammes,
                            favoriteIds = overlayFavoriteIds,
                            playlistMarks = overlayPlaylistMarks,
                            title = zapOverlayTitle,
                            showNumbers = directTuneEnabled,
                            onSelect = { liveVm.ensurePlaying(it); showQuickGuide = false },
                            onDismiss = { showQuickGuide = false },
                            onOpenCategories = { liveVm.showCategories() },
                            loadNow = { liveVm.nowFull(it) },
                            loadSchedule = if (glcnZapPanel == 1) ({ liveVm.scheduleFor(it) }) else null,
                            onCyclePanel = if (glcnZapViews and (glcnZapViews - 1) == 0) null else ({ scope.launch { settingsRepo.setZapPanel(tv.own.owntv.core.settings.ZapViews.next(glcnZapPanel, glcnZapViews)) } }),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Right — recently watched, to hop straight back to the previous channel.
                if (showHistoryList && isLiveChannel && historyChannels.isNotEmpty()) {
                    tv.own.owntv.features.shell.components.ChannelListOverlay(
                        channels = historyChannels,
                        currentId = previewChannel?.id,
                        nowPlaying = historyNowPlaying,
                        nowProgrammes = overlayProgrammes,
                        favoriteIds = overlayFavoriteIds,
                        playlistMarks = overlayPlaylistMarks,
                        watchedAt = historyWatchedAt,
                        title = stringResource(R.string.content_category_recently_watched),
                        showNumbers = directTuneEnabled,
                        alignEnd = true,
                        onSelect = { liveVm.ensurePlaying(it); showHistoryList = false },
                        onDismiss = { showHistoryList = false },
                        loadNow = { liveVm.nowFull(it) },
                        loadSchedule = if (glcnZapPanel == 1) ({ liveVm.scheduleFor(it) }) else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // glcn: the channel gives no picture and its only other copy is in another playlist: say so first.
                val crossCopy by liveVm.crossCopy.collectAsStateWithLifecycle()
                crossCopy?.let { offer ->
                    androidx.compose.runtime.key(offer.to.id) {
                        tv.own.owntv.features.shell.components.CrossCopyCard(
                            channel = offer.to.name, playlist = offer.playlist,
                            onAccept = { liveVm.acceptCrossCopy() }, onDecline = { liveVm.declineCrossCopy() },
                        )
                    }
                }
                zapOptionsChannel?.let { ch ->
                    androidx.compose.runtime.key(ch.id) { tv.own.owntv.features.live.PlayerChannelOptions(liveVm, ch, onDismiss = { zapOptionsChannel = null }) }
                }
            } else if (!inPip) {
                MiniPlayer(
                    player = if (liveOnExo) liveVm.previewEngine else mpvEngine,
                    onExpand = expandPlayer,
                    onClose = exitPlayer,
                    onCycleSize = { scope.launch { settingsRepo.setMiniPlayerSizePct(tv.own.owntv.core.player.MiniPlayerSize.next(miniSizePct)) } },
                    onCyclePosition = { scope.launch { settingsRepo.setMiniPlayerPosition(miniPos.next().name) } },
                    onAudioMode = toAudioMode,
                    entryFocusRequester = miniEntryFocus,
                    onBack = { runCatching { shellContentFocus.requestFocus() } },
                    modifier = Modifier.fillMaxSize().onFocusChanged { miniHasFocus = it.hasFocus },
                )
            }
        }
      }

    if (showExit) {
        ExitDialog(onConfirm = onExitApp, onDismiss = { showExit = false })
    }
    if (showAvatarPicker) {
        AvatarPickerDialog(
                selectedId = avatarId,
                onSelect = onSetAvatar,
                customPath = avatarPath,
                onPickCustom = { showAvatarPicker = false; showAvatarChooser = true },
                onClearCustom = { onClearCustomAvatar(); showAvatarPicker = false },
                onDismiss = { showAvatarPicker = false },
        )
    }
    if (showAvatarChooser) {
        tv.own.owntv.features.profiles.ProfilePictureChooser(
                hasImage = avatarPath.isNotBlank(),
                onPicked = { file -> onSetCustomAvatar(file); showAvatarChooser = false },
                onClear = { onClearCustomAvatar(); showAvatarChooser = false },
                onDismiss = { showAvatarChooser = false },
            )
    }
    if (showPlaylistPicker) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showPlaylistPicker = false }, stageLayout = true) {
            tv.own.owntv.features.shell.components.StagePlaylistMenu(
                playlists = playlists,
                activeId = activePlaylistId,
                activeSet = activePlaylistSet,
                onSelectMany = onSelectPlaylists,
                pillLabel = playlistLabel,
                pillBounds = playlistPillBounds,
                onSelect = onSelectPlaylist,
                onResync = { shellVm.resync(playlists.filter { it.id == activePlaylistId }.ifEmpty { playlists }) },
                onDismiss = { showPlaylistPicker = false },
                onRefreshGuide = { epgVm.refreshGuideNow() },
            )
        }
    }

    // A Guide auto-match keeps running when the Guide is left, and its review waits there for the
    // Guide to come back — but finishing off the Guide said nothing at all. Say how it went, here
    // because this layer is on screen everywhere, the player included. Not on the Guide itself:
    // there the line beside the button, or the review window, already says it.
    val epgMatchToast = tv.own.owntv.ui.components.rememberInAppToast()
    val shellLiveView by liveVm.liveView.collectAsStateWithLifecycle()
    val guideOnScreen = playerMode != PlayerMode.FULLSCREEN && multiview == null &&
        (selectedSection == MainSection.EPG ||
            (selectedSection == MainSection.LIVE_TV && shellLiveView == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE))
    val currentGuideOnScreen by rememberUpdatedState(guideOnScreen)
    var finishedMatch by remember { mutableStateOf<tv.own.owntv.features.epg.EpgMatchSummary?>(null) }
    LaunchedEffect(epgVm) {
        epgVm.matchFinished.collect { if (!currentGuideOnScreen) finishedMatch = it }
    }
    finishedMatch?.let { summary ->
        val text = tv.own.owntv.features.epg.epgMatchSummaryText(summary)
        LaunchedEffect(summary) {
            epgMatchToast.show(text, durationMs = EPG_MATCH_TOAST_MS)
            finishedMatch = null
        }
    }
    tv.own.owntv.ui.components.InAppToast(epgMatchToast)

    // Automatic update check (GitHub Releases) shortly after launch, once per session: a small
        // top-right status card shows "Checking… / up to date" (auto-hides) or stays with
        // Update now / Later when a release is newer. Hidden while in Settings (its manual
        // "Check for updates" dialog drives the same state machine) and during playback.
        // Interrupted restore: the marker outlives the process, so if it's still set at launch
        // the last restore didn't complete. Acknowledging clears it.
        val restoreSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val incompleteRestore by restoreSettings.restoreInProgress.collectAsStateWithLifecycle(initialValue = null)
    var restoreNoticeDismissed by remember { mutableStateOf(false) }
    incompleteRestore?.takeIf { !restoreNoticeDismissed }?.let { description ->
        val dismissRestoreNotice: () -> Unit = {
            restoreNoticeDismissed = true
            scope.launch { restoreSettings.clearRestoreMarker() }
        }
        IncompleteRestoreDialog(
                description = description,
                onDismiss = dismissRestoreNotice,
            )
    }

        val updateManager = koinInject<UpdateManager>()
        var showStartupToast by remember { mutableStateOf(false) }
        var showChangelog by remember { mutableStateOf(false) }
        val settingsRepo = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
        val updateCheckOnStart by settingsRepo.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
        LaunchedEffect(updateCheckOnStart) {
            // Once per launch: a rebuilt shell must not bring the "latest version" card back.
            if (updateCheckOnStart && !showStartupToast && !shellVm.startupUpdateCheckDone) {
                kotlinx.coroutines.delay(5_000)
                shellVm.startupUpdateCheckDone = true
                showStartupToast = true
                updateManager.check()
            }
        }
        if (showChangelog) {
            // Full "What's New" changelog (same dialog the manual Settings check uses), shown when
            // the startup card's "What's New" is pressed. No re-check — the release is already loaded.
            val dismissChangelog: () -> Unit = {
                showChangelog = false
                showStartupToast = false
                updateManager.reset()
            }
            UpdateDialog(onDismiss = dismissChangelog, checkOnOpen = false)
        } else if (showStartupToast && selectedSection != MainSection.SETTINGS && playerMode == PlayerMode.NONE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                UpdateStatusToast(
                    onDone = { showStartupToast = false; updateManager.reset() },
                    onViewChangelog = { showChangelog = true },
                )
            }
        }
    }
    }
}

/** A goal toast stays this long. */
private const val GOAL_TOAST_MS = 8_000L

/** Long enough to read "Auto-matched 12 channels. 40 channels need review." from the sofa. */
private const val EPG_MATCH_TOAST_MS = 6_000L

/** The line under a rail item at Size = Extra wide; real data only, so most items can have none. */
@Composable
private fun railDetail(section: MainSection, d: RailDetails): String? = when (section) {
    MainSection.LIVE_TV -> d.lastChannel?.let { stringResource(R.string.shell_rail_detail_last, it) }
    MainSection.MOVIES -> d.resumeMovie?.let { stringResource(R.string.shell_rail_detail_resume, it) }
    MainSection.SERIES -> d.episode?.let { e ->
        val title = stringResource(R.string.shell_rail_detail_episode, e.series, stringResource(R.string.content_season_episode, e.season, e.episode))
        stringResource(if (e.resume) R.string.shell_rail_detail_resume else R.string.shell_rail_detail_next, title)
    }
    MainSection.DOWNLOADS -> if (d.downloading == 0) null else {
        val progress = d.downloadProgress?.let {
            java.text.NumberFormat.getPercentInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(it)
        }
        if (progress == null) null
        else androidx.compose.ui.res.pluralStringResource(R.plurals.shell_rail_detail_downloads, d.downloading, d.downloading, progress)
    }
    MainSection.MORE -> stringResource(R.string.shell_rail_detail_more)
    else -> null
}

/** How recently ◀ / ▶ or [focusRail] must have happened for a rail visit to open the rail. */
private const val RAIL_KEY_WINDOW_MS = 400L

/** glcn: how long Back is held in the full-screen player to close it rather than leave it in the window. */

/** glcn: a second stop after a held Back, once the closing has settled. */
private const val HOLD_SETTLE_MS = 600L

/** glcn: how long after tuning a film's details are read ahead for its info screen. */
private const val FILM_WARM_MS = 1_500L

/** glcn: a section's entrance — how long and from how far right. */
private const val SECTION_IN_MS = 180
private val SECTION_IN_SLIDE = 14.dp

/** glcn: a stored JSON array of strings (TMDB genres) as a list; empty when absent or unreadable. */
private fun cinemaJsonList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())
}

/** glcn: how long the film takes to slide aside for the cinema panel. */
private const val CINEMA_SHIFT_MS = 380

/**
 * glcn: the film's box as the cinema panel opens — at [p] = 0 the whole screen, at 1 a 16:9 picture filling
 * the left 56% of the width, centred top to bottom. Measured, not scaled: the picture is a SurfaceView, which
 * follows its layout box but not a drawing transform.
 */
private fun Modifier.cinemaInset(p: Float): Modifier = if (p <= 0f) this else this.layout { measurable, constraints ->
    val fullW = constraints.maxWidth
    val fullH = constraints.maxHeight
    val smallW = fullW * 0.56f
    val smallH = smallW * 9f / 16f
    val left = 48.dp.toPx()
    val w = (fullW + (smallW - fullW) * p).toInt()
    val h = (fullH + (smallH - fullH) * p).toInt()
    val x = (left * p).toInt()
    val y = ((fullH - h) / 2f).toInt()
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1)))
    layout(fullW, fullH) { placeable.place(x, y) }
}
