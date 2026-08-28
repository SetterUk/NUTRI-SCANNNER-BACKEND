package com.example.healthheatv2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductCacheEntity::class,
        UserProfile::class,
        LoggedMeal::class,
        ICMRRule::class,
        IFCTFood::class,
        FoodMaster::class,
        FoodAlias::class,
        NutrientDefinition::class,
        FoodNutrient::class,
        ServingSize::class,
        FoodPreparation::class,
        Recipe::class,
        RecipeIngredient::class,
        FoodRole::class,
        FoodSubstitution::class,
        FoodAllergen::class,
        MealTemplate::class,
        MealItem::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "healthheat_user_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val dao = getDatabase(context).nutritionDao()
                
                // Seed ICMR Medical Rules from JSON
                try {
                    val icmrStream = context.assets.open("icmr_rules.json")
                    val icmrString = icmrStream.bufferedReader().use { it.readText() }
                    val icmrArray = org.json.JSONObject(icmrString).getJSONArray("rules")
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                try {
                    val inputStream = context.assets.open("indian_foods.json")
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(jsonString)
                    val foodsArray = jsonObject.getJSONArray("foods")
                    val ifctFoods = mutableListOf<IFCTFood>()
                    
                    for (i in 0 until foodsArray.length()) {
                        val food = foodsArray.getJSONObject(i)
                        val id = food.getString("id")
                        val name = food.getString("name")
                        val serving = food.getInt("serving_size_g").toString() + "g"
                        val macros = food.getJSONObject("macros")
                        val energy = macros.getDouble("calories").toFloat()
                        val protein = macros.getDouble("protein").toFloat()
                        val carbs = macros.getDouble("carbs").toFloat()
                        val category = food.getString("category")
                        val isVeg = if (food.getBoolean("veg")) "vegetarian" else "non-vegetarian"
                        
                        val aliasesArray = food.optJSONArray("aliases")
                        val aliases = mutableListOf<String>()
                        if (aliasesArray != null) {
                            for (j in 0 until aliasesArray.length()) {
                                aliases.add(aliasesArray.getString(j))
                            }
                        }
                        val tags = listOf(category, isVeg) + aliases
                        
                        ifctFoods.add(IFCTFood(id, name, serving, energy, protein, carbs, tags.joinToString(",")))
                    }
                    dao.insertIFCTFoods(ifctFoods)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}