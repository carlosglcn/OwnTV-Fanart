package tv.own.owntv.player

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.tv.material3.MaterialTheme
import tv.own.owntv.ui.theme.hudAccent
import tv.own.owntv.ui.theme.hudShade
import tv.own.owntv.ui.theme.hudInk
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.player.PlayerControl
import tv.own.owntv.core.player.ControlCluster
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn

/**
 * The HUD's chrome: the top strip, the channel/direct-tune OSD cards, the centre transport and the
 * bottom bar. Split out of [PlayerHud] — behaviour unchanged; these are `internal` rather than
 * file-private only because [PlayerHud] now lives in a sibling file.
 */

// ---------------- Top bar ----------------

@Composable
internal fun TopBar(
    player: PlaybackEngine, isLive: Boolean, chips: List<String>, duration: Long,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    // Live only: the Now/Next guide, rendered at the far end of the same strip.
    trailing: (@Composable () -> Unit)? = null,
    // The clock. Rendered between two equal-weight halves so it lands on the true centre of the screen
    // whatever the channel name and guide card happen to be doing on either side of it.
    centre: (@Composable () -> Unit)? = null,
    // Live: one more line under the channel name (a Sports match's score).
    below: (@Composable () -> Unit)? = null,
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    val localizedSubtitle = meta.localizedSubtitle()
    // Films and series: the title art in place of the name, once it has arrived and loaded.
    val titleArt = meta.titleLogoUrl.takeUnless { isLive }
    var titleArtFailed by remember(titleArt) { mutableStateOf(false) }
    val showTitleArt = titleArt != null && !titleArtFailed
    // An episode carries its show's name as the subtitle; a film's name is the title.
    val episode = meta.seasonNumber != null || meta.episodeNumber != null
    val res by player.videoRes.collectAsStateWithLifecycle()
    val ultraHdBadge = !isLive && isUltraHd(res)
    val vodSubtitle = if (isLive) {
        meta.subtitle
    } else {
        buildList {
            localizedSubtitle?.takeIf { it.isNotBlank() && !(showTitleArt && episode) }?.let(::add)
            meta.seasonNumber?.let { add(stringResource(R.string.player_season_number, it)) }
        }.joinToString(stringResource(R.string.content_metadata_separator)).ifBlank { null }
    }
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
      // Left half. Equal weight to the right half, so [centre] sits on the real midpoint of the screen
      // rather than the midpoint of whatever space the title happened to leave over.
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        // Live: the channel logo sits with the channel NAME (identity), not with the programme — so the
        // whole "which channel am I on" group reads as one unit however wide the TV is.
        if (isLive) {
            ChannelLogo(meta.logoUrl, displayTitle, size = 46)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            val chipRow: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ultraHdBadge) res?.let { UltraHdBadge(it) }
                    val durMin = (duration / 60000)
                    val parts = buildList {
                        meta.year?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (!isLive && durMin > 0) add(stringResource(R.string.player_duration_minutes, durMin))
                        // aspect · resolution · fps · audio — 4K already stands in its badge
                        addAll(if (ultraHdBadge) chips.filterNot { it == res } else chips)
                    }
                    parts.forEachIndexed { i, label ->
                        if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(hudInk.copy(alpha = 0.3f)))
                        Text(label, style = tv.own.owntv.ui.theme.stageText(22, 700), color = hudInk.copy(alpha = 0.5f))
                    }
                    // The LIVE badge has left the top bar: live status belongs beside the timeline that
                    // describes it, so it is now the stateful badge at the right end of the dock's band A.
                }
            }
            // Live stacks the technical chips ABOVE the channel name; VOD keeps title-then-chips.
            if (isLive) {
                chipRow()
                Spacer(Modifier.height(2.dp))
                // Channel number ahead of the name — this is where you look to learn the number of a
                // channel you arrived at by zapping. meta.subtitle carries it ("#123") only while the
                // "Channel numbers" setting is on, so an off setting leaves the name alone.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = tv.own.owntv.ui.theme.stageText(32, 600),
                            color = hudInk.copy(alpha = 0.45f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(displayTitle, style = tv.own.owntv.ui.theme.stageText(32, 600), color = hudInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                below?.invoke()
            } else {
                if (showTitleArt && titleArt != null) {
                    tv.own.owntv.ui.stage.StageTitleArt(
                        url = titleArt,
                        contentDescription = if (episode) meta.subtitle else displayTitle,
                        onFailed = { titleArtFailed = true },
                        modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 420.dp).fillMaxWidth().height(72.dp),
                    )
                }
                vodSubtitle?.let {
                    Text(it, style = tv.own.owntv.ui.theme.stageText(22, 700), color = hudInk.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // A film's art is its name; an episode keeps its own name under the show's art.
                if (!showTitleArt || episode) {
                    Text(displayTitle, style = tv.own.owntv.ui.theme.stageText(32, 600), color = hudInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                chipRow()
            }
        }
      } // end left half
      if (centre != null) {
          Spacer(Modifier.width(20.dp))
          centre()
          Spacer(Modifier.width(20.dp))
      }
      // Right half, pushed to the far edge. Same weight as the left, hence the true centring above.
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
          if (trailing != null) trailing()
      }
    }
}

