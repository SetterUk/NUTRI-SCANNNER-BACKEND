package com.example.healthheatv2.network

import  retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.http.PUT
import retrofit2.http.Body
import java.util.concurrent.TimeUnit

data class IngredientAnalysis(
    @SerializedName("name") val name: String?,
    @SerializedName("quantity") val quantity: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("reason") val reason: String?
)
data class NutritionAnalysis(
    @SerializedName("energy_estimation") val energyEstimation: String?,
    @SerializedName("macronutrient_balance") val macronutrientBalance: String?
)
data class AlternativeProduct(
    @SerializedName("product_name") val name: String?,
    @SerializedName("brands") val brand: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("nutriscore_grade") val nutriScore: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("color_hex") val colorHex: String?
)
data class TokenData(
    @SerializedName("id_token") val idToken: String
)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)
data class FoodResponse(
    @SerializedName("verdict") val verdict: String?,
    @SerializedName("health_score") val healthScore: Int?,
    @SerializedName("summary") val summary: String?,

    // NEW FIELDS ADDED HERE
    @SerializedName("is_good_for_health") val isGoodForHealth: Boolean?,
    @SerializedName("health_reason") val healthReason: String?,
    @SerializedName("health_scale") val healthScale: Double?,
    @SerializedName("safe_consumption_frequency") val safeConsumptionFrequency: String?,
    @SerializedName("nutrition_analysis") val nutritionAnalysis: NutritionAnalysis?,

    @SerializedName("ingredients_analysis") val ingredientsAnalysis: List<IngredientAnalysis>?,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("quantity") val quantity: String?,
    @SerializedName("ingredients_text") val ingredientsText: String?,
    @SerializedName("ingredients") val ingredients: List<String>?,

    @SerializedName("nutrients") val nutrients: Map<String, Any>?,

    @SerializedName("nutri_score") val nutriScore: String?,
    @SerializedName("nova_group") val novaGroup: Int?,
    @SerializedName("nova_tags") val novaTags: List<String>?,
    @SerializedName("categories") val categories: String?,
    @SerializedName("countries") val countries: String?,
    @SerializedName("allergens") val allergens: String?,
    @SerializedName("additives_tags") val additivesTags: List<String>?,
    @SerializedName("serving_size") val servingSize: String?,
    @SerializedName("ecoscore_grade") val ecoscoreGrade: String?,
    @SerializedName("nutrient_levels") val nutrientLevels: Map<String, String>?,
    @SerializedName("packaging") val packaging: String?,
    @SerializedName("alternatives") val alternatives: List<AlternativeProduct>?,
    @SerializedName("verdict_color") val verdictColor: String?
)
interface ApiService {
    // 1. Changed to @POST
    // 2. The {barcode} in the path matches the @Path variable below
    @POST("/api/scan/{barcode}")
    suspend fun getFoodData(
        @Path("barcode") barcode: String,
        @Query("user_profile") userProfile: String = "General Health" // Optional, matches your default
    ): FoodResponse

    @GET("/api/history")
    suspend fun getHistory(): List<FoodResponse>

    @PUT("/api/profile")
    suspend fun updateProfile(@Body profileData: Map<String, Any>): Map<String, String>

    @POST("/api/auth/google")
    suspend fun googleAuth(@Body tokenData: TokenData): AuthResponse
}

// 3. Create the Retrofit Singleton
object RetrofitClient {
    // For physical device testing, use the computer's local IP address
    private const val BASE_URL = "http://10.50.156.69:8000/"
    // private const val BASE_URL = "https://nutri-scanner-api.onrender.com/"
    
    var authToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
        authToken?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}