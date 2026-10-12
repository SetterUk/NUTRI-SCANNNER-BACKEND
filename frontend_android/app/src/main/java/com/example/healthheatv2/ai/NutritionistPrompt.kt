package com.example.healthheatv2.ai

import com.example.healthheatv2.data.IFCTFood
import com.example.healthheatv2.data.ICMRRule
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.UserProfile
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.services.GapAnalysis
import com.example.healthheatv2.services.NutritionGap
import com.example.healthheatv2.services.NutritionIntake
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
 * Builds all AI system prompts for Gemma 4 E2B and Cloud AI.
 * Each method is tailored to a specific use case and injects the user's real context.
 *
 * Rules enforced in every prompt:
 * - Never violate allergies or diet type
 * - Plain text only (output is also read aloud by TTS)
 * - Use the user's actual numbers; say "not provided" instead of inventing missing ones
 * - Give practical food guidance directly; defer only diagnosis/medication questions to a doctor
 */
class NutritionistPrompt {

    // ── Shared user context block (injected into every prompt) ────────────────

    private fun goalLabel(goal: String): String = when (goal) {
        "weight_loss" -> "lose weight"
        "weight_gain" -> "gain weight"
        "muscle_gain" -> "build muscle"
        else -> "eat healthier / maintain"
    }

    private fun buildUserContext(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal> = emptyList(),
        icmrRules: List<ICMRRule> = emptyList(),
        eligibleFoods: List<IFCTFood> = emptyList()
    ): String {
        val missing = profile.missingFields.toSet()
        fun orMissing(field: String, value: String) = if (field in missing) "not provided" else value

        val bmi = profile.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "unknown"

        val topGaps = gaps?.topThreeGaps?.filter { it.gap > 0f }?.joinToString(", ") {
            "${it.nutrient} ${it.gap.toInt()}${it.unit} short"
        }?.ifBlank { "none, targets met so far" }
            ?: "Calories ${maxOf(0, (profile.dailyCalories?.toInt() ?: 2000) - (intake?.calories?.toInt() ?: 0))} kcal remaining"

        val allergyStr = if (profile.allergies.isNotEmpty()) {
            profile.allergies.joinToString(", ").uppercase() + " (never suggest anything containing these)"
        } else "None"

        val dietRule = when (profile.dietType.lowercase().trim()) {
            "vegan" -> "VEGAN: no meat, fish, eggs, milk, paneer, curd, ghee, butter, cheese, whey or honey."
            "vegetarian" -> "VEGETARIAN: no meat, chicken, fish, seafood or eggs. Dairy is fine."
            "jain" -> "JAIN: no meat, fish or eggs, and no root vegetables (onion, garlic, potato, carrot, radish, beetroot)."
            "eggitarian" -> "EGGITARIAN: vegetarian plus eggs. No meat or fish."
            else -> "OMNIVORE: vegetarian and non-vegetarian foods are both fine."
        }
        val restrictions = profile.dietaryRestrictions.joinToString(", ").ifBlank { "none" }

        val conditionsStr = profile.healthTags.joinToString(", ").ifBlank { "none reported" }
        val conditionGuidance = icmrRules.joinToString("\n") {
            "- ${it.condition_tag}: focus on ${it.clinical_focus}; limit ${it.must_avoid}; good swaps: ${it.recommended_swaps}"
        }.ifBlank { "- none" }

        val mealsToday = if (todayMeals.isEmpty()) "nothing logged yet" else todayMeals.joinToString("; ") {
            "${it.mealSlot}: ${it.foodName} (${it.quantity.toInt()}g, ${it.calories.toInt()} kcal${if (it.isEstimated) ", estimated" else ""})"
        }

        val foodOptions = eligibleFoods.joinToString(", ") { it.food_name }.ifBlank { "use common Indian foods that fit the rules above" }

        val missingNote = if (missing.isNotEmpty()) {
            "\nNOTE: The user has not provided: ${missing.joinToString(", ")}. Targets use estimates for these. " +
                "If it matters for the answer, briefly suggest they add it in Profile."
        } else ""

        return """
USER PROFILE:
- Age: ${orMissing("age", profile.age.toString())} | Sex: ${orMissing("sex", profile.sex)} | Height: ${orMissing("height", "${profile.heightCm.toInt()} cm")} | Weight: ${orMissing("weight", "${profile.weightKg.toInt()} kg")}
- BMI: $bmi | Activity: ${orMissing("activity level", profile.activityLevel)}
- Goal: ${profile.primaryGoal} (${goalLabel(profile.primaryGoal)})
- Diet: $dietRule
- Other restrictions: $restrictions
- Allergies: $allergyStr
- Health conditions: $conditionsStr
- Medical notes from user: ${profile.medicalReports.replace("\n", " ").take(600).ifBlank { "none" }}

DAILY TARGETS: ${profile.dailyCalories?.toInt() ?: 2000} kcal, ${profile.dailyProtein?.toInt() ?: 60} g protein, ${profile.dailyCarbs?.toInt() ?: 250} g carbs, ${profile.dailyFat?.toInt() ?: 60} g fat, ${profile.dailyFiber?.toInt() ?: 30} g fiber
TODAY SO FAR: ${intake?.calories?.toInt() ?: 0} kcal, ${intake?.protein?.toInt() ?: 0} g protein, ${intake?.carbs?.toInt() ?: 0} g carbs, ${intake?.fat?.toInt() ?: 0} g fat
MEALS LOGGED TODAY: $mealsToday
BIGGEST GAPS: $topGaps

ICMR-NIN GUIDANCE FOR THEIR CONDITIONS:
$conditionGuidance

FOODS FROM OUR DATABASE THAT FIT THIS USER: $foodOptions$missingNote
        """.trimIndent()
    }

