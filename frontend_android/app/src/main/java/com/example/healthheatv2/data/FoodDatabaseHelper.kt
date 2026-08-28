package com.example.healthheatv2.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FoodDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "healthheat.db"
        const val DB_VERSION = 1 // Our ETL generated version
    }

    private var db: SQLiteDatabase? = null

    init {
        copyDatabaseIfNeeded()
        db = this.readableDatabase
    }

    private fun copyDatabaseIfNeeded() {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Not needed, we rely on the pre-packaged asset
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Replace on upgrade
        context.getDatabasePath(DB_NAME).delete()
        copyDatabaseIfNeeded()
    }

    suspend fun searchFood(query: String): List<FoodMaster> = withContext(Dispatchers.IO) {
        val foods = mutableListOf<FoodMaster>()
        val cursor = db?.rawQuery("SELECT * FROM FoodMaster WHERE canonicalName LIKE ?", arrayOf("%$query%"))
        
        cursor?.use {
            val idIdx = it.getColumnIndex("id")
            val nameIdx = it.getColumnIndex("canonicalName")
            val catIdx = it.getColumnIndex("category")
            val subcatIdx = it.getColumnIndex("subcategory")
            val vegIdx = it.getColumnIndex("vegetarian")
            val veganIdx = it.getColumnIndex("vegan")
            val eggIdx = it.getColumnIndex("eggitarian")
            val regionIdx = it.getColumnIndex("region")
            val cuisineIdx = it.getColumnIndex("cuisine")
            val prepIdx = it.getColumnIndex("defaultPreparation")
            val sourceIdx = it.getColumnIndex("source")

            while (it.moveToNext()) {
                val vegetarianFlag = if (vegIdx != -1 && !it.isNull(vegIdx)) it.getInt(vegIdx) > 0 else false
                val veganFlag = if (veganIdx != -1 && !it.isNull(veganIdx)) it.getInt(veganIdx) > 0 else false
                val eggFlag = if (eggIdx != -1 && !it.isNull(eggIdx)) it.getInt(eggIdx) > 0 else false

                foods.add(
                    FoodMaster(
                        id = it.getString(idIdx),
                        canonicalName = it.getString(nameIdx),
                        category = if (catIdx != -1 && !it.isNull(catIdx)) it.getString(catIdx) else null,
                        subcategory = if (subcatIdx != -1 && !it.isNull(subcatIdx)) it.getString(subcatIdx) else null,
                        vegetarian = vegetarianFlag,
                        vegan = veganFlag,
                        eggitarian = eggFlag,
                        region = if (regionIdx != -1 && !it.isNull(regionIdx)) it.getString(regionIdx) else null,
                        cuisine = if (cuisineIdx != -1 && !it.isNull(cuisineIdx)) it.getString(cuisineIdx) else null,
                        defaultPreparation = if (prepIdx != -1 && !it.isNull(prepIdx)) it.getString(prepIdx) else null,
                        source = if (sourceIdx != -1 && !it.isNull(sourceIdx)) it.getString(sourceIdx) else null
                    )
                )
            }
        }
        return@withContext foods
    }

    suspend fun getNutrientAmount(foodId: String, nutrientId: String): Float? = withContext(Dispatchers.IO) {
        var amount: Float? = null
        val cursor = db?.rawQuery("SELECT amountPer100g FROM FoodNutrient WHERE foodId = ? AND nutrientId = ?", arrayOf(foodId, nutrientId))
        cursor?.use {
            if (it.moveToFirst()) {
                amount = it.getFloat(0)
            }
        }
        return@withContext amount
    }
}
