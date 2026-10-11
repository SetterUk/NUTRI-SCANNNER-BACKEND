package com.example.healthheatv2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meal_logs", indices = [Index(value = ["date"])])
data class LoggedMeal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long, // timestamp
    val mealSlot: String, // "breakfast", "lunch", "snack", "dinner"
    val foodId: String, // references IndianFood or PackagedProduct
    val foodName: String,
    val quantity: Float, // grams eaten
    val unit: String, // "g", "cup", "piece"

    // Calculated nutrition
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val iron: Float,
    val calcium: Float,
    val zinc: Float,
    val b12: Float,
    val vitaminD: Float,
    val folate: Float,

    val createdAt: Long = System.currentTimeMillis(),

    // True when nutrition came from an AI estimate rather than the food database or a product label.
    val isEstimated: Boolean = false
)

@Dao
interface MealLogDao {
    @Insert
    suspend fun insertMeal(meal: LoggedMeal): Long

    @Query("SELECT * FROM meal_logs WHERE date BETWEEN :startOfDay AND :endOfDay ORDER BY date")
    suspend fun getTodayMeals(startOfDay: Long, endOfDay: Long): List<LoggedMeal>

    @Query("SELECT * FROM meal_logs WHERE date BETWEEN :startOfDay AND :endOfDay ORDER BY date")
    fun observeMealsBetween(startOfDay: Long, endOfDay: Long): Flow<List<LoggedMeal>>

    @Query("SELECT * FROM meal_logs WHERE date >= :since ORDER BY date DESC")
    suspend fun getMealsSince(since: Long): List<LoggedMeal>

    @Query("SELECT * FROM meal_logs ORDER BY date DESC")
    suspend fun getAllMeals(): List<LoggedMeal>

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteMeal(id: Int)

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAllMeals()
}
