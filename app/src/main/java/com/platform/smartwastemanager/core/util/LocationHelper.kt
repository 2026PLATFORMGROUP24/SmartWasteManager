package com.platform.smartwastemanager.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Utility object for getting the device's current GPS location and
 * converting it to a human-readable street name via Android's Geocoder.
 *
 * Requires ACCESS_FINE_LOCATION permission to be granted before calling.
 */
object LocationHelper {

    /**
     * Returns the device's current [GeoPoint] using FusedLocationProviderClient.
     * Uses a one-shot HIGH_ACCURACY request (not a continuous stream).
     *
     * @param context  Application or activity context.
     * @return         GeoPoint(latitude, longitude), or GeoPoint(0,0) on failure.
     */
    @SuppressLint("MissingPermission") // Permission is checked in the UI before calling
    suspend fun getCurrentLocation(context: Context): GeoPoint {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancellationToken = CancellationTokenSource()

            val location = client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            if (location != null) {
                GeoPoint(location.latitude, location.longitude)
            } else {
                GeoPoint(0.0, 0.0)
            }
        } catch (e: Exception) {
            GeoPoint(0.0, 0.0)
        }
    }

    /**
     * Converts a [GeoPoint] to a street name string using Android's Geocoder.
     * Falls back to "Unknown location" if geocoding fails or returns no results.
     *
     * On Android 8+ (Tiramisu+) the new listener-based API is used.
     * On older versions the synchronous API is used.
     *
     * @param context   Application or activity context.
     * @param geoPoint  The coordinates to reverse-geocode.
     * @return          A street name string, e.g. "Main Street, Springfield".
     */
    suspend fun getStreetName(context: Context, geoPoint: GeoPoint): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val lat = geoPoint.latitude
            val lng = geoPoint.longitude

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ uses a callback-based API
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val result = addresses.firstOrNull()?.let { addr ->
                            buildStreetString(addr)
                        } ?: "Unknown location"
                        continuation.resume(result) {}
                    }
                }
            } else {
                // Android 12 and below use the synchronous API
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let { buildStreetString(it) }
                    ?: "Unknown location"
            }
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    /** Builds a clean street string from an Address object. */
    private fun buildStreetString(address: android.location.Address): String {
        val parts = listOfNotNull(
            address.thoroughfare,       // e.g. "Main Street"
            address.subLocality,        // e.g. suburb name
            address.locality            // e.g. city name
        )
        return if (parts.isNotEmpty()) parts.joinToString(", ")
        else address.getAddressLine(0) ?: "Unknown location"
    }
}