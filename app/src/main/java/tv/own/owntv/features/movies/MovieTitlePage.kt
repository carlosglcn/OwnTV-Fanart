package tv.own.owntv.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    var fullTrailer by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val enter = remember { androidx.compose.animation.core.Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    val enterSpec = tv.own.owntv.ui.theme.ownTvTween<Float>(PAGE_ENTER_MS, androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
    LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { playFocus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    fullTrailer?.let { key -> tv.own.owntv.ui.components.TrailerPlayerScreen(videoKey = key, title = movie.name) { fullTrailer = null } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)

    if (inPlace) androidx.compose.runtime.DisposableEffect(Unit) {
        tv.own.owntv.features.home.CinemaTrailer.keepOnMove = true
        onDispose { tv.own.owntv.features.home.CinemaTrailer.keepOnMove = false }
    }
    val pageFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().then(if (inPlace) Modifier.trapAllFocusExit().onKeyEvent { e ->
            // The page's own ◀ ▶ between its buttons: the shell's "◀ at the edge goes to the menu" must not see them.
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionLeft -> { pageFocus.moveFocus(androidx.compose.ui.focus.FocusDirection.Left); true }
                androidx.compose.ui.input.key.Key.DirectionRight -> { pageFocus.moveFocus(androidx.compose.ui.focus.FocusDirection.Right); true }
                androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionDown -> true
                else -> false
            }
        }.focusGroup() else Modifier.background(page))) {
            if (!inPlace) {
                tv.own.owntv.ui.components.ArtTint(info.backdropUrl, Modifier.fillMaxSize(), strength = 0.7f)
                // The picture, wide, and the trailer over it — the same window as the list's.
                tv.own.owntv.features.shell.components.VodCinematicBackdrop(
                    info.backdropUrl, series = false, Modifier.align(Alignment.TopEnd),
                    trailer = meta?.trailerKey, trailerTitle = movie.name, focusKey = movie.id,
                )
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer { if (inPlace) alpha = enter.value }
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 0.8f to page.copy(alpha = 0.85f), 1f to page.copy(alpha = 0.96f))),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 96.mpx, bottom = 72.mpx, end = 96.mpx)
                    .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.mpx.toPx() },
                verticalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                // The list's hero, in full: lettering, facts, synopsis and cast.
                tv.own.owntv.features.shell.components.VodHero(info, Modifier.width(1100.mpx))
                Row(Modifier.padding(top = 8.mpx).horizontalScroll(androidx.compose.foundation.rememberScrollState()).focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                    val resume = saved >= RESUME_MIN_MS
                    StageButton(
                        if (resume) stringResource(R.string.glcn_title_resume, tv.own.owntv.features.shell.components.vodRuntime((saved / 1000).toInt()))
                        else stringResource(R.string.content_play),
                        onClick = { onPlay(if (resume) saved else 0L) },
                        icon = OwnTVIcon.PLAY, iconFilled = true, height = 56.mpx, textSize = 20,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    if (resume) StageButton(stringResource(R.string.glcn_title_start_over), onClick = { onPlay(0L) }, icon = OwnTVIcon.REWIND, height = 56.mpx, textSize = 19)
                    meta?.trailerKey?.let { stored ->
                        StageButton(stringResource(R.string.glcn_dock_trailer), onClick = {
                            fullTrailer = trailerKeysFirst(stored)
                        }, icon = OwnTVIcon.VIDEO, height = 56.mpx, textSize = 19)
                    }
                    if (trackKey != null) {
                        StageButton(
                            stringResource(R.string.glcn_title_audio, tracks?.first?.let { TrackLanguages.displayName(it, locale) } ?: stringResource(R.string.settings_auto)),
                            onClick = { picking = Picker.AUDIO }, icon = OwnTVIcon.AUDIO, height = 56.mpx, textSize = 19,
                        )
                        StageButton(
                            stringResource(
                                R.string.glcn_title_subtitles,
                                when (val s = tracks?.second) {
                                    null -> stringResource(R.string.settings_auto)
                                    PlaybackPrefsStore.SUBTITLES_OFF -> stringResource(R.string.common_off)
                                    else -> TrackLanguages.displayName(s, locale)
                                },
                            ),
                            onClick = { picking = Picker.SUBTITLES }, icon = OwnTVIcon.SUBTITLE, height = 56.mpx, textSize = 19,
                        )
                    }
                    if (versions.size > 1) StageButton(
                        stringResource(R.string.glcn_title_versions, versions.size),
                        onClick = { choosingVersion = true }, icon = OwnTVIcon.LAYERS, height = 56.mpx, textSize = 19,
                    )
                    StageButton(
                        stringResource(if (favorite) R.string.content_remove_favourite else R.string.content_add_favourite),
                        onClick = onToggleFavorite, icon = OwnTVIcon.FAVORITE, iconFilled = favorite, height = 56.mpx, textSize = 19,
                    )
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
            tv.own.owntv.ui.stage.StagePopupOption(
                title = tags.name.ifBlank { v.name },
                subtitle = listOfNotNull(tags.tags.joinToString(" ").ifBlank { null }, v.year?.toString(), playlist).joinToString(" · ").ifBlank { null },
                chosen = v.id == movie.id,
                onClick = { choosingVersion = false; onPlayVersion(v) },
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
/** glcn: under a minute in is nothing to resume (it read "Resume from 0 min"). */
private const val RESUME_MIN_MS = 60_000L
