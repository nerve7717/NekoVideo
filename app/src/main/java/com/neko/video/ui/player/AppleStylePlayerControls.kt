package com.neko.video.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.video.R
import com.neko.video.model.AspectRatioMode
import com.neko.video.model.AudioTrackInfo
import com.neko.video.model.SubtitleTrackInfo
import com.neko.video.player.engine.PlayerEngineState
import com.neko.video.util.FormatUtils

private val ControlWhite = Color.White
private val ControlDim = Color.White.copy(alpha = 0.88f)
private val GlassCircle = Color.White.copy(alpha = 0.18f)
private val GlassCirclePressed = Color.White.copy(alpha = 0.28f)

/**
 * iOS AVKit-inspired overlay: gradient edges, circular glass play button,
 * thin scrubber, and secondary actions tucked into a overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleStylePlayerControls(
    title: String,
    engineState: PlayerEngineState,
    locked: Boolean,
    isFavorite: Boolean,
    hasPlaylist: Boolean,
    aspectRatio: AspectRatioMode,
    isLandscapeLocked: Boolean,
    pipEnabled: Boolean,
    seekSeconds: Int,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleLandscape: () -> Unit,
    onPip: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSelectAudio: (groupIndex: Int, trackIndex: Int) -> Unit,
    onSelectSubtitle: (groupIndex: Int, trackIndex: Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onLoadSubtitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top / bottom gradients (AVKit-like).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = ControlWhite,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = ControlWhite,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!locked) {
                GlassCircleButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF453A) else ControlWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(modifier = Modifier.width(8.dp))
                Box {
                    GlassCircleButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.player_more),
                            tint = ControlWhite,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (locked) {
                                        stringResource(R.string.player_unlock)
                                    } else {
                                        stringResource(R.string.player_lock)
                                    },
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onToggleLock()
                            },
                            leadingIcon = {
                                Icon(
                                    if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isLandscapeLocked) {
                                        stringResource(R.string.player_exit_fullscreen)
                                    } else {
                                        stringResource(R.string.player_fullscreen)
                                    },
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onToggleLandscape()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isLandscapeLocked) {
                                        Icons.Outlined.FullscreenExit
                                    } else {
                                        Icons.Outlined.Fullscreen
                                    },
                                    contentDescription = null,
                                )
                            },
                        )
                        if (pipEnabled && onPip != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_pip)) },
                                onClick = {
                                    showMoreMenu = false
                                    onPip()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = null)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_tracks_subtitle)) },
                            onClick = {
                                showMoreMenu = false
                                showSubtitleMenu = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_tracks_audio)) },
                            onClick = {
                                showMoreMenu = false
                                showAudioMenu = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_speed)) },
                            onClick = {
                                showMoreMenu = false
                                showSpeedMenu = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_aspect_ratio)) },
                            onClick = {
                                showMoreMenu = false
                                showAspectMenu = true
                            },
                        )
                    }
                    SubtitleMenu(
                        expanded = showSubtitleMenu,
                        tracks = engineState.subtitleTracks,
                        onDismiss = { showSubtitleMenu = false },
                        onOff = {
                            onDisableSubtitles()
                            showSubtitleMenu = false
                        },
                        onLoad = {
                            showSubtitleMenu = false
                            onLoadSubtitle()
                        },
                        onSelect = { g, t ->
                            onSelectSubtitle(g, t)
                            showSubtitleMenu = false
                        },
                    )
                    AudioMenu(
                        expanded = showAudioMenu,
                        tracks = engineState.audioTracks,
                        onDismiss = { showAudioMenu = false },
                        onSelect = { g, t ->
                            onSelectAudio(g, t)
                            showAudioMenu = false
                        },
                    )
                    SpeedMenu(
                        expanded = showSpeedMenu,
                        current = engineState.speed,
                        onDismiss = { showSpeedMenu = false },
                        onSelect = {
                            onSetSpeed(it)
                            showSpeedMenu = false
                        },
                    )
                    AspectMenu(
                        expanded = showAspectMenu,
                        current = aspectRatio,
                        onDismiss = { showAspectMenu = false },
                        onSelect = {
                            onSetAspectRatio(it)
                            showAspectMenu = false
                        },
                    )
                }
            } else {
                GlassCircleButton(onClick = onToggleLock) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.player_unlock),
                        tint = ControlWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (!locked) {
            // Center transport
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasPlaylist) {
                    TransportIconButton(
                        onClick = onPrevious,
                        size = 44.dp,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = ControlWhite,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                TransportIconButton(
                    onClick = { onSeekBy(-(seekSeconds * 1000L)) },
                    size = 52.dp,
                ) {
                    Icon(
                        Icons.Outlined.Replay10,
                        contentDescription = stringResource(R.string.player_rewind),
                        tint = ControlWhite,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(GlassCirclePressed)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPause,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (engineState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (engineState.isPlaying) {
                            stringResource(R.string.player_pause)
                        } else {
                            stringResource(R.string.player_play)
                        },
                        tint = ControlWhite,
                        modifier = Modifier.size(40.dp),
                    )
                }
                TransportIconButton(
                    onClick = { onSeekBy(seekSeconds * 1000L) },
                    size = 52.dp,
                ) {
                    Icon(
                        Icons.Outlined.Forward10,
                        contentDescription = stringResource(R.string.player_forward),
                        tint = ControlWhite,
                        modifier = Modifier.size(28.dp),
                    )
                }
                if (hasPlaylist) {
                    TransportIconButton(
                        onClick = onNext,
                        size = 44.dp,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.player_next),
                            tint = ControlWhite,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            // Bottom scrubber + quick actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val progress = if (engineState.durationMs > 0) {
                    engineState.positionMs.toFloat() / engineState.durationMs.toFloat()
                } else {
                    0f
                }
                val remaining = (engineState.durationMs - engineState.positionMs).coerceAtLeast(0L)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        FormatUtils.formatDuration(engineState.positionMs),
                        color = ControlDim,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(48.dp),
                    )
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { ratio ->
                            onSeekTo((ratio * engineState.durationMs).toLong())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = ControlWhite,
                            activeTrackColor = ControlWhite,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        ),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                colors = SliderDefaults.colors(thumbColor = ControlWhite),
                                thumbSize = DpSize(14.dp, 14.dp),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = ControlWhite,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                                ),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null,
                            )
                        },
                    )
                    Text(
                        "-${FormatUtils.formatDuration(remaining)}",
                        color = ControlDim,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp),
                    )
                }
                Box(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%.2fx".format(engineState.speed),
                        color = ControlDim,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSpeedMenu = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onToggleLandscape) {
                            Icon(
                                imageVector = if (isLandscapeLocked) {
                                    Icons.Outlined.FullscreenExit
                                } else {
                                    Icons.Outlined.Fullscreen
                                },
                                contentDescription = if (isLandscapeLocked) {
                                    stringResource(R.string.player_exit_fullscreen)
                                } else {
                                    stringResource(R.string.player_fullscreen)
                                },
                                tint = ControlWhite,
                            )
                        }
                        if (pipEnabled && onPip != null) {
                            IconButton(onClick = onPip) {
                                Icon(
                                    Icons.Outlined.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.player_pip),
                                    tint = ControlWhite,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(GlassCircle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TransportIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassCircle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SubtitleMenu(
    expanded: Boolean,
    tracks: List<SubtitleTrackInfo>,
    onDismiss: () -> Unit,
    onOff: () -> Unit,
    onLoad: () -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_subtitle_off)) },
            onClick = onOff,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_load_subtitle)) },
            onClick = onLoad,
        )
        tracks.forEach { track ->
            DropdownMenuItem(
                text = { Text(track.name + if (track.selected) " ✓" else "") },
                onClick = { onSelect(track.groupIndex, track.trackIndex) },
            )
        }
    }
}

@Composable
private fun AudioMenu(
    expanded: Boolean,
    tracks: List<AudioTrackInfo>,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        tracks.forEach { track ->
            val label = buildString {
                append(track.name)
                track.language?.let { append(" ($it)") }
                track.codecs?.let { append(" · $it") }
                track.channelCount?.let { append(" · ${it}ch") }
                if (track.selected) append(" ✓")
            }
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSelect(track.groupIndex, track.trackIndex) },
            )
        }
    }
}

@Composable
private fun SpeedMenu(
    expanded: Boolean,
    current: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
            DropdownMenuItem(
                text = {
                    Text("%.2fx".format(speed) + if (current == speed) " ✓" else "")
                },
                onClick = { onSelect(speed) },
            )
        }
    }
}

@Composable
private fun AspectMenu(
    expanded: Boolean,
    current: AspectRatioMode,
    onDismiss: () -> Unit,
    onSelect: (AspectRatioMode) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AspectRatioMode.entries.forEach { mode ->
            val label = when (mode) {
                AspectRatioMode.FIT -> stringResource(R.string.aspect_fit)
                AspectRatioMode.FILL -> stringResource(R.string.aspect_fill)
                AspectRatioMode.CROP -> stringResource(R.string.aspect_crop)
                AspectRatioMode.STRETCH -> stringResource(R.string.aspect_stretch)
                AspectRatioMode.RATIO_16_9 -> stringResource(R.string.aspect_16_9)
                AspectRatioMode.RATIO_4_3 -> stringResource(R.string.aspect_4_3)
            }
            DropdownMenuItem(
                text = { Text(label + if (current == mode) " ✓" else "") },
                onClick = { onSelect(mode) },
            )
        }
    }
}
