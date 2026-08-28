package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.example.healthheatv2.data.NutritionDao
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.data.UserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import com.google.ai.edge.aicore.AiCore
import com.google.ai.edge.aicore.AiFeature
import com.google.ai.edge.aicore.AiFeatureStatus
import com.google.ai.edge.aicore.GenerativeModel

class NanoNutritionistCoach(
    private val context: Context,
    private val nutritionDao: NutritionDao
) {
    private var generativeModel: Any? = null // Using Any? so we don't crash if AICore fails completely

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val aiCore = AiCore.create(context)
            val status = aiCore.getFeatureStatus(AiFeature.GEMINI_NANO).await()
            if (status == AiFeatureStatus.DOWNLOADABLE) {
                aiCore.downloadFeature(AiFeature.GEMINI_NANO).await()
            }
            if (status == AiFeatureStatus.AVAILABLE || status == AiFeatureStatus.DOWNLOADABLE) {
                generativeModel = GenerativeModel("gemini-nano")
            }
            Log.d("NanoNutritionist", "Initialized Nano Coach. Status: $status")
        } catch (e: Exception) {
            Log.e("NanoNutritionist", "Failed to initialize Gemini Nano", e)
        }
    }

    suspend fun generateNutriBotResponse(chatHistory: List<com.example.healthheatv2.ui.screens.ChatMessage>, userProfile: UserProfile): String = withContext(Dispatchers.IO) {
        // 1. Calculate Deficit
        val consumedKcal = userProfile.dailyCalories ?: 0f
        val targetKcal = userProfile.bmr ?: 2000f
        val calorieDeficit = maxOf(0f, targetKcal - consumedKcal)
        val minProtein = (userProfile.dailyProtein ?: 50f) / 3f // Target 1/3 of daily protein per meal
        
        // 2. Query ICMR Rule for user's medical condition
        val medicalCondition = userProfile.healthTags.firstOrNull() ?: "general"
        val icmrRule = nutritionDao.getICMRRule(medicalCondition)
        
        // 3. Query IFCT Foods using RAG
        val requiredTag = icmrRule?.required_tags?.split(",")?.firstOrNull() ?: ""
        val bannedTag = icmrRule?.banned_tags?.split(",")?.firstOrNull() ?: ""
        
        val eligibleFoods = nutritionDao.getEligibleFoods(calorieDeficit, minProtein, requiredTag, bannedTag)
        
        // 4. Construct JSON Prompt
        val foodListJson = eligibleFoods.joinToString(", ") { "{name: '${it.food_name}', kcal: ${it.energy_kcal}, protein: ${it.protein_g}}" }
        
        // Format the last 3 messages for context memory
        val memoryContext = chatHistory.takeLast(3).joinToString("\n") { 
            if (it.isUser) "USER: ${it.text}" else "NUTRIBOT: ${it.text}" 
        }
        
        val systemPrompt = """
            SYSTEM INSTRUCTION: You are an expert dietitian. Answer the user strictly using this data:
            {
              "user_state": {
                "calorie_deficit": $calorieDeficit,
                "active_condition": "$medicalCondition"
              },
              "icmr_rules": {
                "must_avoid": "${icmrRule?.must_avoid}",
                "recommended": "${icmrRule?.recommended_swaps}"
              },
              "eligible_foods": [$foodListJson]
            }
            
            IMPORTANT LOGGING RULE: 
            If the user explicitly states they ate a food (e.g. "I just ate dal", "I had chicken for lunch"), you MUST include this exact tag at the very end of your response: [LOG_FOOD: Food_Name]. 
            Example User: "I had 1 bowl of spinach dal"
            Example You: "That's a great choice, high in fiber! [LOG_FOOD: Spinach Dal]"
            
            CONVERSATION HISTORY (Last 3 messages):
            $memoryContext
            
            USER'S LATEST REPLY: ${chatHistory.lastOrNull()?.text ?: ""}
        """.trimIndent()

        // 5. Try Gemini Nano first, then fallback
        try {
            if (generativeModel != null) {
                // AICore execution:
                val response = (generativeModel as GenerativeModel).generateContent(systemPrompt).await()
                return@withContext "⚡ [On-Device Nano]\n" + (response.text ?: "No response generated.")
            }
            
            // Fallback to Cloud AI (Groq Backend via Retrofit)
            val apiMessages = listOf(
                com.example.healthheatv2.network.ApiChatMessage(role = "system", content = systemPrompt),
                com.example.healthheatv2.network.ApiChatMessage(role = "user", content = chatHistory.lastOrNull()?.text ?: "Hello")
            )
            val response = com.example.healthheatv2.network.RetrofitClient.apiService.chat(
                com.example.healthheatv2.network.ChatRequest(messages = apiMessages)
            )
            return@withContext "☁️ [Cloud AI]\n" + response.response
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Ultimate offline mock fallback if backend fails
            val safeAvoid = icmrRule?.must_avoid ?: "unhealthy processed foods"
            val recommendedFoods = if (eligibleFoods.isNotEmpty()) eligibleFoods.take(2).joinToString { it.food_name } else "some leafy greens"
            return@withContext "🔋 [Offline Fallback]\nBased on your calorie deficit of ${calorieDeficit.toInt()} kcal and your profile ($medicalCondition), I recommend: $recommendedFoods. Remember to avoid $safeAvoid."
        }
    }
}
