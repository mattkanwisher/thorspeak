package nu.hyperworks.thorspeak

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nu.hyperworks.thorspeak.audio.AudioCache
import nu.hyperworks.thorspeak.audio.Player
import nu.hyperworks.thorspeak.data.SettingsRepository
import nu.hyperworks.thorspeak.net.ApiClient
import java.io.File

class ThorSpeakApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var audioCache: AudioCache
        private set
    lateinit var player: Player
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        apiClient = ApiClient()
        audioCache = AudioCache(File(cacheDir, "audio"), apiClient)
        player = Player(this)
        appScope.launch {
            settingsRepository.settings.collect { s ->
                audioCache.maxBytes = s.cacheMaxMb.toLong() * 1024 * 1024
            }
        }
    }
}
