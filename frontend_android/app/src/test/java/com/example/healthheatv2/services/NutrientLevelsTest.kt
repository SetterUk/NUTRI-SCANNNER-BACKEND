package com.example.healthheatv2.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientLevelsTest {
    // Farali Chiwda, from the OFF record shown in the old screenshots
    private val chiwda = mapOf<String, Any>(
        "energy-kcal_100g" to 587.0, "energy_100g" to 2456.0, "fat_100g" to 42.0, "fiber_100g" to 4.5,
        "proteins_100g" to 14.1, "salt_100g" to 0.55, "saturated-fat_100g" to 8.6,
        "sodium_100g" to 0.22, "sugars_100g" to 10.0, "nova-group_100g" to 3.0
    )

    @Test
    fun fibreAndProteinAreRatedFromValuesNotUnknown() {
        val byName = NutrientLevels.levels(chiwda, mapOf("fat" to "high", "salt" to "moderate")).associateBy { it.name }
        assertEquals("Source", byName["Fibre"]!!.label)
        assertEquals(NutrientLevels.Tone.GOOD, byName["Fibre"]!!.tone)
        assertEquals("High", byName["Protein"]!!.label)
        // OFF level wins when present; computed otherwise
        assertEquals("Moderate", byName["Salt"]!!.label)
        assertEquals("High", byName["Saturated fat"]!!.label)
        assertEquals("Moderate", byName["Sugar"]!!.label)
    }

    @Test
    fun missingValuesSayNotListed() {
        val levels = NutrientLevels.levels(emptyMap(), null)
        assertTrue(levels.all { it.tone == NutrientLevels.Tone.UNKNOWN && it.label == "Not listed" })
    }

    @Test
    fun rowsHaveUnitsAndSkipNonNutrients() {
        val rows = NutrientLevels.displayRows(chiwda).associate { it.label.trim() to it.value }
        assertEquals("587 kcal", rows["Energy"])
        assertEquals("220 mg", rows["Sodium"])
        assertEquals("0.6 g", rows["Salt"])
        assertTrue(rows.keys.none { it.contains("Nova", ignoreCase = true) })
    }

    @Test
    fun kilojoulesOnlyIsConverted() {
        val rows = NutrientLevels.displayRows(mapOf("energy_100g" to 418.4)).associate { it.label to it.value }
        assertEquals("100 kcal", rows["Energy"])
    }
}
