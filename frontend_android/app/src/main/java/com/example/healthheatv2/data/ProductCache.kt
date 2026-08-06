package com.example.healthheatv2.data

import androidx.room.*
import com.example.healthheatv2.network.FoodResponse

// 1. The Entity (The Database Table)
@Entity(tableName = "scanned_products")
data class ProductCacheEntity(
    @PrimaryKey val barcode: String,
    val foodResponse: FoodResponse, // The TypeConverter handles this automatically!
    val scannedAt: Long = System.currentTimeMillis() // To help us sort by newest
)

// 2. The DAO (Data Access Object - Your Queries)
@Dao
interface ProductDao {
    // Look up a single product by barcode
    @Query("SELECT * FROM scanned_products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProduct(barcode: String): ProductCacheEntity?

    // Get all history, limited to the 100 most recent searches
    @Query("SELECT * FROM scanned_products ORDER BY scannedAt DESC LIMIT 100")
    suspend fun getSearchHistory(): List<ProductCacheEntity>

    // Save a new product to the database
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductCacheEntity)

    // Optional cleanup: Delete anything beyond the top 100 recent scans
    @Query("DELETE FROM scanned_products WHERE barcode NOT IN (SELECT barcode FROM scanned_products ORDER BY scannedAt DESC LIMIT 100)")
    suspend fun cleanupOldEntries()
}