package tv.own.owntv.features.shell.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * glcn: an overlay opened by holding OK must not take the rest of that press: the key keeps repeating while held, and
 * its release would "click" whatever row has focus — the quick guide opened and closed again on release, tuning the
 * channel already playing. OK reaches the overlay only from its first fresh press on.
 */
internal fun Modifier.okAfterFreshPress(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        if (!ok) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
        !armed
    }
}
