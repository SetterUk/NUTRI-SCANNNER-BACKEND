package com.example.healthheatv2.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.network.TokenData
import com.example.healthheatv2.network.UserProfileResponse


sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Seeded from the on-device copy so screens get the user's real profile immediately,
    // even when the backend is cold-starting or the phone is offline.
    private val _userProfile = MutableStateFlow<UserProfileResponse?>(loadCachedProfile())
    val userProfile: StateFlow<UserProfileResponse?> = _userProfile.asStateFlow()

    init {
        val user = auth?.currentUser
        val savedToken = prefs.getString("backend_token", null)
        
        if (user != null && savedToken != null) {
            RetrofitClient.authToken = savedToken
            _authState.value = AuthState.Success
            fetchProfile()
        } else {
            auth?.signOut()
            prefs.edit().remove("backend_token").remove(KEY_CACHED_PROFILE).apply()
            RetrofitClient.authToken = null
            _userProfile.value = null
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        setLoading()
        viewModelScope.launch {
            try {
                // Send the raw Google ID token to our backend FIRST
                val backendResponse = RetrofitClient.apiService.googleAuth(TokenData(idToken))
                val accessToken = backendResponse.accessToken
                RetrofitClient.authToken = accessToken
                prefs.edit().putString("backend_token", accessToken).apply()

                // Meal logs, chat and scan results on this phone belong to whoever signed in last.
                // A different account must not see them.
                val userId = backendResponse.user?.id?.toString()
                val previousUserId = prefs.getString(KEY_LAST_USER_ID, null)
                if (userId != null && previousUserId != null && previousUserId != userId) {
                    wipePersonalData()
                    prefs.edit().remove(KEY_CACHED_PROFILE).remove("onboarding_seen").apply()
                    _userProfile.value = null
                }
                if (userId != null) prefs.edit().putString(KEY_LAST_USER_ID, userId).apply()
                
                // Then optionally login to Firebase
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth?.signInWithCredential(credential)?.await()
                
                fetchProfile()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                android.util.Log.e("Firebase_Auth_Error", "Detailed error:", e)

                _authState.value = AuthState.Error(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }

    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun signOut() {
        auth?.signOut()
        prefs.edit().remove("backend_token").remove(KEY_CACHED_PROFILE).apply()
        RetrofitClient.authToken = null
        _userProfile.value = null
        _authState.value = AuthState.Idle
    }

    /**
     * Deletes the account on the server, then wipes everything stored on this device
     * (meal logs, chat history, scan cache, cached profile) and signs out.
     */
    suspend fun deleteAccount(): Result<Unit> = try {
        RetrofitClient.apiService.deleteAccount()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.healthheatv2.data.UserDatabase.getDatabase(getApplication())
            db.clearAllTables()
            // clearAllTables also removed the bundled food database; re-import it for the next user.
            getApplication<Application>().getSharedPreferences("food_seed", Context.MODE_PRIVATE).edit().clear().apply()
            com.example.healthheatv2.data.FoodSeeder.invalidate()
            com.example.healthheatv2.data.FoodSeeder.ensureSeeded(getApplication(), db.nutritionDao())
        }
        prefs.edit().clear().apply()
        signOut()
        Result.success(Unit)
    } catch (e: Exception) {
        android.util.Log.e("AuthViewModel", "Account deletion failed", e)
        Result.failure(e)
    }

    fun fetchProfile() {
        viewModelScope.launch {
            try {
                val profile = RetrofitClient.apiService.getProfile()
                val previous = _userProfile.value
                if (previous != null && previous != profile) {
                    // Saved scan results include allergy checks for the old profile.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.healthheatv2.data.UserDatabase.getDatabase(getApplication()).productDao().deleteAll()
                    }
                }
                _userProfile.value = profile
                prefs.edit().putString(KEY_CACHED_PROFILE, gson.toJson(profile)).apply()
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed to fetch profile", e)
            }
        }
    }

    /** Deletes everything on this phone that belongs to one person (not the bundled food data). */
    private suspend fun wipePersonalData() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = com.example.healthheatv2.data.UserDatabase.getDatabase(getApplication())
        db.runInTransaction {
            val sql = db.openHelper.writableDatabase
            for (table in com.example.healthheatv2.data.UserDatabase.PERSONAL_TABLES) {
                sql.execSQL("DELETE FROM $table")
            }
        }
    }

    fun getCurrentUser() = auth?.currentUser

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean("onboarding_seen", false)

    fun markOnboardingSeen() = prefs.edit().putBoolean("onboarding_seen", true).apply()

    private fun loadCachedProfile(): UserProfileResponse? = try {
        prefs.getString(KEY_CACHED_PROFILE, null)?.let { gson.fromJson(it, UserProfileResponse::class.java) }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY_CACHED_PROFILE = "cached_profile_json"
        const val KEY_LAST_USER_ID = "last_backend_user_id"
        val gson = com.google.gson.Gson()
    }
}