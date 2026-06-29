package com.videoeditor.app.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    uri: Uri,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    endPositionMs: Long = Long.MAX_VALUE,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startPositionMs)
                .setEndPositionMs(if (endPositionMs == Long.MAX_VALUE) Long.MIN_VALUE else endPositionMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(clipping)
                .build()
            setMediaItem(mediaItem)
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(uri, startPositionMs, endPositionMs) {
        onPlayerReady(player)
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    )
}
