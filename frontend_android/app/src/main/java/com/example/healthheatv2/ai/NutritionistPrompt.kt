package com.example.healthheatv2.ai

import com.example.healthheatv2.data.IFCTFood
import com.example.healthheatv2.data.ICMRRule
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.services.GapAnalysis
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.NutritionIntake
import com.example.healthheatv2.ui.screens.ChatMessage

data class NutrientGap(
    val nutrient: String,
    val targetG: Float,
    val currentG: Float,
    val gapG: Float
)

data class CurrentContext(
    val scannedProduct: String? = null,
    val nutritionGaps: List<NutrientGap> = emptyList(),
    val todayIntake: NutritionIntake? = null
)

/**
 * Builds all AI system prompts for Gemma 4 E2B.
 * Each method is tailored to a specific use case and injects the full user context.
 *
 * Critical rules enforced in every prompt:
 * - NEVER violate allergies or diet type
 * - NEVER use markdown (Gemma 4 TTS-safe plain text output)
 * - NEVER diagnose or prescribe; escalate to doctor
 * - ALWAYS use the user's actual numeric data
 */
class NutritionistPrompt {

    // ── Shared user context block (injected into every prompt) ────────────────

    private fun buildUserContextJson(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal> = emptyList(),
        icmrRule: ICMRRule? = null,
        eligibleFoods: List<IFCTFood> = emptyList()
    ): String {
        val heightM = (profile.heightCm / 100f).coerceAtLeast(0.5f)
        val calculatedBmi = profile.bmi ?: String.format(java.util.Locale.US, "%.1f", profile.weightKg / (heightM * heightM)).toFloatOrNull() ?: 22.0f
        val calculatedBmr = profile.bmr ?: if (profile.sex.equals("F", true)) {
            (10f * profile.weightKg + 6.25f * profile.heightCm - 5f * profile.age - 161f)
        } else {
            (10f * profile.weightKg + 6.25f * profile.heightCm - 5f * profile.age + 5f)
        }
        val calculatedTdee = profile.tdee ?: (calculatedBmr * 1.375f)

        val topGaps = gaps?.topThreeGaps?.joinToString(", ") {
            "${it.nutrient}: need ${it.gap.toInt()}${it.unit} more"
        } ?: "Calorie gap: ${maxOf(0, (profile.dailyCalories?.toInt() ?: 2000) - (intake?.calories?.toInt() ?: 0))} kcal"

        val allergyStr = if (profile.allergies.isNotEmpty()) {
            profile.allergies.joinToString(", ").uppercase() + " (STRICT ZERO TOLERANCE)"
        } else "None"

        val conditionsStr = profile.healthTags.joinToString(", ").ifBlank { "General wellness" }
        val foodOptions = eligibleFoods.take(4).joinToString(", ") { "${it.food_name} (${it.protein_g}g P)" }
            .ifBlank { "Indian lentils, paneer, sprouts, millets" }

        return """
USER CLINICAL PROFILE:
- Age: ${profile.age} | Sex: ${if (profile.sex.equals("F", true)) "Female" else "Male"} | Height: ${profile.heightCm.toInt()}cm | Weight: ${profile.weightKg.toInt()}kg
- BMI: $calculatedBmi | BMR: ${calculatedBmr.toInt()} kcal/day | TDEE: ${calculatedTdee.toInt()} kcal/day
- Primary Goal: ${profile.primaryGoal.replace("_", " ")} | Activity: ${profile.activityLevel}
- Diet Type: ${profile.dietType.uppercase()} (Never suggest non-veg if veg/vegan)
- ALLERGIES: $allergyStr
- Health Conditions: $conditionsStr
- Medical Notes: ${profile.medicalReports.replace("\n", " ").take(150).ifBlank { "None" }}
- Daily Targets: ${profile.dailyCalories?.toInt() ?: 2000} kcal, ${profile.dailyProtein?.toInt() ?: 60}g protein
- Today's Intake: ${intake?.calories?.toInt() ?: 0} kcal, ${intake?.protein?.toInt() ?: 0}g protein
- Deficits / Gaps: $topGaps
- Recommended Foods from DB: $foodOptions
        """.trimIndent()
    }

