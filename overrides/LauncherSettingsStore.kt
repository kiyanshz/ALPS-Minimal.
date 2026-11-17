package com.alps.minimal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.alpsSettingsDataStore by preferencesDataStore(name = "alps_settings")

/** Small persistent preference store for launcher-owned settings. */
class LauncherSettingsStore(context: Context) {
    private val store = context.applicationContext.alpsSettingsDataStore
    private val favoritesKey = stringSetPreferencesKey("favorite_apps")

    val favorites: Flow<Set<String>> = store.data.map { it[favoritesKey] ?: emptySet() }

    suspend fun toggleFavorite(packageName: String): Boolean {
        var isFavorite = false
        store.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            isFavorite = if (packageName in current) {
                prefs[favoritesKey] = current - packageName
                false
            } else {
                prefs[favoritesKey] = current + packageName
                true
            }
        }
        return isFavorite
    }

    suspend fun favoritePackages(): Set<String> = favorites.first()
}
