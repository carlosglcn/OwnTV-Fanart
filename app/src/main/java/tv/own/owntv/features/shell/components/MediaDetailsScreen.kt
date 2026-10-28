package tv.own.owntv.features.shell.components

import tv.own.owntv.ui.theme.stageTone
import tv.own.owntv.ui.theme.LocalStagePalette
import androidx.compose.runtime.Immutable

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.own.owntv.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.gradientWash

/**
 * Read-only, already-merged data for the [MediaDetailsScreen] window. The caller applies the
 * provider/TMDB merge and builds image.tmdb.org URLs, so this window is source-agnostic and reused for
 * movie / series / episode.
 */
@Immutable
data class MediaDetailsUi(
    val title: String,
    val subtitle: String? = null,       // e.g. "S2 · E5 · aired 2019-04-14"
    val backdropUrl: String? = null,    // 16:9 hero
    val logoUrl: String? = null,        // title-logo artwork; null means "draw the title as text"
    val posterUrl: String? = null,      // 2:3 poster (or 16:9 still for episodes)
    val metaLine: String = "",          // "2026 · ★ 7.6 · 2h 10m"
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    val cast: List<tv.own.owntv.core.metadata.CastMember> = emptyList(),
    /** glcn: "Related" — other titles of the same category; filled in by the caller once loaded. */
    val related: List<RelatedUi> = emptyList(),
    /** glcn: the title's trailer (YouTube keys as stored) — it plays silently over the backdrop after a few seconds. */
    val trailerKey: String? = null,
)

/** glcn: one related title on the details page. */
@Immutable
data class RelatedUi(val name: String, val posterUrl: String?, val year: Int?)

/**
 * TMDB details as a Stage page in its own full-screen window: the backdrop edge to edge,
 * fading into the page, then the eyebrow (episode line), the title 64/800, the meta line, genres as
 * tags, the overview and the cast. Purely for reading: **Up/Down scroll, Back exits, nothing is
 * selectable** — the page owns focus and turns Up/Down into a scroll, so the D-pad cannot leak behind.
 */
