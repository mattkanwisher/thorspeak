package nu.hyperworks.thorspeak.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nu.hyperworks.thorspeak.net.ProcessResponse

sealed interface SessionStatus {
    data object Idle : SessionStatus
    data object Capturing : SessionStatus
    data object Processing : SessionStatus
    data object Speaking : SessionStatus
    data class Error(val message: String) : SessionStatus
}

/** Shared state between CaptureService and the UI (both live in one process). */
object SessionState {
    private val _status = MutableStateFlow<SessionStatus>(SessionStatus.Idle)
    val status: StateFlow<SessionStatus> = _status

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _lastResponse = MutableStateFlow<ProcessResponse?>(null)
    val lastResponse: StateFlow<ProcessResponse?> = _lastResponse

    /** Latest captured full frame, for the region-select screen. */
    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame

    private val _gateLog = MutableStateFlow("")
    val gateLog: StateFlow<String> = _gateLog

    fun setStatus(s: SessionStatus) { _status.value = s }
    fun setRunning(r: Boolean) {
        _running.value = r
        if (!r) _status.value = SessionStatus.Idle
    }
    fun setResponse(r: ProcessResponse) { _lastResponse.value = r }
    fun setFrame(b: Bitmap) { _lastFrame.value = b }
    fun setGateLog(msg: String) { _gateLog.value = msg }
}
