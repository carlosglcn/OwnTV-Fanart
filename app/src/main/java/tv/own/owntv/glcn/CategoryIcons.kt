package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: a small picture beside each provider group in the category lists — a football for sport, a clapperboard for
 * cinema, the flag of a country, and for a quality group (4K, HDR, Dolby Vision…) the same mark the posters carry.
 * Picked from the group's name; hold OK on a group › Icon to choose another or none. One switch in Settings › Fanart.
 * Kept per device, by the group's name (ids change when a playlist is synced again).
 */
object CategoryIcons {
    sealed interface Glyph {
        /** A colour emoji, drawn by the system font. */
        data class Emoji(val text: String) : Glyph
        /** A quality mark in the posters' style; [gold] for the HDR formats. */
        data class Badge(val text: String, val gold: Boolean) : Glyph
    }

    var on by mutableStateOf(true)
        private set
    private val overrides = mutableStateMapOf<String, String>()
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        on = p.getBoolean(KEY_ON, true)
        p.all.forEach { (k, v) -> if (k.startsWith(PREFIX) && v is String) overrides[k.removePrefix(PREFIX)] = v }
    }

    fun setOn(context: Context, value: Boolean) {
        on = value
        prefs(context).edit().putBoolean(KEY_ON, value).apply()
    }

    /** [code]: an emoji from [CHOICES], [NONE] for no icon, null to go back to the automatic one. */
    fun choose(context: Context, groupName: String, code: String?) {
        val k = key(groupName)
        if (code == null) overrides.remove(k) else overrides[k] = code
        prefs(context).edit().apply { if (code == null) remove(PREFIX + k) else putString(PREFIX + k, code) }.apply()
    }

    /** The group's picture, or null (switched off, none chosen, nothing recognised). */
    fun glyph(groupName: String, tags: List<String> = emptyList(), country: String? = null): Glyph? {
        if (!on) return null
        overrides[key(groupName)]?.let { return if (it == NONE) null else Glyph.Emoji(it) }
        return auto(groupName, tags, country)
    }

    /** Whether the group has a chosen icon (or none chosen) rather than the automatic one. */
    fun isChosen(groupName: String): Boolean = key(groupName) in overrides

    internal fun auto(groupName: String, tags: List<String>, country: String? = null): Glyph? {
        val text = " " + normalize(groupName) + " "
        TOPICS.firstOrNull { (_, words) -> words.any { w -> text.contains(" $w ") } }?.let { return Glyph.Emoji(it.first) }
        COUNTRIES.firstOrNull { (_, words) -> words.any { w -> text.contains(" $w ") } }?.let { return Glyph.Emoji(flag(it.first)) }
        // The provider's "ES|" prefix, when the name itself says nothing better.
        val quality = qualityOf(text, tags)
        if (quality == null && country != null && country.length == 2 && country.all { it in 'A'..'Z' }) return Glyph.Emoji(flag(country))
        return quality
    }

    private fun qualityOf(text: String, tags: List<String>): Glyph? {
        val all = text + " " + tags.joinToString(" ") { normalize(it) } + " "
        return when {
            all.contains(" DOLBY VISION ") || all.contains(" DV ") || all.contains(" DOVI ") -> Glyph.Badge(BADGE_DV, gold = true)
            all.contains(" HDR10+ ") || all.contains(" HDR10 PLUS ") -> Glyph.Badge(BADGE_HDR10_PLUS, gold = true)
            all.contains(" HDR10 ") -> Glyph.Badge(BADGE_HDR10, gold = true)
            all.contains(" HDR ") -> Glyph.Badge(BADGE_HDR, gold = true)
            all.contains(" 8K ") -> Glyph.Badge(BADGE_8K, gold = false)
            all.contains(" 4K ") || all.contains(" UHD ") || all.contains(" 2160P ") -> Glyph.Badge(BADGE_4K, gold = false)
            all.contains(" FHD ") || all.contains(" 1080P ") -> Glyph.Badge(BADGE_FHD, gold = false)
            text.trim() == "HD" || text.trim() == "SD" -> Glyph.Badge(text.trim(), gold = false)
            else -> null
        }
    }

    /** Upper case, accents and punctuation gone, so "Películas", "PELICULAS" and "⚽ Fútbol |" all match. */
    private fun normalize(s: String): String {
        val plain = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")
        return plain.uppercase(java.util.Locale.ROOT).replace(NON_WORD, " ").replace(SPACES, " ").trim()
    }

    /** "ES" → 🇪🇸: the two regional-indicator letters. */
    private fun flag(code: String): String {
        val c = if (code == "UK") "GB" else code
        return String(Character.toChars(REGIONAL_A + (c[0] - 'A'))) + String(Character.toChars(REGIONAL_A + (c[1] - 'A')))
    }

    private fun key(groupName: String) = normalize(groupName)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    const val NONE = "-"

    /** What the picker offers, in this order. */
    val CHOICES: List<String> = CHOICE_LIST.split(' ')

    private val TOPICS: List<Pair<String, List<String>>> = TOPIC_TABLE.lines().filter { it.isNotBlank() }.map { line ->
        val (emoji, words) = line.trim().split('=', limit = 2)
        emoji to words.split(',').map { it.trim() }
    }
    private val COUNTRIES: List<Pair<String, List<String>>> = COUNTRY_TABLE.lines().filter { it.isNotBlank() }.map { line ->
        val (code, words) = line.trim().split('=', limit = 2)
        code to words.split(',').map { it.trim() }
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^A-Z0-9+]+")
    private val SPACES = Regex(" +")
    private const val REGIONAL_A = 0x1F1E6
    private const val PREFS = "glcn_category_icons"
    private const val KEY_ON = "on"
    private const val PREFIX = "g:"
    private const val BADGE_DV = "DV"
    private const val BADGE_HDR10_PLUS = "HDR10+"
    private const val BADGE_HDR10 = "HDR10"
    private const val BADGE_HDR = "HDR"
    private const val BADGE_8K = "8K"
    private const val BADGE_4K = "4K"
    private const val BADGE_FHD = "FHD"
}

