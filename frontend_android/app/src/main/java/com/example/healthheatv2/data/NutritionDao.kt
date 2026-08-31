package com.example.healthheatv2.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NutritionDao {
    @Query("SELECT * FROM FoodMaster WHERE canonicalName LIKE '%' || :query || '%'")
    suspend fun searchFood(query: String): List<FoodMaster>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodMaster)

    @Query("SELECT amountPer100g FROM FoodNutrient WHERE foodId = :foodId AND nutrientId = :nutrientId")
    suspend fun getNutrientAmount(foodId: String, nutrientId: String): Float?

    @Query("SELECT * FROM ServingSize WHERE foodId = :foodId AND unit = :unit")
    suspend fun getServingSize(foodId: String, unit: String): ServingSize?

    @Query("SELECT * FROM FoodPreparation WHERE foodId = :foodId AND state = :state")
    suspend fun getPreparation(foodId: String, state: String): FoodPreparation?

    @Query("SELECT * FROM Recipe WHERE name LIKE '%' || :query || '%'")
    suspend fun getRecipe(query: String): List<Recipe>

    @Query("SELECT * FROM RecipeIngredient WHERE recipeId = :recipeId")
    suspend fun getRecipeIngredients(recipeId: String): List<RecipeIngredient>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertICMRRule(rule: ICMRRule)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertIFCTFood(food: IFCTFood)

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertIFCTFoods(foods: List<IFCTFood>)

    @Query("SELECT * FROM IFCTFood WHERE food_name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' LIMIT 1")
    suspend fun searchFoodByName(query: String): IFCTFood?

    @Query("SELECT * FROM ICMRRule WHERE condition_tag = :condition LIMIT 1")
    suspend fun getICMRRule(condition: String): ICMRRule?

    @Query("SELECT * FROM IFCTFood WHERE energy_kcal <= :deficit AND protein_g >= :minProtein AND tags LIKE '%' || :requiredTag || '%' AND (:bannedTag IS '' OR tags NOT LIKE '%' || :bannedTag || '%') LIMIT 3")
    suspend fun getEligibleFoods(deficit: Float, minProtein: Float, requiredTag: String, bannedTag: String): List<IFCTFood>

    @Query("SELECT * FROM IFCTFood")
    suspend fun getAllIFCTFoods(): List<IFCTFood>

    @Query("SELECT * FROM IFCTFood ORDER BY RANDOM() LIMIT 500")
    suspend fun getSampleIFCTFoods(): List<IFCTFood>

    @Query("SELECT * FROM SavedChatMessage ORDER BY timestamp ASC")
    suspend fun getChatHistory(): List<SavedChatMessage>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: SavedChatMessage)

    @Query("DELETE FROM SavedChatMessage")
    suspend fun clearChatHistory()
}
