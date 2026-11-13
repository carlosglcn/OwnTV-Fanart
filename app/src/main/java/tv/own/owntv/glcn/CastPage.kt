package tv.own.owntv.glcn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.metadata.CastLibrary
import tv.own.owntv.core.metadata.CastMember
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageDisplay
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.stageTone

/**
 * glcn: a film's cast as a page of its own — a row of portraits along the top; the one with the cursor is told about
 * below (no OK needed): their Wikipedia summary with a code to scan for the whole article on the phone, and their
 * other films in your playlists (▼, then OK opens one).
 */
@Composable
fun CastPage(
    film: String,
    backdrop: String?,
    cast: List<CastMember>,
    currentId: Long,
    onOpen: (MovieEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val filmsFocus = remember { FocusRequester() }
    var at by remember { mutableIntStateOf(0) }
    val people = cast.take(CAST_MAX)
    val page = stageTone(Color(0xFF070B0E), LocalStagePalette.current.page)
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true, stageScaled = false) {
        LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
        Box(Modifier.fillMaxSize().background(page).trapAllFocusExit()) {
            tv.own.owntv.ui.components.ArtTint(backdrop, Modifier.matchParentSize(), strength = 0.6f)
            Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 48.mpx, bottom = 40.mpx)) {
                Text(
                    stringResource(R.string.content_media_cast).uppercase(), style = stageText(18, 800),
                    color = stageAccent.ink, modifier = Modifier.padding(horizontal = SIDE),
                )
                Text(film, style = stageText(30, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = SIDE).padding(top = 4.mpx))
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 22.mpx).focusGroup(),
                    contentPadding = PaddingValues(horizontal = SIDE, vertical = 14.mpx),
                    horizontalArrangement = Arrangement.spacedBy(22.mpx),
                ) {
                    itemsIndexed(people, key = { i, p -> "$i-${p.name}" }) { i, p ->
                        StagePoster(
                            title = p.name, onClick = { runCatching { filmsFocus.requestFocus() } }, width = PORTRAIT_W, height = PORTRAIT_H,
                            modifier = Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged { if (it.hasFocus || it.isFocused) at = i },
                        ) { Portrait(p) }
                    }
                }
                val who = people.getOrNull(at)
                if (who != null) androidx.compose.runtime.key(who.name) { PersonPanel(who, currentId, filmsFocus, onOpen) }
            }
        }
    }
}

