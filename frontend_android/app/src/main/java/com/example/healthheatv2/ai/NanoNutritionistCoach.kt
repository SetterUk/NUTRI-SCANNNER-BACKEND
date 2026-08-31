package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.example.healthheatv2.data.NutritionDao
import com.example.healthheatv2.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.common.FeatureStatus
import java.io.File

class NanoNutritionistCoach(
    private val context: Context,
    private val nutritionDao: NutritionDao,
    val modelManager: LLMModelManager = LLMModelManager(context),
    val gemmaManager: GemmaInferenceManager = GemmaInferenceManager(context)
) {
    private var generativeModel: GenerativeModel? = null // Gemini Nano AICore
    
    private val _downloadState = MutableStateFlow("Checking AI models...")
    val downloadState = _downloadState.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // 1. Try Initializing Gemma 2B on-device first
        val existingModel = modelManager.checkExistingModel()
        if (existingModel != null) {
            _downloadState.value = "Loading Gemma 2B..."
            val success = gemmaManager.initialize(existingModel)
            if (success) {
                _downloadState.value = "Model Ready (Gemma 2B GPU)"
                Log.d("NutritionistCoach", "Gemma 2B loaded successfully!")
                return@withContext
            }
        }

        // 2. Try Initializing Gemini Nano (ML Kit AICore) as fallback
        try {
            generativeModel = Generation.getClient()
            val status = generativeModel?.checkStatus()
            if (status == FeatureStatus.DOWNLOADABLE) {
                _downloadState.value = "Downloading Nano Model..."
                Log.d("NutritionistCoach", "Gemini Nano downloading...")
                generativeModel?.download()?.collect { downloadStatus ->
                    _downloadState.value = "Downloading Nano..."
                    Log.d("NutritionistCoach", "Download status: $downloadStatus")
                }
                _downloadState.value = "Model Ready (Nano)"
            } else if (status == FeatureStatus.AVAILABLE) {
                _downloadState.value = "Model Ready (Nano)"
                Log.d("NutritionistCoach", "Gemini Nano ready!")
            } else {
                _downloadState.value = "Cloud AI Ready"
            }
        } catch (e: Exception) {
            Log.w("NutritionistCoach", "Gemini Nano not supported on this device: ${e.message}")
            _downloadState.value = "Cloud AI Ready"
        }
    }

    suspend fun loadGemmaModel(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        _downloadState.value = "Loading Gemma 2B..."
        val success = gemmaManager.initialize(modelFile)
        if (success) {
            _downloadState.value = "Model Ready (Gemma 2B GPU)"
        } else {
            _downloadState.value = "Gemma Load Failed"
        }
        success
    }

    suspend fun generateNutriBotResponse(
        chatHistory: List<com.example.healthheatv2.ui.screens.ChatMessage>,
        userProfile: UserProfile,
        consumedKcal: Float = userProfile.dailyCalories ?: 0f
    ): String = withContext(Dispatchers.IO) {
        // 1. Calculate Deficit
        val actualConsumed = if (consumedKcal > 0f) consumedKcal else (userProfile.dailyCalories ?: 0f)
        val targetKcal = userProfile.bmr ?: 2000f
        val calorieDeficit = maxOf(0f, targetKcal - actualConsumed)
        val minProtein = (userProfile.dailyProtein ?: 50f) / 3f // Target 1/3 of daily protein per meal
        
        // 2. Query ICMR Rule for user's medical condition
        val medicalCondition = userProfile.healthTags.firstOrNull() ?: "general"
        val icmrRule = nutritionDao.getICMRRule(medicalCondition)
        
        // 3. Query IFCT Foods using local database
        val requiredTag = icmrRule?.required_tags?.split(",")?.firstOrNull() ?: ""
        val bannedTag = icmrRule?.banned_tags?.split(",")?.firstOrNull() ?: ""
        val eligibleFoods = nutritionDao.getEligibleFoods(calorieDeficit, minProtein, requiredTag, bannedTag)
        
        // 4. Construct JSON Knowledge Context
        val foodListJson = eligibleFoods.joinToString(", ") { "{name: '${it.food_name}', kcal: ${it.energy_kcal}, protein: ${it.protein_g}}" }
        
        val memoryContext = chatHistory.takeLast(3).joinToString("\n") { 
            if (it.isUser) "USER: ${it.text}" else "NUTRIBOT: ${it.text}" 
        }

        val systemPrompt = """
            You are an expert clinical dietitian and friendly nutrition coach.
            Strictly use this verified nutritional data:
            {
              "user_state": {
                "calorie_deficit": $calorieDeficit,
                "active_condition": "$medicalCondition"
              },
              "icmr_rules": {
                "must_avoid": "${icmrRule?.must_avoid ?: "excess refined sugars and trans fats"}",
                "recommended": "${icmrRule?.recommended_swaps ?: "whole grains, pulses, lean proteins"}"
              },
              "eligible_foods": [$foodListJson]
            }
            
            IMPORTANT LOGGING RULE:
            If the user explicitly states they ate a food (e.g. "I just ate dal", "I had chicken for lunch"), append this exact tag at the very end of your response: [LOG_FOOD: Food_Name].
            
            RECENT CONVERSATION:
            $memoryContext
        """.trimIndent()

        val latestUserQuery = chatHistory.lastOrNull()?.text ?: "Hello"

        // --- TIER 1: On-Device Gemma 2B / E2B SLM ---
        if (gemmaManager.isModelReady()) {
            try {
                Log.d("NutritionistCoach", "Executing inference on local Gemma 2B...")
                val formattedPrompt = gemmaManager.formatGemmaPrompt(
                    systemInstruction = systemPrompt,
                    userPrompt = latestUserQuery
                )
                val responseText = gemmaManager.generateResponse(formattedPrompt).trim()
                if (responseText.isNotBlank()) {
                    return@withContext "⚡ [Gemma 2B Local]\n$responseText"
                }
            } catch (e: Exception) {
                Log.w("NutritionistCoach", "Gemma 2B inference failed, falling back", e)
            }
        }

        // --- TIER 2: On-Device Gemini Nano AICore ---
        if (generativeModel != null) {
            try {
                val fullPrompt = "$systemPrompt\n\nUSER'S LATEST QUERY: $latestUserQuery"
                val response = generativeModel?.generateContent(fullPrompt)
                val generatedText = response?.candidates?.firstOrNull()?.text?.trim()
                if (!generatedText.isNullOrBlank()) {
                    return@withContext "⚡ [On-Device Nano]\n$generatedText"
                }
            } catch (e: Exception) {
                Log.w("NutritionistCoach", "Gemini Nano failed, falling back to Cloud AI", e)
            }
        }

        // --- TIER 3: Backend Cloud AI (Groq / FastAPI) ---
        try {
            val apiMessages = listOf(
                com.example.healthheatv2.network.ApiChatMessage(role = "system", content = systemPrompt),
                com.example.healthheatv2.network.ApiChatMessage(role = "user", content = latestUserQuery)
            )
            val response = com.example.healthheatv2.network.RetrofitClient.apiService.chat(
                com.example.healthheatv2.network.ChatRequest(messages = apiMessages)
            )
            return@withContext "☁️ [Cloud AI]\n" + response.response
        } catch (e: Exception) {
            Log.e("NutritionistCoach", "Cloud AI failed", e)
        }

        // --- TIER 4: Pure Offline Heuristic Fallback ---
        val safeAvoid = icmrRule?.must_avoid ?: "unhealthy processed foods"
        val recommendedFoods = if (eligibleFoods.isNotEmpty()) {
            eligibleFoods.take(2).joinToString { it.food_name }
        } else {
            "sprouted moong or roasted chana"
        }
        return@withContext "🔋 [Offline Fallback]\nBased on your daily deficit (${calorieDeficit.toInt()} kcal) and health profile ($medicalCondition), I recommend: $recommendedFoods. Please avoid $safeAvoid."
    }
}
