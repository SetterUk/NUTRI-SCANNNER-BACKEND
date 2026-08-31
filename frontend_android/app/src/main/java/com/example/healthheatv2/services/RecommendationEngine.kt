package com.example.healthheatv2.services

import com.example.healthheatv2.data.NutritionDao
import com.example.healthheatv2.data.IFCTFood
import com.example.healthheatv2.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MealCandidate(
    val foodId: String,
    val name: String,
    val amountToConsume: String,
    val gapReduced: Float,
    val remainingCaloriesConstraint: Boolean
)

class RecommendationEngine(
    private val dao: NutritionDao,
    private val userProfile: UserProfile
) {
    suspend fun generateRecommendations(
        biggestGap: NutritionGap,
        remainingCalories: Float,
        budgetPref: Float? = null
    ): List<MealCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<MealCandidate>()

        // 1. Map Gap
        val targetNutrient = biggestGap.nutrient.lowercase()

        // 2. Fetch Base Candidates from IFCTFood table
        val allFoods = dao.getAllIFCTFoods()
        
        val userDiet = userProfile.dietType.lowercase()
        val isVegetarian = userDiet == "vegetarian" || userDiet == "vegan"
        val isVegan = userDiet == "vegan"
        val hasPeanutAllergy = userProfile.allergies.any { it.equals("peanut", ignoreCase = true) }
        val hasDairyAllergy = userProfile.allergies.any { it.equals("dairy", ignoreCase = true) }

        for (food in allFoods) {
            // 3. Filter by Diet Preferences
            if (isVegetarian && !food.tags.contains("vegetarian", ignoreCase = true)) continue
            // if vegan, we ideally check for vegan tag, but IFCT only marks vegetarian. We skip Dairy category.
            if (isVegan && food.tags.contains("Dairy", ignoreCase = true)) continue

            // 4. Filter by Allergies
            if (hasPeanutAllergy && food.food_name.contains("peanut", ignoreCase = true)) continue
            if (hasDairyAllergy && food.tags.contains("Dairy", ignoreCase = true)) continue

            // 5. Calculate Contribution
            val nutrientAmountPer100g = when (targetNutrient) {
                "calories" -> food.energy_kcal
                "protein" -> food.protein_g
                "carbs" -> food.carbs_g
                "fat" -> food.fat_g
                "fiber" -> food.fiber_g
                "iron" -> food.iron_mg
                "calcium" -> food.calcium_mg
                "zinc" -> food.zinc_mg
                "b12" -> food.b12_mcg
                "vitamin d" -> food.vitamin_d_iu
                "folate" -> food.folate_mcg
                else -> 0f
            }
            
            val caloriesPer100g = food.energy_kcal

            if (nutrientAmountPer100g > 0) {
                // Determine serving size dynamically to max out remaining calories or gap
                val maxServingsByCalories = if (caloriesPer100g > 0) remainingCalories / caloriesPer100g else 1f
                val servingsToFillGap = biggestGap.gap / nutrientAmountPer100g
                
                // Pick a realistic serving (e.g. max 300g at once)
                val servings = minOf(maxServingsByCalories, servingsToFillGap, 3f).coerceAtLeast(0.5f)
                val proposedCalories = caloriesPer100g * servings
                val fitsCalories = proposedCalories <= remainingCalories

                candidates.add(
                    MealCandidate(
                        foodId = food.id,
                        name = food.food_name,
                        amountToConsume = "${(servings * 100).toInt()}g",
                        gapReduced = nutrientAmountPer100g * servings,
                        remainingCaloriesConstraint = fitsCalories
                    )
                )
            }
        }

        // 6. Rank Candidates
        return@withContext candidates
            .filter { it.remainingCaloriesConstraint }
            .sortedByDescending { it.gapReduced }
            .take(5)
    }
}
