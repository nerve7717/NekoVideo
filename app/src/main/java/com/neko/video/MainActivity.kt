package com.neko.video

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.neko.video.settings.SettingsState
import com.neko.video.ui.navigation.NekoVideoAppNav
import com.neko.video.ui.theme.NekoVideoTheme
import com.neko.video.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private var pendingPlayUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPlayUri = extractPlayUri(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        window.isNavigationBarContrastEnforced = false

        val app = application.asNekoVideoApp()
        setContent {
            val settings by app.container.settings.settingsFlow.collectAsStateWithLifecycle(
                initialValue = SettingsState(),
            )
            NekoVideoTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                amoledDark = settings.amoledDark,
                enableBlur = settings.enableBlur,
                enableFloatingBottomBar = settings.enableFloatingBottomBar,
                enableFloatingBottomBarBlur = settings.enableFloatingBottomBarBlur,
                keyColorArgb = settings.keyColor,
                colorStyle = settings.colorStyle,
                colorSpec = settings.colorSpec,
            ) {
                val dark = isInDarkTheme()
                // KernelSU: page chrome uses surface end-to-end (not background), avoiding
                // a hard color split under the large TopAppBar title.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (settings.amoledDark && dark) {
                                Color.Black
                            } else {
                                MiuixTheme.colorScheme.surface
                            },
                        ),
                ) {
                    NekoVideoAppNav(
                        pendingPlayUri = pendingPlayUri,
                        onPendingPlayConsumed = { pendingPlayUri = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPlayUri = extractPlayUri(intent)
    }

    private fun extractPlayUri(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null
        return intent.dataString?.takeIf { it.isNotBlank() }
    }
}
