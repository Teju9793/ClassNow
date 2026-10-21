package com.teju.classnow.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.teju.classnow.data.local.SettingsRepository
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.SilentModeAction
import kotlinx.coroutines.flow.first

object SmartSilentManager {

    /**
     * Checks if the app has permission to modify Do Not Disturb / Notification Policy.
     * Required on Android 7.0+ (API 24+) to change ringer mode to/from silent.
     */
    fun hasDndPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return notificationManager?.isNotificationPolicyAccessGranted == true
        }
        return true
    }

    /**
     * Intent to open the system DND access screen.
     */
    fun getDndSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Applies the calculated Smart Silent Mode action safely and idempotently.
     */
    suspend fun applySilentMode(context: Context, state: ScheduleState, settingsRepository: SettingsRepository) {
        val isMasterEnabled = settingsRepository.isSmartSilentModeEnabled.first()
        if (!isMasterEnabled) {
            return
        }

        if (!hasDndPermission(context)) {
            // Permission not granted; cannot safely manipulate ringer mode without throwing SecurityException
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val currentRingerMode = audioManager.ringerMode
        val targetSilencePreference = settingsRepository.smartSilentTarget.first() // "SILENT" or "VIBRATE"

        val targetSilentMode = if (targetSilencePreference == "VIBRATE") {
            AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_SILENT
        }

        try {
            when (state.silentModeAction) {
                SilentModeAction.SILENT -> {
                    // Only change if not already silenced
                    if (currentRingerMode != AudioManager.RINGER_MODE_SILENT &&
                        currentRingerMode != AudioManager.RINGER_MODE_VIBRATE) {
                        // Save the user's previous ringer mode so we can restore it after the day ends
                        val saved = settingsRepository.savedRingerMode.first()
                        if (saved == -1) {
                            settingsRepository.setSavedRingerMode(currentRingerMode)
                        }
                        audioManager.ringerMode = targetSilentMode
                    }
                }

                SilentModeAction.NORMAL -> {
                    // Final class ended: restore the original ringer mode
                    val saved = settingsRepository.savedRingerMode.first()
                    val modeToRestore = if (saved != -1) saved else AudioManager.RINGER_MODE_NORMAL
                    if (currentRingerMode != modeToRestore) {
                        audioManager.ringerMode = modeToRestore
                    }
                    settingsRepository.setSavedRingerMode(-1)
                }

                SilentModeAction.IGNORE -> {
                    // Leave device sound mode untouched
                }
            }
        } catch (e: SecurityException) {
            // Guard against unexpected OEM policy violations
            e.printStackTrace()
        }
    }

    /**
     * Called when the user explicitly toggles the Master Smart Silent switch OFF.
     * Stops automation and leaves current phone state untouched without restoring obsolete states.
     */
    suspend fun onMasterToggleDisabled(settingsRepository: SettingsRepository) {
        settingsRepository.setSavedRingerMode(-1)
        settingsRepository.setManualOverridePeriod("")
    }

    /**
     * Called when the user manually changes sound mode during an active class period.
     * Records a manual override for the current period so automation will not override the user.
     */
    suspend fun recordManualOverride(settingsRepository: SettingsRepository, periodKey: String) {
        settingsRepository.setManualOverridePeriod(periodKey)
    }

    /**
     * Clears manual override if any.
     */
    suspend fun clearManualOverride(settingsRepository: SettingsRepository) {
        settingsRepository.setManualOverridePeriod("")
    }
}
