package com.example.helloworld

import android.util.Log
import com.example.helloworld.data.UserPreferencesRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GeoapifyPlacesApiService(
    private val userPreferencesRepository: UserPreferencesRepository
) : PlacesBackend {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    private suspend fun getApiKey(): String? {
        val key = userPreferencesRepository.geoapifyApiKey.first()
        if (key.isNullOrEmpty()) {
            Log.e("GeoapifyPlacesApiService", "Geoapify API key not configured")
            return null
        }
        return key
    }

    override suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val radiusMiles = userPreferencesRepository.searchRadius.first()
            // Convert miles to meters (approx) and cap to ~10km to avoid huge search areas
            val radiusMeters = (radiusMiles * 1609).coerceAtMost(10_000)
            val trimmedQuery = query.trim()

            val httpStart = System.currentTimeMillis()
            val response: GeoapifyPlacesResponse = client.get("https://api.geoapify.com/v2/places") {
                parameter("apiKey", apiKey)
                // Use broad categories to cover typical POIs
                parameter(
                    "categories",
                    "catering,commercial,service,entertainment,leisure,accommodation,amenity"
                )
                parameter("limit", 30)
                if (trimmedQuery.isNotEmpty()) {
                    // Narrow results on the server side when possible
                    parameter("name", trimmedQuery)
                }
                if (lat != 0.0 || lon != 0.0) {
                    // Prefer proximity bias without an explicit circle filter to reduce backend load
                    parameter("bias", "proximity:$lon,$lat")
                }
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart

            Log.d(
                "GeoapifyPlacesApiService",
                "search query='${trimmedQuery}' lat=$lat lon=$lon radiusMeters=$radiusMeters features=${response.features.size} httpMs=$httpDuration"
            )

            val allPois = response.features.mapNotNull { feature ->
                val props = feature.properties
                val name = props.name ?: props.street ?: return@mapNotNull null

                val address = Address(
                    street = listOfNotNull(props.street, props.housenumber)
                        .joinToString(" ")
                        .ifBlank { "" },
                    city = props.city ?: "",
                    state = props.state ?: "",
                    zip = props.postcode ?: "",
                    country = props.country ?: ""
                )

                Poi(
                    name = name,
                    address = address,
                    hours = emptyList(),
                    phone = props.phone,
                    description = props.categories?.joinToString(", ") ?: "",
                    website = props.website,
                    lat = feature.geometry.coordinates.getOrNull(1),
                    lng = feature.geometry.coordinates.getOrNull(0)
                )
            }

            // If the user entered a query, filter client-side by name/description
            if (trimmedQuery.isEmpty()) {
                allPois
            } else {
                val q = trimmedQuery.lowercase()
                allPois.filter { poi ->
                    poi.name.lowercase().contains(q) ||
                            poi.description.lowercase().contains(q)
                }
            }
        } catch (e: CancellationException) {
            // Expected when the coroutine scope is cancelled (e.g. new query typed)
            Log.d("GeoapifyPlacesApiService", "Search cancelled", e)
            throw e
        } catch (e: Exception) {
            Log.e("GeoapifyPlacesApiService", "Error searching Geoapify places", e)
            emptyList()
        }
    }

    override suspend fun autocomplete(query: String): List<String> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val response: GeoapifyAutocompleteResponse = client.get("https://api.geoapify.com/v1/geocode/autocomplete") {
                parameter("apiKey", apiKey)
                parameter("text", query)
            }.body()

            response.features.mapNotNull { it.properties.formatted }
        } catch (e: Exception) {
            Log.e("GeoapifyPlacesApiService", "Error getting Geoapify autocomplete", e)
            emptyList()
        }
    }
}

@Serializable
private data class GeoapifyPlacesResponse(
    val features: List<GeoapifyPlaceFeature> = emptyList()
)

@Serializable
private data class GeoapifyPlaceFeature(
    val properties: GeoapifyPlaceProperties,
    val geometry: GeoapifyGeometry
)

@Serializable
private data class GeoapifyPlaceProperties(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val categories: List<String>? = null
)

@Serializable
private data class GeoapifyGeometry(
    val coordinates: List<Double>
)

@Serializable
private data class GeoapifyAutocompleteResponse(
    val features: List<GeoapifyAutocompleteFeature> = emptyList()
)

@Serializable
private data class GeoapifyAutocompleteFeature(
    val properties: GeoapifyAutocompleteProperties
)

@Serializable
private data class GeoapifyAutocompleteProperties(
    val formatted: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    @SerialName("postcode") val postcode: String? = null
)