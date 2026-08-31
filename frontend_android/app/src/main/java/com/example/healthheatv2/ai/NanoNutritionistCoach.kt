package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.example.healthheatv2.data.NutritionDao
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.services.GapAnalysis
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.ui.screens.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Central AI orchestrator for NutriScanner.
 *
 * Routes all AI tasks through a 3-tier cascade:
 *   1. ⚡ Gemma 4 E2B (on-device, private, zero-cost)
 *   2. ☁️ Cloud Groq API (openai/gpt-oss-120b, fallback)
 *   3. 🔋 Offline ICMR + SQLite rules (always available)
 *
 * Exposes 5 task-specific methods, each injecting the full user context
 * and real-time nutrition data into a tailored Gemma 4 prompt.
 */
class NanoNutritionistCoach(
    private val context: Context,
    private val nutritionDao: NutritionDao,
    private val gemmaManager: GemmaInferenceManager
) {
    private val promptBuilder = NutritionistPrompt()
    private val prefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)

    private val KEY_USE_LOCAL_MODEL = "use_local_model"
    private val KEY_PROMPTED_LOCAL_MODEL = "prompted_local_model"

    private val _useLocalModel = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean(KEY_USE_LOCAL_MODEL, false))
    val useLocalModel: StateFlow<Boolean> = _useLocalModel

    /** Exposes the Gemma 4 model state for the UI status badge. */
    val modelState: StateFlow<GemmaInferenceManager.ModelState> get() = gemmaManager.modelState

    fun getStatusLabel(): String {
        return if (!_useLocalModel.value) {
            "☁️ Cloud AI (Default)"
        } else {
            gemmaManager.getStatusLabel()
        }
    }

    fun isLocalModelReady(): Boolean = gemmaManager.isReady

    fun hasPromptedLocalModel(): Boolean = prefs.getBoolean(KEY_PROMPTED_LOCAL_MODEL, false)

    fun setHasPromptedLocalModel(prompted: Boolean = true) {
        prefs.edit().putBoolean(KEY_PROMPTED_LOCAL_MODEL, prompted).apply()
    }

    /**
     * Enables or disables on-device local model inference.
     * When enabling, loads Gemma into GPU/RAM memory.
     * When disabling, unloads Gemma and immediately frees GPU/RAM memory.
     */
    suspend fun setUseLocalModel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_MODEL, enabled).apply()
        _useLocalModel.value = enabled
        if (enabled) {
            gemmaManager.initialize()
        } else {
            gemmaManager.dispose()
        }
    }

    /** Loads local model into memory and enables on-device mode */
    suspend fun loadLocalModel() {
        setUseLocalModel(true)
    }

    /** Unloads local model from memory, freeing GPU/RAM, and reverts to Cloud default */
    fun unloadLocalModel() {
        prefs.edit().putBoolean(KEY_USE_LOCAL_MODEL, false).apply()
        _useLocalModel.value = false
        gemmaManager.dispose()
    }

    /** Initializes on-device model */
    suspend fun initialize() = gemmaManager.initialize()

    // ── Internal helper: fetch all real-time context from DB ─────────────────

    private suspend fun fetchLiveContext(
        nutritionEngine: NutritionEngine,
        userProfile: UserProfile
    ): Triple<com.example.healthheatv2.services.NutritionIntake, GapAnalysis, com.example.healthheatv2.data.ICMRRule?> {
        val intake = nutritionEngine.getTodayIntake()
        val gaps = nutritionEngine.calculateGaps()
        val condition = userProfile.healthTags.firstOrNull() ?: "general"
        val icmrRule = nutritionDao.getICMRRule(condition)
        return Triple(intake, gaps, icmrRule)
    }

    // ── 3-Tier cascade ────────────────────────────────────────────────────────

    private suspend fun runCascade(
        systemPrompt: String,
        userQuery: String,
        offlineFallback: () -> String
    ): String {
        // Tier 1: Gemma 4 on-device (ONLY if user opted-in and model is loaded/ready)
        if (_useLocalModel.value && gemmaManager.isReady) {
            val result = gemmaManager.generateContent(systemPrompt, userQuery)
            if (!result.isNullOrBlank()) {
                Log.d(TAG, "✅ Responded via Gemma 4 (on-device)")
                return "⚡ [Gemma 4]\n$result"
            }
            Log.w(TAG, "Gemma 4 returned empty. Falling back to cloud.")
        }

        // Tier 2: Cloud Groq API (Default)
        return try {
            val apiMessages = listOf(
                com.example.healthheatv2.network.ApiChatMessage(role = "system", content = systemPrompt),
                com.example.healthheatv2.network.ApiChatMessage(role = "user", content = userQuery)
            )
            val response = com.example.healthheatv2.network.RetrofitClient.apiService.chat(
                com.example.healthheatv2.network.ChatRequest(messages = apiMessages)
            )
            Log.d(TAG, "✅ Responded via Cloud AI")
            "☁️ [Cloud AI]\n${response.response}"
        } catch (e: Exception) {
            Log.w(TAG, "Cloud AI failed. Using offline ICMR fallback.", e)
            // Tier 3: Offline ICMR fallback
            "🔋 [Offline]\n${offlineFallback()}"
        }
    }

    // ── 1. NUTRIBOT CHAT ─────────────────────────────────────────────────────

    /**
     * Main conversational chat. Called from NutritionistChatSc.
     * Fetches live gaps + ICMR rules + eligible foods on every call.
     */
    suspend fun generateNutriBotResponse(
        chatHistory: List<ChatMessage>,
        userProfile: UserProfile,
        nutritionEngine: NutritionEngine
    ): String = withContext(Dispatchers.IO) {
        val (intake, gaps, icmrRule) = fetchLiveContext(nutritionEngine, userProfile)
        val todayMeals = nutritionEngine.getHistoricalMeals().filter {
            it.date > System.currentTimeMillis() - 86_400_000L
        }

        val requiredTag = icmrRule?.required_tags?.split(",")?.firstOrNull() ?: ""
        val bannedTag = icmrRule?.banned_tags?.split(",")?.firstOrNull() ?: ""
        val remainingCal = maxOf(0f, (userProfile.dailyCalories ?: 2000f) - intake.calories)
        val minProtein = (userProfile.dailyProtein ?: 50f) / 3f
        val eligibleFoods = nutritionDao.getEligibleFoods(remainingCal, minProtein, requiredTag, bannedTag)

        val systemPrompt = promptBuilder.buildChatSystemPrompt(
            profile = userProfile, intake = intake, gaps = gaps,
            todayMeals = todayMeals, icmrRule = icmrRule,
            eligibleFoods = eligibleFoods, chatHistory = chatHistory
        )
        val userQuery = chatHistory.lastOrNull()?.text ?: "Hello"

        runCascade(systemPrompt, userQuery) {
            val safeAvoid = icmrRule?.must_avoid ?: "processed foods"
            val topFood = eligibleFoods.firstOrNull()?.food_name ?: "a balanced meal"
            "Based on your ${gaps.actionableGap?.nutrient ?: "calorie"} deficit, consider having $topFood. Avoid $safeAvoid."
        }
    }

    // ── 2. ON-DEVICE PRODUCT VERDICT ─────────────────────────────────────────

    /**
     * Personalized product analysis. Called from ProductSc "Ask AI" button.
     */
    suspend fun generateProductVerdict(
        product: FoodResponse,
        userProfile: UserProfile,
        nutritionEngine: NutritionEngine
    ): String = withContext(Dispatchers.IO) {
        val (intake, gaps, _) = fetchLiveContext(nutritionEngine, userProfile)
        val systemPrompt = promptBuilder.buildProductAnalysisPrompt(product, userProfile, intake, gaps)
        val productName = product.name ?: "this product"

        runCascade(systemPrompt, "Is $productName suitable for me?") {
            val allergenConflict = userProfile.allergies.any { allergy ->
                product.ingredients?.any { it.contains(allergy, ignoreCase = true) } == true ||
                product.ingredientsText?.contains(allergy, ignoreCase = true) == true
            }
            if (allergenConflict) {
                "Warning: This product may contain one of your allergens. Please check the ingredient list carefully before consuming."
            } else {
                "Check the label for your allergens. Given your ${userProfile.primaryGoal} goal, monitor your calorie intake with this product."
            }
        }
    }

    // ── 3. DAILY MEAL PLAN ────────────────────────────────────────────────────

    /**
     * Generates a full-day Indian meal plan. Called from DashboardSc.
     */
    suspend fun generateMealPlanSuggestion(
        userProfile: UserProfile,
        nutritionEngine: NutritionEngine
    ): String = withContext(Dispatchers.IO) {
        val (intake, gaps, icmrRule) = fetchLiveContext(nutritionEngine, userProfile)
        val todayMeals = nutritionEngine.getHistoricalMeals().filter {
            it.date > System.currentTimeMillis() - 86_400_000L
        }
        val requiredTag = icmrRule?.required_tags?.split(",")?.firstOrNull() ?: ""
        val bannedTag = icmrRule?.banned_tags?.split(",")?.firstOrNull() ?: ""
        val remainingCal = maxOf(0f, (userProfile.dailyCalories ?: 2000f) - intake.calories)
        val minProtein = (userProfile.dailyProtein ?: 50f) / 4f
        val eligibleFoods = nutritionDao.getEligibleFoods(remainingCal, minProtein, requiredTag, bannedTag)

        val systemPrompt = promptBuilder.buildMealPlanPrompt(
            userProfile, intake, gaps, todayMeals, icmrRule, eligibleFoods
        )

        runCascade(systemPrompt, "Generate a practical Indian meal plan for me for today.") {
            val topFood = eligibleFoods.firstOrNull()?.food_name ?: "Dal and rice"
            "Breakfast: Oats with milk. Lunch: $topFood with roti. Snack: Fruits. Dinner: Vegetables and dal. Stay hydrated and meet your ${gaps.actionableGap?.nutrient ?: "protein"} target today."
        }
    }

    // ── 4. FIX MY NUTRITION RATIONALE ────────────────────────────────────────

    /**
     * Explains why specific foods fix the user's biggest gap. Called from FixMyNutritionSc.
     */
    suspend fun generateFixMyNutritionRationale(
        gap: NutritionGap,
        candidates: List<MealCandidate>,
        userProfile: UserProfile
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = promptBuilder.buildGapRationalePrompt(gap, candidates, userProfile)

        runCascade(systemPrompt, "Why are these foods recommended for my ${gap.nutrient} gap?") {
            "These foods were selected because they are high in ${gap.nutrient} and safe for your ${userProfile.dietType} diet. They should help close your current deficit of ${gap.gap.toInt()}${gap.unit}."
        }
    }

    // ── 5. WEEKLY INSIGHT ─────────────────────────────────────────────────────

    /**
     * Analyses the past 7 days of logs. Called from DashboardSc weekly card.
     */
    suspend fun generateWeeklyInsight(
        userProfile: UserProfile,
        nutritionEngine: NutritionEngine
    ): String = withContext(Dispatchers.IO) {
        val allMeals = nutritionEngine.getHistoricalMeals()
        val systemPrompt = promptBuilder.buildWeeklyInsightPrompt(allMeals, userProfile)

        runCascade(systemPrompt, "Give me a weekly nutrition insight based on my logs.") {
            "You have logged ${allMeals.size} meals. Keep tracking consistently to get better insights. Focus on hitting your ${userProfile.primaryGoal} goal by maintaining balanced meals every day."
        }
    }

    companion object {
        private const val TAG = "NanoNutritionistCoach"
    }
}
