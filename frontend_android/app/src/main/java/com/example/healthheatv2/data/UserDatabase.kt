package com.example.healthheatv2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProductCacheEntity::class,
        UserProfile::class,
        LoggedMeal::class
    ],
    version = 4, // Incremented version since we drastically changed schema
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun mealLogDao(): MealLogDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "healthheat_user_database"
                )
                .fallbackToDestructiveMigration() // Still acceptable for hackathon dev until prod release
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}