package com.example.healthheatv2.services

import com.example.healthheatv2.data.IFCTFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.google.gson.JsonParser
import java.io.File

class MealTextParserTest {

    @Test
    fun splitsMultipleItemsWithQuantities() {
        val meal = MealTextParser.parse("I had 2 roti, 1 bowl dal and a glass of buttermilk for lunch")
        assertEquals("lunch", meal.mealSlot)
        assertEquals(listOf("roti", "dal", "buttermilk"), meal.items.map { it.name })
        assertEquals(2f, meal.items[0].count)
        assertEquals("bowl", meal.items[1].unit)
        assertEquals("glass", meal.items[2].unit)
    }

    @Test
    fun gramsAreAbsoluteNotServings() {
        // Regression: "200g paneer" used to be read as 200 servings, clamped to 10.
        val item = MealTextParser.parse("200g paneer").items.single()
        assertEquals("paneer", item.name)
        assertEquals(200f, item.grams)
        assertEquals(200f, MealTextParser.resolveGrams(item, servingGrams = 100f))
    }

    @Test
    fun resolvesHouseholdUnitsAndCounts() {
        val roti = MealTextParser.parse("3 roti").items.single()
        assertEquals(120f, MealTextParser.resolveGrams(roti, servingGrams = 40f))
        val dal = MealTextParser.parse("1 katori dal").items.single()
        assertEquals(150f, MealTextParser.resolveGrams(dal, servingGrams = 200f))
        val half = MealTextParser.parse("half plate biryani").items.single()
        assertEquals(150f, MealTextParser.resolveGrams(half, servingGrams = 250f))
    }

    @Test
    fun understandsNumberWordsAndFractions() {
        assertEquals(2f, MealTextParser.parse("two eggs").items.single().count)
        assertEquals(1.5f, MealTextParser.parse("1 1/2 roti").items.single().count)
    }
}

class FoodMatcherTest {

    /** Loads the real bundled database so tests catch regressions in the data as well as the matcher. */
    private val foods: List<IFCTFood> by lazy {
        val file = listOf("src/main/assets/indian_foods.json", "app/src/main/assets/indian_foods.json")
            .map { File(it) }.first { it.exists() }
        // Gson rather than org.json: Android's org.json is a stub in plain JVM unit tests.
        val arr = JsonParser().parse(file.readText()).asJsonObject.getAsJsonArray("foods")
        arr.map { el: com.google.gson.JsonElement ->
            val f = el.asJsonObject
            val m = f.getAsJsonObject("macros")
            val aliases = f.getAsJsonArray("aliases").map { a: com.google.gson.JsonElement -> a.asString }
            val tags = listOf(f.get("category").asString, if (f.get("veg").asBoolean) "vegetarian" else "non-vegetarian") + aliases
            IFCTFood(
                id = f.get("id").asString, food_name = f.get("name").asString, portion_size = "${f.get("serving_size_g").asInt}g",
                energy_kcal = m.get("calories").asFloat, protein_g = m.get("protein").asFloat,
                carbs_g = m.get("carbs").asFloat, fat_g = 0f, fiber_g = 0f, iron_mg = 0f, calcium_mg = 0f,
                zinc_mg = 0f, b12_mcg = 0f, vitamin_d_iu = 0f, folate_mcg = 0f, tags = tags.joinToString(",")
            )
        }
    }

    private fun match(q: String, preferVeg: Boolean = false) = FoodMatcher(foods).best(q, preferVeg)?.food?.id

    @Test
    fun commonIndianDishesAreFound() {
        // Previously none of these existed, so they were logged as a fake 220 kcal "estimate".
        assertEquals("idli", match("idli"))
        assertEquals("idli", match("idlis"))
        assertEquals("dosa_masala", match("masala dosa"))
        assertEquals("sambar", match("sambhar"))
        assertEquals("masala_chai", match("chai"))
        assertEquals("chole", match("chana masala"))
        assertEquals("dal_tadka", match("dal"))
        assertEquals("rice_white", match("rice"))
        assertEquals("roti_wheat", match("roti"))
        assertEquals("egg_whole", match("eggs"))
    }

    @Test
    fun prefersCookedDishOverRawIngredient() {
        assertEquals("poha_cooked", match("poha"))
    }

    @Test
    fun prefersVegForVegetarians() {
        assertEquals("veg_biryani", match("biryani", preferVeg = true))
        assertNotNull(match("chicken biryani"))
        assertEquals("chicken_biryani", match("chicken biryani", preferVeg = true))
    }

    @Test
    fun unknownFoodIsNotForcedOntoARandomMatch() {
        assertNull(match("quinoa tabbouleh"))
    }
}

class ProfileNormalizerTest {

    @Test
    fun activityLabelsFromUiMapToCalculatorKeys() {
        // Regression: "Sedentary"/"Lightly Active" never matched, so everyone got the 1.55 "moderate" multiplier.
        assertEquals("sedentary", ProfileNormalizer.activity("Sedentary"))
        assertEquals("light", ProfileNormalizer.activity("Lightly Active"))
        assertEquals("moderate", ProfileNormalizer.activity("Moderately Active"))
        assertEquals("active", ProfileNormalizer.activity("Very Active"))
        assertEquals("very_active", ProfileNormalizer.activity("Extra Active"))
    }

    @Test
    fun freeTextGoalsAreUnderstood() {
        // Regression: free text like "lose weight" never matched "weight_loss", so everyone got maintenance calories.
        assertEquals("weight_loss", ProfileNormalizer.goal("I want to lose 5 kg"))
        assertEquals("muscle_gain", ProfileNormalizer.goal("build muscle, gym"))
        assertEquals("weight_gain", ProfileNormalizer.goal("gain weight"))
        assertEquals("muscle_gain", ProfileNormalizer.goal("", rawDiet = "Bulking"))
        assertEquals("general_health", ProfileNormalizer.goal(null))
    }

    @Test
    fun conditionsAreRecoveredFromOldFreeText() {
        val tags = ProfileNormalizer.healthTags(emptyList(), "Diabetic", "Weight loss, high BP, thyroid")
        assertEquals(setOf("diabetes", "hypertension", "thyroid"), tags.toSet())
    }

    @Test
    fun weightLossTargetsAreBelowMaintenanceForSedentaryWoman() {
        val bmr = calculateBMR(30, ProfileNormalizer.sex("Female"), 160f, 70f)
        val tdee = calculateTDEE(bmr, ProfileNormalizer.activity("Sedentary"))
        val targets = calculateTargets(tdee, ProfileNormalizer.goal("lose weight"), "vegetarian", 70f)
        assertEquals(1667f, tdee, 1f) // (700 + 1000 - 150 - 161) x 1.2
        assertEquals(tdee * 0.85f, targets.dailyCalories, 1f)
    }

    @Test
    fun ironTargetIsSexSpecific() {
        assertEquals(29f, ProfileNormalizer.microTargets("Female").iron)
        assertEquals(19f, ProfileNormalizer.microTargets("Male").iron)
    }
}
