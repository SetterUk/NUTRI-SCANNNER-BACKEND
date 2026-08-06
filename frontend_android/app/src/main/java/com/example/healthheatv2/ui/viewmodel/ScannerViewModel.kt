package com.example.healthheatv2.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthheatv2.data.ProductCacheEntity
import com.example.healthheatv2.data.ProductRepository
import com.example.healthheatv2.network.FoodResponse
import kotlinx.coroutines.launch

sealed class ApiState {
    object Idle : ApiState()
    object Loading : ApiState()
    data class Success(val data: FoodResponse) : ApiState()
    data class Error(val message: String) : ApiState()
}

// 1. Pass the repository into the ViewModel constructor
class ScannerViewModel(private val repository: ProductRepository) : ViewModel() {
    private val _apiState = mutableStateOf<ApiState>(ApiState.Idle)
    val apiState: State<ApiState> = _apiState

    private val _searchHistory = mutableStateOf<List<ProductCacheEntity>>(emptyList())
    val searchHistory: State<List<ProductCacheEntity>> = _searchHistory

    init {
        refreshHistory()
    }

    // Fetch history from the database
    fun refreshHistory() {
        viewModelScope.launch {
            _searchHistory.value = repository.getSearchHistory()
        }
    }

    fun lookupBarcode(barcode: String) {
        _apiState.value = ApiState.Loading
        viewModelScope.launch {
            try {
                // 2. Call the repository instead of the API directly!
                val response = repository.getProduct(barcode)

                _apiState.value = ApiState.Success(response)

                // 3. Refresh the history list so the new scan appears on the History screen
                refreshHistory()

            } catch (e: Exception) {
                _apiState.value = ApiState.Error(e.message ?: "Unknown error occurred")
                Log.e("API_CALL", "Error fetching data", e)
            }
        }
    }

    fun loadFromHistory(cachedProduct: FoodResponse) {
        _apiState.value = ApiState.Success(cachedProduct)
    }

    fun resetState() {
        _apiState.value = ApiState.Idle
    }
}