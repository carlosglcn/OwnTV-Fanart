package tv.own.owntv.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.glcn.CinemaEpisode
import tv.own.owntv.glcn.CinemaInfo
import tv.own.owntv.glcn.staggerIn
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.displayLabel
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTitleArt
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the film player's "cinema" chrome (films and episodes), in the spirit of Apple TV and Netflix:
 *
 * - [CinemaBanner]: the film's lettering at the foot of the picture over a dark wash, its quality badges, the
 *   time it ends, the timeline and a short row of actions.
 * - [CinemaPanel]: ▲ / an action opens a panel on the right while the picture slides to the left and keeps
 *   playing — Info (with the rating), Audio, Subtitles, Episodes and Settings.
 *
 * Always dark, whatever the app's style: a film is watched with the lights down.
 */

internal enum class CinemaTab { INFO, AUDIO, SUBS, EPISODES, SETTINGS }

/** What is actually playing, read from the engine: picture, sound and who plays it. */
@Immutable
internal data class CinemaTech(
    val engine: StreamEngine? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val hdr: StreamHdrMode? = null,
    val dolbyVision: Boolean = false,
    val dvConverted: Boolean = false,
    val audioCodec: String? = null,
    val channels: Int? = null,
    val passthrough: Boolean? = null,
)

/** A quality badge; [key] names an optional official artwork, `res/drawable/glcn_badge_<key>`. */
@Immutable
internal data class CinemaBadge(val key: String, val label: String)

/** The engine's readout, refreshed every few seconds while the chrome shows (the tracks come in late). */
@Composable
internal fun rememberCinemaTech(player: PlaybackEngine): CinemaTech {
    val dv by DolbyVisionNow.state.collectAsStateWithLifecycle()
    val rows by produceState(emptyList<StreamInfoRow>(), player) {
        while (true) {
            value = runCatching { player.streamInfo() }.getOrDefault(emptyList())
            delay(TECH_REFRESH_MS)
        }
    }
    var t = CinemaTech()
    for (r in rows) when (val v = r.value) {
        is StreamInfoValue.Engine -> t = t.copy(engine = v.engine)
        is StreamInfoValue.Video -> t = t.copy(width = v.width, height = v.height, fps = v.fps)
        is StreamInfoValue.Hdr -> t = t.copy(hdr = v.mode)
        is StreamInfoValue.Audio -> t = t.copy(audioCodec = v.codec, channels = v.channelCount)
        is StreamInfoValue.AudioOutput -> t = t.copy(passthrough = v.kind == AudioOutputKind.PASSTHROUGH)
        else -> Unit
    }
    val onExo = t.engine == StreamEngine.EXOPLAYER
    return t.copy(dolbyVision = onExo && dv.dolbyVision, dvConverted = onExo && dv.converted)
}

/** The badges for what plays — the stream first, the catalogue's marks where the stream cannot tell. */
internal fun cinemaBadges(t: CinemaTech, info: CinemaInfo?): List<CinemaBadge> {
    val caps = info?.capabilities.orEmpty().lowercase()
    val out = ArrayList<CinemaBadge>(4)
    val h = t.height ?: 0
    val w = t.width ?: 0
    when {
        h >= UHD_HEIGHT || w >= UHD_WIDTH -> out += CinemaBadge(KEY_4K, LABEL_4K)
        h in 1 until UHD_HEIGHT && (h >= HD_HEIGHT || w >= HD_WIDTH) -> out += CinemaBadge(KEY_HD, LABEL_HD)
        h == 0 && (info?.qualityRank ?: 0) >= RANK_4K -> out += CinemaBadge(KEY_4K, LABEL_4K)
    }
    when {
        t.dolbyVision -> out += CinemaBadge(KEY_DV, LABEL_DV)
        t.hdr == StreamHdrMode.HDR10_PQ -> out += if ("hdr10+" in caps) CinemaBadge(KEY_HDR10P, LABEL_HDR10P) else CinemaBadge(KEY_HDR10, LABEL_HDR10)
        t.hdr == StreamHdrMode.HLG -> out += CinemaBadge(KEY_HLG, LABEL_HLG)
        t.hdr == null && "dolby vision" in caps -> out += CinemaBadge(KEY_DV, LABEL_DV)
        t.hdr == null && "hdr" in caps -> out += CinemaBadge(KEY_HDR10, LABEL_HDR10)
    }
    soundBadge(t.audioCodec, caps)?.let { out += it }
    when (t.channels) {
        6 -> out += CinemaBadge(KEY_51, LABEL_51)
        8 -> out += CinemaBadge(KEY_71, LABEL_71)
    }
    return out
}