/** Emoji = words (upper case, no accents), first match wins — so sport before "TV", kids before "cine". */
private const val TOPIC_TABLE = """
⚽=FUTBOL,FOOTBALL,SOCCER,LALIGA,LIGA,CHAMPIONS,PREMIER,BUNDESLIGA,SERIE A,LIGUE 1,DAZN,MOVISTAR LIGA,M LIGA,EUROPA LEAGUE
🏀=BALONCESTO,BASKET,BASKETBALL,NBA,ACB,EUROLIGA,EUROLEAGUE
🎾=TENIS,TENNIS,ATP,WTA
🏎️=F1,FORMULA 1,MOTOR,MOTOGP,MOTORSPORT,RACING
🥊=BOXEO,BOXING,UFC,MMA,LUCHA,WRESTLING,WWE
⛳=GOLF
🏆=DEPORTES,DEPORTE,SPORTS,SPORT,ESPN,EUROSPORT,EVENTOS,EVENTS,PPV
🧸=INFANTIL,INFANTILES,KIDS,NINOS,CHILDREN,DIBUJOS,CARTOONS,ANIMACION,ANIMATION,DISNEY,JUNIOR
🍥=ANIME
🔫=ACTION,ACCION
📖=BIOGRAPHY,BIOGRAFIA,BIOGRAFIAS,BIOPIC
🎭=DRAMA,DRAMAS
🕵️=NOIR,CRIME,CRIMEN,THRILLER,THRILLERS,SUSPENSE,SUSPENSO,MISTERIO,MYSTERY,POLICIACA,POLICIACO,INTRIGA
🗺️=ADVENTURE,ADVENTURES,AVENTURA,AVENTURAS
👽=SCI FI,SCIFI,CIENCIA FICCION,SCIENCE FICTION
🧙=FANTASY,FANTASIA
💕=ROMANCE,ROMANTICA,ROMANTICAS,ROMANTICO,ROMANTIC
👪=FAMILY,FAMILIA,FAMILIAR
⚔️=WAR,GUERRA,BELICA,BELICO
🤠=WESTERN,WESTERNS
🎬=TV MOVIE,TELEFILM,TELEFILMS,CINE,PELICULAS,PELICULA,MOVIES,MOVIE,FILMS,FILM,ESTRENOS,CINEMA,PREMIERES
📺=SERIES,SERIE,SHOWS,TV SHOWS,TELENOVELAS,TELENOVELA,NOVELAS,SOAP,REALITY,REALITY SHOW,TALK,TALK SHOW,CONCURSOS,GAME SHOW
🎵=MUSICA,MUSIC,MUSICAL,MUSICALES,RADIO,CONCIERTOS,CONCERTS
📰=NOTICIAS,NEWS,INFORMATIVOS,NOTICIAS 24H
🌍=DOCUMENTALES,DOCUMENTAL,DOCUMENTARY,DOCUMENTARIES,DOCS,NATURALEZA,NATURE,HISTORIA,HISTORY
🍳=COCINA,COOKING,FOOD,GASTRONOMIA
😂=COMEDIA,COMEDIAS,COMEDY,COMEDIES,HUMOR
👻=TERROR,HORROR,MIEDO,HALLOWEEN
⛪=RELIGION,RELIGIOSOS,RELIGIOUS
🔞=ADULTOS,ADULT,ADULTS,XXX,18
🎄=NAVIDAD,CHRISTMAS
⭐=FAVORITOS,FAVORITES,VIP,PREMIUM
📡=TDT,NACIONALES,GENERALISTAS,LOCAL,LOCALES,AUTONOMICAS,REGIONAL
"""

