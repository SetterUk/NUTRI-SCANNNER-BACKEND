package com.example.healthheatv2.services

/**
 * Turns free text like "2 roti, 1 katori dal and 200g curd for lunch" into
 * structured items. Pure Kotlin so it can be unit tested without Android.
 */
object MealTextParser {

    data class ParsedItem(
        val name: String,       // cleaned food words, e.g. "roti"
        val count: Float,       // numeric multiplier, 1 if unspecified
        val unit: String?,      // canonical unit key or null
        val grams: Float?       // set only when the user gave an absolute weight/volume
    )

    data class ParsedMeal(val items: List<ParsedItem>, val mealSlot: String?)

    /** Typical Indian household measures, in grams (or ml for liquids ~ 1 g/ml). */
    val UNIT_GRAMS: Map<String, Float> = mapOf(
        "bowl" to 150f,
        "cup" to 200f,
        "glass" to 250f,
        "plate" to 300f,
        "tbsp" to 15f,
        "tsp" to 5f,
        "handful" to 30f,
        "slice" to 30f
    )

    private val UNIT_ALIASES: Map<String, String> = mapOf(
        "g" to "g", "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g", "gr" to "g",
        "kg" to "kg", "kgs" to "kg",
        "ml" to "ml", "l" to "l", "litre" to "l", "liter" to "l", "litres" to "l", "liters" to "l",
        "bowl" to "bowl", "bowls" to "bowl", "katori" to "bowl", "katoris" to "bowl",
        "cup" to "cup", "cups" to "cup",
        "glass" to "glass", "glasses" to "glass",
        "plate" to "plate", "plates" to "plate", "thali" to "plate",
        "piece" to "piece", "pieces" to "piece", "pc" to "piece", "pcs" to "piece", "nos" to "piece",
        "slice" to "slice", "slices" to "slice",
        "tbsp" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "tsp" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp", "spoon" to "tsp", "spoons" to "tsp",
        "handful" to "handful", "handfuls" to "handful", "mutthi" to "handful",
        "serving" to "serving", "servings" to "serving", "portion" to "serving", "portions" to "serving"
    )

    private val NUMBER_WORDS: Map<String, Float> = mapOf(
        "a" to 1f, "an" to 1f, "one" to 1f, "two" to 2f, "three" to 3f, "four" to 4f, "five" to 5f,
        "six" to 6f, "seven" to 7f, "eight" to 8f, "nine" to 9f, "ten" to 10f,
        "half" to 0.5f, "quarter" to 0.25f, "couple" to 2f, "few" to 3f,
        "ek" to 1f, "do" to 2f, "teen" to 3f, "char" to 4f, "aadha" to 0.5f
    )

    private val SLOT_WORDS: Map<String, String> = mapOf(
        "breakfast" to "breakfast", "nashta" to "breakfast",
        "lunch" to "lunch",
        "dinner" to "dinner", "supper" to "dinner",
        "snack" to "snack", "snacks" to "snack", "evening snack" to "snack"
    )

    private val LEAD_PHRASES = Regex(
        "^(please\\s+)?(log|add|track|record|i\\s+(just\\s+)?(had|ate|eaten|drank|have\\s+had|consumed)|ate|had|drank|maine)\\b",
        RegexOption.IGNORE_CASE
    )

    private val FILLER = setOf(
        "of", "some", "the", "my", "for", "in", "at", "today", "just", "about", "around", "approx", "roughly",
        "khaya", "khayi", "piya", "liya", "morning", "night", "evening", "afternoon", "also", "then", "plus"
    )

    fun parse(text: String): ParsedMeal {
        var t = text.lowercase().trim()
            .replace(Regex("[.!?]+$"), "")
            .replace(LEAD_PHRASES, "")
            .trim()

        // Pull out a meal slot mention ("for lunch") before splitting.
        var slot: String? = null
        for ((word, key) in SLOT_WORDS) {
            val re = Regex("\\b(for|at|in|during)?\\s*$word\\b")
            if (re.containsMatchIn(t)) {
                slot = key
                t = re.replace(t, " ")
            }
        }

        val chunks = t.split(Regex("\\s*(,|;|\\+|&|\\band\\b|\\bwith\\b|\\baur\\b|\\bn\\b)\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val items = chunks.mapNotNull { parseItem(it) }
        return ParsedMeal(items, slot)
    }

    private fun parseItem(chunk: String): ParsedItem? {
        // Normalise "200g" / "1.5kg" / "250ml" into "200 g".
        var s = chunk.replace(Regex("(\\d)([a-z]+)\\b")) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()

        var count: Float? = null
        var unit: String? = null
        val nameTokens = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            val num = parseNumber(tok)
            when {
                num != null && count == null -> count = num
                // "1 and a half" style already split; handle "1 1/2"
                num != null && count != null && tok.contains("/") -> count = count + num
                UNIT_ALIASES.containsKey(tok) && unit == null && (count != null || nameTokens.isEmpty()) -> unit = UNIT_ALIASES[tok]
                tok in FILLER -> Unit
                else -> nameTokens += tok
            }
            i++
        }

        val name = nameTokens.joinToString(" ").replace(Regex("[^a-z0-9 /-]"), "").trim()
        if (name.isBlank()) return null

        val c = count ?: 1f
        val grams = when (unit) {
            "g" -> c
            "kg" -> c * 1000f
            "ml" -> c
            "l" -> c * 1000f
            else -> null
        }
        val canonicalUnit = when (unit) {
            "g", "kg", "ml", "l" -> "g"
            else -> unit
        }
        return ParsedItem(
            name = name,
            count = if (grams != null) 1f else c.coerceIn(0.1f, 20f),
            unit = canonicalUnit,
            grams = grams?.coerceIn(1f, 3000f)
        )
    }

    private fun parseNumber(tok: String): Float? {
        NUMBER_WORDS[tok]?.let { return it }
        if (tok.contains("/")) {
            val parts = tok.split("/")
            val a = parts.getOrNull(0)?.toFloatOrNull()
            val b = parts.getOrNull(1)?.toFloatOrNull()
            if (a != null && b != null && b != 0f) return a / b
        }
        return tok.toFloatOrNull()
    }

    /**
     * Resolve how many grams were eaten, given the food's reference serving size.
     * "2 roti" -> 2 x 40 g, "1 bowl dal" -> 150 g, "200 g paneer" -> 200 g.
     */
    fun resolveGrams(item: ParsedItem, servingGrams: Float): Float {
        item.grams?.let { return it }
        val perUnit = when (item.unit) {
            null, "piece", "serving" -> servingGrams
            else -> UNIT_GRAMS[item.unit] ?: servingGrams
        }
        return item.count * perUnit
    }
}
