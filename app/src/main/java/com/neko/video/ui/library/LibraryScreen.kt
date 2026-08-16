package com.neko.video.ui.library

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.neko.video.R
import com.neko.video.model.MediaFolder
import com.neko.video.model.MediaItemInfo
import com.neko.video.ui.component.GlassFloatingTabRow
import com.neko.video.ui.components.EmptyState
import com.neko.video.ui.components.LoadingState
import com.neko.video.ui.components.MiuixPage
import com.neko.video.ui.components.PrimaryButton
import com.neko.video.ui.components.SecondaryButton
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import com.neko.video.util.FormatUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LibraryScreen(
    onPlay: (MediaItemInfo) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setPermissionGranted(granted)
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPlay(viewModel.mediaFromPickedUri(uri, null))
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importTree(uri)
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        }
    }

    val tabs = listOf(
        stringResource(R.string.library_recent),
        stringResource(R.string.library_folders),
        stringResource(R.string.library_all_videos),
    )

    val layoutDirection = LocalLayoutDirection.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    // Same pattern as the floating bottom bar: content is the blur source, pill floats on top.
    val tabGlassBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    // Pill height + vertical gaps reserved so list content can scroll under the glass tabs.
    val floatingTabInset = 56.dp + 16.dp

    MiuixPage(
        title = stringResource(R.string.library_title),
        actions = {
            IconButton(onClick = { pickVideoLauncher.launch(arrayOf("video/*")) }) {
                Icon(Icons.Outlined.VideoFile, contentDescription = stringResource(R.string.library_pick_video))
            }
            IconButton(onClick = { pickFolderLauncher.launch(null) }) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.library_pick_folder))
            }
            IconButton(onClick = { viewModel.refresh(force = true) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.library_refresh))
            }
        },
    ) { padding, nestedScrollConnection ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(tabGlassBackdrop),
            ) {
                when {
                    state.isLoading -> LoadingState(stringResource(R.string.library_scanning))
                    state.error != null && state.videos.isEmpty() -> {
                        EmptyState(
                            title = stringResource(R.string.error_connection),
                            message = stringResource(R.string.error_generic, state.error.orEmpty()),
                            action = {
                                PrimaryButton(
                                    text = stringResource(R.string.action_retry),
                                    onClick = { viewModel.refresh(force = true) },
                                )
                            },
                        )
                    }
                    !state.hasPermission && state.videos.isEmpty() -> {
                        EmptyState(
                            title = stringResource(R.string.library_empty),
                            message = stringResource(R.string.library_empty_hint),
                            action = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrimaryButton(
                                        text = stringResource(R.string.library_grant_permission),
                                        onClick = {
                                            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                                        },
                                    )
                                    SecondaryButton(
                                        text = stringResource(R.string.library_pick_video),
                                        onClick = { pickVideoLauncher.launch(arrayOf("video/*")) },
                                    )
                                }
                            },
                        )
                    }
                    else -> {
                        AnimatedContent(
                            targetState = state.tab to state.selectedFolder?.id,
                            transitionSpec = {
                                val forward = targetState.first.ordinal >= initialState.first.ordinal
                                val enter = fadeIn() + slideInHorizontally { full ->
                                    if (forward) full / 5 else -full / 5
                                }
                                val exit = fadeOut() + slideOutHorizontally { full ->
                                    if (forward) -full / 5 else full / 5
                                }
                                enter togetherWith exit
                            },
                            label = "libraryTab",
                            modifier = Modifier.fillMaxSize(),
                        ) { (tab, _) ->
                            when (tab) {
                                LibraryTab.RECENT -> {
                                    if (recent.isEmpty()) {
                                        EmptyState(
                                            title = stringResource(R.string.history_empty),
                                            message = stringResource(R.string.history_empty_hint),
                                        )
                                    } else {
                                        VideoList(
                                            items = recent,
                                            onPlay = onPlay,
                                            nestedScrollConnection = nestedScrollConnection,
                                            topContentPadding = floatingTabInset,
                                        )
                                    }
                                }
                                LibraryTab.FOLDERS -> {
                                    if (state.selectedFolder != null) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = state.selectedFolder!!.name,
                                                style = MiuixTheme.textStyles.headline1,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable(onClick = viewModel::clearFolder)
                                                    .padding(
                                                        start = 16.dp,
                                                        end = 16.dp,
                                                        top = floatingTabInset + 4.dp,
                                                        bottom = 8.dp,
                                                    ),
                                            )
                                            VideoList(
                                                items = state.folderVideos,
                                                onPlay = onPlay,
                                                nestedScrollConnection = nestedScrollConnection,
                                                topContentPadding = 0.dp,
                                            )
                                        }
                                    } else if (state.folders.isEmpty()) {
                                        EmptyState(
                                            title = stringResource(R.string.library_empty),
                                            message = stringResource(R.string.library_empty_hint),
                                        )
                                    } else {
                                        FolderGrid(
                                            folders = state.folders,
                                            onOpen = viewModel::openFolder,
                                            nestedScrollConnection = nestedScrollConnection,
                                            topContentPadding = floatingTabInset,
                                        )
                                    }
                                }
                                LibraryTab.ALL -> {
                                    if (state.videos.isEmpty()) {
                                        EmptyState(
                                            title = stringResource(R.string.library_empty),
                                            message = stringResource(R.string.library_empty_hint),
                                            action = {
                                                PrimaryButton(
                                                    text = stringResource(R.string.library_pick_video),
                                                    onClick = {
                                                        pickVideoLauncher.launch(arrayOf("video/*"))
                                                    },
                                                )
                                            },
                                        )
                                    } else {
                                        VideoList(
                                            items = state.videos,
                                            onPlay = onPlay,
                                            nestedScrollConnection = nestedScrollConnection,
                                            topContentPadding = floatingTabInset,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GlassFloatingTabRow(
                tabs = tabs,
                selectedTabIndex = state.tab.ordinal,
                onTabSelected = { index ->
                    viewModel.selectTab(LibraryTab.entries[index])
                },
                backdrop = tabGlassBackdrop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun VideoList(
    items: List<MediaItemInfo>,
    onPlay: (MediaItemInfo) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    topContentPadding: Dp = 8.dp,
) {
    val bottomPadding = LocalMainBottomContentPadding.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = topContentPadding,
            bottom = 8.dp + bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        overscrollEffect = null,
    ) {
        items(items, key = { it.id }) { item ->
            Card(onClick = { onPlay(item) }) {
                VideoRow(item = item)
            }
        }
    }
}

@Composable
private fun FolderGrid(
    folders: List<MediaFolder>,
    onOpen: (MediaFolder) -> Unit,
    nestedScrollConnection: NestedScrollConnection,
    topContentPadding: Dp = 12.dp,
) {
    val bottomPadding = LocalMainBottomContentPadding.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = topContentPadding,
            bottom = 12.dp + bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null,
    ) {
        items(folders, key = { it.id }) { folder ->
            Card(onClick = { onOpen(folder) }) {
                Column(modifier = Modifier.padding(10.dp)) {
                    BoxThumbnail(folder.thumbnailUri)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = folder.name,
                        style = MiuixTheme.textStyles.headline1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.library_videos_count, folder.videoCount),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRow(item: MediaItemInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxThumbnail(item.thumbnailUri, modifier = Modifier.width(120.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MiuixTheme.textStyles.headline1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val meta = buildList {
                if (item.duration > 0) add(FormatUtils.formatDuration(item.duration))
                item.displayResolution?.let { add(it) }
                if (item.size > 0) add(FormatUtils.formatSize(item.size))
                if (item.playbackPosition > 0) {
                    add(FormatUtils.formatDuration(item.playbackPosition))
                }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BoxThumbnail(
    uri: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (uri == null) {
        Box(
            modifier = modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}
