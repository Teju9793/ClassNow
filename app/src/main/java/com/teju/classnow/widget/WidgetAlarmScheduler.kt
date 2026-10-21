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

    private const val REQUEST_CODE = 4412
    const val ACTION_UPDATE_TIMETABLE = "com.teju.classnow.action.UPDATE_WIDGET"

    // Interval for intermediate progress checkpoints during active classes
    private const val PROGRESS_CHECKPOINT_MINUTES = 5L

    fun scheduleNextAlarm(context: Context, state: ScheduleState) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ACTION_UPDATE_TIMETABLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel previous alarm to avoid duplicate triggers
        alarmManager.cancel(pendingIntent)

        val nextTriggerTime = calculateNextTriggerTime(state) ?: return
        val now = LocalDateTime.now()
        var targetDateTime = LocalDateTime.of(LocalDate.now(), nextTriggerTime)

        // If the target time is earlier than now (e.g. crossing midnight), schedule for next day
        if (!targetDateTime.isAfter(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val triggerAtMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
            // Graceful fallback if exact alarm permission was revoked
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
     * Determines the next trigger time.
     * When an active class has substantial time remaining, schedules an intermediate
     * checkpoint every 5 minutes to keep widget progress and time fresh without battery drain.
     */
    private fun calculateNextTriggerTime(state: ScheduleState): LocalTime? {
        val boundary = state.nextTransitionLocalTime ?: return null
        val now = LocalTime.now()

        return when (state.status) {
            TimetableStatus.CURRENT_CLASS -> {
                // If more than 5 minutes remain in the class, schedule an intermediate checkpoint
                if (state.remainingMinutes > PROGRESS_CHECKPOINT_MINUTES) {
                    val checkpoint = now.plusMinutes(PROGRESS_CHECKPOINT_MINUTES)
                    if (checkpoint.isBefore(boundary)) {
                        checkpoint
                    } else {
                        boundary
                    }
                } else {
                    boundary
                }
            }
            TimetableStatus.BREAK,
            TimetableStatus.LUNCH,
            TimetableStatus.FREE_PERIOD,
            TimetableStatus.BEFORE_CLASSES -> {
                // Trigger right when next class starts
                boundary
            }
            TimetableStatus.NO_MORE_CLASSES,
            TimetableStatus.NO_CLASSES_TODAY -> {
                // Refresh after midnight for the next day
                boundary
            }
        }
    }

    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ACTION_UPDATE_TIMETABLE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
