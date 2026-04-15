package com.platform.smartwastemanager.features.map.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the free OSRM routing API.
 *
 * Base URL: https://router.project-osrm.org/
 * No API key required — completely free and open.
 */
interface OsrmApiService {

    /**
     * Requests an optimised round trip through all provided coordinates.
     * Used to sort collection stops into the most efficient visit order.
     *
     * @param coordinates Semicolon-separated "longitude,latitude" pairs.
     *                    NOTE: OSRM uses longitude FIRST, then latitude.
     */
    @GET("trip/v1/driving/{coordinates}")
    suspend fun getOptimisedTrip(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("roundtrip")   roundtrip: Boolean = false,
        @Query("source")      source: String = "first",
        @Query("destination") destination: String = "last",
        @Query("geometries")  geometries: String = "geojson",
        @Query("overview")    overview: String = "full"
    ): OsrmTripResponse

    /**
     * Requests a driving route between exactly two coordinates with step-by-step instructions.
     * Used to get turn-by-turn directions from the driver's current location to the next stop.
     *
     * @param coordinates Two "longitude,latitude" pairs separated by a semicolon.
     *                    Example: "28.0473,-26.2041;28.05,-26.21"
     * @param steps       true = include turn-by-turn step instructions in the response.
     */
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("steps")       steps: Boolean = true,
        @Query("geometries")  geometries: String = "geojson",
        @Query("overview")    overview: String = "full"
    ): OsrmRouteResponse
}