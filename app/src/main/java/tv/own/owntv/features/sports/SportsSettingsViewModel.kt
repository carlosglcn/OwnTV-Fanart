package tv.own.owntv.features.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.RebuildState
import tv.own.owntv.core.sports.SportsActivityTracker
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.SportsSuggestion
import tv.own.owntv.core.sports.model.Team
import java.util.Locale

/**
 * Settings → Sports. Every value is the active profile's `SportsConfig`, so it travels in
 * backups and local sync with no extra work; "Show Sports in the menu" is the Navigation setting's own
 * hidden set.
 */
class SportsSettingsViewModel(
    private val settings: SettingsRepository,
    private val source: SportsSource,
    private val repository: SportsRepository,
    activity: SportsActivityTracker,
    sourceDao: SourceDao,
) : ViewModel() {

    val config: StateFlow<SportsConfig> = settings.activeSportsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsConfig())

    val shownInMenu: StateFlow<Boolean> = settings.navMenuHidden.map { MainSection.SPORTS.name !in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The profile's live playlists (id → name), for the Playlist filter. */
    val playlists: StateFlow<List<Pair<Long, String>>> = activeProfileSources(settings, sourceDao)
        .map { a -> a.sources.filter { it.syncLive }.map { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When the sports data was last rebuilt, and whether a rebuild runs now (Refresh sports data now). */
    val rebuiltAt: StateFlow<Long?> = activity.rebuiltAt
    val rebuilding: StateFlow<Boolean> = activity.rebuild.map { it is RebuildState.Running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun rebuild() = repository.rebuild()

    /** The app's language, which ranks first until the user picks commentary languages. */
    val appLanguage: String get() = Locale.getDefault().language

    fun update(transform: (SportsConfig) -> SportsConfig) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow(), transform)
    }

    fun setShownInMenu(on: Boolean) = viewModelScope.launch {
        val hidden = settings.navMenuHidden.first()
        settings.setNavMenuHidden(if (on) hidden - MainSection.SPORTS.name else hidden + MainSection.SPORTS.name)
    }

    /** glcn: follow what is usually followed in [country] (adds only), then fetch the board again. */
    fun applyPreset(country: String) = viewModelScope.launch {
        val preset = tv.own.owntv.core.sports.SportsPresets.leaguesFor(country)
        if (preset.isEmpty()) return@launch
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c -> c.copy(leagues = c.leagues + preset.filter { it !in c.leagues }) }
        repository.rebuild()
    }

    fun toggleLeague(key: String) = update { c ->
        val now = c.leagues
        c.copy(leagues = if (key in now) now - key else now + key)
    }

    /** The Move overlay saved a new sport order (Settings → Sports and leagues). */
    fun setSportOrder(order: List<tv.own.owntv.core.sports.model.Sport>) = update { it.copy(sportOrder = order) }

    /**
     * The Move overlay saved a new order for one sport's chosen leagues: they go first, the other
     * chosen leagues follow as they were (only the order within a sport ranks anything).
     */
    fun setLeagueOrder(keys: List<String>) = update { c -> c.copy(leagues = keys + (c.leagues - keys.toSet())) }

    fun toggleTeam(team: FollowedTeam) = update { c ->
        if (c.teams.any { it.key == team.key }) c.copy(teams = c.teams.filterNot { it.key == team.key })
        else {
            // A team from a league not followed yet brings its league along, or the hub would have nothing of it.
            val league = teamLeague[team.key]
            val leagues = if (league != null && !c.allSports && league !in c.leagues) c.leagues + league else c.leagues
            c.copy(teams = c.teams + team, leagues = leagues)
        }
    }

    fun suggestions(c: SportsConfig): List<SportsSuggestion> = SportsMemory.suggestions(c, c.followedLeagues())

    /** Teams per catalogue league, kept once fetched; a league that failed is asked again on the next search. */
    private val leagueTeams = HashMap<String, List<Pair<FollowedTeam, String>>>()
    /** Where each team found by the search plays: following it also follows that league, or the hub would never show it. */
    private val teamLeague = HashMap<String, String>()

    private suspend fun indexOf(keys: Collection<String>): List<Pair<FollowedTeam, String>> {
        val sports = tv.own.owntv.core.sports.model.Sport.values().filter {
            it != tv.own.owntv.core.sports.model.Sport.TENNIS && it != tv.own.owntv.core.sports.model.Sport.RACING && it != tv.own.owntv.core.sports.model.Sport.CYCLING &&
                it != tv.own.owntv.core.sports.model.Sport.MMA && it != tv.own.owntv.core.sports.model.Sport.CRICKET
        }.toSet()
        val leagues = keys.mapNotNull { SportsCatalog.league(it) }.filter { it.sport in sports }
        coroutineScope {
            leagues.filter { it.key !in leagueTeams }.map { league ->
                async {
                    val teams = runCatching { source.teams(league) }.getOrNull()
                    if (!teams.isNullOrEmpty()) {
                        leagueTeams[league.key] = teams.map { t ->
                            teamLeague.putIfAbsent("${league.sport.path}/${t.id}", league.key)
                            FollowedTeam(league.sport, t.id, t.name, t.abbreviation) to
                                listOf(t.name, t.shortName, t.location.orEmpty(), t.abbreviation).joinToString(" ")
                        }
                    }
                }
            }.forEach { it.await() }
        }
        return leagues.flatMap { leagueTeams[it.key].orEmpty() }.distinctBy { it.first.key }
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    suspend fun searchTeams(query: String): List<FollowedTeam> {
        if (query.trim().length < 2) return emptyList()
        val q = fold(query.trim())
        fun hits(index: List<Pair<FollowedTeam, String>>) = index.filter { (_, text) -> fold(text).contains(q) }.map { it.first }.take(30)
        val own = hits(indexOf(config.value.followedLeagues()))
        if (own.isNotEmpty()) return own
        // Nothing among the followed leagues: look in the whole catalogue.
        return hits(indexOf(SportsCatalog.leagues.map { it.key }))
    }
}
