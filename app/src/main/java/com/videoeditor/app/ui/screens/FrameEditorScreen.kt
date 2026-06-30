package com.videoeditor.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.videoeditor.app.ui.components.AdjustmentSlider
import com.videoeditor.app.ui.theme.EditorAccent
import com.videoeditor.app.ui.theme.EditorBackground
import com.videoeditor.app.ui.theme.EditorSurface
import com.videoeditor.app.ui.theme.TimelineTrack
import com.videoeditor.app.viewmodel.VideoEditorViewModel

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameEditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentFrameIndex) {
        if (project.sourceUri != null && project.durationMs > 0) {
            val timeMs: Long = (project.trimStartMs +
                (currentFrameIndex.toFloat() / maxOf(thumbnails.size - 1, 1)) *
                (project.trimEndMs - project.trimStartMs)).toLong()
            viewModel.extractFrameAt(timeMs)
        }
    }

    val currentFrame by viewModel.currentFrame.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Frames",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetAdjustments() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reset",
                            tint = EditorAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorBackground)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Frame preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TimelineTrack),
                contentAlignment = Alignment.Center
            ) {
                val displayBitmap: Bitmap? = currentFrame ?: thumbnails.getOrNull(currentFrameIndex)
                if (displayBitmap != null) {
                    val adjustedBitmap: Bitmap = applyAdjustments(
                        displayBitmap,
                        project.brightness,
                        project.contrast,
                        project.saturation
                    )
                    Image(
                        bitmap = adjustedBitmap.asImageBitmap(),
                        contentDescription = "Current frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        "No frame available",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Frame navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentFrameIndex > 0) currentFrameIndex--
                    },
                    enabled = currentFrameIndex > 0
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous frame",
                        tint = if (currentFrameIndex > 0) EditorAccent else TimelineTrack,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Frame ${currentFrameIndex + 1} / ${maxOf(thumbnails.size, 1)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        if (currentFrameIndex < thumbnails.size - 1) currentFrameIndex++
                    },
                    enabled = currentFrameIndex < thumbnails.size - 1
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next frame",
                        tint = if (currentFrameIndex < thumbnails.size - 1) EditorAccent else TimelineTrack,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Frame scrubber
            Slider(
                value = if (thumbnails.isNotEmpty()) {
                    currentFrameIndex.toFloat() / maxOf(thumbnails.size - 1, 1)
                } else 0f,
                onValueChange = { value ->
                    scrubPosition = value
                    currentFrameIndex = (value * maxOf(thumbnails.size - 1, 1)).toInt()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = EditorAccent,
                    activeTrackColor = EditorAccent,
                    inactiveTrackColor = TimelineTrack
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Adjustment controls
            Text(
                text = "Frame Adjustments",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            AdjustmentSlider(
                label = "Brightness",
                value = project.brightness,
                onValueChange = { viewModel.setBrightness(it) },
                valueRange = -1f..1f
            )

            AdjustmentSlider(
                label = "Contrast",
                value = project.contrast,
                onValueChange = { viewModel.setContrast(it) },
                valueRange = 0f..2f
            )

            AdjustmentSlider(
                label = "Saturation",
                value = project.saturation,
                onValueChange = { viewModel.setSaturation(it) },
                valueRange = 0f..2f
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reset button
            Button(
                onClick = { viewModel.resetAdjustments() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorSurface)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Adjustments")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun applyAdjustments(
    source: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float
): Bitmap {
    val output: Bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()

    val cm = ColorMatrix()

    // Apply brightness
    val brightnessMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, brightness * 255f,
            0f, 1f, 0f, 0f, brightness * 255f,
            0f, 0f, 1f, 0f, brightness * 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm.postConcat(brightnessMatrix)

    // Apply contrast
    val scale = contrast
    val translate = (-.5f * scale + .5f) * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm.postConcat(contrastMatrix)

    // Apply saturation
    val satMatrix = ColorMatrix()
    satMatrix.setSaturation(saturation)
    cm.postConcat(satMatrix)

    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(source, 0f, 0f, paint)

    return output
}