private fun soundBadge(codec: String?, caps: String): CinemaBadge? {
    val c = codec?.lowercase() ?: return if ("atmos" in caps) CinemaBadge(KEY_ATMOS, LABEL_ATMOS) else null
    val atmosHint = "atmos" in caps
    return when {
        "joc" in c -> CinemaBadge(KEY_ATMOS, LABEL_ATMOS)
        "truehd" in c || "true-hd" in c -> if (atmosHint) CinemaBadge(KEY_ATMOS, LABEL_ATMOS) else CinemaBadge(KEY_TRUEHD, LABEL_TRUEHD)
        "eac3" in c || "e-ac-3" in c -> if (atmosHint) CinemaBadge(KEY_ATMOS, LABEL_ATMOS) else CinemaBadge(KEY_DDP, LABEL_DDP)
        "ac3" in c || "ac-3" in c -> CinemaBadge(KEY_DD, LABEL_DD)
        "dts" in c && ("hd" in c || "ma" in c) -> CinemaBadge(KEY_DTSHD, LABEL_DTSHD)
        "dts" in c || "dca" in c -> CinemaBadge(KEY_DTS, LABEL_DTS)
        else -> null
    }
}

/** A badge: the official artwork when the app carries it, otherwise a clean outlined label. */
@android.annotation.SuppressLint("DiscouragedApi")
@Composable
internal fun CinemaBadgeView(b: CinemaBadge, height: Int = 30) {
    val context = LocalContext.current
    val res = remember(b.key) {
        context.resources.getIdentifier(BADGE_PREFIX + b.key, "drawable", context.packageName)
    }
    if (res != 0) {
        Image(
            painterResource(res), contentDescription = b.label,
            modifier = Modifier.height(height.mpx), contentScale = ContentScale.FillHeight,
        )
    } else {
        Box(
            Modifier.height(height.mpx).border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(5.mpx))
                .padding(horizontal = 9.mpx),
            contentAlignment = Alignment.Center,
        ) {
            Text(b.label.uppercase(), style = stageText(15, 800, letterSpacing = 1.2.sp), color = Color.White, maxLines = 1)
        }
    }
}

