package tv.own.owntv.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import tv.own.owntv.glcn.staggerIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The "Who's watching?" launch gate. Shown when more than one profile exists. Picking a
 * profile makes it active (and prompts for a PIN if it's locked); [onEnter] records the selected
 * profile id and proceeds into the shell.
 */
@Composable
fun ProfileGate(onEnter: (Long) -> Unit, onAddProfile: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(profiles.isNotEmpty()) {
        if (profiles.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    fun choose(p: ProfileEntity) {
        if (p.pinHash != null) {
            pinError = false
            pinFor = p
        } else {
            vm.switchTo(p) { onEnter(p.id) }
        }
    }

    // glcn: the screen follows the profile in focus — its colour in a slow aurora, and the fanart of what it
    // watched last (with "Continue: …" under its name).
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val arts = rememberGateArts(profiles)
    val focused = profiles.firstOrNull { it.id == focusedId } ?: profiles.firstOrNull()
    val tint by androidx.compose.animation.animateColorAsState(
        tv.own.owntv.ui.components.avatarColor(focused?.avatarId ?: -1),
        androidx.compose.animation.core.tween(GATE_FADE_MS), label = "gateTint",
    )
    Box(
        modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05080A)),
        contentAlignment = Alignment.Center,
    ) {
        GateAurora(tint, Modifier.fillMaxSize())
        androidx.compose.animation.Crossfade(
            targetState = focused?.id?.let { arts[it]?.backdrop },
            animationSpec = androidx.compose.animation.core.tween(GATE_FADE_MS),
            label = "gateArt",
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            if (url != null) coil3.compose.AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
            )
        }
        // Dark at the foot and the edges, so the names and the aurora read over any picture.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(
            0f to androidx.compose.ui.graphics.Color.Transparent,
            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
            radius = 1600f,
        )))
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0.45f to androidx.compose.ui.graphics.Color.Transparent,
            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
        )))
        tv.own.owntv.player.PlayerClock(watchingMs = null, modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 44.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greet = when (hour) { in 6..12 -> R.string.glcn_greet_morning; in 13..20 -> R.string.glcn_greet_afternoon; else -> R.string.glcn_greet_evening }
            Text(
                stringResource(greet), style = tv.own.owntv.ui.theme.stageText(58, 800),
                color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.staggerIn(0),
            )
            Text(
                stringResource(R.string.profiles_gate_title), style = tv.own.owntv.ui.theme.stageText(26, 600),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), modifier = Modifier.staggerIn(60).padding(top = 6.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                // glcn: room inside the row for the focused tile's glow — the list clips to its own bounds, and
                // the glow used to stop there in straight lines above and beside the tile.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 72.dp, vertical = 44.dp),
                modifier = Modifier.focusGroup(),
            ) {
                items(profiles, key = { it.id }) { p ->
                    val i = profiles.indexOf(p)
                    Box(Modifier.staggerIn(120 + i * GATE_STAGGER_MS)) {
                        ProfileTile(
                            profile = p,
                            continueTitle = arts[p.id]?.title,
                            modifier = (if (p.id == profiles.first().id) Modifier.focusRequester(firstFocus) else Modifier)
                                .onFocusChanged { if (it.hasFocus || it.isFocused) focusedId = p.id },
                            onClick = { choose(p) },
                        )
                    }
                }
                item { Box(Modifier.staggerIn(120 + profiles.size * GATE_STAGGER_MS)) { AddTile(onClick = onAddProfile) } }
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    vm.switchTo(p) { onEnter(p.id) }
                } else pinError = true
            },
            onDismiss = { pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, continueTitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.12f else 1f, androidx.compose.animation.core.spring(stiffness = 420f, dampingRatio = 0.7f), label = "tileGrow")
    val glow = tv.own.owntv.ui.components.avatarColor(profile.avatarId)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(200.dp)) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 30.dp,
            modifier = modifier.size(140.dp)
                .graphicsLayer { scaleX = grow; scaleY = grow }
                .drawBehind {
                    if (focused) drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to glow.copy(alpha = 0.55f), 1f to androidx.compose.ui.graphics.Color.Transparent,
                            center = center, radius = size.minDimension * 0.8f,
                        ),
                        radius = size.minDimension * 0.8f,
                    )
                },
            focusDecor = false,
            contentAlignment = Alignment.Center,
        ) { f ->
            LaunchedEffect(f) { focused = f }
            Box(
                Modifier.size(120.dp).then(
                    if (f) Modifier.border(3.dp, androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(36.dp)).padding(6.dp) else Modifier.padding(6.dp),
                ),
                contentAlignment = Alignment.TopEnd,
            ) {
                OwnTVAvatar(avatarId = profile.avatarId, imagePath = profile.avatarPath.orEmpty(), modifier = Modifier.fillMaxSize())
                // glcn: a TV profile wears a little TV at its foot.
                if (tv.own.owntv.glcn.TvProfiles.isTv(profile.id)) Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.stageAccent.accent),
                    contentAlignment = Alignment.Center,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = tv.own.owntv.ui.theme.stageAccent.onAccent, modifier = Modifier.size(20.dp)) }
                if (profile.pinHash != null) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.LOCK, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            profile.name, style = tv.own.owntv.ui.theme.stageText(30, if (focused) 800 else 600),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (focused) 1f else 0.75f), textAlign = TextAlign.Center, maxLines = 1,
        )
        if (profile.isKids) {
            Text(stringResource(R.string.profiles_kids_badge), style = tv.own.owntv.ui.theme.stageText(20, 500), color = tv.own.owntv.ui.theme.stageAccent.accent)
        }
        val line = if (focused && continueTitle != null) stringResource(R.string.glcn_gate_continue, continueTitle) else ""
        Text(
            line, style = tv.own.owntv.ui.theme.stageText(18, 500),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).height(48.dp),
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = Modifier.size(132.dp),
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(OwnTVIcon.ADD, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.profiles_add), style = tv.own.owntv.ui.theme.stageText(30, 600), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
    }
}

