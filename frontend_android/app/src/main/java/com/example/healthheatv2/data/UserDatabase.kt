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
        SavedDailyPlan::class,
        WeightLog::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun weightLogDao(): WeightLogDao

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        /** Tables holding one person's data; wiped when a different account signs in on this phone. */
        val PERSONAL_TABLES = listOf(
            "scanned_products", "meal_logs", "weight_logs", "user_profiles", "SavedChatMessage", "SavedDailyPlan"
        )

        /** The personal tables that are someone's diary (synced to their account); see wipeOnSignIn. */
        val DIARY_TABLES = listOf("meal_logs", "weight_logs")

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

        /**
         * v10 -> v11: meal logs sync to the account. Each meal gets a stable random id, a dirty flag
         * (existing meals start dirty so they upload once) and soft deletion; weight_logs is new.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_logs ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meal_logs ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE meal_logs ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE meal_logs SET clientId = lower(hex(randomblob(16))) WHERE clientId = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_logs_clientId` ON `meal_logs` (`clientId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`measuredAt` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `clientId` TEXT NOT NULL, " +
                        "`dirty` INTEGER NOT NULL, `deleted` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_logs_clientId` ON `weight_logs` (`clientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_logs_measuredAt` ON `weight_logs` (`measuredAt`)")
            }
        }

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "healthheat_user_database"
                )
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                // Only pre-release development schemas may be wiped.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
