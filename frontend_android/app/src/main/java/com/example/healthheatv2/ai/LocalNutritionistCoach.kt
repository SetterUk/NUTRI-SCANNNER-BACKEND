package com.example.healthheatv2.ai

import android.content.Context
import com.example.healthheatv2.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.network.ChatRequest
import com.example.healthheatv2.network.ApiChatMessage
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LocalNutritionistCoach(
    private val context: Context,
    private var userProfile: UserProfile
) {
    private val conversationHistory = mutableListOf<ApiChatMessage>()
    private val promptBuilder = NutritionistPrompt()
    private var llmInference: LlmInference? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath("/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin")
                    .setMaxTokens(1024)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val currentContext = CurrentContext() 
        return promptBuilder.buildSystemPrompt(userProfile, currentContext)
    }

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            return@withContext "Sorry, the local AI model (MediaPipe) could not be loaded. Please ensure the model file is on the device."
        }

        if (conversationHistory.isEmpty()) {
            val systemPrompt = buildSystemPrompt()
            conversationHistory.add(ApiChatMessage("system", systemPrompt))
        }
        
        conversationHistory.add(ApiChatMessage("user", userMessage))
        
        val fullPrompt = conversationHistory.joinToString("\n") { 
            if (it.role == "system") it.content else "${it.role}: ${it.content}" 
        } + "\nassistant: "

        return@withContext try {
            val response = llmInference?.generateResponse(fullPrompt) ?: "I'm having trouble thinking right now."
            conversationHistory.add(ApiChatMessage("assistant", response))
            response
        } catch (e: Exception) {
            e.printStackTrace()
            "Sorry, the local AI brain encountered an error."
        }
    }

    fun resetContextAndChat(userMessage: String, freshProfile: UserProfile) {
        userProfile = freshProfile
        conversationHistory.clear()
    }
}
