package com.videoeditor.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.videoeditor.app.data.SlideshowEffect
import com.videoeditor.app.data.SlideshowFilter
import com.videoeditor.app.data.SlideshowImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import androidx.core.graphics.withSave
import androidx.core.graphics.scale

class ImageToVideoConverter(private val context: Context) {

    companion object {
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val BIT_RATE = 8_000_000
    }

    suspend fun createVideoFromImages(
        images: List<SlideshowImage>,
        durationPerImageMs: Long,
        audioUri: Uri? = null,
        onProgress: (Float) -> Unit
    ): Uri = withContext(Dispatchers.Default) {
        val outputFile = createOutputFile()

        // Calculate total frames accounting for per-image slow motion
        val baseFramesPerImage = (durationPerImageMs * FRAME_RATE / 1000).toInt()
        val totalFrames = images.sumOf { img ->
            val factor = if (img.effect == SlideshowEffect.SLOW_MOTION) 2 else 1
            baseFramesPerImage * factor
        }

        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
                .apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var frameIndex = 0

        // Ken Burns: alternate between pan directions per image
        val kenBurnsDirections = listOf(
            KenBurnsDirection.LEFT_TO_RIGHT,
            KenBurnsDirection.RIGHT_TO_LEFT,
            KenBurnsDirection.TOP_TO_BOTTOM,
            KenBurnsDirection.BOTTOM_TO_TOP
        )

        try {
            for ((imageIdx: Int, slideshowImage: SlideshowImage) in images.withIndex()) {
                val bitmap: Bitmap = decodeBitmapFitToVideo(slideshowImage.uri)
                val effect: SlideshowEffect = slideshowImage.effect
                val filter: SlideshowFilter = slideshowImage.filter
                val filterPaint: Paint? = createFilterPaint(filter)
                val slowMotionFactor: Int = if (effect == SlideshowEffect.SLOW_MOTION) 2 else 1
                val framesPerImage: Int = baseFramesPerImage * slowMotionFactor

                for (frame in 0 until framesPerImage) {
                    val progress = frame.toFloat() / framesPerImage.coerceAtLeast(1)
                    val canvas = inputSurface.lockCanvas(null)
                    canvas.drawColor(Color.BLACK)

                    canvas.withSave {
                        when (effect) {
                            SlideshowEffect.ZOOM_IN -> {
                                val scale = 1f + progress * 0.3f
                                translate(VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
                                scale(scale, scale)
                                translate(-VIDEO_WIDTH / 2f, -VIDEO_HEIGHT / 2f)
                            }

                            SlideshowEffect.ZOOM_OUT -> {
                                val scale = 1.3f - progress * 0.3f
                                translate(VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
                                scale(scale, scale)
                                translate(-VIDEO_WIDTH / 2f, -VIDEO_HEIGHT / 2f)
                            }

                            SlideshowEffect.KEN_BURNS -> {
                                val direction: KenBurnsDirection =
                                    kenBurnsDirections[imageIdx % kenBurnsDirections.size]
                                applyKenBurns(this, progress, direction)
                            }

                            else -> {}
                        }

                        drawBitmapCentered(this, bitmap, filterPaint)
                    }

                    // Apply fade alpha overlay
                    when (effect) {
                        SlideshowEffect.FADE_IN -> {
                            val alpha = ((1f - progress) * 255).roundToInt().coerceIn(0, 255)
                            if (alpha > 0) {
                                canvas.drawColor(Color.argb(alpha, 0, 0, 0))
                            }
                        }

                        SlideshowEffect.FADE_OUT -> {
                            val alpha = (progress * 255).roundToInt().coerceIn(0, 255)
                            if (alpha > 0) {
                                canvas.drawColor(Color.argb(alpha, 0, 0, 0))
                            }
                        }

                        SlideshowEffect.FADE_IN_OUT -> {
                            val alpha = if (progress < 0.15f) {
                                ((1f - progress / 0.15f) * 255).roundToInt()
                            } else if (progress > 0.85f) {
                                (((progress - 0.85f) / 0.15f) * 255).roundToInt()
                            } else 0
                            if (alpha > 0) {
                                canvas.drawColor(Color.argb(alpha.coerceIn(0, 255), 0, 0, 0))
                            }
                        }

                        else -> {}
                    }

                    inputSurface.unlockCanvasAndPost(canvas)

                    drainEncoder(
                        codec,
                        bufferInfo,
                        muxer,
                        trackIndex,
                        muxerStarted
                    ).let { (ti, ms) ->
                        trackIndex = ti
                        muxerStarted = ms
                    }

                    frameIndex++
                    onProgress(frameIndex.toFloat() / totalFrames)
                }

                bitmap.recycle()
            }

            // Signal end of stream
            codec.signalEndOfInputStream()
            drainEncoderEnd(codec, bufferInfo, muxer, trackIndex, muxerStarted)

        } finally {
            codec.stop()
            codec.release()
            inputSurface.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }

        val finalFile = if (audioUri != null) {
            val actualDurationMs = images.sumOf { img ->
                val factor = if (img.effect == SlideshowEffect.SLOW_MOTION) 2L else 1L
                durationPerImageMs * factor
            }
            val muxedFile: File = muxAudioIntoVideo(outputFile, audioUri, actualDurationMs)
            outputFile.delete()
            muxedFile
        } else {
            outputFile
        }

        val savedUri: Uri? = saveToMediaStore(finalFile)
        savedUri ?: Uri.fromFile(finalFile)
    }

    private fun muxAudioIntoVideo(videoFile: File, audioUri: Uri, videoDurationMs: Long): File {
        val outputFile = File(videoFile.parent, "muxed_${System.currentTimeMillis()}.mp4")
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add video track
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)
        val videoTrackIdx: Int = findTrack(videoExtractor, "video/")
        videoExtractor.selectTrack(videoTrackIdx)
        val videoFormat: MediaFormat = videoExtractor.getTrackFormat(videoTrackIdx)
        val muxerVideoTrack: Int = muxer.addTrack(videoFormat)

        // Add audio track — transcode to AAC if source is not AAC (e.g. MP3)
        val audioExtractor = MediaExtractor()
        val assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(audioUri, "r")
            ?: throw IllegalArgumentException("Cannot open audio URI: $audioUri")
        audioExtractor.setDataSource(
            assetFileDescriptor.fileDescriptor,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.length
        )
        assetFileDescriptor.close()
        val audioTrackIdx: Int = findTrack(audioExtractor, "audio/")
        audioExtractor.selectTrack(audioTrackIdx)
        val audioFormat: MediaFormat = audioExtractor.getTrackFormat(audioTrackIdx)
        val audioMime: String = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""

        val videoDurationUs: Long = videoDurationMs * 1000L

        if (audioMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
            processAAC(
                muxer,
                audioFormat,
                videoExtractor,
                muxerVideoTrack,
                audioExtractor,
                videoDurationUs
            )
        } else {
            processNonAAC(
                audioExtractor,
                audioFormat,
                videoDurationUs,
                muxer,
                videoExtractor,
                muxerVideoTrack
            )
        }

        return outputFile
    }

    private fun processNonAAC(
        audioExtractor: MediaExtractor,
        audioFormat: MediaFormat,
        videoDurationUs: Long,
        muxer: MediaMuxer,
        videoExtractor: MediaExtractor,
        muxerVideoTrack: Int
    ) {
        // Non-AAC audio (MP3, etc.) — transcode to AAC then mux
        val transcodedAacFile: File = transcodeAudioToAac(audioExtractor, audioFormat, videoDurationUs)
        audioExtractor.release()

        // Re-extract the transcoded AAC file
        val aacExtractor = MediaExtractor()
        aacExtractor.setDataSource(transcodedAacFile.absolutePath)
        val aacTrackIdx: Int = findTrack(aacExtractor, "audio/")
        aacExtractor.selectTrack(aacTrackIdx)
        val aacFormat: MediaFormat = aacExtractor.getTrackFormat(aacTrackIdx)
        val muxerAudioTrack: Int = muxer.addTrack(aacFormat)

        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        writeVideoSamples(videoExtractor, muxer, muxerVideoTrack, buffer, bufferInfo)

        // Write transcoded audio samples
        while (true) {
            val sampleSize = aacExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = aacExtractor.sampleTime
            bufferInfo.flags = getBufferFlag(aacExtractor.sampleFlags)
            muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
            aacExtractor.advance()
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        aacExtractor.release()
        transcodedAacFile.delete()
    }

    private fun processAAC(
        muxer: MediaMuxer,
        audioFormat: MediaFormat,
        videoExtractor: MediaExtractor,
        muxerVideoTrack: Int,
        audioExtractor: MediaExtractor,
        videoDurationUs: Long
    ) {
        val muxerAudioTrack: Int = muxer.addTrack(audioFormat)
        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        writeVideoSamples(videoExtractor, muxer, muxerVideoTrack, buffer, bufferInfo)

        // Write audio samples (trimmed to video duration)
        while (true) {
            val sampleSize = audioExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val sampleTime = audioExtractor.sampleTime
            if (sampleTime > videoDurationUs) break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = sampleTime
            bufferInfo.flags = getBufferFlag(audioExtractor.sampleFlags)
            muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
            audioExtractor.advance()
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }

    private fun getBufferFlag(flag: Int): Int {
        return when (flag) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME -> MediaCodec.BUFFER_FLAG_KEY_FRAME
            MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
            MediaCodec.BUFFER_FLAG_END_OF_STREAM -> MediaCodec.BUFFER_FLAG_END_OF_STREAM
            MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            else -> {
                MediaCodec.BUFFER_FLAG_DECODE_ONLY
            }
        }
    }

    private fun writeVideoSamples(
        videoExtractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerVideoTrack: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        while (true) {
            val sampleSize: Int = videoExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = videoExtractor.sampleTime
            bufferInfo.flags =  getBufferFlag(videoExtractor.sampleFlags)
            muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
            videoExtractor.advance()
        }
    }

    private fun transcodeAudioToAac(
        extractor: MediaExtractor,
        sourceFormat: MediaFormat,
        maxDurationUs: Long
    ): File {
        val sampleRate: Int = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount: Int = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // Set up AAC encoder
        val encoderFormat: MediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 320_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 50)
        }

        val encoder: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        // Set up decoder for source audio
        val mime: String = sourceFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder: MediaCodec = MediaCodec.createDecoderByType(mime)
        decoder.configure(sourceFormat, null, null, 0)
        decoder.start()

        val outputFile = File(context.cacheDir, "transcoded_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var isEncoderDone = false
        val timeoutUs = 10_000L

        while (!isEncoderDone) {
            // Feed data to decoder
            if (!inputDone) {
                val inputBufIdx: Int = decoder.dequeueInputBuffer(timeoutUs)
                if (inputBufIdx >= 0) {
                    val inputBuf = decoder.getInputBuffer(inputBufIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0 || extractor.sampleTime > maxDurationUs) {
                        decoder.queueInputBuffer(
                            inputBufIdx,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufIdx,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            // Drain decoder output -> feed to encoder
            if (!decoderDone) {
                val decoderOutIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (decoderOutIdx >= 0) {
                    val isEndOfStream: Boolean = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val decodedBuf: ByteBuffer? = decoder.getOutputBuffer(decoderOutIdx)
                    if (decodedBuf != null && bufferInfo.size > 0) {
                        // Send PCM to encoder
                        val encInputIdx: Int = encoder.dequeueInputBuffer(timeoutUs)
                        if (encInputIdx >= 0) {
                            val encInputBuf = encoder.getInputBuffer(encInputIdx)!!
                            encInputBuf.clear()
                            val bytesToCopy: Int = minOf(decodedBuf.remaining(), encInputBuf.remaining())
                            val tempBuf = ByteArray(bytesToCopy)
                            decodedBuf.get(tempBuf)
                            encInputBuf.put(tempBuf)
                            encoder.queueInputBuffer(
                                encInputIdx, 0, bytesToCopy, bufferInfo.presentationTimeUs,
                                if (isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                        }
                    } else if (isEndOfStream) {
                        val encInputIdx: Int = encoder.dequeueInputBuffer(timeoutUs)
                        if (encInputIdx >= 0) {
                            encoder.queueInputBuffer(
                                encInputIdx,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }
                    decoder.releaseOutputBuffer(decoderOutIdx, false)
                    if (isEndOfStream) { decoderDone = true }
                }
            }

            // Drain encoder output -> write to muxer
            val encOutIdx: Int = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                encOutIdx >= 0 -> {
                    val encOutputBuf: ByteBuffer? = encoder.getOutputBuffer(encOutIdx)
                    encOutputBuf?.let {
                        if (bufferInfo.size > 0 && muxerStarted) {
                            it.position(bufferInfo.offset)
                            it.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, it, bufferInfo)                        }
                    }
                    val isEndOfSync: Boolean = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (isEndOfSync) isEncoderDone = true
                }
            }
        }

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
        return outputFile
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        throw IllegalStateException("No track with mime prefix $mimePrefix found")
    }

    private fun decodeBitmapFitToVideo(uri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image URI: $uri")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val sampleSize =
            calculateInSampleSize(options.outWidth, options.outHeight, VIDEO_WIDTH, VIDEO_HEIGHT)

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val stream = context.contentResolver.openInputStream(uri)!!
        val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)!!
        stream.close()

        return decoded.scale(
            VIDEO_WIDTH.coerceAtMost(decoded.width * VIDEO_HEIGHT / decoded.height)
                .coerceAtMost(VIDEO_WIDTH),
            VIDEO_HEIGHT.coerceAtMost(decoded.height * VIDEO_WIDTH / decoded.width)
                .coerceAtMost(VIDEO_HEIGHT)
        ).also { if (it !== decoded) decoded.recycle() }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun drawBitmapCentered(canvas: Canvas, bitmap: Bitmap, paint: Paint? = null) {
        val scaleX: Float = canvas.width.toFloat() / bitmap.width
        val scaleY: Float = canvas.height.toFloat() / bitmap.height
        val scale = minOf(scaleX, scaleY)

        val dstWidth = (bitmap.width * scale).toInt()
        val dstHeight = (bitmap.height * scale).toInt()
        val left = (canvas.width - dstWidth) / 2
        val top = (canvas.height - dstHeight) / 2

        val dstRect = Rect(left, top, left + dstWidth, top + dstHeight)
        canvas.drawBitmap(bitmap, null, dstRect, paint)
    }

    private enum class KenBurnsDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP
    }

    private fun applyKenBurns(canvas: Canvas, progress: Float, direction: KenBurnsDirection) {
        // Ken Burns: slow zoom from 1.0 to 1.2 with a gentle pan
        val scale = 1.0f + progress * 0.2f
        val panAmount = progress * 0.1f

        canvas.translate(VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
        canvas.scale(scale, scale)
        when (direction) {
            KenBurnsDirection.LEFT_TO_RIGHT -> canvas.translate(panAmount * VIDEO_WIDTH, 0f)
            KenBurnsDirection.RIGHT_TO_LEFT -> canvas.translate(-panAmount * VIDEO_WIDTH, 0f)
            KenBurnsDirection.TOP_TO_BOTTOM -> canvas.translate(0f, panAmount * VIDEO_HEIGHT)
            KenBurnsDirection.BOTTOM_TO_TOP -> canvas.translate(0f, -panAmount * VIDEO_HEIGHT)
        }
        canvas.translate(-VIDEO_WIDTH / 2f, -VIDEO_HEIGHT / 2f)
    }

    private fun createFilterPaint(filter: SlideshowFilter): Paint? {
        if (filter == SlideshowFilter.NONE) return null

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colorMatrix = when (filter) {
            SlideshowFilter.CONTRAST -> ColorMatrix().apply {
                val contrast = 1.5f
                val translate = (-.5f * contrast + .5f) * 255f
                set(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }

            SlideshowFilter.SEPIA -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            SlideshowFilter.WARM -> ColorMatrix(
                floatArrayOf(
                    1.2f, 0.1f, 0f, 0f, 10f,
                    0f, 1.0f, 0f, 0f, 5f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            SlideshowFilter.NONE -> null
        }

        colorMatrix?.let { paint.colorFilter = ColorMatrixColorFilter(it) }
        return paint
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean
    ): Pair<Int, Boolean> {
        var currentTrackIndex = trackIndex
        var started = muxerStarted

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    currentTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && started) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        return Pair(currentTrackIndex, started)
    }

    private fun drainEncoderEnd(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean
    ) {
        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    private fun createOutputFile(): File {
        val outputDir = File(context.cacheDir, "slideshow")
        outputDir.mkdirs()
        return File(outputDir, "slideshow_${System.currentTimeMillis()}.mp4")
    }

    private fun saveToMediaStore(file: File): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/VideoEditor"
                )
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