/**
 * glcn: live TV's channel, at the top of the dock — back, the channel's logo, its number and name, and
 * the stream as chips: resolution, frame rate, bitrate, sound. The engine is on its own button below and
 * the shape of the picture rarely tells anyone anything, so neither is repeated here.
 */
@Composable
internal fun LiveChannelHeader(player: PlaybackEngine, chips: List<String>, onBack: () -> Unit) {
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val name = meta.title?.takeIf { it.isNotBlank() } ?: ""
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(OwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier.size(width = 80.dp, height = 54.dp).clip(RoundedCornerShape(12.dp)).background(hudShade(0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!meta.logoUrl.isNullOrBlank()) {
                AsyncImage(model = meta.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(5.dp))
            } else {
                Text(name.take(3).uppercase(), style = MaterialTheme.typography.labelLarge, color = hudInk, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = hudInk.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                }
                Text(name, style = tv.own.owntv.ui.theme.stageText(28, 800), color = hudInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StreamChipRow(chips, hudInk, Modifier.padding(top = 5.dp))
        }
    }
}

/**
 * The stream's facts (4K · 50 Hz · HDR…) as one low line of small chips — shared by the classic header and the
 * Cine / Ficha banners, so every banner style tells what is playing. The engine and key:value lines stay out.
 */
@Composable
internal fun StreamChipRow(chips: List<String>, ink: Color, modifier: Modifier = Modifier, size: Int = 15) {
    val shown = chips.filterNot { it.contains(':') || it == "MPV" || it == "EXO" }
    if (shown.isEmpty()) return
    // glcn: one low line of small chips (they used to wrap to a second line and lift the whole bar). Only whole
    // chips: those that do not fit are left out, from the end, instead of being cut through the middle.
    androidx.compose.ui.layout.Layout(
        content = {
            shown.forEach { label ->
                Text(
                    label,
                    style = tv.own.owntv.ui.theme.stageText(size, 700),
                    color = ink.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(ink.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        var used = 0
        val kept = ArrayList<androidx.compose.ui.layout.Placeable>()
        for (pl in placeables) {
            val need = (if (kept.isEmpty()) 0 else gap) + pl.width
            if (used + need > room) break
            kept += pl; used += need
        }
        val height = kept.maxOfOrNull { it.height } ?: 0
        layout(used, height) {
            var x = 0
            kept.forEach { pl -> pl.place(x, (height - pl.height) / 2); x += pl.width + gap }
        }
    }
}

/** The 4K badge of a film or episode: the engine's own resolution label, on gold. */
@Composable
private fun UltraHdBadge(label: String) {
    Text(
        label,
        style = tv.own.owntv.ui.theme.stageText(18, 800),
        color = Color(0xFF1A1300),
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFE2B23C)).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** The channel logo tile, falling back to the first letters of the channel name. */
@Composable
private fun ChannelLogo(logoUrl: String?, title: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text((title ?: "?").take(3).uppercase(), style = tv.own.owntv.ui.theme.stageText(22, 700), color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
    }
}

/**
 * The player's channel OSD. glcn: one glass card a little in from the corner — the logo on a light glass plate, the
 * number large in the accent and the name on one line, the programme under them (picture, title on up to two lines,
 * progress, what follows). Zapping changes only what is inside: the card stays put.
 */
@Composable
internal fun ChannelOsdCard(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    /** Now/next under the name on a zap; null (or a channel with no guide) keeps it name-only. */
    guide: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.width(if (guide != null) 620.dp else 400.dp)
            .clip(RoundedCornerShape(22.dp)).background(hudShade(0.62f))
            .border(1.dp, hudInk.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // glcn: no cross-fade between channels — the outgoing channel's picture flashed up before the new one's.
        val shownTitle = title
        run {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.14f)).padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        else Text((shownTitle ?: "?").take(3).uppercase(), style = tv.own.owntv.ui.theme.stageText(18, 800), color = hudInk)
                    }
                    // A channel's number ("#7") stands large in the accent; any other line stays small under the name.
                    val number = subtitle?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() && subtitle.count(Char::isLetter) == 0 }
                    if (number != null) Text(number, style = tv.own.owntv.ui.theme.stageText(30, 800), color = hudAccent, maxLines = 1)
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(shownTitle ?: "", style = tv.own.owntv.ui.theme.stageText(25, 600), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (number == null) subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = tv.own.owntv.ui.theme.stageText(18, 500), color = hudInk.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (guide != null) Box(Modifier.padding(top = 10.dp)) { guide() }
            }
        }
    }
}

@Composable
internal fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier, guide: (@Composable () -> Unit)? = null) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() }
        ?: meta.episodeNumber?.let { stringResource(R.string.player_episode_number, it) }
        ?: ""
    ChannelOsdCard(title = displayTitle, subtitle = meta.localizedSubtitle(), logoUrl = meta.logoUrl, modifier = modifier, guide = guide)
}

