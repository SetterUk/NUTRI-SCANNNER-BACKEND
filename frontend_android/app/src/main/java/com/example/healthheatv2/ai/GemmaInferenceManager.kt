package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

class GemmaInferenceManager(private val context: Context) {
    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var modelFile: File? = null

    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    fun isModelReady(): Boolean = isInitialized && llmInference != null

    suspend fun initialize(model: File): Boolean = withContext(Dispatchers.IO) {
        if (!model.exists() || model.length() == 0L) {
            Log.w("GemmaInference", "Model file does not exist or is empty: ${model.absolutePath}")
            return@withContext false
        }

        try {
            Log.d("GemmaInference", "Initializing Gemma 2B from: ${model.absolutePath} (Size: ${model.length() / (1024 * 1024)} MB)")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.absolutePath)
                .setMaxTokens(512)
                .setResultListener { partialResult, done ->
                    _partialResults.tryEmit(partialResult)
                    if (done) {
                        Log.d("GemmaInference", "Generation completed.")
                    }
                }
                .setErrorListener { error ->
                    Log.e("GemmaInference", "MediaPipe LlmInference error: $error")
                }
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            modelFile = model
            Log.d("GemmaInference", "Gemma 2B successfully initialized on-device!")
            true
        } catch (t: Throwable) {
            Log.e("GemmaInference", "Failed to initialize Gemma 2B: ${t.message}", t)
            isInitialized = false
            llmInference = null
            false
        }
    }

    /**
     * Formats prompt with Gemma turn tokens:
     * <start_of_turn>user\n${prompt}<end_of_turn>\n<start_of_turn>model\n
     */
    fun formatGemmaPrompt(systemInstruction: String, userPrompt: String): String {
        return "<start_of_turn>user\n" +
                "$systemInstruction\n\n" +
                "$userPrompt<end_of_turn>\n" +
                "<start_of_turn>model\n"
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        val inference = llmInference ?: throw IllegalStateException("Gemma model not initialized")
        try {
            val response = inference.generateResponse(prompt)
            response ?: ""
        } catch (e: Exception) {
            Log.e("GemmaInference", "Error during Gemma inference", e)
            throw e
        }
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e("GemmaInference", "Error closing LlmInference", e)
        } finally {
            llmInference = null
            isInitialized = false
        }
    }
}