    // ── OUTPUT RULES (appended to every prompt) ───────────────────────────────

    private val outputRules = """
DIRECT INSTRUCTIONS:
1. When asked about BMI, BMR, daily targets, calorie/protein needs, or diet plans, report the exact numbers from USER CLINICAL PROFILE above directly and explain what they mean for the user's goal.
2. Recommend authentic Indian foods that fit their diet type and strictly avoid their allergies.
3. Keep advice tailored to their specific Health Conditions.
4. Respond in PLAIN TEXT ONLY. No markdown, no bold, no asterisks, no hash symbols.
5. Keep your tone warm, encouraging, expert, and under 150 words.
    """.trimIndent()

    // ── 1. CHAT PROMPT ────────────────────────────────────────────────────────

    /**
     * For the main Nutribot conversational chat in NutritionistChatSc.
     */
    fun buildChatSystemPrompt(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal>,
        icmrRule: ICMRRule?,
        eligibleFoods: List<IFCTFood>,
        chatHistory: List<ChatMessage>
    ): String {
        val memoryContext = chatHistory.takeLast(4).joinToString("\n") {
            if (it.isUser) "User: ${it.text}" else "NutriBot: ${it.text}"
        }.ifBlank { "No prior messages." }

        val context = buildUserContextJson(profile, intake, gaps, todayMeals, icmrRule, eligibleFoods)

        return """
You are NutriBot, the user's personal nutrition tracking companion. You already have access to all their verified metrics below. Answer their questions directly using these numbers.

$context

RECENT CONVERSATION:
$memoryContext

$outputRules

FOOD LOGGING: If the user says they ate a food, append [LOG_FOOD: FoodName] at the end.
        """.trimIndent()
    }

    // ── 2. PRODUCT ANALYSIS PROMPT ────────────────────────────────────────────

    /**
     * For on-device product verdict in ProductSc.
     * Gives a personalized health opinion on a scanned product.
     */
    fun buildProductAnalysisPrompt(
        product: FoodResponse,
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?
    ): String {
        val context = buildUserContextJson(profile, intake, gaps)
        val ingredients = product.ingredientsText ?: product.ingredients?.joinToString(", ") ?: "Not available"
        val backendScore = product.healthScore ?: "Not scored"
        val backendVerdict = product.verdict ?: "Not analysed"
        val calories = product.nutrients?.get("energy-kcal") ?: product.nutrients?.get("calories") ?: "?"
        val protein = product.nutrients?.get("proteins") ?: product.nutrients?.get("protein") ?: "?"
        val carbs = product.nutrients?.get("carbohydrates") ?: product.nutrients?.get("carbs") ?: "?"
        val fat = product.nutrients?.get("fat") ?: "?"

        return """
You are an expert nutritionist. Analyse the scanned product below for THIS specific user.
Tell them in 3-5 sentences: (1) Is this product good for their goal? (2) Any allergen or diet conflict? (3) How does it fit into today's remaining intake? Be warm and direct.

USER DATA:
$context

SCANNED PRODUCT:
- Name: ${product.name ?: "Unknown"}
- Brand: ${product.brand ?: "Unknown"}
- Calories per 100g: $calories kcal
- Protein: ${protein}g | Carbs: ${carbs}g | Fat: ${fat}g
- Ingredients: $ingredients
- Backend Health Score: $backendScore/100
- Backend Verdict: $backendVerdict

$outputRules
        """.trimIndent()
    }

    // ── 3. DAILY MEAL PLAN PROMPT ─────────────────────────────────────────────

