package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: a small picture beside each provider group in the category lists — a line icon in the app's own style (a football
 * for football, a clapperboard for cinema, masks for drama…), the country's code for a country, and for a quality group
 * (4K, HDR, Dolby Vision…) the same mark the posters carry.
 * Picked from the group's name; hold OK on a group › Icon to choose another or none. One switch in Settings › Fanart.
 * Kept per device, by the group's name (ids change when a playlist is synced again).
 */
object CategoryIcons {
    sealed interface Glyph {
        /** A line icon from [CategoryGlyphPaths], tinted like the list's other icons. */
        data class Icon(val key: String) : Glyph
        /** A platform's own logo (an Emby / Jellyfin library grouped by platform), on a light plate. */
        data class Logo(val url: String) : Glyph
        /** A quality mark in the posters' style; [gold] for the HDR formats. */
        data class Badge(val text: String, val gold: Boolean) : Glyph
    }

    /** How the category lists name their groups: words only (the default), a picture beside the words, or the logo alone. */
    enum class Mode { TEXT, ICONS, LOGOS }

    var mode by mutableStateOf(Mode.TEXT)
        private set

    /** Any picture at all (icons, or logos). */
    val on: Boolean get() = mode != Mode.TEXT
    private val overrides = mutableStateMapOf<String, String>()
    private var loaded = false

    fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        mode = p.getString(KEY_MODE, null)?.let { m -> Mode.entries.firstOrNull { it.name == m } } ?: Mode.TEXT
        p.all.forEach { (k, v) -> if (k.startsWith(PREFIX) && v is String) overrides[k.removePrefix(PREFIX)] = v }
    }

    /** Words only → icons and words → logos alone → words only. */
    fun cycle(context: Context) {
        mode = Mode.entries[(mode.ordinal + 1) % Mode.entries.size]
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** A platform category of an Emby / Jellyfin library grouped by platform (Netflix, HBO… or "Other networks"). */
    fun isPlatform(groupName: String): Boolean =
        tv.own.owntv.core.sync.PlatformLogos.logoFor(groupName) != null || groupName == tv.own.owntv.core.sync.PlatformNames.other()

    /** Whether the row shows [glyph] instead of the group's name (a platform's logo, with logos alone chosen). */
    fun hidesName(glyph: Glyph?): Boolean = mode == Mode.LOGOS && glyph is Glyph.Logo

    /** [code]: an icon key from [CHOICES], [NONE] for no icon, null to go back to the automatic one. */
    fun choose(context: Context, groupName: String, code: String?) {
        val k = key(groupName)
        if (code == null) overrides.remove(k) else overrides[k] = code
        prefs(context).edit().apply { if (code == null) remove(PREFIX + k) else putString(PREFIX + k, code) }.apply()
    }

    /** The group's picture, or null (switched off, none chosen, nothing recognised). */
    fun glyph(groupName: String, tags: List<String> = emptyList(), country: String? = null): Glyph? {
        if (!on) return null
        // glcn: a platform category (Netflix, HBO…) shows the platform's logo.
        tv.own.owntv.core.sync.PlatformLogos.logoFor(groupName)?.let { return Glyph.Logo(it) }
        // A choice from before the line icons (an emoji) is not a key any more: back to the automatic one.
        overrides[key(groupName)]?.let { if (it == NONE) return null; if (it in CategoryGlyphPaths.paths) return Glyph.Icon(it) }
        return auto(groupName, tags, country)
    }

    /** Whether the group has a chosen icon (or none chosen) rather than the automatic one. */
    fun isChosen(groupName: String): Boolean = key(groupName) in overrides

    internal fun auto(groupName: String, tags: List<String>, country: String? = null): Glyph? {
        val text = " " + normalize(groupName) + " "
        TOPICS.firstOrNull { (_, words) -> words.any { w -> text.contains(" $w ") } }?.let { return Glyph.Icon(it.first) }
        COUNTRIES.firstOrNull { (_, words) -> words.any { w -> text.contains(" $w ") } }?.let { return Glyph.Badge(it.first, gold = false) }
        // The provider's "ES|" prefix, when the name itself says nothing better.
        val quality = qualityOf(text, tags)
        if (quality == null && country != null && country.length in 2..3 && country.all { it in 'A'..'Z' }) return Glyph.Badge(country, gold = false)
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
    private const val PREFS = "glcn_category_icons"
    private const val KEY_MODE = "mode"
    private const val PREFIX = "g:"
    private const val BADGE_DV = "DV"
    private const val BADGE_HDR10_PLUS = "HDR10+"
    private const val BADGE_HDR10 = "HDR10"
    private const val BADGE_HDR = "HDR"
    private const val BADGE_8K = "8K"
    private const val BADGE_4K = "4K"
    private const val BADGE_FHD = "FHD"
}

/** Icon key = words (upper case, no accents), first match wins — so sport before "TV", kids before "cine". */
private const val TOPIC_TABLE = """
soccer=FUTBOL,FOOTBALL,SOCCER,LALIGA,LIGA,CHAMPIONS,PREMIER,BUNDESLIGA,SERIE A,LIGUE 1,DAZN,MOVISTAR LIGA,M LIGA,EUROPA LEAGUE
basketball=BALONCESTO,BASKET,BASKETBALL,NBA,ACB,EUROLIGA,EUROLEAGUE
tennis=TENIS,TENNIS,ATP,WTA,PADEL
motor=F1,FORMULA 1,MOTOR,MOTOGP,MOTORSPORT,RACING
combat=BOXEO,BOXING,UFC,MMA,LUCHA,WRESTLING,WWE
golf=GOLF
handball=BALONMANO,HANDBALL
football=NFL,FUTBOL AMERICANO,AMERICAN FOOTBALL
hockey=HOCKEY,NHL
esports=ESPORTS,E SPORTS,GAMING,VIDEOJUEGOS
sports=DEPORTES,DEPORTE,SPORTS,SPORT,ESPN,EUROSPORT,EVENTOS,EVENTS,PPV
anime=ANIME
kids=INFANTIL,INFANTILES,KIDS,NINOS,CHILDREN,DIBUJOS,CARTOONS,DISNEY,JUNIOR
animation=ANIMACION,ANIMATION
action=ACTION,ACCION
biography=BIOGRAPHY,BIOGRAFIA,BIOGRAFIAS,BIOPIC
drama=DRAMA,DRAMAS
crime=NOIR,CRIME,CRIMEN,POLICIACA,POLICIACO,POLICIACAS
thriller=THRILLER,THRILLERS
mystery=MISTERIO,MYSTERY
suspense=SUSPENSE,SUSPENSO
intrigue=INTRIGA
adventure=ADVENTURE,ADVENTURES,AVENTURA,AVENTURAS
scifi=SCI FI,SCIFI,CIENCIA FICCION,SCIENCE FICTION
fantasy=FANTASY,FANTASIA
romance=ROMANCE,ROMANTICA,ROMANTICAS,ROMANTICO,ROMANTIC
family=FAMILY,FAMILIA,FAMILIAR
war=WAR,GUERRA,BELICA,BELICO
western=WESTERN,WESTERNS
tvmovie=TV MOVIE,TELEFILM,TELEFILMS
movies=CINE,PELICULAS,PELICULA,MOVIES,MOVIE,FILMS,FILM,ESTRENOS,CINEMA,PREMIERES
reality=REALITY,REALITY SHOW,CONCURSOS,GAME SHOW
talk=TALK,TALK SHOW,TERTULIA,MAGAZINE
series=SERIES,SERIE,SHOWS,TV SHOWS,TELENOVELAS,TELENOVELA,NOVELAS,SOAP
radio=RADIO
music=MUSICA,MUSIC,MUSICAL,MUSICALES,CONCIERTOS,CONCERTS
news=NOTICIAS,NEWS,INFORMATIVOS,NOTICIAS 24H
history=HISTORIA,HISTORY
nature=NATURALEZA,NATURE,ANIMALES,ANIMALS
science=CIENCIA,SCIENCE
travel=VIAJES,TRAVEL
docs=DOCUMENTALES,DOCUMENTAL,DOCUMENTARY,DOCUMENTARIES,DOCS
cooking=COCINA,COOKING,FOOD,GASTRONOMIA
comedy=COMEDIA,COMEDIAS,COMEDY,COMEDIES,HUMOR
horror=TERROR,HORROR,MIEDO,HALLOWEEN
religion=RELIGION,RELIGIOSOS,RELIGIOUS
adult=ADULTOS,ADULT,ADULTS,XXX,18
christmas=NAVIDAD,CHRISTMAS
favorites=FAVORITOS,FAVORITES,VIP,PREMIUM
tdt=OTRAS CADENAS,OTHER NETWORKS,CADENAS,NETWORKS,TDT,NACIONALES,GENERALISTAS,LOCAL,LOCALES,AUTONOMICAS,REGIONAL
live=24 7,24/7,EN DIRECTO,LIVE
"""

/** Country code shown = names a group may carry (upper case, no accents). */
private const val COUNTRY_TABLE = """
ES=ESPANA,SPAIN,ESPANOL,ESPANOLA,SPANISH
PT=PORTUGAL,PORTUGUES,PORTUGUESE
FR=FRANCIA,FRANCE,FRANCAIS,FRENCH
IT=ITALIA,ITALY,ITALIANO,ITALIAN
DE=ALEMANIA,GERMANY,DEUTSCHLAND,GERMAN,DEUTSCH
UK=REINO UNIDO,UNITED KINGDOM,ENGLAND,INGLATERRA,BRITISH
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

/** The icons the picker offers, in this order. */
private const val CHOICE_LIST = "soccer basketball tennis motor combat golf handball football hockey esports sports movies tvmovie series kids animation anime action drama comedy horror crime thriller mystery suspense intrigue adventure scifi fantasy romance family war western biography history docs nature science travel music radio news reality talk cooking religion adult christmas favorites tdt live"