/** glcn: what a profile watched last — for the gate's background and its "Continue: …" line. */
private data class GateArt(val title: String, val backdrop: String?)

/** glcn: each profile's last film or series, read once when the gate opens (a handful of profiles). */
@Composable
private fun rememberGateArts(profiles: List<ProfileEntity>): Map<Long, GateArt> {
    val history = org.koin.compose.koinInject<tv.own.owntv.core.database.dao.HistoryDao>()
    val movies = org.koin.compose.koinInject<tv.own.owntv.core.database.dao.MovieDao>()
    val series = org.koin.compose.koinInject<tv.own.owntv.core.database.dao.SeriesDao>()
    val meta = org.koin.compose.koinInject<tv.own.owntv.core.metadata.MetadataRepository>()
    val ids = profiles.map { it.id }
    val arts by androidx.compose.runtime.produceState(emptyMap<Long, GateArt>(), ids) {
        val out = HashMap<Long, GateArt>()
        for (pid in ids) {
            runCatching {
                val types = listOf(
                    tv.own.owntv.core.model.MediaType.MOVIE,
                    tv.own.owntv.core.model.MediaType.EPISODE,
                    tv.own.owntv.core.model.MediaType.SERIES,
                )
                val last = types.mapNotNull { t -> history.observeMostRecentOfType(pid, t).first() }.maxByOrNull { it.watchedAt } ?: return@runCatching
                val art = when (last.mediaType) {
                    tv.own.owntv.core.model.MediaType.MOVIE -> movies.getById(last.itemId)?.let { m ->
                        val md = runCatching { meta.resolveMovie(m) }.getOrNull()
                        GateArt(m.name, tv.own.owntv.core.metadata.MetadataImages.backdrop(md?.backdropPath, GATE_BACKDROP_SIZE) ?: m.backdropUrl)
                    }
                    else -> {
                        val sid = if (last.mediaType == tv.own.owntv.core.model.MediaType.EPISODE) series.getEpisodeById(last.itemId)?.seriesId else last.itemId
                        sid?.let { series.getSeriesById(it) }?.let { sh ->
                            val md = runCatching { meta.resolveSeries(sh) }.getOrNull()
                            GateArt(sh.name, tv.own.owntv.core.metadata.MetadataImages.backdrop(md?.backdropPath, GATE_BACKDROP_SIZE) ?: sh.backdropUrl)
                        }
                    }
                }
                if (art != null) { out[pid] = art; value = HashMap(out) }
            }
        }
    }
    return arts
}

/**
 * glcn: the gate's aurora — three soft lights in the focused profile's colour drifting slowly over the dark.
 * Still on modest boxes (no endless animation there).
 */
@Composable
private fun GateAurora(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val rich = tv.own.owntv.glcn.DeviceClass.richMotion
    val t = if (rich) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
        inf.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(AURORA_CYCLE_MS, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "auroraT",
        ).value
    } else 0.2f
    androidx.compose.foundation.Canvas(modifier) {
        val a = (t * 2 * Math.PI).toFloat()
        fun light(cx: Float, cy: Float, r: Float, alpha: Float) = drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                0f to tint.copy(alpha = alpha), 1f to androidx.compose.ui.graphics.Color.Transparent,
                center = androidx.compose.ui.geometry.Offset(cx, cy), radius = r,
            ),
            radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        val w = size.width
        val h = size.height
        light(w * (0.25f + 0.08f * kotlin.math.cos(a)), h * (0.30f + 0.06f * kotlin.math.sin(a)), w * 0.45f, 0.38f)
        light(w * (0.78f + 0.06f * kotlin.math.sin(a * 1.3f)), h * (0.62f + 0.08f * kotlin.math.cos(a)), w * 0.40f, 0.30f)
        light(w * (0.52f + 0.10f * kotlin.math.sin(a * 0.7f)), h * (0.95f + 0.04f * kotlin.math.cos(a * 1.6f)), w * 0.50f, 0.22f)
    }
}

private const val GATE_FADE_MS = 600
private const val GATE_STAGGER_MS = 70
private const val AURORA_CYCLE_MS = 24_000
private const val GATE_BACKDROP_SIZE = "w1280"
