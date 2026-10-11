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
        
        val userDiet = userProfile.dietType.lowercase().trim()
        val isVegan = userDiet == "vegan"
        val isVegetarian = isVegan || userDiet == "vegetarian" || userDiet == "jain" || userDiet == "eggitarian"
        val isJain = userDiet == "jain"
        val userAllergies = userProfile.allergies.map { it.lowercase().trim() }.filter { it.isNotBlank() }

        val nonVegKeywords = listOf("chicken", "mutton", "fish", "meat", "prawn", "crab", "pork", "beef", "poultry", "seafood", "salmon", "tuna")
        val dairyKeywords = listOf("milk", "paneer", "curd", "yogurt", "ghee", "butter", "cheese", "whey", "cream", "dahi")
        val eggKeywords = listOf("egg", "anda", "omelette")
        val rootVegKeywords = listOf("onion", "garlic", "potato", "carrot", "radish", "beetroot")

        for (food in allFoods) {
            val tags = food.tags.lowercase()
            val name = food.food_name.lowercase()

            // 3. Strict Diet Filtering
            if (isVegetarian) {
                if (tags.contains("non-vegetarian") || nonVegKeywords.any { name.contains(it) || tags.contains(it) }) continue
                if (userDiet != "eggitarian" && eggKeywords.any { name.contains(it) || tags.contains(it) }) continue
            }
            if (isVegan) {
                if (tags.contains("non-vegetarian") || tags.contains("dairy")) continue
                if (nonVegKeywords.any { name.contains(it) || tags.contains(it) }) continue
                if (dairyKeywords.any { name.contains(it) || tags.contains(it) }) continue
                if (eggKeywords.any { name.contains(it) || tags.contains(it) }) continue
                if (name.contains("honey") || tags.contains("honey")) continue
            }
            if (isJain) {
                if (rootVegKeywords.any { name.contains(it) || tags.contains(it) }) continue
            }

            // 4. Strict Allergen Filtering
            var hasAllergyConflict = false
            for (allergy in userAllergies) {
                if (name.contains(allergy) || tags.contains(allergy)) { hasAllergyConflict = true; break }
                if ((allergy == "peanut" || allergy == "peanuts" || allergy == "nuts") && (name.contains("peanut") || name.contains("groundnut"))) { hasAllergyConflict = true; break }
                if ((allergy == "milk" || allergy == "dairy" || allergy == "lactose") && (dairyKeywords.any { name.contains(it) || tags.contains(it) })) { hasAllergyConflict = true; break }
                if ((allergy == "egg" || allergy == "eggs") && (eggKeywords.any { name.contains(it) || tags.contains(it) })) { hasAllergyConflict = true; break }
                if ((allergy == "gluten" || allergy == "wheat") && (name.contains("wheat") || name.contains("atta") || name.contains("maida") || name.contains("bread") || name.contains("roti") || name.contains("gluten"))) { hasAllergyConflict = true; break }
                if ((allergy == "soy" || allergy == "soya") && (name.contains("soy") || name.contains("soya") || name.contains("tofu"))) { hasAllergyConflict = true; break }
                if ((allergy == "fish" || allergy == "seafood" || allergy == "shellfish") && (name.contains("fish") || name.contains("prawn") || name.contains("seafood"))) { hasAllergyConflict = true; break }
            }
            if (hasAllergyConflict) continue

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
