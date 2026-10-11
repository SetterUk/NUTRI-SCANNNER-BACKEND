package com.example.healthheatv2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["date"]), Index(value = ["clientId"], unique = true)]
)
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
    val isEstimated: Boolean = false,

    // Sync with the user's account (see TrackingSync): a stable id across devices, whether the
    // server has the latest version, and soft deletion so deletes reach other installs.
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val dirty: Boolean = true,
    val deleted: Boolean = false
)

@Dao
interface MealLogDao {
    @Insert
    suspend fun insertMeal(meal: LoggedMeal): Long

    @Query("SELECT * FROM meal_logs WHERE deleted = 0 AND date BETWEEN :startOfDay AND :endOfDay ORDER BY date")
    suspend fun getTodayMeals(startOfDay: Long, endOfDay: Long): List<LoggedMeal>

    @Query("SELECT * FROM meal_logs WHERE deleted = 0 AND date BETWEEN :startOfDay AND :endOfDay ORDER BY date")
    fun observeMealsBetween(startOfDay: Long, endOfDay: Long): Flow<List<LoggedMeal>>

    @Query("SELECT * FROM meal_logs WHERE deleted = 0 AND date >= :since ORDER BY date DESC")
    suspend fun getMealsSince(since: Long): List<LoggedMeal>

    @Query("SELECT * FROM meal_logs WHERE deleted = 0 ORDER BY date DESC")
    suspend fun getAllMeals(): List<LoggedMeal>

    /** Soft delete, so the deletion syncs to the account. */
    @Query("UPDATE meal_logs SET deleted = 1, dirty = 1 WHERE id = :id")
    suspend fun deleteMeal(id: Int)

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAllMeals()

    // ── Sync ──────────────────────────────────────
    @Query("SELECT * FROM meal_logs WHERE dirty = 1")
    suspend fun getDirtyMeals(): List<LoggedMeal>

    @Query("SELECT COUNT(*) FROM meal_logs WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT * FROM meal_logs WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): LoggedMeal?

    /** Marks a row synced only if it hasn't changed since it was sent. */
    @Query("UPDATE meal_logs SET dirty = 0 WHERE clientId = :clientId AND deleted = :deletedWhenSent")
    suspend fun markClean(clientId: String, deletedWhenSent: Boolean)

    @androidx.room.Update
    suspend fun updateMeal(meal: LoggedMeal)
}
