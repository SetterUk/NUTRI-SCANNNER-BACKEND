package com.example.healthheatv2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductCacheEntity::class,
        UserProfile::class,
        LoggedMeal::class,
        ICMRRule::class,
        IFCTFood::class,
        FoodMaster::class,
        FoodAlias::class,
        NutrientDefinition::class,
        FoodNutrient::class,
        ServingSize::class,
        FoodPreparation::class,
        Recipe::class,
        RecipeIngredient::class,
        FoodRole::class,
        FoodSubstitution::class,
        FoodAllergen::class,
        MealTemplate::class,
        MealItem::class,
        SavedChatMessage::class,
        SavedDailyPlan::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        /** Tables holding one person's data; wiped when a different account signs in on this phone. */
        val PERSONAL_TABLES = listOf(
            "scanned_products", "meal_logs", "user_profiles", "SavedChatMessage", "SavedDailyPlan"
        )

        /**
         * v9 -> v10: meal_logs gains isEstimated + a date index; user_profiles gains missingFields.
         * Every schema change from here on needs a Migration: users' meal history and
         * chat live in this database, and a destructive fallback would erase them on update.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_logs ADD COLUMN isEstimated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_logs_date ON meal_logs (date)")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN missingFields TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "healthheat_user_database"
                )
                .addMigrations(MIGRATION_9_10)
                // Only pre-release development schemas may be wiped.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
