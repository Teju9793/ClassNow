package com.teju.classnow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.TimetableStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object WidgetAlarmScheduler {

    // Request code for essential timetable state transitions (class start/end, breaks)
    const val REQUEST_CODE_BOUNDARY = 4412

    // Request code for intermediate non-waking widget progress refreshes
    const val REQUEST_CODE_PROGRESS = 4413

    const val ACTION_UPDATE_TIMETABLE = "com.teju.classnow.action.UPDATE_WIDGET"

    // Interval for non-waking visual progress checkpoints during an ongoing class
    const val PROGRESS_CHECKPOINT_MINUTES = 5L

    fun scheduleNextAlarm(context: Context, state: ScheduleState) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 1. Schedule Essential Boundary Transition Alarm (P0: Smart Silent Mode & State Changes)
        scheduleBoundaryAlarm(context, alarmManager, state)

        // 2. Schedule Non-Waking Visual Progress Checkpoint (Battery-Safe: Does NOT wake from Doze)
        scheduleProgressAlarm(context, alarmManager, state)
    }

    /**
     * Schedules the exact waking alarm for timetable state transitions.
     * Essential for Smart Silent Mode muting/unmuting at class start and end boundaries.
     */
    private fun scheduleBoundaryAlarm(context: Context, alarmManager: AlarmManager, state: ScheduleState) {
        val intent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ACTION_UPDATE_TIMETABLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val boundaryTime = state.nextTransitionLocalTime
        if (boundaryTime == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAtMillis = calculateEpochMillis(boundaryTime)

        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                // Inexact fallback for Android 12+ without exact alarm permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (fallbackException: Exception) {
                fallbackException.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Schedules a non-waking checkpoint for visual progress updates during an active class.
     * Uses AlarmManager.RTC (non-wakeup) so the device is NEVER pulled out of Doze/Deep Sleep
     * while the phone is locked in a student's pocket.
     */
    private fun scheduleProgressAlarm(context: Context, alarmManager: AlarmManager, state: ScheduleState) {
        val intent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ACTION_UPDATE_TIMETABLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PROGRESS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Only schedule progress ticks during an active class when more than 5 minutes remain
        if (state.status == TimetableStatus.CURRENT_CLASS && state.remainingMinutes > PROGRESS_CHECKPOINT_MINUTES) {
            val checkpointTime = LocalTime.now().plusMinutes(PROGRESS_CHECKPOINT_MINUTES)
            val boundary = state.nextTransitionLocalTime

            if (boundary == null || checkpointTime.isBefore(boundary)) {
                val triggerAtMillis = calculateEpochMillis(checkpointTime)
                try {
                    // Non-waking: AlarmManager.RTC fires when awake or defers until next wake
                    alarmManager.set(AlarmManager.RTC, triggerAtMillis, pendingIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return
            }
        }

        // Cancel pending progress checkpoint if class ended or within 5 minutes of boundary
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateEpochMillis(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var targetDateTime = LocalDateTime.of(LocalDate.now(), time)

        // If target time is in the past:
        // - If within 60 seconds (execution latency at boundary), trigger immediately (+100ms) rather than 24h later.
        // - If more than 60 seconds (e.g. crossing midnight 00:01), schedule for tomorrow.
        if (!targetDateTime.isAfter(now)) {
            val diffSeconds = java.time.Duration.between(targetDateTime, now).seconds
            if (diffSeconds > 60) {
                targetDateTime = targetDateTime.plusDays(1)
            } else {
                return System.currentTimeMillis() + 100L
            }
        }

        return targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ACTION_UPDATE_TIMETABLE
        }

        val boundaryPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(boundaryPendingIntent)

        val progressPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PROGRESS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(progressPendingIntent)
    }
}
