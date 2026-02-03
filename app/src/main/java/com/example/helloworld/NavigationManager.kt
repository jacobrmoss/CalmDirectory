package com.example.helloworld

import android.content.Context
import com.calmapps.directory.R
import com.mapbox.common.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NavigationManager {
    private var _mapboxNavigation: MapboxNavigation? = null
    val mapboxNavigation: MapboxNavigation?
        get() = _mapboxNavigation

    private val _isNavigationActive = MutableStateFlow(false)
    val isNavigationActive = _isNavigationActive.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground = _isAppInForeground.asStateFlow()

    fun getInstance(context: Context): MapboxNavigation {
        if (_mapboxNavigation == null) {
            if (MapboxOptions.accessToken == null) {
                MapboxOptions.accessToken = context.getString(R.string.mapbox_access_token)
            }

            val navigationOptions = NavigationOptions.Builder(context).build()
            _mapboxNavigation = MapboxNavigationProvider.create(navigationOptions)
        }
        return _mapboxNavigation!!
    }

    fun setNavigationActive(active: Boolean) {
        _isNavigationActive.value = active
    }

    fun setAppInForeground(foreground: Boolean) {
        _isAppInForeground.value = foreground
    }

    fun destroy() {
        if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.destroy()
        }
        _mapboxNavigation = null
        _isNavigationActive.value = false
    }
}