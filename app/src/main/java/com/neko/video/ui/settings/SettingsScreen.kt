package com.neko.video.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko.video.BuildConfig
import com.neko.video.R
import com.neko.video.asNekoVideoApp
import com.neko.video.model.AspectRatioMode
import com.neko.video.model.ThemeMode
import com.neko.video.model.UserAgentPreset
import com.neko.video.player.cache.MediaCache
import com.neko.video.settings.SettingsState
import com.neko.video.ui.components.ListPickerPreference
import com.neko.video.ui.components.MiuixConfirmDialog
import com.neko.video.ui.components.MiuixPage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.asNekoVideoApp().container
    val settings = container.settings.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsState(),
    )

    fun update(transform: (SettingsState) -> SettingsState) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.mediaRepository.clearHistory() }
    }

    fun clearCache() {
        MediaCache.clear(getApplication())
    }
}

@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.settings.collectAsStateWithLifecycle()
    var showClearHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    val aspectModes = listOf(
        AspectRatioMode.FIT,
        AspectRatioMode.FILL,
        AspectRatioMode.CROP,
        AspectRatioMode.STRETCH,
        AspectRatioMode.RATIO_16_9,
        AspectRatioMode.RATIO_4_3,
    )
    val aspectLabels = listOf(
        stringResource(R.string.aspect_fit),
        stringResource(R.string.aspect_fill),
        stringResource(R.string.aspect_crop),
        stringResource(R.string.aspect_stretch),
        stringResource(R.string.aspect_16_9),
        stringResource(R.string.aspect_4_3),
    )

    val uaItems = listOf(
        stringResource(R.string.settings_ua_app_default),
        stringResource(R.string.settings_ua_android),
        stringResource(R.string.settings_ua_chrome),
        stringResource(R.string.settings_ua_custom),
    )
    val uaPresets = listOf(
        UserAgentPreset.APP_DEFAULT,
        UserAgentPreset.ANDROID,
        UserAgentPreset.CHROME_ANDROID,
        UserAgentPreset.CUSTOM,
    )
    val seekItems = listOf("5s", "10s", "15s", "30s", "60s")
    val seekValues = listOf(5, 10, 15, 30, 60)
    val languageCodes = listOf("system", "none", "zh", "en", "ja")
    val languageLabels = listOf(
        stringResource(R.string.settings_language_system),
        stringResource(R.string.settings_language_none),
        stringResource(R.string.settings_language_zh),
        stringResource(R.string.settings_language_en),
        stringResource(R.string.settings_language_ja),
    )

    val layoutDirection = LocalLayoutDirection.current
    val mainBottomPadding = LocalMainBottomContentPadding.current
    MiuixPage(title = stringResource(R.string.settings_title)) { padding, nestedScrollConnection ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(nestedScrollConnection)
                .padding(horizontal = 12.dp),
            // Top/bottom as contentPadding so the large title can collapse over content (KernelSU).
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = mainBottomPadding + 24.dp,
            ),
            overscrollEffect = null,
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel(stringResource(R.string.settings_playback))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_auto_resume),
                        checked = state.autoResume,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(autoResume = checked) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_auto_play_next),
                        checked = state.autoPlayNext,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(autoPlayNext = checked) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_keep_screen_on),
                        checked = state.keepScreenOn,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(keepScreenOn = checked) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_background_play),
                        checked = state.backgroundPlay,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(backgroundPlay = checked) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_pip),
                        checked = state.pipEnabled,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(pipEnabled = checked) }
                        },
                    )
                    ListPickerPreference(
                        title = stringResource(R.string.settings_seek_seconds),
                        items = seekItems,
                        selectedIndex = seekValues.indexOf(state.seekSeconds).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val seconds = seekValues.getOrNull(index) ?: return@ListPickerPreference
                            viewModel.update { it.copy(seekSeconds = seconds) }
                        },
                    )
                    PreferenceSlider(
                        title = stringResource(R.string.settings_default_speed),
                        valueText = "%.2fx".format(state.defaultSpeed),
                        value = state.defaultSpeed,
                        valueRange = 0.25f..2.0f,
                        onValueChange = { value ->
                            viewModel.update { it.copy(defaultSpeed = value) }
                        },
                    )
                    PreferenceSlider(
                        title = stringResource(R.string.settings_completion_threshold),
                        valueText = stringResource(
                            R.string.settings_completion_threshold_summary,
                            state.completionThresholdPercent,
                        ),
                        value = state.completionThresholdPercent.toFloat(),
                        valueRange = 1f..15f,
                        onValueChange = { value ->
                            viewModel.update {
                                it.copy(completionThresholdPercent = value.toInt())
                            }
                        },
                    )
                    ListPickerPreference(
                        title = stringResource(R.string.settings_default_aspect),
                        items = aspectLabels,
                        selectedIndex = aspectModes.indexOf(state.defaultAspectRatio).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val mode = aspectModes.getOrNull(index) ?: return@ListPickerPreference
                            viewModel.update { it.copy(defaultAspectRatio = mode) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.settings_subtitle))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListPickerPreference(
                        title = stringResource(R.string.settings_subtitle_language),
                        items = languageLabels,
                        selectedIndex = languageCodes.indexOf(state.subtitleLanguage).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val code = languageCodes.getOrNull(index) ?: return@ListPickerPreference
                            viewModel.update { it.copy(subtitleLanguage = code) }
                        },
                    )
                    PreferenceSlider(
                        title = stringResource(R.string.settings_subtitle_size),
                        valueText = "%.1fx".format(state.subtitleSize),
                        value = state.subtitleSize,
                        valueRange = 0.75f..2.0f,
                        onValueChange = { value ->
                            viewModel.update { it.copy(subtitleSize = value) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.settings_audio))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListPickerPreference(
                        title = stringResource(R.string.settings_audio_language),
                        items = languageLabels,
                        selectedIndex = languageCodes.indexOf(state.audioLanguage).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val code = languageCodes.getOrNull(index) ?: return@ListPickerPreference
                            viewModel.update { it.copy(audioLanguage = code) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_audio_focus),
                        checked = state.audioFocusEnabled,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(audioFocusEnabled = checked) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.settings_network))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListPickerPreference(
                        title = stringResource(R.string.settings_default_user_agent),
                        items = uaItems,
                        selectedIndex = uaPresets.indexOf(state.userAgentPreset).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val preset = uaPresets.getOrNull(index) ?: return@ListPickerPreference
                            viewModel.update { it.copy(userAgentPreset = preset) }
                        },
                    )
                    if (state.userAgentPreset == UserAgentPreset.CUSTOM) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_custom_user_agent),
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = state.customUserAgent,
                                onValueChange = { value ->
                                    viewModel.update { it.copy(customUserAgent = value) }
                                },
                                label = stringResource(R.string.settings_custom_user_agent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    SwitchPreference(
                        title = stringResource(R.string.settings_mobile_data_warning),
                        checked = state.mobileDataWarning,
                        onCheckedChange = { checked ->
                            viewModel.update { it.copy(mobileDataWarning = checked) }
                        },
                    )
                    PreferenceSlider(
                        title = stringResource(R.string.settings_network_timeout),
                        valueText = "${state.networkTimeoutSeconds}s",
                        value = state.networkTimeoutSeconds.toFloat(),
                        valueRange = 5f..60f,
                        onValueChange = { value ->
                            viewModel.update { it.copy(networkTimeoutSeconds = value.toInt()) }
                        },
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_default_headers),
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = state.defaultHeadersText,
                            onValueChange = { value ->
                                viewModel.update { it.copy(defaultHeadersText = value) }
                            },
                            label = stringResource(R.string.settings_default_headers_hint),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.settings_appearance))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_theme),
                        summary = when (state.themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        onClick = onOpenTheme,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.settings_other))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_history),
                        onClick = { showClearHistory = true },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_cache),
                        summary = if (cacheCleared) {
                            stringResource(R.string.settings_cache_cleared)
                        } else {
                            ""
                        },
                        onClick = {
                            viewModel.clearCache()
                            cacheCleared = true
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(
                            R.string.settings_version,
                            BuildConfig.VERSION_NAME,
                        ),
                        onClick = { showAbout = true },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_about_repo),
                        summary = stringResource(R.string.settings_github_url),
                        onClick = {
                            val url = context.getString(R.string.settings_github_url)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    MiuixConfirmDialog(
        show = showClearHistory,
        title = stringResource(R.string.settings_clear_history),
        summary = stringResource(R.string.history_clear_confirm),
        confirmText = stringResource(R.string.action_ok),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = {
            viewModel.clearHistory()
            showClearHistory = false
        },
        onDismiss = { showClearHistory = false },
    )

    MiuixConfirmDialog(
        show = showAbout,
        title = stringResource(R.string.settings_about),
        summary = stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME),
        confirmText = stringResource(R.string.action_ok),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = { showAbout = false },
        onDismiss = { showAbout = false },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun PreferenceSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = title)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = valueText,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
