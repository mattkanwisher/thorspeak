package nu.hyperworks.thorspeak.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class CaptureRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun isFullFrame() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f
}

data class AppSettings(
    val serverUrl: String,
    val intervalMs: Long,
    val lang: String,
    val cacheMaxMb: Int,
    val pixelOnlyGate: Boolean,
    val region: CaptureRegion,
    val voiceJa: String,
    val voiceEn: String,
    val voiceTh: String,
) {
    fun voiceFor(lang: String): String? = when (lang) {
        "ja" -> voiceJa
        "en" -> voiceEn
        "th" -> voiceTh
        else -> ""
    }.ifBlank { null }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val intervalMs = longPreferencesKey("interval_ms")
        val lang = stringPreferencesKey("lang")
        val cacheMaxMb = intPreferencesKey("cache_max_mb")
        val pixelOnly = booleanPreferencesKey("pixel_only_gate")
        val regionL = floatPreferencesKey("region_l")
        val regionT = floatPreferencesKey("region_t")
        val regionR = floatPreferencesKey("region_r")
        val regionB = floatPreferencesKey("region_b")
        val voiceJa = stringPreferencesKey("voice_ja")
        val voiceEn = stringPreferencesKey("voice_en")
        val voiceTh = stringPreferencesKey("voice_th")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.serverUrl] ?: "http://192.168.1.100:8737",
            intervalMs = p[Keys.intervalMs] ?: 1500L,
            lang = p[Keys.lang] ?: "ja",
            cacheMaxMb = p[Keys.cacheMaxMb] ?: 200,
            pixelOnlyGate = p[Keys.pixelOnly] ?: false,
            region = CaptureRegion(
                p[Keys.regionL] ?: 0f,
                p[Keys.regionT] ?: 0f,
                p[Keys.regionR] ?: 1f,
                p[Keys.regionB] ?: 1f,
            ),
            voiceJa = p[Keys.voiceJa] ?: "",
            voiceEn = p[Keys.voiceEn] ?: "",
            voiceTh = p[Keys.voiceTh] ?: "",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setServerUrl(url: String) = context.dataStore.edit { it[Keys.serverUrl] = url.trim().trimEnd('/') }
    suspend fun setIntervalMs(ms: Long) = context.dataStore.edit { it[Keys.intervalMs] = ms }
    suspend fun setLang(lang: String) = context.dataStore.edit { it[Keys.lang] = lang }
    suspend fun setCacheMaxMb(mb: Int) = context.dataStore.edit { it[Keys.cacheMaxMb] = mb }
    suspend fun setPixelOnlyGate(v: Boolean) = context.dataStore.edit { it[Keys.pixelOnly] = v }
    suspend fun setVoice(lang: String, voice: String) = context.dataStore.edit {
        when (lang) {
            "ja" -> it[Keys.voiceJa] = voice
            "en" -> it[Keys.voiceEn] = voice
            "th" -> it[Keys.voiceTh] = voice
        }
    }

    suspend fun setRegion(r: CaptureRegion) = context.dataStore.edit {
        it[Keys.regionL] = r.left
        it[Keys.regionT] = r.top
        it[Keys.regionR] = r.right
        it[Keys.regionB] = r.bottom
    }
}
