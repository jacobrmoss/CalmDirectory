package com.example.helloworld

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calmapps.directory.R
import com.mapbox.bindgen.Expected
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull

class NavigationOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var closeTargetView: ComposeView? = null
    private var originalX: Int = 0
    private var originalY: Int = 0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    private var maneuverApi: MapboxManeuverApi? = null
    private var maneuversResult: Expected<ManeuverError, List<Maneuver>>? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        userPreferencesRepository = UserPreferencesRepository(this)

        serviceScope.launch {
            val distanceUnit = userPreferencesRepository.distanceUnit.firstOrNull() ?: DistanceUnit.IMPERIAL
            val distanceFormatterOptions = DistanceFormatterOptions.Builder(this@NavigationOverlayService)
                .unitType(if (distanceUnit == DistanceUnit.IMPERIAL) UnitType.IMPERIAL else UnitType.METRIC)
                .build()
            val distanceFormatter = MapboxDistanceFormatter(distanceFormatterOptions)
            maneuverApi = MapboxManeuverApi(distanceFormatter)

            combine(
                NavigationManager.isAppInForeground,
                NavigationManager.isNavigationActive
            ) { isForeground, isActive ->
                !isForeground && isActive
            }.collect { shouldShow ->
                if (shouldShow) {
                    registerNavObserver()
                    if (overlayView == null) showOverlay()
                } else {
                    removeOverlayView()
                }
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        maneuverApi?.getManeuvers(routeProgress)?.let {
            maneuversResult = it
            overlayView?.setContent { OverlayContent() }
        }
    }

    private fun registerNavObserver() {
        NavigationManager.mapboxNavigation?.registerRouteProgressObserver(routeProgressObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
            originalX = 0
            originalY = y
        }

        val view = ComposeView(this).apply {
            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                OverlayContent()
            }
        }

        overlayLayoutParams = layoutParams

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @androidx.compose.runtime.Composable
    private fun OverlayContent() {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(2.dp, Color.Black, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { showCloseTarget() },
                        onDragEnd = { handleDragEnd() },
                        onDragCancel = { handleDragEnd() }
                    ) { change, dragAmount ->
                        change.consumeAllChanges()
                        moveOverlayBy(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.width(300.dp).wrapContentHeight(),
                factory = { ctx ->
                    val wrappedContext = ContextThemeWrapper(ctx, R.style.Theme_HelloWorld)
                    val view = LayoutInflater.from(wrappedContext).inflate(R.layout.view_maneuver, null, false) as MapboxManeuverView

                    val whiteRes = android.R.color.white
                    val blackStyle = R.style.ManeuverTextAppearance

                    val options = ManeuverViewOptions.Builder()
                        .maneuverBackgroundColor(whiteRes)
                        .subManeuverBackgroundColor(whiteRes)
                        .upcomingManeuverBackgroundColor(whiteRes)
                        .stepDistanceTextAppearance(blackStyle)
                        .primaryManeuverOptions(ManeuverPrimaryOptions.Builder().textAppearance(blackStyle).build())
                        .secondaryManeuverOptions(ManeuverSecondaryOptions.Builder().textAppearance(blackStyle).build())
                        .subManeuverOptions(ManeuverSubOptions.Builder().textAppearance(blackStyle).build())
                        .build()

                    view.updateManeuverViewOptions(options)

                    view.post {
                        val black = android.graphics.Color.BLACK
                        fun tintBlack(v: View) {
                            if (v is ImageView) v.setColorFilter(black)
                            else if (v is TextView) v.setTextColor(black)
                            else if (v is ViewGroup) (0 until v.childCount).forEach { tintBlack(v.getChildAt(it)) }
                        }
                        tintBlack(view)
                    }

                    // Long click to open the app
                    view.setOnLongClickListener {
                        val intent = Intent(this@NavigationOverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        true
                    }

                    view
                },
                update = { view ->
                    maneuversResult?.let { result ->
                        view.renderManeuvers(result)
                        view.visibility = if (result.value?.isNotEmpty() == true) View.VISIBLE else View.GONE
                    }
                }
            )
        }
    }

    private fun moveOverlayBy(dx: Float, dy: Float) {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    private fun showCloseTarget() {
        if (closeTargetView != null) return
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(200, 200, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }
        val view = ComposeView(this).apply {
            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(0.7f)).size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, tint = Color.White)
                }
            }
        }
        closeTargetView = view
        try { windowManager.addView(view, params) } catch (_: Exception) {}
    }

    private fun handleDragEnd() {
        val params = overlayLayoutParams
        val metrics = resources.displayMetrics
        if (params != null && params.y > metrics.heightPixels - 300) {
            NavigationManager.mapboxNavigation?.stopTripSession()
            NavigationManager.setNavigationActive(false)
            stopSelf()
        } else {
            params?.x = originalX
            params?.y = originalY
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        }
        closeTargetView?.let { windowManager.removeView(it) }
        closeTargetView = null
    }

    private fun removeOverlayView() {
        NavigationManager.mapboxNavigation?.unregisterRouteProgressObserver(routeProgressObserver)
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        hideCloseTarget()
    }

    private fun hideCloseTarget() {
        closeTargetView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        closeTargetView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}