package com.example.healthheatv2.services

/**
 * Single source of truth for turning what the user picked in Onboarding/Profile
 * ("Female", "Lightly Active", "lose 5 kg before my wedding", "Diabetic") into the
 * canonical values the calculators, recommenders and prompts expect.
 *
 * Before this existed, each screen compared against its own spelling, so most
 * user choices silently fell through to defaults.
 */
object ProfileNormalizer {

    enum class Sex { MALE, FEMALE, UNSPECIFIED }

    /** Health-condition chips shown in Onboarding/Profile. Keys match icmr_rules.json condition_tag. */
    val CONDITION_OPTIONS: List<Pair<String, String>> = listOf(
        "diabetes" to "Diabetes",
        "pcos" to "PCOS / PCOD",
        "hypertension" to "High BP",
        "thyroid" to "Thyroid",
        "high_cholesterol" to "High Cholesterol",
        "anemia" to "Anemia",
        "fatty_liver" to "Fatty Liver",
        "acid_reflux" to "Acidity / GERD",
        "gout" to "Gout / Uric Acid",
        "kidney_disease" to "Kidney Disease"
    )

    /** Goal chips shown in Onboarding/Profile. Keys are what calculateTargets expects. */
    val GOAL_OPTIONS: List<Pair<String, String>> = listOf(
        "weight_loss" to "Lose weight",
        "muscle_gain" to "Build muscle",
        "weight_gain" to "Gain weight",
        "general_health" to "Eat healthier"
    )

    fun sex(raw: String?): Sex = when (raw?.trim()?.lowercase()) {
        "m", "male", "man" -> Sex.MALE
        "f", "female", "woman" -> Sex.FEMALE
        else -> Sex.UNSPECIFIED
    }

    fun sexLabel(raw: String?): String = when (sex(raw)) {
        Sex.MALE -> "Male"
        Sex.FEMALE -> "Female"
        Sex.UNSPECIFIED -> "Not specified"
    }

    /** Maps UI labels ("Lightly Active") to the keys calculateTDEE understands. */
    fun activity(raw: String?): String {
        val s = raw?.trim()?.lowercase().orEmpty()
        return when {
            s.isBlank() || s == "prefer not to say" -> "light"
            s.startsWith("sedentary") -> "sedentary"
            s.startsWith("light") -> "light"
            s.startsWith("moderate") -> "moderate"
            s.startsWith("extra") || s == "very_active" -> "very_active"
            s.startsWith("very") || s == "active" -> "active"
            else -> "light"
        }
    }

    /**
     * Goal used to be a free-text box, so older profiles contain sentences.
     * Accept both canonical keys and natural language; the diet chip "Bulking"
     * is a goal hint too.
     */
    fun goal(rawGoal: String?, rawDiet: String? = null): String {
        val g = rawGoal?.trim()?.lowercase().orEmpty()
        if (g in setOf("weight_loss", "weight_gain", "muscle_gain", "general_health")) return g
        val looseWeight = listOf("lose", "loss", "losing", "fat", "slim", "cut", "reduce", "obes", "overweight", "kam")
        val muscle = listOf("muscle", "bulk", "gym", "strength", "lean mass", "body build")
        val gain = listOf("gain weight", "weight gain", "put on", "underweight", "increase weight", "gain")
        return when {
            muscle.any { g.contains(it) } -> "muscle_gain"
            looseWeight.any { g.contains(it) } -> "weight_loss"
            gain.any { g.contains(it) } -> "weight_gain"
            rawDiet?.trim()?.equals("bulking", ignoreCase = true) == true -> "muscle_gain"
            else -> "general_health"
        }
    }

    /** Diet type for food filtering. Keto/Diabetic/Gluten-Free are not food-group filters, so they map to omnivore + extras. */
    fun diet(raw: String?): String = when (raw?.trim()?.lowercase()) {
        "vegan" -> "vegan"
        "vegetarian", "veg" -> "vegetarian"
        "jain" -> "jain"
        "eggitarian", "eggetarian" -> "eggitarian"
        else -> "omnivore"
    }

    /**
     * Explicit health tags, plus conditions implied by the diet chip, plus conditions the user
     * typed into the old free-text "Health Goals & Conditions" box (e.g. "weight loss, diabetic, BP").
     */
    fun healthTags(rawTags: List<String>?, rawDiet: String?, rawGoalText: String? = null): List<String> {
        val tags = rawTags.orEmpty().map { it.trim().lowercase() }.filter { it.isNotBlank() }.toMutableSet()
        when (rawDiet?.trim()?.lowercase()) {
            "diabetic" -> tags += "diabetes"
            "gluten-free", "gluten free" -> tags += "gluten_free"
        }
        tags += conditionsFromText(rawGoalText)
        return tags.toList()
    }

    fun conditionsFromText(text: String?): Set<String> {
        val t = text?.lowercase() ?: return emptySet()
        val found = mutableSetOf<String>()
        if (Regex("diabet|sugar patient|blood sugar").containsMatchIn(t)) found += "diabetes"
        if (Regex("pcos|pcod").containsMatchIn(t)) found += "pcos"
        if (Regex("\\bbp\\b|blood pressure|hypertension").containsMatchIn(t)) found += "hypertension"
        if (Regex("thyroid").containsMatchIn(t)) found += "thyroid"
        if (Regex("cholesterol|lipid").containsMatchIn(t)) found += "high_cholesterol"
        if (Regex("anemi|anaemi|low haemoglobin|low hemoglobin|iron deficien").containsMatchIn(t)) found += "anemia"
        if (Regex("fatty liver").containsMatchIn(t)) found += "fatty_liver"
        if (Regex("acidity|acid reflux|gerd").containsMatchIn(t)) found += "acid_reflux"
        if (Regex("gout|uric acid").containsMatchIn(t)) found += "gout"
        if (Regex("kidney|ckd|renal").containsMatchIn(t)) found += "kidney_disease"
        return found
    }

    fun dietaryRestrictions(rawDiet: String?): List<String> = when (rawDiet?.trim()?.lowercase()) {
        "keto" -> listOf("keto")
        "gluten-free", "gluten free" -> listOf("gluten_free")
        else -> emptyList()
    }

    /**
     * ICMR-NIN 2020 RDA for sedentary adults. When sex is unspecified we use the midpoint
     * so the gap tracker neither over- nor under-states needs.
     */
    data class MicroTargets(
        val iron: Float, val calcium: Float, val zinc: Float,
        val b12: Float, val vitaminD: Float, val folate: Float
    )

    fun microTargets(rawSex: String?): MicroTargets = when (sex(rawSex)) {
        Sex.MALE -> MicroTargets(iron = 19f, calcium = 1000f, zinc = 17f, b12 = 2.5f, vitaminD = 600f, folate = 300f)
        Sex.FEMALE -> MicroTargets(iron = 29f, calcium = 1000f, zinc = 13.2f, b12 = 2.5f, vitaminD = 600f, folate = 220f)
        Sex.UNSPECIFIED -> MicroTargets(iron = 24f, calcium = 1000f, zinc = 15f, b12 = 2.5f, vitaminD = 600f, folate = 260f)
    }
}
