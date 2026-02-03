package com.example.helloworld

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.calmapps.directory.R
import com.example.helloworld.data.DistanceUnit
import com.example.helloworld.data.UserPreferencesRepository
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.Value
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.Maneuver
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverError
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.view.ContextThemeWrapper

private enum class ScreenState {
    POI_OVERVIEW,
    ROUTE_PREVIEW,
    NAVIGATING
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(MapboxExperimental::class, ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    navController: NavController,
    poiName: String,
    poiAddress: String,
    isPlace: Boolean,
    poiLat: Double,
    poiLng: Double
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val distanceUnit by userPreferencesRepository.distanceUnit.collectAsState(initial = DistanceUnit.IMPERIAL)

    var currentUserLocation by remember { mutableStateOf<android.location.Location?>(null) }
    LaunchedEffect(Unit) {
        currentUserLocation = locationService.getBestLocationOrNull()
    }

    val mapboxNavigation = remember(context) {
        NavigationManager.getInstance(context)
    }

    DisposableEffect(mapboxNavigation) {
        onDispose {
            // Do NOT destroy here.
        }
    }

    var screenState by remember { mutableStateOf(ScreenState.POI_OVERVIEW) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var originLabel by remember { mutableStateOf("Locating...") }
    val markerBitmap = rememberMarkerBitmap()
    val puckBitmap = rememberLocationPuckBitmap()

    var maneuversResult by remember { mutableStateOf<Expected<ManeuverError, List<Maneuver>>?>(null) }

    var showOverlayPermissionSheet by remember { mutableStateOf(false) }
    val overlaySheetState = rememberModalBottomSheetMMDState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (showOverlayPermissionSheet && Settings.canDrawOverlays(context)) {
                    showOverlayPermissionSheet = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun setVolumeControl(streamType: Int) {
        context.findActivity()?.volumeControlStream = streamType
    }

    val speechApi = remember { MapboxSpeechApi(context, Locale.getDefault().toLanguageTag()) }
    val voiceInstructionsPlayer = remember { MapboxVoiceInstructionsPlayer(context, Locale.getDefault().toLanguageTag()) }

    val voiceInstructionsPlayerCallback = remember(context) {
        { announcement: SpeechAnnouncement ->
            speechApi.clean(announcement)
            setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)
        }
    }

    val speechCallback = remember(context, voiceInstructionsPlayerCallback) {
        { expected: Expected<SpeechError, SpeechValue> ->
            setVolumeControl(AudioManager.STREAM_MUSIC)
            expected.fold(
                { error -> voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback) },
                { value -> voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback) }
            )
        }
    }

    val voiceInstructionsObserver = remember(context, speechCallback) {
        VoiceInstructionsObserver { voiceInstructions ->
            speechApi.generate(voiceInstructions, speechCallback)
        }
    }

    val distanceFormatterOptions = remember(distanceUnit) {
        DistanceFormatterOptions.Builder(context)
            .unitType(
                if (distanceUnit == DistanceUnit.IMPERIAL) UnitType.IMPERIAL else UnitType.METRIC
            )
            .build()
    }
    val distanceFormatter = remember(distanceFormatterOptions) { MapboxDistanceFormatter(distanceFormatterOptions) }
    val maneuverApi = remember(distanceFormatter) { MapboxManeuverApi(distanceFormatter) }

    var routeTime by remember { mutableStateOf("") }
    var routeDistance by remember { mutableStateOf("") }
    var routeEta by remember { mutableStateOf("") }

