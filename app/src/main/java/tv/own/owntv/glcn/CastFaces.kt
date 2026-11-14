package tv.own.owntv.glcn

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.core.metadata.CastMember
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.Wiki
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: who is in it, as faces — a round photo with the name under it. [onPick] makes each one a button (OK opens the
 * cast page on that person); without it the row is only shown (the pause screen). A name with no TMDB photo (one read
 * from the guide) gets Wikipedia's, and its initials until then.
 */
@Composable
fun CastFaces(
    people: List<CastMember>,
    modifier: Modifier = Modifier,
    face: Dp = 96.mpx,
    max: Int = FACES_MAX,
    ink: Color = Color.White,
    onPick: ((Int) -> Unit)? = null,
    firstFocus: FocusRequester? = null,
) {
    Row(modifier.then(if (onPick != null) Modifier.focusGroup() else Modifier), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
        people.take(max).forEachIndexed { i, p ->
            Column(Modifier.width(face + 24.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                if (onPick != null) {
                    StageSurface(
                        onClick = { onPick(i) }, radius = face / 2, focusStyle = StageFocus.PRIMARY,
                        modifier = Modifier.size(face).then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { FacePhoto(p, face) }
                } else {
                    Box(Modifier.size(face), contentAlignment = Alignment.Center) { FacePhoto(p, face) }
                }
                Text(
                    p.name, style = stageText(15, 600), color = ink.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.mpx),
                )
            }
        }
    }
}

@Composable
private fun FacePhoto(p: CastMember, face: Dp) {
    val tmdb = MetadataImages.profile(p.profilePath, FACE_PHOTO)
    val photo by produceState(tmdb, p.name, tmdb) { if (tmdb == null) value = runCatching { Wiki.person(p.name)?.photo }.getOrNull() }
    Box(Modifier.size(face).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Text(initialsOf(p.name), style = stageText(26, 800), color = Color.White.copy(alpha = 0.7f))
        photo?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun initialsOf(name: String) = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/**
 * glcn: the names after "Reparto:" in a guide entry's description ("· Reparto: Lucy O'Connell, Tom Hanks y Ana Belén."),
 * as cast members without photos. Empty when the guide names nobody.
 */
fun guideCast(description: String?): List<CastMember> {
    val m = CAST_LINE.find(description ?: return emptyList()) ?: return emptyList()
    return m.groupValues[1].trim().trimEnd('.').split(CAST_SPLIT)
        .map { it.trim().trimEnd('.') }.filter { it.length in 3..60 && it.any(Char::isLetter) }
        .distinct().take(GUIDE_CAST_MAX).map { CastMember(it) }
}

private val CAST_LINE = Regex("""(?:Reparto|Int[ée]rpretes|Cast|Starring)\s*:\s*([^\n]+)""", RegexOption.IGNORE_CASE)
private val CAST_SPLIT = Regex("""\s*,\s*|\s+y\s+|\s+and\s+""")
private const val GUIDE_CAST_MAX = 12
private const val FACES_MAX = 6
private const val FACE_PHOTO = "w185"
