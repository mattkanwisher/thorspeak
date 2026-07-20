package nu.hyperworks.thorspeak.capture

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Cheap change gate: 8x8 grayscale average-hash + stability debounce.
 *
 * Game Boy dialogue renders with a typewriter effect; requiring two
 * consecutive matching frames means we only fire once the text box has
 * finished drawing.
 */
class FrameGate(private val hammingThreshold: Int = 4) {

    private var lastHash: Long? = null
    private var pending = false

    /** Returns true when a *newly stabilized* frame should be processed. */
    fun offer(bitmap: Bitmap): Boolean {
        val hash = averageHash(bitmap)
        val prev = lastHash
        lastHash = hash
        if (prev == null) {
            pending = true
            return false
        }
        if (hamming(hash, prev) > hammingThreshold) {
            // Content is changing (typewriter effect / scene transition) —
            // wait until it settles.
            pending = true
            return false
        }
        if (pending) {
            pending = false
            return true
        }
        return false
    }

    fun reset() {
        lastHash = null
        pending = false
    }

    companion object {
        fun averageHash(bitmap: Bitmap): Long {
            val small = Bitmap.createScaledBitmap(bitmap, 8, 8, false)
            val gray = IntArray(64)
            var sum = 0L
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val p = small.getPixel(x, y)
                    val g = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                    gray[y * 8 + x] = g
                    sum += g
                }
            }
            if (small !== bitmap) small.recycle()
            val avg = sum / 64
            var hash = 0L
            for (i in 0 until 64) {
                if (gray[i] > avg) hash = hash or (1L shl i)
            }
            return hash
        }

        fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
    }
}
