package com.example.helloworld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class DistanceUnit {
    IMPERIAL,
    METRIC
}

class UserPreferencesRepository(
    private val context: Context
) {
    val googleApiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }

    val useDeviceLocation: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_DEVICE_LOCATION] ?: false
        }

    val defaultLocation: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_LOCATION]
        }

    val mapApp: Flow<MapApp> = context.dataStore.data
        .map { preferences ->
            MapApp.valueOf(preferences[MAP_APP] ?: MapApp.DEFAULT.name)
        }

    val searchProvider: Flow<SearchProvider> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[SEARCH_PROVIDER]
            SearchProvider.values().firstOrNull { it.name == stored } ?: SearchProvider.HERE
        }

    val searchRadius: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SEARCH_RADIUS] ?: 10
        }

    val distanceUnit: Flow<DistanceUnit> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[DISTANCE_UNIT]
            if (stored != null) {
                try {
                    DistanceUnit.valueOf(stored)
                } catch (e: IllegalArgumentException) {
                    DistanceUnit.IMPERIAL
                }
            } else {
                DistanceUnit.IMPERIAL
            }
        }

    suspend fun saveGoogleApiKey(apiKey: String) {
        context.dataStore.edit { settings ->
            settings[API_KEY] = apiKey
        }
    }

    suspend fun saveUseDeviceLocation(useDeviceLocation: Boolean) {
        context.dataStore.edit { settings ->
            settings[USE_DEVICE_LOCATION] = useDeviceLocation
        }
    }

    suspend fun saveDefaultLocation(defaultLocation: String) {
        context.dataStore.edit { settings ->
            settings[DEFAULT_LOCATION] = defaultLocation
        }
    }

    suspend fun saveMapApp(mapApp: MapApp) {
        context.dataStore.edit { settings ->
            settings[MAP_APP] = mapApp.name
        }
    }

    suspend fun saveSearchRadius(radius: Int) {
        context.dataStore.edit { settings ->
            settings[SEARCH_RADIUS] = radius
        }
    }

    suspend fun saveSearchProvider(provider: SearchProvider) {
        context.dataStore.edit { settings ->
            settings[SEARCH_PROVIDER] = provider.name
        }
    }

    suspend fun saveDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { settings ->
            settings[DISTANCE_UNIT] = unit.name
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val USE_DEVICE_LOCATION = booleanPreferencesKey("use_device_location")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val MAP_APP = stringPreferencesKey("map_app")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARCH_RADIUS = intPreferencesKey("search_radius")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    }
}