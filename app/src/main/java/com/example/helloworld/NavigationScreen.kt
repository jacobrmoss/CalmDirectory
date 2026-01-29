package com.example.helloworld

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.calmapps.directory.R
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Value
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.Marker
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.bindgen.Expected
import com.mapbox.common.location.Location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.directions.session.RoutesUpdatedResult
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import java.util.Locale

/**
 * Basic navigation screen.
 */
@OptIn(MapboxExperimental::class)
@Composable
fun NavigationScreen(
    navController: NavController,
    poiName: String,
    poiLat: Double,
    poiLng: Double
) {
    val context = LocalContext.current
    val mapboxNavigation = MapboxNavigationApp.current()

    val speechApi = remember {
        MapboxSpeechApi(
            context,
            Locale.getDefault().toLanguageTag()
        )
    }
    val voiceInstructionsPlayer = remember {
        MapboxVoiceInstructionsPlayer(
            context,
            Locale.getDefault().toLanguageTag()
        )
    }

    val voiceInstructionsPlayerCallback = remember {
        MapboxNavigationConsumer<SpeechAnnouncement> { announcement ->
            speechApi.clean(announcement)
        }
    }

    val speechCallback = remember {
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback)
                },
                { value ->
                    voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback)
                }
            )
        }
    }

    val voiceInstructionsObserver = remember {
        VoiceInstructionsObserver { voiceInstructions ->
            speechApi.generate(voiceInstructions, speechCallback)
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasRoute by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var originLabel by remember { mutableStateOf("Locating current position…") }

    val distanceFormatterOptions = remember {
        DistanceFormatterOptions.Builder(context).build()
    }
    val maneuverApi = remember {
        MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))
    }
    var maneuverView by remember { mutableStateOf<MapboxManeuverView?>(null) }

    val routeLineColorResources = remember {
        RouteLineColorResources.Builder()
            .routeDefaultColor(Color.BLACK)
            .routeLineTraveledColor(Color.BLACK)
            .routeLowCongestionColor(Color.BLACK)
            .routeModerateCongestionColor(Color.BLACK)
            .routeHeavyCongestionColor(Color.BLACK)
            .routeSevereCongestionColor(Color.BLACK)
            .routeUnknownCongestionColor(Color.BLACK)
            .routeCasingColor(Color.BLACK)
            .alternativeRouteDefaultColor(Color.DKGRAY)
            .alternativeRouteCasingColor(Color.DKGRAY)
            .alternativeRouteLowCongestionColor(Color.DKGRAY)
            .alternativeRouteModerateCongestionColor(Color.DKGRAY)
            .alternativeRouteHeavyCongestionColor(Color.DKGRAY)
            .alternativeRouteSevereCongestionColor(Color.DKGRAY)
            .alternativeRouteUnknownCongestionColor(Color.DKGRAY)
            .inActiveRouteLegsColor(Color.DKGRAY)
            .build()
    }

    val routeLineApi = remember {
        MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
    }
    val routeLineView = remember {
        MapboxRouteLineView(
            MapboxRouteLineViewOptions
                .Builder(context)
                .routeLineColorResources(routeLineColorResources)
                .build()
        )
    }

    val routeArrowApi = remember { MapboxRouteArrowApi() }
    val routeArrowView = remember {
        val arrowOptions = RouteArrowOptions.Builder(context).build()
        MapboxRouteArrowView(arrowOptions)
    }

    val navigationLocationProvider = remember { NavigationLocationProvider() }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var navigationCamera by remember { mutableStateOf<NavigationCamera?>(null) }
    var viewportDataSource by remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    var isStyleLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapboxNavigation) {
        try {
            isLoading = true
            errorMessage = null

            if (mapboxNavigation == null) {
                errorMessage = "Navigation engine is not initialized."
                isLoading = false
                return@LaunchedEffect
            }

            val locationService = LocationService(context)
            val bestLocation = locationService.getBestLocationOrNull()
            val originPoint = if (bestLocation != null) {
                originLabel = "Current location"
                Point.fromLngLat(bestLocation.longitude, bestLocation.latitude)
            } else {
                originLabel = "Location unavailable (using destination as start)"
                Point.fromLngLat(poiLng, poiLat)
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
                    override fun onRoutesReady(
                        routes: List<NavigationRoute>,
                        routerOrigin: String
                    ) {
                        if (routes.isNotEmpty()) {
                            mapboxNavigation.setNavigationRoutes(routes)
                            hasRoute = true
                        } else {
                            errorMessage = "No route found to destination."
                        }
                        isLoading = false
                    }

                    override fun onFailure(
                        reasons: List<RouterFailure>,
                        routeOptions: RouteOptions
                    ) {
                        errorMessage = "Failed to fetch route."
                        isLoading = false
                    }

                    override fun onCanceled(
                        routeOptions: RouteOptions,
                        routerOrigin: String
                    ) {
                        errorMessage = "Route request was canceled."
                        isLoading = false
                    }
                }
            )
        } catch (_: SecurityException) {
            errorMessage = "Location permission not granted. Enable location or set a default location in Settings."
            originLabel = "Location permission not granted"
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to resolve origin location."
            originLabel = "Location unavailable"
            isLoading = false
        }
    }

    LaunchedEffect(isStyleLoaded, hasRoute, isNavigating, navigationCamera, viewportDataSource, mapView) {
        val camera = navigationCamera
        val viewport = viewportDataSource
        val map = mapView
        if (isStyleLoaded && hasRoute && !isNavigating && camera != null && viewport != null && map != null) {
            val mapboxNav = MapboxNavigationApp.current()
            if (mapboxNav != null) {
                val routes = mapboxNav.getNavigationRoutes()
                if (routes.isNotEmpty()) {
                    viewport.onRouteChanged(routes[0])
                    viewport.evaluate()

                    val route = routes[0].directionsRoute
                    val coordinates = route.geometry()?.let { geometry ->
                        com.mapbox.geojson.LineString.fromPolyline(geometry, 6).coordinates()
                    }

                    if (coordinates != null && coordinates.isNotEmpty()) {
                        val pixelDensity = context.resources.displayMetrics.density.toDouble()
                        val padding = EdgeInsets(
                            100.0 * pixelDensity,
                            100.0 * pixelDensity,
                            100.0 * pixelDensity,
                            100.0 * pixelDensity
                        )

                        val cameraOptions = map.getMapboxMap().cameraForCoordinates(coordinates, padding)

                        map.camera.easeTo(
                            cameraOptions,
                            com.mapbox.maps.plugin.animation.MapAnimationOptions.mapAnimationOptions {
                                duration(1500)
                            }
                        )
                        Log.d("NavigationScreen", "Animating camera to fit route geometry")
                    }
                }
            }
        }
    }

    if (mapboxNavigation != null && mapView != null && isStyleLoaded) {
        DisposableEffect(mapboxNavigation, mapView!!, isStyleLoaded) {
            val mapboxMap = mapView!!.getMapboxMap()

            if (viewportDataSource == null || navigationCamera == null) {
                viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap).apply {
                    val pixelDensity = context.resources.displayMetrics.density.toDouble()

                    followingPadding = EdgeInsets(
                        120.0 * pixelDensity,
                        40.0 * pixelDensity,
                        120.0 * pixelDensity,
                        40.0 * pixelDensity
                    )

                    overviewPadding = EdgeInsets(
                        200.0 * pixelDensity,
                        60.0 * pixelDensity,
                        200.0 * pixelDensity,
                        60.0 * pixelDensity
                    )
                }
                navigationCamera = NavigationCamera(
                    mapboxMap,
                    mapView!!.camera,
                    viewportDataSource!!
                )

                mapView!!.location.updateSettings {
                    enabled = true
                    locationPuck = LocationPuck2D()
                }
                mapView!!.location.setLocationProvider(navigationLocationProvider)
            }

            val routesObserver = RoutesObserver { result: RoutesUpdatedResult ->
                speechApi.cancel()
                voiceInstructionsPlayer.clear()

                routeLineApi.setNavigationRoutes(result.navigationRoutes) { drawData ->
                    mapView?.getMapboxMap()?.getStyle()?.let { style ->
                        routeLineView.renderRouteDrawData(style, drawData)
                    }
                }

                if (result.navigationRoutes.isNotEmpty()) {
                    val primaryRoute = result.navigationRoutes[0]
                    viewportDataSource?.onRouteChanged(primaryRoute)
                    viewportDataSource?.evaluate()

                    if (!isNavigating) {
                        navigationCamera?.requestNavigationCameraToOverview()
                    }
                } else {
                    viewportDataSource?.evaluate()
                }
            }

            val locationObserver = object : LocationObserver {
                override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                    val enhanced = locationMatcherResult.enhancedLocation
                    navigationLocationProvider.changePosition(
                        enhanced,
                        locationMatcherResult.keyPoints
                    )
                    viewportDataSource?.onLocationChanged(enhanced)
                    viewportDataSource?.evaluate()
                }

                override fun onNewRawLocation(rawLocation: Location) {
                    // Not used; NavigationLocationProvider uses matcher result above.
                }
            }

            val routeProgressObserver = RouteProgressObserver { routeProgress ->
                viewportDataSource?.onRouteProgressChanged(routeProgress)
                viewportDataSource?.evaluate()

                mapView?.getMapboxMap()?.getStyle()?.let { style ->
                    val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
                    routeArrowView.renderManeuverUpdate(style, arrowUpdate)
                }

                val maneuvers = maneuverApi.getManeuvers(routeProgress)
                maneuvers.fold(
                    { /* ignore errors for now */ },
                    { maneuverList ->
                        if (maneuverList.isNotEmpty()) {
                            maneuverView?.visibility = View.VISIBLE
                        }
                    }
                )
                maneuverView?.renderManeuvers(maneuvers)
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
                speechApi.cancel()
                voiceInstructionsPlayer.shutdown()
                routeLineApi.cancel()
                routeLineView.cancel()
                maneuverApi.cancel()
                maneuverView = null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                if (isNavigating) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            val view = LayoutInflater.from(ctx)
                                .inflate(R.layout.view_maneuver, null, false) as MapboxManeuverView

                            val whiteColorRes = android.R.color.white
                            val blackTextStyle = R.style.ManeuverTextAppearance

                            val options = ManeuverViewOptions.Builder()
                                .maneuverBackgroundColor(whiteColorRes)
                                .subManeuverBackgroundColor(whiteColorRes)
                                .upcomingManeuverBackgroundColor(whiteColorRes)
                                .stepDistanceTextAppearance(blackTextStyle)
                                .primaryManeuverOptions(
                                    ManeuverPrimaryOptions.Builder()
                                        .textAppearance(blackTextStyle)
                                        .build()
                                )
                                .secondaryManeuverOptions(
                                    ManeuverSecondaryOptions.Builder()
                                        .textAppearance(blackTextStyle)
                                        .build()
                                )
                                .subManeuverOptions(
                                    ManeuverSubOptions.Builder()
                                        .textAppearance(blackTextStyle)
                                        .build()
                                )
                                .build()

                            view.updateManeuverViewOptions(options)

                            view.post {
                                fun tintIconsBlack(v: View) {
                                    if (v is ImageView) {
                                        v.setColorFilter(Color.BLACK)
                                    } else if (v is ViewGroup) {
                                        for (i in 0 until v.childCount) {
                                            tintIconsBlack(v.getChildAt(i))
                                        }
                                    }
                                }
                                tintIconsBlack(view)
                            }

                            view.visibility = View.INVISIBLE
                            maneuverView = view
                            view
                        }
                    )
                } else {
                    IconButton(
                        onClick = { navController.popBackStack() },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Origin",
                                tint = MaterialTheme.colorScheme.onSurface
                            )

                            VerticalDottedLine(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(32.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Icon(
                                imageVector = Icons.Outlined.Place,
                                contentDescription = "Destination",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = originLabel,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 2.dp),
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            HorizontalDividerMMD(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )

                            Text(
                                text = poiName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 2.dp),
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDividerMMD(
                thickness = 3.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MapboxMap(
                modifier = Modifier.fillMaxSize()
            ) {
                MapEffect(Unit) { mapViewInstance ->
                    mapView = mapViewInstance
                    val styleUri = mapViewInstance.context.getString(R.string.mapbox_eink_style_uri)
                    val mapboxMap = mapViewInstance.getMapboxMap()

                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(Point.fromLngLat(poiLng, poiLat))
                            .zoom(12.0)
                            .build()
                    )
                    Log.d("NavigationScreen", "MapEffect: Loading style from: $styleUri")
                    mapboxMap.loadStyleUri(styleUri) { style ->
                        Log.d("NavigationScreen", "MapEffect: Style loaded successfully")
                        isStyleLoaded = true
                        style.styleLayers.forEach { layerInfo ->
                            when (layerInfo.type) {
                                "background" -> {
                                    style.setStyleLayerProperty(
                                        layerInfo.id,
                                        "background-color",
                                        Value.valueOf("#FFFFFF")
                                    )
                                }

                                "line" -> {
                                    if (layerInfo.id.startsWith("road")) {
                                        val color = when {
                                            layerInfo.id.contains("motorway") ||
                                                    layerInfo.id.contains("trunk") -> "#222222"

                                            layerInfo.id.contains("primary") ||
                                                    layerInfo.id.contains("secondary") -> "#444444"

                                            else -> "#777777"
                                        }
                                        style.setStyleLayerProperty(
                                            layerInfo.id,
                                            "line-color",
                                            Value.valueOf(color)
                                        )
                                    }
                                }

                                "symbol" -> {
                                    if (layerInfo.id.contains("label")) {
                                        style.setStyleLayerProperty(
                                            layerInfo.id,
                                            "text-color",
                                            Value.valueOf("#000000")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Marker(
                    point = Point.fromLngLat(poiLng, poiLat),
                    color = androidx.compose.ui.graphics.Color.White,
                    stroke = androidx.compose.ui.graphics.Color.Black,
                    innerColor = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDividerMMD(
                    thickness = 3.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    ButtonMMD(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val mapboxNavigation = MapboxNavigationApp.current()
                            if (mapboxNavigation != null && hasRoute) {
                                if (!isNavigating) {
                                    mapboxNavigation.startTripSession()
                                    isNavigating = true
                                    navigationCamera?.requestNavigationCameraToFollowing()
                                } else {
                                    mapboxNavigation.stopTripSession()
                                    isNavigating = false
                                    navController.popBackStack()
                                }
                            }
                        },
                        enabled = !isLoading && errorMessage == null && hasRoute,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Text(if (isNavigating) "End navigation" else "Start navigation")
                    }

                    Spacer(modifier = Modifier.padding(8.dp))
                }
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