@Composable
private fun Portrait(p: CastMember) {
    val photo = MetadataImages.profile(p.profilePath, PHOTO_SIZE)
    Box(Modifier.fillMaxSize().background(StageColors.Ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        if (photo == null) {
            Text(initials(p.name), style = stageDisplay(56), color = StageColors.Muted)
        } else {
            AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The person under the cursor: settles a moment before reading Wikipedia, so running along the row reads nothing.
 * The films come in two steps: at once those the app already knows (cached TMDB casts), then, once Wikidata has
 * answered, every film of the person's filmography that is in your playlists or servers.
 */
@Composable
private fun PersonPanel(p: CastMember, currentId: Long, filmsFocus: FocusRequester, onOpen: (MovieEntity) -> Unit) {
    val wiki by produceState<Pair<Boolean, Wiki.Summary?>>(false to null, p.name) {
        value = false to null
        delay(SETTLE_MS)
        value = true to Wiki.person(p.name)
    }
    val qid = wiki.second?.qid
    val facts by produceState<Wiki.Facts?>(null, qid) { value = null; if (qid != null) value = Wiki.facts(qid) }
    val films by produceState<List<MovieEntity>?>(null, p.name, facts) {
        if (facts == null) delay(SETTLE_MS)
        val credits = facts?.films.orEmpty().map { CastLibrary.Credit(it.tmdb, it.titles, it.year) }
        value = CastLibrary.filmsWith(p.name, except = currentId, credits = credits)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = SIDE).padding(top = 6.mpx), horizontalArrangement = Arrangement.spacedBy(48.mpx)) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = stageDisplay(50), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(wiki, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "wiki") { (done, s) ->
                Column(Modifier.fillMaxWidth().height(WIKI_H)) {
                    when {
                        !done -> repeat(4) { k ->
                            Box(Modifier.padding(top = 14.mpx).fillMaxWidth(if (k == 3) 0.55f else 1f).height(20.mpx).clip(RoundedCornerShape(6.mpx)).skeleton())
                        }
                        s == null -> Text(stringResource(R.string.glcn_castpage_wiki_none), style = stageText(20, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
                        else -> {
                            s.description?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, style = stageText(20, 600), color = stageAccent.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.mpx)) }
                            factsLine(facts)?.let { Text(it, style = stageText(19, 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx)) }
                            Text(s.extract, style = stageText(20, 400), color = StageColors.Text.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.mpx))
                        }
                    }
                }
            }
        }
        // The whole article, on the phone: a TV has no good browser, a phone is in every hand.
        val s = wiki.second
        Box(Modifier.width(QR_BOX), contentAlignment = Alignment.TopCenter) {
            if (s != null) {
                val qr = remember(s.url) { CompanionLink.renderQr(s.url, QR_PX)?.asImageBitmap() }
                if (qr != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 10.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White).padding(10.mpx)) {
                        Image(qr, contentDescription = null, modifier = Modifier.size(QR_SIZE))
                    }
                    Text(stringResource(R.string.glcn_castpage_wiki_scan), style = stageText(15, 600), color = StageColors.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.mpx))
                }
            }
        }
    }
    val list = films
    Row(Modifier.padding(horizontal = SIDE).padding(top = 10.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.glcn_castpage_films), style = stageText(20, 700), color = StageColors.Text)
        if (!list.isNullOrEmpty()) Text("  ·  ${list.size}", style = stageText(20, 600), color = StageColors.Muted)
    }
    when {
        list == null -> Box(Modifier.height(FILM_ROW_H))
        list.isEmpty() -> Text(stringResource(R.string.glcn_cast_none, p.name), style = stageText(18, 500), color = StageColors.Muted,
            modifier = Modifier.height(FILM_ROW_H).padding(horizontal = SIDE).padding(top = 10.mpx))
        else -> LazyRow(
            Modifier.fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(horizontal = SIDE, vertical = 16.mpx),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
        ) {
            itemsIndexed(list, key = { _, f -> f.id }) { i, f ->
                StagePoster(
                    title = f.name, onClick = { onOpen(f) }, width = FILM_W, height = FILM_H, compact = true, line = f.year?.toString(),
                    modifier = if (i == 0) Modifier.focusRequester(filmsFocus) else Modifier,
                ) {
                    if (f.posterUrl.isNullOrBlank()) Box(Modifier.fillMaxSize().background(StageColors.Ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.MOVIES, StageColors.Muted, Modifier.size(40.mpx))
                    } else AsyncImage(f.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** "Born 18 December 1963 in Shawnee · 62 years old", or with the death date and age at death. */
@Composable
private fun factsLine(f: Wiki.Facts?): String? {
    val born = f?.born ?: return null
    val fmt = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
    val first = buildString {
        append(stringResource(R.string.glcn_castpage_born, born.format(fmt)))
        f.place?.let { append(' ').append(stringResource(R.string.glcn_castpage_born_in, it)) }
    }
    val died = f.died
    val second = if (died != null) {
        stringResource(R.string.glcn_castpage_died, died.format(fmt), java.time.Period.between(born, died).years)
    } else {
        stringResource(R.string.glcn_castpage_age, java.time.Period.between(born, java.time.LocalDate.now()).years)
    }
    return "$first  ·  $second"
}

private fun initials(name: String) = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

private val SIDE = 100.mpx
private val PORTRAIT_W = 140.mpx
private val PORTRAIT_H = 210.mpx
private val FILM_W = 130.mpx
private val FILM_H = 195.mpx
private val FILM_ROW_H = 120.mpx
private val WIKI_H = 196.mpx
private val QR_BOX = 230.mpx
private val QR_SIZE = 170.mpx
private const val QR_PX = 400
private const val PHOTO_SIZE = "w342"
private const val SETTLE_MS = 350L
private const val CAST_MAX = 20
