package com.neko.video.ui.network

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko.video.R
import com.neko.video.model.MediaItemInfo
import com.neko.video.remote.RemoteProtocol
import com.neko.video.remote.RemoteServerConfig
import com.neko.video.ui.components.EmptyState
import com.neko.video.ui.components.LoadingState
import com.neko.video.ui.components.MiuixConfirmDialog
import com.neko.video.ui.components.PrimaryButton
import com.neko.video.ui.components.SecondaryButton
import com.neko.video.ui.components.SettingsGroupCard
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import com.neko.video.util.FormatUtils
import com.neko.video.webdav.WebDavServerConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun NetworkScreen(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onBrowseServer: (String) -> Unit,
    onAddRemote: (RemoteProtocol) -> Unit,
    onEditRemote: (RemoteProtocol, String) -> Unit,
    onBrowseRemote: (RemoteProtocol, String) -> Unit,
    onOpenUrl: () -> Unit,
    onPlay: (MediaItemInfo) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val remoteServers by viewModel.remoteServers.collectAsStateWithLifecycle()
    val recentUrls by viewModel.recentUrls.collectAsStateWithLifecycle()
    val bottomPadding = LocalMainBottomContentPadding.current
    val smbServers = remoteServers.filter { it.protocol == RemoteProtocol.SMB }
    val sftpServers = remoteServers.filter { it.protocol == RemoteProtocol.SFTP }
    val ftpServers = remoteServers.filter { it.protocol == RemoteProtocol.FTP }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        // Keep content under the floating glass bar; only list contentPadding reserves scroll room.
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.network_title),
                actions = {
                    IconButton(onClick = onAddServer) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.network_add_server),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            item {
                SettingsGroupCard {
                    ArrowPreference(
                        title = stringResource(R.string.network_http_url),
                        summary = stringResource(R.string.network_url_hint),
                        startAction = {
                            Icon(
                                Icons.Outlined.Link,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        onClick = onOpenUrl,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.network_webdav),
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            if (servers.isEmpty()) {
                item {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.network_add_server),
                            summary = stringResource(R.string.network_servers_empty_hint),
                            onClick = onAddServer,
                        )
                    }
                }
            } else {
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        title = server.name,
                        summary = server.url,
                        onBrowse = { onBrowseServer(server.id) },
                        onEdit = { onEditServer(server.id) },
                        onDelete = { viewModel.deleteServer(server.id) },
                    )
                }
            }
            item {
                ProtocolSectionHeader(
                    title = stringResource(R.string.network_smb),
                    addLabel = stringResource(R.string.network_add_smb),
                    onAdd = { onAddRemote(RemoteProtocol.SMB) },
                )
            }
            items(smbServers, key = { "smb_${it.id}" }) { server ->
                RemoteServerCard(
                    server = server,
                    onBrowse = { onBrowseRemote(server.protocol, server.id) },
                    onEdit = { onEditRemote(server.protocol, server.id) },
                    onDelete = { viewModel.deleteRemoteServer(server.id) },
                )
            }
            item {
                ProtocolSectionHeader(
                    title = stringResource(R.string.network_sftp),
                    addLabel = stringResource(R.string.network_add_sftp),
                    onAdd = { onAddRemote(RemoteProtocol.SFTP) },
                )
            }
            items(sftpServers, key = { "sftp_${it.id}" }) { server ->
                RemoteServerCard(
                    server = server,
                    onBrowse = { onBrowseRemote(server.protocol, server.id) },
                    onEdit = { onEditRemote(server.protocol, server.id) },
                    onDelete = { viewModel.deleteRemoteServer(server.id) },
                )
            }
            item {
                ProtocolSectionHeader(
                    title = stringResource(R.string.network_ftp),
                    addLabel = stringResource(R.string.network_add_ftp),
                    onAdd = { onAddRemote(RemoteProtocol.FTP) },
                )
            }
            items(ftpServers, key = { "ftp_${it.id}" }) { server ->
                RemoteServerCard(
                    server = server,
                    onBrowse = { onBrowseRemote(server.protocol, server.id) },
                    onEdit = { onEditRemote(server.protocol, server.id) },
                    onDelete = { viewModel.deleteRemoteServer(server.id) },
                )
            }
            if (recentUrls.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.network_recent_urls),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                items(recentUrls, key = { it.url }) { recent ->
                    SettingsGroupCard {
                        ArrowPreference(
                            title = recent.title.ifBlank { recent.url },
                            summary = recent.url,
                            onClick = { onPlay(viewModel.mediaFromRecent(recent)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolSectionHeader(
    title: String,
    addLabel: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = addLabel)
        }
    }
}

@Composable
private fun RemoteServerCard(
    server: RemoteServerConfig,
    onBrowse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ServerCard(
        title = server.name,
        summary = server.displayEndpoint(),
        onBrowse = onBrowse,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun ServerCard(
    title: String,
    summary: String,
    onBrowse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SettingsGroupCard {
        ArrowPreference(
            title = title,
            summary = summary,
            startAction = {
                Icon(
                    Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            endActions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null)
                }
            },
            onClick = onBrowse,
        )
    }
    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.action_edit),
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.action_delete),
                        onClick = {
                            showMenu = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }
    }
    MiuixConfirmDialog(
        show = confirmDelete,
        title = stringResource(R.string.action_delete),
        summary = stringResource(R.string.network_delete_confirm),
        confirmText = stringResource(R.string.action_ok),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = {
            confirmDelete = false
            onDelete()
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
fun OpenUrlScreen(
    onBack: () -> Unit,
    onPlay: (MediaItemInfo) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    var url by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.network_http_url),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.network_url_label),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = userAgent,
                onValueChange = { userAgent = it },
                label = stringResource(R.string.settings_custom_user_agent),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = headers,
                onValueChange = { headers = it },
                label = stringResource(R.string.settings_default_headers_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            PrimaryButton(
                text = stringResource(R.string.player_play),
                onClick = {
                    viewModel.createHttpMedia(url, userAgent, headers)?.let(onPlay)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun WebDavEditorScreen(
    serverId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    val editor by viewModel.editorState.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) {
        viewModel.loadEditor(serverId)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(
                    if (serverId == null) R.string.network_add_server else R.string.network_edit_server,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = editor.name,
                onValueChange = { viewModel.updateEditor { copy(name = it) } },
                label = stringResource(R.string.server_name),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.url,
                onValueChange = { viewModel.updateEditor { copy(url = it) } },
                label = stringResource(R.string.server_url),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.username,
                onValueChange = { viewModel.updateEditor { copy(username = it) } },
                label = stringResource(R.string.server_username),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.password,
                onValueChange = { viewModel.updateEditor { copy(password = it) } },
                label = stringResource(R.string.server_password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.userAgent,
                onValueChange = { viewModel.updateEditor { copy(userAgent = it) } },
                label = stringResource(R.string.settings_custom_user_agent),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.headersText,
                onValueChange = { viewModel.updateEditor { copy(headersText = it) } },
                label = stringResource(R.string.settings_default_headers_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )
            editor.message?.let {
                Text(text = it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.network_test_connection),
                    onClick = { viewModel.testEditorConnection() },
                    enabled = !editor.testing && !editor.saving,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.saveEditor(onSaved) },
                    enabled = !editor.testing && !editor.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun WebDavBrowserScreen(
    serverId: String,
    path: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onPlay: (MediaItemInfo, List<MediaItemInfo>) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    val context = LocalContext.current
    val browser by viewModel.browserState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    // Stay on one screen while browsing folders — avoids NavBackStackEntry/ViewModel
    // recreation flash on every directory enter.
    var currentPath by rememberSaveable(serverId) { mutableStateOf(path) }
    LaunchedEffect(path) {
        if (path != currentPath) currentPath = path
    }

    val displayName = remember(serverId, servers, browser.serverName) {
        servers.firstOrNull { it.id == serverId }?.name
            ?.takeIf { it.isNotBlank() }
            ?: browser.serverName.takeIf { it.isNotBlank() }
            ?: ""
    }
    val titleFallback = stringResource(R.string.network_webdav)
    val titleName = displayName.ifBlank { titleFallback }

    fun navigateTo(nextPath: String) {
        if (nextPath == currentPath) {
            viewModel.loadBrowser(serverId, nextPath)
        } else {
            currentPath = nextPath
        }
    }

    LaunchedEffect(serverId, currentPath) {
        viewModel.loadBrowser(serverId, currentPath)
    }

    val crumbs = remember(titleName, browser.serverBaseUrl, currentPath) {
        buildWebDavBreadcrumbs(
            serverName = titleName,
            serverBaseUrl = browser.serverBaseUrl,
            currentPath = currentPath,
        )
    }

    val atRoot = isWebDavRootPath(browser.serverBaseUrl, currentPath)
    BackHandler {
        if (atRoot) {
            onBack()
        } else {
            val parent = parentWebDavPath(browser.serverBaseUrl, currentPath)
            navigateTo(parent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = titleName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadBrowser(serverId, currentPath) }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.action_retry),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PathBreadcrumbBar(
                crumbs = crumbs,
                onCrumbClick = { navigateTo(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
            if (browser.loading && browser.items.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MiuixTheme.colorScheme.primary,
                    trackColor = MiuixTheme.colorScheme.surfaceContainer,
                )
            }
            SwitchPreference(
                title = stringResource(R.string.network_filter_videos),
                checked = browser.videosOnly,
                onCheckedChange = { viewModel.setVideosOnly(it) },
            )
            when {
                browser.loading && browser.items.isEmpty() -> LoadingState(
                    message = stringResource(R.string.network_loading),
                )
                browser.error != null && browser.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.error_connection),
                    message = browser.error.orEmpty(),
                    action = {
                        PrimaryButton(
                            text = stringResource(R.string.action_retry),
                            onClick = { viewModel.loadBrowser(serverId, currentPath) },
                        )
                    },
                )
                browser.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.network_folder_empty),
                    message = stringResource(R.string.network_folder_empty_hint),
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(browser.items, key = { it.id }) { item ->
                            SettingsGroupCard {
                                ArrowPreference(
                                    title = item.name,
                                    summary = if (!item.isDirectory && item.size > 0) {
                                        FormatUtils.formatSize(item.size)
                                    } else {
                                        ""
                                    },
                                    startAction = {
                                        Icon(
                                            imageVector = if (item.isDirectory) {
                                                Icons.Outlined.Folder
                                            } else {
                                                Icons.Outlined.VideoFile
                                            },
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(end = 6.dp),
                                        )
                                    },
                                    endActions = {
                                        IconButton(
                                            onClick = {
                                                viewModel.toggleFavorite(item) { added ->
                                                    Toast.makeText(
                                                        context,
                                                        if (added) {
                                                            context.getString(R.string.favorite_added)
                                                        } else {
                                                            context.getString(R.string.favorite_removed)
                                                        },
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Outlined.FavoriteBorder,
                                                contentDescription = stringResource(R.string.favorite_add),
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (item.isDirectory) {
                                            navigateTo(item.uri)
                                        } else {
                                            val videos = browser.items.filter { !it.isDirectory }
                                            onPlay(item, videos)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isWebDavRootPath(serverBaseUrl: String, path: String): Boolean {
    val normalized = path.trim().trimEnd('/').ifBlank { "/" }
    if (normalized == "/" || normalized.isBlank()) return true
    val base = serverBaseUrl.trim().trimEnd('/')
    return normalized.equals(base, ignoreCase = true)
}

private fun parentWebDavPath(serverBaseUrl: String, path: String): String {
    val base = serverBaseUrl.trim().trimEnd('/')
    val raw = path.trim().trimEnd('/')
    if (raw.isEmpty() || raw == "/" || raw.equals(base, ignoreCase = true)) return "/"
    val parent = raw.substringBeforeLast('/', missingDelimiterValue = "")
    return when {
        parent.isBlank() || parent == "http:" || parent == "https:" -> "/"
        parent.equals(base, ignoreCase = true) -> "/"
        parent.startsWith("http://", ignoreCase = true) ||
            parent.startsWith("https://", ignoreCase = true) -> parent
        else -> parent.ifBlank { "/" }
    }
}

@Composable
private fun PathBreadcrumbBar(
    crumbs: List<PathBreadcrumb>,
    onCrumbClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs.size, crumbs.lastOrNull()?.path) {
        scroll.scrollTo(scroll.maxValue)
    }
    Row(
        modifier = modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val isLast = index == crumbs.lastIndex
            Text(
                text = crumb.label,
                color = if (isLast) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.primary
                },
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(enabled = !isLast) { onCrumbClick(crumb.path) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
            )
            if (!isLast) {
                Text(
                    text = " / ",
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

private fun buildWebDavBreadcrumbs(
    serverName: String,
    serverBaseUrl: String,
    currentPath: String,
): List<PathBreadcrumb> {
    val root = PathBreadcrumb(label = serverName, path = "/")
    val segments = relativeDavSegments(serverBaseUrl, currentPath)
    if (segments.isEmpty()) return listOf(root)

    val crumbs = mutableListOf(root)
    val base = serverBaseUrl.trim().trimEnd('/')
    val built = StringBuilder(base)
    for (segment in segments) {
        built.append('/').append(Uri.encode(segment))
        crumbs += PathBreadcrumb(label = segment, path = built.toString())
    }
    return crumbs
}

/**
 * Convert a browse target (absolute WebDAV href or relative path) into folder name
 * segments under [serverBaseUrl], e.g.
 * base=https://pan.neko.xin/dav + current=https://pan.neko.xin/dav/百度网盘 -> ["百度网盘"]
 */
private fun relativeDavSegments(serverBaseUrl: String, currentPath: String): List<String> {
    val base = serverBaseUrl.trim().trimEnd('/')
    val raw = currentPath.trim().trimEnd('/')
    if (raw.isEmpty() || raw == "/") return emptyList()
    if (raw.equals(base, ignoreCase = true)) return emptyList()

    val relative = when {
        raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true) -> {
            when {
                raw.startsWith(base, ignoreCase = true) -> raw.substring(base.length)
                else -> {
                    val basePath = Uri.parse(base).encodedPath.orEmpty().trimEnd('/')
                    val curPath = Uri.parse(raw).encodedPath.orEmpty().trimEnd('/')
                    if (basePath.isNotEmpty() && curPath.startsWith(basePath)) {
                        curPath.removePrefix(basePath)
                    } else {
                        curPath
                    }
                }
            }
        }
        else -> raw
    }

    return relative
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
        .map { Uri.decode(it) }
}
