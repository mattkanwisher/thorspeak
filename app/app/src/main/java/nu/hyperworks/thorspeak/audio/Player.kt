package nu.hyperworks.thorspeak.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * ExoPlayer wrapper. New text interrupts current playback — the voiceover
 * mirrors on-screen dialogue, so a stale line is worse than a cut-off one.
 */
class Player(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private var lastFile: File? = null
    var onPlaybackEnded: (() -> Unit)? = null

    init {
        main.post {
            exo = ExoPlayer.Builder(context).build().also { player ->
                player.addListener(object : Media3Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Media3Player.STATE_ENDED) onPlaybackEnded?.invoke()
                    }
                })
            }
        }
    }

    fun play(file: File) {
        lastFile = file
        main.post {
            exo?.run {
                stop()
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                play()
            }
        }
    }

    fun replay() {
        lastFile?.let { play(it) }
    }

    fun stop() {
        main.post { exo?.stop() }
    }

    fun release() {
        main.post { exo?.release(); exo = null }
    }
}
