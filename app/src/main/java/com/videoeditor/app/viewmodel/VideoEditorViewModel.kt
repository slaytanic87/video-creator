package com.videoeditor.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.videoeditor.app.data.VideoFilter
import com.videoeditor.app.data.VideoProject
import com.videoeditor.app.util.FrameExtractor
import com.videoeditor.app.util.VideoExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val frameExtractor = FrameExtractor(application)
    private val videoExporter = VideoExporter(application)

    private val _project = MutableStateFlow(VideoProject())
    val project: StateFlow<VideoProject> = _project.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportSuccess = MutableStateFlow<Boolean?>(null)
    val exportSuccess: StateFlow<Boolean?> = _exportSuccess.asStateFlow()

    private val _thumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val thumbnails: StateFlow<List<Bitmap>> = _thumbnails.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    fun setVideoUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val duration = frameExtractor.getDuration(uri)
            _project.update {
                it.copy(
                    sourceUri = uri,
                    durationMs = duration,
                    trimStartMs = 0L,
                    trimEndMs = duration,
                    outputUri = uri
                )
            }
            val frames = frameExtractor.extractFrames(uri, 15)
            _thumbnails.value = frames
            _isLoading.value = false
        }
    }

    fun setTrimStart(ms: Long) {
        _project.update { it.copy(trimStartMs = ms.coerceIn(0, it.trimEndMs - 500)) }
    }

    fun setTrimEnd(ms: Long) {
        _project.update { it.copy(trimEndMs = ms.coerceIn(it.trimStartMs + 500, it.durationMs)) }
    }

    fun setBrightness(value: Float) {
        _project.update { it.copy(brightness = value.coerceIn(-1f, 1f)) }
    }

    fun setContrast(value: Float) {
        _project.update { it.copy(contrast = value.coerceIn(0f, 2f)) }
    }

    fun setSaturation(value: Float) {
        _project.update { it.copy(saturation = value.coerceIn(0f, 2f)) }
    }

    fun setFilter(filter: VideoFilter) {
        _project.update { it.copy(selectedFilter = filter) }
    }

    fun extractFrameAt(timeMs: Long) {
        viewModelScope.launch {
            val uri = _project.value.sourceUri ?: return@launch
            _currentFrame.value = frameExtractor.extractFrameAt(uri, timeMs)
        }
    }

    fun resetAdjustments() {
        _project.update {
            it.copy(
                brightness = 0f,
                contrast = 1f,
                saturation = 1f,
                selectedFilter = VideoFilter.NONE
            )
        }
    }

    fun exportPreview() {
        val currentProject: VideoProject = _project.value
        val sourceUri: Uri = currentProject.sourceUri ?: return

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            _exportSuccess.value = null

            try {
                val outputUri: Uri = videoExporter.exportPreview(
                    sourceUri = sourceUri,
                    trimStartMs = currentProject.trimStartMs,
                    trimEndMs = currentProject.trimEndMs,
                    brightness = currentProject.brightness,
                    contrast = currentProject.contrast,
                    filter = currentProject.selectedFilter,
                    onProgress = { progress -> _exportProgress.value = progress }
                )
                _project.update { it.copy(outputUri = outputUri) }
                _exportSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _exportSuccess.value = false
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportVideo() {
        val currentProject: VideoProject = _project.value
        val sourceUri: Uri = currentProject.sourceUri ?: return

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0f
            _exportSuccess.value = null

            try {
                val outputUri: Uri = videoExporter.exportVideo(
                    sourceUri = sourceUri,
                    trimStartMs = currentProject.trimStartMs,
                    trimEndMs = currentProject.trimEndMs,
                    brightness = currentProject.brightness,
                    contrast = currentProject.contrast,
                    filter = currentProject.selectedFilter,
                    onProgress = { progress -> _exportProgress.value = progress }
                )
                _project.update { it.copy(outputUri = outputUri) }
                _exportSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _exportSuccess.value = false
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearExportStatus() {
        _exportSuccess.value = null
    }

    fun getTrimmedDuration(): Long {
        val p = _project.value
        return p.trimEndMs - p.trimStartMs
    }
}
