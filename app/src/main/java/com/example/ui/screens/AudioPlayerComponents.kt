package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.media.MediaItemData
import com.example.media.MediaPlayerViewModel
import com.example.media.PlaybackManager
import com.example.media.RepeatState
import com.example.ui.components.MediaTimeFormatter
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BottomMiniPlayer(
    viewModel: MediaPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentItem by PlaybackManager.currentMediaItem.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()
    val currentPosMs by PlaybackManager.currentPositionMs.collectAsState()
    val durationMs by PlaybackManager.durationMs.collectAsState()

    if (currentItem == null) return

    val progress = if (durationMs > 0) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = { viewModel.expandAudioPlayer(true) },
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonViolet.copy(alpha = 0.5f)))
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(0.dp), spotColor = NeonCyan.copy(alpha = 0.2f))
            .testTag("mini_player_dock")
    ) {
        Column {
            // Linear Progress Line at Top Edge
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = NeonCyan,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail with Waveform
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentItem?.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            WaveformVisualizer(isPlaying = true, color = NeonCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem?.title ?: "",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentItem?.artistOrDesc ?: "",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val sleepTimerMode by viewModel.sleepTimerMode.collectAsState()
                        if (sleepTimerMode != com.example.media.SleepTimerMode.OFF) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Sleep timer active",
                                tint = NeonViolet,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Controls
                IconButton(
                    onClick = { PlaybackManager.togglePlayPause() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { PlaybackManager.next() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenAudioPlayer(
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val currentItem by PlaybackManager.currentMediaItem.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()
    val currentPosMs by PlaybackManager.currentPositionMs.collectAsState()
    val durationMs by PlaybackManager.durationMs.collectAsState()
    val isShuffle by PlaybackManager.isShuffle.collectAsState()
    val repeatMode by PlaybackManager.repeatMode.collectAsState()
    val speed by PlaybackManager.playbackSpeed.collectAsState()
    val sleepSeconds by viewModel.sleepTimerSeconds.collectAsState()
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsState()
    val isFadeOut by viewModel.isFadeOutEnabled.collectAsState()

    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    // Slider state
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    if (showSleepDialog) {
        SleepTimerDialog(
            timerMode = sleepTimerMode,
            currentRemainingSeconds = sleepSeconds,
            isFadeOut = isFadeOut,
            currentTrackTitle = currentItem?.title,
            onDismiss = { showSleepDialog = false },
            onSetDuration = { minutes, fadeOut -> viewModel.setSleepTimerDuration(minutes, fadeOut) },
            onSetEndOfTrack = { viewModel.setSleepTimerEndOfTrack() },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onAddFiveMinutes = { viewModel.addFiveMinutes() },
            onToggleFadeOut = { viewModel.toggleFadeOut() }
        )
    }

    if (showEqDialog) {
        com.example.ui.components.EqualizerDialog(
            viewModel = viewModel,
            onDismiss = { showEqDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkSurfaceElevated,
                            DarkBackground,
                            DarkBackground
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(DarkSurfaceBorder)
                            .testTag("audio_player_collapse")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "مشغل الصوت (NOW PLAYING)",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = currentItem?.category ?: "VibePlayer",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Sleep Timer Button with Active State
                        val isTimerActive = sleepTimerMode != com.example.media.SleepTimerMode.OFF
                        Surface(
                            onClick = { showSleepDialog = true },
                            shape = RoundedCornerShape(0.dp),
                            color = if (isTimerActive) NeonViolet.copy(alpha = 0.25f) else DarkSurfaceBorder,
                            border = if (isTimerActive) androidx.compose.foundation.BorderStroke(1.dp, NeonViolet) else null,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("open_sleep_timer_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Sleep timer",
                                    tint = if (isTimerActive) NeonViolet else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (sleepTimerMode == com.example.media.SleepTimerMode.DURATION) {
                                    Text(
                                        text = MediaTimeFormatter.formatMs(sleepSeconds * 1000L),
                                        color = NeonViolet,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (sleepTimerMode == com.example.media.SleepTimerMode.END_OF_TRACK) {
                                    Text(
                                        text = "نهاية المقطع",
                                        color = NeonViolet,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Speed Button
                        Box {
                            IconButton(
                                onClick = { speedMenuOpen = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(0.dp))
                                    .background(DarkSurfaceBorder)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                                modifier = Modifier.background(DarkSurfaceElevated)
                            ) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${s}x",
                                                color = if (speed == s) NeonCyan else TextPrimary,
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

                        // Equalizer Button
                        IconButton(
                            onClick = { showEqDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(0.dp))
                                .background(DarkSurfaceBorder)
                                .testTag("open_equalizer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Glowing Album Artwork with rotation
                val rotationAngle = remember { Animatable(0f) }
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        rotationAngle.animateTo(
                            targetValue = rotationAngle.value + 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(24000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                    } else {
                        rotationAngle.stop()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(0.dp),
                            spotColor = NeonViolet.copy(alpha = 0.5f),
                            ambientColor = NeonCyan.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(0.dp))
                        .border(3.dp, Brush.sweepGradient(listOf(NeonCyan, NeonViolet, NeonPink, NeonCyan)), RoundedCornerShape(0.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentItem?.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = currentItem?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle.value)
                    )

                    // Vinyl Center Hole
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(DarkBackground)
                            .border(2.dp, NeonCyan, RoundedCornerShape(0.dp))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title, Artist & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem?.title ?: "No track playing",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentItem?.artistOrDesc ?: "",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    currentItem?.let { item ->
                        IconButton(
                            onClick = { viewModel.toggleFavorite(item) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) NeonPink else TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Progress Bar & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderProgress = if (isDraggingSlider) {
                        dragValue
                    } else if (durationMs > 0) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = sliderProgress,
                        onValueChange = {
                            isDraggingSlider = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            val targetMs = (dragValue * durationMs).toLong()
                            PlaybackManager.seekTo(targetMs)
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("full_audio_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayPos = if (isDraggingSlider) (dragValue * durationMs).toLong() else currentPosMs
                        Text(
                            text = MediaTimeFormatter.formatMs(displayPos),
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = MediaTimeFormatter.formatMs(durationMs),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = { PlaybackManager.toggleShuffle() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) NeonCyan else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { PlaybackManager.previous() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(DarkSurfaceBorder)
                            .testTag("audio_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Large Play/Pause
                    IconButton(
                        onClick = { PlaybackManager.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(
                                Brush.linearGradient(listOf(NeonCyan, NeonViolet))
                            )
                            .shadow(16.dp, RoundedCornerShape(0.dp), spotColor = NeonCyan)
                            .testTag("audio_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { PlaybackManager.next() },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(DarkSurfaceBorder)
                            .testTag("audio_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Repeat Mode
                    IconButton(
                        onClick = { PlaybackManager.cycleRepeatMode() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (repeatMode == RepeatState.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != RepeatState.OFF) NeonViolet else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
