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

    /**
     * Default broad category set used for free-text searches when we don't have
     * a specific known category (e.g. user typed "Target").
     */
    private val defaultCategories =
        "catering,commercial,service,entertainment,leisure,accommodation,amenity"

    /**
     * Internal mapping between high-level conceptual categories (as surfaced on the
     * landing screen or typed by the user) and the concrete Geoapify `categories`
     * parameter values.
     */
    private val categoryMappings: List<CategoryMapping> = listOf(
        CategoryMapping(
            labels = setOf("gas stations", "gas station", "fuel"),
            geoapifyCategories = "commercial.gas,service.vehicle.fuel"
        ),
        CategoryMapping(
            labels = setOf("restaurants", "restaurant", "food"),
            geoapifyCategories = "catering.restaurant"
        ),
        CategoryMapping(
            labels = setOf("entertainment"),
            geoapifyCategories = "entertainment"
        ),
        CategoryMapping(
            labels = setOf("coffee", "coffee shops", "coffee shop", "cafe", "cafes"),
            geoapifyCategories = "catering.cafe"
        ),
        CategoryMapping(
            labels = setOf("shopping", "shops", "store", "stores"),
            geoapifyCategories = "commercial"
        ),
        CategoryMapping(
            labels = setOf("hotels", "hotel", "lodging"),
            geoapifyCategories = "accommodation.hotel"
        )
    )

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

    /**
     * Attempts to map a raw query string (either from the landing screen category
     * or from user input) into a concrete Geoapify `categories` string.
     *
     * The matching is intentionally strict (exact match on a small set of
     * normalized labels) so that free-form searches like "restaurants in NYC"
     * are treated as text queries, not as category shortcuts.
     */
    private fun mapQueryToCategories(query: String): String? {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return null

        return categoryMappings.firstOrNull { mapping ->
            normalized in mapping.labels
        }?.geoapifyCategories
    }

    override suspend fun search(query: String, lat: Double, lon: Double): List<Poi> {
        val apiKey = getApiKey() ?: return emptyList()

        return try {
            val radiusMiles = userPreferencesRepository.searchRadius.first()
            // Convert miles to meters (approx) and cap to ~10km to avoid huge search areas
            val radiusMeters = (radiusMiles * 1609).coerceAtMost(10_000)
            val trimmedQuery = query.trim()
            val categoriesForQuery = mapQueryToCategories(trimmedQuery)

            val httpStart = System.currentTimeMillis()
            val response: GeoapifyPlacesResponse = client.get("https://api.geoapify.com/v2/places") {
                parameter("apiKey", apiKey)
                // Use specific categories for known home screen categories when possible,
                // otherwise fall back to a broad category set that covers typical POIs.
                val categories = categoriesForQuery ?: defaultCategories
                parameter("categories", categories)
                parameter("limit", 30)
                if (trimmedQuery.isNotEmpty() && categoriesForQuery == null) {
                    // Narrow results on the server side when possible for free-text queries.
                    parameter("name", trimmedQuery)
                }
                if (lat != 0.0 || lon != 0.0) {
                    // Constrain search area with a circle and sort by proximity within it
                    parameter("filter", "circle:$lon,$lat,$radiusMeters")
                    parameter("bias", "proximity:$lon,$lat")
                }
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart

            Log.d(
                "GeoapifyPlacesApiService",
                "search query='${trimmedQuery}' lat=$lat lon=$lon radiusMeters=$radiusMeters categories='${categoriesForQuery}' features=${response.features.size} httpMs=$httpDuration"
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

            // For category-based queries (landing screen shortcuts), return all POIs in
            // that category and radius. For free-text queries, filter client-side by
            // name/description.
            if (categoriesForQuery != null) {
                allPois
            } else if (trimmedQuery.isEmpty()) {
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

private data class CategoryMapping(
    val labels: Set<String>,
    val geoapifyCategories: String
)

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