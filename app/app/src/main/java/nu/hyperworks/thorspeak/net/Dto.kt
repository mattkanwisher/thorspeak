package nu.hyperworks.thorspeak.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CachedFlags(val translation: Boolean, val tts: Boolean)

@Serializable
data class ProcessResponse(
    val text: String,
    val normalized: String,
    val lang: String,
    val translation: String? = null,
    val voice: String? = null,
    @SerialName("audio_hash") val audioHash: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    val cached: CachedFlags,
)

@Serializable
data class SpeakRequest(val text: String, val lang: String, val voice: String? = null)

@Serializable
data class LookupRequest(val word: String, val context: String? = null)

@Serializable
data class LookupResponse(
    val word: String,
    val reading: String,
    val meaning: String,
    @SerialName("part_of_speech") val partOfSpeech: String,
    val example: String,
    @SerialName("example_translation") val exampleTranslation: String,
)

@Serializable
data class FlashcardIn(
    val word: String,
    val reading: String,
    val meaning: String,
    @SerialName("part_of_speech") val partOfSpeech: String,
    val example: String,
    @SerialName("example_translation") val exampleTranslation: String,
    @SerialName("source_text") val sourceText: String? = null,
)

@Serializable
data class FlashcardDto(
    val id: Long,
    val word: String,
    val reading: String,
    val meaning: String,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    val example: String? = null,
    @SerialName("example_translation") val exampleTranslation: String? = null,
    @SerialName("source_text") val sourceText: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("ocr_model_loaded") val ocrModelLoaded: Boolean,
    val version: String,
)
