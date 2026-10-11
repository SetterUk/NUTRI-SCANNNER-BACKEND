package com.example.healthheatv2.services

import com.example.healthheatv2.data.LoggedMeal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WeeklySummaryTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 20, 0, 0) }.timeInMillis
    private fun daysAgo(n: Int, hour: Int = 13) = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, -n); set(Calendar.HOUR_OF_DAY, hour)
    }.timeInMillis

    private fun meal(at: Long, kcal: Float, protein: Float, fibre: Float) = LoggedMeal(
        date = at, mealSlot = "lunch", foodId = "x", foodName = "x", quantity = 100f, unit = "g",
        calories = kcal, protein = protein, carbs = 0f, fat = 0f, fiber = fibre,
        iron = 0f, calcium = 0f, zinc = 0f, b12 = 0f, vitaminD = 0f, folate = 0f
    )

    @Test
    fun countsDaysAndTargets() {
        val meals = listOf(
            meal(daysAgo(0, 9), 900f, 30f, 10f), meal(daysAgo(0, 20), 1100f, 30f, 20f), // today: 2000 kcal, 60 g, 30 g
            meal(daysAgo(2), 1200f, 20f, 5f),
            meal(daysAgo(6), 2100f, 70f, 28f),
            meal(daysAgo(8), 5000f, 200f, 50f) // outside the week
        )
        val s = WeeklySummary.compute(meals, calorieTarget = 2000f, proteinTarget = 60f, fibreTarget = 30f, now = now)
        assertEquals(7, s.days.size)
        assertEquals(3, s.daysLogged)
        assertEquals(2, s.proteinDaysHit)       // today and 6 days ago
        assertEquals(2, s.fibreDaysHit)
        assertEquals(2, s.caloriesInRangeDays)  // 2000 and 2100; 1200 is too low
        assertEquals((2000f + 1200f + 2100f) / 3f, s.avgCalories, 0.01f)
        assertEquals(2000f, s.days.last().calories, 0.01f)
    }

    @Test
    fun emptyWeek() {
        val s = WeeklySummary.compute(emptyList(), 2000f, 60f, 30f, now)
        assertEquals(0, s.daysLogged)
        assertEquals(0f, s.avgCalories, 0f)
    }
}
