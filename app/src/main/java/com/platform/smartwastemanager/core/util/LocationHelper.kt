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
     *
     * Strategy (most accurate to fastest):
     *  1. Try a fresh HIGH_ACCURACY one-shot request — best accuracy but may
     *     return null on a cold start (no recent GPS fix cached on the device).
     *  2. If that returns null, fall back to getLastLocation() — returns the
     *     most recently cached GPS fix almost instantly.
     *  3. If both return null, fall back to GeoPoint(0, 0) so the caller
     *     always receives a non-null value.
     *
     * This two-step approach prevents the common "first stop is not nearest"
     * bug that occurs when getCurrentLocation returns null on a cold GPS start.
     *
     * @param context  Application or activity context.
     * @return         GeoPoint(latitude, longitude), or GeoPoint(0, 0) on failure.
     */
    @SuppressLint("MissingPermission") // Permission is checked in the UI before calling
    suspend fun getCurrentLocation(context: Context): GeoPoint {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)

            // --- Step 1: Fresh high-accuracy fix ---
            val cancellationToken = CancellationTokenSource()
            val freshLocation = client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            if (freshLocation != null) {
                // Got a fresh GPS fix — use it.
                return GeoPoint(freshLocation.latitude, freshLocation.longitude)
            }

            // --- Step 2: Fall back to last known cached location ---
            // This is fast (no GPS warm-up needed) and works even when
            // PRIORITY_HIGH_ACCURACY returns null on a cold start.
            val lastLocation = client.lastLocation.await()
            if (lastLocation != null) {
                GeoPoint(lastLocation.latitude, lastLocation.longitude)
            } else {
                // No fix available at all — return zero so callers can detect this.
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
     * On Android 13+ (Tiramisu) the new listener-based API is used.
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

    /**
     * Converts an address string to a [GeoPoint] using Android's Geocoder.
     *
     * @param context Application or activity context.
     * @param addressName The address to search for.
     * @return GeoPoint if found, null otherwise.
     */
    suspend fun getCoordinates(context: Context, addressName: String): GeoPoint? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(addressName, 1) { addresses ->
                        val result = addresses.firstOrNull()?.let { addr ->
                            GeoPoint(addr.latitude, addr.longitude)
                        }
                        continuation.resume(result) {}
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(addressName, 1)
                addresses?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
            }
        } catch (e: Exception) {
            null
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