    /**
     * For the Dashboard "Get today's meal plan" AI card.
     * Generates a practical, personalized full-day Indian meal plan.
     */
    fun buildMealPlanPrompt(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal>,
        icmrRule: ICMRRule?,
        eligibleFoods: List<IFCTFood>
    ): String {
        val context = buildUserContextJson(profile, intake, gaps, todayMeals, icmrRule, eligibleFoods)
        val alreadyConsumedCal = intake?.calories?.toInt() ?: 0
        val targetCal = profile.dailyCalories?.toInt() ?: 2000
        val remainingCal = maxOf(0, targetCal - alreadyConsumedCal)

        return """
You are an expert Indian nutritionist. Create a practical meal plan for the rest of today for this user.
The user has already consumed $alreadyConsumedCal kcal today out of their $targetCal kcal target.
They need approximately $remainingCal more kcal. Focus on filling their top nutrient deficits using Indian foods they can actually find.

USER DATA:
$context

FORMAT (plain text, no markdown):
Breakfast: (if not eaten yet)
Lunch: (if not eaten yet)
Snack:
Dinner:
One tip for their specific goal.

$outputRules
        """.trimIndent()
    }

    // ── 4. GAP RATIONALE PROMPT ───────────────────────────────────────────────

    /**
     * For the FixMyNutritionSc "Why these foods?" explanation card.
     */
    fun buildGapRationalePrompt(
        gap: NutritionGap,
        candidates: List<com.example.healthheatv2.services.MealCandidate>,
        profile: UserProfile
    ): String {
        val foodList = candidates.joinToString(", ") { "${it.name} (${it.amountToConsume})" }

        return """
You are a nutritionist. Explain in 3-4 plain-text sentences why the recommended foods below will help fix this specific user's nutrient gap.
Mention the gap size, why each food was chosen, and how it fits the user's diet preferences. Be specific, warm, and brief.

NUTRIENT GAP:
- Nutrient: ${gap.nutrient}
- Deficit: ${gap.gap.toInt()} ${gap.unit}
- Current intake: ${gap.current.toInt()} / ${gap.target.toInt()} ${gap.unit}

USER PROFILE (brief):
- Diet: ${profile.dietType}
- Allergies: ${profile.allergies.joinToString(", ").ifBlank { "none" }}
- Goal: ${profile.primaryGoal}
- Health conditions: ${profile.healthTags.joinToString(", ").ifBlank { "none" }}

RECOMMENDED FOODS:
$foodList

$outputRules
        """.trimIndent()
    }

    // ── 5. WEEKLY INSIGHT PROMPT ──────────────────────────────────────────────

    /**
     * For the Dashboard weekly summary card.
     * Analyses last 7 days of logged meals for trends and gives actionable advice.
     */
    fun buildWeeklyInsightPrompt(
        historicalMeals: List<LoggedMeal>,
        profile: UserProfile
    ): String {
        val recentMeals = historicalMeals.takeLast(50)
        val avgCalories = if (recentMeals.isNotEmpty()) recentMeals.map { it.calories }.average().toInt() else 0
        val avgProtein = if (recentMeals.isNotEmpty()) recentMeals.map { it.protein }.average().toInt() else 0
        val uniqueFoods = recentMeals.map { it.foodName }.distinct().take(10).joinToString(", ")

        return """
You are an expert nutritionist reviewing this user's eating habits from the past 7 days.
Give a warm, honest 4-5 sentence summary: (1) What are they doing well? (2) What is the biggest area to improve? (3) One specific, actionable recommendation for next week.

USER PROFILE:
- Goal: ${profile.primaryGoal}
- Diet: ${profile.dietType}
- Daily calorie target: ${profile.dailyCalories?.toInt() ?: 2000} kcal
- Daily protein target: ${profile.dailyProtein?.toInt() ?: 50}g

LAST 7 DAYS DATA:
- Average daily calories logged: $avgCalories kcal
- Average daily protein: ${avgProtein}g
- Foods eaten: $uniqueFoods

$outputRules
        """.trimIndent()
    }
}
