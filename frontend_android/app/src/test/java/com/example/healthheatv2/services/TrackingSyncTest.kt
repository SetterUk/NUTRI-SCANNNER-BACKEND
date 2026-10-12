package com.example.healthheatv2.services

import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.WeightLog
import com.example.healthheatv2.services.TrackingSync.toLocal
import com.example.healthheatv2.services.TrackingSync.toSync
import com.example.healthheatv2.ui.components.weightChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingSyncTest {
    @Test
    fun mealRoundTripKeepsEverythingAndArrivesClean() {
        val meal = LoggedMeal(
            id = 7, date = 1_760_000_000_000, mealSlot = "dinner", foodId = "ifct_dal", foodName = "Moong dal",
            quantity = 150f, unit = "g", calories = 160f, protein = 10f, carbs = 25f, fat = 1f, fiber = 6f,
            iron = 2f, calcium = 40f, zinc = 1f, b12 = 0f, vitaminD = 0f, folate = 80f,
            isEstimated = true, clientId = "abc", dirty = true, deleted = false
        )
        val back = meal.toSync().toLocal(id = 7)
        assertEquals(meal.copy(dirty = false, createdAt = back.createdAt), back)
    }

    @Test
    fun weightRoundTrip() {
        val w = WeightLog(id = 3, measuredAt = 5L, weightKg = 71.2f, clientId = "w", dirty = true, deleted = true)
        val back = w.toSync().toLocal(id = 3)
        assertEquals(w.copy(dirty = false), back)
        assertFalse(back.dirty)
    }

    @Test
    fun weightChangeOverRecentWeeks() {
        val day = 86_400_000L
        val now = 100 * day
        val entries = listOf(
            WeightLog(id = 1, measuredAt = now - 60 * day, weightKg = 80f, clientId = "a"), // too old
            WeightLog(id = 2, measuredAt = now - 28 * day, weightKg = 75f, clientId = "b"),
            WeightLog(id = 3, measuredAt = now, weightKg = 73.5f, clientId = "c"),
        )
        val (delta, days) = weightChange(entries, now)!!
        assertEquals(-1.5f, delta, 0.001f)
        assertEquals(28, days)
        assertNull(weightChange(entries.take(1), now))
    }
}