@Composable
fun MediaDetailsScreen(details: MediaDetailsUi, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val step = 260f
    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        // Back is taken here: on the page's own focus node it would otherwise be spent as a focus exit,
        // which the trap cancels, and the window would never close. Closed on release.
        if (e.key == Key.Back) {
            if (e.type == KeyEventType.KeyUp) onExit()
            return@onKey true
        }
        if (e.type != KeyEventType.KeyDown) return@onKey false
        when (e.key) {
            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(step) }; true }
            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-step) }; true }
            else -> false
        }
    }
    val page = stageTone(Color(0xFF070B0E), LocalStagePalette.current.page)
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onExit, stageLayout = true, stageScaled = false) {
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
        Box(modifier.fillMaxSize().background(page).trapAllFocusExit()) {
            // glcn: the page takes the tone of the backdrop.
            tv.own.owntv.ui.components.ArtTint(details.backdropUrl ?: details.posterUrl, Modifier.matchParentSize(), strength = 0.7f, focusX = 0.5f, focusY = 0.45f)
            // glcn: the picture fills the screen and settles in — it opens a touch closer and eases
            // back, as if the card the user pressed had grown into the page.
            val animate = tv.own.owntv.ui.theme.animationsOn
            val settle = remember { androidx.compose.animation.core.Animatable(if (animate) 0f else 1f) }
            LaunchedEffect(Unit) { settle.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            if (!details.backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = details.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).graphicsLayer {
                        val k = 1.08f - 0.08f * settle.value
                        scaleX = k; scaleY = k; alpha = 0.3f + 0.7f * settle.value
                    },
                )
            }
            // glcn: the trailer, silent, over the picture after a few seconds on the page (not in light mode, not with
            // animations off); it goes once the page is scrolled down or the trailer ends.
            var trailerOn by remember { mutableStateOf(false) }
            val trailerAllowed = animate && !tv.own.owntv.glcn.LowRam.on && !details.trailerKey.isNullOrBlank()
            LaunchedEffect(trailerAllowed) {
                if (!trailerAllowed) return@LaunchedEffect
                kotlinx.coroutines.delay(DETAILS_TRAILER_MS)
                if (scroll.value == 0) trailerOn = true
            }
            if (scroll.value > 0 && trailerOn) trailerOn = false
            if (trailerOn) details.trailerKey?.let { key ->
                tv.own.owntv.ui.components.AmbientTrailer(key, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { trailerOn = false }
            }
                        // A light picture still has to carry white text: a general dim, then the fade into the page.
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(page.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).gradientWash(vertical = true, 0.2f to Color.Transparent, 0.8f to page))
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).gradientWash(vertical = false, 0f to page.copy(alpha = 0.85f), 0.6f to Color.Transparent))
            Column(
                Modifier.fillMaxSize().focusRequester(focus).onKeyEvent(onKey).focusable().verticalScroll(scroll)
                    .padding(start = 120.mpx, end = 120.mpx, top = 300.mpx, bottom = 80.mpx),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.mpx), verticalAlignment = Alignment.Bottom) {
                    if (!details.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = details.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(220.mpx).aspectRatio(2f / 3f).clip(RoundedCornerShape(20.mpx)),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        if (!details.subtitle.isNullOrBlank()) {
                            Text(details.subtitle, style = stageText(18, 800, 0.08.em), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // glcn: the title's own logo, when TMDB has one; the words otherwise.
                        var logoFailed by remember(details.logoUrl) { mutableStateOf(false) }
                        if (!details.logoUrl.isNullOrBlank() && !logoFailed) {
                            AsyncImage(
                                model = details.logoUrl, contentDescription = details.title, contentScale = ContentScale.Fit,
                                alignment = Alignment.BottomStart,
                                onError = { logoFailed = true },
                                modifier = Modifier.padding(top = 6.mpx).widthIn(max = 620.mpx).height(150.mpx),
                            )
                        } else {
                            Text(details.title, style = stageText(64, 800, (-1.5).mpxSp), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                        }
                        if (details.metaLine.isNotBlank()) {
                            Text(details.metaLine, style = stageText(22, 600), color = StageColors.ItemText, modifier = Modifier.padding(top = 12.mpx))
                        }
                        if (details.genres.isNotEmpty()) {
                            FlowRow(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                                details.genres.forEach { tv.own.owntv.ui.stage.StageTag(it) }
                            }
                        }
                    }
                }
                if (!details.plot.isNullOrBlank()) {
                    DetailsLabel(stringResource(R.string.content_media_overview))
                    Text(details.plot, style = stageText(21, 400).copy(lineHeight = (21 * 1.55f).mpxSp), color = StageColors.ItemText, modifier = Modifier.widthIn(max = 1300.mpx))
                }
                if (details.cast.isNotEmpty()) {
                    DetailsLabel(stringResource(R.string.content_media_cast))
                    CastGrid(details.cast)
                }
                if (details.related.isNotEmpty()) {
                    DetailsLabel(stringResource(R.string.glcn_related_title))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalArrangement = Arrangement.spacedBy(24.mpx)) {
                        details.related.forEach { r ->
                            Column(Modifier.width(150.mpx)) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.mpx))
                                        .background(StageColors.Ink.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!r.posterUrl.isNullOrBlank()) {
                                        AsyncImage(model = r.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Text(
                                            r.name.take(1).uppercase(), style = stageText(34, 800), color = StageColors.Muted,
                                        )
                                    }
                                }
                                Text(
                                    r.name, style = stageText(15, 600), color = StageColors.ItemText,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx),
                                )
                                r.year?.let { Text(it.toString(), style = stageText(13, 500), color = StageColors.Dim, maxLines = 1) }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.content_media_press_back), style = stageText(16, 600), color = StageColors.Dim, modifier = Modifier.padding(top = 40.mpx))
            }
        }
    }
}

/** A section label on the details page ("OVERVIEW", "CAST"): 15/800 caps, dim. */
@Composable
private fun DetailsLabel(text: String) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Text(text.uppercase(locale), style = stageText(15, 800, 0.13.em), color = StageColors.Dim, modifier = Modifier.padding(top = 44.mpx, bottom = 14.mpx))
}

/**
 * Cast as photo + name cards that wrap onto as many lines as needed.
 *
 * Non-focusable by design: the page is scroll-only with no inner focus targets (it owns focus and turns
 * Up/Down into a scroll), so a focusable row would break its D-pad model. Wrapping instead of scrolling
 * sideways keeps every credited actor reachable with the same Up/Down that scrolls the rest.
 *
 * The photos come straight from TMDB's image CDN, which needs no API key and does not touch the metadata
 * service, so showing them costs nothing against anyone's allowance.
 */
@Composable
private fun CastGrid(cast: List<tv.own.owntv.core.metadata.CastMember>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.mpx), verticalArrangement = Arrangement.spacedBy(24.mpx)) {
        cast.forEach { member ->
            Column(Modifier.width(132.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                val photo = tv.own.owntv.core.metadata.MetadataImages.profile(member.profilePath)
                Box(
                    Modifier.size(112.mpx).clip(RoundedCornerShape(56.mpx)).background(StageColors.Ink.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        // Plenty of credited actors have no TMDB photo; initials read better than a gap.
                        Text(
                            member.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                            style = stageText(26, 800), color = StageColors.Muted,
                        )
                    }
                }
                Text(
                    member.name, style = stageText(16, 600), color = StageColors.ItemText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.mpx),
                )
            }
        }
    }
}


/** glcn: how long the details page rests on its picture before the trailer starts. */
private const val DETAILS_TRAILER_MS = 3_000L
