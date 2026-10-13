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
        // Earlier builds didn't record whose data is on this phone; the saved login says.
        backendUserId(savedToken)?.let { prefs.edit().putString(KEY_LAST_USER_ID, it).apply() }
        clearScanCacheOnce()

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
                val userId = backendResponse.user?.id?.toString() ?: backendUserId(accessToken)

                // Meal logs, chat and scan results on this phone belong to whoever signed in last.
                // A different account must not see them. Cleared before the new login is used, so
                // nothing left over syncs into the new account.
                val personalTables = com.example.healthheatv2.data.UserDatabase.PERSONAL_TABLES
                when (wipeOnSignIn(prefs.getString(KEY_LAST_USER_ID, null), userId)) {
                    LocalDataWipe.NONE -> Unit
                    LocalDataWipe.ALL -> {
                        wipePersonalData(personalTables)
                        prefs.edit().remove(KEY_CACHED_PROFILE).remove("onboarding_seen").apply()
                        _userProfile.value = null
                        com.example.healthheatv2.services.TrackingSync.reset(getApplication())
                    }
                    LocalDataWipe.ALL_BUT_DIARY -> {
                        wipePersonalData(personalTables - com.example.healthheatv2.data.UserDatabase.DIARY_TABLES)
                        com.example.healthheatv2.services.TrackingSync.reset(getApplication())
                    }
                }
                if (userId != null) prefs.edit().putString(KEY_LAST_USER_ID, userId).apply()
                RetrofitClient.authToken = accessToken
                prefs.edit().putString("backend_token", accessToken).apply()

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
        com.example.healthheatv2.services.TrackingSync.reset(getApplication())
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

    /** Deletes data on this phone that belongs to one person (never the bundled food data). */
    private suspend fun wipePersonalData(tables: List<String>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = com.example.healthheatv2.data.UserDatabase.getDatabase(getApplication())
        db.runInTransaction {
            val sql = db.openHelper.writableDatabase
            for (table in tables) {
                sql.execSQL("DELETE FROM $table")
            }
        }
    }

    /**
     * Phones that went through a sign-in without the account-switch wipe (owner not recorded yet) may
     * hold another person's scans. They are only a cache: History comes from the account.
     */
    private fun clearScanCacheOnce() {
        if (prefs.getBoolean(KEY_SCAN_CACHE_CLEARED, false)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.healthheatv2.data.UserDatabase.getDatabase(getApplication()).productDao().deleteAll()
            prefs.edit().putBoolean(KEY_SCAN_CACHE_CLEARED, true).apply()
        }
    }

    /** A new weight reading also updates the profile, so calorie and protein targets follow it. */
    fun updateWeight(weightKg: Float) {
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.updateProfile(mapOf("weight_kg" to weightKg))
                fetchProfile()
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed to update weight on profile", e)
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
        const val KEY_SCAN_CACHE_CLEARED = "scan_cache_cleared_v1"
        val gson = com.google.gson.Gson()
    }
}

internal enum class LocalDataWipe { NONE, ALL, ALL_BUT_DIARY }

/**
 * What to clear from this phone when [userId] signs in. [previousUserId] is whoever's data is here;
 * null when unknown (a fresh install, or data from a build that didn't record it). Then scans, chat and
 * plans go, but the meal and weight diary stays: on a phone one person uses (nearly always) it is theirs,
 * and it may not have reached the server yet.
 */
internal fun wipeOnSignIn(previousUserId: String?, userId: String?): LocalDataWipe = when {
    previousUserId == null -> LocalDataWipe.ALL_BUT_DIARY
    previousUserId == userId -> LocalDataWipe.NONE
    else -> LocalDataWipe.ALL
}

/** The backend user id ("sub") in our own login token. The signature isn't checked: this only tells accounts apart. */
internal fun backendUserId(token: String?): String? {
    val payload = token?.split('.')?.getOrNull(1) ?: return null
    return try {
        val json = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject::class.java)
            ?.get("sub")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}