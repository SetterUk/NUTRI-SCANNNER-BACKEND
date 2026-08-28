package com.example.healthheatv2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    val userId: String = "user_1", // singleton for demo
    
    // Demographics
    val age: Int,
    val sex: String, // "M" | "F" | "Other"
    val heightCm: Float, // height in cm
    val weightKg: Float, // weight in kg
    
    // Activity & Goals
    val activityLevel: String, // "sedentary" | "light" | "moderate" | "active" | "very_active"
    val primaryGoal: String, // "weight_loss" | "weight_gain" | "muscle_gain" | "general_health"
    val secondaryGoals: List<String>, // ["improve_energy", "better_digestion", "skin_health"]
    
    // Dietary Preferences & Restrictions
    val dietType: String, // "omnivore" | "vegetarian" | "vegan" | "pescatarian"
    val allergies: List<String>, // ["peanuts", "gluten", "shellfish"]
    val dietaryRestrictions: List<String>, // ["dairy_free", "no_processed", "low_sodium"]
    val dislikedFoods: List<String>, // ["bitter_gourd", "mushrooms"]
    val preferredCuisines: List<String>, // ["North Indian", "South Indian"]
    
    // Health Conditions & Medical Reports (user self-reported or PDF extracted)
    val healthTags: List<String>, // ["diabetic", "prediabetic", "high_bp", "vegetarian_protein_deficient"]
    val medicalReports: String = "", // Detailed medical notes or extracted PDF text
    
    // Calculated Targets (derived from BMI/BMR/TDEE)
    val bmi: Float? = null,
    val bmr: Float? = null, // Basal Metabolic Rate (kcal/day)
    val tdee: Float? = null, // Total Daily Energy Expenditure
    val dailyCalories: Float? = null,
    val dailyProtein: Float? = null, // grams
    val dailyCarbs: Float? = null,
    val dailyFat: Float? = null,
    val dailyFiber: Float? = null,
    val dailyWater: Float? = null, // liters
    
    // Micronutrient targets (based on IFCT 2017 RDA for India)
    val dailyIron: Float? = null,
    val dailyCalcium: Float? = null,
    val dailyZinc: Float? = null,
    val dailyB12: Float? = null,
    val dailyVitaminD: Float? = null,
    val dailyFolate: Float? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
