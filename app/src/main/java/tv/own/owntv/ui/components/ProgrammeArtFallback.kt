package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/**
 * glcn: the programme's own picture, or — for a film or series the guide gives none — TMDB's wide picture when it is
 * already known. Never waits: an unknown one is asked for in the background (a moment after the card appears, within
 * the hourly allowance), and the card has it the next time it is drawn.
 */
@Composable
fun programmeArtOrTmdb(p: EpgProgrammeEntity?): String? {
    val own = p?.imageUrl?.takeIf { it.isNotBlank() }
    if (p == null || own != null) return own
    val resolver: tv.own.owntv.core.metadata.ProgrammeArtResolver = org.koin.compose.koinInject()
    val known = remember(p.id, p.title) { resolver.knownBackdrop(p) }
    if (known == null) LaunchedEffect(p.id) { kotlinx.coroutines.delay(WARM_DELAY_MS); resolver.warm(p) }
    return known
}

/** Long enough that zapping past a card asks nothing. */
private const val WARM_DELAY_MS = 600L
