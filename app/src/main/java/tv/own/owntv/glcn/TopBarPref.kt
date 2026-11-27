package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.own.owntv.R

/**
 * glcn: how the top bar (Navigation = Top bar) is drawn. CAPSULE is the bar as it always was — a glass capsule at the
 * top left; ISLAND sits in the middle with no background until the focus is on it; TABS is bare names over a veil,
 * the open one underlined; PILLS gives every section its own chip; ICONS is a short capsule of icons that name
 * themselves when open or focused.
 */
enum class TopBarShape(val label: Int) {
    CAPSULE(R.string.glcn_top_bar_capsule),
    ISLAND(R.string.glcn_top_bar_island),
    TABS(R.string.glcn_top_bar_tabs),
    PILLS(R.string.glcn_top_bar_pills),
    ICONS(R.string.glcn_top_bar_icons),
    ;

    /** In the middle of the free width, the logo left outside it. */
    val centred: Boolean get() = this == ISLAND || this == ICONS
}

/**
 * glcn: the chosen [TopBarShape]. The capsule unless another is picked — it brings its own glass, so its names read
 * over any picture, and it fits beside the chips on the right (the island, once the default of a new install, did not
 * fit on a 1080p screen with every name).
 */
object TopBarPref {
    var shape by mutableStateOf(TopBarShape.CAPSULE)
        private set
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)?.let { name -> TopBarShape.entries.firstOrNull { it.name == name } }
        shape = stored ?: TopBarShape.CAPSULE.also {
            prefs.edit().putString(KEY, it.name).apply()
        }
    }

    fun set(value: TopBarShape) {
        shape = value
        app?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putString(KEY, value.name)?.apply()
    }

    fun next() = set(TopBarShape.entries[(shape.ordinal + 1) % TopBarShape.entries.size])

    private const val FILE = "glcn_top_bar"
    private const val KEY = "shape"
}
