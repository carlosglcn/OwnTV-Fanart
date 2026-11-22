package tv.own.owntv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.core.theme.ThemeMode

/**
 * Available OwnTV themes. Persisted via DataStore and selectable from Settings → Theme.
 * SYSTEM follows the platform dark/light setting.
 */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.DARK }

/**
 * Width of the focus ring the user picks in Settings, to make the selection prominent.
 * A CompositionLocal rather than a [Dimens] constant because every surface that draws it has to
 * follow the same choice.
 */
val LocalFocusBorderWidth = staticCompositionLocalOf { Dimens.FocusBorderWidth }

/** The offered ring widths in dp — thin, normal, thick, extra thick. 2 dp is the shipped default. */
val FocusBorderWidthChoices = listOf(1, 2, 4, 6)

/** Map the resolved OwnTV tokens onto a tv-material3 M3 [ColorScheme]. */
private fun schemeFrom(c: OwnTVColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    }

@Composable
fun OwnTVTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    systemInDarkTheme: Boolean,
    customAccent: String = "",
    focusHighlight: String = "",
    focusBorderWidthDp: Int = 2,
    animationLevel: AnimationLevel = AnimationLevel.FULL,
    mainFontFamily: AppFontFamily = AppFontFamily.PLUS_JAKARTA_SANS,
    popupFontFamily: AppFontFamily = AppFontFamily.PLUS_JAKARTA_SANS,
    popupFontSizePercent: Int = PopupFontScale.DEFAULT,
    popupSizePercent: Int = PopupSizeScale.DEFAULT,
    /** glcn: a ready-made style's own page tones over the theme's palette. */
    glcnStyle: tv.own.owntv.core.theme.GlcnStyle? = null,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    val colors = ownTvColors(
        isDark = useDark,
        accent = accent,
        customAccent = customAccent,
        focusHighlight = focusHighlight,
    )

    CompositionLocalProvider(
        LocalOwnTVColors provides colors,
        LocalStagePalette provides (if (useDark) StagePalette.Dark else StagePalette.Light).let { base ->
            val st = glcnStyle?.takeIf { it.light == !useDark } ?: return@let base
            val page = Color(st.page)
            base.copy(
                page = page,
                wash = page,
                glassTint = Color(st.glassTint),
                glassOff = Color(st.glassTint),
                text = Color(st.text),
                // glcn: the style's surfaces take its own tone — every flat fill is its ink at a low alpha, so a navy
                // theme's panels are navy, a warm one's warm, instead of the same grey for all.
                ink = if (useDark) androidx.compose.ui.graphics.lerp(Color.White, Color(st.text), 0.6f) else Color(st.text),
                itemText = Color(st.text),
                menuItemText = Color(st.text),
                muted = Color(st.muted),
                navText = Color(st.muted),
                body = Color(st.body),
            )
        },
        LocalThemeMode provides themeMode,
        LocalFocusBorderWidth provides focusBorderWidthDp.dp,
        LocalAnimationLevel provides animationLevel,
        LocalMainFontFamily provides mainFontFamily.asComposeFamily(),
        LocalPopupFontFamily provides popupFontFamily.asComposeFamily(),
        LocalPopupFontScaleFactor provides PopupFontScale.factor(popupFontSizePercent),
        LocalPopupSizeScaleFactor provides PopupSizeScale.factor(popupSizePercent),
    ) {
        MaterialTheme(
            colorScheme = schemeFrom(colors),
            typography = ownTVTypography(LocalMainFontFamily.current),
            content = content,
        )
    }
}

/** Convenience accessor: `OwnTVTheme.colors.focusBorder`. */
object OwnTVTheme {
    val colors: OwnTVColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOwnTVColors.current
}
