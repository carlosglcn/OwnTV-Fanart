package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * glcn: the focus of a style with a second accent (Brasa). Rows, cards and posters focus as an OUTLINE in the
 * accent's gradient over the faintest wash — the picture under a focused card still shows — and buttons, pills and
 * menu items fill with the gradient. Null [second] = the style has none and focus is drawn as it always was.
 */
object EmberFocus {
    var second by mutableStateOf<Color?>(null)
    val on: Boolean get() = second != null
}
