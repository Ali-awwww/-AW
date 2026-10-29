package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.MediaType
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import java.util.Locale

object MediaTimeFormatter {
    fun formatMs(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = ms / 1000
        val seconds = totalSec % 60
        val minutes = (totalSec / 60) % 60
        val hours = totalSec / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = NeonCyan
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val animHeight = remember { Animatable(0.3f) }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    val target = (0.3f + (index % 3) * 0.35f).coerceIn(0.2f, 1f)
                    animHeight.animateTo(
                        targetValue = target,
                        animationSpec = infiniteRepeatable(
                            animation = tween(280 + index * 90, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                } else {
                    animHeight.snapTo(0.25f)
                }
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16 * animHeight.value).dp)
                    .background(color)
            )
        }
    }
}

@Composable
fun GeometricBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth().background(com.example.ui.theme.DarkBackground)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(800.dp)) {
            val gridSpacing = 40.dp.toPx()
            val lineColor = Color.White.copy(alpha = 0.03f)
            
            // Vertical grid lines
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += gridSpacing
            }
            
            // Horizontal grid lines
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += gridSpacing
            }
        }
        content()
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DarkSurfaceElevated,
                    labelColor = TextSecondary,
                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                    selectedLabelColor = NeonCyan
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) NeonCyan else DarkSurfaceBorder,
                    selectedBorderColor = NeonCyan,
                    enabled = true,
                    selected = isSelected
                ),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.testTag("category_chip_$category")
            )
        }
    }
}

@Composable
fun AddStreamDialog(
    initialMediaType: MediaType,
    onDismiss: () -> Unit,
    onAdd: (title: String, artistOrDesc: String, url: String, type: MediaType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(initialMediaType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة رابط بث مباشر / Stream URL", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == MediaType.VIDEO,
                        onClick = { selectedType = MediaType.VIDEO },
                        leadingIcon = { Icon(Icons.Default.Videocam, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("فيديو (Video)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == MediaType.AUDIO,
                        onClick = { selectedType = MediaType.AUDIO },
                        leadingIcon = { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("صوت (Audio)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("رابط البث (URL: .mp4, .m3u8, .mp3)") },
                    placeholder = { Text("https://example.com/stream.mp4") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_url_input")
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المقطع (Title)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_title_input")
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("الوصف أو اسم الفنان (Artist / Source)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onAdd(title, desc, url.trim(), selectedType)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("submit_stream_button")
            ) {
                Text("تشغيل وإضافة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
    )
}

@Composable
fun SleepTimerDialog(
    timerMode: com.example.media.SleepTimerMode,
    currentRemainingSeconds: Int,
    isFadeOut: Boolean,
    currentTrackTitle: String?,
    onDismiss: () -> Unit,
    onSetDuration: (minutes: Int, fadeOut: Boolean) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onToggleFadeOut: () -> Unit
) {
    val presetOptions = listOf(10, 15, 30, 45, 60, 90)
    var customMinutes by remember { androidx.compose.runtime.mutableFloatStateOf(20f) }
    var fadeOutState by remember { mutableStateOf(isFadeOut) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = NeonViolet,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("مؤقت النوم (Sleep Timer)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("إيقاف الصوت تلقائياً بعد فترة محددة", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Active Timer Card
                if (timerMode != com.example.media.SleepTimerMode.OFF) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = NeonCyan.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(0.dp),
                        border = androidx.compose.material3.CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(NeonCyan.copy(alpha = 0.6f))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (timerMode == com.example.media.SleepTimerMode.END_OF_TRACK) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "نشط: يتوقف عند نهاية المقطع الحالي",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                if (!currentTrackTitle.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentTrackTitle,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "المؤقت نشط الآن:",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = MediaTimeFormatter.formatMs(currentRemainingSeconds * 1000L),
                                            color = NeonCyan,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = onAddFiveMinutes,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DarkSurfaceBorder,
                                            contentColor = NeonCyan
                                        ),
                                        shape = RoundedCornerShape(0.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("+5 دقائق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    onCancelTimer()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                                shape = RoundedCornerShape(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cancel_sleep_timer_button")
                            ) {
                                Text("إلغاء مؤقت النوم", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 1. End of Track Option
                androidx.compose.material3.Card(
                    onClick = {
                        onSetEndOfTrack()
                        onDismiss()
                    },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (timerMode == com.example.media.SleepTimerMode.END_OF_TRACK)
                            NeonViolet.copy(alpha = 0.2f)
                        else
                            DarkSurfaceBorder
                    ),
                    shape = RoundedCornerShape(0.dp),
                    border = androidx.compose.material3.CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (timerMode == com.example.media.SleepTimerMode.END_OF_TRACK) NeonViolet else Color.Transparent
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_timer_end_of_track")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(0.dp))
                                .background(NeonViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "عند نهاية المقطع الحالي (End of Track)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "إيقاف التشغيل بمجرد انتهاء المقطع الحالي تلقائياً",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 2. Preset Durations
                Text(
                    text = "أو اختر مدة زمنية محددة:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetOptions.take(3).forEach { min ->
                        Button(
                            onClick = {
                                onSetDuration(min, fadeOutState)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceBorder),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$min د", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetOptions.drop(3).forEach { min ->
                        Button(
                            onClick = {
                                onSetDuration(min, fadeOutState)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceBorder),
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$min د", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }

                // 3. Custom Duration Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .background(DarkSurfaceBorder.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تحديد مدة مخصصة:", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "${customMinutes.toInt()} دقيقة",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    androidx.compose.material3.Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSetDuration(customMinutes.toInt(), fadeOutState)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تفعيل (${customMinutes.toInt()} دقيقة)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // 4. Fade Out Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .clickable {
                            fadeOutState = !fadeOutState
                            onToggleFadeOut()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تلاشي تدريجي للصوت", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("خفض الصوت بنعومة في آخر 20 ثانية", color = TextSecondary, fontSize = 11.sp)
                    }

                    androidx.compose.material3.Switch(
                        checked = fadeOutState,
                        onCheckedChange = {
                            fadeOutState = it
                            onToggleFadeOut()
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonCyan
                        )
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.border(1.dp, NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
    )
}
