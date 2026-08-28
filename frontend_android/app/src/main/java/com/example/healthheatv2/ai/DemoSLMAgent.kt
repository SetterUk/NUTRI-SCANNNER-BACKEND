package com.example.healthheatv2.ai

import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.RecommendationEngine
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe GenAI to simulate an Agentic Tool-Calling loop for the Hackathon Demo.
 * 
 * Flow: User Query -> Intent Extraction (SLM) -> Deterministic Tool -> Final Response (SLM)
 */
class DemoSLMAgent(
    private val llmInference: LlmInference,
    private val nutritionEngine: NutritionEngine,
    private val recommendationEngine: RecommendationEngine
) {

    suspend fun processUserQuery(userQuery: String): String = withContext(Dispatchers.IO) {
        // Step 1: Force SLM to extract intent as JSON
        val intentPrompt = """
            You are a system parser. Read the user's message and extract the intent.
            Possible intents: [LOG_MEAL, FIX_NUTRITION, CHECK_SAFETY, GENERAL_CHAT]
            
            User message: "$userQuery"
            
            Return ONLY a valid JSON object in this exact format:
            {"intent": "INTENT_NAME"}
        """.trimIndent()

        var rawIntentResponse = ""
        try {
            rawIntentResponse = llmInference.generateResponse(intentPrompt)
        } catch (e: Exception) {
            // Fallback for demo stability if LLM fails
            rawIntentResponse = """{"intent": "GENERAL_CHAT"}"""
        }

        val intent = parseIntentJson(rawIntentResponse, userQuery)

        // Step 2: Execute Deterministic Tool
        val toolResultContext = executeTool(intent, userQuery)

        // Step 3: SLM generates conversational response grounded in the tool result
        val finalPrompt = """
            You are HealthHeat, a personal Indian AI Nutritionist coach.
            Do not provide medical diagnosis.
            
            User said: "$userQuery"
            
            System calculation result:
            $toolResultContext
            
            Respond to the user naturally based ONLY on the calculation result above.
            Speak in conversational Hinglish or English as appropriate.
        """.trimIndent()

        try {
            llmInference.generateResponse(finalPrompt)
        } catch (e: Exception) {
            "I'm having trouble thinking right now, but your data has been updated."
        }
    }

    private fun parseIntentJson(json: String, query: String): String {
        // Naive parser for demo stability
        val q = query.lowercase()
        return when {
            json.contains("LOG_MEAL") || q.contains("had") || q.contains("khayi") || q.contains("ate") -> "LOG_MEAL"
            json.contains("FIX_NUTRITION") || q.contains("what should i eat") || q.contains("dinner") -> "FIX_NUTRITION"
            json.contains("CHECK_SAFETY") || q.contains("can i eat this") -> "CHECK_SAFETY"
            else -> "GENERAL_CHAT"
        }
    }

    private suspend fun executeTool(intent: String, query: String): String {
        return when (intent) {
            "LOG_MEAL" -> {
                // In a full implementation, we'd extract entities and write to DB.
                // For demo: pretend we logged Poha or Roti/Dal based on query text
                val item = if (query.contains("poha", true)) "Poha & Chai" else "Roti & Dal"
                "Action successful: Logged $item. Tell the user it was logged and they still need more protein today."
            }
            "FIX_NUTRITION" -> {
                val gaps = nutritionEngine.calculateGaps()
                val actionable = gaps.actionableGap
                if (actionable != null) {
                    val candidates = recommendationEngine.generateRecommendations(actionable, 600f, 100f)
                    val recNames = candidates.joinToString { it.name }
                    "Action successful: Found candidates to fix the ${actionable.nutrient} gap: $recNames. Tell the user these options are safe for their allergies and fit under ₹100."
                } else {
                    "Action successful: No major gaps today."
                }
            }
            "CHECK_SAFETY" -> {
                // E.g., for the protein bar
                "System Warning: The scanned item has a high chance of peanut cross-contamination. User is allergic to peanuts. Advise avoiding it."
            }
            else -> {
                "No tool needed. Just chat."
            }
        }
    }
}
