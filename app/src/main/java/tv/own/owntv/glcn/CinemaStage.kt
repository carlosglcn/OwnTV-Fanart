package tv.own.owntv.glcn

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: the film player's "cinema" mode — what the film or episode is (for the banner and the Info tab) and
 * whether the side panel is open. The shell reads [panelOpen] to slide the picture to the left while the
 * panel is up; the player's HUD sets it.
 */
object CinemaStage {
    var panelOpen by mutableStateOf(false)
}

/** What the cinema banner and its Info tab show about the film or episode playing. */
@Immutable
data class CinemaInfo(
    val title: String,
    val logoUrl: String? = null,
    /** The film's or show's wide picture (fanart), behind the picture while the side panel is open. */
    val backdropUrl: String? = null,
    val year: Int? = null,
    val durationMin: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val overview: String? = null,
    val cast: List<String> = emptyList(),
    /** The show's name, for an episode. */
    val show: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** The catalogue's own quality marks ("Dolby Vision, Dolby Atmos"), used until the stream says more. */
    val capabilities: String? = null,
    val qualityRank: Int = 0,
)

/** One episode of the season playing, for the Episodes tab. */
@Immutable
data class CinemaEpisode(
    val season: Int,
    val number: Int,
    val title: String,
    val overview: String?,
    val durationMin: Int?,
)
