package nu.hyperworks.thorspeak.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.File
import java.util.concurrent.TimeUnit

interface ThorSpeakApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @Multipart
    @POST("process")
    suspend fun process(
        @Part image: MultipartBody.Part,
        @Part("lang") lang: RequestBody,
        @Part("voice") voice: RequestBody?,
        @Part("gate_text") gateText: RequestBody?,
    ): ProcessResponse

    @POST("speak")
    suspend fun speak(@Body req: SpeakRequest): ProcessResponse

    @POST("lookup")
    suspend fun lookup(@Body req: LookupRequest): LookupResponse

    @POST("flashcards")
    suspend fun addFlashcard(@Body card: FlashcardIn): FlashcardDto

    @GET("flashcards")
    suspend fun flashcards(): List<FlashcardDto>

    @DELETE("flashcards/{id}")
    suspend fun deleteFlashcard(@Path("id") id: Long): Response<Unit>
}

/** Holds a Retrofit instance, rebuilt whenever the configured base URL changes. */
class ApiClient {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var currentUrl: String? = null
    private var api: ThorSpeakApi? = null

    @Synchronized
    fun api(baseUrl: String): ThorSpeakApi {
        val url = baseUrl.trimEnd('/') + "/"
        if (api == null || currentUrl != url) {
            api = Retrofit.Builder()
                .baseUrl(url)
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ThorSpeakApi::class.java)
            currentUrl = url
        }
        return api!!
    }

    /** Plain download (audio files, apkg export) via OkHttp. */
    fun download(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
            if (!tmp.renameTo(dest)) throw java.io.IOException("rename failed for $dest")
        }
    }

    companion object {
        fun jpegPart(bytes: ByteArray): MultipartBody.Part =
            MultipartBody.Part.createFormData(
                "image", "frame.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()),
            )

        fun textPart(value: String): RequestBody =
            value.toRequestBody("text/plain".toMediaType())
    }
}
