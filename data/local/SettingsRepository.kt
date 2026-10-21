package com.teju.classnow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val LIVE_NOTIFICATION_ENABLED = booleanPreferencesKey("live_notification_enabled")
        val SHOW_ON_LOCKSCREEN = booleanPreferencesKey("show_on_lockscreen")
    }

    val isLiveNotificationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[LIVE_NOTIFICATION_ENABLED] ?: false
        }

    val showOnLockscreen: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_ON_LOCKSCREEN] ?: true
        }

    suspend fun setLiveNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LIVE_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setShowOnLockscreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_ON_LOCKSCREEN] = enabled
        }
    }
}