/** Direct-tune entry OSD: the number as it's typed, on the same surface (position, radius, scrim) the
 *  channel card uses, so a resolved number simply becomes that card. A blinking caret says "still
 *  accepting digits" and the bar along the bottom drains over the auto-submit window, so the wait is
 *  visible instead of mysterious. [error] turns it into the failure readout for the same number. */
@Composable
internal fun ChannelNumberCard(digits: String, error: String? = null, modifier: Modifier = Modifier) {
    // Animations Off: a steady caret, with no infinite transition started (never a 0 ms one).
    val caretAlpha = if (!animationsOn) 1f else {
        val caret = rememberInfiniteTransition(label = "tuneCaret")
        val blink by caret.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "tuneCaretAlpha",
        )
        blink
    }
    val countdown = remember { Animatable(0f) }
    // Captured before the draw lambda: a DrawScope is not a composable, so it can't read the theme.
    val accent = hudAccent
    LaunchedEffect(digits, error) {
        if (error != null) { countdown.snapTo(0f); return@LaunchedEffect }
        countdown.snapTo(1f)
        countdown.animateTo(0f, tween(DIRECT_TUNE_TIMEOUT_MS.toInt(), easing = LinearEasing))
    }
    val track = hudInk.copy(alpha = 0.08f)
    Column(
        modifier.widthIn(min = 148.dp, max = 340.dp).clip(RoundedCornerShape(14.dp)).background(hudShade(0.55f))
            // Painted, not laid out: a real bar would fillMaxWidth and stretch the card to its max width.
            .drawWithContent {
                drawContent()
                val barHeight = 3.dp.toPx()
                val top = Offset(0f, size.height - barHeight)
                drawRect(track, topLeft = top, size = Size(size.width, barHeight))
                drawRect(accent, topLeft = top, size = Size(size.width * countdown.value, barHeight))
            }
            .padding(bottom = 3.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                stringResource(R.string.player_channel_label),
                style = tv.own.owntv.ui.theme.stageText(20, 500), color = accent, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    digits,
                    style = tv.own.owntv.ui.theme.stageText(43, 400), color = hudInk, fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                if (error == null) {
                    Box(
                        Modifier.padding(start = 4.dp, bottom = 4.dp).width(3.dp).height(22.dp)
                            .clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = caretAlpha)),
                    )
                }
            }
            error?.let {
                Text(it, style = tv.own.owntv.ui.theme.stageText(22, 700), color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Center transport ----------------

@Composable
internal fun CenterControls(
    player: PlaybackEngine, nav: NavState, isPlaying: Boolean, isLive: Boolean,
    onRewindLive: (() -> Unit)?, onForwardLive: (() -> Unit)?, timeshiftOffset: () -> Int?,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
    /** glcn: the stream is loading — the play button wears the turning ring (see [LoadMorph]). */
    buffering: Boolean = false,
) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
    val rewindMode = onRewindLive != null // this is a catch-up-capable Live channel
    // Read here, not in the HUD root: it ticks every second while rewound.
    val timeshiftOffsetSec = timeshiftOffset()
    val timeshifting = timeshiftOffsetSec != null
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (timeshifting) {
            // Counts down as the archive catches up to the live edge; grows if you pause.
            Text(
                if (timeshiftOffsetSec <= 1) stringResource(R.string.player_at_live_edge) else stringResource(R.string.player_behind_live, mmss(timeshiftOffsetSec)),
                style = tv.own.owntv.ui.theme.stageText(25, 600),
                color = hudAccent,
            )
            Spacer(Modifier.height(12.dp))
        }
        TransportCapsule {
            if (nav.hasPrev) CircleButton(OwnTVIcon.SKIP_PREVIOUS, size = 44) { player.previous() }
            when {
                rewindMode -> CircleButton(OwnTVIcon.REWIND, size = 44) { onRewindLive() } // step back into the archive
                !isLive -> CircleButton(OwnTVIcon.REWIND, size = 44) { player.seekBy(-seekStep) }
            }
            Box(contentAlignment = Alignment.Center) {
                if (buffering) SpinRing(sizeDp = 76)
                CircleButton(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY, size = 64, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
            }
            when {
                rewindMode && timeshifting -> CircleButton(OwnTVIcon.FORWARD, size = 44) { onForwardLive?.invoke() } // toward live
                !isLive && !rewindMode -> CircleButton(OwnTVIcon.FORWARD, size = 44) { player.seekBy(seekStep) }
            }
            // "Go to live" is no longer a transport button: it is the Go Live pill at the head of the
            // dock's left cluster, beside the timeline it acts on.
            if (nav.hasNext) CircleButton(OwnTVIcon.SKIP_NEXT, size = 44) { player.next() }
        }
    }
}

// ---------------- Bottom bar ----------------

@Composable
internal fun BottomBar(
    player: PlaybackEngine, isLive: Boolean, position: () -> Long, duration: Long,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode, speedLabel: String,
    onScrubLive: ((Int) -> Unit)?, timeshiftOffset: () -> Int?, onGoToLive: (() -> Unit)?, onOpenJumpBack: (() -> Unit)?,
    liveProgrammes: List<LiveProgramme> = emptyList(),
    liveGaps: () -> List<LongRange> = { emptyList() },
    compatMode: Boolean?, onToggleCompatMode: (() -> Unit)?,
    vodOnExo: Boolean?, onToggleVodEngine: (() -> Unit)?,
    onInfo: (() -> Unit)? = null, infoOn: Boolean = false, onReport: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onChannels: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
    onOtherChannels: (() -> Unit)? = null, otherChannelsCount: Int = 0,
    onChannelCopies: (() -> Unit)? = null,
    hasSources: Boolean = false,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onAudioMode: (() -> Unit)?,
    onMultiview: (() -> Unit)? = null, onRecordThis: (() -> Unit)? = null, recordingThis: Boolean = false,
    /** Given to whichever timeline is drawn, so Left/Right with the controls hidden can land on it. */
    seekFocus: FocusRequester? = null,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    /** glcn: a row at the top of the dock — live TV puts the channel there (logo, name, stream chips). */
    header: (@Composable () -> Unit)? = null,
    /** glcn: live TV — the programme on air, at the dock's right. */
    side: (@Composable () -> Unit)? = null,
    /** glcn: Ficha banner — the tools beside the channel on its line, and [belowHeader] under them. */
    toolsUp: Boolean = false,
    belowHeader: (@Composable () -> Unit)? = null,
) {
    // Changes only while rewound into the archive. The film position is read lower still, by
    // [VodTimeline], so the tool buttons do not redraw every second.
    val timeshiftOffsetSec = timeshiftOffset()
    // Only whether one runs: the countdown itself ticks every second, and this bar must not.
    val sleepLeft = org.koin.compose.koinInject<SleepTimer>().remainingMs.collectAsStateWithLifecycle()
    val sleepRunning by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { sleepLeft.value != null } }
    // glcn: the tools as one piece, so a banner style can put them beside the channel ([toolsUp]).
    val tools: @Composable (Modifier) -> Unit = { toolsModifier ->
        Column(toolsModifier) {
        val toolFocus = androidx.compose.runtime.remember { ToolFocusState() }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        // glcn: the tool's name above the row; on the Ficha banner under it (above, it left an empty band at the top).
        if (!toolsUp) ToolLabelLine(toolFocus)
        androidx.compose.runtime.CompositionLocalProvider(LocalToolFocus provides toolFocus) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { toolFocus.origin = it.positionInWindow() }) {
        val toolsMaxWidth = maxWidth * 0.62f
        ToolFocusBox(toolFocus, Modifier.matchParentSize())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().focusGroup().onKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                val toward = when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left to androidx.compose.ui.focus.FocusDirection.Right
                    androidx.compose.ui.input.key.Key.DirectionRight -> androidx.compose.ui.focus.FocusDirection.Right to androidx.compose.ui.focus.FocusDirection.Left
                    else -> return@onKeyEvent false
                }
                if (!focusManager.moveFocus(toward.first)) {
                    // At the end: walk to the other end, one step at a time.
                    var guard = 0
                    while (guard++ < 40 && focusManager.moveFocus(toward.second)) { }
                }
                true
            },
        ) {
            // glcn: the media cluster takes the room the tools leave and scrolls within it, so at a large
            // interface size no button is cut off or squeezed (the focused one is brought into view).
            Row(
                Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The ORDER comes from core's canonical list, not from the order these lines
                // happen to be written in. Each control keeps its own composable and its own
                // condition; only the sequence is shared, which is what makes this bar and the
                // phone's read alike. The `when` is exhaustive, so a control added to the shared
                // list cannot be silently missing here.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.MEDIA).forEach { control ->
                    when (control) {
                        // Go Live leads the media cluster, but only while there is actually a live
                        // edge to go back to: at the edge it is absent and the tools sit flush left,
                        // and falling behind inserts it and pushes them right. onGoToLive alone is
                        // non-null on every tuned live channel, so onScrubLive is what says the
                        // channel has an archive at all.
                        PlayerControl.GO_LIVE -> if (onGoToLive != null && onScrubLive != null) {
                            GoLivePill(enabled = (timeshiftOffsetSec ?: 0) > 1) { onGoToLive() }
                        }
                        PlayerControl.VOLUME ->
                            CtrlButton(volumeIcon(volume), label = stringResource(R.string.player_tool_volume)) { onOpenDialog(HudDialog.VOLUME) }
                        PlayerControl.SPEED ->
                            SpeedButton(label = speedLabel, active = speedLabel != stringResource(R.string.player_speed_normal_short), toolLabel = stringResource(R.string.player_tool_speed)) { onOpenDialog(HudDialog.SPEED) }
                        PlayerControl.SUBTITLES ->
                            CtrlButton(OwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }, label = stringResource(R.string.player_tool_subtitles)) { onOpenDialog(HudDialog.SUBS) }
                        PlayerControl.AUDIO ->
                            CtrlButton(OwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }, label = stringResource(R.string.player_tool_audio)) { onOpenDialog(HudDialog.AUDIO) }
                        // Favorite the current channel/movie/series without leaving the stream (coral
                        // heart = on, the same colour the marker has on posters and in browse rows).
                        PlayerControl.FAVOURITE -> if (onToggleFavorite != null) {
                            CtrlButton(OwnTVIcon.FAVORITE, active = favorite, activeTint = OwnTVTheme.colors.favorite, label = stringResource(R.string.player_tool_favorite)) { onToggleFavorite() }
                        }
                        // "Go back to…" — jump straight to a time in this channel's archive. Only on
                        // catch-up channels. CATCHUP (a TV with a replay loop): REWIND is already the
                        // transport button beside it, and a plain clock would not say which of the
                        // two time controls this is.
                        PlayerControl.CATCH_UP -> if (onOpenJumpBack != null) {
                            CtrlButton(OwnTVIcon.CATCHUP, label = stringResource(R.string.player_tool_catchup)) { onOpenJumpBack() }
                        }
                        // Back to the channel watched before; present only once there is one.
                        PlayerControl.PREVIOUS_CHANNEL -> if (onPreviousChannel != null) {
                            CtrlButton(OwnTVIcon.HISTORY, label = stringResource(R.string.player_previous_channel)) { onPreviousChannel() }
                        }
                        // Sports Hub: only for a channel started from a match.
                        PlayerControl.OTHER_CHANNELS -> {
                            if (onOtherChannels != null) {
                                CtrlButton(OwnTVIcon.SPORTS, label = stringResource(R.string.sports_other_channels_count, otherChannelsCount)) { onOtherChannels() }
                            }
                            // glcn: live — switch between the copies of this channel (FHD / HD / SD), nothing else.
                            if (onChannelCopies != null) {
                                CtrlButton(OwnTVIcon.VIDEO, label = stringResource(R.string.player_tool_quality)) { onChannelCopies() }
                            }
                        }
                        // Not shown on the TV: Back from the player already returns to the match page.
                        PlayerControl.MATCH -> Unit
                        // The same channel in another quality, category or playlist. Live only; the
                        // list is read when it opens.
                        PlayerControl.SOURCES -> if (hasSources) {
                            CtrlButton(OwnTVIcon.PLAYLIST, label = stringResource(R.string.player_tool_sources)) { onOpenDialog(HudDialog.SOURCES) }
                        }
                        // Belongs to the tools cluster; `clusterFor` never hands them to this loop.
                        PlayerControl.BRIGHTNESS, PlayerControl.CHANNEL_LIST, PlayerControl.ENGINE,
                        PlayerControl.ASPECT, PlayerControl.QUALITY, PlayerControl.MINI_PLAYER, PlayerControl.AUDIO_ONLY,
                        PlayerControl.MULTIVIEW, PlayerControl.RECORD, PlayerControl.SLEEP_TIMER,
                        PlayerControl.INFO, PlayerControl.REPORT,
                        -> Unit
                    }
                }
                // glcn: the channel list and the guide, without leaving the channel.
                if (onChannels != null) {
                    CtrlButton(OwnTVIcon.LIST, label = stringResource(R.string.content_channel_overlay_title)) { onChannels() }
                }
                if (onGuide != null) {
                    CtrlButton(OwnTVIcon.EPG, label = stringResource(R.string.common_nav_guide)) { onGuide() }
                }
            }
            if (isLive && side != null && onScrubLive == null) {
                Spacer(Modifier.width(8.dp))
                LiveStateBadge(timeshiftOffsetSec)
                Spacer(Modifier.width(8.dp))
            }
            // glcn: the tools keep their own width up to most of the bar, and scroll past it.
            Row(
                Modifier.widthIn(max = toolsMaxWidth).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The ORDER is core's, exactly as in the media cluster above. Each control
                // keeps its own composable and condition; only the sequence is shared.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.TOOLS).forEach { control ->
                    when (control) {
                        PlayerControl.ENGINE -> {
                            // Live "compatibility mode" (Live TV + channels opened from the Guide):
                            // pin this channel to mpv. The pill shows the active engine and flips on
                            // click (teal while pinned to mpv).
                            if (onToggleCompatMode != null) {
                                EngineToggle(label = stringResource(if (compatMode == true) R.string.player_engine_mpv else R.string.player_engine_exo), active = compatMode == true, toolLabel = stringResource(R.string.player_tool_engine)) { onToggleCompatMode() }
                            }
                            // VOD engine toggle (Movies/Series): flip THIS movie/episode between mpv
                            // and ExoPlayer. The pill shows the active engine (teal while ExoPlayer
                            // owns playback).
                            if (onToggleVodEngine != null) {
                                EngineToggle(label = stringResource(if (vodOnExo == true) R.string.player_engine_exo else R.string.player_engine_mpv), active = vodOnExo == true, toolLabel = stringResource(R.string.player_tool_engine)) { onToggleVodEngine() }
                            }
                        }
                        // Aspect/zoom works in every mode now — direct mode resizes the surface view
                        // itself (see MpvVideoSurface), GL mode scales internally.
                        // A press steps to the next mode (named beside the label, so each press shows
                        // where it landed); OK held opens the whole list.
                        PlayerControl.ASPECT ->
                            CtrlButton(
                                OwnTVIcon.ASPECT,
                                active = zoomMode != ZoomMode.FIT,
                                label = stringResource(R.string.player_tool_aspect),
                                value = stringResource(zoomMode.labelRes),
                                onLongClick = { onOpenDialog(HudDialog.ZOOM) },
                            ) { player.setZoomModeByUser(ZoomMode.entries[(zoomMode.ordinal + 1) % ZoomMode.entries.size]) }
                        // Only when this stream offers several; tinted while a pick overrides Auto.
                        PlayerControl.QUALITY -> {
                            val qualities by player.videoQualities.collectAsStateWithLifecycle()
                            val pick by player.videoQualityPick.collectAsStateWithLifecycle()
                            if (qualities.isNotEmpty()) {
                                CtrlButton(OwnTVIcon.VIDEO, active = pick != null, label = stringResource(R.string.player_tool_quality)) { onOpenDialog(HudDialog.QUALITY) }
                            }
                        }
                        PlayerControl.MINI_PLAYER -> if (onPip != null) {
                            CtrlButton(OwnTVIcon.PIP, label = stringResource(R.string.player_tool_mini)) { onPip() }
                        }
                        PlayerControl.AUDIO_ONLY -> if (onAudioMode != null) {
                            CtrlButton(OwnTVIcon.HEADPHONES, label = stringResource(R.string.player_tool_audio_only)) { onAudioMode() }
                        }
                        // Live only, and only once the user has switched Multiview on: four tiles
                        // from the channel already playing. Next to the mini-player button, which is
                        // its nearest relative.
                        PlayerControl.MULTIVIEW -> if (onMultiview != null) {
                            CtrlButton(OwnTVIcon.LIST_GRID, label = stringResource(R.string.multiview_button)) { onMultiview() }
                        }
                        // Record what is already on screen. Present only when the setting is on, so
                        // it never needs to explain itself here — the trade-off was accepted when it
                        // was enabled. Tinted while running, and the label flips, because
                        // "Record" on a button that is already recording is the kind of thing that
                        // loses an hour of somebody's evening.
                        PlayerControl.RECORD -> if (onRecordThis != null) {
                            CtrlButton(
                                OwnTVIcon.LIVE_TV,
                                active = recordingThis,
                                activeTint = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                label = stringResource(
                                    if (recordingThis) R.string.recording_stop else R.string.recording_record,
                                ),
                            ) { onRecordThis() }
                        }
                        // Stop after a while; tinted while a countdown is running.
                        PlayerControl.SLEEP_TIMER ->
                            CtrlButton(OwnTVIcon.BEDTIME, active = sleepRunning, label = stringResource(R.string.player_sleep_timer)) { onOpenDialog(HudDialog.SLEEP_TIMER) }
                        // Stream technical info (codec/res/HDR/bitrate/decoder/audio/buffer) —
                        // toggles the overlay. Parked at the far right, where the redundant
                        // exit-fullscreen button used to sit (Back already leaves the player, so
                        // that button never did anything the remote couldn't).
                        PlayerControl.INFO -> if (onInfo != null) {
                            CtrlButton(OwnTVIcon.INFO, active = infoOn, label = stringResource(R.string.player_tool_info)) { onInfo() }
                        }
                        // "Report this stream": copies the readout the user is looking at into the
                        // playback log, so a "this channel judders" complaint carries the
                        // codec/decoder/bitrate that caused it. Only offered while the info overlay
                        // is open — there is nothing to report otherwise, and the bar stays as short
                        // as it was for everyone who never needs this. That is the rule on both
                        // apps.
                        PlayerControl.REPORT -> if (infoOn && onReport != null) {
                            CtrlButton(OwnTVIcon.SHARE, label = stringResource(R.string.player_tool_report)) { onReport() }
                        }
                        // Belongs to the media cluster; `clusterFor` never hands them to this loop.
                        PlayerControl.GO_LIVE, PlayerControl.VOLUME, PlayerControl.BRIGHTNESS,
                        PlayerControl.SPEED, PlayerControl.SUBTITLES, PlayerControl.AUDIO,
                        PlayerControl.FAVOURITE, PlayerControl.CATCH_UP, PlayerControl.PREVIOUS_CHANNEL,
                        PlayerControl.OTHER_CHANNELS, PlayerControl.MATCH, PlayerControl.SOURCES, PlayerControl.CHANNEL_LIST,
                        -> Unit
                    }
                }
            }
        }
        }
        }
        if (toolsUp) ToolLabelLine(toolFocus, compact = true)
        }
    }
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (header != null && toolsUp) {
            // glcn: Ficha banner — every control on the top line (all fifteen take the panel's whole width: beside the
            // channel they ran off its edge), then the programme with the channel named over it.
            tools(Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).height(1.dp).background(hudInk.copy(alpha = 0.10f)))
            belowHeader?.invoke()
        } else if (header != null) {
            // glcn: live TV — the channel on the left, the programme on air with its picture on the right,
            // then every control in one full-width row below.
            if (side != null) {
                // glcn: the programme beside never takes more than about half the row, so the channel's name and its
                // facts keep room (on a narrower screen or a bigger zoom they were cut through).
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sideMax = minOf(540.dp, maxWidth * 0.46f)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { header() }
                        Spacer(Modifier.width(18.dp))
                        Box(Modifier.widthIn(max = sideMax)) { side() }
                    }
                }
            } else header()
            // glcn: on a channel with an archive the timeline below takes this line's place — no extra row.
            if (onScrubLive == null || side == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = if (side != null) 2.dp else 10.dp).height(1.dp).background(hudInk.copy(alpha = 0.10f)))
            } else Spacer(Modifier.height(8.dp))
        }
        // Band A — the instrument. The times are the bar's own end caps now; the separate time row is
        // gone, and on live the right cap is the state badge instead of a clock.
        when {
            // Catch-up live channel → a scrubbable live timeline (last LIVE_WINDOW up to the live edge).
            onScrubLive != null -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        // The live edge is now; the offset says how far behind it the picture is.
                        LiveTimelineBar(
                            offsetSec = timeshiftOffsetSec ?: 0,
                            programmes = liveProgrammes,
                            liveEdgeMs = System.currentTimeMillis(),
                            onScrub = onScrubLive,
                            focusRequester = seekFocus,
                            gaps = liveGaps,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    LiveStateBadge(timeshiftOffsetSec)
                }
                Spacer(Modifier.height(10.dp))
            }
            !isLive && duration > 0 -> {
                VodTimeline(player, position, duration, seekFocus)
                Spacer(Modifier.height(10.dp))
            }
            // A live channel with no archive has no timeline to scrub, but it still has a state to
            // report — and the badge no longer lives in the top bar.
            // glcn: with the programme card beside the channel, the state badge sits in the tools row instead.
            isLive && side == null -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LiveStateBadge(timeshiftOffsetSec) }
                Spacer(Modifier.height(10.dp))
            }
        }
        // Band B — the tools. Each cluster hugs its own screen edge and the gap between them is the
        // slack a focused button expands into, so growth is always toward the centre: the left cluster
        // pushes only the buttons to its right, the right cluster only those to its left. Walk either
        // cluster outward-in and nothing you have already passed ever moves.
        // glcn: the tool's name in one fixed place, one focus box that slides between the tools, and the row
        // wrapping round — ◀ from the first tool reaches Info at the far end, ▶ from the last returns.
        if (!toolsUp) tools(Modifier)
    }
    Dock(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = if (side != null) 6.dp else 20.dp), bare = side != null) { body() }
}

