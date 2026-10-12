package com.example.healthheatv2.data

import android.util.Log
import com.example.healthheatv2.network.ApiService
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.network.RetrofitClient
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ProductRepository(
    private val productDao: ProductDao,
    private val apiService: ApiService
) {
    /**
     * Recent results come from the phone; older ones are re-fetched so improved analyses,
     * profile changes and new alternatives reach the user. A stale copy is only used when
     * the server can't be reached.
     */
    suspend fun getProduct(barcode: String): FoodResponse {
        val cached = productDao.getProduct(barcode)
        val fresh = cached != null &&
            System.currentTimeMillis() - cached.scannedAt < CACHE_TTL_MS &&
            cached.foodResponse.healthScore != null
        if (fresh) {
            Log.d("REPOSITORY", "Cache hit for $barcode")
            return cached!!.foodResponse
        }

        val networkResponse = try {
            apiService.getFoodData(barcode)
        } catch (e: java.io.IOException) {
            if (cached != null) return cached.foodResponse
            throw e
        } catch (e: retrofit2.HttpException) {
            if (cached != null && e.code() >= 500) return cached.foodResponse
            throw e
        }

        // A failed analysis must not stick: the user should get a real result on the next try.
        if (networkResponse.healthScore != null) {
            productDao.insertProduct(ProductCacheEntity(barcode = barcode, foodResponse = networkResponse))
            productDao.cleanupOldEntries()
        }
        return networkResponse
    }

    /** Results are personalized (allergy alerts, alternatives), so they must not outlive the account. */
    suspend fun clearCache() = productDao.deleteAll()

    suspend fun contributeToDatabase(barcode: String, newName: String, newIngredients: String, imageBase64: String?) {
        try {
            apiService.contributeProduct(
                barcode,
                com.example.healthheatv2.network.ContributeRequest(newName, newIngredients, imageBase64)
            )
        } catch (e: Exception) {
            Log.e("REPOSITORY", "Failed to contribute to backend", e)
            throw e // rethrow to be caught by ViewModel
        }

        // Wipe local cache so it fetches fresh AI analysis next time!
        productDao.deleteProduct(barcode)
    }

    // Fetches the entire scan history for the History Screen
    suspend fun getSearchHistory(): List<ProductCacheEntity> {
        return try {
            if (RetrofitClient.authToken != null) {
                // User is logged in, fetch from cloud!
                val apiHistory = apiService.getHistory()
                // Convert to ProductCacheEntity for UI compatibility
                apiHistory.map {
                    ProductCacheEntity(
                        barcode = it.barcode ?: "",
                        foodResponse = it,
                        scannedAt = parseServerTime(it.scannedAt) ?: 0L
                    )
                }
            } else {
                productDao.getSearchHistory()
            }
        } catch (e: Exception) {
            Log.e("REPOSITORY", "Failed to fetch cloud history", e)
            productDao.getSearchHistory()
        }
    }

    companion object {
        const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L
    }
}

/** The backend sends naive UTC timestamps ("2026-10-12T09:30:00.123456"). */
fun parseServerTime(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
