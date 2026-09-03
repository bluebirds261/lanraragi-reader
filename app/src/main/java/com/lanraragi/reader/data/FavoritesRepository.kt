package com.lanraragi.reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    private val dataStore = context.favoritesDataStore

    companion object {
        val KEY = stringSetPreferencesKey("favorite_arcids")
    }

    val favorites: Flow<Set<String>> = dataStore.data.map { it[KEY] ?: emptySet() }

    suspend fun isFavorite(arcid: String): Boolean = favorites.first().contains(arcid)

    suspend fun toggle(arcid: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY] ?: emptySet()
            val next = current.toMutableSet()
            if (!next.add(arcid)) next.remove(arcid)
            prefs[KEY] = next
        }
    }
}
