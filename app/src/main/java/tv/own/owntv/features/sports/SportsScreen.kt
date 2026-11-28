package tv.own.owntv.features.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.live.LiveViewModel

/**
 * The Sports section: the hub, and the match page opened from it (Back returns to the hub) — or from the
 * player's *Match* button, when Back plays that channel again.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onAddPlaylist: () -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    /** False while the player is full screen over the section: its Back is the player's. */
    active: Boolean = true,
    vm: SportsViewModel = koinViewModel(),
    liveVm: LiveViewModel = koinViewModel(),
) {
    var match by remember { mutableStateOf<Match?>(null) }
    var filters by remember { mutableStateOf(false) }
    // The league page, over the hub or a match (Back returns to whichever it came from).
    var league by remember { mutableStateOf<String?>(null) }
    val openLeague: (String?) -> Unit = { k -> league = k; vm.openLeague(k) }
    // Set when the page was opened from the player's Match button: Back plays that channel again.
    var returnTo by remember { mutableStateOf<ChannelEntity?>(null) }
    // The card a match was opened from, so Back lands on it.
    var lastKey by remember { mutableStateOf<String?>(null) }
    val open: (Match?) -> Unit = { m -> if (m != null) lastKey = m.key; match = m; vm.open(m) }
    val playback = koinInject<SportsPlayback>()
    LaunchedEffect(Unit) {
        playback.pendingMatch.value?.let { (m, ch) ->
            playback.pendingMatch.value = null
            returnTo = ch
            open(m)
        } ?: playback.returnToMatch?.let { m ->
            playback.returnToMatch = null
            open(m)
        }
    }
    // From upstream: a match or league page tells the shell when focus is on it, as every tab does. Without it, focus
    // that passed through the menu once left the shell believing the menu still held it, and the next ◀ / ▶ inside
    // the page opened the menu over the page.
    val pageModifier = modifier.onFocusChanged { if (it.hasFocus) onChildFocused() }
    val current = match
    val leagueKey = league
    if (leagueKey != null) {
        SportsLeagueScreen(
            vm = vm,
            onOpenMatch = { m -> openLeague(null); open(m) },
            onBack = { openLeague(null) },
            active = active,
            contentStart = contentStart,
            modifier = pageModifier,
        )
    } else if (current == null) {
        SportsHubScreen(
            vm = vm,
            onOpenMatch = open,
            onPlayChannel = onPlayChannel,
            onChildFocused = onChildFocused,
            onEntryHook = onEntryHook,
            contentStart = contentStart,
            modifier = modifier,
            restoreKey = lastKey,
        )
    } else {
        SportsMatchScreen(
            vm = vm,
            liveVm = liveVm,
            onPlayChannel = { ch, zap -> playback.returnToMatch = current; onPlayChannel(ch, zap) },
            onOpenFilters = { filters = true },
            onOpenLeague = { m -> openLeague(tv.own.owntv.core.sports.model.League.keyOf(m.sport, m.leagueSlug)) },
            onAddPlaylist = onAddPlaylist,
            onBack = {
                val ch = returnTo
                returnTo = null
                open(null)
                if (ch != null) onPlayChannel(ch, playback.session.value?.options?.map { it.channel }.orEmpty())
            },
            active = active,
            contentStart = contentStart,
            modifier = pageModifier,
        )
        val page = vm.matchPage.collectAsStateWithLifecycle().value
        if (filters && page != null && page.hasPlaylist) {
            SportsFilterPanel(page, onFilter = vm::setFilter, onRemember = vm::setRememberFilters, onDismiss = { filters = false })
        }
    }
}
