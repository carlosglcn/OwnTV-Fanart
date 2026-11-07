package tv.own.owntv.glcn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** glcn: the picture lent by title to the programme on the focused channel (the guide gave none), for the backdrop. */
object LentLiveArt {
    private var current by mutableStateOf<Pair<String, String>?>(null)

    fun set(title: String, url: String?) {
        current = url?.takeIf { it.isNotBlank() }?.let { title to it }
    }

    fun forTitle(title: String?): String? = current?.takeIf { title != null && it.first == title }?.second
}
