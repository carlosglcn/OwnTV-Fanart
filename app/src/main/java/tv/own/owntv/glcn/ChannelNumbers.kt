package tv.own.owntv.glcn

import androidx.compose.runtime.compositionLocalOf

/**
 * glcn: Settings › Live TV › "Channel numbers". Provided once by the shell so every list, guide row,
 * card and plate that prints a provider number reads the same switch — Off hides them all, without
 * touching the stored numbers. Default on, as the setting itself.
 */
val LocalShowChannelNumbers = compositionLocalOf { true }
