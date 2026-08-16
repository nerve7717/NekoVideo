package com.neko.video.ui.network

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko.video.R
import com.neko.video.asNekoVideoApp
import com.neko.video.database.RecentUrlEntity
import com.neko.video.model.MediaItemInfo
import com.neko.video.network.HttpHeaderResolver
import com.neko.video.network.NetworkErrorMapper
import com.neko.video.network.AppError
import com.neko.video.remote.RemoteProtocol
import com.neko.video.remote.RemoteServerConfig
import com.neko.video.util.FormatUtils
import com.neko.video.webdav.WebDavException
import com.neko.video.webdav.WebDavServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerEditorState(
    val id: String? = null,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val userAgent: String = "",
    val headersText: String = "",
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testSuccess: Boolean? = null,
    val message: String? = null,
)

data class BrowserState(
    val serverName: String = "",
    val serverBaseUrl: String = "",
    val path: String = "/",
    val items: List<MediaItemInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val videosOnly: Boolean = true,
)

data class PathBreadcrumb(
    val label: String,
    val path: String,
)

data class RemoteEditorState(
    val id: String? = null,
    val protocol: RemoteProtocol = RemoteProtocol.SMB,
    val name: String = "",
    val host: String = "",
    val port: String = "",
    val share: String = "",
    val initialPath: String = "/",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testSuccess: Boolean? = null,
    val message: String? = null,
)

