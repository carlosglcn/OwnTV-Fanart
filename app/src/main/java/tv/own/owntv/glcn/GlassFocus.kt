package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** glcn: with Glass on, row and card focus is drawn as tinted glass (see drawStageFocus). */
object GlassFocus {
    var on by mutableStateOf(false)
}
