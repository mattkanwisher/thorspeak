package nu.hyperworks.thorspeak.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Floating translation window on the DEFAULT display (the Thor's top screen,
 * same display MediaProjection captures). Must be hidden around each frame
 * grab or the capture pipeline would OCR our own overlay.
 */
class OverlayController(context: Context) {

    private val windowContext: Context
    private val wm: WindowManager
    private val view: TextView
    private val params: WindowManager.LayoutParams
    private var added = false
    private var restoreAfterCapture = false

    @Volatile
    var isShowing = false
        private set

    init {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        windowContext = context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        wm = windowContext.getSystemService(WindowManager::class.java)

        view = TextView(windowContext).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(20, 12, 20, 12)
            background = GradientDrawable().apply {
                setColor(0xE0141420.toInt())
                cornerRadius = 14f
            }
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /** Place the translation at (x, y) in physical display pixels. */
    suspend fun show(text: String, x: Int, y: Int, width: Int) = withContext(Dispatchers.Main) {
        view.text = text
        params.x = x
        params.y = y
        params.width = width
        if (!added) {
            wm.addView(view, params)
            added = true
        } else {
            wm.updateViewLayout(view, params)
        }
        view.visibility = View.VISIBLE
        isShowing = true
    }

    /** Dialogue closed — remove the bubble until the next translation. */
    suspend fun clear() = withContext(Dispatchers.Main) {
        if (added) view.visibility = View.GONE
        isShowing = false
    }

    /** Blank the window just for the duration of a frame grab. */
    suspend fun hideForCapture() = withContext(Dispatchers.Main) {
        restoreAfterCapture = added && view.visibility == View.VISIBLE
        if (restoreAfterCapture) view.visibility = View.INVISIBLE
    }

    suspend fun restore() = withContext(Dispatchers.Main) {
        if (restoreAfterCapture) view.visibility = View.VISIBLE
        restoreAfterCapture = false
    }

    fun destroy() {
        if (added) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
            added = false
        }
        isShowing = false
    }
}
