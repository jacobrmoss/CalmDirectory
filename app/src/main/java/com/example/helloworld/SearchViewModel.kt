package com.example.helloworld

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val placesApiService = GooglePlacesApiService(application, userPreferencesRepository)
    private val locationService = LocationService(application)
    private val geocodingService = GoogleGeocodingService(userPreferencesRepository)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Poi>>(emptyList())
    val searchResults: StateFlow<List<Poi>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var searchJob: Job? = null
    private var cachedLocation: Pair<Double, Double>? = null

    init {
        // Observe preference changes to automatically invalidate the location cache.
        viewModelScope.launch {
            // Any change in either location-related preference will trigger a cache invalidation.
            userPreferencesRepository.useDeviceLocation
                .combine(userPreferencesRepository.defaultLocation) { _, _ -> }
                .collect {
                    invalidateLocation()
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isNotBlank()) {
            _isLoading.value = true
            searchJob = viewModelScope.launch {
                delay(300L) // Debounce for 300 milliseconds
                try {
                    val (lat, lon) = getOrFetchLocation()
                    val results = placesApiService.search(query, lat, lon)
                    _searchResults.value = results
                } catch (e: SecurityException) {
                    Log.e("SearchViewModel", "Location permission not granted", e)
                    _searchResults.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    private suspend fun getOrFetchLocation(): Pair<Double, Double> {
        if (cachedLocation != null) {
            return cachedLocation!!
        }

        val useDeviceLocation = userPreferencesRepository.useDeviceLocation.first()
        val location = if (useDeviceLocation) {
            val deviceLocation = locationService.getCurrentLocation()
            (deviceLocation?.latitude ?: 0.0) to (deviceLocation?.longitude ?: 0.0)
        } else {
            val defaultLocation = userPreferencesRepository.defaultLocation.first()
            if (!defaultLocation.isNullOrBlank()) {
                geocodingService.getCoordinates(defaultLocation) ?: (0.0 to 0.0)
            } else {
                0.0 to 0.0
            }
        }
        cachedLocation = location
        return location
    }

    private fun invalidateLocation() {
        cachedLocation = null
        prefetchLocation()
    }

    private fun prefetchLocation() {
        viewModelScope.launch {
            try {
                getOrFetchLocation()
            } catch (e: SecurityException) {
                Log.e("SearchViewModel", "Location permission not granted during prefetch", e)
            }
        }
    }
}