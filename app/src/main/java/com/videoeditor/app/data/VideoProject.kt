package com.videoeditor.app.data

import android.graphics.Bitmap
import android.net.Uri

data class VideoProject(
    val sourceUri: Uri? = null,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val selectedFilter: VideoFilter = VideoFilter.NONE,
    val frames: List<Bitmap> = emptyList(),
    val outputUri: Uri? = null
)

enum class VideoFilter(val displayName: String) {
    NONE("None"),
    GRAYSCALE("Grayscale"),
    SEPIA("Sepia"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    HIGH_CONTRAST("High Contrast")
}
