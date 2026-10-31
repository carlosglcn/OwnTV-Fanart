package tv.own.owntv.features.search

import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.mpx

/**
 * Search's field, with the voice-search microphone on its right where the device has a speech
 * recogniser. What the user says becomes the query, so it searches exactly as typing it would.
 * ▼ from either goes to [focusDown].
 */
@Composable
internal fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    fieldFocus: FocusRequester,
    focusDown: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // glcn: words typed on the paired phone become the query.
    val typed = VoiceTrigger.typed.value
    androidx.compose.runtime.LaunchedEffect(typed) {
        if (typed != null) { onQueryChange(typed); VoiceTrigger.typed.value = null }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        StageSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.search_field_hint),
            modifier = Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                .focusProperties { down = focusDown },
        )
        // glcn: "pon La 1" / "busca Severance" — the command words are not part of the search.
        VoiceSearchButton(onQuery = { spoken -> onQueryChange(tv.own.owntv.core.voice.VoiceCommandParser.parse(spoken)?.query ?: spoken) }, modifier = Modifier.focusProperties { down = focusDown })
    }
}

/** The 48-high microphone; absent when no recogniser answers, or once one has refused to start. */
@Composable
private fun VoiceSearchButton(onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var available by remember(context) { mutableStateOf(VoiceSearch.isAvailable(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        VoiceSearch.spokenQuery(result.resultCode, matches)?.let(onQuery)
    }
    if (!available) return
    val label = stringResource(R.string.search_voice)
    val prompt = stringResource(R.string.search_voice_prompt)
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    // glcn: the remote's microphone button asked for it (see [VoiceTrigger]).
    val asked = VoiceTrigger.pending.intValue
    LaunchedEffect(asked) {
        if (VoiceTrigger.consume()) {
            try { launcher.launch(VoiceSearch.intent(prompt, language)) } catch (_: ActivityNotFoundException) { available = false }
        }
    }
    StageTool(
        text = null,
        icon = OwnTVIcon.MIC,
        boxed = true,
        onClick = {
            // Resolving is not a promise: the recogniser can be disabled between the check and OK.
            // The recogniser is a screen OwnTV opens itself, so it must not open picture-in-picture.
            try {
                tv.own.owntv.features.pip.startAsHandOff(VoiceSearch.intent(prompt, language)) { launcher.launch(it) }
            } catch (_: ActivityNotFoundException) {
                available = false
            }
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}
