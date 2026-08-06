package com.example.healthheatv2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.healthheatv2.ui.theme.HealthHeatv2Theme

import com.google.firebase.FirebaseApp
import com.example.healthheatv2.data.RemoteConfigManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        try {
            FirebaseApp.initializeApp(this)
            RemoteConfigManager.fetchAndActivate()
        } catch (e: Exception) {
            // Ignore if already initialized or if config is missing (will be handled by the UI/ViewModel later if needed)
        }
        enableEdgeToEdge()
        setContent {
            HealthHeatv2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
