package nu.hyperworks.thorspeak.audio

import nu.hyperworks.thorspeak.net.ApiClient
import java.io.File

/**
 * Rotating on-device audio cache, keyed by the server-computed audio hash.
 * Plain files named {hash}.mp3 with LRU eviction by mtime — the same scheme
 * the server uses, so behavior is easy to reason about end to end.
 */
class AudioCache(private val dir: File, private val client: ApiClient) {

    @Volatile
    var maxBytes: Long = 200L * 1024 * 1024

    init {
        dir.mkdirs()
    }

    fun getOrFetch(hash: String, url: String): File {
        val file = File(dir, "$hash.mp3")
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        client.download(url, file)
        prune()
        return file
    }

    fun has(hash: String): Boolean = File(dir, "$hash.mp3").let { it.exists() && it.length() > 0 }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    @Synchronized
    fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".mp3") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
