@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.mbox.stream.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mbox.stream.MboxApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun Float.speedLabel(): String = if (this % 1f == 0f) "${toInt()}x" else "${this}x"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Fit: letterbox. Fill: fill width. Zoom: crop to fill. Stretch: distort to fill. */
private enum class AspectOption(val label: String, val mode: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

private enum class Menu { Speed, Aspect, Tracks }

private fun subtitleMime(name: String): String {
    val n = name.lowercase()
    return when {
        n.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        n.endsWith(".ass") || n.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        n.endsWith(".ttml") || n.endsWith(".xml") || n.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

private fun buildMediaItem(uri: String, mime: String?, subtitle: String?, subtitleName: String): MediaItem {
    val builder = MediaItem.Builder().setUri(Uri.parse(uri))
    if (!mime.isNullOrBlank()) builder.setMimeType(mime)
    if (!subtitle.isNullOrBlank()) {
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle))
                    .setMimeType(subtitleMime(subtitleName.ifBlank { subtitle }))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
    }
    return builder.build()
}

private fun applySubtitlePrefs(player: ExoPlayer, lang: String) {
    val b = player.trackSelectionParameters.buildUpon()
    if (lang == "off") {
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(lang)
            .setSelectUndeterminedTextLanguage(true)
    }
    player.trackSelectionParameters = b.build()
}

private fun describeError(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Network error. Check your connection and try again."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "The server refused the request. The link may have expired or you may not have access."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "The video file could not be found."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        "This video format is not supported on this device."
    else -> "Playback failed (${e.errorCodeName})."
}

private fun displayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment ?: ""
} catch (_: Exception) {
    ""
}

