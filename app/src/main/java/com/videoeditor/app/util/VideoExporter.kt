package com.videoeditor.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.videoeditor.app.data.VideoFilter
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class VideoExporter(private val context: Context) {

    suspend fun exportPreview(
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        brightness: Float,
        contrast: Float,
        filter: VideoFilter,
    ): Uri = withContext(Dispatchers.Main) {
        val outputTmpFile = File(context.cacheDir, "edited_tmp_${System.currentTimeMillis()}.mp4")
        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(trimEndMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val videoEffects: List<Effect>
        = buildVideoEffects(brightness, contrast, filter)

        val effects = Effects(
            /* audioProcessors= */ emptyList(),
            /* videoEffects= */ videoEffects
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    continuation.resume(Uri.fromFile(outputTmpFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    continuation.resumeWithException(exportException)
                }
            }

            val exportTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            exportTransformer.start(editedMediaItem, outputTmpFile.absolutePath)
            continuation.invokeOnCancellation {
                exportTransformer.cancel()
            }
        }
    }

    suspend fun exportVideo(
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        brightness: Float,
        contrast: Float,
        filter: VideoFilter,
    ): Uri = withContext(Dispatchers.Main) {
        val outputFile: File = createOutputFile()

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(trimEndMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val videoEffects = buildVideoEffects(brightness, contrast, filter)

        val effects = Effects(
            /* audioProcessors= */ emptyList(),
            /* videoEffects= */ videoEffects
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val savedUri = saveToMediaStore(outputFile)
                    continuation.resume(savedUri ?: Uri.fromFile(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    continuation.resumeWithException(exportException)
                }
            }

            val exportTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            exportTransformer.start(editedMediaItem, outputFile.absolutePath)

            continuation.invokeOnCancellation {
                exportTransformer.cancel()
            }
        }
    }

    private fun buildVideoEffects(
        brightness: Float,
        contrast: Float,
        filter: VideoFilter
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        if (contrast != 1f) {
            effects.add(Contrast(contrast - 1f))
        }

        when (filter) {
            VideoFilter.GRAYSCALE -> effects.add(RgbFilter.createGrayscaleFilter())
            else -> { /* other filters handled via color matrix */ }
        }

        return effects
    }

    private fun createOutputFile(): File {
        val outputDir = File(context.cacheDir, "exports")
        outputDir.mkdirs()
        return File(outputDir, "edited_${System.currentTimeMillis()}.mp4")
    }

    private fun saveToMediaStore(file: File): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let { mediaUri ->
            resolver.openOutputStream(mediaUri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(mediaUri, contentValues, null, null)
            }
        }

        file.delete()
        return uri
    }
}
