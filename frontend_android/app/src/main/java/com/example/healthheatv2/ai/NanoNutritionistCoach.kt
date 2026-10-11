package com.example.healthheatv2.ai

import android.content.Context
import android.util.Log
import com.example.healthheatv2.data.FoodSeeder
import com.example.healthheatv2.data.ICMRRule
import com.example.healthheatv2.data.IFCTFood
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.NutritionDao
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.network.ApiChatMessage
import com.example.healthheatv2.network.ChatRequest
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.services.GapAnalysis
import com.example.healthheatv2.services.MealCandidate
import com.example.healthheatv2.services.NutritionEngine
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.NutritionIntake
import com.example.healthheatv2.ui.screens.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Central AI orchestrator for NutriScanner.
 *
 * Routes all AI tasks through a 3-tier cascade:
 *   1. ⚡ Gemma 4 E2B (on-device, opt-in)
 *   2. ☁️ Cloud Groq API (default)
 *   3. 🔋 Offline ICMR + SQLite rules (always available)
 *
 * Exposes 5 task-specific methods, each injecting the user's profile and
 * today's real nutrition data into a tailored prompt.
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

    private data class LiveContext(
        val intake: NutritionIntake,
        val gaps: GapAnalysis,
        val todayMeals: List<LoggedMeal>,
        val icmrRules: List<ICMRRule>
    )

    private suspend fun fetchLiveContext(
        nutritionEngine: NutritionEngine,
        userProfile: UserProfile
    ): LiveContext {
        FoodSeeder.ensureSeeded(context, nutritionDao)
        // Every condition the user has, not just the first one.
        val rules = userProfile.healthTags.distinct().mapNotNull { nutritionDao.getICMRRule(it) }
        return LiveContext(
            intake = nutritionEngine.getTodayIntake(),
            gaps = nutritionEngine.calculateGaps(),
            todayMeals = nutritionEngine.getTodayMeals(),
            icmrRules = rules
        )
    }

    private fun stripTierPrefix(text: String): String =
        text.replace(Regex("^(⚡ \\[Gemma 4]|☁️ \\[Cloud AI]|🔋 \\[Offline])\\s*"), "")

    /** A bare refusal ("I can't provide medical advice") with no actual guidance in it. */
    private fun isBareRefusal(text: String): Boolean {
        val lower = text.lowercase()
        val patterns = listOf(
            "not authorized to provide medical", "not authorized to give medical",
            "cannot provide medical advice", "can't provide medical advice",
            "i'm not able to provide medical", "i am not able to provide medical"
        )
        return text.length < 400 && patterns.any { lower.contains(it) }
    }

    // ── 3-Tier cascade ────────────────────────────────────────────────────────

    /**
     * @param messages conversation turns after the system prompt (oldest first, last = user).
     */
    private suspend fun runCascade(
        systemPrompt: String,
        messages: List<ApiChatMessage>,
        offlineFallback: () -> String
    ): String {
        val userQuery = messages.lastOrNull { it.role == "user" }?.content ?: "Hello"

        // Tier 1: Gemma 4 on-device (ONLY if user opted-in and model is loaded/ready)
        if (_useLocalModel.value && gemmaManager.isReady) {
            val recent = messages.dropLast(1).takeLast(4).joinToString("\n") {
                "${if (it.role == "user") "User" else "NutriBot"}: ${it.content.take(300)}"
            }
            val query = if (recent.isBlank()) userQuery else "Earlier:\n$recent\n\nUser: $userQuery"
            val result = gemmaManager.generateContent(systemPrompt, query)
            if (!result.isNullOrBlank()) {
                Log.d(TAG, "✅ Responded via Gemma 4 (on-device)")
                return "⚡ [Gemma 4]\n$result"
            }
            Log.w(TAG, "Gemma 4 returned empty. Falling back to cloud.")
        }

        // Tier 2: Cloud Groq API (Default)
        return try {
            val apiMessages = listOf(ApiChatMessage(role = "system", content = systemPrompt)) + messages
            var reply = RetrofitClient.apiService.chat(ChatRequest(messages = apiMessages)).response
            if (isBareRefusal(reply)) {
                // Retry once with an explicit scope reminder rather than swapping in canned text.
                Log.w(TAG, "Cloud AI refused; retrying once with scope clarification.")
                val nudge = ApiChatMessage(
                    role = "system",
                    content = "The user is asking for general food and nutrition guidance, which is within your role. " +
                        "Answer with practical food advice. If a doctor should be involved, add one short line saying so."
                )
                reply = RetrofitClient.apiService.chat(ChatRequest(messages = apiMessages + nudge)).response
            }
            Log.d(TAG, "✅ Responded via Cloud AI")
            "☁️ [Cloud AI]\n$reply"
        } catch (e: Exception) {
            Log.w(TAG, "Cloud AI failed. Using offline ICMR fallback.", e)
            // Tier 3: Offline ICMR fallback
            "🔋 [Offline]\n${offlineFallback()}"
        }
    }

    private suspend fun runCascade(systemPrompt: String, userQuery: String, offlineFallback: () -> String): String =
        runCascade(systemPrompt, listOf(ApiChatMessage(role = "user", content = userQuery)), offlineFallback)

    // ── Food suggestions for prompts ─────────────────────────────────────────

    /**
     * Foods that are safe for this user (diet, allergies, dislikes, condition limits), ranked by
     * how well they fill today's biggest gap. Previously this always returned the four highest-protein
     * items, so suggestions repeated regardless of what the user actually lacked.
     */
    private suspend fun getStrictEligibleFoods(
        userProfile: UserProfile,
        gaps: GapAnalysis,
        icmrRules: List<ICMRRule>,
        limit: Int = 6
    ): List<IFCTFood> {
        val allFoods = nutritionDao.getAllIFCTFoods()
        val diet = userProfile.dietType.lowercase().trim()
        val isVegan = diet == "vegan"
        val isVegetarian = isVegan || diet == "vegetarian" || diet == "jain" || diet == "eggitarian"
        val isJain = diet == "jain"
        val userAllergies = userProfile.allergies.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val disliked = userProfile.dislikedFoods.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val limitedByCondition = icmrRules.flatMap { it.must_avoid.lowercase().split(",") }
            .map { it.replace(Regex("\\(.*"), "").trim() }
            .filter { it.length > 3 }

        val nonVegKeywords = listOf("chicken", "mutton", "fish", "meat", "prawn", "crab", "pork", "beef", "poultry", "seafood", "salmon", "tuna")
        val dairyKeywords = listOf("milk", "paneer", "curd", "yogurt", "ghee", "butter", "cheese", "whey", "cream", "dahi", "lassi", "raita", "kheer", "buttermilk")
        val eggKeywords = listOf("egg", "anda", "omelette")
        val rootVegKeywords = listOf("onion", "garlic", "potato", "aloo", "carrot", "radish", "beetroot")
        // Never suggested as gap fillers, even if they are dense in some nutrient.
        val notAMeal = setOf("oils", "condiments", "sweets")
        val junk = listOf("soft drink", "sugar", "jaggery", "honey", "instant noodles", "biscuit", "samosa", "pakora", "jalebi")

        val filtered = allFoods.filter { food ->
            val tags = food.tags.lowercase()
            val name = food.food_name.lowercase()
            val tagList = tags.split(",").map { it.trim() }
            fun mentions(words: List<String>) = words.any { name.contains(it) || tags.contains(it) }

            if (tagList.firstOrNull() in notAMeal) return@filter false
            if (junk.any { name.contains(it) }) return@filter false

            // 1. Diet
            if (isVegetarian) {
                if ("non-vegetarian" in tagList && !(diet == "eggitarian" && mentions(eggKeywords))) return@filter false
                if (mentions(nonVegKeywords)) return@filter false
                if (diet != "eggitarian" && mentions(eggKeywords)) return@filter false
            }
            if (isVegan) {
                if (mentions(dairyKeywords) || mentions(eggKeywords)) return@filter false
                if (name.contains("honey") || tags.contains("honey")) return@filter false
            }
            if (isJain && mentions(rootVegKeywords)) return@filter false

            // 2. Allergies
            for (allergy in userAllergies) {
                if (name.contains(allergy) || tags.contains(allergy)) return@filter false
                if ((allergy == "peanut" || allergy == "peanuts" || allergy == "nuts") && (name.contains("peanut") || name.contains("groundnut"))) return@filter false
                if ((allergy == "milk" || allergy == "dairy" || allergy == "lactose") && mentions(dairyKeywords)) return@filter false
                if ((allergy == "egg" || allergy == "eggs") && mentions(eggKeywords)) return@filter false
                if ((allergy == "gluten" || allergy == "wheat") && listOf("wheat", "atta", "maida", "bread", "roti", "paratha", "naan", "puri", "dalia", "gluten").any { name.contains(it) }) return@filter false
                if ((allergy == "soy" || allergy == "soya") && listOf("soy", "soya", "tofu").any { name.contains(it) }) return@filter false
                if ((allergy == "fish" || allergy == "seafood" || allergy == "shellfish") && listOf("fish", "prawn", "seafood").any { name.contains(it) }) return@filter false
            }

            // 3. Dislikes and condition limits
            if (disliked.any { name.contains(it) }) return@filter false
            if (limitedByCondition.any { name.contains(it) }) return@filter false
            true
        }

        // Rank by density of the nutrient the user is most short on.
        val target = gaps.gaps.filter { it.nutrient != "Calories" && it.target > 0f }
            .maxByOrNull { it.gap / it.target }?.nutrient ?: "Protein"
        fun density(f: IFCTFood): Float {
            val kcal = f.energy_kcal.coerceAtLeast(20f)
            val amount = when (target) {
                "Protein" -> f.protein_g
                "Fiber" -> f.fiber_g
                "Iron" -> f.iron_mg
                "Calcium" -> f.calcium_mg
                "Zinc" -> f.zinc_mg
                "B12" -> f.b12_mcg
                "Vitamin D" -> f.vitamin_d_iu
                // Carb gaps are best filled with fibre-rich carbs; fat gaps are rarely the real problem.
                "Carbs" -> f.fiber_g
                else -> f.protein_g
            }
            return amount / kcal
        }

        // Take the strongest candidates, then rotate daily so suggestions don't repeat every message.
        val top = filtered.sortedByDescending { density(it) }.take(limit * 2)
        val daySeed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toLong()
        return top.shuffled(java.util.Random(daySeed)).take(limit)
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
        val live = fetchLiveContext(nutritionEngine, userProfile)
        val eligibleFoods = getStrictEligibleFoods(userProfile, live.gaps, live.icmrRules)

        val systemPrompt = promptBuilder.buildChatSystemPrompt(
            profile = userProfile, intake = live.intake, gaps = live.gaps,
            todayMeals = live.todayMeals, icmrRules = live.icmrRules,
            eligibleFoods = eligibleFoods
        )

        // Real multi-turn history (last 10 turns) instead of pasting it into the system prompt.
        val turns = chatHistory.filter { it.text.isNotBlank() }.takeLast(10).map {
            ApiChatMessage(role = if (it.isUser) "user" else "assistant", content = stripTierPrefix(it.text).take(1500))
        }

        runCascade(systemPrompt, turns) {
            val avoid = live.icmrRules.joinToString(", ") { it.must_avoid }.ifBlank { null }
            val gapName = live.gaps.actionableGap?.nutrient ?: "calorie"
            val foods = eligibleFoods.take(3).joinToString(", ") { it.food_name }.ifBlank { "a balanced home-cooked meal" }
            buildString {
                append("I'm offline right now, so here's a quick suggestion from your data: your biggest gap today is $gapName. ")
                append("Good options for you: $foods.")
                if (avoid != null) append(" For your conditions, limit $avoid.")
            }
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
        val live = fetchLiveContext(nutritionEngine, userProfile)
        val systemPrompt = promptBuilder.buildProductAnalysisPrompt(product, userProfile, live.intake, live.gaps, live.icmrRules)
        val productName = product.name ?: "this product"

        runCascade(systemPrompt, "Is $productName suitable for me?") {
            val allergenConflict = userProfile.allergies.any { allergy ->
                product.ingredients?.any { it.contains(allergy, ignoreCase = true) } == true ||
                product.ingredientsText?.contains(allergy, ignoreCase = true) == true
            }
            if (allergenConflict) {
                "Warning: This product may contain one of your allergens. Please check the ingredient list carefully before consuming."
            } else {
                "Check the label for your allergens. Given your ${userProfile.primaryGoal.replace("_", " ")} goal, watch the portion size with this product."
            }
        }
    }

    // ── 3. DAILY MEAL PLAN ────────────────────────────────────────────────────

    /**
     * Generates a plan for the rest of today. Called from DashboardSc.
     */
    suspend fun generateMealPlanSuggestion(
        userProfile: UserProfile,
        nutritionEngine: NutritionEngine
    ): String = withContext(Dispatchers.IO) {
        val live = fetchLiveContext(nutritionEngine, userProfile)
        val eligibleFoods = getStrictEligibleFoods(userProfile, live.gaps, live.icmrRules, limit = 8)

        val systemPrompt = promptBuilder.buildMealPlanPrompt(
            userProfile, live.intake, live.gaps, live.todayMeals, live.icmrRules, eligibleFoods
        )

        runCascade(systemPrompt, "Plan the rest of my meals for today.") {
            val topFood = eligibleFoods.firstOrNull()?.food_name ?: when (userProfile.dietType.lowercase().trim()) {
                "vegan" -> "Lentil stew with millets"
                "vegetarian", "jain" -> "Paneer and dal with roti"
                else -> "Boiled eggs and dal with rice"
            }
            val breakfast = when (userProfile.dietType.lowercase().trim()) {
                "vegan" -> "Breakfast: Oats with plant milk and chia seeds."
                "vegetarian", "jain" -> "Breakfast: Poha or besan chilla with curd."
                else -> "Breakfast: Boiled eggs with whole wheat toast."
            }
            "$breakfast Lunch: $topFood with salad. Snack: Roasted chana or a fruit. Dinner: Vegetables with dal and roti. Stay hydrated."
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
        val weekMeals = nutritionEngine.getMealsSince(System.currentTimeMillis() - 7L * 86_400_000L)
        val systemPrompt = promptBuilder.buildWeeklyInsightPrompt(weekMeals, userProfile)

        runCascade(systemPrompt, "Give me a weekly nutrition insight based on my logs.") {
            "You have logged ${weekMeals.size} items this week. Keep tracking consistently to get better insights, and aim for balanced meals that support your ${userProfile.primaryGoal.replace("_", " ")} goal."
        }
    }

    companion object {
        private const val TAG = "NanoNutritionistCoach"
    }
}
