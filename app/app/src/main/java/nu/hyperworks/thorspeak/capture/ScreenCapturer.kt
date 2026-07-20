package nu.hyperworks.thorspeak.capture

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.graphics.PixelFormat
import android.view.Display

/**
 * Captures the DEFAULT display (the Thor's top screen — MediaProjection always
 * mirrors the default display) at half resolution. The app itself runs on the
 * bottom/secondary screen, so never derive capture geometry from the
 * Activity's own display.
 */
class ScreenCapturer(context: Context, private val projection: MediaProjection) {

    private val width: Int
    private val height: Int
    private val reader: ImageReader
    private var virtualDisplay: VirtualDisplay?

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseInternal()
        }
    }

    init {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        // Half res: the Game Boy source is 160x144 upscaled — nothing is lost,
        // and pixel-diff + OCR cost drops 4x.
        width = mode.physicalWidth / 2
        height = mode.physicalHeight / 2
        val dpi = context.resources.configuration.densityDpi

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        projection.registerCallback(projectionCallback, null)
        virtualDisplay = projection.createVirtualDisplay(
            "thorspeak-capture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, null,
        )
    }

    /** Grab the most recent frame, or null if none is available yet. */
    fun capture(): Bitmap? {
        val image = reader.acquireLatestImage() ?: return null
        image.use {
            val plane = it.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            // rowStride may exceed width*pixelStride (row padding) — create the
            // bitmap at padded width, then crop.
            val paddedWidth = rowStride / pixelStride
            val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(plane.buffer)
            return if (paddedWidth == width) bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, width, height).also { cropped ->
                if (cropped !== bitmap) bitmap.recycle()
            }
        }
    }

    fun release() {
        projection.unregisterCallback(projectionCallback)
        releaseInternal()
        projection.stop()
    }

    private fun releaseInternal() {
        virtualDisplay?.release()
        virtualDisplay = null
        reader.close()
    }

    private inline fun <R> android.media.Image.use(block: (android.media.Image) -> R): R =
        try { block(this) } finally { close() }
}