data class RemoteBrowserState(
    val serverName: String = "",
    val path: String = "/",
    val items: List<MediaItemInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val videosOnly: Boolean = true,
)

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.asNekoVideoApp()
    private val repository = app.container.mediaRepository
    private val settings = app.container.settings

    val servers = repository.webDavServersFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val recentUrls = repository.recentUrlsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val remoteServers = repository.remoteServersFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _editorState = MutableStateFlow(ServerEditorState())
    val editorState: StateFlow<ServerEditorState> = _editorState.asStateFlow()

    private val _browserState = MutableStateFlow(BrowserState())
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    private val _remoteEditorState = MutableStateFlow(RemoteEditorState())
    val remoteEditorState: StateFlow<RemoteEditorState> = _remoteEditorState.asStateFlow()

    private val _remoteBrowserState = MutableStateFlow(RemoteBrowserState())
    val remoteBrowserState: StateFlow<RemoteBrowserState> = _remoteBrowserState.asStateFlow()

    private var currentConfig: WebDavServerConfig? = null
    private var currentRemoteConfig: RemoteServerConfig? = null
    private var browserLoadJob: Job? = null
    private var remoteBrowserLoadJob: Job? = null

    fun deleteServer(id: String) {
        viewModelScope.launch { repository.deleteWebDavServer(id) }
    }

    fun deleteRemoteServer(id: String) {
        viewModelScope.launch { repository.deleteRemoteServer(id) }
    }

    fun toggleFavorite(item: MediaItemInfo, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.toggleFavorite(item))
        }
    }

    fun mediaFromRecent(recent: RecentUrlEntity): MediaItemInfo {
        val headers = runCatching {
            val json = org.json.JSONObject(recent.headersJson.ifBlank { "{}" })
            buildMap {
                json.keys().forEach { key -> put(key, json.optString(key)) }
            }
        }.getOrDefault(emptyMap())
        return repository.createHttpMedia(recent.url, recent.title, headers)
    }

    fun createHttpMedia(url: String, userAgent: String, headersText: String): MediaItemInfo? {
        if (url.isBlank()) return null
        viewModelScope.launch {
            val settingsState = settings.settingsFlow.first()
            val mediaHeaders = FormatUtils.parseHeaders(headersText).toMutableMap()
            if (userAgent.isNotBlank()) mediaHeaders["User-Agent"] = userAgent
            val resolved = HttpHeaderResolver.resolve(settingsState, mediaHeaders)
            val media = repository.createHttpMedia(url, null, resolved)
            repository.saveRecentUrl(url, media.name, resolved)
        }
        val mediaHeaders = FormatUtils.parseHeaders(headersText).toMutableMap()
        if (userAgent.isNotBlank()) mediaHeaders["User-Agent"] = userAgent
        return repository.createHttpMedia(url, null, mediaHeaders)
    }

    fun loadEditor(serverId: String?) {
        viewModelScope.launch {
            if (serverId == null) {
                _editorState.value = ServerEditorState()
                return@launch
            }
            val config = repository.getWebDavServer(serverId) ?: return@launch
            _editorState.value = ServerEditorState(
                id = config.id,
                name = config.name,
                url = config.url,
                username = config.username,
                password = "",
                userAgent = config.userAgent.orEmpty(),
                headersText = FormatUtils.headersToText(config.headers),
            )
        }
    }

    fun updateEditor(transform: ServerEditorState.() -> ServerEditorState) {
        _editorState.update { it.transform() }
    }

    fun testEditorConnection() {
        viewModelScope.launch {
            val editor = _editorState.value
            _editorState.update { it.copy(testing = true, message = null, testSuccess = null) }
            val config = editorToConfigResolved(editor)
            val result = repository.testWebDav(config)
            val context = getApplication<Application>()
            if (result.isSuccess) {
                _editorState.update {
                    it.copy(
                        testing = false,
                        testSuccess = true,
                        message = context.getString(R.string.network_test_success),
                    )
                }
            } else {
                val error = (result.exceptionOrNull() as? WebDavException)?.appError
                    ?: NetworkErrorMapper.fromThrowable(result.exceptionOrNull()!!)
                _editorState.update {
                    it.copy(
                        testing = false,
                        testSuccess = false,
                        message = context.getString(
                            R.string.network_test_failed,
                            NetworkErrorMapper.toUserMessage(context, error),
                        ),
                    )
                }
            }
        }
    }

    fun saveEditor(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val editor = _editorState.value
            val context = getApplication<Application>()
            if (editor.name.isBlank()) {
                _editorState.update {
                    it.copy(message = context.getString(R.string.server_name_required))
                }
                return@launch
            }
            if (editor.url.isBlank()) {
                _editorState.update {
                    it.copy(message = context.getString(R.string.server_url_required))
                }
                return@launch
            }
            if (!editor.url.startsWith("http://") && !editor.url.startsWith("https://")) {
                _editorState.update {
                    it.copy(message = context.getString(R.string.server_url_invalid))
                }
                return@launch
            }
            _editorState.update { it.copy(saving = true) }
            val saved = repository.upsertWebDavServer(
                id = editor.id,
                name = editor.name.trim(),
                url = editor.url.trim(),
                username = editor.username.trim(),
                password = editor.password,
                userAgent = editor.userAgent.takeIf { it.isNotBlank() },
                headers = FormatUtils.parseHeaders(editor.headersText),
            )
            _editorState.update { it.copy(saving = false) }
            onSaved(saved.id)
        }
    }

    /**
     * Apply known server title synchronously (from in-memory servers list) so the top bar
     * never flashes the generic "WebDAV" label while PROPFIND is in flight.
     */
    fun prefetchBrowserTitle(serverId: String, path: String) {
        val cached = servers.value.firstOrNull { it.id == serverId } ?: return
        currentConfig = cached
        val current = _browserState.value
        if (current.serverName == cached.name &&
            current.serverBaseUrl == cached.url &&
            current.path == path &&
            current.loading
        ) {
            return
        }
        _browserState.update {
            it.copy(
                loading = true,
                error = null,
                path = path,
                // Keep previous items to avoid a blank/loading flash while PROPFIND runs.
                serverName = cached.name,
                serverBaseUrl = cached.url,
            )
        }
    }

    fun loadBrowser(serverId: String, path: String) {
        // Sync title from in-memory server list before the coroutine yields.
        prefetchBrowserTitle(serverId, path)
        browserLoadJob?.cancel()
        browserLoadJob = viewModelScope.launch {
            val config = repository.getWebDavServer(serverId)
            if (config == null) {
                _browserState.update {
                    it.copy(
                        loading = false,
                        error = "Server not found",
                        path = path,
                    )
                }
                return@launch
            }
            currentConfig = config
            _browserState.update {
                it.copy(
                    loading = true,
                    error = null,
                    path = path,
                    serverName = config.name,
                    serverBaseUrl = config.url,
                )
            }
            val timeout = settings.settingsFlow.first().networkTimeoutSeconds
            val result = repository.listWebDav(
                config,
                path,
                _browserState.value.videosOnly,
                timeoutSeconds = timeout,
            )
            if (result.isSuccess) {
                _browserState.update {
                    it.copy(
                        loading = false,
                        serverName = config.name,
                        serverBaseUrl = config.url,
                        path = path,
                        error = null,
                        items = result.getOrDefault(emptyList()),
                    )
                }
            } else {
                val error = (result.exceptionOrNull() as? WebDavException)?.appError
                    ?: AppError.Connection(result.exceptionOrNull()?.message)
                _browserState.update {
                    it.copy(
                        loading = false,
                        serverName = config.name,
                        serverBaseUrl = config.url,
                        path = path,
                        error = NetworkErrorMapper.toUserMessage(getApplication(), error),
                    )
                }
            }
        }
    }

    fun setVideosOnly(enabled: Boolean) {
        val path = _browserState.value.path
        val serverId = currentConfig?.id ?: return
        _browserState.update { it.copy(videosOnly = enabled) }
        loadBrowser(serverId, path)
    }

    private fun editorToConfig(editor: ServerEditorState): WebDavServerConfig {
        return WebDavServerConfig(
            id = editor.id ?: "temp",
            name = editor.name,
            url = editor.url,
            username = editor.username,
            password = editor.password,
            userAgent = editor.userAgent.takeIf { it.isNotBlank() },
            headers = FormatUtils.parseHeaders(editor.headersText),
        )
    }

    private suspend fun editorToConfigResolved(editor: ServerEditorState): WebDavServerConfig {
        val base = editorToConfig(editor)
        if (base.password.isNotBlank() || editor.id == null) return base
        val existing = repository.getWebDavServer(editor.id) ?: return base
        return base.copy(password = existing.password)
    }

    fun loadRemoteEditor(protocol: RemoteProtocol, serverId: String?) {
        viewModelScope.launch {
            if (serverId == null) {
                _remoteEditorState.value = RemoteEditorState(protocol = protocol)
                return@launch
            }
            val config = repository.getRemoteServer(serverId) ?: run {
                _remoteEditorState.value = RemoteEditorState(protocol = protocol)
                return@launch
            }
            _remoteEditorState.value = RemoteEditorState(
                id = config.id,
                protocol = config.protocol,
                name = config.name,
                host = config.host,
                port = if (config.port > 0) config.port.toString() else "",
                share = config.share,
                initialPath = config.initialPath,
                username = config.username,
                password = "",
                domain = config.domain,
            )
        }
    }

    fun updateRemoteEditor(transform: RemoteEditorState.() -> RemoteEditorState) {
        _remoteEditorState.update { it.transform() }
    }

    fun testRemoteEditor() {
        viewModelScope.launch {
            val editor = _remoteEditorState.value
            _remoteEditorState.update { it.copy(testing = true, message = null, testSuccess = null) }
            val config = remoteEditorToConfigResolved(editor)
            val result = repository.testRemoteServer(config)
            val context = getApplication<Application>()
            if (result.isSuccess) {
                _remoteEditorState.update {
                    it.copy(
                        testing = false,
                        testSuccess = true,
                        message = context.getString(R.string.network_test_success),
                    )
                }
            } else {
                _remoteEditorState.update {
                    it.copy(
                        testing = false,
                        testSuccess = false,
                        message = context.getString(
                            R.string.network_test_failed,
                            result.exceptionOrNull()?.message
                                ?: context.getString(R.string.error_connection),
                        ),
                    )
                }
            }
        }
    }

    fun saveRemoteEditor(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val editor = _remoteEditorState.value
            val context = getApplication<Application>()
            if (editor.name.isBlank() || editor.host.isBlank()) {
                _remoteEditorState.update {
                    it.copy(message = context.getString(R.string.server_name_required))
                }
                return@launch
            }
            if (editor.protocol == RemoteProtocol.SMB && editor.share.isBlank()) {
                _remoteEditorState.update {
                    it.copy(message = context.getString(R.string.remote_smb_share_required))
                }
                return@launch
            }
            _remoteEditorState.update { it.copy(saving = true) }
            val saved = repository.upsertRemoteServer(
                id = editor.id,
                protocol = editor.protocol,
                name = editor.name,
                host = editor.host,
                port = editor.port.toIntOrNull() ?: 0,
                share = editor.share,
                initialPath = editor.initialPath.ifBlank { "/" },
                username = editor.username,
                password = editor.password,
                domain = editor.domain,
            )
            _remoteEditorState.update { it.copy(saving = false) }
            onSaved(saved.id)
        }
    }

    fun loadRemoteBrowser(protocol: RemoteProtocol, serverId: String, path: String) {
        remoteBrowserLoadJob?.cancel()
        remoteBrowserLoadJob = viewModelScope.launch {
            _remoteBrowserState.update {
                it.copy(loading = true, error = null, path = path, items = emptyList())
            }
            val config = repository.getRemoteServer(serverId)
            if (config == null || config.protocol != protocol) {
                _remoteBrowserState.update {
                    it.copy(loading = false, error = "Server not found")
                }
                return@launch
            }
            currentRemoteConfig = config
            _remoteBrowserState.update {
                it.copy(serverName = config.name, path = path, loading = true)
            }
            val target = path.ifBlank { config.initialPath.ifBlank { "/" } }
            val videosOnly = _remoteBrowserState.value.videosOnly
            val result = repository.listRemote(config, target, videosOnly)
            // Drop stale responses after a faster folder switch.
            if (_remoteBrowserState.value.path != path &&
                _remoteBrowserState.value.path != target
            ) {
                return@launch
            }
            if (result.isSuccess) {
                _remoteBrowserState.update {
                    it.copy(
                        loading = false,
                        serverName = config.name,
                        path = target,
                        items = result.getOrDefault(emptyList()),
                        error = null,
                    )
                }
            } else {
                _remoteBrowserState.update {
                    it.copy(
                        loading = false,
                        serverName = config.name,
                        path = target,
                        items = emptyList(),
                        error = result.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.error_connection),
                    )
                }
            }
        }
    }

    fun setRemoteVideosOnly(enabled: Boolean) {
        val path = _remoteBrowserState.value.path
        val config = currentRemoteConfig ?: return
        _remoteBrowserState.update { it.copy(videosOnly = enabled) }
        loadRemoteBrowser(config.protocol, config.id, path)
    }

    private fun remoteEditorToConfig(editor: RemoteEditorState): RemoteServerConfig {
        return RemoteServerConfig(
            id = editor.id ?: "temp",
            protocol = editor.protocol,
            name = editor.name,
            host = editor.host,
            port = editor.port.toIntOrNull() ?: 0,
            share = editor.share,
            initialPath = editor.initialPath.ifBlank { "/" },
            username = editor.username,
            password = editor.password,
            domain = editor.domain,
        )
    }

    private suspend fun remoteEditorToConfigResolved(editor: RemoteEditorState): RemoteServerConfig {
        val base = remoteEditorToConfig(editor)
        if (base.password.isNotBlank() || editor.id == null) return base
        val existing = repository.getRemoteServer(editor.id) ?: return base
        return base.copy(password = existing.password)
    }
}
