package com.example.healthheatv2.data

import androidx.room.TypeConverter
import com.example.healthheatv2.network.FoodResponse
import com.google.gson.Gson

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromFoodResponse(value: FoodResponse): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFoodResponse(value: String): FoodResponse {
        return gson.fromJson(value, FoodResponse::class.java)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}