/** The film's lettering, or its name when there is none. */
@Composable
private fun CinemaTitle(info: CinemaInfo, widthPx: Int, heightPx: Int, textSize: Int) {
    var failed by remember(info.logoUrl) { mutableStateOf(false) }
    val logo = info.logoUrl
    if (!logo.isNullOrBlank() && !failed) {
        StageTitleArt(
            url = logo, contentDescription = info.show ?: info.title,
            modifier = Modifier.widthIn(max = widthPx.mpx).height(heightPx.mpx), alignment = Alignment.BottomStart,
            onFailed = { failed = true }, onDarkScreen = true,
        )
    } else {
        Text(info.show ?: info.title, style = stageText(textSize, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun episodeLine(info: CinemaInfo): String? {
    val s = info.season
    val e = info.episode
    if (info.show == null || s == null || e == null) return null
    return stringResource(R.string.glcn_cine_episode_code, s, e) + "  ·  " + info.title
}

@Composable
private fun metaLine(info: CinemaInfo, durationMs: Long, positionMs: Long): String {
    val sep = "  ·  "
    val timeFmt = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    val parts = buildList {
        info.year?.let { add(it.toString()) }
        val min = info.durationMin ?: (durationMs / 60_000L).toInt().takeIf { it > 0 }
        min?.let { m -> add(if (m >= 60) stringResource(R.string.content_duration_hours, m / 60, m % 60) else stringResource(R.string.player_duration_minutes, m)) }
        info.rating?.takeIf { it > 0 }?.let { add(STAR + tv.own.owntv.core.metadata.ProgrammeArtResolver.format(it)) }
        if (durationMs > 0) {
            val end = System.currentTimeMillis() + (durationMs - positionMs).coerceAtLeast(0)
            add(stringResource(R.string.glcn_cine_ends_at, timeFmt(end)))
        }
    }
    return parts.joinToString(sep)
}

/** The banner at the foot of the picture. */
@Composable
internal fun CinemaBanner(
    player: PlaybackEngine,
    info: CinemaInfo,
    tech: CinemaTech,
    position: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasEpisodes: Boolean,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    onOpen: (CinemaTab) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.62f).background(
                Brush.verticalGradient(0f to Color.Transparent, 0.38f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.9f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 80.mpx, end = 80.mpx, bottom = 44.mpx)) {
            Box(Modifier.staggerIn(0)) { CinemaTitle(info, widthPx = 620, heightPx = 140, textSize = 56) }
            episodeLine(info)?.let {
                Text(it, style = stageText(24, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.staggerIn(40).padding(top = 10.mpx))
            }
            Row(
                Modifier.staggerIn(80).padding(top = 14.mpx),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
            ) {
                cinemaBadges(tech, info).forEach { CinemaBadgeView(it) }
                Text(metaLine(info, duration, position()), style = stageText(21, 600), color = Color.White.copy(alpha = 0.78f), maxLines = 1, modifier = Modifier.padding(start = 6.mpx))
            }
            if (duration > 0) {
                val seekStep by player.seekStepMs.collectAsStateWithLifecycle()
                val buffered by player.bufferedMs.collectAsStateWithLifecycle()
                val pos = position()
                Row(Modifier.staggerIn(120).padding(top = 22.mpx).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(pos), style = stageText(20, 700), color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.width(16.mpx))
                    Box(Modifier.weight(1f)) {
                        SeekBar(positionMs = pos, durationMs = duration, bufferedMs = buffered, stepMs = seekStep, onSeek = { player.seekBy(it) }, focusRequester = seekFocus)
                    }
                    Spacer(Modifier.width(16.mpx))
                    Text(stringResource(R.string.player_time_remaining, formatTime((duration - pos).coerceAtLeast(0))), style = stageText(20, 700), color = Color.White.copy(alpha = 0.85f))
                }
            }
            Row(
                Modifier.staggerIn(160).padding(top = 18.mpx).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageTool(
                    text = stringResource(if (isPlaying) R.string.glcn_cine_pause else R.string.glcn_cine_play),
                    icon = if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY,
                    onClick = { player.togglePlayPause() }, boxed = true,
                    modifier = Modifier.focusRequester(playFocus),
                )
                StageTool(stringResource(R.string.glcn_cine_audio_subs), icon = OwnTVIcon.SUBTITLE, onClick = { onOpen(CinemaTab.AUDIO) }, boxed = true)
                if (hasEpisodes) StageTool(stringResource(R.string.glcn_cine_episodes), icon = OwnTVIcon.LIST, onClick = { onOpen(CinemaTab.EPISODES) }, boxed = true)
                StageTool(stringResource(R.string.glcn_cine_info), icon = OwnTVIcon.INFO, onClick = { onOpen(CinemaTab.INFO) }, boxed = true)
                StageTool(stringResource(R.string.glcn_cine_settings), icon = OwnTVIcon.GEAR, onClick = { onOpen(CinemaTab.SETTINGS) }, boxed = true)
                if (hasNext) StageTool(stringResource(R.string.glcn_cine_next), icon = OwnTVIcon.SKIP_NEXT, onClick = onNext, boxed = true)
            }
        }
    }
}

/** What the Settings tab can open or do — the HUD's own dialogs and actions. */
internal class CinemaActions(
    val openDialog: (HudDialog) -> Unit,
    val vodOnExo: Boolean?,
    val onToggleEngine: (() -> Unit)?,
    val onPip: (() -> Unit)?,
    val onAudioOnly: (() -> Unit)?,
    val favorite: Boolean,
    val onToggleFavorite: (() -> Unit)?,
    val onTechDetails: () -> Unit,
    val onSearchSubtitles: (() -> Unit)?,
    val onLocalSubtitle: (() -> Unit)?,
)

/** The side panel. The picture has slid to the left (the shell does that from [tv.own.owntv.glcn.CinemaStage]). */
@Composable
internal fun CinemaPanel(
    tab: CinemaTab,
    onTab: (CinemaTab) -> Unit,
    player: PlaybackEngine,
    info: CinemaInfo,
    tech: CinemaTech,
    episodes: List<CinemaEpisode>,
    episodeIndex: Int,
    onPlayEpisode: ((Int) -> Unit)?,
    actions: CinemaActions,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val slide = remember { Animatable(if (tv.own.owntv.glcn.LowRam.on) 1f else 0f) }
    LaunchedEffect(Unit) { slide.animateTo(1f, tween(PANEL_IN_MS, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
    val tabs = buildList {
        add(CinemaTab.INFO); add(CinemaTab.AUDIO); add(CinemaTab.SUBS)
        if (episodes.size > 1 && onPlayEpisode != null) add(CinemaTab.EPISODES)
        add(CinemaTab.SETTINGS)
    }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(top = 48.mpx, bottom = 48.mpx, end = 48.mpx)
                .width(PANEL_WIDTH.mpx).fillMaxHeight()
                .graphicsLayer { translationX = (1f - slide.value) * 120.mpx.toPx(); alpha = slide.value }
                .clip(RoundedCornerShape(28.mpx)).background(Color(0xF20E1113))
                .padding(28.mpx),
        ) {
            Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                tabs.forEach { t ->
                    val selected = t == tab
                    StageSurface(
                        onClick = { onTab(t) },
                        radius = 22.mpx,
                        modifier = Modifier.height(44.mpx)
                            .then(if (selected) Modifier.focusRequester(focus) else Modifier)
                            .onFocusChanged { if (it.isFocused && !selected) onTab(t) },
                        idle = if (selected) Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.mpx)) else Modifier,
                    ) { focused ->
                        Text(
                            stringResource(tabLabel(t)),
                            style = stageText(18, 700),
                            color = if (focused) stageAccent.onAccent else if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.mpx),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.mpx))
            when (tab) {
                CinemaTab.INFO -> InfoTab(info, tech, actions)
                CinemaTab.AUDIO -> AudioTab(player, actions)
                CinemaTab.SUBS -> SubsTab(player, actions)
                CinemaTab.EPISODES -> EpisodesTab(episodes, episodeIndex, onPlayEpisode)
                CinemaTab.SETTINGS -> SettingsTab(player, actions)
            }
        }
    }
}

private fun tabLabel(t: CinemaTab): Int = when (t) {
    CinemaTab.INFO -> R.string.glcn_cine_info
    CinemaTab.AUDIO -> R.string.player_tool_audio
    CinemaTab.SUBS -> R.string.player_tool_subtitles
    CinemaTab.EPISODES -> R.string.glcn_cine_episodes
    CinemaTab.SETTINGS -> R.string.glcn_cine_settings
}

/** One choosable row of the panel. */
@Composable
private fun PanelRow(text: String, onClick: () -> Unit, selected: Boolean = false, detail: String? = null, trailing: String? = null) {
    StageSurface(onClick = onClick, radius = 14.mpx, modifier = Modifier.fillMaxWidth().heightIn(min = 56.mpx)) { focused ->
        val ink = if (focused) stageAccent.onAccent else Color.White
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = 10.mpx), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = stageText(20, if (selected) 800 else 600), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = stageText(16, 500), color = ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            trailing?.let { Text(it, style = stageText(17, 700), color = ink.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.padding(start = 12.mpx)) }
            if (selected) OwnTVIcon(OwnTVIcon.CHECK, ink, Modifier.padding(start = 12.mpx).height(22.mpx).width(22.mpx))
        }
    }
}

