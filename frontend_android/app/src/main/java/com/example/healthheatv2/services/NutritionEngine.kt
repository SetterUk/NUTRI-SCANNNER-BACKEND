package com.example.healthheatv2.services
import com.example.healthheatv2.data.UserDatabase
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.UserProfile
import java.util.Calendar
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

    suspend fun calculateGaps(): GapAnalysis {
        val intake = getTodayIntake()
        
        val gaps = listOf(
            createGap("Calories", "kcal", userProfile.dailyCalories ?: 2000f, intake.calories),
            createGap("Protein", "g", userProfile.dailyProtein ?: 50f, intake.protein),
            createGap("Carbs", "g", userProfile.dailyCarbs ?: 250f, intake.carbs),
            createGap("Fat", "g", userProfile.dailyFat ?: 65f, intake.fat),
            createGap("Fiber", "g", userProfile.dailyFiber ?: 30f, intake.fiber),
            createGap("Iron", "mg", userProfile.dailyIron ?: 18f, intake.iron),
            createGap("Calcium", "mg", userProfile.dailyCalcium ?: 1000f, intake.calcium),
            createGap("Zinc", "mg", userProfile.dailyZinc ?: 11f, intake.zinc),
            createGap("B12", "mcg", userProfile.dailyB12 ?: 2.4f, intake.b12),
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
        val isVegetarian = userProfile.dietType == "vegetarian" || userProfile.dietType == "vegan"
        if (isVegetarian && productIngredients?.contains("meat", ignoreCase = true) == true) {
            personalScore -= 50
        }
        
        if (userProfile.dietType == "vegan") {
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
}