@Composable
fun PlayerScreen(exo: ExoPlayer, req: PlayRequest, activity: PlayerActivity) {
    val context = LocalContext.current
    val dao = remember { (context.applicationContext as MboxApplication).db.userDao() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(req.speed) }
    var aspect by remember { mutableStateOf(AspectOption.Fit) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var hud by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var subtitleUri by remember { mutableStateOf(req.subtitle) }
    var subtitleName by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    fun load(startMs: Long) {
        error = null
        exo.setMediaItem(buildMediaItem(req.uri, req.mime, subtitleUri, subtitleName), startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun seekBy(deltaMs: Long) {
        val max = if (duration > 0) duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, max))
        hud = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        interaction++
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Persistable access is optional for a one-off subtitle file.
            }
            subtitleUri = uri.toString()
            subtitleName = displayName(context, uri)
            load(exo.currentPosition)
        }
    }

    // Initial load with resume position.
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { dao.progress(req.entryKey) }
        applySubtitlePrefs(exo, req.subLang)
        exo.setPlaybackSpeed(req.speed)
        load(saved?.positionMs ?: 0L)
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = exo.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) controlsVisible = true
            }
            override fun onPlayerError(e: PlaybackException) { error = describeError(e) }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Position polling and periodic progress saving.
    LaunchedEffect(exo) {
        var tick = 0
        while (true) {
            position = exo.currentPosition
            val d = exo.duration
            if (d != C.TIME_UNSET && d > 0) duration = d
            delay(500)
            tick++
            if (tick % 10 == 0 && exo.isPlaying) activity.savePosition()
        }
    }

    // Auto-hide controls.
    LaunchedEffect(controlsVisible, interaction, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                }
            },
            update = { it.resizeMode = aspect.mode },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture layer: tap, double-tap seek, drag to seek / brightness / volume.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; interaction++ },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) seekBy(-10_000) else seekBy(10_000)
                        }
                    )
                }
                .pointerInput(Unit) {
                    var mode = 0 // 1 = seek, 2 = brightness, 3 = volume
                    var startX = 0f
                    var totalDx = 0f
                    var startPos = 0L
                    var target = 0L
                    var level = 0f
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0
                            startX = o.x
                            totalDx = 0f
                            startPos = exo.currentPosition
                            target = startPos
                        },
                        onDragEnd = {
                            if (mode == 1) exo.seekTo(target)
                            mode = 0
                        },
                        onDragCancel = { mode = 0 },
                        onDrag = { change, drag ->
                            change.consume()
                            if (mode == 0) {
                                if (abs(drag.x) > abs(drag.y)) {
                                    mode = 1
                                } else if (startX < size.width / 2) {
                                    mode = 2
                                    val b = activity.window.attributes.screenBrightness
                                    level = if (b < 0f) 0.5f else b
                                } else {
                                    mode = 3
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                                }
                            }
                            when (mode) {
                                1 -> {
                                    totalDx += drag.x
                                    val max = if (duration > 0) duration else Long.MAX_VALUE
                                    target = (startPos + (totalDx * 120).toLong()).coerceIn(0L, max)
                                    hud = "${formatTime(target)} / ${formatTime(duration)}"
                                }
                                2 -> {
                                    level = (level - drag.y / size.height).coerceIn(0.01f, 1f)
                                    val attrs = activity.window.attributes
                                    attrs.screenBrightness = level
                                    activity.window.attributes = attrs
                                    hud = "Brightness ${(level * 100).roundToInt()}%"
                                }
                                3 -> {
                                    level = (level - drag.y / size.height).coerceIn(0f, 1f)
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
                                    hud = "Volume ${(level * 100).roundToInt()}%"
                                }
                            }
                        }
                    )
                }
        )

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
        }

        hud?.let {
            Text(
                it,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        req.title,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { menu = Menu.Speed; interaction++ }) {
                        Text(speed.speedLabel(), color = Color.White)
                    }
                    IconButton(onClick = { menu = Menu.Aspect; interaction++ }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect ratio", tint = Color.White)
                    }
                    IconButton(onClick = { menu = Menu.Tracks; interaction++ }) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = "Audio and subtitles", tint = Color.White)
                    }
                    IconButton(onClick = { activity.toggleOrientation(); interaction++ }) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                    }
                }

                // Bottom bar
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(if (dragging) dragValue.toLong() else position), color = Color.White)
                        Slider(
                            value = (if (dragging) dragValue else position.toFloat()).coerceIn(0f, maxOf(duration, 1L).toFloat()),
                            onValueChange = { dragging = true; dragValue = it; interaction++ },
                            onValueChangeFinished = { exo.seekTo(dragValue.toLong()); dragging = false },
                            valueRange = 0f..maxOf(duration, 1L).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(formatTime(duration), color = Color.White)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { seekBy(-10_000) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (exo.isPlaying) exo.pause() else {
                                    if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
                                    exo.play()
                                }
                                interaction++
                            }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                        IconButton(onClick = { seekBy(10_000) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                        }
                    }
                }
            }
        }

        error?.let { message ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(message, color = Color.White, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { error = null; exo.prepare(); exo.play() }) { Text("Retry") }
                    OutlinedButton(onClick = { activity.finish() }) { Text("Close", color = Color.White) }
                }
            }
        }
    }

    when (menu) {
        Menu.Speed -> OptionDialog(
            title = "Playback speed",
            options = SPEEDS.map { it.speedLabel() to (it == speed) },
            onPick = { i -> speed = SPEEDS[i]; exo.setPlaybackSpeed(speed); menu = null },
            onDismiss = { menu = null }
        )
        Menu.Aspect -> OptionDialog(
            title = "Aspect ratio",
            options = AspectOption.values().map { it.label to (it == aspect) },
            onPick = { i -> aspect = AspectOption.values()[i]; menu = null },
            onDismiss = { menu = null }
        )
        Menu.Tracks -> TracksDialog(
            exo = exo,
            tracks = tracks,
            onPickSubtitleFile = { menu = null; subtitlePicker.launch(arrayOf("*/*")) },
            onDismiss = { menu = null }
        )
        null -> Unit
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, (label, selected) ->
                    TextButton(onClick = { onPick(i) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected) "✓  $label" else label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun trackLabel(f: Format, index: Int): String =
    listOfNotNull(f.label, f.language?.takeIf { it != "und" }).joinToString(" • ").ifBlank { "Track ${index + 1}" }

@Composable
private fun TracksDialog(
    exo: ExoPlayer,
    tracks: Tracks,
    onPickSubtitleFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val subtitlesOff = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio & subtitles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Audio", fontWeight = FontWeight.Bold)
                if (audio.isEmpty()) Text("No selectable audio tracks reported.")
                audio.forEach { g ->
                    for (i in 0 until g.length) {
                        if (!g.isTrackSupported(i)) continue
                        val selected = g.isTrackSelected(i)
                        TextButton(
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                    .build()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text((if (selected) "✓  " else "") + trackLabel(g.getTrackFormat(i), i)) }
                    }
                }
                Text("Subtitles", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                TextButton(
                    onClick = {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text((if (subtitlesOff) "✓  " else "") + "Off") }
                text.forEach { g ->
                    for (i in 0 until g.length) {
                        if (!g.isTrackSupported(i)) continue
                        val selected = g.isTrackSelected(i) && !subtitlesOff
                        TextButton(
                            onClick = {
                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                                    .build()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text((if (selected) "✓  " else "") + trackLabel(g.getTrackFormat(i), i)) }
                    }
                }
                OutlinedButton(onClick = onPickSubtitleFile, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Load subtitle file (.srt, .vtt, .ass, .ttml)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
