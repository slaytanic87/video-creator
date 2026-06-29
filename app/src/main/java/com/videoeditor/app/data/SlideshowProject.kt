package com.videoeditor.app.data

import android.net.Uri

data class SlideshowImage(
    val uri: Uri,
    val effect: SlideshowEffect = SlideshowEffect.NONE,
    val filter: SlideshowFilter = SlideshowFilter.NONE
)

data class SlideshowProject(
    val imageUris: List<Uri> = emptyList(),
    val durationPerImageMs: Long = 3000L,
    val outputUri: Uri? = null
)
