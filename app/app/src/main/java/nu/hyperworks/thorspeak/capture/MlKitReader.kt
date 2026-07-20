package nu.hyperworks.thorspeak.capture

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.Normalizer
import kotlin.coroutines.resume

/**
 * On-device ML Kit Japanese OCR, used only as a change gate: its output is
 * compared against its own previous output, never against server text, so
 * mediocre accuracy on pixel fonts is fine.
 */
/** Recognized text plus the union bounding box of all blocks (crop coordinates). */
data class GateRead(val text: String, val box: Rect?)

class MlKitReader {

    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    suspend fun read(bitmap: Bitmap): GateRead = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                var union: Rect? = null
                for (block in result.textBlocks) {
                    val b = block.boundingBox ?: continue
                    union = union?.apply { union(b) } ?: Rect(b)
                }
                cont.resume(GateRead(result.text, union))
            }
            .addOnFailureListener { cont.resume(GateRead("", null)) }
    }

    fun close() = recognizer.close()

    companion object {
        /** Approximate mirror of the server's normalization — NFKC + strip whitespace. */
        fun normalizeLocal(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")
    }
}