@Composable
private fun InfoTab(info: CinemaInfo, tech: CinemaTech, actions: CinemaActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        CinemaTitle(info, widthPx = 420, heightPx = 96, textSize = 34)
        episodeLine(info)?.let { Text(it, style = stageText(19, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        info.rating?.takeIf { it > 0 }?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                Text(STAR + tv.own.owntv.core.metadata.ProgrammeArtResolver.format(r), style = stageText(30, 800), color = StageColors.RatingStar)
                Text(stringResource(R.string.glcn_cine_rating), style = stageText(17, 600), color = Color.White.copy(alpha = 0.6f))
            }
        }
        val facts = (listOfNotNull(info.year?.toString()) + info.genres.take(3)).joinToString("  ·  ")
        if (facts.isNotBlank()) Text(facts, style = stageText(18, 600), color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        info.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = stageText(18, 400), color = Color.White.copy(alpha = 0.85f), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (info.cast.isNotEmpty()) {
            Text(stringResource(R.string.glcn_cine_cast) + ": " + info.cast.take(5).joinToString(", "), style = stageText(16, 500), color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.mpx))
        Text(stringResource(R.string.glcn_cine_tech), style = stageText(16, 800), color = stageAccent.accent)
        TechLine(stringResource(R.string.glcn_cine_tech_player), when (tech.engine) {
            StreamEngine.EXOPLAYER -> stringResource(R.string.player_engine_exo)
            StreamEngine.MPV -> stringResource(R.string.player_engine_mpv)
            null -> null
        })
        TechLine(stringResource(R.string.glcn_cine_tech_picture), pictureText(tech))
        TechLine(stringResource(R.string.glcn_cine_tech_sound), soundText(tech, info))
        Spacer(Modifier.height(4.mpx))
        StageTool(stringResource(R.string.glcn_cine_details), onClick = actions.onTechDetails, icon = OwnTVIcon.INFO, boxed = true)
    }
}

@Composable
private fun TechLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(label, style = stageText(17, 700), color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(150.mpx), maxLines = 1)
        Text(value, style = stageText(17, 600), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun pictureText(t: CinemaTech): String? {
    val parts = buildList {
        if ((t.width ?: 0) > 0 && (t.height ?: 0) > 0) add("${t.width}×${t.height}")
        t.fps?.takeIf { it > 0 }?.let { f ->
            val whole = kotlin.math.abs(f - Math.round(f)) < 0.01
            add(if (whole) String.format(java.util.Locale.ROOT, FPS_WHOLE, Math.round(f)) else String.format(java.util.Locale.ROOT, FPS_FORMAT, f))
        }
        when {
            t.dvConverted -> add(stringResource(R.string.glcn_cine_dv_converted))
            t.dolbyVision -> add(LABEL_DV)
            t.hdr == StreamHdrMode.HDR10_PQ -> add(LABEL_HDR10)
            t.hdr == StreamHdrMode.HLG -> add(LABEL_HLG)
        }
    }
    return parts.joinToString("  ·  ").ifBlank { null }
}

@Composable
private fun soundText(t: CinemaTech, info: CinemaInfo): String? {
    val name = listOfNotNull(soundBadge(t.audioCodec, info.capabilities.orEmpty().lowercase())?.label ?: t.audioCodec,
        when (t.channels) { 6 -> LABEL_51; 8 -> LABEL_71; 2 -> LABEL_20; else -> null }).joinToString(" ").ifBlank { return null }
    return when (t.passthrough) {
        true -> stringResource(R.string.glcn_cine_sound_passthrough, name)
        false -> stringResource(R.string.glcn_cine_sound_decoded, name)
        null -> name
    }
}

/** A track list, read again after each pick ([tick]) and polled while still empty (heavy files list theirs late). */
@Composable
private fun rememberTracks(tick: Int, read: () -> List<TrackOption>): List<TrackOption> {
    val list by produceState(read(), tick) {
        value = read()
        repeat(TRACK_TRIES) {
            if (value.isNotEmpty()) return@produceState
            delay(TRACK_POLL)
            value = read()
        }
    }
    return list
}

@Composable
private fun AudioTab(player: PlaybackEngine, actions: CinemaActions) {
    var tick by remember { mutableStateOf(0) }
    val tracks = rememberTracks(tick) { player.audioTracks() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        itemsIndexed(tracks) { _, t ->
            PanelRow(t.displayLabel(), onClick = { player.selectAudio(t.mpvId); tick++ }, selected = t.selected, trailing = t.codec?.uppercase())
        }
        item { Spacer(Modifier.height(10.mpx)) }
        item { PanelRow(stringResource(R.string.glcn_cine_sync_audio), onClick = { actions.openDialog(HudDialog.AUDIO) }) }
    }
}

@Composable
private fun SubsTab(player: PlaybackEngine, actions: CinemaActions) {
    var tick by remember { mutableStateOf(0) }
    val tracks = rememberTracks(tick) { player.textTracks() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        item {
            PanelRow(stringResource(R.string.glcn_cine_subs_off), onClick = { player.disableSubtitles(); tick++ }, selected = tracks.none { it.selected })
        }
        itemsIndexed(tracks) { _, t ->
            PanelRow(t.displayLabel(), onClick = { player.selectSubtitle(t.mpvId); tick++ }, selected = t.selected, trailing = t.codec?.uppercase())
        }
        item { Spacer(Modifier.height(10.mpx)) }
        item { PanelRow(stringResource(R.string.glcn_cine_subs_more), onClick = { actions.openDialog(HudDialog.SUBS) }) }
        actions.onSearchSubtitles?.let { open -> item { PanelRow(stringResource(R.string.player_search_subtitles), onClick = open) } }
        actions.onLocalSubtitle?.let { open -> item { PanelRow(stringResource(R.string.player_select_local_subtitle), onClick = open) } }
    }
}

@Composable
private fun EpisodesTab(episodes: List<CinemaEpisode>, current: Int, onPlay: ((Int) -> Unit)?) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.mpx)) {
        itemsIndexed(episodes) { i, e ->
            val code = stringResource(R.string.glcn_cine_episode_code, e.season, e.number)
            PanelRow(
                text = "$code  ·  ${e.title}",
                detail = if (i == current) stringResource(R.string.glcn_cine_now_playing) else e.overview,
                selected = i == current,
                trailing = e.durationMin?.let { stringResource(R.string.player_duration_minutes, it) },
                onClick = { if (i != current) onPlay?.invoke(i) },
            )
        }
    }
}

