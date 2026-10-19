package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "costumer_delivery_prefs")

class PreferenceRepository(private val context: Context) {

    private val KEY_DARK_MODE = booleanPreferencesKey("key_dark_mode")
    private val KEY_IS_LOGGED_IN = booleanPreferencesKey("key_is_logged_in")

    val isDarkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_DARK_MODE] ?: false
        }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_IS_LOGGED_IN] ?: false
        }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_LOGGED_IN] = loggedIn
        }
    }

    private val sharedPrefs = context.getSharedPreferences("delivio_cancellations", Context.MODE_PRIVATE)

    fun isOrderCancellationAnnounced(orderId: String): Boolean {
        val set = sharedPrefs.getStringSet("announced_cancelled_orders", emptySet()) ?: emptySet()
        return set.contains(orderId)
    }

    fun markOrderCancellationAnnounced(orderId: String) {
        val current = sharedPrefs.getStringSet("announced_cancelled_orders", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(orderId)
        sharedPrefs.edit().putStringSet("announced_cancelled_orders", current).apply()
    }

    fun isOrderCancelledByCustomer(orderId: String): Boolean {
        val set = sharedPrefs.getStringSet("customer_cancelled_orders", emptySet()) ?: emptySet()
        return set.contains(orderId)
    }

    fun markOrderCancelledByCustomer(orderId: String) {
        val current = sharedPrefs.getStringSet("customer_cancelled_orders", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(orderId)
        sharedPrefs.edit().putStringSet("customer_cancelled_orders", current).apply()
    }
}
