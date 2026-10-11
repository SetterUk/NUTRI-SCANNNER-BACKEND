package com.example.healthheatv2.services

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.healthheatv2.data.LoggedMeal
import com.example.healthheatv2.data.UserDatabase
import com.example.healthheatv2.data.WeightLog
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.network.SyncMeal
import com.example.healthheatv2.network.SyncWeight
import com.example.healthheatv2.network.TrackingSyncRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps meal and weight logs in the user's account so they survive reinstalls and follow the
 * user to a new phone. Works offline: rows are marked dirty locally and uploaded on the next sync.
 * A row changed on this phone after it was sent stays dirty and wins; otherwise the server copy wins.
 */
object TrackingSync {
    private const val PREFS = "tracking_sync"
    private const val KEY_SINCE = "since_ms"
    // Re-read a little history each time so rows another device saved concurrently aren't missed.
    private const val OVERLAP_MS = 2 * 60 * 1000L

    private val mutex = Mutex()

    suspend fun sync(context: Context): Boolean = mutex.withLock {
        if (RetrofitClient.authToken == null) return false
        val db = UserDatabase.getDatabase(context)
        val meals = db.mealLogDao()
        val weights = db.weightLogDao()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_SINCE, -1L).takeIf { it >= 0 }?.let { (it - OVERLAP_MS).coerceAtLeast(0) }

        val dirtyMeals = meals.getDirtyMeals()
        val dirtyWeights = weights.getDirty()
        val response = try {
            RetrofitClient.apiService.syncTracking(
                TrackingSyncRequest(since, dirtyMeals.map { it.toSync() }, dirtyWeights.map { it.toSync() })
            )
        } catch (e: Exception) {
            Log.w("TrackingSync", "Sync failed; will retry later", e)
            return false
        }

        db.withTransaction {
            dirtyMeals.forEach { meals.markClean(it.clientId, it.deleted) }
            dirtyWeights.forEach { weights.markClean(it.clientId, it.deleted) }
            for (remote in response.meals.orEmpty()) {
                val local = meals.getByClientId(remote.clientId)
                when {
                    local == null -> if (!remote.deleted) meals.insertMeal(remote.toLocal())
                    !local.dirty -> meals.updateMeal(remote.toLocal(id = local.id))
                }
            }
            for (remote in response.weights.orEmpty()) {
                val local = weights.getByClientId(remote.clientId)
                when {
                    local == null -> if (!remote.deleted) weights.insert(remote.toLocal())
                    !local.dirty -> weights.update(remote.toLocal(id = local.id))
                }
            }
        }
        prefs.edit().putLong(KEY_SINCE, response.serverTimeMs).apply()
        true
    }

    /** Forget the sync position, e.g. after another account's data was wiped from this phone. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    internal fun LoggedMeal.toSync() = SyncMeal(
        clientId = clientId, loggedAtMs = date, mealSlot = mealSlot, foodId = foodId, foodName = foodName,
        quantity = quantity, unit = unit, calories = calories, protein = protein, carbs = carbs, fat = fat,
        fiber = fiber, iron = iron, calcium = calcium, zinc = zinc, b12 = b12, vitaminD = vitaminD,
        folate = folate, isEstimated = isEstimated, deleted = deleted
    )

    internal fun SyncMeal.toLocal(id: Int = 0) = LoggedMeal(
        id = id, date = loggedAtMs, mealSlot = mealSlot, foodId = foodId, foodName = foodName,
        quantity = quantity, unit = unit, calories = calories, protein = protein, carbs = carbs, fat = fat,
        fiber = fiber, iron = iron, calcium = calcium, zinc = zinc, b12 = b12, vitaminD = vitaminD,
        folate = folate, isEstimated = isEstimated, clientId = clientId, dirty = false, deleted = deleted
    )

    internal fun WeightLog.toSync() = SyncWeight(clientId, measuredAt, weightKg, deleted)

    internal fun SyncWeight.toLocal(id: Int = 0) =
        WeightLog(id = id, measuredAt = measuredAtMs, weightKg = weightKg, clientId = clientId, dirty = false, deleted = deleted)
}
