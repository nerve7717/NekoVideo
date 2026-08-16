package com.neko.video.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.neko.video.model.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color.White,
    secondary = Color(0xFF5B6B7C),
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFFFFFFF),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF5BA0FF),
    onPrimary = Color(0xFF001B3D),
    secondary = Color(0xFFA8B3C0),
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
)

@Composable
fun NekoVideoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    enableBlur: Boolean = true,
    enableFloatingBottomBar: Boolean = true,
    enableFloatingBottomBarBlur: Boolean = true,
    keyColorArgb: Int = 0,
    colorStyle: String = "TonalSpot",
    colorSpec: String = "SPEC_2025",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Match KernelSU MiuixKernelSUTheme: non-Monet → System/Light/Dark with null keyColor
    // so miuix uses built-in Xiaomi blue. Monet only when dynamic color is on.
    val colorSchemeMode = when {
        dynamicColor && themeMode == ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
        dynamicColor && themeMode == ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        dynamicColor && themeMode == ThemeMode.DARK -> ColorSchemeMode.MonetDark
        themeMode == ThemeMode.SYSTEM -> ColorSchemeMode.System
        themeMode == ThemeMode.LIGHT -> ColorSchemeMode.Light
        themeMode == ThemeMode.DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val resolvedKeyColor: Color? = when {
        keyColorArgb != 0 -> Color(keyColorArgb)
        dynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context).primary
            else dynamicLightColorScheme(context).primary
        }
        else -> null
    }

    val paletteStyle = runCatching {
        ThemePaletteStyle.valueOf(colorStyle)
    }.getOrDefault(ThemePaletteStyle.TonalSpot)
    val themeColorSpec = if (colorSpec == "SPEC_2021") {
        ThemeColorSpec.Spec2021
    } else {
        ThemeColorSpec.Spec2025
    }

    val controller = remember(colorSchemeMode, resolvedKeyColor, darkTheme, paletteStyle, themeColorSpec) {
        ThemeController(
            colorSchemeMode,
            keyColor = resolvedKeyColor,
            isDark = darkTheme,
            paletteStyle = paletteStyle,
            colorSpec = themeColorSpec,
        )
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalEnableBlur provides enableBlur,
        LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides enableFloatingBottomBarBlur,
    ) {
        MiuixTheme(controller = controller) {
            LaunchedEffect(darkTheme) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            val miuixScheme = MiuixTheme.colorScheme
            val materialScheme = when {
                darkTheme && amoledDark -> FallbackDark.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    primary = miuixScheme.primary,
                )
                darkTheme -> FallbackDark.copy(primary = miuixScheme.primary)
                else -> FallbackLight.copy(primary = miuixScheme.primary)
            }
            CompositionLocalProvider(
                LocalContentColor provides miuixScheme.onBackground,
            ) {
                MaterialTheme(
                    colorScheme = materialScheme,
                    typography = NekoTypography,
                    content = content,
                )
            }
        }
    }
}
