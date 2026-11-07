package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * glcn: where a title's page begins its words (the logo's top, in window pixels), so the trailer can play in the
 * free room above them, at the top left, instead of over the picture. 0 = no title page measured.
 */
object TitleRoom {
    var textTop by mutableFloatStateOf(0f)
}
