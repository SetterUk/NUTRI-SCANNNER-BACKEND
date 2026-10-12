package com.example.healthheatv2.ai

import com.example.healthheatv2.data.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionistPromptTest {

    @Test
    fun testChatPromptContainsUserAllergiesAndDiet() {
        val vegProfile = UserProfile(
            age = 28,
            sex = "M",
            heightCm = 175f,
            weightKg = 70f,
            activityLevel = "moderate",
            primaryGoal = "general_health",
            secondaryGoals = emptyList(),
            dietType = "vegan",
            allergies = listOf("peanuts"),
            dietaryRestrictions = emptyList(),
            dislikedFoods = emptyList(),
            preferredCuisines = emptyList(),
            healthTags = emptyList()
        )

        val promptBuilder = NutritionistPrompt()
        val systemPrompt = promptBuilder.buildChatSystemPrompt(
            profile = vegProfile,
            intake = null,
            gaps = null,
            todayMeals = emptyList(),
            icmrRules = emptyList(),
            eligibleFoods = emptyList()
        )

        assertTrue(systemPrompt.contains("vegan", ignoreCase = true))
        assertTrue(systemPrompt.contains("peanuts", ignoreCase = true))
        assertTrue(systemPrompt.contains("PLAIN TEXT ONLY"))
    }

    @Test
    fun testMealPlanPromptContainsTargets() {
        val muscleGainProfile = UserProfile(
            age = 30,
            sex = "M",
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = "active",
            primaryGoal = "muscle_gain",
            secondaryGoals = emptyList(),
            dietType = "omnivore",
            allergies = emptyList(),
            dietaryRestrictions = emptyList(),
            dislikedFoods = emptyList(),
            preferredCuisines = emptyList(),
            healthTags = emptyList(),
            dailyProtein = 180f,
            dailyCalories = 2500f
        )

        val promptBuilder = NutritionistPrompt()
        val systemPrompt = promptBuilder.buildMealPlanPrompt(
            profile = muscleGainProfile,
            intake = null,
            gaps = null,
            todayMeals = emptyList(),
            icmrRules = emptyList(),
            eligibleFoods = emptyList()
        )

        assertTrue(systemPrompt.contains("muscle_gain"))
        assertTrue(systemPrompt.contains("180"))
        assertTrue(systemPrompt.contains("2500"))
    }

    @Test
    fun testPromptEnforcesStrictMedicalSafetyRules() {
        val profile = UserProfile(
            age = 40,
            sex = "F",
            heightCm = 160f,
            weightKg = 60f,
            activityLevel = "sedentary",
            primaryGoal = "weight_loss",
            secondaryGoals = emptyList(),
            dietType = "omnivore",
            allergies = emptyList(),
            dietaryRestrictions = emptyList(),
            dislikedFoods = emptyList(),
            preferredCuisines = emptyList(),
            healthTags = emptyList()
        )

        val promptBuilder = NutritionistPrompt()
        val systemPrompt = promptBuilder.buildChatSystemPrompt(
            profile = profile,
            intake = null,
            gaps = null,
            todayMeals = emptyList(),
            icmrRules = emptyList(),
            eligibleFoods = emptyList()
        )

        assertTrue(systemPrompt.contains("ICMR", ignoreCase = true))
        assertTrue(systemPrompt.contains("PLAIN TEXT ONLY"))
    }

    private fun profile(sex: String, missing: List<String> = emptyList()) = UserProfile(
        age = 32, sex = sex, heightCm = 160f, weightKg = 62f, activityLevel = "light",
        primaryGoal = "weight_loss", secondaryGoals = emptyList(), dietType = "vegetarian",
        allergies = emptyList(), dietaryRestrictions = emptyList(), dislikedFoods = emptyList(),
        preferredCuisines = emptyList(), healthTags = listOf("diabetes"), missingFields = missing
    )

    @Test
    fun femaleUserIsDescribedAsFemale() {
        // Regression: the prompt used to check sex == "F" while the app stores "Female", so every woman was labelled Male.
        val prompt = NutritionistPrompt().buildChatSystemPrompt(profile("Female"), null, null, emptyList(), emptyList(), emptyList())
        assertTrue(prompt.contains("Sex: Female"))
        assertFalse(prompt.contains("Sex: Male"))
    }

    @Test
    fun missingFieldsAreNotPresentedAsFacts() {
        val prompt = NutritionistPrompt().buildChatSystemPrompt(
            profile("Not specified", missing = listOf("age", "weight")), null, null, emptyList(), emptyList(), emptyList()
        )
        assertTrue(prompt.contains("Age: not provided"))
        assertTrue(prompt.contains("Weight: not provided"))
        assertFalse(prompt.contains("Age: 32"))
    }

    @Test
    fun todaysMealsAreIncluded() {
        val meal = com.example.healthheatv2.data.LoggedMeal(
            date = System.currentTimeMillis(), mealSlot = "breakfast", foodId = "idli", foodName = "Idli",
            quantity = 120f, unit = "g", calories = 174f, protein = 6f, carbs = 36f, fat = 0.6f, fiber = 1.8f,
            iron = 0.9f, calcium = 18f, zinc = 0.6f, b12 = 0f, vitaminD = 0f, folate = 18f
        )
        val prompt = NutritionistPrompt().buildChatSystemPrompt(profile("Female"), null, null, listOf(meal), emptyList(), emptyList())
        assertTrue(prompt.contains("breakfast: Idli (120g, 174 kcal)"))
    }

    @Test
    fun weeklyInsightAveragesPerDayNotPerMeal() {
        val now = System.currentTimeMillis()
        fun meal(kcal: Float) = com.example.healthheatv2.data.LoggedMeal(
            date = now, mealSlot = "lunch", foodId = "x", foodName = "X", quantity = 100f, unit = "g",
            calories = kcal, protein = 10f, carbs = 0f, fat = 0f, fiber = 0f, iron = 0f, calcium = 0f,
            zinc = 0f, b12 = 0f, vitaminD = 0f, folate = 0f
        )
        // Three meals on one day = 1800 kcal for that day (old code reported the 600 kcal per-meal average).
        val prompt = NutritionistPrompt().buildWeeklyInsightPrompt(listOf(meal(600f), meal(600f), meal(600f)), profile("Male"))
        assertTrue(prompt.contains("1800 kcal"))
    }
}
