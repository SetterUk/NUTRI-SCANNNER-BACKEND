package com.example.healthheatv2.ai

import com.example.healthheatv2.data.UserProfile
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
            icmrRule = null,
            eligibleFoods = emptyList(),
            chatHistory = emptyList()
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
            icmrRule = null,
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
            icmrRule = null,
            eligibleFoods = emptyList(),
            chatHistory = emptyList()
        )

        assertTrue(systemPrompt.contains("NEVER diagnose conditions or prescribe medications"))
        assertTrue(systemPrompt.contains("consulting a doctor"))
    }
}
