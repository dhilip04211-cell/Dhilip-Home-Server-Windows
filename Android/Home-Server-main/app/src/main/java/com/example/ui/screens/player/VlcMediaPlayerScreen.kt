package com.example.ui.screens.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

private val Glass = Color.Black.copy(alpha = 0.46f)
private val Accent = Color(0xFF42C7FF)

@Composable
fun VlcMediaPlayerScreen(
    videoUrl: String,
    videoTitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var moreOpen by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun interact() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }
    fun seek(offset: Long) {
        interact()
        player.seekTo((player.currentPosition + offset).coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    BackHandler(onBack = onNavigateBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) { errorText = error.message ?: "Playback failed"; buffering = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(player, lastInteraction, controlsVisible, isPlaying) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            if (controlsVisible && isPlaying) {
                delay(3500)
                if (System.currentTimeMillis() - lastInteraction >= 3300) controlsVisible = false
            } else delay(400)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, dragAmount ->
                    if (kotlin.math.abs(dragAmount) > 45) seek(if (dragAmount > 0) 10_000 else -10_000)
                }
            )
        }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    controllerAutoShow = false
                    resizeMode = resizeMode
                    setShutterBackgroundColor(Color.Black.hashCode())
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeMode; it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = buffering && errorText == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            LinearProgressIndicator(modifier = Modifier.width(180.dp).clip(CircleShape))
        }

        AnimatedVisibility(visible = errorText != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp).clip(RoundedCornerShape(22.dp)).background(Glass).padding(22.dp)) {
                Text("Unable to play", color = Color.White, fontWeight = FontWeight.Bold)
                Text(errorText ?: "Playback error", color = Color.White.copy(alpha = .72f), modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                    IconButton(onClick = { errorText = null; player.prepare(); player.play() }) { Icon(Icons.Default.PlayArrow, "Retry", tint = Accent) }
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
            }
        }

        AnimatedVisibility(visible = controlsVisible && errorText == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { interact() }) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Text(videoTitle, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { interact(); moreOpen = true }) { Icon(Icons.Default.AspectRatio, "More player options", tint = Color.White) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value -> DropdownMenuItem(text = { Text("Speed ${value}x") }, onClick = { speed = value; player.setPlaybackSpeed(value); moreOpen = false }) }
                            DropdownMenuItem(text = { Text("Fit / Fill") }, onClick = { resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT; moreOpen = false })
                        }
                    }
                    IconButton(onClick = { interact(); moreOpen = true }) { Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White) }
                }

                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    IconButton(onClick = { seek(-10_000) }, Modifier.size(58.dp).clip(CircleShape).background(Glass)) { Icon(Icons.Default.Replay10, "Rewind 10 seconds", tint = Color.White) }
                    IconButton(onClick = { interact(); if (player.isPlaying) player.pause() else player.play() }, Modifier.size(76.dp).clip(CircleShape).background(Accent)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)) }
                    IconButton(onClick = { seek(10_000) }, Modifier.size(58.dp).clip(CircleShape).background(Glass)) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White) }
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.86f)))).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${speed}x", color = Accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { interact(); muted = !muted; player.volume = if (muted) 0f else 1f }) { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeDown, "Mute", tint = Color.White) }
                    }
                    Slider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, onValueChange = { interact(); player.seekTo((it * duration).toLong()) }, colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(.28f)))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
