package com.neko.video.ui.favorites

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko.video.R
import com.neko.video.asNekoVideoApp
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.remote.RemoteProtocol
import com.neko.video.ui.components.EmptyState
import com.neko.video.ui.components.MiuixPage
import com.neko.video.ui.components.SettingsGroupCard
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.asNekoVideoApp().container.mediaRepository
    val favorites = repository.favoritesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}

@Composable
fun FavoritesScreen(
    onPlay: (MediaItemInfo) -> Unit,
    onBrowseWebDav: (serverId: String, path: String) -> Unit,
    onBrowseRemote: (RemoteProtocol, String, String) -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    MiuixPage(title = stringResource(R.string.favorites_title)) { padding, nestedScrollConnection ->
        if (favorites.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.favorites_empty),
                message = stringResource(R.string.favorites_empty_hint),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = 8.dp + LocalMainBottomContentPadding.current,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                overscrollEffect = null,
            ) {
                items(favorites, key = { it.id }) { item ->
                    SettingsGroupCard {
                        ArrowPreference(
                            title = item.name,
                            summary = item.sourceType.name,
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
                            onClick = {
                                val serverId = item.serverId
                                val remote = RemoteProtocol.fromSourceType(item.sourceType)
                                when {
                                    item.isDirectory && item.sourceType == SourceType.WEBDAV &&
                                        serverId != null -> onBrowseWebDav(serverId, item.uri)
                                    item.isDirectory && remote != null && serverId != null ->
                                        onBrowseRemote(
                                            remote,
                                            serverId,
                                            item.path ?: "/",
                                        )
                                    else -> onPlay(item)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
