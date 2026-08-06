package com.example.healthheatv2.data

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RemoteConfigManager {
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
    
    // Key names from Firebase Remote Config
    private const val KEY_SMASH_THRESHOLD = "smash_score_threshold"

    // Default values if Firebase fetch fails
    private const val DEFAULT_SMASH_THRESHOLD = 60L

    private val _smashThreshold = MutableStateFlow(DEFAULT_SMASH_THRESHOLD)
    val smashThreshold: StateFlow<Long> = _smashThreshold.asStateFlow()

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // Cache for 1 hour in production
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_SMASH_THRESHOLD to DEFAULT_SMASH_THRESHOLD
        ))
    }

    fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d("RemoteConfig", "Config params updated: $updated")
                } else {
                    Log.e("RemoteConfig", "Fetch failed")
                }
                
                // Update the state flows with fetched values (or defaults if failed)
                _smashThreshold.value = remoteConfig.getLong(KEY_SMASH_THRESHOLD)
            }
    }
}
