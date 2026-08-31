package com.example.healthheatv2.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "FoodMaster")
data class FoodMaster(
    @PrimaryKey val id: String,
    val canonicalName: String,
    val category: String?,
    val subcategory: String?,
    val vegetarian: Boolean?,
    val vegan: Boolean?,
    val eggitarian: Boolean?,
    val cuisine: String?,
    val region: String?,
    val defaultPreparation: String?,
    val source: String?
)

@Entity(tableName = "FoodAliases",
    foreignKeys = [ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["foodId"])]
)
data class FoodAlias(
    @PrimaryKey(autoGenerate = true) val aliasId: Long = 0,
    val foodId: String,
    val alias: String
)

@Entity(tableName = "NutrientDefinition")
data class NutrientDefinition(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val category: String?
)

@Entity(tableName = "FoodNutrient",
    primaryKeys = ["foodId", "nutrientId"],
    foreignKeys = [
        ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NutrientDefinition::class, parentColumns = ["id"], childColumns = ["nutrientId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["nutrientId"])]
)
data class FoodNutrient(
    val foodId: String,
    val nutrientId: String,
    val amountPer100g: Float?,
    val source: String?
)

@Entity(tableName = "ServingSize",
    foreignKeys = [ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["foodId"])]
)
data class ServingSize(
    @PrimaryKey(autoGenerate = true) val servingId: Long = 0,
    val foodId: String,
    val unit: String,
    val quantity: Float?,
    val grams: Float?,
    val milliliters: Float?,
    val description: String?,
    val source: String?
)

@Entity(tableName = "FoodPreparation",
    foreignKeys = [ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["foodId"])]
)
data class FoodPreparation(
    @PrimaryKey val id: String,
    val foodId: String,
    val preparationMethod: String?,
    val state: String?,
    val nutrientReference: String?
)

@Entity(tableName = "Recipe")
data class Recipe(
    @PrimaryKey val id: String,
    val name: String,
    val cuisine: String?,
    val region: String?,
    val mealType: String?,
    val servings: Float?,
    val instructions: String?,
    val source: String?
)

@Entity(tableName = "RecipeIngredient",
    foreignKeys = [
        ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["recipeId"]), Index(value = ["foodId"])]
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val ingredientId: Long = 0,
    val recipeId: String,
    val foodId: String,
    val quantity: Float?,
    val unit: String?,
    val preparation: String?
)

@Entity(tableName = "FoodRoles",
    foreignKeys = [ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["foodId"])]
)
data class FoodRole(
    @PrimaryKey(autoGenerate = true) val roleId: Long = 0,
    val foodId: String,
    val role: String
)

@Entity(tableName = "FoodSubstitutions",
    foreignKeys = [
        ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["alternativeFoodId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["foodId"]), Index(value = ["alternativeFoodId"])]
)
data class FoodSubstitution(
    @PrimaryKey(autoGenerate = true) val subId: Long = 0,
    val foodId: String,
    val alternativeFoodId: String,
    val nutritionalRole: String?,
    val compatibility: String?
)

@Entity(tableName = "FoodAllergen",
    foreignKeys = [ForeignKey(entity = FoodMaster::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["foodId"])]
)
data class FoodAllergen(
    @PrimaryKey(autoGenerate = true) val allergenIdAuto: Long = 0,
    val foodId: String,
    val allergenId: String,
    val source: String?,
    val confidence: String?
)

@Entity(tableName = "MealTemplate")
data class MealTemplate(
    @PrimaryKey val id: String,
    val name: String?,
    val mealType: String?,
    val region: String?
)

@Entity(tableName = "MealItem",
    foreignKeys = [ForeignKey(entity = MealTemplate::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["templateId"])]
)
data class MealItem(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0,
    val templateId: String,
    val foodOrRecipeId: String?,
    val type: String?
)

@Entity(tableName = "ICMRRule")
data class ICMRRule(
    @PrimaryKey val id: String,
    val condition_tag: String,
    val clinical_focus: String,
    val must_avoid: String,
    val recommended_swaps: String,
    val required_tags: String,
    val banned_tags: String
)

@Entity(tableName = "IFCTFood")
data class IFCTFood(
    @PrimaryKey val id: String,
    val food_name: String,
    val portion_size: String,
    val energy_kcal: Float,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val fiber_g: Float,
    val iron_mg: Float,
    val calcium_mg: Float,
    val zinc_mg: Float,
    val b12_mcg: Float,
    val vitamin_d_iu: Float,
    val folate_mcg: Float,
    val tags: String
)
@Entity(tableName = "SavedChatMessage")
data class SavedChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "SavedDailyPlan")
data class SavedDailyPlan(
    @PrimaryKey val dateKey: String, // "2026-08-31"
    val planText: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val aiTier: String = "unknown" // "gemma4_e2b", "cloud", "offline"
)
