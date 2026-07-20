package nu.hyperworks.thorspeak.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nu.hyperworks.thorspeak.MainActivity
import nu.hyperworks.thorspeak.R
import nu.hyperworks.thorspeak.ThorSpeakApp
import nu.hyperworks.thorspeak.data.CaptureRegion
import nu.hyperworks.thorspeak.data.SessionState
import nu.hyperworks.thorspeak.data.SessionStatus
import nu.hyperworks.thorspeak.net.ApiClient
import java.io.ByteArrayOutputStream

private const val TAG = "ThorSpeak"

class CaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var capturer: ScreenCapturer? = null
    private val gate = FrameGate()
    private val mlKit = MlKitReader()
    private var lastSentGateText: String? = null

    private val app get() = application as ThorSpeakApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val projection: MediaProjection = mpm.getMediaProjection(resultCode, resultData)
        capturer = ScreenCapturer(this, projection)
        SessionState.setRunning(true)
        SessionState.setStatus(SessionStatus.Capturing)
        scope.launch { captureLoop() }
        return START_NOT_STICKY
    }

    private suspend fun captureLoop() {
        while (scope.isActive) {
            val settings = app.settingsRepository.current()
            try {
                val frame = capturer?.capture()
                if (frame != null) {
                    SessionState.setFrame(frame)
                    val crop = cropToRegion(frame, settings.region)
                    processFrame(crop, settings.lang, settings.pixelOnlyGate, settings.serverUrl, settings.voiceFor(settings.lang))
                }
            } catch (e: Exception) {
                Log.w(TAG, "capture loop error", e)
                SessionState.setStatus(SessionStatus.Error(e.message ?: "capture error"))
            }
            delay(settings.intervalMs)
        }
    }

    private suspend fun processFrame(
        crop: Bitmap,
        lang: String,
        pixelOnly: Boolean,
        serverUrl: String,
        voice: String?,
    ) {
        if (!gate.offer(crop)) {
            SessionState.setGateLog("dropped: unchanged/unstable")
            return
        }

        var gateText: String? = null
        if (!pixelOnly) {
            gateText = MlKitReader.normalizeLocal(mlKit.read(crop))
            if (gateText.isBlank()) {
                SessionState.setGateLog("dropped: ML Kit saw no text")
                return
            }
            if (gateText == lastSentGateText) {
                SessionState.setGateLog("dropped: same text as last sent")
                return
            }
            lastSentGateText = gateText
        }
        SessionState.setGateLog("sent to server")
        Log.i(TAG, "gate passed; sending frame to server")

        SessionState.setStatus(SessionStatus.Processing)
        try {
            val jpeg = ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val api = app.apiClient.api(serverUrl)
            val resp = api.process(
                ApiClient.jpegPart(jpeg),
                ApiClient.textPart(lang),
                voice?.let { ApiClient.textPart(it) },
                gateText?.let { ApiClient.textPart(it) },
            )
            val dropped = resp.droppedReason
            if (dropped != null) {
                // Server refused to speak (hallucinated OCR / nothing speakable):
                // keep the last real line on screen instead of showing garbage.
                SessionState.setGateLog("server dropped: $dropped")
                SessionState.setStatus(SessionStatus.Capturing)
                return
            }
            SessionState.setResponse(resp)
            val hash = resp.audioHash
            val url = resp.audioUrl
            if (hash != null && url != null) {
                val file = app.audioCache.getOrFetch(hash, serverUrl.trimEnd('/') + url)
                SessionState.setStatus(SessionStatus.Speaking)
                app.player.play(file)
            } else {
                SessionState.setStatus(SessionStatus.Capturing)
            }
        } catch (e: Exception) {
            Log.w(TAG, "server processing failed", e)
            SessionState.setStatus(SessionStatus.Error("server: ${e.message}"))
        }
    }

    private fun cropToRegion(frame: Bitmap, region: CaptureRegion): Bitmap {
        if (region.isFullFrame()) return frame
        val r = Rect(
            (region.left * frame.width).toInt().coerceIn(0, frame.width - 1),
            (region.top * frame.height).toInt().coerceIn(0, frame.height - 1),
            (region.right * frame.width).toInt().coerceIn(1, frame.width),
            (region.bottom * frame.height).toInt().coerceIn(1, frame.height),
        )
        if (r.width() < 8 || r.height() < 8) return frame
        return Bitmap.createBitmap(frame, r.left, r.top, r.width(), r.height())
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Capture session", NotificationManager.IMPORTANCE_LOW),
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("ThorSpeak")
            .setContentText("Watching the top screen")
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        capturer?.release()
        capturer = null
        mlKit.close()
        app.player.stop()
        SessionState.setRunning(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "nu.hyperworks.thorspeak.STOP"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
