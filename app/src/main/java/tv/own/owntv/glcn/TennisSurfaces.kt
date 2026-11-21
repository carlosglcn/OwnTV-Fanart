package tv.own.owntv.glcn

import androidx.compose.ui.graphics.Color
import tv.own.owntv.R

/**
 * glcn: what a tennis tournament is played on, told from its name — the feed does not say. The tour's calendar
 * changes little from year to year, so a list of names covers it; a tournament not on it simply has no surface
 * shown. Each surface has its colour, which a match card of that tournament takes.
 */
enum class TennisSurface(val label: Int, val tone: Color, private val names: String) {
    // Indoors first: "Paris" alone would not tell the Masters from Roland Garros.
    INDOOR(R.string.glcn_tennis_indoor, Color(0xFF4E46A8), TENNIS_INDOOR),
    GRASS(R.string.glcn_tennis_grass, Color(0xFF2F7D3E), TENNIS_GRASS),
    CLAY(R.string.glcn_tennis_clay, Color(0xFFB8521F), TENNIS_CLAY),
    HARD(R.string.glcn_tennis_hard, Color(0xFF1F5FA8), TENNIS_HARD),
    ;

    private val words by lazy { names.split(TENNIS_SPLIT) }

    companion object {
        fun of(tournament: String?): TennisSurface? {
            if (tournament.isNullOrBlank()) return null
            val name = java.text.Normalizer.normalize(tournament, java.text.Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase()
            return entries.firstOrNull { s -> s.words.any { it in name } }
        }
    }
}

private const val TENNIS_SPLIT = "|"
private const val TENNIS_INDOOR = "atp finals|wta finals|paris masters|rolex paris|vienna|erste bank|basel|swiss indoors|stockholm|antwerp|metz|rotterdam|marseille|montpellier|dallas|sofia|astana|almaty|next gen|davis cup finals|billie jean king cup finals|linz|ostrava|st. petersburg|belgrade|brussels|athens"
private const val TENNIS_GRASS = "wimbledon|queen|halle|eastbourne|mallorca|hertogenbosch|libema|newport|nottingham|birmingham|bad homburg|berlin|stuttgart|boss open"
private const val TENNIS_CLAY = "roland garros|french open|monte-carlo|monte carlo|madrid|rome|internazionali|italian open|barcelona|godo|hamburg|munich|bmw open|estoril|geneva|lyon|bastad|gstaad|umag|kitzbuhel|buenos aires|rio open|santiago|cordoba|marrakech|bucharest|houston|charleston|bogota|rabat|strasbourg|palermo|prague|budapest|lausanne|iasi|parma"
private const val TENNIS_HARD = "australian open|us open|indian wells|bnp paribas|miami|canada|canadian|national bank|montreal|toronto|cincinnati|western & southern|shanghai|beijing|china open|tokyo|japan open|dubai|doha|qatar|acapulco|washington|citi open|winston|brisbane|adelaide|auckland|hong kong|delray|los cabos|atlanta|chengdu|zhuhai|hangzhou|wuhan|guadalajara|monterrey|san diego|cleveland|united cup|abu dhabi|singapore|seoul|osaka|ningbo|guangzhou|hobart|merida|austin|san jose|olympic"
