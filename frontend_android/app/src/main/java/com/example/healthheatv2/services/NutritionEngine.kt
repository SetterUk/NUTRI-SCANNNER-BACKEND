package com.example.healthheatv2.services
import com.example.healthheatv2.data.UserDatabase
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.UserProfile
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

data class NutritionTargets(
    val dailyCalories: Float,
    val dailyProtein: Float, // grams
    val dailyCarbs: Float,
    val dailyFat: Float,
    val dailyFiber: Float, // grams (target: 25-35g)
    val dailyWater: Float // liters
)

fun calculateBMI(heightCm: Float, weightKg: Float): Float {
    val heightM = heightCm / 100f
    return weightKg / (heightM * heightM)
}

fun getBMICategory(bmi: Float): String = when {
    bmi < 18.5 -> "Underweight"
    bmi < 25.0 -> "Normal weight"
    bmi < 30.0 -> "Overweight"
    else -> "Obese"
}

fun calculateBMR(
    ageYears: Int,
    sexM: Boolean, // true = male, false = female
    heightCm: Float,
    weightKg: Float
): Float {
    val bmr = if (sexM) {
        (10 * weightKg) + (6.25f * heightCm) - (5 * ageYears) + 5
    } else {
        (10 * weightKg) + (6.25f * heightCm) - (5 * ageYears) - 161
    }
    return bmr
}

/** Mifflin-St Jeor; for unspecified sex uses the midpoint of the male (+5) and female (-161) constants. */
fun calculateBMR(ageYears: Int, sex: ProfileNormalizer.Sex, heightCm: Float, weightKg: Float): Float {
    val base = (10 * weightKg) + (6.25f * heightCm) - (5 * ageYears)
    return base + when (sex) {
        ProfileNormalizer.Sex.MALE -> 5f
        ProfileNormalizer.Sex.FEMALE -> -161f
        ProfileNormalizer.Sex.UNSPECIFIED -> -78f
    }
}

fun calculateTDEE(bmr: Float, activityLevel: String): Float {
    val multiplier = when (activityLevel) {
        "sedentary" -> 1.2f // little or no exercise
        "light" -> 1.375f // light exercise 1-3 days/week
        "moderate" -> 1.55f // moderate exercise 3-5 days/week
        "active" -> 1.725f // intense exercise 6-7 days/week
        "very_active" -> 1.9f // very intense exercise + physical job
        else -> 1.55f
    }
    return bmr * multiplier
}

fun calculateTargets(
    tdee: Float,
    goal: String, // "weight_loss" | "weight_gain" | "muscle_gain"
    dietType: String, // "omnivore" | "vegetarian" | "vegan"
    weightKg: Float // Adding weight for protein calculation
): NutritionTargets {
    val calories = when (goal) {
        "weight_loss" -> tdee * 0.85f // 15% deficit
        "weight_gain" -> tdee * 1.15f // 15% surplus
        "muscle_gain" -> tdee * 1.1f // 10% surplus
        else -> tdee // maintenance
    }
    
    // Protein targets (high for muscle gain, moderate for maintenance)
    val proteinPerKg = when (goal) {
        "muscle_gain" -> 2.0f // 2g per kg body weight
        "weight_loss" -> 1.8f // preserve muscle during deficit
        else -> 1.6f // general health
    }
    val protein = weightKg * proteinPerKg 
    
    // Carbs and fats from remaining calories
    val proteinCals = protein * 4f
    val fatCals = calories * 0.25f // 25% from fat
    val carbCals = calories - proteinCals - fatCals
    
    return NutritionTargets(
        dailyCalories = calories,
        dailyProtein = protein,
        dailyCarbs = carbCals / 4f,
        dailyFat = fatCals / 9f,
        dailyFiber = 30f, // RDA for India: 25-35g, target 30g
        dailyWater = 2.5f // liters, can be adjusted by weight/activity
    )
}

data class NutritionGap(
    val nutrient: String,
    val unit: String,
    val target: Float,
    val current: Float,
    val gap: Float,
    val percentageOfTarget: Float
)

data class GapAnalysis(
    val gaps: List<NutritionGap>,
    val topThreeGaps: List<NutritionGap>,
    val status: Map<String, String>,
    val actionableGap: NutritionGap?
)

data class NutritionIntake(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val iron: Float,
    val calcium: Float,
    val zinc: Float,
    val b12: Float,
    val vitaminD: Float,
    val folate: Float
)

