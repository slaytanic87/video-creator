package com.videoeditor.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.videoeditor.app.ui.components.AdjustmentSlider
import com.videoeditor.app.ui.components.FilterSelector
import com.videoeditor.app.ui.components.VideoPlayerView
import com.videoeditor.app.ui.components.VideoTimeline
import com.videoeditor.app.ui.theme.EditorAccent
import com.videoeditor.app.ui.theme.EditorBackground
import com.videoeditor.app.ui.theme.EditorSurface
import com.videoeditor.app.viewmodel.VideoEditorViewModel

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    viewModel: VideoEditorViewModel,
    onNavigateToFrameEditor: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportSuccess by viewModel.exportSuccess.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportSuccess) {
        exportSuccess?.let { success ->
            snackbarHostState.showSnackbar(
                if (success) "Video saved successfully!" else "Error saving video"
            )
            viewModel.clearExportStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Video",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorBackground)
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = EditorAccent
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Video player
                    project.sourceUri?.let { uri ->
                        VideoPlayerView(
                            uri = uri,
                            startPositionMs = project.trimStartMs,
                            endPositionMs = project.trimEndMs,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Section: Trim
                    SectionHeader(
                        icon = Icons.Filled.ContentCut,
                        title = "Trim Video"
                    )

                    VideoTimeline(
                        thumbnails = thumbnails,
                        durationMs = project.durationMs,
                        trimStartMs = project.trimStartMs,
                        trimEndMs = project.trimEndMs,
                        onTrimStartChanged = { viewModel.setTrimStart(it) },
                        onTrimEndChanged = { viewModel.setTrimEnd(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section: Adjustments
                    SectionHeader(
                        icon = Icons.Filled.Brush,
                        title = "Adjustments"
                    )

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

                    // Filters
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )

                    FilterSelector(
                        selectedFilter = project.selectedFilter,
                        onFilterSelected = { viewModel.setFilter(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Button(
                            onClick = onNavigateToFrameEditor,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EditorSurface)
                        ) {
                            Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Frames")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onNavigateToPreview,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EditorSurface)
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Export button
                    Button(
                        onClick = { viewModel.exportVideo() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EditorAccent),
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Exporting...")
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Video", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = EditorAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
