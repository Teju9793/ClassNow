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

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val LIVE_NOTIFICATION_ENABLED = booleanPreferencesKey("live_notification_enabled")
        val SHOW_ON_LOCKSCREEN = booleanPreferencesKey("show_on_lockscreen")
        val SMART_SILENT_MODE_ENABLED = booleanPreferencesKey("smart_silent_mode_enabled")
        val SMART_SILENT_TARGET = androidx.datastore.preferences.core.stringPreferencesKey("smart_silent_target") // "SILENT" or "VIBRATE"
        val SAVED_RINGER_MODE = androidx.datastore.preferences.core.intPreferencesKey("saved_ringer_mode") // -1 = none
        val MANUAL_OVERRIDE_PERIOD = androidx.datastore.preferences.core.stringPreferencesKey("manual_override_period")
        val THEME_MODE = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    val isLiveNotificationEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { preferences -> preferences[LIVE_NOTIFICATION_ENABLED] ?: false }

    val showOnLockscreen: Flow<Boolean> = appContext.dataStore.data
        .map { preferences -> preferences[SHOW_ON_LOCKSCREEN] ?: true }

    val isSmartSilentModeEnabled: Flow<Boolean> = appContext.dataStore.data
        .map { preferences -> preferences[SMART_SILENT_MODE_ENABLED] ?: false }

    val smartSilentTarget: Flow<String> = appContext.dataStore.data
        .map { preferences -> preferences[SMART_SILENT_TARGET] ?: "SILENT" }

    val savedRingerMode: Flow<Int> = appContext.dataStore.data
        .map { preferences -> preferences[SAVED_RINGER_MODE] ?: -1 }

    val manualOverridePeriod: Flow<String> = appContext.dataStore.data
        .map { preferences -> preferences[MANUAL_OVERRIDE_PERIOD] ?: "" }

    val themeMode: Flow<String> = appContext.dataStore.data
        .map { preferences -> preferences[THEME_MODE] ?: "SYSTEM" }

    val hasSeenOnboarding: Flow<Boolean> = appContext.dataStore.data
        .map { preferences -> preferences[HAS_SEEN_ONBOARDING] ?: false }

    suspend fun setLiveNotificationEnabled(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[LIVE_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setShowOnLockscreen(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[SHOW_ON_LOCKSCREEN] = enabled
        }
    }

    suspend fun setSmartSilentModeEnabled(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[SMART_SILENT_MODE_ENABLED] = enabled
        }
    }

    suspend fun setSmartSilentTarget(target: String) {
        appContext.dataStore.edit { preferences ->
            preferences[SMART_SILENT_TARGET] = target
        }
    }

    suspend fun setSavedRingerMode(mode: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[SAVED_RINGER_MODE] = mode
        }
    }

    suspend fun setManualOverridePeriod(periodKey: String) {
        appContext.dataStore.edit { preferences ->
            preferences[MANUAL_OVERRIDE_PERIOD] = periodKey
        }
    }

    suspend fun setThemeMode(mode: String) {
        appContext.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[HAS_SEEN_ONBOARDING] = seen
        }
    }

}
