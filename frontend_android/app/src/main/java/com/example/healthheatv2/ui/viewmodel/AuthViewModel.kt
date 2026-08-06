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

    private val _userProfile = MutableStateFlow<UserProfileResponse?>(null)
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
            prefs.edit().remove("backend_token").apply()
            RetrofitClient.authToken = null
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
        prefs.edit().remove("backend_token").apply()
        RetrofitClient.authToken = null
        _userProfile.value = null
        _authState.value = AuthState.Idle
    }

    fun fetchProfile() {
        viewModelScope.launch {
            try {
                _userProfile.value = RetrofitClient.apiService.getProfile()
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Failed to fetch profile", e)
            }
        }
    }

    fun getCurrentUser() = auth?.currentUser
}