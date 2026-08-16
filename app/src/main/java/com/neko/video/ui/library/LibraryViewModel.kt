package com.neko.video.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neko.video.asNekoVideoApp
import com.neko.video.model.MediaFolder
import com.neko.video.model.MediaItemInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab { RECENT, FOLDERS, ALL }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.ALL,
    val isLoading: Boolean = false,
    val videos: List<MediaItemInfo> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val selectedFolder: MediaFolder? = null,
    val folderVideos: List<MediaItemInfo> = emptyList(),
    val error: String? = null,
    val hasPermission: Boolean = false,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.asNekoVideoApp().container.mediaRepository
    private var scanJob: Job? = null
    private var importJob: Job? = null

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            videos = LocalMediaCache.videos.orEmpty(),
            folders = LocalMediaCache.folders.orEmpty(),
            isLoading = false,
        ),
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val recent = repository.recentHistoryFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            // Re-entering the tab must not rescan; only load once per process (or after clear).
            ensureLoaded()
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(tab = tab, selectedFolder = null) }
    }

    /** Load from memory cache if present; otherwise scan once. */
    fun ensureLoaded() {
        val cachedVideos = LocalMediaCache.videos
        val cachedFolders = LocalMediaCache.folders
        if (cachedVideos != null && cachedFolders != null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    videos = cachedVideos,
                    folders = cachedFolders,
                    error = null,
                )
            }
            return
        }
        refresh(force = true)
    }

    fun refresh(force: Boolean = true) {
        if (!force && LocalMediaCache.isReady) {
            ensureLoaded()
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.loadLocalVideos() }
                .onSuccess { videos ->
                    val folders = repository.loadLocalFolders(videos)
                    LocalMediaCache.update(videos, folders)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            videos = videos,
                            folders = folders,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
        }
    }

    fun openFolder(folder: MediaFolder) {
        val videos = _uiState.value.videos.filter { item ->
            item.path?.startsWith(folder.path) == true &&
                item.path.substringAfterLast('/').let { fileName ->
                    val parent = item.path.removeSuffix("/$fileName")
                    parent == folder.path
                }
        }
        _uiState.update {
            it.copy(selectedFolder = folder, folderVideos = videos, tab = LibraryTab.FOLDERS)
        }
    }

    fun clearFolder() {
        _uiState.update { it.copy(selectedFolder = null, folderVideos = emptyList()) }
    }

    fun importTree(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.loadFromTreeUri(uri) }
                .onSuccess { imported ->
                    val merged = (_uiState.value.videos + imported)
                        .distinctBy { it.uri }
                        .sortedByDescending { it.modifiedTime }
                    val folders = repository.loadLocalFolders(merged)
                    LocalMediaCache.update(merged, folders)
                    _uiState.update {
                        it.copy(isLoading = false, videos = merged, folders = folders, tab = LibraryTab.ALL)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun mediaFromPickedUri(uri: Uri, name: String?): MediaItemInfo {
        return MediaItemInfo(
            id = "picked_${uri.hashCode()}",
            name = name ?: uri.lastPathSegment ?: "video",
            uri = uri.toString(),
            sourceType = com.neko.video.model.SourceType.LOCAL,
            thumbnailUri = uri.toString(),
        )
    }
}
