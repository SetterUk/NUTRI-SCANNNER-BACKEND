package com.example.healthheatv2.ui.viewmodel

import androidx.lifecycle.ViewModel
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


sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Automatically skip login if they are already authenticated
        if (auth?.currentUser != null) {
            _authState.value = AuthState.Success
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        setLoading()
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth!!.signInWithCredential(credential).await()
                
                // Get fresh ID token from Firebase to send to backend
                val firebaseUser = authResult.user
                val freshIdToken = firebaseUser?.getIdToken(true)?.await()?.token
                
                if (freshIdToken != null) {
                    val backendResponse = RetrofitClient.apiService.googleAuth(TokenData(freshIdToken))
                    RetrofitClient.authToken = backendResponse.accessToken
                }
                
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                // ADD THIS LINE TO CATCH THE THIEF!
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
        _authState.value = AuthState.Idle
    }

    fun getCurrentUser() = auth?.currentUser
}