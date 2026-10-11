package com.example.healthheatv2.services

import com.example.healthheatv2.data.IFCTFood

/**
 * Ranks foods against a free-text query. Replaces `LIKE '%q%' LIMIT 1`, which
 * returned an arbitrary row (e.g. "rice" -> "Brown Rice", "dal" -> whichever dal sorted first).
 */
class FoodMatcher(foods: List<IFCTFood>) {

    private data class Entry(val food: IFCTFood, val names: List<String>, val words: Set<String>)

    private val entries: List<Entry> = foods.map { food ->
        val primary = normalise(food.food_name.substringBefore("(").trim())
        val slashParts = food.food_name.substringBefore("(").split("/").map { normalise(it) }
        val aliases = food.tags.split(",").map { normalise(it) }
            .filter { it.isNotBlank() && it !in CATEGORY_TAGS }
        val names = (listOf(normalise(food.food_name), primary) + slashParts + aliases).filter { it.isNotBlank() }.distinct()
        Entry(food, names, names.flatMap { it.split(" ") }.filter { it.length > 1 }.toSet())
    }

    data class Match(val food: IFCTFood, val score: Int)

    /**
     * @param preferVeg breaks ties toward vegetarian items, so "biryani" for a vegetarian
     *                  user resolves to Veg Biryani rather than Chicken Biryani.
     */
    fun best(query: String, preferVeg: Boolean = false): Match? {
        val q = normalise(query)
        if (q.isBlank()) return null
        val qWords = q.split(" ").filter { it.length > 1 && it !in STOP_WORDS }
        if (qWords.isEmpty()) return null
        val queryMentionsRaw = "raw" in qWords

        var best: Match? = null
        var bestRank = 0
        for (e in entries) {
            val score = score(q, qWords, e)
            if (score <= 0) continue
            // Ranking only: people log what they ate, so prefer cooked dishes over raw ingredients
            // ("poha" -> cooked poha), then the user's diet.
            var rank = score * 10
            if (!queryMentionsRaw && e.food.food_name.contains("raw", ignoreCase = true)) rank -= 30
            val isVeg = e.food.tags.split(",").any { it.trim().equals("vegetarian", ignoreCase = true) }
            if (preferVeg && isVeg) rank += 5
            if (rank > bestRank) {
                bestRank = rank
                best = Match(e.food, score)
            }
        }
        return best?.takeIf { it.score >= ACCEPT_THRESHOLD }
    }

    private fun score(q: String, qWords: List<String>, e: Entry): Int {
        if (e.names.any { it == q }) return 100
        if (e.names.any { it.startsWith("$q ") || it.endsWith(" $q") }) return 85
        val hits = qWords.count { w -> w in e.words }
        if (hits == qWords.size) {
            // All query words present; prefer entries with fewer extra words.
            val shortest = e.names.minOf { it.split(" ").size }
            return (75 - (shortest - qWords.size).coerceAtLeast(0) * 3).coerceAtLeast(61)
        }
        if (hits > 0 && qWords.size >= 2) {
            return 40 + hits * 10
        }
        return 0
    }

    companion object {
        const val ACCEPT_THRESHOLD = 60
        private val CATEGORY_TAGS = setOf(
            "vegetarian", "non-vegetarian", "legumes", "dairy", "protein", "grains", "vegetables",
            "fruits", "nuts & seeds", "nuts seeds", "oils", "condiments", "dishes", "breakfast", "snacks",
            "beverages", "sweets", "rice dishes", "breads", "curries"
        )
        private val STOP_WORDS = setOf("of", "and", "with", "the", "some", "a", "an")

        fun normalise(s: String): String {
            val base = s.lowercase()
                .replace(Regex("[()\\[\\],.]"), " ")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            // Crude singularisation: "eggs" -> "egg", "rotis" -> "roti", "idlis" -> "idli".
            return base.split(" ").joinToString(" ") { w ->
                when {
                    w.length > 3 && w.endsWith("ies") -> w.dropLast(3) + "y"
                    w.length > 3 && w.endsWith("es") && (w.endsWith("toes") || w.endsWith("oes")) -> w.dropLast(2)
                    w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
                    else -> w
                }
            }
        }
    }
}