@Composable
private fun SettingsTab(player: PlaybackEngine, actions: CinemaActions) {
    val speed by player.speed.collectAsStateWithLifecycle()
    val qualities by player.videoQualities.collectAsStateWithLifecycle()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        item { PanelRow(stringResource(R.string.player_tool_aspect), onClick = { actions.openDialog(HudDialog.ZOOM) }) }
        if (qualities.isNotEmpty()) item { PanelRow(stringResource(R.string.player_tool_quality), onClick = { actions.openDialog(HudDialog.QUALITY) }) }
        item { PanelRow(stringResource(R.string.player_tool_speed), onClick = { actions.openDialog(HudDialog.SPEED) }, trailing = formatSpeed(speed)) }
        item { PanelRow(stringResource(R.string.player_tool_volume), onClick = { actions.openDialog(HudDialog.VOLUME) }) }
        actions.onToggleEngine?.let { toggle ->
            item {
                PanelRow(
                    stringResource(R.string.player_tool_engine), onClick = toggle,
                    trailing = stringResource(if (actions.vodOnExo == true) R.string.player_engine_exo else R.string.player_engine_mpv),
                )
            }
        }
        item { PanelRow(stringResource(R.string.player_sleep_timer), onClick = { actions.openDialog(HudDialog.SLEEP_TIMER) }) }
        actions.onToggleFavorite?.let { fav -> item { PanelRow(stringResource(R.string.player_tool_favorite), onClick = fav, selected = actions.favorite) } }
        actions.onPip?.let { pip -> item { PanelRow(stringResource(R.string.player_tool_mini), onClick = pip) } }
        actions.onAudioOnly?.let { a -> item { PanelRow(stringResource(R.string.player_tool_audio_only), onClick = a) } }
    }
}

