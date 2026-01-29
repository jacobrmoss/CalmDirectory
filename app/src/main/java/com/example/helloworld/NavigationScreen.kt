package com.example.helloworld

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.calmapps.directory.R
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.Value
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
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
import kotlinx.coroutines.launch
import java.util.Locale
import android.graphics.RectF
import android.graphics.Path
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Clear

private enum class ScreenState {
    POI_OVERVIEW,
    ROUTE_PREVIEW,
    NAVIGATING
}

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
    val scope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf(ScreenState.POI_OVERVIEW) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var originLabel by remember { mutableStateOf("Locating...") }
    val markerBitmap = rememberMarkerBitmap()
    val speechApi = remember { MapboxSpeechApi(context, Locale.getDefault().toLanguageTag()) }
    val voiceInstructionsPlayer = remember { MapboxVoiceInstructionsPlayer(context, Locale.getDefault().toLanguageTag()) }
    val voiceInstructionsPlayerCallback = remember {
        MapboxNavigationConsumer<SpeechAnnouncement> { announcement -> speechApi.clean(announcement) }
    }
    val speechCallback = remember {
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error -> voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback) },
                { value -> voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback) }
            )
        }
    }
    val voiceInstructionsObserver = remember {
        VoiceInstructionsObserver { voiceInstructions -> speechApi.generate(voiceInstructions, speechCallback) }
    }

    val distanceFormatterOptions = remember { DistanceFormatterOptions.Builder(context).build() }
    val maneuverApi = remember { MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions)) }
    var maneuverView by remember { mutableStateOf<MapboxManeuverView?>(null) }

    val routeLineColorResources = remember {
        RouteLineColorResources.Builder()
            .routeDefaultColor(AndroidColor.BLACK)
            .routeLineTraveledColor(AndroidColor.BLACK)
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
        if (mapboxNavigation == null) return

        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val locationService = LocationService(context)
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

    if (mapboxNavigation != null && mapView != null && isStyleLoaded) {
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
                    locationPuck = LocationPuck2D()
                    setLocationProvider(navigationLocationProvider)
                }
            }

            val routesObserver = RoutesObserver { result ->
                speechApi.cancel()
                voiceInstructionsPlayer.clear()

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

                val maneuvers = maneuverApi.getManeuvers(routeProgress)
                maneuvers.fold({}, { list ->
                    if (list.isNotEmpty()) maneuverView?.visibility = View.VISIBLE
                })
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
                mapboxNavigation.setNavigationRoutes(emptyList())
                routeLineApi.clearRouteLine { }

                speechApi.cancel()
                voiceInstructionsPlayer.shutdown()
                routeLineApi.cancel()
                routeLineView.cancel()
                maneuverApi.cancel()
                maneuverView = null
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
                            val view = LayoutInflater.from(ctx).inflate(R.layout.view_maneuver, null, false) as MapboxManeuverView
                            val white = android.R.color.white
                            val blackStyle = R.style.ManeuverTextAppearance
                            val options = ManeuverViewOptions.Builder()
                                .maneuverBackgroundColor(white)
                                .subManeuverBackgroundColor(white)
                                .upcomingManeuverBackgroundColor(white)
                                .stepDistanceTextAppearance(blackStyle)
                                .primaryManeuverOptions(ManeuverPrimaryOptions.Builder().textAppearance(blackStyle).build())
                                .secondaryManeuverOptions(ManeuverSecondaryOptions.Builder().textAppearance(blackStyle).build())
                                .subManeuverOptions(ManeuverSubOptions.Builder().textAppearance(blackStyle).build())
                                .build()
                            view.updateManeuverViewOptions(options)
                            view.post {
                                fun tintBlack(v: View) {
                                    if (v is ImageView) v.setColorFilter(AndroidColor.BLACK)
                                    else if (v is ViewGroup) (0 until v.childCount).forEach { tintBlack(v.getChildAt(it)) }
                                }
                                tintBlack(view)
                            }
                            maneuverView = view
                            view
                        }
                    )
                } else if (screenState == ScreenState.ROUTE_PREVIEW || isLoading) {
                    IconButton(onClick = {
                        when(screenState) {
                            ScreenState.POI_OVERVIEW -> navController.popBackStack()
                            ScreenState.ROUTE_PREVIEW -> {
                                mapboxNavigation?.setNavigationRoutes(emptyList())
                                routeLineApi.clearRouteLine { }
                                screenState = ScreenState.POI_OVERVIEW
                                resetCameraToPoi()
                            }
                            ScreenState.NAVIGATING -> {
                                mapboxNavigation?.stopTripSession()
                                screenState = ScreenState.ROUTE_PREVIEW
                                navigationCamera?.requestNavigationCameraToOverview()
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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
                        setCamera(CameraOptions.Builder().center(Point.fromLngLat(poiLng, poiLat)).zoom(15.0).build())
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
                                        mapboxNavigation?.startTripSession()
                                        screenState = ScreenState.NAVIGATING
                                        navigationCamera?.requestNavigationCameraToFollowing()
                                    }
                                    ScreenState.NAVIGATING -> {
                                        mapboxNavigation?.stopTripSession()
                                        screenState = ScreenState.ROUTE_PREVIEW
                                        navigationCamera?.requestNavigationCameraToOverview()
                                    }
                                }
                            },
                            enabled = !isLoading && errorMessage == null,
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Text(
                                text = when (screenState) {
                                    ScreenState.POI_OVERVIEW -> "Get Directions"
                                    ScreenState.ROUTE_PREVIEW -> "Start Navigation"
                                    ScreenState.NAVIGATING -> "End Navigation"
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

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {

                    IconButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = {
                            mapboxNavigation?.stopTripSession()
                            screenState = ScreenState.ROUTE_PREVIEW
                            navigationCamera?.requestNavigationCameraToOverview()
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Clear,
                            "End Navigation",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // add route time here
                    // add route distance here
                    // add eta here

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

@Composable
fun rememberMarkerBitmap(): Bitmap? {
    val context = LocalContext.current
    return remember(context) {
        val width = 64
        val height = 76
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paints
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