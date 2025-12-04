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

    private val defaultCategories =
        "catering,commercial,service,entertainment,leisure,accommodation,amenity"

    @Volatile
    private var selectedTopLevelCategory: String? = null

    fun setTopLevelCategory(category: String?) {
        selectedTopLevelCategory = category?.takeIf { it.isNotBlank() }
    }

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

    private fun getApiKey(): String? {
        val key = BuildConfig.GEOAPIFY_API_KEY
        if (key.isNullOrEmpty()) {
            Log.e("GeoapifyPlacesApiService", "Geoapify API key not configured")
            return null
        }
        return key
    }

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
            val radiusMeters = (radiusMiles * 1609).coerceAtMost(10_000)
            val trimmedQuery = query.trim()
            val categoriesForQuery = mapQueryToCategories(trimmedQuery)

            // Treat (0,0) as an invalid location and avoid issuing a global, unconstrained query.
            if (lat == 0.0 && lon == 0.0) {
                Log.w(
                    "GeoapifyPlacesApiService",
                    "Skipping search: invalid location (0,0); configure device or default location"
                )
                return emptyList()
            }

            val httpStart = System.currentTimeMillis()
            val response: GeoapifyPlacesResponse = client.get("https://api.geoapify.com/v2/places") {
                parameter("apiKey", apiKey)

                val effectiveCategories = when {
                    categoriesForQuery != null -> categoriesForQuery
                    selectedTopLevelCategory != null -> selectedTopLevelCategory!!
                    else -> defaultCategories
                }
                parameter("categories", effectiveCategories)
                parameter("limit", 30)
                if (trimmedQuery.isNotEmpty()) {
                    parameter("name", trimmedQuery)
                }
                if (lat != 0.0 || lon != 0.0) {
                    parameter("filter", "circle:$lon,$lat,$radiusMeters")
                    parameter("bias", "proximity:$lon,$lat")
                }
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart

            Log.d(
                "GeoapifyPlacesApiService",
                "search query='${trimmedQuery}' lat=$lat lon=$lon radiusMeters=$radiusMeters mappedCategories='${categoriesForQuery}' selectedTopLevelCategory='${selectedTopLevelCategory}' features=${response.features.size} httpMs=$httpDuration"
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

                val hoursList = props.openingHours
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val rawPhone = props.contact?.phone
                    ?: props.contact?.phoneOther?.firstOrNull()
                    ?: props.phone

                Log.d(
                    "GeoapifyPlacesApiService",
                    "POI '" + name + "' rawPhone='" + (rawPhone ?: "null") + "' opening_hours='" + (props.openingHours ?: "null") + "'"
                )

                Poi(
                    name = name,
                    address = address,
                    hours = hoursList,
                    phone = rawPhone,
                    description = props.categories?.joinToString(", ") ?: "",
                    website = props.website,
                    lat = feature.geometry.coordinates.getOrNull(1),
                    lng = feature.geometry.coordinates.getOrNull(0),
                    geoapifyPlaceId = props.placeId
                )
            }

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

    /**
     * Fetch richer details for a single place using the Geoapify Place Details API.
     * Returns a [Poi] with phone/hours populated when available, or null if not found.
     */
    suspend fun getPlaceDetails(placeId: String): Poi? {
        val apiKey = getApiKey() ?: return null

        return try {
            val httpStart = System.currentTimeMillis()
            val response: GeoapifyPlaceDetailsResponse = client.get("https://api.geoapify.com/v2/place-details") {
                parameter("apiKey", apiKey)
                parameter("id", placeId)
            }.body()
            val httpDuration = System.currentTimeMillis() - httpStart
            Log.d("GeoapifyPlacesApiService", "getPlaceDetails placeId=$placeId features=${response.features.size} httpMs=$httpDuration")

            val feature = response.features.firstOrNull() ?: return null
            val props = feature.properties
            val name = props.name ?: props.street ?: return null

            val address = Address(
                street = listOfNotNull(props.street, props.housenumber)
                    .joinToString(" ")
                    .ifBlank { "" },
                city = props.city ?: "",
                state = props.state ?: "",
                zip = props.postcode ?: "",
                country = props.country ?: ""
            )

            val hoursList = props.openingHours
                ?.split(";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            val rawPhone = props.contact?.phone
                ?: props.contact?.phoneOther?.firstOrNull()
                ?: props.phone

            Poi(
                name = name,
                address = address,
                hours = hoursList,
                phone = rawPhone,
                description = props.categories?.joinToString(", ") ?: "",
                website = props.website,
                lat = null,
                lng = null,
                geoapifyPlaceId = props.placeId
            )
        } catch (e: Exception) {
            Log.e("GeoapifyPlacesApiService", "Error getting Geoapify place details", e)
            null
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
    @SerialName("place_id") val placeId: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val categories: List<String>? = null,
    @SerialName("opening_hours") val openingHours: String? = null,
    val contact: GeoapifyContact? = null
)

@Serializable
private data class GeoapifyContact(
    val phone: String? = null,
    @SerialName("phone_other") val phoneOther: List<String>? = null
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
private data class GeoapifyPlaceDetailsResponse(
    val features: List<GeoapifyPlaceDetailsFeature> = emptyList()
)

@Serializable
private data class GeoapifyPlaceDetailsFeature(
    val properties: GeoapifyPlaceProperties
)

@Serializable
private data class GeoapifyAutocompleteProperties(
    val formatted: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    @SerialName("postcode") val postcode: String? = null
)
