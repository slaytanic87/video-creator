package com.videoeditor.app.util

fun formatTime(ms: Long): String {
    val totalSeconds: Long = ms / 1000
    val minutes: Long = totalSeconds / 60
    val seconds: Long = totalSeconds % 60
    val millis: Long = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, millis)
}

fun formatDuration(ms: Long): String {
    val totalSeconds: Long = ms / 1000
    val hours: Long = totalSeconds / 3600
    val minutes: Long = (totalSeconds % 3600) / 60
    val seconds: Long = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
