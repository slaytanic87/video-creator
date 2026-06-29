package com.videoeditor.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object VideoEditor : Screen("video_editor")
    data object FrameEditor : Screen("frame_editor")
    data object Preview : Screen("preview")
    data object SlideshowCreator : Screen("slideshow_creator")
}
