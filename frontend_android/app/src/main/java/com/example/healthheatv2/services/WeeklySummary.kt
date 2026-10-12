package com.example.healthheatv2.services

import com.example.healthheatv2.data.LoggedMeal
import java.util.Calendar

/** Per-day totals for the last 7 days, oldest first, and how many days hit the targets. */
data class WeeklySummary(
    val days: List<DayTotal>,
    val daysLogged: Int,
    val proteinDaysHit: Int,
    val fibreDaysHit: Int,
    val caloriesInRangeDays: Int,
    val avgCalories: Float
) {
    data class DayTotal(val dayStart: Long, val calories: Float, val protein: Float, val fibre: Float, val logged: Boolean)

    companion object {
        /** A day "hits" protein/fibre at 90 % of target; calories are "in range" within ±15 %. */
        fun compute(
            meals: List<LoggedMeal>,
            calorieTarget: Float,
            proteinTarget: Float,
            fibreTarget: Float,
            now: Long = System.currentTimeMillis()
        ): WeeklySummary {
            val todayStart = startOfDay(now)
            val days = (6 downTo 0).map { back ->
                val start = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis
                val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val dayMeals = meals.filter { it.date in start until end }
                DayTotal(
                    dayStart = start,
                    calories = dayMeals.sumOf { it.calories.toDouble() }.toFloat(),
                    protein = dayMeals.sumOf { it.protein.toDouble() }.toFloat(),
                    fibre = dayMeals.sumOf { it.fiber.toDouble() }.toFloat(),
                    logged = dayMeals.isNotEmpty()
                )
            }
            val logged = days.filter { it.logged }
            return WeeklySummary(
                days = days,
                daysLogged = logged.size,
                proteinDaysHit = logged.count { proteinTarget > 0 && it.protein >= proteinTarget * 0.9f },
                fibreDaysHit = logged.count { fibreTarget > 0 && it.fibre >= fibreTarget * 0.9f },
                caloriesInRangeDays = logged.count {
                    calorieTarget > 0 && it.calories in (calorieTarget * 0.85f)..(calorieTarget * 1.15f)
                },
                avgCalories = if (logged.isEmpty()) 0f else logged.map { it.calories }.average().toFloat()
            )
        }

        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
