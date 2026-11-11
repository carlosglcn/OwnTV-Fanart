package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import tv.own.owntv.features.settings.VideoGroup

/**
 * glcn: how Settings groups are laid out.
 *
 * - Each group shows what people change first; the rest sit behind "Show advanced settings", and the
 *   open / closed state is kept per group.
 * - Fanart rows also live in the group they belong to, tagged FANART; the Fanart group keeps them all.
 * - NEW badges: [newKeys] lists the settings added or changed in this release cycle. A row loses its
 *   badge once opened, a group card once the group is entered. Bump [CYCLE] and refill [newKeys] for
 *   each release: every badge of the previous cycle disappears on its own.
 */
object SettingsTiers {
    private const val FILE = "glcn_settings_tiers"
    const val CYCLE = "2026.11a"

    /** Root rows shown only with the group's advanced part open, by group key. */
    val advanced: Map<String, Set<String>> = mapOf(
        "group_sources" to setOf("epg_offset", "catchup", "catchup_sources"),
        "group_appearance" to setOf(
            "focus_highlight", "ambient_glow", "fonts", "popup_size", "glcn_safe_area", "logo_plate",
            "glcn_ui_sound_style", "glcn_reset_appearance",
        ),
        "group_layout" to setOf("tab_PANEL_WIDTH", "tab_GUIDE_WIDTH", "tab_CONTENT_MENUS", "tab_CH_NAV", "glcn_emby_groups", "glcn_category_icons"),
        "group_app" to setOf("app_icon", "brand_accent", "update_startup", "tab_NETWORK", "tab_DNS"),
        "group_picture" to setOf("glcn_fps"),
        "group_sound" to setOf("glcn_mpv_passthrough"),
    )

    /** Player groups: the parts (by index in [VideoGroup.parts]) that count as advanced. */
    internal val advancedParts: Map<VideoGroup, Set<Int>> = mapOf(
        VideoGroup.PLAYER to setOf(1),
        VideoGroup.PICTURE to setOf(1),
        VideoGroup.LIVE to setOf(1),
        VideoGroup.WATCHING to setOf(1),
        VideoGroup.DIAGNOSTICS to setOf(0),
    )

    /** Where each Fanart row also lives, by group key. Rows not listed stay in Fanart only. */
    val fanartHome: Map<String, String> = mapOf(
        "glcn_style" to "group_appearance",
        "glcn_accessible" to "group_appearance",
        "glcn_season" to "group_appearance",
        "glcn_safe_area" to "group_appearance",
        "glcn_ui_sounds" to "group_appearance",
        "glcn_ui_sound_style" to "group_appearance",
        "animations" to "group_appearance",
        "logo_plate" to "group_appearance",
        "glcn_tv_home" to "group_layout",
        "glcn_home_backdrop" to "group_layout",
        "glcn_home_bg" to "group_layout",
        "glcn_emby_groups" to "group_layout",
        "glcn_category_icons" to "group_layout",
        "glcn_back_window" to "group_player",
        "glcn_osd" to "group_player",
        "glcn_fps" to "group_picture",
        "glcn_dialogue" to "group_sound",
        "glcn_mpv_passthrough" to "group_sound",
        "glcn_zap_views" to "group_live",
        "glcn_auto_skip_intro" to "group_watching",
        "glcn_habits" to "group_watching",
    )

    /** This cycle's new or changed settings, with the group each sits in. */
    val newKeys: Map<String, String> = mapOf(
        "tab_SOURCES" to "group_sources",
        "tab_GLASS_EFFECT" to "group_appearance",
        "glcn_home_bg" to "group_layout",
        "glcn_trakt" to "group_sources",
    )

    /** Bumped on every change, so rows and cards reading it recompose. */
    private val version = mutableIntStateOf(0)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOpen(ctx: Context, group: String): Boolean {
        version.intValue
        return prefs(ctx).getBoolean("open.$group", false)
    }

    fun setOpen(ctx: Context, group: String, open: Boolean) {
        prefs(ctx).edit().putBoolean("open.$group", open).apply()
        version.intValue++
    }

    fun isNewRow(ctx: Context, key: String): Boolean {
        version.intValue
        return key in newKeys && !prefs(ctx).getBoolean("seen.$CYCLE.$key", false)
    }

    fun isNewGroup(ctx: Context, group: String): Boolean {
        version.intValue
        return newKeys.values.contains(group) && !prefs(ctx).getBoolean("seen.$CYCLE.$group", false)
    }

    fun seen(ctx: Context, key: String) {
        val p = prefs(ctx)
        if (p.getBoolean("seen.$CYCLE.$key", false)) return
        p.edit().putBoolean("seen.$CYCLE.$key", true).apply()
        version.intValue++
    }

    /** The groups' keys, in the order of `SettingsGroup`. */
    val groupKeys = listOf(
        "group_quick", "group_fanart", "group_profile", "group_sources", "group_appearance", "group_layout",
        "group_content_metadata", "group_player", "group_picture", "group_sound", "group_live", "group_watching", "group_app",
    )
}
