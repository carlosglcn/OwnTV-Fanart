package tv.own.owntv.glcn

/**
 * glcn: Movies / Series categories in sections — platforms (Netflix, Apple TV…), genres, then everything else
 * (studios, collections) — instead of one mixed "Providers" list. Only the order shown changes, never the
 * list's own order or its indices.
 */
object VodSections {
    const val PLATFORMS = 0
    const val GENRES = 1
    const val MORE = 2

    fun of(name: String): Int = when {
        CategoryIcons.isPlatform(name) -> PLATFORMS
        isGenre(name) -> GENRES
        else -> MORE
    }

    private fun isGenre(name: String): Boolean {
        val n = " " + java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        // A short name that is, or carries, a genre word ("Terror", "Cine de terror", "Comedy movies").
        if (n.trim().split(' ').size > 4) return false
        return GENRE_WORDS.any { n.contains(" $it ") }
    }

    private val GENRE_WORDS = listOf(
        "accion", "action", "aventura", "aventuras", "adventure", "animacion", "animation", "anime", "comedia", "comedy",
        "crimen", "crime", "documental", "documentales", "documentary", "drama", "dramas", "familia", "familiar", "family",
        "fantasia", "fantasy", "historia", "historico", "history", "terror", "horror", "musica", "music", "musical",
        "misterio", "mystery", "romance", "romantica", "romanticas", "ciencia ficcion", "sci fi", "science fiction",
        "suspense", "thriller", "belica", "belico", "guerra", "war", "western", "infantil", "infantiles", "kids",
        "biografia", "biografico", "biography", "policiaca", "policiaco", "superheroes", "navidad", "christmas", "erotico",
    )
}
