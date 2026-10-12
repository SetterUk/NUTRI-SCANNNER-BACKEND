package com.example.healthheatv2.services

/**
 * Traffic-light levels and readable nutrient rows for the product report.
 *
 * OpenFoodFacts only provides levels for sugar, fat, saturated fat and salt, and not always
 * those; the report used to show "Unknown" for fibre and protein even when the values were
 * right there. Levels are computed from the values with the UK FSA per-100 g thresholds when
 * OFF has none, and fibre/protein are rated as benefits (more is better).
 */
object NutrientLevels {

    enum class Tone { GOOD, OKAY, BAD, NEUTRAL, UNKNOWN }

    data class Level(val name: String, val label: String, val tone: Tone)

    data class Row(val label: String, val value: String)

    private data class Limit(val key: String, val offKey: String, val name: String, val low: Float, val high: Float)

    // FSA front-of-pack thresholds, grams per 100 g
    private val LIMITS = listOf(
        Limit("sugars_100g", "sugars", "Sugar", 5f, 22.5f),
        Limit("fat_100g", "fat", "Fat", 3f, 17.5f),
        Limit("saturated-fat_100g", "saturated-fat", "Saturated fat", 1.5f, 5f),
        Limit("salt_100g", "salt", "Salt", 0.3f, 1.5f),
    )

    fun number(nutrients: Map<String, Any>?, key: String): Float? = when (val v = nutrients?.get(key)) {
        is Number -> v.toFloat()
        is String -> v.trim().toFloatOrNull()
        else -> null
    }

    private fun salt(nutrients: Map<String, Any>?): Float? =
        number(nutrients, "salt_100g") ?: number(nutrients, "sodium_100g")?.let { it * 2.5f }

    fun levels(nutrients: Map<String, Any>?, offLevels: Map<String, String>?): List<Level> {
        val out = mutableListOf<Level>()
        for (l in LIMITS) {
            val value = if (l.key == "salt_100g") salt(nutrients) else number(nutrients, l.key)
            val level = offLevels?.get(l.offKey)?.lowercase()?.takeIf { it in setOf("low", "moderate", "high") }
                ?: value?.let { if (it <= l.low) "low" else if (it > l.high) "high" else "moderate" }
            out += when (level) {
                "low" -> Level(l.name, "Low", Tone.GOOD)
                "moderate" -> Level(l.name, "Moderate", Tone.OKAY)
                "high" -> Level(l.name, "High", Tone.BAD)
                else -> Level(l.name, "Not listed", Tone.UNKNOWN)
            }
        }
        out += number(nutrients, "fiber_100g")?.let {
            when {
                it >= 6f -> Level("Fibre", "High", Tone.GOOD)
                it >= 3f -> Level("Fibre", "Source", Tone.GOOD)
                else -> Level("Fibre", "Low", Tone.NEUTRAL)
            }
        } ?: Level("Fibre", "Not listed", Tone.UNKNOWN)
        out += number(nutrients, "proteins_100g")?.let {
            when {
                it >= 12f -> Level("Protein", "High", Tone.GOOD)
                it >= 6f -> Level("Protein", "Moderate", Tone.NEUTRAL)
                else -> Level("Protein", "Low", Tone.NEUTRAL)
            }
        } ?: Level("Protein", "Not listed", Tone.UNKNOWN)
        return out
    }

    private data class Field(val key: String, val label: String, val unit: String, val factor: Float = 1f)

    // OFF stores everything in grams per 100 g; minerals and vitamins are shown in mg/µg.
    private val FIELDS = listOf(
        Field("energy-kcal_100g", "Energy", "kcal"),
        Field("fat_100g", "Fat", "g"),
        Field("saturated-fat_100g", "  Saturated fat", "g"),
        Field("trans-fat_100g", "  Trans fat", "g"),
        Field("cholesterol_100g", "Cholesterol", "mg", 1000f),
        Field("carbohydrates_100g", "Carbohydrates", "g"),
        Field("sugars_100g", "  Sugars", "g"),
        Field("added-sugars_100g", "  Added sugars", "g"),
        Field("fiber_100g", "Fibre", "g"),
        Field("proteins_100g", "Protein", "g"),
        Field("salt_100g", "Salt", "g"),
        Field("sodium_100g", "Sodium", "mg", 1000f),
        Field("calcium_100g", "Calcium", "mg", 1000f),
        Field("iron_100g", "Iron", "mg", 1000f),
        Field("potassium_100g", "Potassium", "mg", 1000f),
        Field("magnesium_100g", "Magnesium", "mg", 1000f),
        Field("zinc_100g", "Zinc", "mg", 1000f),
        Field("vitamin-c_100g", "Vitamin C", "mg", 1000f),
        Field("vitamin-a_100g", "Vitamin A", "µg", 1_000_000f),
        Field("vitamin-d_100g", "Vitamin D", "µg", 1_000_000f),
        Field("vitamin-b12_100g", "Vitamin B12", "µg", 1_000_000f),
        Field("folates_100g", "Folate", "µg", 1_000_000f),
    )

    fun displayRows(nutrients: Map<String, Any>?): List<Row> {
        val rows = FIELDS.mapNotNull { f ->
            number(nutrients, f.key)?.let { Row(f.label, "${format(it * f.factor)} ${f.unit}") }
        }.toMutableList()
        // Some products only list energy in kJ
        if (rows.none { it.label == "Energy" }) {
            number(nutrients, "energy_100g")?.let { rows.add(0, Row("Energy", "${format(it / 4.184f)} kcal")) }
        }
        return rows
    }

    private fun format(v: Float): String = when {
        v >= 10f || v == Math.round(v).toFloat() -> Math.round(v).toString()
        else -> String.format(java.util.Locale.US, "%.1f", v)
    }
}
