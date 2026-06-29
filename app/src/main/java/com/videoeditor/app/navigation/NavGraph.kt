package com.videoeditor.app.navigation

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.videoeditor.app.ui.screens.FrameEditorScreen
import com.videoeditor.app.ui.screens.HomeScreen
import com.videoeditor.app.ui.screens.PreviewScreen
import com.videoeditor.app.ui.screens.SlideshowCreatorScreen
import com.videoeditor.app.ui.screens.VideoEditorScreen
import com.videoeditor.app.viewmodel.SlideshowViewModel
import com.videoeditor.app.viewmodel.VideoEditorViewModel

@OptIn(UnstableApi::class)
@Composable
fun NavGraph(navController: NavHostController, viewModel: VideoEditorViewModel = viewModel()) {
    val slideshowViewModel: SlideshowViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToEditor = { navController.navigate(Screen.VideoEditor.route) },
                onNavigateToSlideshow = { navController.navigate(Screen.SlideshowCreator.route) }
            )
        }
        composable(Screen.VideoEditor.route) {
            VideoEditorScreen(
                viewModel = viewModel,
                onNavigateToFrameEditor = { navController.navigate(Screen.FrameEditor.route) },
                onNavigateToPreview = { navController.navigate(Screen.Preview.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.FrameEditor.route) {
            FrameEditorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Preview.route) {
            PreviewScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.SlideshowCreator.route) {
            SlideshowCreatorScreen(
                viewModel = slideshowViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