    // ── OUTPUT RULES (appended to every prompt) ───────────────────────────────

    private val outputRules = """
RULES:
1. You are a nutrition coach. Give concrete, practical food guidance (meal plans, swaps, portions, calorie and macro maths) directly, using the profile above and everyday Indian foods.
2. Follow ICMR-NIN dietary guidelines. Respect the diet and allergies above without exception.
3. For diagnosis, medication, supplements in therapeutic doses, pregnancy, or kidney disease, still give the food guidance, then add one short line suggesting they confirm with their doctor. Do not refuse.
4. Use the user's real numbers. Never invent numbers for fields marked "not provided".
5. Respond in PLAIN TEXT ONLY. No markdown, bold, asterisks, hash symbols or tables. Short paragraphs or simple "Breakfast: ..." lines are fine.
6. Be warm, specific and brief. Prefer 80-180 words unless the user asks for a full plan.
    """.trimIndent()

    // ── 1. CHAT PROMPT ────────────────────────────────────────────────────────

    /**
     * For the main Nutribot conversational chat in NutritionistChatSc.
     * Conversation history is sent as separate chat messages, not embedded here.
     */
    fun buildChatSystemPrompt(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal>,
        icmrRules: List<ICMRRule>,
        eligibleFoods: List<IFCTFood>
    ): String {
        val context = buildUserContext(profile, intake, gaps, todayMeals, icmrRules, eligibleFoods)

        return """
You are NutriBot, a friendly Indian nutrition coach inside the HealthHeat app. Today is ${today()}.

$context

$outputRules

FOOD LOGGING:
When the user says they ate or drank something, confirm it warmly in one line and add one tag per food item at the very end of your reply, in exactly this format:
[LOG_FOOD: <quantity and food as the user said it> | <estimated grams> | <kcal> | <protein g> | <carbs g> | <fat g>]
Example: [LOG_FOOD: 2 roti | 80 | 240 | 7 | 40 | 3]
Only add tags for food the user says they already ate, never for suggestions. The app removes the tags before showing your reply.
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
        gaps: GapAnalysis?,
        icmrRules: List<ICMRRule> = emptyList()
    ): String {
        val context = buildUserContext(profile, intake, gaps, icmrRules = icmrRules)
        val ingredients = product.ingredientsText ?: product.ingredients?.joinToString(", ") ?: "Not available"
        val n = product.nutrients
        fun nutrient(vararg keys: String): String = keys.firstNotNullOfOrNull { n?.get(it) }?.toString() ?: "?"

        return """
You are NutriBot. Analyse the scanned product below for THIS specific user.
In 3-5 sentences: (1) Is it a good choice for their goal and conditions? (2) Any allergen or diet conflict? (3) How much fits into today's remaining intake? Be warm and direct.

$context

SCANNED PRODUCT:
- Name: ${product.name ?: "Unknown"}
- Brand: ${product.brand ?: "Unknown"}
- Per 100 g: ${nutrient("energy-kcal_100g", "energy-kcal")} kcal, protein ${nutrient("proteins_100g", "proteins")} g, carbs ${nutrient("carbohydrates_100g", "carbohydrates")} g, sugar ${nutrient("sugars_100g", "sugars")} g, fat ${nutrient("fat_100g", "fat")} g, sodium ${nutrient("sodium_100g", "sodium")} g
- Ingredients: ${ingredients.take(800)}
- Declared allergens: ${product.allergens?.ifBlank { null } ?: "none listed"}
- Health score: ${product.healthScore ?: "not scored"}/100

$outputRules
        """.trimIndent()
    }

    // ── 3. DAILY MEAL PLAN PROMPT ─────────────────────────────────────────────

    /**
     * For the Dashboard "Get today's meal plan" AI card.
     * Generates a practical, personalized plan for the rest of the day.
     */
    fun buildMealPlanPrompt(
        profile: UserProfile,
        intake: NutritionIntake?,
        gaps: GapAnalysis?,
        todayMeals: List<LoggedMeal>,
        icmrRules: List<ICMRRule>,
        eligibleFoods: List<IFCTFood>
    ): String {
        val context = buildUserContext(profile, intake, gaps, todayMeals, icmrRules, eligibleFoods)
        val alreadyConsumedCal = intake?.calories?.toInt() ?: 0
        val targetCal = profile.dailyCalories?.toInt() ?: 2000
        val remainingCal = maxOf(0, targetCal - alreadyConsumedCal)
        val eatenSlots = todayMeals.map { it.mealSlot }.toSet()
        val slotsToPlan = listOf("breakfast", "lunch", "snack", "dinner").filter { it !in eatenSlots }
            .ifEmpty { listOf("snack", "dinner") }

        return """
You are NutriBot, an Indian nutrition coach. Plan the rest of today for this user.
They have eaten $alreadyConsumedCal of $targetCal kcal, so about $remainingCal kcal remain. Close their biggest gaps with foods easily found in an Indian kitchen, with portions in household units (katori, roti, glass).

$context

FORMAT (plain text):
${slotsToPlan.joinToString("\n") { "${it.replaceFirstChar { c -> c.uppercase() }}: ..." }}
Tip: one tip for their goal.

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
     * Analyses the last 7 days of logged meals for trends and gives actionable advice.
     */
    fun buildWeeklyInsightPrompt(
        historicalMeals: List<LoggedMeal>,
        profile: UserProfile
    ): String {
        val weekAgo = System.currentTimeMillis() - 7L * 86_400_000L
        val recentMeals = historicalMeals.filter { it.date >= weekAgo }
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val byDay = recentMeals.groupBy { dayFmt.format(it.date) }
        val daysLogged = byDay.size
        // Averages are per logged day (previously these were per meal, which understated intake ~3x).
        val avgCalories = if (daysLogged > 0) byDay.values.map { d -> d.sumOf { it.calories.toDouble() } }.average().toInt() else 0
        val avgProtein = if (daysLogged > 0) byDay.values.map { d -> d.sumOf { it.protein.toDouble() } }.average().toInt() else 0
        val avgFiber = if (daysLogged > 0) byDay.values.map { d -> d.sumOf { it.fiber.toDouble() } }.average().toInt() else 0
        val topFoods = recentMeals.groupingBy { it.foodName }.eachCount().entries
            .sortedByDescending { it.value }.take(10).joinToString(", ") { "${it.key} x${it.value}" }

        return """
You are NutriBot reviewing this user's eating over the past 7 days.
Give a warm, honest 4-5 sentence summary: (1) what they are doing well, (2) the biggest thing to improve, (3) one specific, actionable change for next week.

USER PROFILE:
- Goal: ${profile.primaryGoal} (${goalLabel(profile.primaryGoal)})
- Diet: ${profile.dietType}
- Health conditions: ${profile.healthTags.joinToString(", ").ifBlank { "none" }}
- Daily targets: ${profile.dailyCalories?.toInt() ?: 2000} kcal, ${profile.dailyProtein?.toInt() ?: 50} g protein, ${profile.dailyFiber?.toInt() ?: 30} g fiber

LAST 7 DAYS:
- Days with any food logged: $daysLogged of 7
- Average per logged day: $avgCalories kcal, $avgProtein g protein, $avgFiber g fiber
- Most frequent foods: ${topFoods.ifBlank { "none logged" }}
${if (daysLogged < 3) "- Note: few days logged, so treat trends as tentative and encourage consistent logging." else ""}

$outputRules
        """.trimIndent()
    }

    private fun today(): String =
        SimpleDateFormat("EEEE, d MMMM", Locale.US).format(Calendar.getInstance().time)
}
