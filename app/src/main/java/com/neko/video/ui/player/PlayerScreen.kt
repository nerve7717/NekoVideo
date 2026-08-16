package com.neko.video.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.util.Rational
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.neko.video.R
import com.neko.video.model.SourceType
import com.neko.video.ui.components.MiuixConfirmDialog
import com.neko.video.util.FormatUtils
import com.neko.video.util.NetworkUtils
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import kotlin.math.abs

@UnstableApi
@Composable
fun PlayerScreen(
    uri: String,
    title: String,
    sourceType: String,
    mediaId: String?,
    serverId: String?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showMeteredWarning by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var isLandscapeLocked by remember { mutableStateOf(false) }
    val view = LocalView.current

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        picked?.toString()?.let { viewModel.addExternalSubtitle(it) }
    }

    fun startPlayback() {
        viewModel.prepare(uri, title, sourceType, mediaId, serverId)
    }

    fun restoreSystemUi() {
        val window = activity?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun leavePlayer() {
        // Clear video output before navigation so the last frame cannot linger.
        viewModel.prepareForExit()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        isLandscapeLocked = false
        restoreSystemUi()
        onBack()
    }

    LaunchedEffect(uri, settings.mobileDataWarning) {
        val isLocal = sourceType == SourceType.LOCAL.name
        if (!isLocal && settings.mobileDataWarning && NetworkUtils.isMeteredNetwork(context)) {
            showMeteredWarning = true
        } else {
            startPlayback()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val inPip = activity?.isInPictureInPictureMode == true
        viewModel.onAppBackgrounded(inPictureInPicture = inPip)
    }

    DisposableEffect(settings.keepScreenOn) {
        val window = activity?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Always restore orientation + system bars when leaving the player screen.
    DisposableEffect(activity) {
        onDispose {
            viewModel.prepareForExit()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            restoreSystemUi()
        }
    }

    LaunchedEffect(isLandscapeLocked) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        activity.requestedOrientation = if (isLandscapeLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (isLandscapeLocked) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        if (uiState.locked) {
            viewModel.toggleLock()
        } else {
            leavePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                val playerView = LayoutInflater.from(ctx)
                    .inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView
                playerView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    viewModel.playerEngine.attachVideoView(this)
                    subtitleView?.setFixedTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        18f * settings.subtitleSize,
                    )
                }
            },
            update = { playerView ->
                if (playerView.visibility != android.view.View.VISIBLE) {
                    playerView.visibility = android.view.View.VISIBLE
                }
                viewModel.playerEngine.attachVideoView(playerView)
                viewModel.playerEngine.setAspectRatio(uiState.aspectRatio)
                playerView.subtitleView?.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    18f * settings.subtitleSize,
                )
            },
            onRelease = { playerView ->
                viewModel.playerEngine.detachVideoView()
                playerView.player = null
                playerView.visibility = android.view.View.GONE
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                )
                .pointerInput(uiState.locked) {
                    if (uiState.locked) {
                        detectTapGestures { viewModel.toggleLock() }
                        return@pointerInput
                    }
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { offset ->
                            val width = size.width
                            val seekMs = viewModel.seekSeconds() * 1000L
                            when {
                                offset.x < width / 3f -> viewModel.playerEngine.seekBy(-seekMs)
                                offset.x > width * 2 / 3f -> viewModel.playerEngine.seekBy(seekMs)
                                else -> viewModel.playerEngine.togglePlayPause()
                            }
                            viewModel.showControlsTemporarily()
                        },
                    )
                }
                .pointerInput(uiState.locked) {
                    if (uiState.locked) return@pointerInput
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                    }
                }
                .pointerInput(uiState.locked) {
                    if (uiState.locked) return@pointerInput
                    val audioManager = context.getSystemService(AudioManager::class.java)
                    coroutineScope {
                        val scope = this
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var startX = down.position.x
                            var startY = down.position.y
                            var totalX = 0f
                            var mode: GestureMode? = null
                            val seekStart = viewModel.playerEngine.state.value.positionMs
                            var boosted = false
                            val savedSpeed = viewModel.playerEngine.state.value.speed
                            var active = true

                            val boostJob = scope.launch {
                                delay(420L)
                                if (active && mode == null && !boosted) {
                                    boosted = true
                                    val boost = if (savedSpeed >= 2f) 3f else 2f
                                    viewModel.playerEngine.setSpeed(boost)
                                    viewModel.setGestureLabel(
                                        context.getString(
                                            R.string.player_gesture_speed_boost,
                                            "%.1fx".format(boost),
                                        ),
                                    )
                                }
                            }

                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    val dx = change.position.x - startX
                                    val dy = change.position.y - startY
                                    totalX = dx

                                    if (!boosted && mode == null && (abs(dx) > 24f || abs(dy) > 24f)) {
                                        boostJob.cancel()
                                        mode = if (abs(dx) > abs(dy)) {
                                            GestureMode.SEEK
                                        } else if (startX < size.width / 2f) {
                                            GestureMode.BRIGHTNESS
                                        } else {
                                            GestureMode.VOLUME
                                        }
                                    }

                                    if (boosted) {
                                        change.consume()
                                        continue
                                    }

                                    when (mode) {
                                        GestureMode.SEEK -> {
                                            val deltaMs = (dx / size.width * 180_000f).toLong()
                                            val target = (seekStart + deltaMs).coerceAtLeast(0L)
                                            val label = if (deltaMs >= 0) {
                                                context.getString(
                                                    R.string.player_seek_delta_forward,
                                                    FormatUtils.formatDuration(abs(deltaMs)),
                                                )
                                            } else {
                                                context.getString(
                                                    R.string.player_seek_delta_backward,
                                                    FormatUtils.formatDuration(abs(deltaMs)),
                                                )
                                            }
                                            viewModel.setGestureLabel(
                                                "${FormatUtils.formatDuration(target)}  $label",
                                            )
                                        }
                                        GestureMode.VOLUME -> {
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val delta = (-dy / size.height * max).toInt()
                                            val next = (current + delta).coerceIn(0, max)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                            viewModel.setGestureLabel(
                                                context.getString(
                                                    R.string.player_gesture_volume,
                                                    (next * 100 / max.coerceAtLeast(1)),
                                                ),
                                            )
                                            startY = change.position.y
                                        }
                                        GestureMode.BRIGHTNESS -> {
                                            val window = activity?.window
                                            val lp = window?.attributes
                                            val current = if (lp?.screenBrightness ?: -1f >= 0f) {
                                                lp!!.screenBrightness
                                            } else {
                                                Settings.System.getInt(
                                                    context.contentResolver,
                                                    Settings.System.SCREEN_BRIGHTNESS,
                                                    125,
                                                ) / 255f
                                            }
                                            val next = (current - dy / size.height).coerceIn(0.01f, 1f)
                                            if (lp != null) {
                                                lp.screenBrightness = next
                                                window?.attributes = lp
                                            }
                                            viewModel.setGestureLabel(
                                                context.getString(
                                                    R.string.player_gesture_brightness,
                                                    (next * 100).toInt(),
                                                ),
                                            )
                                            startY = change.position.y
                                        }
                                        null -> Unit
                                    }
                                    if (mode != null) change.consume()
                                } while (true)
                            } finally {
                                active = false
                                boostJob.cancel()
                                if (mode == GestureMode.SEEK) {
                                    val deltaMs = (totalX / size.width * 180_000f).toLong()
                                    viewModel.playerEngine.seekTo((seekStart + deltaMs).coerceAtLeast(0L))
                                }
                                if (boosted) {
                                    viewModel.playerEngine.setSpeed(savedSpeed)
                                }
                                if (mode != null || boosted) {
                                    viewModel.setGestureLabel(null)
                                }
                            }
                        }
                    }
                },
        )

        if (engineState.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = Color.White,
                    disabledForegroundColor = Color.White.copy(alpha = 0.4f),
                    backgroundColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }

        uiState.gestureLabel?.let { label ->
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = uiState.controlsVisible || uiState.locked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppleStylePlayerControls(
                title = engineState.title.ifBlank { title },
                engineState = engineState,
                locked = uiState.locked,
                isFavorite = uiState.isFavorite,
                hasPlaylist = uiState.hasPlaylist,
                aspectRatio = uiState.aspectRatio,
                isLandscapeLocked = isLandscapeLocked,
                pipEnabled = settings.pipEnabled && activity != null,
                seekSeconds = viewModel.seekSeconds(),
                onBack = ::leavePlayer,
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleLock = viewModel::toggleLock,
                onToggleLandscape = { isLandscapeLocked = !isLandscapeLocked },
                onPip = activity?.let { act -> { enterPip(act) } },
                onPlayPause = {
                    viewModel.playerEngine.togglePlayPause()
                    viewModel.showControlsTemporarily()
                },
                onSeekBy = { delta ->
                    viewModel.playerEngine.seekBy(delta)
                    viewModel.showControlsTemporarily()
                },
                onSeekTo = { pos ->
                    viewModel.playerEngine.seekTo(pos)
                    viewModel.showControlsTemporarily()
                },
                onPrevious = viewModel::playPreviousIfAvailable,
                onNext = viewModel::playNextIfAvailable,
                onSetSpeed = viewModel.playerEngine::setSpeed,
                onSetAspectRatio = viewModel::setAspectRatio,
                onSelectAudio = viewModel.playerEngine::selectAudioTrack,
                onSelectSubtitle = viewModel.playerEngine::selectSubtitleTrack,
                onDisableSubtitles = viewModel.playerEngine::disableSubtitles,
                onLoadSubtitle = {
                    subtitlePicker.launch(
                        arrayOf(
                            "text/*",
                            "application/x-subrip",
                            "*/*",
                        ),
                    )
                },
            )
        }

        engineState.errorMessage?.let { error ->
            Text(
                text = stringResource(R.string.player_error, error),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
            )
        }
    }

    MiuixConfirmDialog(
        show = uiState.resumePrompt != null,
        title = stringResource(R.string.player_resume_title),
        summary = uiState.resumePrompt?.let { prompt ->
            stringResource(
                R.string.player_resume_message,
                FormatUtils.formatDuration(prompt.positionMs),
            )
        }.orEmpty(),
        confirmText = stringResource(R.string.player_resume),
        dismissText = stringResource(R.string.player_restart),
        onConfirm = viewModel::resumeFromPrompt,
        onDismiss = viewModel::restartFromPrompt,
    )

    MiuixConfirmDialog(
        show = showMeteredWarning,
        title = stringResource(R.string.player_metered_title),
        summary = stringResource(R.string.player_metered_message),
        confirmText = stringResource(R.string.action_ok),
        dismissText = stringResource(R.string.action_cancel),
        onConfirm = {
            showMeteredWarning = false
            startPlayback()
        },
        onDismiss = {
            showMeteredWarning = false
            leavePlayer()
        },
    )
}

private enum class GestureMode { SEEK, VOLUME, BRIGHTNESS }

private fun enterPip(activity: Activity) {
    runCatching {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}