private const val TECH_REFRESH_MS = 4_000L
private const val TRACK_TRIES = 10
private const val TRACK_POLL = 300L
private const val PANEL_IN_MS = 380
private const val PANEL_WIDTH = 720
private const val UHD_HEIGHT = 1600
private const val UHD_WIDTH = 3000
private const val HD_HEIGHT = 700
private const val HD_WIDTH = 1200
private const val RANK_4K = 4
private const val STAR = "★ "
private const val FPS_FORMAT = "%.3f fps"
private const val FPS_WHOLE = "%d fps"
private const val BADGE_PREFIX = "glcn_badge_"
private const val KEY_4K = "4k"
private const val KEY_HD = "hd"
private const val KEY_DV = "dolby_vision"
private const val KEY_HDR10 = "hdr10"
private const val KEY_HDR10P = "hdr10_plus"
private const val KEY_HLG = "hlg"
private const val KEY_ATMOS = "dolby_atmos"
private const val KEY_TRUEHD = "truehd"
private const val KEY_DDP = "dolby_digital_plus"
private const val KEY_DD = "dolby_digital"
private const val KEY_DTSHD = "dts_hd"
private const val KEY_DTS = "dts"
private const val KEY_51 = "5_1"
private const val KEY_71 = "7_1"
private const val LABEL_4K = "4K"
private const val LABEL_HD = "HD"
private const val LABEL_DV = "Dolby Vision"
private const val LABEL_HDR10 = "HDR10"
private const val LABEL_HDR10P = "HDR10+"
private const val LABEL_HLG = "HLG"
private const val LABEL_ATMOS = "Dolby Atmos"
private const val LABEL_TRUEHD = "TrueHD"
private const val LABEL_DDP = "Dolby Digital Plus"
private const val LABEL_DD = "Dolby Digital"
private const val LABEL_DTSHD = "DTS-HD"
private const val LABEL_DTS = "DTS"
private const val LABEL_51 = "5.1"
private const val LABEL_71 = "7.1"
private const val LABEL_20 = "2.0"
