package tv.own.owntv.glcn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * glcn: a section is shown when it has something to show. Opening Movies, Series, Live TV or the Guide used to bring
 * the page in at once and let it fill in steps — the frame, then the cards, then the words, then the pictures — each
 * step a small jolt. Now the page stays behind a curtain until its first content is there ([SectionReady]), and a
 * moment more for the pictures already on disk; then it comes in whole, with the section's one fade. Never longer
 * than [MAX_WAIT_MS]: a slow list shows what it has, as before.
 */
object SectionCurtain {
    private val ready = MutableStateFlow(false)

    /** The sections that say when they are ready; the others come in at once. */
    private val reporting = setOf("MOVIES", "SERIES", "LIVE_TV", "EPG")

    fun waits(section: String): Boolean = section in reporting

    fun open() { ready.value = true }
    fun closed() { ready.value = false }

    /** Returns when the section on screen has its first content (or has taken too long). */
    suspend fun await() {
        withTimeoutOrNull(MAX_WAIT_MS) { ready.first { it } }
        delay(SETTLE_MS)
    }

    private const val MAX_WAIT_MS = 700L
    private const val SETTLE_MS = 90L
}

/** A section's page says here when its first content is in ([ready]); leaving it draws the curtain again. */
@Composable
fun SectionReady(ready: Boolean) {
    LaunchedEffect(ready) { if (ready) SectionCurtain.open() }
    DisposableEffect(Unit) { onDispose { SectionCurtain.closed() } }
}
