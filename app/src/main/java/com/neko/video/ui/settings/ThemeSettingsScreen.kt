package com.neko.video.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko.video.R
import com.neko.video.model.ThemeMode
import com.neko.video.ui.component.GlassFloatingTabRow
import com.neko.video.ui.components.ListPickerPreference
import com.neko.video.ui.theme.LocalEnableBlur
import com.neko.video.ui.theme.keyColorOptions
import com.neko.video.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val surfaceColor = colorScheme.surface
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val barBackdrop = if (enableBlur && isRenderEffectSupported()) contentBackdrop else null
    val barColor = if (barBackdrop != null) Color.Transparent else surfaceColor

    val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val themeLabels = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark),
    )
    val selectedIndex = themeModes.indexOf(state.themeMode).coerceAtLeast(0)

    val colorLabels = listOf(
        stringResource(R.string.settings_key_color_default),
        stringResource(R.string.color_red),
        stringResource(R.string.color_pink),
        stringResource(R.string.color_purple),
        stringResource(R.string.color_deep_purple),
        stringResource(R.string.color_indigo),
        stringResource(R.string.color_blue),
        stringResource(R.string.color_cyan),
        stringResource(R.string.color_teal),
        stringResource(R.string.color_green),
        stringResource(R.string.color_yellow),
        stringResource(R.string.color_amber),
        stringResource(R.string.color_orange),
        stringResource(R.string.color_brown),
        stringResource(R.string.color_blue_grey),
        stringResource(R.string.color_sakura),
    )
    val colorValues = listOf(0) + keyColorOptions
    val styles = ThemePaletteStyle.entries
    val styleNames = styles.map { it.name }

    Scaffold(
        topBar = {
            BlurredBar(barBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_theme),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_back),
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        // Same pattern as Library: blur source is the scrolling content;
        // the glass tab row floats as a sibling so drawBackdrop never samples itself
        // (self-sampling can SIGSEGV on the RenderThread).
        val floatingTabInset = 56.dp + 16.dp
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + floatingTabInset,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
            ) {
                item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_dynamic_color),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        checked = state.dynamicColor,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(dynamicColor = checked) }
                        },
                    )
                    AnimatedVisibility(visible = state.dynamicColor) {
                        ListPickerPreference(
                            title = stringResource(R.string.settings_key_color),
                            items = colorLabels,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Colorize,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            selectedIndex = colorValues.indexOf(state.keyColor).takeIf { it >= 0 } ?: 0,
                            onSelectedIndexChange = { index ->
                                val color = colorValues.getOrNull(index) ?: return@ListPickerPreference
                                viewModel.update { it.copy(keyColor = color) }
                            },
                        )
                    }
                    AnimatedVisibility(visible = state.dynamicColor && state.keyColor != 0) {
                        ListPickerPreference(
                            title = stringResource(R.string.settings_color_style),
                            items = styleNames,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Style,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            selectedIndex = styles.indexOfFirst { it.name == state.colorStyle }
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val style = styles.getOrNull(index) ?: return@ListPickerPreference
                                viewModel.update { it.copy(colorStyle = style.name) }
                            },
                        )
                    }
                    AnimatedVisibility(visible = state.dynamicColor && state.keyColor != 0) {
                        ListPickerPreference(
                            title = stringResource(R.string.settings_color_spec),
                            items = listOf("SPEC_2025", "SPEC_2021"),
                            startAction = {
                                Icon(
                                    Icons.Rounded.DesignServices,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            selectedIndex = if (state.colorSpec == "SPEC_2021") 1 else 0,
                            onSelectedIndexChange = { index ->
                                viewModel.update {
                                    it.copy(colorSpec = if (index == 1) "SPEC_2021" else "SPEC_2025")
                                }
                            },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.settings_amoled_dark),
                        summary = stringResource(R.string.settings_amoled_dark_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.DarkMode,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        checked = state.amoledDark,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(amoledDark = checked) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_blur),
                            summary = stringResource(R.string.settings_enable_blur_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            checked = state.enableBlur,
                            onCheckedChange = { checked ->
                                viewModel.update { it.copy(enableBlur = checked) }
                            },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.settings_floating_bottom_bar),
                        summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                        startAction = {
                            Icon(
                                Icons.Rounded.CallToAction,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        checked = state.enableFloatingBottomBar,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(enableFloatingBottomBar = checked) }
                        },
                    )
                    AnimatedVisibility(
                        visible = state.enableFloatingBottomBar &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_glass),
                            summary = stringResource(R.string.settings_enable_glass_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.WaterDrop,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            checked = state.enableFloatingBottomBarBlur,
                            onCheckedChange = { checked ->
                                viewModel.update { it.copy(enableFloatingBottomBarBlur = checked) }
                            },
                        )
                    }
                }
                }
            }

            GlassFloatingTabRow(
                tabs = themeLabels,
                selectedTabIndex = selectedIndex,
                onTabSelected = { index ->
                    viewModel.update { it.copy(themeMode = themeModes[index]) }
                },
                backdrop = contentBackdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 8.dp),
            )
        }
    }
}