private fun volumeIcon(volume: Int): OwnTVIcon = when {
    volume == 0 -> OwnTVIcon.VOLUME_MUTE
    volume < 50 -> OwnTVIcon.VOLUME_LOW
    else -> OwnTVIcon.VOLUME_HIGH
}

/** A film's seek bar and its two time caps — the only part of the dock that needs the position, so the
 *  only part that recomposes as it ticks. */
@Composable
private fun VodTimeline(player: PlaybackEngine, position: () -> Long, duration: Long, seekFocus: FocusRequester?) {
    val seekStep by player.seekStepMs.collectAsStateWithLifecycle() // Settings -> Seek step
    val buffered by player.bufferedMs.collectAsStateWithLifecycle()
    val pos = position()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimeCap(formatTime(pos), Alignment.Start)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            SeekBar(positionMs = pos, durationMs = duration, bufferedMs = buffered, stepMs = seekStep, onSeek = { player.seekBy(it) }, focusRequester = seekFocus)
        }
        Spacer(Modifier.width(12.dp))
        TimeCap(stringResource(R.string.player_time_remaining, formatTime((duration - pos).coerceAtLeast(0))), Alignment.End)
    }
}

/** Next-episode countdown card: "Next episode in Ns" + title, with Play now / Cancel. Play now advances
 *  immediately; Cancel suppresses the automatic advance for the current item. [seconds] is read inside,
 *  so the countdown redraws this card rather than the whole HUD. */
@Composable
internal fun NextEpisodeCard(
    seconds: () -> Int,
    title: String,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(hudShade(0.82f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.player_next_episode, seconds()),
            style = tv.own.owntv.ui.theme.stageText(25, 600),
            color = hudAccent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = tv.own.owntv.ui.theme.stageText(25, 500),
            color = hudInk,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tv.own.owntv.ui.stage.StageButton(
                stringResource(R.string.player_play_now),
                onClick = onPlayNow,
                modifier = Modifier.focusRequester(playFocus),
                icon = OwnTVIcon.PLAY,
                tinted = true,
            )
            tv.own.owntv.ui.stage.StageButton(
                stringResource(R.string.common_cancel),
                onClick = onCancel,
                icon = OwnTVIcon.CLOSE,
            )
        }
    }
}

