package com.neko.video.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.player.PlaybackSession
import com.neko.video.remote.RemoteProtocol
import com.neko.video.ui.component.bottombar.BottomBarDestination
import com.neko.video.ui.component.bottombar.MainBottomBar
import com.neko.video.ui.favorites.FavoritesScreen
import com.neko.video.ui.history.HistoryScreen
import com.neko.video.ui.library.LibraryScreen
import com.neko.video.ui.network.NetworkScreen
import com.neko.video.ui.network.OpenUrlScreen
import com.neko.video.ui.network.RemoteBrowserScreen
import com.neko.video.ui.network.RemoteEditorScreen
import com.neko.video.ui.network.WebDavBrowserScreen
import com.neko.video.ui.network.WebDavEditorScreen
import com.neko.video.ui.player.PlayerScreen
import com.neko.video.ui.settings.SettingsScreen
import com.neko.video.ui.settings.ThemeSettingsScreen
import com.neko.video.ui.theme.LocalEnableBlur
import com.neko.video.ui.theme.LocalEnableFloatingBottomBar
import com.neko.video.ui.theme.LocalEnableFloatingBottomBarBlur
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import com.neko.video.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

object Routes {
    const val MAIN = "main"
    const val THEME_SETTINGS = "theme_settings"
    const val OPEN_URL = "open_url"

    fun player(media: MediaItemInfo): String {
        val uri = android.net.Uri.encode(media.uri)
        val title = android.net.Uri.encode(media.name)
        return "player/$uri/$title/${media.sourceType.name}?mediaId=${android.net.Uri.encode(media.id)}" +
            (media.serverId?.let { "&serverId=${android.net.Uri.encode(it)}" } ?: "")
    }

    fun webDavEditor(serverId: String? = null): String {
        return if (serverId == null) "webdav_editor" else "webdav_editor?serverId=$serverId"
    }

    fun webDavBrowser(serverId: String, path: String = "/"): String {
        return "webdav_browser/$serverId?path=${android.net.Uri.encode(path)}"
    }

    fun remoteEditor(protocol: RemoteProtocol, serverId: String? = null): String {
        return if (serverId == null) {
            "remote_editor/${protocol.name}"
        } else {
            "remote_editor/${protocol.name}?serverId=$serverId"
        }
    }

    fun remoteBrowser(protocol: RemoteProtocol, serverId: String, path: String = "/"): String {
        return "remote_browser/${protocol.name}/$serverId?path=${android.net.Uri.encode(path)}"
    }
}

