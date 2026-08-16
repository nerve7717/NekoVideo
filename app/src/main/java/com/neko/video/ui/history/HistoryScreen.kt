package com.neko.video.ui.history

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.neko.video.R
import com.neko.video.asNekoVideoApp
import com.neko.video.model.MediaItemInfo
import com.neko.video.ui.components.EmptyState
import com.neko.video.ui.components.MiuixConfirmDialog
import com.neko.video.ui.components.MiuixPage
import com.neko.video.ui.theme.LocalMainBottomContentPadding
import com.neko.video.util.FormatUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.asNekoVideoApp().container.mediaRepository
    val history = repository.historyFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun clear() {
        viewModelScope.launch { repository.clearHistory() }
    }
}

@Composable
fun HistoryScreen(
    onPlay: (MediaItemInfo) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    MiuixPage(
        title = stringResource(R.string.history_title),
        actions = {
            if (history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.history_clear),
                    )
                }
            }
        },
    ) { padding, nestedScrollConnection ->
        if (history.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.history_empty),
                message = stringResource(R.string.history_empty_hint),
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
                items(history, key = { it.id }) { item ->
                    Card(onClick = { onPlay(item) }) {
                        HistoryRow(item = item)
                    }
                }
            }
        }
    }

    MiuixConfirmDialog(
        show = confirmClear,
        title = stringResource(R.string.history_clear),
        summary = stringResource(R.string.history_clear_confirm),
        confirmText = stringResource(R.string.action_ok),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = {
            confirmClear = false
            viewModel.clear()
        },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun HistoryRow(item: MediaItemInfo) {
    val context = LocalContext.current
    val progress = if (item.duration > 0) {
        (item.playbackPosition.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.thumbnailUri ?: item.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MiuixTheme.textStyles.headline1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.playbackPosition > 0 && item.duration > 0 &&
                    item.playbackPosition >= item.duration * 0.95
                ) {
                    stringResource(R.string.history_completed)
                } else {
                    stringResource(
                        R.string.history_continue_from,
                        FormatUtils.formatDuration(item.playbackPosition),
                    )
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
