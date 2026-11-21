package tv.own.owntv.glcn

import tv.own.owntv.R

/**
 * glcn: the Shelves layout of Films and Series — Cinematic, with rows above the posters of All: the best rated, the
 * recent releases and what is popular now (the titles of Home's Trending that the lists carry). A row with too few
 * titles is left out; the posters of All follow below as always.
 */
enum class ShelfKind(val title: Int) {
    RATED(R.string.glcn_shelf_rated),
    RECENT(R.string.glcn_shelf_recent),
    POPULAR(R.string.glcn_shelf_popular),
}

data class Shelf<T>(val kind: ShelfKind, val items: List<T>)

object VodShelves {
    /** Asked of the database: more than shown, the same title often comes several times (4K, HD, each list). */
    const val FETCH = 90
    const val RATED_FLOOR = 7.0
    private const val SHOWN = 24
    private const val FEWEST = 4
    private const val EARLY_MONTHS = 2

    /** Recent releases: this year's — and last year's too while the year is young. */
    fun fromYear(): Int {
        val now = java.util.Calendar.getInstance()
        val year = now.get(java.util.Calendar.YEAR)
        return if (now.get(java.util.Calendar.MONTH) < EARLY_MONTHS) year - 1 else year
    }

    /** One of each title, the first as ordered; null when the row would be too short to be worth its place. */
    fun <T> row(kind: ShelfKind, rows: List<T>, same: (T) -> Any): Shelf<T>? =
        rows.distinctBy(same).take(SHOWN).takeIf { it.size >= FEWEST }?.let { Shelf(kind, it) }

    /** A shelf shows wide pictures when most of its titles have one. */
    fun <T> upright(items: List<T>, wide: (T) -> String?): Boolean =
        items.count { !wide(it).isNullOrBlank() } * WIDE_SHARE_DEN < items.size * WIDE_SHARE_NUM

    private const val WIDE_SHARE_NUM = 3
    private const val WIDE_SHARE_DEN = 5
}
