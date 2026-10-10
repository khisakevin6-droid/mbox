@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.mbox.stream.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.mbox.stream.MboxApplication
import com.mbox.stream.data.db.ProgressEntry
import com.mbox.stream.ui.theme.MboxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hosts the ExoPlayer instance so its lifetime follows the activity:
 * created in onCreate, paused and saved in onStop, released in onDestroy.
 */
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var request: PlayRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = PlayRequest.from(intent)
        if (req == null) {
            finish()
            return
        }
        request = req
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        setContent {
            MboxTheme(themeMode = "dark") {
                PlayerScreen(exo, req, this)
            }
        }
    }

    fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    fun toggleOrientation() {
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    /** Persists the position so the title shows up in Continue Watching and can be resumed. */
    fun savePosition() {
        val p = player ?: return
        val r = request ?: return
        val duration = p.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val position = p.currentPosition
        val finished = position >= duration * 0.97
        val entry = ProgressEntry(
            entryKey = r.entryKey,
            titleId = r.titleId,
            episodeKey = r.episodeKey,
            positionMs = if (finished) 0L else position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
            label = r.title
        )
        val dao = (applicationContext as MboxApplication).db.userDao()
        CoroutineScope(Dispatchers.IO).launch { dao.saveProgress(entry) }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
        savePosition()
    }

    override fun onDestroy() {
        savePosition()
        player?.release()
        player = null
        super.onDestroy()
    }
}
