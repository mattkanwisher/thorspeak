package nu.hyperworks.thorspeak.data

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nu.hyperworks.thorspeak.ThorSpeakApp
import nu.hyperworks.thorspeak.net.FlashcardDto
import nu.hyperworks.thorspeak.net.FlashcardIn
import nu.hyperworks.thorspeak.net.LookupRequest
import nu.hyperworks.thorspeak.net.LookupResponse
import nu.hyperworks.thorspeak.net.SpeakRequest
import java.io.File

sealed interface LookupState {
    data object Hidden : LookupState
    data class Loading(val word: String) : LookupState
    data class Ready(val result: LookupResponse, val added: Boolean = false) : LookupState
    data class Failed(val message: String) : LookupState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<ThorSpeakApp>()
    private val settingsRepo get() = app.settingsRepository

    val settings = settingsRepo.settings

    private val _lookup = MutableStateFlow<LookupState>(LookupState.Hidden)
    val lookup: StateFlow<LookupState> = _lookup

    private val _flashcards = MutableStateFlow<List<FlashcardDto>>(emptyList())
    val flashcards: StateFlow<List<FlashcardDto>> = _flashcards

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }

    private suspend fun api() = app.apiClient.api(settingsRepo.current().serverUrl)

    fun setLang(lang: String) = viewModelScope.launch { settingsRepo.setLang(lang) }

    /** Replay the last recognized line, honoring the currently selected language. */
    fun replay() = viewModelScope.launch(Dispatchers.IO) {
        val last = SessionState.lastResponse.value ?: return@launch
        val s = settingsRepo.current()
        try {
            if (last.lang == s.lang && last.audioHash != null && app.audioCache.has(last.audioHash)) {
                app.player.replay()
                return@launch
            }
            // Different language (or evicted): re-request via /speak using the
            // original Japanese text — the server caches make this cheap.
            val resp = api().speak(SpeakRequest(last.text, s.lang, s.voiceFor(s.lang)))
            SessionState.setResponse(resp)
            val hash = resp.audioHash ?: return@launch
            val file = app.audioCache.getOrFetch(hash, s.serverUrl.trimEnd('/') + resp.audioUrl)
            app.player.play(file)
        } catch (e: Exception) {
            _message.value = "Replay failed: ${e.message}"
        }
    }

    fun lookupWord(word: String) = viewModelScope.launch(Dispatchers.IO) {
        _lookup.value = LookupState.Loading(word)
        try {
            val context = SessionState.lastResponse.value?.normalized
            val result = api().lookup(LookupRequest(word, context))
            _lookup.value = LookupState.Ready(result)
        } catch (e: Exception) {
            _lookup.value = LookupState.Failed(e.message ?: "lookup failed")
        }
    }

    fun dismissLookup() { _lookup.value = LookupState.Hidden }

    fun addToFlashcards(result: LookupResponse) = viewModelScope.launch(Dispatchers.IO) {
        try {
            api().addFlashcard(
                FlashcardIn(
                    word = result.word,
                    reading = result.reading,
                    meaning = result.meaning,
                    partOfSpeech = result.partOfSpeech,
                    example = result.example,
                    exampleTranslation = result.exampleTranslation,
                    sourceText = SessionState.lastResponse.value?.normalized,
                ),
            )
            _lookup.value = LookupState.Ready(result, added = true)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) {
                _lookup.value = LookupState.Ready(result, added = true)
                _message.value = "Already in flashcards"
            } else {
                _message.value = "Add failed: HTTP ${e.code()}"
            }
        } catch (e: Exception) {
            _message.value = "Add failed: ${e.message}"
        }
    }

    fun loadFlashcards() = viewModelScope.launch(Dispatchers.IO) {
        try {
            _flashcards.value = api().flashcards()
        } catch (e: Exception) {
            _message.value = "Load failed: ${e.message}"
        }
    }

    fun deleteFlashcard(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            api().deleteFlashcard(id)
            _flashcards.value = _flashcards.value.filterNot { it.id == id }
        } catch (e: Exception) {
            _message.value = "Delete failed: ${e.message}"
        }
    }

    /** Download the .apkg from the server and open a share sheet (AnkiDroid accepts it). */
    fun exportAnki() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val s = settingsRepo.current()
            val dir = File(app.cacheDir, "export").apply { mkdirs() }
            val dest = File(dir, "thorspeak.apkg")
            app.apiClient.download(s.serverUrl.trimEnd('/') + "/anki/export.apkg", dest)
            val uri = FileProvider.getUriForFile(app, "nu.hyperworks.thorspeak.fileprovider", dest)
            val share = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) {
                app.startActivity(
                    Intent.createChooser(share, "Export Anki deck")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } catch (e: Exception) {
            _message.value = "Export failed: ${e.message}"
        }
    }

    fun testServer(onResult: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val text = try {
            val h = api().health()
            "OK — server ${h.version}, OCR loaded: ${h.ocrModelLoaded}"
        } catch (e: Exception) {
            "Unreachable: ${e.message}"
        }
        withContext(Dispatchers.Main) { onResult(text) }
    }
}
