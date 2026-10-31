package tv.own.owntv.features.movies

import androidx.compose.foundation.background
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.player.PlaybackPrefsStore
import tv.own.owntv.core.player.TrackLanguages
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: a film's own page before it plays ("Page before watching", Settings › Browsing & lists). The picture — or its
 * trailer, carried on from the list without starting over — fills the screen; over it the title's lettering, the facts,
 * the synopsis and the cast, and one row of buttons: Play (or Resume, focused, so OK twice still starts the film),
 * Trailer full screen with sound, Audio and Subtitles chosen before it starts, and Favourite.
 */
@Composable
internal fun MovieTitlePage(
    movie: MovieEntity,
    meta: MetadataCacheEntity?,
    info: tv.own.owntv.features.shell.components.VodTitleInfo,
    favorite: Boolean,
    resumeMs: suspend () -> Long,
    onPlay: (startMs: Long) -> Unit,
    /** glcn: the other copies of this film (another playlist, another quality); a pick plays that one. */
    loadVersions: suspend () -> List<Pair<MovieEntity, String?>> = { emptyList() },
    onPlayVersion: (MovieEntity) -> Unit = {},
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    /** glcn: films like this one (in the library), for the page's second part; choosing one opens its page. */
    loadRelated: suspend () -> List<MovieEntity> = { emptyList() },
    onOpenRelated: (MovieEntity) -> Unit = {},
    /**
     * glcn: drawn on the list's own page, over its picture and the trailer already playing there — the trailer goes
     * on without a break (a new window would start a new player, from black). The list hides its words and posters.
     */
    inPlace: Boolean = false,
) {
    val prefs: PlaybackPrefsStore = koinInject()
    val scope = rememberCoroutineScope()
    val trackKey = remember(movie.id) { tv.own.owntv.core.player.enginePinKey(movie.sourceId, "MOVIE", movie.remoteId) }
    val saved by produceState(0L, movie.id) { value = runCatching { resumeMs() }.getOrDefault(0L) }
    var tracks by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(trackKey) { tracks = trackKey?.let { prefs.tracksFor(it) } }
    var picking by remember { mutableStateOf<Picker?>(null) }
    val versions by produceState<List<Pair<MovieEntity, String?>>>(emptyList(), movie.id) { value = runCatching { loadVersions() }.getOrDefault(emptyList()) }
    var choosingVersion by remember { mutableStateOf(false) }
    // glcn: the copy the page will play — another version can be chosen (its quality shows), then Play plays it.
    var chosen by remember(movie.id) { mutableStateOf(movie) }
    val shownInfo = if (chosen.id == movie.id) info else info.copy(tags = versionBadges(chosen))
    val playChosen: (Long) -> Unit = { start -> if (chosen.id == movie.id) onPlay(start) else onPlayVersion(chosen) }
    var fullTrailer by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val enter = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(PAGE_ENTER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { playFocus.requestFocus() } }
    var lower by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { if (lower) { lower = false; runCatching { playFocus.requestFocus() } } else onDismiss() }
    fullTrailer?.let { key -> tv.own.owntv.ui.components.TrailerPlayerScreen(videoKey = key, title = movie.name) { fullTrailer = null } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)

    if (inPlace) androidx.compose.runtime.DisposableEffect(Unit) {
        tv.own.owntv.features.home.CinemaTrailer.keepOnMove = true
        onDispose { tv.own.owntv.features.home.CinemaTrailer.keepOnMove = false }
    }
    // glcn: two parts, as Apple TV draws it — the title over the picture, then (▼) the cast, the versions and more.
    val lowerAt by androidx.compose.animation.core.animateFloatAsState(
        if (lower) 1f else 0f, tv.own.owntv.ui.theme.ownTvTween(LOWER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "titleLower",
    )
    val lowerFocus = remember { FocusRequester() }
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    // glcn: the film's other videos — teasers, clips, featurettes, behind the scenes — each full screen with sound.
    val metaRepo: tv.own.owntv.core.metadata.MetadataRepository = koinInject()
    val extras by produceState(emptyList<tv.own.owntv.core.metadata.VideoExtra>(), meta?.tmdbId) {
        val id = meta?.tmdbId ?: return@produceState
        value = runCatching { metaRepo.extrasFor(id, isTv = false) }.getOrDefault(emptyList())
            .filterNot { it.key == meta.trailerKey?.let { k -> trailerKeysFirst(k) } }
    }
    val related by produceState(emptyList<MovieEntity>(), movie.id) {
        value = runCatching { loadRelated() }.getOrDefault(emptyList())
    }
    val goLower = { lower = true; scope.launch { androidx.compose.runtime.withFrameNanos { }; runCatching { lowerFocus.requestFocus() } }; Unit }
    val goUpper = { lower = false; runCatching { playFocus.requestFocus() }; Unit }
    val body: @Composable () -> Unit = {
        // glcn: this window's own focus — in its separate window (not in place), the shell's could not move it, and
        // ◀ ▶ were taken for nothing.
        val pageFocus = androidx.compose.ui.platform.LocalFocusManager.current
        Box(
            Modifier.fillMaxSize()
                // glcn: opened over another screen, the page grows out of the card it came from (a short zoom).
                .then(if (inPlace) Modifier else Modifier.graphicsLayer {
                    val s = PAGE_ZOOM_FROM + (1f - PAGE_ZOOM_FROM) * enter.value
                    scaleX = s; scaleY = s; alpha = (enter.value * 2f).coerceAtMost(1f)
                })
                .then(if (inPlace) Modifier else Modifier.background(page)).trapAllFocusExit()
                .onPreviewKeyEvent { e ->
                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        androidx.compose.ui.input.key.Key.DirectionDown -> if (!lower) { goLower(); true } else false
                        androidx.compose.ui.input.key.Key.DirectionUp -> { if (lower) goUpper(); true }
                        else -> false
                    }
                }
                .onKeyEvent { e ->
                    // The page's own ◀ ▶: the shell's "◀ at the edge goes to the menu" must not see them.
                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        androidx.compose.ui.input.key.Key.DirectionLeft -> { pageFocus.moveFocus(androidx.compose.ui.focus.FocusDirection.Left); true }
                        androidx.compose.ui.input.key.Key.DirectionRight -> { pageFocus.moveFocus(androidx.compose.ui.focus.FocusDirection.Right); true }
                        else -> false
                    }
                }
                .focusGroup(),
        ) {
            if (!inPlace) {
                tv.own.owntv.ui.components.ArtTint(info.backdropUrl, Modifier.fillMaxSize(), strength = 0.7f)
                // The picture — or its trailer — edge to edge.
                tv.own.owntv.features.shell.components.VodCinematicBackdrop(
                    info.backdropUrl, series = false, Modifier.align(Alignment.TopEnd),
                    trailer = meta?.trailerKey, trailerTitle = movie.name, focusKey = movie.id,
                )
            }
            // The lower half darkens for the words; the whole page darkens further for the second part.
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to page.copy(alpha = 0.8f), 1f to page.copy(alpha = 0.95f))),
            )
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = lowerAt }.background(page.copy(alpha = 0.86f)))
            // First part: the title, its facts and the buttons.
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 64.mpx, end = 96.mpx)
                    .graphicsLayer {
                        alpha = enter.value * (1f - lowerAt)
                        translationY = (1f - enter.value) * 40.mpx.toPx() - lowerAt * 160.mpx.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                tv.own.owntv.features.shell.components.VodHero(shownInfo, Modifier.width(1100.mpx), showCast = false, plotLines = 3)
                val resume = saved >= RESUME_MIN_MS
                val audioName = tracks?.first?.let { TrackLanguages.displayName(it, locale) } ?: stringResource(R.string.settings_auto)
                val subsName = when (val sub = tracks?.second) {
                    null -> stringResource(R.string.settings_auto)
                    PlaybackPrefsStore.SUBTITLES_OFF -> stringResource(R.string.common_off)
                    else -> TrackLanguages.displayName(sub, locale)
                }
                Row(
                    // The row scrolls when it does not fit; room inside it, so the focused button's glow is never clipped.
                    Modifier.padding(top = 2.mpx).offset(x = (-16).mpx).horizontalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 16.mpx, vertical = 8.mpx).focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val playLabel = if (resume) stringResource(R.string.glcn_title_resume, tv.own.owntv.features.shell.components.vodRuntime((saved / 1000).toInt()))
                        else stringResource(R.string.content_play)
                    StageButton(
                        playLabel, onClick = { playChosen(if (resume && chosen.id == movie.id) saved else 0L) },
                        icon = OwnTVIcon.PLAY, iconFilled = true, height = 64.mpx, textSize = 21,
                        modifier = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) focusedLabel = null },
                    )
                    @Composable
                    fun roundButton(label: String, icon: OwnTVIcon, filled: Boolean = false, onClick: () -> Unit) = StageButton(
                        null, onClick = onClick, icon = icon, iconFilled = filled, round = true, height = 64.mpx,
                        modifier = Modifier.onFocusChanged { if (it.isFocused) focusedLabel = label },
                    )
                    if (resume) roundButton(stringResource(R.string.glcn_title_start_over), OwnTVIcon.REWIND) { playChosen(0L) }
                    meta?.trailerKey?.let { stored -> roundButton(stringResource(R.string.glcn_dock_trailer), OwnTVIcon.VIDEO) { fullTrailer = trailerKeysFirst(stored) } }
                    // glcn: the trailer's sound, for remotes whose volume never reaches the box (a TV's own, by HDMI).
                    if (meta?.trailerKey != null) roundButton(
                        stringResource(if (tv.own.owntv.features.home.CinemaTrailer.sound) R.string.glcn_trailer_sound_off else R.string.glcn_trailer_sound_on),
                        if (tv.own.owntv.features.home.CinemaTrailer.sound) OwnTVIcon.VOLUME_MUTE else OwnTVIcon.VOLUME_HIGH,
                    ) { tv.own.owntv.features.home.CinemaTrailer.sound = !tv.own.owntv.features.home.CinemaTrailer.sound }
                    roundButton(
                        stringResource(if (favorite) R.string.content_remove_favourite else R.string.content_add_favourite),
                        OwnTVIcon.FAVORITE, filled = favorite, onClick = onToggleFavorite,
                    )
                    if (trackKey != null) {
                        roundButton(stringResource(R.string.glcn_title_audio, audioName), OwnTVIcon.AUDIO) { picking = Picker.AUDIO }
                        roundButton(stringResource(R.string.glcn_title_subtitles, subsName), OwnTVIcon.SUBTITLE) { picking = Picker.SUBTITLES }
                    }
                    if (versions.size > 1) roundButton(stringResource(R.string.glcn_title_versions, versions.size), OwnTVIcon.LAYERS) { choosingVersion = true }
                }
                // What the focused round button does, else the audio and subtitles chosen — and the way to the rest.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    Text(
                        focusedLabel ?: if (trackKey != null) stringResource(R.string.glcn_title_audio, audioName) + "  \u00B7  " + stringResource(R.string.glcn_title_subtitles, subsName) else "",
                        style = stageText(17, 600), color = StageColors.Body, maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CHEVRON_DOWN, StageColors.Muted, Modifier.size(18.mpx))
                        Text(stringResource(R.string.content_media_cast), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1)
                    }
                }
            }
            // Second part (▼): the cast, the versions and titles like it.
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 96.mpx, top = 90.mpx, end = 96.mpx)
                    .graphicsLayer { alpha = lowerAt; translationY = (1f - lowerAt) * 200.mpx.toPx() }
                    // Cast, extras, films like it and versions may not all fit: the part scrolls as the focus goes down.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 60.mpx),
                verticalArrangement = Arrangement.spacedBy(22.mpx),
            ) {
                Text(info.title, style = stageText(30, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // One focus stop for the reading part, so ▼ always has somewhere to land.
                Column(
                    Modifier.focusRequester(lowerFocus).focusable(),
                    verticalArrangement = Arrangement.spacedBy(22.mpx),
                ) {
                    if (info.cast.isNotEmpty()) {
                        Text(stringResource(R.string.content_media_cast), style = stageText(20, 700), color = StageColors.Text)
                        tv.own.owntv.features.shell.components.VodCast(info.cast, 10)
                    }
                }
                if (extras.isNotEmpty()) {
                    Text(stringResource(R.string.glcn_title_extras), style = stageText(20, 700), color = StageColors.Text)
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 10.mpx).focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(18.mpx),
                    ) {
                        extras.forEach { x ->
                            Column(Modifier.width(300.mpx)) {
                                tv.own.owntv.ui.stage.StageSurface(
                                    onClick = { fullTrailer = x.key }, radius = 12.mpx,
                                    modifier = Modifier.width(300.mpx).height(169.mpx),
                                ) { _ ->
                                    Box(Modifier.fillMaxSize()) {
                                        coil3.compose.AsyncImage(
                                            model = "https://img.youtube.com/vi/" + x.key + "/mqdefault.jpg", contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.mpx)).background(Color.White.copy(alpha = 0.06f)),
                                        )
                                        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.PLAY, Color.White, Modifier.align(Alignment.Center).size(36.mpx))
                                    }
                                }
                                Text(x.name, style = stageText(14, 600), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                            }
                        }
                    }
                }
                if (related.isNotEmpty()) {
                    // glcn: films like this one — each opens its own page.
                    Text(stringResource(R.string.glcn_similar_title), style = stageText(20, 700), color = StageColors.Text)
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 10.mpx).focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(18.mpx),
                    ) {
                        related.take(10).forEach { r ->
                            Column(Modifier.width(150.mpx)) {
                                tv.own.owntv.ui.stage.StageSurface(
                                    onClick = { onOpenRelated(r) }, radius = 12.mpx,
                                    modifier = Modifier.width(150.mpx).height(225.mpx),
                                ) { _ ->
                                    coil3.compose.AsyncImage(
                                        model = r.posterUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.mpx)).background(Color.White.copy(alpha = 0.06f)),
                                    )
                                }
                                Text(r.name, style = stageText(14, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                            }
                        }
                    }
                }
                if (versions.size > 1) {
                    Text(stringResource(R.string.glcn_title_versions, versions.size), style = stageText(20, 700), color = StageColors.Text)
                    Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                        versions.forEach { (v, playlist) ->
                            val tags = tv.own.owntv.core.live.ProviderTags.parse(v.name)
                            val quality = versionBadges(v).joinToString(" ").ifBlank { null } ?: tags.tags.joinToString(" ").ifBlank { null }
                            tv.own.owntv.ui.stage.StagePill(
                                text = listOfNotNull(quality, playlist).joinToString(" \u00B7 ").ifBlank { tags.name.ifBlank { v.name } },
                                onClick = { chosen = v; goUpper() },
                                highlighted = v.id == chosen.id,
                            )
                        }
                    }
                }
            }
        }
    }
    if (inPlace) body() else tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true, stageScaled = false) { body() }

    if (choosingVersion) tv.own.owntv.ui.stage.StagePopup(
        onDismiss = { choosingVersion = false },
        title = stringResource(R.string.glcn_title_versions, versions.size),
        width = 760.mpx,
    ) {
        versions.forEach { (v, playlist) ->
            val tags = tv.own.owntv.core.live.ProviderTags.parse(v.name)
            val quality = versionBadges(v).joinToString(" ").ifBlank { null } ?: tags.tags.joinToString(" ").ifBlank { null }
            tv.own.owntv.ui.stage.StagePopupOption(
                title = quality ?: tags.name.ifBlank { v.name },
                subtitle = listOfNotNull(tags.name.ifBlank { v.name }.takeIf { quality != null }, v.year?.toString(), playlist).joinToString(" · ").ifBlank { null },
                chosen = v.id == chosen.id,
                // glcn: picking a version makes it the page's copy (its quality shows); Play then plays it.
                onClick = { choosingVersion = false; chosen = v; runCatching { playFocus.requestFocus() } },
            )
        }
    }

    picking?.let { which ->
        val codes = remember { PICK_FIRST + TrackLanguages.sortedFor(locale).filterNot { it in PICK_FIRST } }
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { picking = null },
            title = stringResource(if (which == Picker.AUDIO) R.string.glcn_title_pick_audio else R.string.glcn_title_pick_subtitles),
            width = 620.mpx,
        ) {
            if (which == Picker.SUBTITLES) tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.common_off),
                chosen = tracks?.second == PlaybackPrefsStore.SUBTITLES_OFF,
                onClick = {
                    scope.launch { trackKey?.let { prefs.rememberSubtitleLang(it, PlaybackPrefsStore.SUBTITLES_OFF); tracks = prefs.tracksFor(it) } }
                    picking = null
                },
            )
            codes.forEach { code ->
                val current = if (which == Picker.AUDIO) tracks?.first else tracks?.second
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = TrackLanguages.displayName(code, locale),
                    chosen = current == code,
                    onClick = {
                        scope.launch {
                            trackKey?.let {
                                if (which == Picker.AUDIO) prefs.rememberAudioLang(it, code) else prefs.rememberSubtitleLang(it, code)
                                tracks = prefs.tracksFor(it)
                            }
                        }
                        picking = null
                    },
                )
            }
        }
    }
}

private enum class Picker { AUDIO, SUBTITLES }

private fun trailerKeysFirst(stored: String): String = tv.own.owntv.core.metadata.TrailerKeys.split(stored).firstOrNull() ?: stored

/** The languages most wanted here, first in the pickers; the rest follow A–Z. */
private val PICK_FIRST = listOf("spa", "eng", "cat", "fra", "por", "ita", "deu")

private const val PAGE_ENTER_MS = 420
private const val PAGE_ZOOM_FROM = 0.94f
private const val LOWER_MS = 480
/** glcn: under a minute in is nothing to resume (it read "Resume from 0 min"). */
private const val RESUME_MIN_MS = 60_000L

/** glcn: a copy's quality marks — from its name, else what its source said outright (Emby's streams, a share's file). */
internal fun versionBadges(m: MovieEntity): List<String> =
    tv.own.owntv.core.catalog.ProviderCatalogMetadataParser.badges(m.name)
        .ifEmpty { tv.own.owntv.features.shell.components.cinematicQualityBadges(m.qualityRank, m.advertisedCapabilities) }
