package com.neko.video.ui.network

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko.video.R
import com.neko.video.model.MediaItemInfo
import com.neko.video.remote.RemoteProtocol
import com.neko.video.ui.components.EmptyState
import com.neko.video.ui.components.LoadingState
import com.neko.video.ui.components.PrimaryButton
import com.neko.video.ui.components.SecondaryButton
import com.neko.video.ui.components.SettingsGroupCard
import com.neko.video.util.FormatUtils
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun RemoteEditorScreen(
    protocol: RemoteProtocol,
    serverId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    val editor by viewModel.remoteEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(protocol, serverId) {
        viewModel.loadRemoteEditor(protocol, serverId)
    }
    val titleRes = when (protocol) {
        RemoteProtocol.SMB -> R.string.network_smb
        RemoteProtocol.SFTP -> R.string.network_sftp
        RemoteProtocol.FTP -> R.string.network_ftp
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(
                    if (serverId == null) R.string.network_add_server else R.string.network_edit_server,
                ) + " · " + stringResource(titleRes),
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
                onValueChange = { viewModel.updateRemoteEditor { copy(name = it) } },
                label = stringResource(R.string.server_name),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.host,
                onValueChange = { viewModel.updateRemoteEditor { copy(host = it) } },
                label = stringResource(R.string.remote_host),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.port,
                onValueChange = { viewModel.updateRemoteEditor { copy(port = it.filter { ch -> ch.isDigit() }) } },
                label = stringResource(R.string.remote_port_hint, protocol.defaultPort),
                modifier = Modifier.fillMaxWidth(),
            )
            if (protocol == RemoteProtocol.SMB) {
                TextField(
                    value = editor.share,
                    onValueChange = { viewModel.updateRemoteEditor { copy(share = it) } },
                    label = stringResource(R.string.remote_smb_share),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editor.domain,
                    onValueChange = { viewModel.updateRemoteEditor { copy(domain = it) } },
                    label = stringResource(R.string.remote_smb_domain),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextField(
                value = editor.initialPath,
                onValueChange = { viewModel.updateRemoteEditor { copy(initialPath = it) } },
                label = stringResource(R.string.remote_initial_path),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.username,
                onValueChange = { viewModel.updateRemoteEditor { copy(username = it) } },
                label = stringResource(R.string.server_username),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = editor.password,
                onValueChange = { viewModel.updateRemoteEditor { copy(password = it) } },
                label = stringResource(R.string.server_password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            editor.message?.let {
                Text(text = it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.network_test_connection),
                    onClick = { viewModel.testRemoteEditor() },
                    enabled = !editor.testing && !editor.saving,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.saveRemoteEditor(onSaved) },
                    enabled = !editor.testing && !editor.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun RemoteBrowserScreen(
    protocol: RemoteProtocol,
    serverId: String,
    path: String,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit = {},
    onPlay: (MediaItemInfo, List<MediaItemInfo>) -> Unit,
    viewModel: NetworkViewModel = viewModel(),
) {
    val context = LocalContext.current
    val browser by viewModel.remoteBrowserState.collectAsStateWithLifecycle()
    val servers by viewModel.remoteServers.collectAsStateWithLifecycle()
    val server = servers.firstOrNull { it.id == serverId }
    val titleName = server?.name
        ?.takeIf { it.isNotBlank() }
        ?: browser.serverName.ifBlank { stringResource(R.string.network_title) }
    val rootPath = normalizeRemoteRoot(server?.initialPath ?: path)

    // Keep one Nav entry and walk folders locally (same as WebDAV) so Back returns to parent.
    var currentPath by rememberSaveable(serverId, protocol.name) { mutableStateOf(path) }
    LaunchedEffect(path) {
        if (path != currentPath) currentPath = path
    }
    fun navigateTo(nextPath: String) {
        if (nextPath == currentPath) {
            viewModel.loadRemoteBrowser(protocol, serverId, nextPath)
        } else {
            currentPath = nextPath
        }
    }
    LaunchedEffect(protocol, serverId, currentPath) {
        viewModel.loadRemoteBrowser(protocol, serverId, currentPath)
    }
    val atRoot = isRemoteRootPath(currentPath, rootPath)
    BackHandler {
        if (atRoot) onBack() else navigateTo(parentRemotePath(currentPath, rootPath))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = titleName,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (atRoot) onBack() else navigateTo(parentRemotePath(currentPath, rootPath))
                        },
                    ) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRemoteBrowser(protocol, serverId, currentPath) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.network_refresh))
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
            Text(
                text = browser.path.ifBlank { currentPath },
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 2,
            )
            SwitchPreference(
                title = stringResource(R.string.network_filter_videos),
                checked = browser.videosOnly,
                onCheckedChange = { viewModel.setRemoteVideosOnly(it) },
            )
            when {
                browser.loading -> LoadingState(message = stringResource(R.string.network_loading))
                browser.error != null -> EmptyState(
                    title = stringResource(R.string.error_connection),
                    message = browser.error.orEmpty(),
                    action = {
                        PrimaryButton(
                            text = stringResource(R.string.action_retry),
                            onClick = { viewModel.loadRemoteBrowser(protocol, serverId, currentPath) },
                        )
                    },
                )
                browser.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.network_folder_empty),
                    message = stringResource(R.string.network_folder_empty_hint),
                )
                else -> LazyColumn(
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
                                                    context.getString(
                                                        if (added) R.string.favorite_added
                                                        else R.string.favorite_removed,
                                                    ),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.FavoriteBorder,
                                            contentDescription = stringResource(R.string.favorite_add),
                                        )
                                    }
                                },
                                onClick = {
                                    if (item.isDirectory) {
                                        navigateTo(item.path ?: item.uri)
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

private fun normalizeRemoteRoot(path: String): String {
    val trimmed = path.trim().ifBlank { "/" }
    return if (trimmed.startsWith("/")) trimmed.trimEnd('/').ifBlank { "/" }
    else "/${trimmed.trimEnd('/')}"
}

private fun isRemoteRootPath(path: String, rootPath: String): Boolean {
    val normalized = normalizeRemoteRoot(path)
    val root = normalizeRemoteRoot(rootPath)
    return normalized == "/" || normalized == root
}

private fun parentRemotePath(path: String, rootPath: String): String {
    val normalized = normalizeRemoteRoot(path)
    val root = normalizeRemoteRoot(rootPath)
    if (normalized == "/" || normalized == root) return root
    val idx = normalized.lastIndexOf('/')
    val parent = if (idx <= 0) "/" else normalized.substring(0, idx).ifBlank { "/" }
    // Do not walk above the configured server root.
    return if (parent.length < root.length && root.startsWith(parent)) root else parent
}
