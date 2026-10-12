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
/** A healthier product from the same category, already filtered for the user's allergies/diet. */
data class AlternativeProduct(
    @SerializedName("code") val code: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("nutri_score") val nutriScore: String?,
    @SerializedName("health_score") val healthScore: Int?,
    @SerializedName("verdict") val verdict: String?,
    @SerializedName("why_better") val whyBetter: List<String>?
)

/** Plain-language reasons behind the score, computed by the backend's scoring engine (not the LLM). */
data class ScoreBreakdown(
    @SerializedName("concerns") val concerns: List<String>?,
    @SerializedName("positives") val positives: List<String>?,
    @SerializedName("category") val category: String?,
    @SerializedName("allergens") val allergens: List<String>?,
    @SerializedName("data_confidence") val dataConfidence: Double?
)
data class TokenData(
    @SerializedName("id_token") val idToken: String
)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: AuthUser? = null
)
data class AuthUser(
    @SerializedName("id") val id: Long?,
    @SerializedName("email") val email: String?
)
data class ContributeRequest(
    @SerializedName("name") val name: String,
    @SerializedName("ingredients_text") val ingredientsText: String,
    @SerializedName("image_base64") val imageBase64: String? = null
)
data class UserProfileResponse(
    val age: Int?,
    @SerializedName("weight_kg") val weightKg: Double?,
    val height: Double?,
    val gender: String?,
    @SerializedName("activity_level") val activityLevel: String?,
    @SerializedName("dietary_preferences") val dietaryPreferences: String?,
    @SerializedName("health_goals") val healthGoals: String?,
    val allergies: List<String>? = emptyList(),
    @SerializedName("health_tags") val healthTags: List<String>? = emptyList(),
    @SerializedName("preferred_name") val preferredName: String? = null,
    @SerializedName("medical_reports") val medicalReports: String? = null,
    val bmi: Float? = null,
    val bmr: Float? = null
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
    @SerializedName("verdict_color") val verdictColor: String?,
    @SerializedName("score_breakdown") val scoreBreakdown: ScoreBreakdown? = null,
    /** Allergy/diet conflicts for the signed-in user; when non-empty the verdict is AVOID. */
    @SerializedName("personal_alerts") val personalAlerts: List<String>? = null,
    /** Only set on /scan/history rows: when this user last scanned the product (UTC, ISO-8601). */
    @SerializedName("scanned_at") val scannedAt: String? = null
)

// ── Meal/weight sync (/api/tracking/sync); times are epoch milliseconds ──
data class SyncMeal(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("logged_at_ms") val loggedAtMs: Long,
    @SerializedName("meal_slot") val mealSlot: String,
    @SerializedName("food_id") val foodId: String,
    @SerializedName("food_name") val foodName: String,
    @SerializedName("quantity") val quantity: Float,
    @SerializedName("unit") val unit: String,
    @SerializedName("calories") val calories: Float,
    @SerializedName("protein") val protein: Float,
    @SerializedName("carbs") val carbs: Float,
    @SerializedName("fat") val fat: Float,
    @SerializedName("fiber") val fiber: Float,
    @SerializedName("iron") val iron: Float,
    @SerializedName("calcium") val calcium: Float,
    @SerializedName("zinc") val zinc: Float,
    @SerializedName("b12") val b12: Float,
    @SerializedName("vitamin_d") val vitaminD: Float,
    @SerializedName("folate") val folate: Float,
    @SerializedName("is_estimated") val isEstimated: Boolean,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncWeight(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("measured_at_ms") val measuredAtMs: Long,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("deleted") val deleted: Boolean
)

data class TrackingSyncRequest(
    @SerializedName("since_ms") val sinceMs: Long?,
    @SerializedName("meals") val meals: List<SyncMeal>,
    @SerializedName("weights") val weights: List<SyncWeight>
)

data class TrackingSyncResponse(
    @SerializedName("server_time_ms") val serverTimeMs: Long,
    @SerializedName("meals") val meals: List<SyncMeal>?,
    @SerializedName("weights") val weights: List<SyncWeight>?
)

data class ApiChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatRequest(
    @SerializedName("messages") val messages: List<ApiChatMessage>
)

data class ChatResponse(
    @SerializedName("response") val response: String
)

interface ApiService {
    // 1. Changed to @POST
    // 2. The {barcode} in the path matches the @Path variable below
    @POST("/api/scan/{barcode}")
    suspend fun getFoodData(
        @Path("barcode") barcode: String,
        @Query("user_profile") userProfile: String = "General Health" // Optional, matches your default
    ): FoodResponse

    @GET("/api/scan/history")
    suspend fun getHistory(): List<FoodResponse>

    @GET("/api/profile")
    suspend fun getProfile(): UserProfileResponse

    @PUT("/api/profile")
    suspend fun updateProfile(@Body profileData: Map<String, @JvmSuppressWildcards Any>): Map<String, String>

    @POST("/api/auth/google")
    suspend fun googleAuth(@Body tokenData: TokenData): AuthResponse

    @retrofit2.http.PATCH("/api/scan/{barcode}/contribute")
    suspend fun contributeProduct(
        @Path("barcode") barcode: String,
        @Body data: ContributeRequest
    )

    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    /** Uploads changed meal/weight rows and returns everything changed on the server since sinceMs. */
    @POST("/api/tracking/sync")
    suspend fun syncTracking(@Body request: TrackingSyncRequest): TrackingSyncResponse

    /** Permanently deletes the account and all server-side data (required by Google Play). */
    @retrofit2.http.DELETE("/api/account")
    suspend fun deleteAccount(): Map<String, String>
}

// 3. Create the Retrofit Singleton
object RetrofitClient {
    // For physical device testing over the internet, use the production backend
    const val BASE_URL = "https://nutri-scanner-api.onrender.com/"
    
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