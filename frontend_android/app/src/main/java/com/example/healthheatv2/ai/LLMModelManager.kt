package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data class Ready(val modelFile: File, val sizeMb: Long) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

class LLMModelManager(private val context: Context) {
    companion object {
        // Preferred candidate model filenames supported by MediaPipe Tasks GenAI
        val CANDIDATE_MODEL_NAMES = listOf(
            "gemma-2b-it.bin",
            "gemma2-2b-it.bin",
            "gemma2-2b-it-gpu-int4.bin",
            "gemma-2b-it.task",
            "gemma-e2b.bin",
            "model.task",
            "gemma-1b-it.task"
        )

        // Publicly accessible MediaPipe / LiteRT model mirror or gated HuggingFace URL
        const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Gemma-2-2b-it-gpu-int4/resolve/main/model.task"
    }

    private val prefs = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val modelDir: File
        get() {
            val dir = File(context.filesDir, "llm_models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        checkExistingModel()
    }

    fun getHfToken(): String? = prefs.getString("hf_token", null)

    fun setHfToken(token: String) {
        prefs.edit().putString("hf_token", token.trim()).apply()
    }

    fun checkExistingModel(): File? {
        val searchDirs = listOfNotNull(
            modelDir,
            File(context.cacheDir, "llm_models"),
            context.filesDir,
            context.getExternalFilesDir("llm_models"),
            context.getExternalFilesDir(null),
            File("/data/local/tmp"),
            File("/sdcard/Download")
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            // 1. Check known candidate filenames
            for (candidate in CANDIDATE_MODEL_NAMES) {
                val file = File(dir, candidate)
                if (file.exists() && file.length() > 10 * 1024 * 1024) { // Valid if > 10MB
                    val sizeMb = file.length() / (1024 * 1024)
                    Log.d("LLMModelManager", "Found valid Gemma model at: ${file.absolutePath} ($sizeMb MB)")
                    _downloadState.value = ModelDownloadState.Ready(file, sizeMb)
                    return file
                }
            }
            // 2. Auto-detect any .task or .bin file > 10MB in the folder
            val extraFiles = dir.listFiles { _, name -> 
                name.endsWith(".task", ignoreCase = true) || name.endsWith(".bin", ignoreCase = true) 
            }
            if (!extraFiles.isNullOrEmpty()) {
                val modelFile = extraFiles.firstOrNull { it.length() > 10 * 1024 * 1024 }
                if (modelFile != null) {
                    val sizeMb = modelFile.length() / (1024 * 1024)
                    Log.d("LLMModelManager", "Auto-discovered Gemma task file at: ${modelFile.absolutePath} ($sizeMb MB)")
                    _downloadState.value = ModelDownloadState.Ready(modelFile, sizeMb)
                    return modelFile
                }
            }
        }

        _downloadState.value = ModelDownloadState.NotDownloaded
        return null
    }

    suspend fun getOrDownloadModel(
        url: String = DEFAULT_MODEL_URL,
        hfToken: String? = getHfToken()
    ): File = withContext(Dispatchers.IO) {
        val existing = checkExistingModel()
        if (existing != null) {
            return@withContext existing
        }

        val targetFile = File(modelDir, "gemma-2b-it.bin")
        downloadModelFile(url, targetFile, hfToken)
        return@withContext targetFile
    }

    suspend fun downloadModelFile(urlStr: String, destFile: File, hfToken: String? = null) = withContext(Dispatchers.IO) {
        try {
            Log.d("LLMModelManager", "Starting download from $urlStr to ${destFile.absolutePath}")
            _downloadState.value = ModelDownloadState.Downloading(0, 0, 0)

            var currentUrl = urlStr
            var connection: HttpURLConnection? = null
            var redirects = 0
            val maxRedirects = 10

            // Follow redirects manually to handle auth and CDN redirects (HuggingFace -> CloudFront/S3)
            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true

                // Attach Hugging Face auth token if downloading from HuggingFace
                val token = hfToken ?: getHfToken()
                if (!token.isNullOrBlank() && (currentUrl.contains("huggingface.co") || currentUrl.contains("hf.co"))) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }

                connection.connect()
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    Log.d("LLMModelManager", "Redirecting ($responseCode) to: $newUrl")
                    currentUrl = newUrl
                    redirects++
                } else if (responseCode == 401 || responseCode == 403) {
                    throw IllegalStateException(
                        "HTTP $responseCode: Hugging Face requires authentication for Gemma. " +
                        "Please provide your Hugging Face User Access Token (read) or push model via ADB."
                    )
                } else if (responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $responseCode: ${connection.responseMessage}")
                } else {
                    break // Successful connection
                }
            }

            val conn = connection ?: throw IllegalStateException("Failed to establish connection")
            val totalBytes = conn.contentLengthLong
            val tempFile = File(destFile.parentFile, "${destFile.name}.download")

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastEmittedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt()
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                _downloadState.value = ModelDownloadState.Downloading(percent, totalRead, totalBytes)
                            }
                        }
                    }
                }
            }

            if (tempFile.renameTo(destFile)) {
                val sizeMb = destFile.length() / (1024 * 1024)
                Log.d("LLMModelManager", "Download complete: ${destFile.absolutePath} ($sizeMb MB)")
                _downloadState.value = ModelDownloadState.Ready(destFile, sizeMb)
            } else {
                throw IllegalStateException("Failed to move temporary download file to destination.")
            }
        } catch (e: Exception) {
            Log.e("LLMModelManager", "Download failed: ${e.message}", e)
            _downloadState.value = ModelDownloadState.Error(e.localizedMessage ?: "Download failed")
            throw e
        }
    }
}
