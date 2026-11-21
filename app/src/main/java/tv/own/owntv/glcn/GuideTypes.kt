package tv.own.owntv.glcn

import tv.own.owntv.ui.components.ChannelGenre

/**
 * glcn: the Guide's "Type" filter — only the channels showing a film now, or sport, a series, a documentary, the news
 * or something for children. A programme's kind comes from what its guide says of it (its categories), helped by its
 * year, length and episode mark where the categories say nothing or only a genre ("Drama").
 */
object GuideTypes {
    val all = listOf(ChannelGenre.MOVIES, ChannelGenre.SPORT, ChannelGenre.SERIES, ChannelGenre.DOCUMENTARY, ChannelGenre.NEWS, ChannelGenre.KIDS)
    private val genres = setOf(ChannelGenre.ACTION, ChannelGenre.COMEDY, ChannelGenre.DRAMA, ChannelGenre.HORROR, ChannelGenre.SCIFI, ChannelGenre.ROMANCE)
    private const val FILM_MINUTES = 70

    /** How far back a programme still on air may have begun. */
    const val BACK_MS = 8L * 60 * 60 * 1000

    fun of(categories: String?, year: Int?, lengthMin: Int?, episode: String?, startMs: Long, stopMs: Long): ChannelGenre? {
        val told = ChannelGenre.fromCategory(categories)
        val serial = !episode.isNullOrBlank()
        val long = (lengthMin ?: ((stopMs - startMs) / 60_000L).toInt()) >= FILM_MINUTES
        return when {
            told in all -> told
            told == ChannelGenre.ANIME -> ChannelGenre.KIDS
            told in genres -> if (serial) ChannelGenre.SERIES else ChannelGenre.MOVIES
            serial -> ChannelGenre.SERIES
            told == ChannelGenre.OTHER && year != null && long -> ChannelGenre.MOVIES
            else -> null
        }
    }
}