class NutritionEngine(
    val db: com.example.healthheatv2.data.UserDatabase,
    val userProfile: UserProfile
) {
    private fun getTodayStartEnd(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return Pair(startOfDay, endOfDay)
    }

    suspend fun getTodayIntake(): NutritionIntake {
        val today = getTodayStartEnd()
        val todayMeals = db.mealLogDao().getTodayMeals(today.first, today.second)
        return aggregateMealNutrition(todayMeals)
    }

    suspend fun getTodayMeals(): List<LoggedMeal> {
        val today = getTodayStartEnd()
        return db.mealLogDao().getTodayMeals(today.first, today.second)
    }

    /** Emits today's meals and re-emits whenever a meal is added or deleted. */
    fun observeTodayMeals(): Flow<List<LoggedMeal>> {
        val today = getTodayStartEnd()
        return db.mealLogDao().observeMealsBetween(today.first, today.second)
    }

    suspend fun deleteMeal(id: Int) = db.mealLogDao().deleteMeal(id)

    private fun currentMealSlot(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..10 -> "breakfast"
        in 11..15 -> "lunch"
        in 16..18 -> "snack"
        else -> "dinner"
    }

    /** Nutrition the AI estimated for a food it believes is not in our database. */
    data class AiEstimate(val grams: Float, val calories: Float, val protein: Float, val carbs: Float, val fat: Float)

    sealed class LogOutcome {
        abstract val label: String
        data class Matched(override val label: String, val grams: Float, val calories: Float) : LogOutcome()
        data class Estimated(override val label: String, val grams: Float, val calories: Float) : LogOutcome()
        data class NotFound(override val label: String) : LogOutcome()
    }

    private var matcher: FoodMatcher? = null

    private suspend fun foodMatcher(): FoodMatcher {
        matcher?.let { return it }
        val foods = db.nutritionDao().getAllIFCTFoods()
        // Don't cache an empty matcher: seeding may still be finishing on first launch.
        return FoodMatcher(foods).also { if (foods.isNotEmpty()) matcher = it }
    }

    /**
     * Logs every food in [text] ("2 roti, 1 bowl dal and a glass of buttermilk").
     * Database matches use real per-gram nutrition scaled by the parsed quantity.
     * An unmatched single item uses [aiEstimate] when the AI supplied one; otherwise it is
     * reported back as NotFound instead of being logged with made-up numbers.
     */
    suspend fun logFoodsFromText(
        text: String,
        aiEstimate: AiEstimate? = null,
        mealSlotOverride: String? = null
    ): List<LogOutcome> {
        val parsed = MealTextParser.parse(text)
        val slot = mealSlotOverride ?: parsed.mealSlot ?: currentMealSlot()
        val m = foodMatcher()
        val preferVeg = userProfile.dietType != "omnivore"
        val outcomes = mutableListOf<LogOutcome>()

        for (item in parsed.items) {
            val match = m.best(item.name, preferVeg)
            if (match != null) {
                val food = match.food
                val servingG = food.portion_size.filter { it.isDigit() || it == '.' }.toFloatOrNull()?.takeIf { it > 0f } ?: 100f
                val grams = MealTextParser.resolveGrams(item, servingG)
                val k = grams / servingG
                db.mealLogDao().insertMeal(
                    LoggedMeal(
                        date = System.currentTimeMillis(),
                        mealSlot = slot,
                        foodId = food.id,
                        foodName = food.food_name,
                        quantity = grams,
                        unit = "g",
                        calories = food.energy_kcal * k,
                        protein = food.protein_g * k,
                        carbs = food.carbs_g * k,
                        fat = food.fat_g * k,
                        fiber = food.fiber_g * k,
                        iron = food.iron_mg * k,
                        calcium = food.calcium_mg * k,
                        zinc = food.zinc_mg * k,
                        b12 = food.b12_mcg * k,
                        vitaminD = food.vitamin_d_iu * k,
                        folate = food.folate_mcg * k
                    )
                )
                outcomes += LogOutcome.Matched(food.food_name, grams, food.energy_kcal * k)
            } else if (aiEstimate != null && parsed.items.size == 1 && aiEstimate.calories > 0f) {
                val label = item.name.replaceFirstChar { it.uppercase() }
                db.mealLogDao().insertMeal(
                    LoggedMeal(
                        date = System.currentTimeMillis(),
                        mealSlot = slot,
                        foodId = "ai_estimate_${System.currentTimeMillis()}",
                        foodName = label,
                        quantity = aiEstimate.grams,
                        unit = "g",
                        calories = aiEstimate.calories,
                        protein = aiEstimate.protein,
                        carbs = aiEstimate.carbs,
                        fat = aiEstimate.fat,
                        // Micronutrients are unknown for AI estimates; leave at zero rather than inventing them.
                        fiber = 0f, iron = 0f, calcium = 0f, zinc = 0f, b12 = 0f, vitaminD = 0f, folate = 0f,
                        isEstimated = true
                    )
                )
                outcomes += LogOutcome.Estimated(label, aiEstimate.grams, aiEstimate.calories)
            } else {
                outcomes += LogOutcome.NotFound(item.name)
            }
        }
        return outcomes
    }

    /** Back-compat wrapper for callers that only need a success flag. */
    suspend fun searchAndLogFood(rawFoodName: String): Boolean =
        logFoodsFromText(rawFoodName).any { it !is LogOutcome.NotFound }

    suspend fun logScannedProduct(product: com.example.healthheatv2.network.FoodResponse, grams: Float = 100f, mealSlotOverride: String? = null): Boolean {
        val multiplier = (grams / 100f).coerceAtLeast(0.1f)

        // OpenFoodFacts: "<key>_100g" is normalised per 100 g; the bare key may be per serving.
        fun extractNutrient(vararg keys: String): Float? {
            val map = product.nutrients ?: return null
            for (k in keys) {
                val v = map[k] ?: continue
                when (v) {
                    is Number -> return v.toFloat()
                    is String -> {
                        val parsed = v.replace(Regex("[^0-9.]"), "").toFloatOrNull()
                        if (parsed != null) return parsed
                    }
                }
            }
            return null
        }

        // "energy_100g" is kilojoules in OpenFoodFacts, so convert when no kcal value exists.
        val cal100 = extractNutrient("energy-kcal_100g", "energy-kcal", "calories")
            ?: extractNutrient("energy_100g", "energy")?.let { it / 4.184f }
            ?: 0f
        val protein100 = extractNutrient("proteins_100g", "proteins", "protein") ?: 0f
        val carbs100 = extractNutrient("carbohydrates_100g", "carbohydrates", "carbs") ?: 0f
        val fat100 = extractNutrient("fat_100g", "fat") ?: 0f
        val fiber100 = extractNutrient("fiber_100g", "fiber", "dietary-fiber") ?: 0f
        // Micronutrients are stored in grams per 100 g. Missing means unknown -> 0, never a guess.
        val iron100 = (extractNutrient("iron_100g") ?: 0f) * 1000f
        val calcium100 = (extractNutrient("calcium_100g") ?: 0f) * 1000f
        val zinc100 = (extractNutrient("zinc_100g") ?: 0f) * 1000f
        val b12per100 = (extractNutrient("vitamin-b12_100g") ?: 0f) * 1_000_000f
        val vitD100 = (extractNutrient("vitamin-d_100g") ?: 0f) * 1_000_000f * 40f
        val folate100 = (extractNutrient("folates_100g", "vitamin-b9_100g") ?: 0f) * 1_000_000f

        val slot = mealSlotOverride ?: currentMealSlot()

        val meal = LoggedMeal(
            date = System.currentTimeMillis(),
            mealSlot = slot,
            foodId = product.barcode ?: "scanned_${System.currentTimeMillis()}",
            foodName = product.name ?: "Scanned Product",
            quantity = grams,
            unit = "g",
            calories = cal100 * multiplier,
            protein = protein100 * multiplier,
            carbs = carbs100 * multiplier,
            fat = fat100 * multiplier,
            fiber = fiber100 * multiplier,
            iron = iron100 * multiplier,
            calcium = calcium100 * multiplier,
            zinc = zinc100 * multiplier,
            b12 = b12per100 * multiplier,
            vitaminD = vitD100 * multiplier,
            folate = folate100 * multiplier
        )
        db.mealLogDao().insertMeal(meal)
        return true
    }

    suspend fun calculateGaps(): GapAnalysis {
        val intake = getTodayIntake()
        
        val gaps = listOf(
            createGap("Calories", "kcal", userProfile.dailyCalories ?: 2000f, intake.calories),
            createGap("Protein", "g", userProfile.dailyProtein ?: 50f, intake.protein),
            createGap("Carbs", "g", userProfile.dailyCarbs ?: 250f, intake.carbs),
            createGap("Fat", "g", userProfile.dailyFat ?: 65f, intake.fat),
            createGap("Fiber", "g", userProfile.dailyFiber ?: 30f, intake.fiber),
            createGap("Iron", "mg", userProfile.dailyIron ?: 19f, intake.iron),
            createGap("Calcium", "mg", userProfile.dailyCalcium ?: 1000f, intake.calcium),
            createGap("Zinc", "mg", userProfile.dailyZinc ?: 15f, intake.zinc),
            createGap("B12", "mcg", userProfile.dailyB12 ?: 2.5f, intake.b12),
            createGap("Vitamin D", "IU", userProfile.dailyVitaminD ?: 600f, intake.vitaminD)
        )
        
        val statusMap = gaps.associate { gap ->
            gap.nutrient to when {
                gap.percentageOfTarget >= 0.95 -> "On track"
                gap.percentageOfTarget >= 0.75 -> "Moderate"
                else -> "Low"
            }
        }
        
        val topThree = gaps.sortedByDescending { it.gap }.take(3)
        val actionable = gaps.maxByOrNull { it.gap }
        
        return GapAnalysis(
            gaps = gaps,
            topThreeGaps = topThree,
            status = statusMap,
            actionableGap = actionable
        )
    }

    suspend fun getHistoricalMeals(): List<LoggedMeal> {
        return db.mealLogDao().getAllMeals()
    }

    suspend fun getMealsSince(sinceMillis: Long): List<LoggedMeal> {
        return db.mealLogDao().getMealsSince(sinceMillis)
    }
    
    private fun createGap(nutrient: String, unit: String, target: Float, current: Float): NutritionGap {
        val gap = (target - current).coerceAtLeast(0f)
        val percentage = if (target > 0) current / target else 1f
        return NutritionGap(nutrient, unit, target, current, gap, percentage)
    }

    private fun aggregateMealNutrition(meals: List<LoggedMeal>): NutritionIntake {
        return NutritionIntake(
            calories = meals.sumOf { it.calories.toDouble() }.toFloat(),
            protein = meals.sumOf { it.protein.toDouble() }.toFloat(),
            carbs = meals.sumOf { it.carbs.toDouble() }.toFloat(),
            fat = meals.sumOf { it.fat.toDouble() }.toFloat(),
            fiber = meals.sumOf { it.fiber.toDouble() }.toFloat(),
            iron = meals.sumOf { it.iron.toDouble() }.toFloat(),
            calcium = meals.sumOf { it.calcium.toDouble() }.toFloat(),
            zinc = meals.sumOf { it.zinc.toDouble() }.toFloat(),
            b12 = meals.sumOf { it.b12.toDouble() }.toFloat(),
            vitaminD = meals.sumOf { it.vitaminD.toDouble() }.toFloat(),
            folate = meals.sumOf { it.folate.toDouble() }.toFloat()
        )
    }

    fun calculatePersonalScore(
        baseScore: Int,
        productIngredients: String?,
        productCategory: String?
    ): Int {
        var personalScore = baseScore

        // 1. Dietary Preference Check
        val diet = userProfile.dietType.lowercase()
        val isVegetarian = diet in setOf("vegetarian", "vegan", "jain", "eggitarian")
        if (isVegetarian && productIngredients?.contains("meat", ignoreCase = true) == true) {
            personalScore -= 50
        }
        
        if (diet == "vegan") {
            val nonVegan = listOf("milk", "dairy", "honey", "egg", "meat")
            if (nonVegan.any { productIngredients?.contains(it, ignoreCase = true) == true }) {
                personalScore -= 50
            }
        }

        // 2. Allergy Check
        userProfile.allergies.forEach { allergy ->
            if (productIngredients?.contains(allergy, ignoreCase = true) == true) {
                personalScore -= 60 // Huge penalty for allergen
            }
        }

        // 3. Goal Compatibility (Heuristic)
        if (userProfile.primaryGoal == "weight_loss" && productCategory?.contains("snack", ignoreCase = true) == true) {
            personalScore -= 10
        }

        return personalScore.coerceIn(0, 100)
    }

    suspend fun getChatHistory(): List<com.example.healthheatv2.data.SavedChatMessage> {
        return db.nutritionDao().getChatHistory()
    }

    suspend fun saveChatMessage(text: String, isUser: Boolean) {
        val msg = com.example.healthheatv2.data.SavedChatMessage(text = text, isUser = isUser)
        db.nutritionDao().insertChatMessage(msg)
    }

    suspend fun clearChatHistory() {
        db.nutritionDao().clearChatHistory()
    }

    /**
     * Persists an AI-generated daily meal plan keyed by date ("2026-08-31").
     * Screens call this after receiving the plan from NanoNutritionistCoach.
     */
    suspend fun saveDailyPlan(dateKey: String, planText: String, aiTier: String = "unknown") {
        val plan = com.example.healthheatv2.data.SavedDailyPlan(
            dateKey = dateKey,
            planText = planText,
            aiTier = aiTier
        )
        db.nutritionDao().upsertDailyPlan(plan)
    }

    /**
     * Returns the saved meal plan for today if it exists, otherwise null.
     */
    suspend fun getDailyPlan(dateKey: String): com.example.healthheatv2.data.SavedDailyPlan? {
        return db.nutritionDao().getPlanForDate(dateKey)
    }
}
