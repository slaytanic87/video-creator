package com.videoeditor.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.videoeditor.app.ui.theme.EditorAccent
import com.videoeditor.app.ui.theme.TimelineTrack
import com.videoeditor.app.ui.theme.TrimHandle
import com.videoeditor.app.util.formatTime

@Composable
fun VideoTimeline(
    thumbnails: List<Bitmap>,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Thumbnail strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TimelineTrack)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(thumbnails) { _, bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(56.dp)
                            .height(60.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Range slider for trim
        if (durationMs > 0) {
            RangeSlider(
                value = trimStartMs.toFloat()..trimEndMs.toFloat(),
                onValueChange = { range ->
                    onTrimStartChanged(range.start.toLong())
                    onTrimEndChanged(range.endInclusive.toLong())
                },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = TrimHandle,
                    activeTrackColor = EditorAccent,
                    inactiveTrackColor = TimelineTrack
                )
            )

            // Time labels
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = formatTime(trimStartMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Text(
                    text = "Duration: ${formatTime(trimEndMs - trimStartMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorAccent,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    text = formatTime(trimEndMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
