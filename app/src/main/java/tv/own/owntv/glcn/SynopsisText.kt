package tv.own.owntv.glcn

/**
 * glcn: some guides open every synopsis with the programme's genres in brackets — "(Cine,Ciencia ficción) En el año
 * 2027…" — which the line above it already says. That opening goes when all it holds are the programme's own genres;
 * any other bracket (a year, a title in its language) stays.
 */
object SynopsisText {
    private const val OPEN = '('
    private const val CLOSE = ')'
    private const val LONGEST = 80

    fun withoutGenres(text: String, categories: String?): String {
        val body = text.trimStart()
        if (body.firstOrNull() != OPEN) return text
        val end = body.indexOf(CLOSE)
        if (end < 2 || end > LONGEST) return text
        val known = categories.orEmpty().split(',', '/', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (known.isEmpty()) return text
        val inside = body.substring(1, end).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return if (inside.isNotEmpty() && inside.all { it in known }) body.substring(end + 1).trimStart() else text
    }
}
