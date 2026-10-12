package com.example.healthheatv2.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthheatv2.data.ProductCacheEntity
import com.example.healthheatv2.data.ProductRepository
import com.example.healthheatv2.network.FoodResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ApiState {
    object Idle : ApiState()
    /** [slow] turns true after a few seconds, usually because the free server is waking up. */
    data class Loading(val slow: Boolean = false) : ApiState()
    data class Success(val data: FoodResponse) : ApiState()
    data class Error(val message: String) : ApiState()
}

class ScannerViewModel(private val repository: ProductRepository) : ViewModel() {
    private val _apiState = mutableStateOf<ApiState>(ApiState.Idle)
    val apiState: State<ApiState> = _apiState

    private val _searchHistory = mutableStateOf<List<ProductCacheEntity>>(emptyList())
    val searchHistory: State<List<ProductCacheEntity>> = _searchHistory

    var lastScannedBarcode: String = ""
    var selectedGap: com.example.healthheatv2.services.NutritionGap? = null

    private var lookupJob: Job? = null

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
        lastScannedBarcode = barcode
        _apiState.value = ApiState.Loading()
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val slowNotice = launch {
                delay(SLOW_AFTER_MS)
                if (_apiState.value is ApiState.Loading) _apiState.value = ApiState.Loading(slow = true)
            }
            try {
                val response = repository.getProduct(barcode)
                _apiState.value = ApiState.Success(response)
                refreshHistory()
            } catch (e: retrofit2.HttpException) {
                _apiState.value = ApiState.Error(httpMessage(e.code()))
                Log.e("API_CALL", "HTTP Error fetching data", e)
            } catch (e: java.io.IOException) {
                _apiState.value = ApiState.Error("Can't reach the server. Check your internet connection and try again.")
                Log.e("API_CALL", "Network error fetching data", e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _apiState.value = ApiState.Error("Something went wrong. Please try again.")
                Log.e("API_CALL", "Error fetching data", e)
            } finally {
                slowNotice.cancel()
            }
        }
    }

    /**
     * Shows the saved result immediately, then refreshes it: history rows are the generic analysis
     * and carry no allergy checks or alternatives for this user.
     */
    fun loadFromHistory(cachedProduct: FoodResponse) {
        _apiState.value = ApiState.Success(cachedProduct)
        val barcode = cachedProduct.barcode ?: return
        lastScannedBarcode = barcode
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            try {
                val fresh = repository.getProduct(barcode)
                if ((_apiState.value as? ApiState.Success)?.data?.barcode == barcode) {
                    _apiState.value = ApiState.Success(fresh)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("API_CALL", "Background refresh of $barcode failed; keeping saved result", e)
            }
        }
    }

    fun contribute(barcode: String, newName: String, newIngredients: String, imageBase64: String?) {
        _apiState.value = ApiState.Loading()
        viewModelScope.launch {
            try {
                repository.contributeToDatabase(barcode, newName, newIngredients, imageBase64)
                // Re-fetch product to get the new AI analysis and updated data!
                lookupBarcode(barcode)
            } catch (e: Exception) {
                _apiState.value = ApiState.Error("Couldn't send your contribution. Please try again.")
            }
        }
    }

    /** Called when the signed-in account changes or the profile is edited. */
    fun clearPersonalCache() {
        viewModelScope.launch {
            repository.clearCache()
            refreshHistory()
        }
    }

    fun resetState() {
        lookupJob?.cancel()
        _apiState.value = ApiState.Idle
    }

    companion object {
        private const val SLOW_AFTER_MS = 6_000L

        /** "404" is kept in the not-found message: ProductNotFoundScreen keys off it. */
        fun httpMessage(code: Int): String = when (code) {
            404 -> "404 Product Not Found"
            401, 403 -> "Your session has expired. Please sign in again."
            429 -> "Too many scans in a short time. Please wait a minute and try again."
            503 -> "The product database is busy right now. Please try again in a moment."
            in 500..599 -> "Our server had a problem. Please try again in a moment."
            else -> "Something went wrong (error $code). Please try again."
        }
    }
}
