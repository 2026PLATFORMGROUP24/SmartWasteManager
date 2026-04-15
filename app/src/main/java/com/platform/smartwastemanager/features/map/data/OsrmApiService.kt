package com.platform.smartwastemanager.features.map.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the free OSRM routing API.
 *
 * Base URL: https://router.project-osrm.org/
 * No API key required — completely free and open.
 *
 * We use the /trip service which solves the Travelling Salesman Problem,
 * returning the most efficient order to visit all stops.
 */
interface OsrmApiService {

    /**
     * Requests an optimised round trip through all provided coordinates.
     *
     * @param coordinates A semicolon-separated string of "longitude,latitude" pairs.
     *                    Example: "28.0473,-26.2041;28.05,-26.21;28.06,-26.22"
     *                    NOTE: OSRM uses longitude FIRST, then latitude.
     *
     * @param roundtrip   false = open trip (start ≠ end). We use false for collection routes.
     * @param source      "first" = start at the first coordinate.
     * @param destination "last"  = end at the last coordinate.
     * @param geometries  "geojson" = return route geometry as GeoJSON coordinates.
     * @param overview    "full"    = return the complete route geometry.
     *
     * @return [OsrmTripResponse] containing optimised waypoint order + geometry.
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
}