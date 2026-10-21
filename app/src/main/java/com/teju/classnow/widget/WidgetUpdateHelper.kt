package com.teju.classnow.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.teju.classnow.data.local.AppDatabase
import com.teju.classnow.data.local.SettingsRepository
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.util.NotificationHelper
import com.teju.classnow.util.SmartSilentManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

object WidgetUpdateHelper {

    /**
     * Explicitly iterates over all active Glance widget instances and updates each one synchronously
     * within the coroutine, ensuring RemoteViews reach AppWidgetManager before any background receiver finishes.
     */
    suspend fun updateGlanceWidgets(context: Context) {
        val appContext = context.applicationContext
        try {
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(ClassNowWidget::class.java)
            for (glanceId in glanceIds) {
                try {
                    ClassNowWidget().update(appContext, glanceId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                ClassNowWidget().updateAll(appContext)
            } catch (ignored: Exception) {}
        }
    }

    suspend fun triggerUpdate(context: Context) {
        try {
            val appContext = context.applicationContext
            val settingsRepository = SettingsRepository(appContext)
            val dao = AppDatabase.getDatabase(appContext).timetableDao()
            val currentDay = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            val entries = dao.getEntriesForDayList(currentDay)

            val isSmartSilentEnabled = settingsRepository.isSmartSilentModeEnabled.first()
            val manualOverridePeriod = settingsRepository.manualOverridePeriod.first()

            val scheduleState = TimetableEngine.getScheduleState(
                currentTime = LocalTime.now(),
                entries = entries,
                smartSilentMasterEnabled = isSmartSilentEnabled,
                manualOverridePeriodKey = manualOverridePeriod
            )

            // 1. Update Glance Home Screen Widget immediately
            updateGlanceWidgets(appContext)

            // 2. Apply Smart Silent Mode automation
            try {
                SmartSilentManager.applySilentMode(appContext, scheduleState, settingsRepository)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Update Persistent Live Notification if enabled
            val isLiveNotificationEnabled = settingsRepository.isLiveNotificationEnabled.first()
            if (isLiveNotificationEnabled) {
                NotificationHelper.showLiveNotification(appContext, scheduleState)
            } else {
                NotificationHelper.cancelNotification(appContext)
            }

            // 4. Re-schedule alarm for the next state transition or progress checkpoint
            WidgetAlarmScheduler.scheduleNextAlarm(appContext, scheduleState)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Updates Glance widget UI representation only (e.g. on Theme change).
     * Does not recalculate sound automation or reschedule alarms.
     */
    suspend fun updateWidgetUiOnly(context: Context) {
        updateGlanceWidgets(context)
    }
}

