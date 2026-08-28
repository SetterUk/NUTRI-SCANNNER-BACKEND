package com.example.healthheatv2.services

import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionEngineTest {

    @Test
    fun testBMICalculation() {
        val bmi = calculateBMI(170f, 70f)
        assertTrue("BMI should be around 24.2", bmi in 24.0..25.0)
    }

    @Test
    fun testBMRCalculation() {
        // 30yo male, 180cm, 80kg
        val bmr = calculateBMR(30, true, 180f, 80f)
        assertTrue("BMR should be around 1780", bmr in 1700f..1800f)
    }

    @Test
    fun testTDEECalculation() {
        val tdee = calculateTDEE(1750f, "moderate")
        assertTrue("TDEE should be around 2712.5", tdee in 2700f..2800f)
    }

    @Test
    fun testNutritionTargets() {
        val targets = calculateTargets(2500f, "muscle_gain", "omnivore", 80f)
        assertTrue("Protein should be high for muscle gain", targets.dailyProtein > 150f)
    }
}
