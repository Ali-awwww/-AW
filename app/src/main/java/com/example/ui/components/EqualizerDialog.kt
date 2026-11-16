package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.DefaultEqualizerData
import com.example.media.EqualizerBand
import com.example.media.MediaPlayerViewModel
import com.example.ui.theme.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit
) {
    val eqState by viewModel.equalizerState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "معادل الصوت",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (eqState.isEnabled) "مفعل ومضبوط" else "معطل (صوت مباشر)",
                            color = if (eqState.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("equalizer_master_switch")
                )
            }

            // Smooth Frequency Spectrum Curve Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                EqualizerCurveVisualizer(
                    bands = eqState.bands,
                    isEnabled = eqState.isEnabled,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.secondary,
                    mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Preset Selector Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الأنماط الجاهزة",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.selectEqualizerPreset("flat") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "إعادة ضبط",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("preset_chip_${preset.id}")
                        )
                    }
                }
            }

            // 5-Band Vertical Equalizer Faders
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                        .padding(horizontal = 10.dp, vertical = 14.dp),
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
            }

            // Bass Boost Slider Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تضخيم الباس (Bass Boost)",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = "${eqState.bassBoostStrength}%",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge.merge(MonoTextStyle),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = eqState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = eqState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bass_boost_slider")
                    )
                }
            }

            // Loudness Preamp Gain Slider Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مضخم الصوت الإضافي (Preamp Gain)",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${eqState.preampGainPercent}%",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelLarge.merge(MonoTextStyle),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = eqState.preampGainPercent.toFloat(),
                        onValueChange = { viewModel.setPreampGain(it.toInt()) },
                        valueRange = 0f..100f,
                        enabled = eqState.isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preamp_gain_slider")
                    )
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("equalizer_done_button")
            ) {
                Text("تم وحفظ الإعدادات", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EqualizerCurveVisualizer(
    bands: List<EqualizerBand>,
    isEnabled: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (bands.isEmpty()) return@Canvas

        val stepX = width / (bands.size - 1).coerceAtLeast(1)
        val points = bands.mapIndexed { index, band ->
            val x = index * stepX
            val normalized = if (isEnabled) {
                ((band.levelDb.toFloat() + 10f) / 20f).coerceIn(0f, 1f)
            } else 0.5f
            val y = height - (normalized * height)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX = (p0.x + p1.x) / 2f
                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        val gradient = Brush.verticalGradient(
            colors = if (isEnabled) listOf(
                primaryColor.copy(alpha = 0.35f),
                secondaryColor.copy(alpha = 0.18f),
                Color.Transparent
            ) else listOf(
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            )
        )

        drawPath(fillPath, gradient)

        drawPath(
            path = path,
            color = if (isEnabled) primaryColor else mutedColor,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(
                color = if (isEnabled) secondaryColor else mutedColor,
                radius = 4.dp.toPx(),
                center = pt
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
    val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}"
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val disabledColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${if (band.levelDb > 0) "+" else ""}${band.levelDb}",
            color = if (isEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )

        BoxWithConstraints(
            modifier = Modifier
                .width(38.dp)
                .weight(1f)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val totalHeightPx = constraints.maxHeight.toFloat()
            val availableH = maxHeight - 24.dp
            val normalizedLevel = ((band.levelDb.toFloat() + 10f) / 20f).coerceIn(0f, 1f)
            val topOffset = availableH * (1f - normalizedLevel)

            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(1.5.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            )

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
                        .size(30.dp, 22.dp)
                        .align(Alignment.TopCenter)
                        .shadow(4.dp, RoundedCornerShape(10.dp), spotColor = primaryColor)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isEnabled) Brush.verticalGradient(listOf(primaryColor, secondaryColor))
                            else Brush.verticalGradient(listOf(disabledColor, disabledColor))
                        )
                        .border(
                            1.dp,
                            if (isEnabled) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                }
            }
        }

        Text(
            text = freqLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyle.copy(fontSize = 11.sp)
        )
    }
}
