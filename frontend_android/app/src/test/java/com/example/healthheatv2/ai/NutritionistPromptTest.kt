package com.example.healthheatv2.ai

import com.example.healthheatv2.data.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionistPromptTest {

    @Test
    fun testCoachConsidersUserProfile() {
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
        val currentContext = CurrentContext()
        val systemPrompt = promptBuilder.buildSystemPrompt(vegProfile, currentContext)

        assertTrue(systemPrompt.contains("vegan", ignoreCase = true))
        assertTrue(systemPrompt.contains("peanuts", ignoreCase = true))
    }

    @Test
    fun testCoachReferencesHealthGoals() {
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
            dailyProtein = 180f
        )

        val promptBuilder = NutritionistPrompt()
        val currentContext = CurrentContext()
        val systemPrompt = promptBuilder.buildSystemPrompt(muscleGainProfile, currentContext)

        assertTrue(systemPrompt.contains("muscle_gain"))
        assertTrue(systemPrompt.contains("180 g"))
    }

    @Test
    fun testCoachRefuseMedicalDiagnosisInPrompt() {
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
        val currentContext = CurrentContext()
        val systemPrompt = promptBuilder.buildSystemPrompt(profile, currentContext)

        assertTrue(systemPrompt.contains("NEVER diagnose conditions"))
        assertTrue(systemPrompt.contains("NEVER prescribe medications"))
        assertTrue(systemPrompt.contains("talk to your doctor"))
    }
}
