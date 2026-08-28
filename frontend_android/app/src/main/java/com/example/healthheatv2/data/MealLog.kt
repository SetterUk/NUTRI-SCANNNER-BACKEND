package com.example.healthheatv2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "meal_logs")
data class LoggedMeal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long, // timestamp
    val mealSlot: String, // "breakfast", "lunch", "snack", "dinner"
    val foodId: String, // references IndianFood or PackagedProduct
    val foodName: String,
    val quantity: Float, // grams or servings
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
    
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MealLogDao {
    @Insert
    suspend fun insertMeal(meal: LoggedMeal)

    @Query("SELECT * FROM meal_logs WHERE date BETWEEN :startOfDay AND :endOfDay ORDER BY mealSlot")
    suspend fun getTodayMeals(startOfDay: Long, endOfDay: Long): List<LoggedMeal>

    @Query("SELECT * FROM meal_logs ORDER BY date DESC")
    suspend fun getAllMeals(): List<LoggedMeal>
}