    val routeLineColorResources = remember {
        RouteLineColorResources.Builder()
            .routeDefaultColor(AndroidColor.BLACK)
            .routeLineTraveledColor(AndroidColor.TRANSPARENT)
            .routeLowCongestionColor(AndroidColor.BLACK)
            .routeModerateCongestionColor(AndroidColor.BLACK)
            .routeHeavyCongestionColor(AndroidColor.BLACK)
            .routeSevereCongestionColor(AndroidColor.BLACK)
            .routeUnknownCongestionColor(AndroidColor.BLACK)
            .routeCasingColor(AndroidColor.BLACK)
            .alternativeRouteDefaultColor(AndroidColor.DKGRAY)
            .alternativeRouteCasingColor(AndroidColor.DKGRAY)
            .alternativeRouteLowCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteModerateCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteHeavyCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteSevereCongestionColor(AndroidColor.DKGRAY)
            .alternativeRouteUnknownCongestionColor(AndroidColor.DKGRAY)
            .inActiveRouteLegsColor(AndroidColor.DKGRAY)
            .build()
    }
    val routeLineApi = remember { MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build()) }

    val routeLineView = remember {
        MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(context)
                .routeLineColorResources(routeLineColorResources)
                .routeLineBelowLayerId(LocationComponentConstants.LOCATION_INDICATOR_LAYER)
                .build()
        )
    }
    val routeArrowApi = remember { MapboxRouteArrowApi() }
    val routeArrowView = remember { MapboxRouteArrowView(RouteArrowOptions.Builder(context).build()) }

    val navigationLocationProvider = remember { NavigationLocationProvider() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var navigationCamera by remember { mutableStateOf<NavigationCamera?>(null) }
    var viewportDataSource by remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    var isStyleLoaded by remember { mutableStateOf(false) }

    fun fetchRoute() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val bestLocation = locationService.getBestLocationOrNull()
                val originPoint = if (bestLocation != null) {
                    originLabel = "Current location"
                    Point.fromLngLat(bestLocation.longitude, bestLocation.latitude)
                } else {
                    originLabel = "Location unavailable"
                    null
                }

                if (originPoint == null) {
                    errorMessage = "Could not determine location."
                    isLoading = false
                    return@launch
                }

                val destinationPoint = Point.fromLngLat(poiLng, poiLat)

                val routeOptions = RouteOptions.builder()
                    .applyDefaultNavigationOptions()
                    .applyLanguageAndVoiceUnitOptions(context)
                    .coordinatesList(listOf(originPoint, destinationPoint))
                    .build()

                mapboxNavigation.requestRoutes(
                    routeOptions,
                    object : NavigationRouterCallback {
                        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                            if (routes.isNotEmpty()) {
                                mapboxNavigation.setNavigationRoutes(routes)
                            } else {
                                errorMessage = "No route found."
                            }
                            isLoading = false
                        }
                        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                            errorMessage = "Failed to fetch route."
                            isLoading = false
                        }
                        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                            isLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Error finding location: ${e.message}"
                isLoading = false
            }
        }
    }

    fun resetCameraToPoi() {
        mapView?.let { view ->
            view.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(poiLng, poiLat))
                    .zoom(15.0)
                    .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0))
                    .build()
            )
        }
    }

    if (mapView != null && isStyleLoaded) {
        DisposableEffect(mapboxNavigation, mapView!!, isStyleLoaded) {
            val mapboxMap = mapView!!.getMapboxMap()

            if (viewportDataSource == null || navigationCamera == null) {
                viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap).apply {
                    val density = context.resources.displayMetrics.density.toDouble()
                    followingPadding = EdgeInsets(120.0 * density, 40.0 * density, 120.0 * density, 40.0 * density)
                    overviewPadding = EdgeInsets(100.0 * density, 100.0 * density, 100.0 * density, 100.0 * density)
                }
                mapView?.let { view ->
                    navigationCamera = NavigationCamera(mapboxMap, view.camera, viewportDataSource!!)
                }

                mapView!!.location.apply {
                    enabled = true
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.COURSE
                    locationPuck = LocationPuck2D(
                        bearingImage = ImageHolder.from(puckBitmap),
                        shadowImage = null,
                        scaleExpression = null
                    )
                    setLocationProvider(navigationLocationProvider)
                }

                if (screenState == ScreenState.NAVIGATING) {
                    navigationCamera?.requestNavigationCameraToFollowing(
                        stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                            .maxDuration(0)
                            .build()
                    )
                } else if (screenState == ScreenState.POI_OVERVIEW) {
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(Point.fromLngLat(poiLng, poiLat))
                            .zoom(15.0)
                            .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0))
                            .build()
                    )
                }
            }

            val routesObserver = RoutesObserver { result ->
                speechApi.cancel()
                voiceInstructionsPlayer.clear()
                setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)

                routeLineApi.setNavigationRoutes(result.navigationRoutes) { drawData ->
                    mapView?.getMapboxMap()?.let { map ->
                        map.getStyle()?.let { style ->
                            routeLineView.renderRouteDrawData(style, drawData)
                        }
                    }
                }

                if (result.navigationRoutes.isNotEmpty()) {
                    val primaryRoute = result.navigationRoutes[0]
                    viewportDataSource?.onRouteChanged(primaryRoute)
                    viewportDataSource?.evaluate()

                    if (screenState == ScreenState.POI_OVERVIEW) {
                        screenState = ScreenState.ROUTE_PREVIEW
                        navigationCamera?.requestNavigationCameraToOverview()

                    } else if (screenState == ScreenState.ROUTE_PREVIEW) {
                        navigationCamera?.requestNavigationCameraToOverview()
                    }
                }
            }

            val locationObserver = object : LocationObserver {
                override fun onNewLocationMatcherResult(result: LocationMatcherResult) {
                    val enhanced = result.enhancedLocation
                    navigationLocationProvider.changePosition(enhanced, result.keyPoints)
                    viewportDataSource?.onLocationChanged(enhanced)
                    viewportDataSource?.evaluate()
                }
                override fun onNewRawLocation(rawLocation: Location) {}
            }

            val routeProgressObserver = RouteProgressObserver { routeProgress ->
                viewportDataSource?.onRouteProgressChanged(routeProgress)
                viewportDataSource?.evaluate()

                mapView?.getMapboxMap()?.getStyle()?.let { style ->
                    val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
                    routeArrowView.renderManeuverUpdate(style, arrowUpdate)
                }

                maneuversResult = maneuverApi.getManeuvers(routeProgress)

                val duration = routeProgress.durationRemaining
                val distance = routeProgress.distanceRemaining

                val hours = TimeUnit.SECONDS.toHours(duration.toLong())
                val minutes = TimeUnit.SECONDS.toMinutes(duration.toLong()) % 60
                val sb = StringBuilder()
                if (hours > 0) {
                    sb.append("$hours hr ")
                }
                sb.append("$minutes min")
                routeTime = sb.toString()

                routeDistance = distanceFormatter.formatDistance(distance.toDouble()).toString()

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.SECOND, duration.toInt())
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                routeEta = sdf.format(calendar.time)
            }

            mapboxNavigation.registerRoutesObserver(routesObserver)
            mapboxNavigation.registerLocationObserver(locationObserver)
            mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
            mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)

            val currentRoutes = mapboxNavigation.getNavigationRoutes()
            if (currentRoutes.isNotEmpty()) {
                routeLineApi.setNavigationRoutes(currentRoutes) { drawData ->
                    mapView?.getMapboxMap()?.getStyle()?.let { style ->
                        routeLineView.renderRouteDrawData(style, drawData)
                    }
                }
            }

            onDispose {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
                mapboxNavigation.setNavigationRoutes(emptyList())
                routeLineApi.clearRouteLine { }

                speechApi.cancel()
                voiceInstructionsPlayer.shutdown()
                setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)

                routeLineApi.cancel()
                routeLineView.cancel()
                maneuverApi.cancel()
                maneuversResult = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (screenState == ScreenState.NAVIGATING) 0.dp else 16.dp)
            ) {
                if (screenState == ScreenState.NAVIGATING) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
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
                            view
                        },
                        update = { view ->
                            maneuversResult?.let { result ->
                                view.renderManeuvers(result)
                                val list = result.value
                                view.visibility = if (list != null && list.isNotEmpty()) View.VISIBLE else View.GONE

                                view.post {
                                    val black = android.graphics.Color.BLACK
                                    fun tintBlack(v: View) {
                                        if (v is ImageView) v.setColorFilter(black)
                                        else if (v is TextView) v.setTextColor(black)
                                        else if (v is ViewGroup) (0 until v.childCount).forEach { tintBlack(v.getChildAt(it)) }
                                    }
                                    tintBlack(view)
                                }
                            } ?: run {
                                view.visibility = View.GONE
                            }
                        }
                    )
                } else if (screenState == ScreenState.ROUTE_PREVIEW || isLoading || screenState == ScreenState.POI_OVERVIEW) {
                    IconButton(onClick = {
                        when(screenState) {
                            ScreenState.POI_OVERVIEW -> navController.popBackStack()
                            ScreenState.ROUTE_PREVIEW -> {
                                mapboxNavigation.setNavigationRoutes(emptyList())
                                routeLineApi.clearRouteLine { }
                                screenState = ScreenState.POI_OVERVIEW
                                resetCameraToPoi()
                            }

                            else -> {null}
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (screenState == ScreenState.ROUTE_PREVIEW || isLoading) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Outlined.RadioButtonUnchecked, "Origin", tint = MaterialTheme.colorScheme.onSurface)
                                VerticalDottedLine(modifier = Modifier.width(2.dp).height(32.dp))
                                Icon(Icons.Outlined.Place, "Destination", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(originLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                HorizontalDividerMMD(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 16.dp))
                                Text(poiName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isPlace) {
                                Text(
                                    text = poiName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = poiAddress,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                currentUserLocation?.let { loc ->
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        loc.latitude,
                                        loc.longitude,
                                        poiLat,
                                        poiLng,
                                        results
                                    )
                                    val distanceInMeters = results[0]

                                    val distanceString = if (distanceUnit == DistanceUnit.IMPERIAL) {
                                        val miles = distanceInMeters * 0.000621371
                                        if (miles >= 0.1) {
                                            "%.1f mi".format(miles)
                                        } else {
                                            "%.0f ft".format(distanceInMeters * 3.28084)
                                        }
                                    } else {
                                        if (distanceInMeters >= 1000) {
                                            "%.1f km".format(distanceInMeters / 1000)
                                        } else {
                                            "%.0f m".format(distanceInMeters)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = distanceString,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                            } else {
                                Text(
                                    text = poiName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                currentUserLocation?.let { loc ->
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        loc.latitude,
                                        loc.longitude,
                                        poiLat,
                                        poiLng,
                                        results
                                    )
                                    val distanceInMeters = results[0]

                                    val distanceString = if (distanceUnit == DistanceUnit.IMPERIAL) {
                                        val miles = distanceInMeters * 0.000621371
                                        if (miles >= 0.1) {
                                            "%.1f mi".format(miles)
                                        } else {
                                            "%.0f ft".format(distanceInMeters * 3.28084)
                                        }
                                    } else {
                                        if (distanceInMeters >= 1000) {
                                            "%.1f km".format(distanceInMeters / 1000)
                                        } else {
                                            "%.0f m".format(distanceInMeters)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = distanceString,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDividerMMD(
                thickness = 3.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .padding(top = if (screenState == ScreenState.NAVIGATING) 0.dp else 16.dp)
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            MapboxMap(modifier = Modifier.fillMaxSize()) {
                MapEffect(Unit) { mapViewInstance ->
                    mapView = mapViewInstance
                    val styleUri = mapViewInstance.context.getString(R.string.mapbox_eink_style_uri)
                    mapViewInstance.getMapboxMap().apply {
                        if (screenState == ScreenState.POI_OVERVIEW) {
                            setCamera(CameraOptions.Builder().center(Point.fromLngLat(poiLng, poiLat)).zoom(15.0).build())
                        }

                        loadStyleUri(styleUri) { style ->
                            isStyleLoaded = true
                            style.styleLayers.forEach { layer ->
                                when (layer.type) {
                                    "background" -> style.setStyleLayerProperty(layer.id, "background-color", Value.valueOf("#FFFFFF"))
                                    "line" -> if (layer.id.startsWith("road")) {
                                        val color = if (layer.id.contains("motorway") || layer.id.contains("trunk")) "#222222" else "#777777"
                                        style.setStyleLayerProperty(layer.id, "line-color", Value.valueOf(color))
                                    }
                                    "symbol" -> if (layer.id.contains("label")) style.setStyleLayerProperty(layer.id, "text-color", Value.valueOf("#000000"))
                                }
                            }
                        }
                    }
                }

                if (markerBitmap != null) {
                    PointAnnotation(
                        point = Point.fromLngLat(poiLng, poiLat)
                    ) {
                        iconImage = IconImage(markerBitmap)
                        iconAnchor = IconAnchor.BOTTOM
                        iconSize = 1.0
                    }
                }
            }
        }

        if (screenState !== ScreenState.NAVIGATING) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Column(modifier = Modifier.padding(16.dp)) {
                        ButtonMMD(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                when (screenState) {
                                    ScreenState.POI_OVERVIEW -> fetchRoute()
                                    ScreenState.ROUTE_PREVIEW -> {
                                        if (Settings.canDrawOverlays(context)) {
                                            mapboxNavigation.startTripSession()
                                            screenState = ScreenState.NAVIGATING
                                            NavigationManager.setNavigationActive(true)
                                            context.startService(Intent(context, NavigationOverlayService::class.java))
                                            navigationCamera?.requestNavigationCameraToFollowing(
                                                stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                                                    .maxDuration(0)
                                                    .build()
                                            )
                                        } else {
                                            showOverlayPermissionSheet = true
                                        }
                                    } else -> {null}
                                }
                            },
                            enabled = !isLoading && errorMessage == null,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Text(
                                text = when (screenState) {
                                    ScreenState.POI_OVERVIEW -> "Get Directions"
                                    ScreenState.ROUTE_PREVIEW -> "Start Navigation"
                                    else -> {""}
                                }
                            )
                        }

                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        } else if (screenState == ScreenState.NAVIGATING) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                HorizontalDividerMMD(thickness = 3.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = routeTime,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = routeDistance,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = routeEta,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = {
                            mapboxNavigation.stopTripSession()
                            NavigationManager.setNavigationActive(false)
                            context.stopService(Intent(context, NavigationOverlayService::class.java))

                            speechApi.cancel()
                            voiceInstructionsPlayer.clear()
                            setVolumeControl(AudioManager.USE_DEFAULT_STREAM_TYPE)
                            mapView?.getMapboxMap()?.getStyle()?.let { style ->
                                routeArrowView.render(style, routeArrowApi.clearArrows())
                            }

                            NavigationManager.destroy()
                            screenState = ScreenState.ROUTE_PREVIEW
                            navigationCamera?.requestNavigationCameraToOverview()
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Clear,
                            "End Navigation",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }

    if (showOverlayPermissionSheet) {
        ModalBottomSheetMMD(
            onDismissRequest = { showOverlayPermissionSheet = false },
            sheetState = overlaySheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Permission Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "So you don't miss a turn while changing a song or taking a call, " +
                            "please enable this permission so Directory can display the next " +
                            "Maneuver over other apps.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                ButtonMMD(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("Open Settings")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButtonMMD(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showOverlayPermissionSheet = false },
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VerticalDottedLine(
    modifier: Modifier = Modifier,
    color: ComposeColor = MaterialTheme.colorScheme.outlineVariant
) {
    Canvas(modifier = modifier) {
        val dotRadius = 2.dp.toPx()
        val spacing = 4.dp.toPx()
        val centerX = size.width / 2f
        val dotDiameter = dotRadius * 2
        val edgePadding = dotDiameter
        var y = edgePadding
        val maxY = size.height - edgePadding
        while (y <= maxY) {
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(centerX, y)
            )
            y += dotDiameter + spacing
        }
    }
}

@Composable
fun rememberLocationPuckBitmap(): Bitmap {
    val context = LocalContext.current
    return remember(context) {
        val density = context.resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val bitmap = createBitmap(sizePx, sizePx)

        val canvas = AndroidCanvas(bitmap)

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val outlineWidth = 4 * density
        val radius = (sizePx / 2f) - (outlineWidth / 2)

        val circlePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = AndroidColor.WHITE
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = AndroidColor.BLACK
            strokeWidth = outlineWidth
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val arrowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = AndroidColor.BLACK
        }

        val path = Path()
        val arrowSize = radius * 1.2f

        path.moveTo(cx, cy - (arrowSize / 2))
        path.lineTo(cx + (arrowSize / 2.5f), cy + (arrowSize / 2))
        path.lineTo(cx, cy + (arrowSize / 4))
        path.lineTo(cx - (arrowSize / 2.5f), cy + (arrowSize / 2))
        path.close()

        canvas.drawPath(path, arrowPaint)
        bitmap
    }
}

@Composable
fun rememberMarkerBitmap(): Bitmap? {
    val context = LocalContext.current
    return remember(context) {
        val width = 64
        val height = 76
        val bitmap = createBitmap(width, height)
        val canvas = AndroidCanvas(bitmap)

        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = AndroidColor.WHITE
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = AndroidColor.BLACK
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = AndroidColor.BLACK
        }

        val cx = width / 2f
        val cy = width / 2f - 4f
        val radius = 24f
        val tipY = height - 4f

        val path = Path()

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        path.arcTo(rect, 150f, 240f)
        path.lineTo(cx, tipY)

        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(cx, cy, 8f, dotPaint)

        bitmap
    }
}