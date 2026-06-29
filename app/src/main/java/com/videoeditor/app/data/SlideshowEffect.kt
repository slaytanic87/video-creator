package com.videoeditor.app.data

enum class SlideshowEffect(val displayName: String) {
    NONE("None"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    KEN_BURNS("Ken Burns"),
    FADE_IN("Fade In"),
    FADE_OUT("Fade Out"),
    FADE_IN_OUT("Fade In & Out"),
    SLOW_MOTION("Slow Motion")
}

enum class SlideshowFilter(val displayName: String) {
    NONE("None"),
    CONTRAST("Contrast"),
    SEPIA("Sepia"),
    WARM("Warm Color")
}