@Composable
fun NekoVideoAppNav(
    pendingPlayUri: String? = null,
    onPendingPlayConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    fun playMedia(media: MediaItemInfo, playlist: List<MediaItemInfo> = listOf(media)) {
        val index = playlist.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
        PlaybackSession.begin(playlist.ifEmpty { listOf(media) }, index)
        val alreadyOnPlayer = navController.currentBackStackEntry
            ?.destination
            ?.route
            ?.startsWith("player/") == true
        navController.navigate(Routes.player(media)) {
            // Only tear down an existing player; keep WebDAV/Remote browser under the stack.
            if (alreadyOnPlayer) {
                popUpTo(Routes.MAIN) { inclusive = false }
            }
            launchSingleTop = true
        }
    }

    LaunchedEffect(pendingPlayUri) {
        val uri = pendingPlayUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val media = MediaItemInfo(
            id = "intent_${uri.hashCode()}",
            name = android.net.Uri.parse(uri).lastPathSegment ?: uri,
            uri = uri,
            sourceType = when {
                uri.startsWith("http", ignoreCase = true) -> SourceType.HTTP
                else -> SourceType.LOCAL
            },
        )
        playMedia(media)
        onPendingPlayConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAIN) {
            MainTabsScreen(
                onPlay = { playMedia(it) },
                onOpenTheme = { navController.navigate(Routes.THEME_SETTINGS) },
                onAddServer = { navController.navigate(Routes.webDavEditor()) },
                onEditServer = { id -> navController.navigate(Routes.webDavEditor(id)) },
                onBrowseServer = { id -> navController.navigate(Routes.webDavBrowser(id)) },
                onBrowseWebDav = { serverId, path ->
                    navController.navigate(Routes.webDavBrowser(serverId, path))
                },
                onAddRemote = { protocol -> navController.navigate(Routes.remoteEditor(protocol)) },
                onEditRemote = { protocol, id ->
                    navController.navigate(Routes.remoteEditor(protocol, id))
                },
                onBrowseRemote = { protocol, id ->
                    navController.navigate(Routes.remoteBrowser(protocol, id))
                },
                onBrowseRemotePath = { protocol, id, path ->
                    navController.navigate(Routes.remoteBrowser(protocol, id, path))
                },
                onOpenUrl = { navController.navigate(Routes.OPEN_URL) },
            )
        }
        composable(Routes.THEME_SETTINGS) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.OPEN_URL) {
            OpenUrlScreen(
                onBack = { navController.popBackStack() },
                onPlay = { media ->
                    playMedia(media)
                },
            )
        }
        composable(
            route = "webdav_editor?serverId={serverId}",
            arguments = listOf(
                navArgument("serverId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            WebDavEditorScreen(
                serverId = entry.arguments?.getString("serverId"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = "webdav_browser/{serverId}?path={path}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = "/"
                },
            ),
        ) { entry ->
            val serverId = entry.arguments?.getString("serverId").orEmpty()
            val path = entry.arguments?.getString("path") ?: "/"
            WebDavBrowserScreen(
                serverId = serverId,
                path = path,
                onBack = { navController.popBackStack() },
                onOpenFolder = {},
                onPlay = { media, playlist -> playMedia(media, playlist) },
            )
        }
        composable(
            route = "remote_editor/{protocol}?serverId={serverId}",
            arguments = listOf(
                navArgument("protocol") { type = NavType.StringType },
                navArgument("serverId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val protocol = runCatching {
                RemoteProtocol.valueOf(
                    entry.arguments?.getString("protocol") ?: RemoteProtocol.SMB.name,
                )
            }.getOrDefault(RemoteProtocol.SMB)
            RemoteEditorScreen(
                protocol = protocol,
                serverId = entry.arguments?.getString("serverId"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = "remote_browser/{protocol}/{serverId}?path={path}",
            arguments = listOf(
                navArgument("protocol") { type = NavType.StringType },
                navArgument("serverId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = "/"
                },
            ),
        ) { entry ->
            val protocol = runCatching {
                RemoteProtocol.valueOf(
                    entry.arguments?.getString("protocol") ?: RemoteProtocol.SMB.name,
                )
            }.getOrDefault(RemoteProtocol.SMB)
            val serverId = entry.arguments?.getString("serverId").orEmpty()
            val path = entry.arguments?.getString("path") ?: "/"
            RemoteBrowserScreen(
                protocol = protocol,
                serverId = serverId,
                path = path,
                onBack = { navController.popBackStack() },
                onOpenFolder = {},
                onPlay = { media, playlist -> playMedia(media, playlist) },
            )
        }
        composable(
            route = "player/{uri}/{title}/{sourceType}?mediaId={mediaId}&serverId={serverId}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("sourceType") { type = NavType.StringType },
                navArgument("mediaId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("serverId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val uri = android.net.Uri.decode(entry.arguments?.getString("uri").orEmpty())
            val title = android.net.Uri.decode(entry.arguments?.getString("title").orEmpty())
            val sourceType = entry.arguments?.getString("sourceType") ?: SourceType.LOCAL.name
            val mediaId = entry.arguments?.getString("mediaId").orEmpty()
            val serverId = entry.arguments?.getString("serverId")
            PlayerScreen(
                uri = uri,
                title = title,
                sourceType = sourceType,
                mediaId = mediaId,
                serverId = serverId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Floating glass bar overlays the pager (content draws underneath).
 * Classic NavigationBar stays in Scaffold bottomBar.
 */
@Composable
private fun MainTabsScreen(
    onPlay: (MediaItemInfo) -> Unit,
    onOpenTheme: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onBrowseServer: (String) -> Unit,
    onBrowseWebDav: (String, String) -> Unit,
    onAddRemote: (RemoteProtocol) -> Unit,
    onEditRemote: (RemoteProtocol, String) -> Unit,
    onBrowseRemote: (RemoteProtocol, String) -> Unit,
    onBrowseRemotePath: (RemoteProtocol, String, String) -> Unit,
    onOpenUrl: () -> Unit,
) {
    val destinations = BottomBarDestination.entries
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedPage.coerceIn(0, destinations.lastIndex),
        pageCount = { destinations.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (selectedPage != page) selectedPage = page
        }
    }

    // After process death, selectedPage is restored but pager may still need an explicit sync.
    LaunchedEffect(Unit) {
        val target = selectedPage.coerceIn(0, destinations.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    fun animateToPage(index: Int) {
        val clamped = index.coerceIn(0, destinations.lastIndex)
        selectedPage = clamped
        scope.launch {
            pagerState.animateScrollToPage(clamped)
        }
    }

    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    // FloatingBottomBar height(64) + outer gap(12) + gesture/nav inset.
    val floatingBarScrollInset = 64.dp + 12.dp + navigationBarPadding

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        bottomBar = {
            // Only classic bar uses the slot. Floating pill must overlay or Scaffold reserves a band.
            if (!enableFloatingBottomBar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MainBottomBar(
                        selectedPage = { selectedPage },
                        onSelectPage = ::animateToPage,
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        },
    ) { innerPadding ->
        val mainBottomContentPadding = if (enableFloatingBottomBar) {
            floatingBarScrollInset
        } else {
            innerPadding.calculateBottomPadding()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalMainBottomContentPadding provides mainBottomContentPadding,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
                        )
                        .then(
                            if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        when (page) {
                            0 -> LibraryScreen(onPlay = onPlay)
                            1 -> NetworkScreen(
                                onAddServer = onAddServer,
                                onEditServer = onEditServer,
                                onBrowseServer = onBrowseServer,
                                onAddRemote = onAddRemote,
                                onEditRemote = onEditRemote,
                                onBrowseRemote = onBrowseRemote,
                                onOpenUrl = onOpenUrl,
                                onPlay = onPlay,
                            )
                            2 -> HistoryScreen(onPlay = onPlay)
                            3 -> FavoritesScreen(
                                onPlay = onPlay,
                                onBrowseWebDav = onBrowseWebDav,
                                onBrowseRemote = onBrowseRemotePath,
                            )
                            4 -> SettingsScreen(onOpenTheme = onOpenTheme)
                        }
                    }
                }
            }

            if (enableFloatingBottomBar) {
                MainBottomBar(
                    selectedPage = { selectedPage },
                    onSelectPage = ::animateToPage,
                    blurBackdrop = blurBackdrop,
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
