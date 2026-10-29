package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.media.MediaItemData
import com.example.media.MediaPlayerViewModel
import com.example.media.MediaType
import com.example.media.PlaybackManager
import com.example.ui.components.AddStreamDialog
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.MediaTimeFormatter
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MusicScreen(
    viewModel: MediaPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audios by viewModel.filteredAudios.collectAsState()
    val allAudios by viewModel.allAudios.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedAudioCategory.collectAsState()
    val currentPlayingItem by PlaybackManager.currentMediaItem.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importLocalMedia(it, context, isVideo = false)
        }
    }

    val categories = remember(allAudios) {
        listOf("All") + allAudios.map { it.category }.distinct()
    }

    if (showAddDialog) {
        AddStreamDialog(
            initialMediaType = MediaType.AUDIO,
            onDismiss = { showAddDialog = false },
            onAdd = { title, desc, url, type ->
                viewModel.addCustomMedia(title, desc, url, "", type)
            }
        )
    }

    if (showEqualizerDialog) {
        com.example.ui.components.EqualizerDialog(
            viewModel = viewModel,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(0.dp))
                                .background(NeonViolet.copy(alpha = 0.15f))
                                .border(1.dp, NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(0.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "الموسيقى (Music)",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet, contentColor = Color.White),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.testTag("add_audio_stream_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رابط صوت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonViolet),
                            modifier = Modifier.testTag("import_local_audio_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("من الهاتف", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = { showEqualizerDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(0.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .testTag("music_screen_equalizer_button")
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

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("ابحث في المقاطع الصوتية...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_audio_input")
                )

                // Category Filter
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { viewModel.setAudioCategory(it) }
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Empty state
        if (audios.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد مقاطع صوتية مطابقة",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Track Items
        items(audios, key = { it.id }) { audio ->
            val isCurrent = currentPlayingItem?.id == audio.id

            AudioTrackRowItem(
                audio = audio,
                isCurrentPlaying = isCurrent && isPlaying,
                isSelected = isCurrent,
                onClick = { viewModel.playAudio(audio) },
                onToggleFavorite = { viewModel.toggleFavorite(audio) }
            )
        }
    }
}

@Composable
fun AudioTrackRowItem(
    audio: MediaItemData,
    isCurrentPlaying: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonViolet.copy(alpha = 0.12f) else DarkSurfaceElevated
        ),
        shape = RoundedCornerShape(0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) NeonCyan else DarkSurfaceBorder)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_track_${audio.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .border(1.dp, if (isSelected) NeonCyan else Color.Transparent, RoundedCornerShape(0.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(audio.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = audio.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WaveformVisualizer(isPlaying = true, color = NeonCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    color = if (isSelected) NeonCyan else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = audio.artistOrDesc,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (audio.durationMs > 0) {
                        Text(text = " • ", color = TextMuted, fontSize = 12.sp)
                        Text(
                            text = MediaTimeFormatter.formatMs(audio.durationMs),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (audio.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (audio.isFavorite) NeonPink else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
