package com.example.healthheatv2.data

import android.util.Log
import com.example.healthheatv2.network.ApiService
import com.example.healthheatv2.network.FoodResponse
import com.example.healthheatv2.network.RetrofitClient

class ProductRepository(
    private val productDao: ProductDao,
    private val apiService: ApiService
) {
    // Fetches the product, handling the caching logic automatically
    suspend fun getProduct(barcode: String): FoodResponse {
        // 1. Check local database first
        val cachedProduct = productDao.getProduct(barcode)

        if (cachedProduct != null) {
            Log.d("REPOSITORY", "Cache hit! Loaded $barcode from local database.")
            return cachedProduct.foodResponse
        }

        // 2. If not found locally, fetch from the API
        Log.d("REPOSITORY", "Cache miss! Fetching $barcode from API...")
        val networkResponse = apiService.getFoodData(barcode)

        // 3. Save the new API response to the local database for next time
        val newEntity = ProductCacheEntity(
            barcode = barcode,
            foodResponse = networkResponse
        )
        productDao.insertProduct(newEntity)

        // 4. Clean up old entries to keep the database size manageable (max 100)
        productDao.cleanupOldEntries()

        // 5. Return the fresh data
        return networkResponse
    }

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
                    ProductCacheEntity(barcode = it.barcode ?: "", foodResponse = it)
                }
            } else {
                productDao.getSearchHistory()
            }
        } catch (e: Exception) {
            Log.e("REPOSITORY", "Failed to fetch cloud history", e)
            productDao.getSearchHistory()
        }
    }
}