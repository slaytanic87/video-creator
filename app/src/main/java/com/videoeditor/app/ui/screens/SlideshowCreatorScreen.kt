package com.videoeditor.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoeditor.app.data.SlideshowEffect
import com.videoeditor.app.data.SlideshowFilter
import com.videoeditor.app.ui.theme.EditorAccent
import com.videoeditor.app.ui.theme.EditorBackground
import com.videoeditor.app.ui.theme.EditorSurface
import com.videoeditor.app.viewmodel.SlideshowViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowCreatorScreen(
    viewModel: SlideshowViewModel,
    onNavigateBack: () -> Unit
) {
    val images by viewModel.images.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val audioUri by viewModel.audioUri.collectAsState()
    val durationPerImageSec by viewModel.durationPerImageSec.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val creationSuccess by viewModel.creationSuccess.collectAsState()

    val selectedImage = if (selectedIndex in images.indices) images[selectedIndex] else null

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setAudioUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
    ) {
        TopAppBar(
            title = { Text("Create Video from Images") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = EditorSurface,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image grid
            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            tint = EditorAccent.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select images to create a video slideshow",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "Tap an image to select it, then choose effect/filter below",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(images) { index, image ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp, EditorAccent, RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable { viewModel.selectImage(index) }
                        ) {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Effect/filter badge
                            val badges = mutableListOf<String>()
                            if (image.effect != SlideshowEffect.NONE) badges.add(image.effect.displayName)
                            if (image.filter != SlideshowFilter.NONE) badges.add(image.filter.displayName)
                            if (badges.isNotEmpty()) {
                                Text(
                                    text = badges.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            // Remove button
                            IconButton(
                                onClick = { viewModel.removeImage(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Per-image Effect selector (only when image selected)
            if (selectedImage != null) {
                Column {
                    Text(
                        text = "Effect for image ${selectedIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SlideshowEffect.entries.forEach { effect ->
                            val isActive = effect == selectedImage.effect
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) EditorAccent else EditorSurface)
                                    .clickable { viewModel.setEffectForSelected(effect) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = effect.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Per-image Filter selector
                Column {
                    Text(
                        text = "Filter for image ${selectedIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SlideshowFilter.entries.forEach { filter ->
                            val isActive = filter == selectedImage.filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) EditorAccent else EditorSurface)
                                    .clickable { viewModel.setFilterForSelected(filter) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = filter.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Duration slider
            Column {
                Text(
                    text = "Duration per image: ${durationPerImageSec}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Slider(
                    value = durationPerImageSec.toFloat(),
                    onValueChange = { viewModel.setDurationPerImage(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = EditorAccent,
                        activeTrackColor = EditorAccent
                    )
                )
                Text(
                    text = "Total duration: ${images.size * durationPerImageSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Audio section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorSurface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = EditorAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (audioUri != null) {
                    Text(
                        text = "Audio attached",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { viewModel.removeAudio() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicOff,
                            contentDescription = "Remove audio",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Text(
                        text = "No background audio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = EditorAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Add Audio", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorSurface)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Images")
                }

                Button(
                    onClick = { viewModel.createVideo() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = images.isNotEmpty() && !isCreating,
                    colors = ButtonDefaults.buttonColors(containerColor = EditorAccent)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${(progress * 100).toInt()}%")
                    } else {
                        Icon(Icons.Filled.Movie, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Video")
                    }
                }
            }

            // Status message
            creationSuccess?.let { success ->
                Text(
                    text = if (success) "Video created successfully! Saved to Movies/VideoEditor."
                    else "Failed to create video. Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (success) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
