package com.platform.smartwastemanager.core.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Creates a single DataStore instance tied to the application context.
// This is the recommended way to use DataStore — one instance per app.
private val Context.dataStore by preferencesDataStore(name = Constants.DATASTORE_NAME)

/**
 * Manages the driver/user view toggle state using DataStore Preferences.
 *
 * DataStore is the modern replacement for SharedPreferences — it is safe
 * to use with coroutines and does not block the main thread.
 *
 * The toggle only matters when the signed-in user has the DRIVER role.
 * When isDriverViewActive = true  → show full driver dashboard (CRUD buttons, etc.)
 * When isDriverViewActive = false → show the resident/user view (read-only)
 */
class ViewToggleRepository(private val context: Context) {

    // The key used to store the boolean value in DataStore
    private val driverViewKey = booleanPreferencesKey(Constants.KEY_DRIVER_VIEW_ACTIVE)

    /**
     * A live stream of the current toggle state.
     * Defaults to true (driver view active) if no value has been saved yet.
     * Collect this in your ViewModel to react to toggle changes instantly.
     */
    val isDriverViewActive: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[driverViewKey] ?: true // default: driver view ON
        }

    /**
     * Saves the new toggle state to DataStore.
     * This is a suspend function — call it from a coroutine (e.g. viewModelScope.launch).
     */
    suspend fun setDriverViewActive(isActive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[driverViewKey] = isActive
        }
    }
}