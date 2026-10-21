package tv.own.owntv.glcn

import android.content.Context

/** glcn: wide network/studio logos (default) or the streaming service's own square icon. */
object PlatformLogoStyle {
    private const val FILE = "glcn_platform_logo"
    private const val KEY = "square"

    fun square(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setSquare(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
