package com.example.helloworld

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.calmapps.directory.R
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.Marker
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(MapboxExperimental::class)
@Composable
fun MapScreen(
    navController: NavController,
    poiName: String,
    poiLat: Double,
    poiLng: Double
) {
    val context = LocalContext.current
    var styleLoaded by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    
    // Ensure access token is set from manifest on first composition
    LaunchedEffect(Unit) {
        try {
            val accessToken = context.getString(R.string.mapbox_access_token)
            if (accessToken.isNotEmpty()) {
                com.mapbox.common.MapboxOptions.accessToken = accessToken
                Log.d("MapScreen", "Access token initialized: ${accessToken.take(10)}...")
                mapReady = true
            } else {
                Log.e("MapScreen", "Mapbox access token is empty!")
            }
        } catch (e: Exception) {
            Log.e("MapScreen", "Failed to load access token", e)
        }
    }
    
    val mapViewportState = rememberMapViewportState {
        setCameraOptions(
            CameraOptions.Builder()
                .center(Point.fromLngLat(poiLng, poiLat))
                .zoom(15.0)
                .build()
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
            ) {
                // Ensure we always use the E-ink optimized (grayscale) Mapbox style
                // and then override key colors for E-ink readability.
                MapEffect(mapReady) { mapView ->
                    if (!mapReady) return@MapEffect
                    val styleUri = context.getString(R.string.mapbox_eink_style_uri)
                    val mapboxMap = mapView.getMapboxMap()
                    Log.d("MapScreen", "MapEffect: Loading style from: $styleUri")
                    mapboxMap.loadStyleUri(styleUri) { style ->
                        try {
                            Log.d("MapScreen", "MapEffect: Style loaded, applying properties")
                            styleLoaded = true
                            // White base/background layers
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
                                        // Road layers in darker greys based on type
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
                                        // Labels in pure black for maximum contrast
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
                        } catch (e: Exception) {
                            Log.e("MapScreen", "Error applying style properties", e)
                        }
                    }
                }

                Marker(
                    point = Point.fromLngLat(poiLng, poiLat),
                    color = Color.White,
                    stroke = Color.Black,
                    innerColor = Color.White
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
                    modifier = Modifier.padding(
                        horizontal = 16.dp, vertical = 24.dp
                    ),
                ) {
                    Text(
                        text = poiName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ButtonMMD(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        onClick = {
                            val encodedName = URLEncoder.encode(poiName, StandardCharsets.UTF_8.toString())
                            navController.navigate(
                                "navigation?poiName=$encodedName&lat=${poiLat.toFloat()}&lng=${poiLng.toFloat()}"
                            )
                        }
                    ) {
                        Text(text = "Get directions")
                    }
                }
            }
        }
    }
}
