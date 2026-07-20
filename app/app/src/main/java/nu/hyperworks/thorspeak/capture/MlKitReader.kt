package nu.hyperworks.thorspeak.capture

import android.graphics.Bitmap
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
class MlKitReader {

    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { cont.resume("") }
    }

    fun close() = recognizer.close()

    companion object {
        /** Approximate mirror of the server's normalization — NFKC + strip whitespace. */
        fun normalizeLocal(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")
    }
}
