package com.neko.video.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.video.R
import com.neko.video.ui.component.FloatingBottomBar
import com.neko.video.ui.component.FloatingBottomBarItem
import com.neko.video.ui.theme.LocalEnableFloatingBottomBar
import com.neko.video.ui.theme.LocalEnableFloatingBottomBarBlur
import com.neko.video.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Library(R.string.nav_library, Icons.Outlined.VideoLibrary),
    Network(R.string.nav_network, Icons.Outlined.Cloud),
    History(R.string.nav_history, Icons.Outlined.History),
    Favorites(R.string.nav_favorites, Icons.Outlined.FavoriteBorder),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

/**
 * KernelSU-style bottom bar: classic [NavigationBar] or floating glass pill.
 */
@Composable
fun MainBottomBar(
    selectedPage: () -> Int,
    onSelectPage: (Int) -> Unit,
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val destinations = BottomBarDestination.entries
    val currentPage = selectedPage()

    if (!enableFloatingBottomBar) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = index == currentPage,
                        onClick = { onSelectPage(index) },
                        icon = destination.icon,
                        label = stringResource(destination.label),
                    )
                }
            }
        }
    } else {
        FloatingBottomBar(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(
                    bottom = 12.dp + WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding(),
                ),
            // Must read Compose State inside this lambda (KernelSU MainPagerState.selectedPage).
            selectedIndex = selectedPage,
            onSelected = onSelectPage,
            backdrop = backdrop,
            tabsCount = destinations.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) {
            destinations.forEachIndexed { index, destination ->
                FloatingBottomBarItem(
                    onClick = { onSelectPage(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 70.dp),
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.label),
                    )
                    Text(
                        text = stringResource(destination.label),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
