package nu.hyperworks.thorspeak.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** Checks GitHub Releases for a newer APK and hands it to the system installer. */
class UpdateManager(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    fun latestRelease(): Release {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub API HTTP ${resp.code}")
            return json.decodeFromString<Release>(resp.body!!.string())
        }
    }

    /** True if tag (e.g. "v0.1.2") is newer than the installed version name. */
    fun isNewer(tag: String, installed: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun downloadApk(release: Release, context: Context, onProgress: (Int) -> Unit): File {
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: throw IOException("release ${release.tag} has no APK asset")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "thorspeak-update.apk")
        val req = Request.Builder().url(asset.downloadUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("download HTTP ${resp.code}")
            val total = resp.body!!.contentLength().takeIf { it > 0 } ?: asset.size
            resp.body!!.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        return dest
    }

    /** Launches the system package installer; Android takes it from there. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        const val REPO = "mattkanwisher/thorspeak"
        const val AUTHORITY = "nu.hyperworks.thorspeak.fileprovider"
    }
}
