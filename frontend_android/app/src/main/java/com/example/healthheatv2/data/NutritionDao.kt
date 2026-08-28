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
}
