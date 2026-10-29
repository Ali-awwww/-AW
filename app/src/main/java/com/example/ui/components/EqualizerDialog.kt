package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.media.DefaultEqualizerData
import com.example.media.EqualizerBand
import com.example.media.MediaPlayerViewModel
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
fun EqualizerDialog(
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val eqState by viewModel.equalizerState.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(0.dp))
                .border(1.dp, Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonViolet.copy(alpha = 0.5f))), RoundedCornerShape(0.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Master Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(0.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(0.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "معادل الصوت (Equalizer)",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (eqState.isEnabled) "مفعل ومضبوط" else "معطل (صوت مباشر)",
                                color = if (eqState.isEnabled) NeonCyan else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = eqState.isEnabled,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = NeonCyan,
                                uncheckedTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.testTag("equalizer_master_switch")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }

                // Interactive Frequency Spectrum Curve Canvas
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                    shape = RoundedCornerShape(0.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                ) {
                    EqualizerCurveVisualizer(
                        bands = eqState.bands,
                        isEnabled = eqState.isEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Preset Selector Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الأنماط الجاهزة (Presets):",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Quick Reset
                        Row(
                            modifier = Modifier
                                .clickable { viewModel.selectEqualizerPreset("flat") }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة ضبط", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DefaultEqualizerData.presets) { preset ->
                            val isSelected = eqState.currentPresetId == preset.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectEqualizerPreset(preset.id) },
                                label = {
                                    Text(
                                        text = preset.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceBorder,
                                    labelColor = TextSecondary,
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) NeonCyan else Color.Transparent,
                                    selectedBorderColor = NeonCyan,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                shape = RoundedCornerShape(0.dp),
                                modifier = Modifier.testTag("preset_chip_${preset.id}")
                            )
                        }
                    }
                }

                // 5-Band Vertical Equalizer Faders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(DarkBackground.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    eqState.bands.forEach { band ->
                        VerticalEqualizerFader(
                            band = band,
                            isEnabled = eqState.isEnabled,
                            onLevelChange = { newDb ->
                                viewModel.setEqualizerBandLevel(band.index, newDb)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bass Boost Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .background(DarkSurfaceBorder.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تضخيم الباس (Bass Boost):",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${eqState.bassBoostStrength}%",
                            color = NeonViolet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = eqState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = eqState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonViolet,
                            activeTrackColor = NeonViolet,
                            inactiveTrackColor = DarkSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bass_boost_slider")
                    )
                }

                // Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("equalizer_done_button")
                ) {
                    Text("تم وحفظ الإعدادات", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun EqualizerCurveVisualizer(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        // Center 0dB dashed line
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (bands.isEmpty()) return@Canvas

        val stepX = width / (bands.size - 1)
        val points = bands.mapIndexed { index, band ->
            val x = index * stepX
            // -10dB to +10dB maps from height to 0
            val normalized = if (isEnabled) {
                ((band.levelDb.toFloat() + 10f) / 20f).coerceIn(0f, 1f)
            } else 0.5f
            val y = height - (normalized * height)
            Offset(x, y)
        }

        // Create geometric polygonal path
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        // Fill area under curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        val gradient = Brush.verticalGradient(
            colors = if (isEnabled) listOf(
                NeonCyan.copy(alpha = 0.4f),
                NeonViolet.copy(alpha = 0.2f),
                Color.Transparent
            ) else listOf(
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            )
        )

        drawPath(fillPath, gradient)

        // Draw curve line
        drawPath(
            path,
            color = if (isEnabled) NeonCyan else TextMuted,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Draw frequency points as squares
        points.forEach { pt ->
            drawRect(
                color = if (isEnabled) NeonViolet else TextMuted,
                size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()),
                topLeft = Offset(pt.x - 3.dp.toPx(), pt.y - 3.dp.toPx())
            )
        }
    }
}

@Composable
fun VerticalEqualizerFader(
    band: EqualizerBand,
    isEnabled: Boolean,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val freqLabel = when {
        band.centerFreqHz >= 1000 -> "${band.centerFreqHz / 1000}k"
        else -> "${band.centerFreqHz}"
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // dB Text
        Text(
            text = "${if (band.levelDb > 0) "+" else ""}${band.levelDb}",
            color = if (isEnabled) NeonCyan else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        // Tactile Vertical Slider Track
        BoxWithConstraints(
            modifier = Modifier
                .width(36.dp)
                .weight(1f)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val totalHeightPx = constraints.maxHeight.toFloat()
            val availableH = maxHeight - 22.dp
            val normalizedLevel = ((band.levelDb.toFloat() + 10f) / 20f).coerceIn(0f, 1f)
            val topOffset = availableH * (1f - normalizedLevel)

            // Track Background
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(0.dp))
                    .background(DarkSurfaceBorder)
            )

            // Center zero tick
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )

            // Draggable Touch Area & Handle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isEnabled) {
                        if (isEnabled) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaDb = -(dragAmount / totalHeightPx) * 20f
                                val targetDb = (band.levelDb + deltaDb).toInt().coerceIn(-10, 10)
                                if (targetDb != band.levelDb) {
                                    onLevelChange(targetDb)
                                }
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = topOffset)
                        .size(30.dp, 20.dp)
                        .align(Alignment.TopCenter)
                        .shadow(6.dp, RoundedCornerShape(0.dp), spotColor = NeonCyan)
                        .clip(RoundedCornerShape(0.dp))
                        .background(
                            if (isEnabled) Brush.verticalGradient(listOf(NeonCyan, NeonViolet))
                            else Brush.verticalGradient(listOf(DarkSurfaceBorder, DarkSurfaceBorder))
                        )
                        .border(
                            1.dp,
                            if (isEnabled) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                            RoundedCornerShape(0.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                    )
                }
            }
        }

        // Frequency Label
        Text(
            text = freqLabel,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
