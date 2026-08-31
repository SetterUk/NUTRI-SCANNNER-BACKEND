package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps the ML Kit GenAI API and MediaPipe LLM Inference for Gemma on-device execution.
 * Manages the full model lifecycle: status check → GPU init → generation → disposal.
 * Thread-safe with internal Mutex to prevent native JNI crashes.
 */
class GemmaInferenceManager(private val context: Context) {

    sealed class ModelState {
        object Unloaded : ModelState()
        object Initializing : ModelState()
        data class Downloading(val progressLabel: String) : ModelState()
        object Ready : ModelState()
        data class Error(val message: String) : ModelState()
        object Unavailable : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Unloaded)
    val modelState = _modelState.asStateFlow()

    private var generativeModel: GenerativeModel? = null
    private var llmInference: LlmInference? = null
    private val inferenceMutex = Mutex()
    private var isInitializing = false

    val isReady: Boolean get() = _modelState.value is ModelState.Ready

    /**
     * Wraps a system prompt + user query into Gemma instruction turn format.
     */
    private fun formatGemmaPrompt(systemPrompt: String, userTurn: String): String =
        "<start_of_turn>user\n$systemPrompt\n\n$userTurn<end_of_turn>\n<start_of_turn>model\n"

    /**
     * Initializes Gemma on-device inference once.
     * Checks for local model file first, then falls back to Android AICore Prompt API.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference != null || _modelState.value is ModelState.Ready || isInitializing) {
            Log.d(TAG, "Gemma model already loaded on GPU or initializing. Skipping redundant init.")
            return@withContext
        }
        isInitializing = true
        try {
            _modelState.value = ModelState.Initializing
            Log.d(TAG, "Initializing Gemma on-device inference...")

            // 1. Check if a model file exists on disk (filesDir or /data/local/tmp)
            val sideloaded = LLMModelManager(context).findSideloadedModel()
            if (sideloaded != null) {
                Log.d(TAG, "✅ Found local model on disk: ${sideloaded.file.absolutePath} (${sideloaded.sizeMb.toInt()} MB)")
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(sideloaded.file.absolutePath)
                        .setMaxTokens(1280)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    _modelState.value = ModelState.Ready
                    Log.d(TAG, "✅ MediaPipe LlmInference loaded successfully onto GPU.")
                    return@withContext
                } catch (e: Throwable) {
                    Log.e(TAG, "MediaPipe LlmInference init failed", e)
                }
            }

            // 2. Otherwise query Android AICore / Google Play GenAI Prompt API
            val client = Generation.getClient()
            val status = client.checkStatus()
            Log.d(TAG, "Model feature status: $status")

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    generativeModel = client
                    _modelState.value = ModelState.Ready
                    Log.d(TAG, "✅ Gemma ready via AICore on GPU.")
                }
                FeatureStatus.DOWNLOADABLE -> {
                    Log.d(TAG, "⬇️ Gemma needs download via AICore. Initiating...")
                    _modelState.value = ModelState.Downloading("Starting download...")
                    try {
                        client.download().collect { _ ->
                            _modelState.value = ModelState.Downloading("Downloading Gemma...")
                            Log.d(TAG, "Download in progress...")
                        }
                        generativeModel = client
                        _modelState.value = ModelState.Ready
                        Log.d(TAG, "✅ Gemma download complete. Model is Ready.")
                    } catch (ex: Exception) {
                        Log.e(TAG, "Download failed", ex)
                        _modelState.value = ModelState.Error("Download failed: ${ex.message}")
                    }
                }
                FeatureStatus.UNAVAILABLE -> {
                    Log.w(TAG, "⚠️ Gemma unavailable on this device. Falling back to cloud.")
                    _modelState.value = ModelState.Unavailable
                }
                else -> {
                    Log.w(TAG, "Model feature status: $status. Marking unavailable.")
                    _modelState.value = ModelState.Unavailable
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GenAI", e)
            _modelState.value = ModelState.Error("Init failed: ${e.message}")
        } finally {
            isInitializing = false
        }
    }

    /**
     * Runs a single inference on-device with Mutex thread-safety.
     */
    suspend fun generateContent(systemPrompt: String, userQuery: String): String? =
        withContext(Dispatchers.IO) {
            inferenceMutex.withLock {
                // Keep total prompt compact to stay within GPU buffer
                val safeSystemPrompt = if (systemPrompt.length > 1500) systemPrompt.take(1500) + "\n...}" else systemPrompt
                val formattedPrompt = formatGemmaPrompt(safeSystemPrompt, userQuery)

                // Try MediaPipe LlmInference first (local .bin file)
                llmInference?.let { inference ->
                    return@withContext try {
                        val result = inference.generateResponse(formattedPrompt)
                        Log.d(TAG, "✅ MediaPipe on-device generation complete. Result length: ${result?.length ?: 0}")
                        result
                    } catch (e: Throwable) {
                        Log.e(TAG, "MediaPipe inference error", e)
                        null
                    }
                }

                // Otherwise try ML Kit GenAI AICore
                val model = generativeModel
                if (model != null && _modelState.value is ModelState.Ready) {
                    return@withContext try {
                        val response = model.generateContent(formattedPrompt)
                        val text = response.candidates?.firstOrNull()?.text
                        Log.d(TAG, "✅ AICore on-device generation complete.")
                        text
                    } catch (e: Throwable) {
                        Log.e(TAG, "AICore generation failed", e)
                        null
                    }
                }

                Log.w(TAG, "generateContent called but no on-device model ready.")
                return@withContext null
            }
        }

    /**
     * Releases the model from GPU/RAM memory.
     */
    fun dispose() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing llmInference", e)
        }
        llmInference = null

        try {
            generativeModel?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during dispose", e)
        }
        generativeModel = null
        _modelState.value = ModelState.Unloaded
        Log.d(TAG, "GemmaInferenceManager disposed. GPU memory freed.")
    }

    /**
     * Short human-readable label for the UI status badge in NutritionistChatSc.
     */
    fun getStatusLabel(): String = when (val s = _modelState.value) {
        is ModelState.Ready        -> "⚡ Gemma 4 (On-Device)"
        is ModelState.Downloading  -> "⬇️ ${s.progressLabel}"
        is ModelState.Initializing -> "⏳ Loading Gemma..."
        is ModelState.Unloaded     -> "☁️ Cloud AI (Default)"
        is ModelState.Error        -> "☁️ Cloud Mode"
        is ModelState.Unavailable  -> "☁️ Cloud Mode"
    }

    companion object {
        private const val TAG = "GemmaInferenceManager"
    }
}