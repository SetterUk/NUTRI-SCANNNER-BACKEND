package com.example.healthheatv2.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Imports the bundled food + ICMR rule assets into Room.
 *
 * Previously this ran only in RoomDatabase.Callback.onCreate (fresh installs), on
 * GlobalScope, racing the first query. That meant (a) the first meal logged after
 * install often found an empty table, and (b) existing installs never received
 * new foods. Now every caller awaits the seed, and a bump of "version" in
 * indian_foods.json re-imports on existing installs.
 */
object FoodSeeder {
    private const val TAG = "FoodSeeder"
    private const val PREFS = "food_seed"
    private const val KEY_VERSION = "seeded_version"
    private val mutex = Mutex()
    @Volatile private var seededThisProcess = false

    /** Call after wiping the database (e.g. account deletion) so the next ensureSeeded re-imports. */
    fun invalidate() {
        seededThisProcess = false
    }

    suspend fun ensureSeeded(context: Context, dao: NutritionDao) {
        if (seededThisProcess) return
        mutex.withLock {
            if (seededThisProcess) return
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val foodsJson = context.assets.open("indian_foods.json").bufferedReader().use { it.readText() }
                val root = JSONObject(foodsJson)
                val assetVersion = root.optInt("version", 1)
                val seeded = prefs.getInt(KEY_VERSION, 0)
                val tableEmpty = dao.countIFCTFoods() == 0
                if (seeded >= assetVersion && !tableEmpty) {
                    seededThisProcess = true
                    return@withContext
                }
                try {
                    seedIcmrRules(context, dao)
                    dao.insertIFCTFoods(parseFoods(root))
                    prefs.edit().putInt(KEY_VERSION, assetVersion).apply()
                    Log.i(TAG, "Seeded food database v$assetVersion")
                } catch (e: Exception) {
                    Log.e(TAG, "Food seeding failed", e)
                    return@withContext
                }
                seededThisProcess = true
            }
        }
    }

    private suspend fun seedIcmrRules(context: Context, dao: NutritionDao) {
        val icmrString = context.assets.open("icmr_rules.json").bufferedReader().use { it.readText() }
        val icmrArray = JSONObject(icmrString).getJSONArray("rules")
        for (i in 0 until icmrArray.length()) {
            val rule = icmrArray.getJSONObject(i)
            dao.insertICMRRule(
                ICMRRule(
                    id = rule.getString("id"),
                    condition_tag = rule.getString("condition_tag"),
                    clinical_focus = rule.getString("clinical_focus"),
                    must_avoid = rule.getString("must_avoid"),
                    recommended_swaps = rule.getString("recommended_swaps"),
                    required_tags = rule.getString("required_tags"),
                    banned_tags = rule.getString("banned_tags")
                )
            )
        }
    }

    private fun parseFoods(root: JSONObject): List<IFCTFood> {
        val foodsArray = root.getJSONArray("foods")
        val result = ArrayList<IFCTFood>(foodsArray.length())
        for (i in 0 until foodsArray.length()) {
            val food = foodsArray.getJSONObject(i)
            val macros = food.getJSONObject("macros")
            val micros = food.optJSONObject("micros") ?: JSONObject()
            val aliases = food.optJSONArray("aliases")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
            val vegTag = if (food.getBoolean("veg")) "vegetarian" else "non-vegetarian"
            val tags = listOf(food.getString("category"), vegTag) + aliases
            result += IFCTFood(
                id = food.getString("id"),
                food_name = food.getString("name"),
                portion_size = food.getInt("serving_size_g").toString() + "g",
                energy_kcal = macros.getDouble("calories").toFloat(),
                protein_g = macros.getDouble("protein").toFloat(),
                carbs_g = macros.getDouble("carbs").toFloat(),
                fat_g = macros.optDouble("fat", 0.0).toFloat(),
                fiber_g = macros.optDouble("fiber", 0.0).toFloat(),
                iron_mg = micros.optDouble("iron_mg", 0.0).toFloat(),
                calcium_mg = micros.optDouble("calcium_mg", 0.0).toFloat(),
                zinc_mg = micros.optDouble("zinc_mg", 0.0).toFloat(),
                b12_mcg = micros.optDouble("b12_mcg", 0.0).toFloat(),
                vitamin_d_iu = micros.optDouble("vitamin_d_iu", 0.0).toFloat(),
                folate_mcg = micros.optDouble("folate_mcg", 0.0).toFloat(),
                tags = tags.joinToString(",")
            )
        }
        return result
    }
}