/** ISO code = names a group may carry (upper case, no accents). */
private const val COUNTRY_TABLE = """
ES=ESPANA,SPAIN,ESPANOL,ESPANOLA,SPANISH
PT=PORTUGAL,PORTUGUES,PORTUGUESE
FR=FRANCIA,FRANCE,FRANCAIS,FRENCH
IT=ITALIA,ITALY,ITALIANO,ITALIAN
DE=ALEMANIA,GERMANY,DEUTSCHLAND,GERMAN,DEUTSCH
GB=REINO UNIDO,UNITED KINGDOM,ENGLAND,INGLATERRA,BRITISH
US=USA,ESTADOS UNIDOS,UNITED STATES,AMERICA
MX=MEXICO,MEXICANO
AR=ARGENTINA
CO=COLOMBIA
CL=CHILE
PE=PERU
VE=VENEZUELA
BR=BRASIL,BRAZIL
NL=HOLANDA,PAISES BAJOS,NETHERLANDS,DUTCH
BE=BELGICA,BELGIUM
CH=SUIZA,SWITZERLAND
AT=AUSTRIA
PL=POLONIA,POLAND
RO=RUMANIA,ROMANIA
GR=GRECIA,GREECE
TR=TURQUIA,TURKEY
MA=MARRUECOS,MOROCCO
IE=IRLANDA,IRELAND
SE=SUECIA,SWEDEN
NO=NORUEGA,NORWAY
DK=DINAMARCA,DENMARK
CA=CANADA
IN=INDIA
"""

/** The icons the picker offers. */
private const val CHOICE_LIST = "⚽ 🏀 🎾 🏎️ 🥊 ⛳ 🏆 🎬 📺 🧸 🍥 🔫 📖 🎭 🕵️ 🗺️ 👽 🧙 💕 👪 ⚔️ 🤠 🎵 📰 🌍 🍳 😂 👻 ⭐ 📡 🎄 🔞 ❤️ 🔥 💎 🎮 🐾 🚀 🇪🇸 🇵🇹 🇫🇷 🇮🇹 🇩🇪 🇬🇧 🇺🇸 🇲🇽 🇦🇷 🇧🇷"
