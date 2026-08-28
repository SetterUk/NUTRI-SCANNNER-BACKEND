package com.example.healthheatv2.services

import com.example.healthheatv2.data.FoodDatabaseHelper
import com.example.healthheatv2.data.FoodMaster
import com.example.healthheatv2.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MealCandidate(
    val foodId: String,
    val name: String,
    val amountToConsume: String, // e.g., "1 katori"
    val gapReduced: Float,
    val remainingCaloriesConstraint: Boolean
)

class RecommendationEngine(
    private val db: com.example.healthheatv2.data.FoodDatabaseHelper,
    private val userProfile: UserProfile
) {
    /**
     * Determines what the user should eat next based on the biggest missing gap.
     * Maps the Nutrient gap to a Food Role, searches the DB, and filters via strict rules.
     */
    suspend fun generateRecommendations(
        biggestGap: NutritionGap,
        remainingCalories: Float,
        budgetPref: Float? = null // Budget limit in rupees, simplified for demo
    ): List<MealCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<MealCandidate>()

        // 1. Map Gap -> Role
        val roleTarget = when (biggestGap.nutrient.lowercase()) {
            "protein" -> "PROTEIN_SOURCE"
            "fiber" -> "FIBER_SOURCE"
            "calcium" -> "CALCIUM_SOURCE"
            else -> null
        }

        if (roleTarget == null) return@withContext emptyList()

        // 2. Fetch Base Candidates (Normally you'd write a direct Join query in DAO, doing manually for flexibility here)
        // Since we don't have a direct "getByRole" in NutritionDao yet, let's assume we do or fetch all and filter.
        // I will write a mock query for now since we just mapped it in Room.
        // We'll simulate fetching from DB by querying the FoodMaster directly and joining on Roles if we had that query.
        
        // As a fallback for the hackathon, we know the foods in our DB:
        // We will fetch all foods and filter manually.
        val allFoods = db.searchFood("") // Returns everything since query is empty

        for (food in allFoods) {
            // 3. Filter by Diet Preferences
            val isVegetarian = userProfile.dietType == "vegetarian" || userProfile.dietType == "vegan"
            if (isVegetarian && food.vegetarian == false) continue
            if (userProfile.dietType == "vegan" && food.vegan == false) continue

            // 4. Filter by Allergies (Assume DB contains allergen check)
            // Let's assume we do a quick strict check. E.g. Peanut allergy
            if (userProfile.allergies.any { it.equals("peanut", ignoreCase = true) }) {
                // Ideally query FoodAllergen table. We will skip foods that have peanut.
                if (food.canonicalName.contains("peanut", ignoreCase = true)) continue
            }

            if (userProfile.allergies.any { it.equals("dairy", ignoreCase = true) }) {
                if (food.category?.equals("Dairy", ignoreCase = true) == true) continue
            }

            // 5. Calculate Contribution
            // In a real app we'd query FoodNutrient table.
            // We use static mappings here for the demo architecture proof.
            val nutrientId = when (roleTarget) {
                "PROTEIN_SOURCE" -> "PROT"
                "FIBER_SOURCE" -> "FIBER"
                "CALCIUM_SOURCE" -> "CA"
                else -> ""
            }
            
            val nutrientAmountPer100g = db.getNutrientAmount(food.id, nutrientId) ?: 0f
            val caloriesPer100g = db.getNutrientAmount(food.id, "CALORIES") ?: 0f

            if (nutrientAmountPer100g > 0) {
                // Propose a standard 100g serving for math simplicity
                val proposedCalories = caloriesPer100g
                val fitsCalories = proposedCalories <= remainingCalories

                candidates.add(
                    MealCandidate(
                        foodId = food.id,
                        name = food.canonicalName,
                        amountToConsume = "100g", // In future, use ServingSize table
                        gapReduced = nutrientAmountPer100g,
                        remainingCaloriesConstraint = fitsCalories
                    )
                )
            }
        }

        // 6. Rank Candidates
        // Primary: Highest gap reduction. Secondary: Fits calorie budget.
        return@withContext candidates
            .filter { it.remainingCaloriesConstraint } // Strict calorie limit
            .sortedByDescending { it.gapReduced }
            .take(3)
    }
}
