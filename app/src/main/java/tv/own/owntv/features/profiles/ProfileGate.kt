package tv.own.owntv.features.profiles

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
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

    // glcn: entering a profile, the screen opens up towards you and fades as Home comes in, instead of a cut.
    val leave = remember { androidx.compose.animation.core.Animatable(0f) }
    val leaveAnimated = tv.own.owntv.glcn.DeviceClass.richMotion && tv.own.owntv.ui.theme.animationsOn
    val leaveScope = androidx.compose.runtime.rememberCoroutineScope()
    fun enter(p: ProfileEntity) {
        if (!leaveAnimated) { vm.switchTo(p) { onEnter(p.id) }; return }
        leaveScope.launch {
            leave.animateTo(1f, androidx.compose.animation.core.tween(GATE_LEAVE_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
            vm.switchTo(p) { onEnter(p.id) }
        }
    }

    fun choose(p: ProfileEntity) {
        if (p.pinHash != null) {
            pinError = false
            pinFor = p
        } else {
            enter(p)
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
        modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF05080A))
            .graphicsLayer {
                val l = leave.value
                scaleX = 1f + GATE_LEAVE_GROW * l; scaleY = 1f + GATE_LEAVE_GROW * l
                alpha = 1f - l
            },
        contentAlignment = Alignment.Center,
    ) {
        // glcn: Settings › Glass & background › Profile screen background — a picture of the person's choosing in
        // place of the aurora and the fanart, darkened a little less so it shows.
        val gateCtx = androidx.compose.ui.platform.LocalContext.current
        tv.own.owntv.glcn.GateWall.ensure(gateCtx)
        val gateWall = tv.own.owntv.glcn.GateWall.model(gateCtx, tv.own.owntv.ui.theme.LocalBackground.current)
        if (gateWall != null) tv.own.owntv.glcn.WallLayer(gateWall, Modifier.fillMaxSize())
        else GateAurora(tint, Modifier.fillMaxSize())
        if (gateWall == null) androidx.compose.animation.Crossfade(
            targetState = focused?.id?.let { arts[it]?.backdrop },
            animationSpec = androidx.compose.animation.core.tween(GATE_FADE_MS),
            label = "gateArt",
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            if (url != null) coil3.compose.AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.38f },
            )
        }
        // Dark at the foot and the edges, so the names and the aurora read over any picture.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(
            0f to androidx.compose.ui.graphics.Color.Transparent,
            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = if (gateWall != null) 0.5f else 0.75f),
            radius = 1600f,
        )))
        // glcn: darker through the middle and the foot, where the greeting and the names sit — white text over a light,
        // greyish picture was hard to read from the sofa.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = if (gateWall != null) 0.12f else 0.3f),
            0.4f to androidx.compose.ui.graphics.Color.Black.copy(alpha = if (gateWall != null) 0.35f else 0.55f),
            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = if (gateWall != null) 0.7f else 0.85f),
        )))
        GateClock(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 44.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greet = when (hour) { in 6..12 -> R.string.glcn_greet_morning; in 13..20 -> R.string.glcn_greet_afternoon; else -> R.string.glcn_greet_evening }
            Text(
                stringResource(greet), style = tv.own.owntv.ui.theme.stageText(46, 800),
                color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.staggerIn(0),
            )
            Text(
                stringResource(R.string.profiles_gate_title), style = tv.own.owntv.ui.theme.stageText(24, 600),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f), modifier = Modifier.staggerIn(60).padding(top = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top,
                // glcn: room inside the row for the focused tile's growth (the list clips to its own bounds).
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 72.dp, vertical = 30.dp),
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
            }
            // glcn: adding a profile is a small quiet button under the profiles, not a tile of its own among them.
            tv.own.owntv.ui.stage.StageButton(
                stringResource(R.string.profiles_add), onClick = onAddProfile,
                icon = OwnTVIcon.ADD, height = 44.dp, textSize = 17,
                modifier = Modifier.staggerIn(120 + profiles.size * GATE_STAGGER_MS),
            )
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    pinFor = null
                    enter(p)
                } else pinError = true
            },
            onDismiss = { pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, continueTitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.1f else 1f, androidx.compose.animation.core.spring(stiffness = 420f, dampingRatio = 0.7f), label = "tileGrow")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(170.dp)) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = modifier.size(108.dp)
                // glcn: no glow drawn round the tile any more — whatever holds the row clipped it to a rectangle with
                // straight edges; the screen's aurora already takes the focused profile's colour.
                .graphicsLayer { scaleX = grow; scaleY = grow },
            focusDecor = false,
            contentAlignment = Alignment.Center,
        ) { f ->
            LaunchedEffect(f) { focused = f }
            val glow = tv.own.owntv.ui.components.avatarColor(profile.avatarId)
            Box(
                Modifier.size(94.dp)
                    // glcn: the focused profile glows softly in its own colour and wears a white rim.
                    .then(if (f) Modifier.drawBehind { drawGlow(glow, 28.dp.toPx()) } else Modifier)
                    .then(
                        if (f) Modifier.border(3.dp, androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).padding(5.dp) else Modifier.padding(5.dp),
                    ),
                contentAlignment = Alignment.TopEnd,
            ) {
                OwnTVAvatar(avatarId = profile.avatarId, imagePath = profile.avatarPath.orEmpty(), modifier = Modifier.fillMaxSize())
                // glcn: a TV profile wears a little TV at its foot.
                if (tv.own.owntv.glcn.TvProfiles.isTv(profile.id)) Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.stageAccent.accent),
                    contentAlignment = Alignment.Center,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = tv.own.owntv.ui.theme.stageAccent.onAccent, modifier = Modifier.size(14.dp)) }
                if (profile.pinHash != null) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.LOCK, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.name, style = tv.own.owntv.ui.theme.stageText(22, if (focused) 800 else 600),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (focused) 1f else 0.75f), textAlign = TextAlign.Center, maxLines = 1,
        )
        if (profile.isKids) {
            Text(stringResource(R.string.profiles_kids_badge), style = tv.own.owntv.ui.theme.stageText(16, 500), color = tv.own.owntv.ui.theme.stageAccent.accent)
        }
        val line = if (focused && continueTitle != null) stringResource(R.string.glcn_gate_continue, continueTitle) else ""
        Text(
            line, style = tv.own.owntv.ui.theme.stageText(15, 500),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).height(40.dp),
        )
    }
}

/** glcn: a soft glow in [color] around a rounded square, as rings fading outwards. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(color: androidx.compose.ui.graphics.Color, radius: Float) {
    val steps = 3
    for (k in steps downTo 1) {
        val grow = k * 2.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.14f),
            topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + grow),
        )
    }
}

/** glcn: the gate's clock — the time and the date, no label, smaller than the greeting. */
@Composable
private fun GateClock(modifier: Modifier = Modifier) {
    val formatTime = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
    val formatDate = tv.own.owntv.ui.format.rememberBestDateFormatter("EEEdMMM")
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(10_000); now = System.currentTimeMillis() } }
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(formatTime(now), style = tv.own.owntv.ui.theme.stageText(30, 700), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
        Text(formatDate(now), style = tv.own.owntv.ui.theme.stageText(17, 500), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f))
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

/** glcn: entering a profile — how long the gate takes to open up and fade, and how much it grows. */
private const val GATE_LEAVE_MS = 380
private const val GATE_LEAVE_GROW = 0.08f
