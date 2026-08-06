package com.example.healthheatv2.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _isDark = mutableStateOf(prefs.getBoolean("is_dark_theme", true))
    val isDark: State<Boolean> = _isDark

    fun toggleTheme() {
        val newValue = !_isDark.value
        _isDark.value = newValue
        prefs.edit().putBoolean("is_dark_theme", newValue).apply()
    }
}
