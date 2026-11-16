package com.example.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toRect
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.media.MediaItemData
import com.example.media.PlaybackManager
import com.example.ui.components.MediaTimeFormatter
import com.example.ui.components.TrackAndSubtitleDialog
import com.example.ui.theme.MonoTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

private enum class GestureAxis { NONE, BRIGHTNESS, VOLUME, SEEK }

@Composable
fun VideoPlayerScreen(
    video: MediaItemData,
    onClose: () -> Unit,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onPlayInBackgroundAudio: () -> Unit = {},
    onUpdatePipParams: (Rational?, Rect?) -> Unit = { _, _ -> },
    onAttachSubtitle: (uri: String, fileName: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxStreamVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val player = PlaybackManager.getPlayer()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()
    val currentPosMs by PlaybackManager.currentPositionMs.collectAsState()
    val durationMs by PlaybackManager.durationMs.collectAsState()
    val speed by PlaybackManager.playbackSpeed.collectAsState()
    val abLoop by PlaybackManager.abLoopState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeGesture by remember { mutableStateOf(GestureAxis.NONE) }
    var brightnessLevel by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0.05f..1f } ?: 0.6f
        )
    }
    var volumeFraction by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxStreamVolume.toFloat()
        )
    }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    if (showTracksDialog) {
        TrackAndSubtitleDialog(
            videoId = video.id,
            onAttachSubtitle = onAttachSubtitle,
            onDismiss = { showTracksDialog = false }
        )
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
            showControls = true
        } else if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            isLandscape = false
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = -1f
                win.attributes = lp
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(750)
            doubleTapFeedback = null
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val overlayContainer = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (isLocked || containerSize.width <= 0) return@detectTapGestures
                        if (offset.x < containerSize.width * 0.45f) {
                            PlaybackManager.skipBackward(10)
                            doubleTapFeedback = "-10 ث"
                        } else if (offset.x > containerSize.width * 0.55f) {
                            PlaybackManager.skipForward(10)
                            doubleTapFeedback = "+10 ث"
                        } else {
                            PlaybackManager.togglePlayPause()
                        }
                    }
                )
            }
            .pointerInput(isLocked, isInPipMode) {
                if (isLocked || isInPipMode) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2 || zoomScale > 1.02f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (pressedCount >= 2 || zoomChange != 1f) {
                                val nextScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
                                zoomScale = nextScale
                                if (nextScale <= 1.01f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    val maxX = containerSize.width * (nextScale - 1f) / 2f
                                    val maxY = containerSize.height * (nextScale - 1f) / 2f
                                    panOffset = Offset(
                                        x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                        y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            } else if (zoomScale > 1.02f && panChange != Offset.Zero) {
                                val maxX = containerSize.width * (zoomScale - 1f) / 2f
                                val maxY = containerSize.height * (zoomScale - 1f) / 2f
                                panOffset = Offset(
                                    x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(isLocked, isInPipMode, durationMs, zoomScale) {
                if (isLocked || isInPipMode || zoomScale > 1.02f) return@pointerInput
                var startX = 0f
                var initialSeekMs = 0L
                detectDragGestures(
                    onDragStart = { startOffset ->
                        activeGesture = GestureAxis.NONE
                        startX = startOffset.x
                        initialSeekMs = PlaybackManager.currentPositionMs.value
                        volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxStreamVolume.toFloat()
                    },
                    onDrag = { change, dragAmount ->
                        if (zoomScale > 1.02f) {
                            activeGesture = GestureAxis.NONE
                            return@detectDragGestures
                        }
                        change.consume()
                        val w = containerSize.width.coerceAtLeast(1).toFloat()
                        val h = containerSize.height.coerceAtLeast(1).toFloat()

                        if (activeGesture == GestureAxis.NONE) {
                            activeGesture = if (abs(dragAmount.x) > abs(dragAmount.y) * 1.2f) {
                                GestureAxis.SEEK
                            } else if (startX < w / 2f) {
                                GestureAxis.BRIGHTNESS
                            } else {
                                GestureAxis.VOLUME
                            }
                        }

                        when (activeGesture) {
                            GestureAxis.BRIGHTNESS -> {
                                val delta = -dragAmount.y / h
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1.0f)
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = brightnessLevel
                                    win.attributes = lp
                                }
                            }
                            GestureAxis.VOLUME -> {
                                val delta = -dragAmount.y / h
                                volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                                val targetVol = (volumeFraction * maxStreamVolume).toInt().coerceIn(0, maxStreamVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            }
                            GestureAxis.SEEK -> {
                                val totalSeekDelta = ((dragAmount.x / w) * 90_000L).toLong()
                                val maxDur = durationMs.coerceAtLeast(1L)
                                initialSeekMs = (initialSeekMs + totalSeekDelta).coerceIn(0L, maxDur)
                                seekTargetMs = initialSeekMs
                            }
                            GestureAxis.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (activeGesture == GestureAxis.SEEK && durationMs > 0L) {
                            PlaybackManager.seekTo(seekTargetMs)
                        }
                        activeGesture = GestureAxis.NONE
                    },
                    onDragCancel = {
                        activeGesture = GestureAxis.NONE
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.resizeMode = resizeMode
                playerViewRef = playerView
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (isInPipMode) 1f else zoomScale,
                    scaleY = if (isInPipMode) 1f else zoomScale,
                    translationX = if (isInPipMode) 0f else panOffset.x,
                    translationY = if (isInPipMode) 0f else panOffset.y
                )
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow().toAndroidRectF().toRect()
                    val vSize = player?.videoSize
                    val ratio = if (vSize != null && vSize != VideoSize.UNKNOWN && vSize.width > 0 && vSize.height > 0) {
                        val rawRatio = vSize.width.toFloat() / vSize.height.toFloat()
                        if (rawRatio in 0.45f..2.35f) {
                            Rational(vSize.width, vSize.height)
                        } else {
                            Rational(16, 9)
                        }
                    } else {
                        Rational(16, 9)
                    }
                    onUpdatePipParams(ratio, rect)
                }
        )

        if (!isInPipMode && zoomScale > 1.02f) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, primaryColor),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 66.dp)
                    .testTag("video_zoom_reset_chip")
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1fx", zoomScale)} • اضغط للإعادة 1x",
                    color = primaryColor,
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (!isInPipMode && activeGesture != GestureAxis.NONE) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                    when (activeGesture) {
                        GestureAxis.BRIGHTNESS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = primaryColor)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "السطوع: ${(brightnessLevel * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        GestureAxis.VOLUME -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = secondaryColor)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "الصوت: ${(volumeFraction * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        GestureAxis.SEEK -> {
                            Text(
                                text = "${MediaTimeFormatter.formatMs(seekTargetMs)} / ${MediaTimeFormatter.formatMs(durationMs)}",
                                color = primaryColor,
                                style = MonoTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        GestureAxis.NONE -> Unit
                    }
                }
            }
        }

        if (!isInPipMode) {
            doubleTapFeedback?.let { label ->
                val align = if (label.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(align)
                        .padding(horizontal = 36.dp)
                ) {
                    Text(
                        text = label,
                        color = primaryColor,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isInPipMode && showControls && isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            FilledTonalIconButton(
                onClick = { isLocked = false },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock screen")
            }
        }

        AnimatedVisibility(
            visible = !isInPipMode && showControls && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = overlayContainer,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("video_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = video.artistOrDesc,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onPlayInBackgroundAudio,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = secondaryColor
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_background_audio_button")
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = "تشغيل كصوت في الخلفية")
                        }

                        FilledTonalIconButton(
                            onClick = { showTracksDialog = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = primaryColor
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_subtitles_tracks_button")
                        ) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = "الترجمة والمسارات الصوتية")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                captureVideoFrame(
                                    context = context,
                                    playerView = playerViewRef,
                                    onResult = { saved ->
                                        doubleTapFeedback = if (saved) {
                                            "تم حفظ لقطة الشاشة ✓"
                                        } else {
                                            "تعذر التقاط الشاشة"
                                        }
                                    },
                                    launchIoSave = { bitmap, onSaved ->
                                        coroutineScope.launch {
                                            val ok = saveBitmapToGallery(context, bitmap)
                                            onSaved(ok)
                                        }
                                    }
                                )
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = primaryColor
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_screenshot_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "التقاط صورة من الفيديو")
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            FilledTonalIconButton(
                                onClick = onEnterPip,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = overlayContainer,
                                    contentColor = primaryColor
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("video_pip_button")
                            ) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "نافذة مصغرة PiP")
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { isLocked = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = primaryColor
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock screen")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = primaryColor
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Resize mode")
                        }

                        Box {
                            FilledTonalIconButton(
                                onClick = { speedMenuOpen = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = overlayContainer,
                                    contentColor = primaryColor
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed")
                            }

                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${s}x",
                                                color = if (speed == s) primaryColor else MaterialTheme.colorScheme.onSurface,
                                                style = MonoTextStyle,
                                                fontWeight = if (speed == s) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            PlaybackManager.setPlaybackSpeed(s)
                                            speedMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = overlayContainer,
                                contentColor = primaryColor
                            ),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_fullscreen_button")
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate")
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { PlaybackManager.skipBackward(10) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = overlayContainer,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("video_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = { PlaybackManager.togglePlayPause() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = primaryColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(74.dp)
                            .testTag("video_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { PlaybackManager.skipForward(10) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = overlayContainer,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("video_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Progress Bar & A-B Repeat Strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { PlaybackManager.togglePointA() },
                            color = if (abLoop.pointAMs != null) MaterialTheme.colorScheme.primaryContainer else overlayContainer,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, if (abLoop.pointAMs != null) primaryColor else Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.testTag("video_ab_point_a")
                        ) {
                            Text(
                                text = if (abLoop.pointAMs != null) "A: ${MediaTimeFormatter.formatMs(abLoop.pointAMs!!)}" else "تحديد A",
                                color = if (abLoop.pointAMs != null) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.85f),
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            onClick = { PlaybackManager.togglePointB() },
                            color = if (abLoop.isActive) MaterialTheme.colorScheme.secondaryContainer else overlayContainer,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, if (abLoop.isActive) secondaryColor else Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.testTag("video_ab_point_b")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = if (abLoop.isActive) MaterialTheme.colorScheme.onSecondaryContainer else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (abLoop.pointBMs != null) "B: ${MediaTimeFormatter.formatMs(abLoop.pointBMs!!)}" else "تحديد B",
                                    color = if (abLoop.isActive) MaterialTheme.colorScheme.onSecondaryContainer else Color.White.copy(alpha = 0.85f),
                                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (abLoop.pointAMs != null || abLoop.isActive) {
                            Surface(
                                onClick = { PlaybackManager.clearABLoop() },
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, tertiaryColor),
                                modifier = Modifier.testTag("video_ab_clear")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "إلغاء التكرار", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("مسح", color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    val progressValue = if (isDraggingSlider) {
                        dragSliderValue
                    } else if (durationMs > 0) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = progressValue,
                        onValueChange = {
                            isDraggingSlider = true
                            dragSliderValue = it
                        },
                        onValueChangeFinished = {
                            val targetMs = (dragSliderValue * durationMs).toLong()
                            PlaybackManager.seekTo(targetMs)
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_progress_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayPos = if (isDraggingSlider) {
                            (dragSliderValue * durationMs).toLong()
                        } else currentPosMs

                        Text(
                            text = MediaTimeFormatter.formatMs(displayPos),
                            color = primaryColor,
                            style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "سحب: سطوع/صوت/تقديم • إصبعين: تكبير",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall
                        )

                        Text(
                            text = MediaTimeFormatter.formatMs(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MonoTextStyle.copy(fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}

private fun findSurfaceView(root: View?): SurfaceView? {
    if (root == null) return null
    if (root is SurfaceView) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val found = findSurfaceView(root.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

private fun captureVideoFrame(
    context: Context,
    playerView: PlayerView?,
    onResult: (Boolean) -> Unit,
    launchIoSave: (Bitmap, (Boolean) -> Unit) -> Unit
) {
    val surfaceView = findSurfaceView(playerView)
    if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0 || !surfaceView.holder.surface.isValid) {
        onResult(false)
        return
    }
    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    launchIoSave(bitmap, onResult)
                } else {
                    bitmap.recycle()
                    onResult(false)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (_: Exception) {
        bitmap.recycle()
        onResult(false)
    }
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val fileName = "frame_${System.currentTimeMillis()}.png"
    return@withContext try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MediaPlayer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                false
            }
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        }
    } catch (_: Exception) {
        false
    } finally {
        bitmap.recycle()
    }
}
