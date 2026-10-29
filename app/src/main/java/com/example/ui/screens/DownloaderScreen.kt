package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.DownloadItem
import com.example.media.DownloadStatus
import com.example.media.MediaPlayerViewModel
import com.example.media.MediaType
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DownloaderScreen(
    viewModel: MediaPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val downloadUrl by viewModel.downloadUrl.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(com.example.ui.components.HexagonShape())
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), com.example.ui.components.HexagonShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "مركز التحميل (Downloader)",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // URL Input
        val detectedPlatform = remember(downloadUrl) {
            when {
                downloadUrl.contains("youtube.com") || downloadUrl.contains("youtu.be") -> "YouTube"
                downloadUrl.contains("instagram.com") -> "Instagram"
                downloadUrl.contains("tiktok.com") -> "TikTok"
                downloadUrl.contains("facebook.com") -> "Facebook"
                downloadUrl.isNotBlank() -> "رابط مباشر (Direct Link)"
                else -> null
            }
        }

        val isExtracting by viewModel.isExtracting.collectAsState()
        val metadata by viewModel.extractedMetadata.collectAsState()

        OutlinedTextField(
            value = downloadUrl,
            onValueChange = { viewModel.setDownloadUrl(it) },
            placeholder = { Text("ضع رابط الفيديو أو الموسيقى هنا...", color = TextMuted) },
            leadingIcon = {
                if (downloadUrl.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setDownloadUrl("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            trailingIcon = {
                IconButton(onClick = {
                    clipboardManager.getText()?.let { viewModel.setDownloadUrl(it.text) }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = NeonCyan)
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DarkSurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download_url_input")
        )

        // Extraction Status / Metadata
        AnimatedVisibility(visible = isExtracting) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("جاري استخراج معلومات الفيديو...", color = TextSecondary, fontSize = 12.sp)
            }
        }

        metadata?.let { info ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = info.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(0.dp)),
                            contentScale = androidx.compose.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(info.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(info.platform, color = NeonCyan, fontSize = 11.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("اختر الجودة (Select Quality):", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.qualities.forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        val type = if (quality.label.contains("Audio")) MediaType.AUDIO else MediaType.VIDEO
                                        viewModel.startDownload(downloadUrl, type, quality.label) 
                                    }
                                    .background(DarkBackground)
                                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(0.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (quality.label.contains("Audio")) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = if (quality.label.contains("Audio")) NeonPink else NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(quality.label, color = TextPrimary, fontSize = 13.sp)
                                }
                                Text(quality.sizeLabel, color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (metadata == null && !isExtracting) {
            detectedPlatform?.let {
                Text(
                    text = "المنصة المكتشفة: $it",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Actions (Direct)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startDownload(downloadUrl, MediaType.VIDEO) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحميل فيديو", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.startDownload(downloadUrl, MediaType.AUDIO) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحميل موسيقى", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Downloads List
        Text(
            text = "التحميلات الحالية (${activeDownloads.size})",
            color = TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد تحميلات نشطة", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeDownloads, key = { it.id }) { download ->
                    DownloadCard(
                        download = download,
                        onDelete = { viewModel.removeDownload(download.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadCard(
    download: DownloadItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_item_${download.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (download.mediaType == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (download.mediaType == MediaType.VIDEO) NeonCyan else NeonPink,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = download.fileName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = download.progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(0.dp)),
                    color = if (download.mediaType == MediaType.VIDEO) NeonCyan else NeonPink,
                    trackColor = DarkSurfaceBorder
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(download.progress * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (download.status) {
                    DownloadStatus.QUEUED -> "في الانتظار..."
                    DownloadStatus.DOWNLOADING -> "جاري التحميل..."
                    DownloadStatus.COMPLETED -> "تم التحميل بنجاح"
                    DownloadStatus.FAILED -> "فشل التحميل"
                },
                color = when (download.status) {
                    DownloadStatus.COMPLETED -> Color.Green
                    DownloadStatus.FAILED -> Color.Red
                    else -> TextMuted
                },
                fontSize = 11.sp
            )
        }
    }
}
