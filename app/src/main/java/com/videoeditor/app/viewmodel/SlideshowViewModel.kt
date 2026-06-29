package com.videoeditor.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videoeditor.app.data.SlideshowEffect
import com.videoeditor.app.data.SlideshowFilter
import com.videoeditor.app.data.SlideshowImage
import com.videoeditor.app.util.ImageToVideoConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SlideshowViewModel(application: Application) : AndroidViewModel(application) {

    private val converter = ImageToVideoConverter(application)

    private val _images = MutableStateFlow<List<SlideshowImage>>(emptyList())
    val images: StateFlow<List<SlideshowImage>> = _images.asStateFlow()

    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _audioUri = MutableStateFlow<Uri?>(null)
    val audioUri: StateFlow<Uri?> = _audioUri.asStateFlow()

    private val _durationPerImageSec = MutableStateFlow(3)
    val durationPerImageSec: StateFlow<Int> = _durationPerImageSec.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _creationSuccess = MutableStateFlow<Boolean?>(null)
    val creationSuccess: StateFlow<Boolean?> = _creationSuccess.asStateFlow()

    fun addImages(uris: List<Uri>) {
        _images.value = _images.value + uris.map { SlideshowImage(uri = it) }
    }

    fun removeImage(index: Int) {
        val current = _images.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _images.value = current
            if (_selectedIndex.value >= current.size) {
                _selectedIndex.value = current.size - 1
            }
            if (current.isEmpty()) {
                _selectedIndex.value = -1
            }
        }
    }

    fun selectImage(index: Int) {
        _selectedIndex.value = if (index in _images.value.indices) index else -1
    }

    fun setEffectForSelected(effect: SlideshowEffect) {
        val idx = _selectedIndex.value
        val current = _images.value.toMutableList()
        if (idx in current.indices) {
            current[idx] = current[idx].copy(effect = effect)
            _images.value = current
        }
    }

    fun setFilterForSelected(filter: SlideshowFilter) {
        val idx = _selectedIndex.value
        val current = _images.value.toMutableList()
        if (idx in current.indices) {
            current[idx] = current[idx].copy(filter = filter)
            _images.value = current
        }
    }

    fun setDurationPerImage(seconds: Int) {
        _durationPerImageSec.value = seconds.coerceIn(1, 10)
    }

    fun setAudioUri(uri: Uri?) {
        _audioUri.value = uri
    }

    fun removeAudio() {
        _audioUri.value = null
    }

    fun createVideo() {
        val imageList = _images.value
        if (imageList.isEmpty()) return

        viewModelScope.launch {
            _isCreating.value = true
            _progress.value = 0f
            _creationSuccess.value = null

            try {
                converter.createVideoFromImages(
                    images = imageList,
                    durationPerImageMs = _durationPerImageSec.value * 1000L,
                    audioUri = _audioUri.value,
                    onProgress = { _progress.value = it }
                )
                _creationSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _creationSuccess.value = false
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun clearStatus() {
        _creationSuccess.value = null
    }
}
