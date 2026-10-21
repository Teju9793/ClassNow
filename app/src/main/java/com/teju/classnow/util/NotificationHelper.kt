package com.teju.classnow.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.teju.classnow.MainActivity
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.TimetableStatus

object NotificationHelper {
    private const val CHANNEL_ID = "live_timetable_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Live Timetable"
            val descriptionText = "Displays ongoing timetable status, current class, and upcoming schedule"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showLiveNotification(context: Context, state: ScheduleState, showOnLockscreen: Boolean = true) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Current Class: ${state.currentClass?.subject ?: "Class"}"
            TimetableStatus.BREAK -> "Short Break"
            TimetableStatus.LUNCH -> "Lunch Break"
            TimetableStatus.FREE_PERIOD -> "Free Period"
            TimetableStatus.BEFORE_CLASSES -> "College Timetable"
            TimetableStatus.NO_MORE_CLASSES -> "Classes Completed"
            TimetableStatus.NO_CLASSES_TODAY -> "No Classes Today"
        }

        val contentText = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> {
                val time = "${TimeUtils.formatTo12Hour(state.currentClass?.startTime)} - ${TimeUtils.formatTo12Hour(state.currentClass?.endTime)}"
                val remaining = " (${state.timeSummary})"
                val next = state.nextClass?.let { "\nNext: ${it.subject} at ${TimeUtils.formatTo12Hour(it.startTime)}" } ?: ""
                "$time$remaining$next"
            }
            TimetableStatus.BREAK,
            TimetableStatus.LUNCH,
            TimetableStatus.FREE_PERIOD -> {
                val next = state.nextClass?.let { "Next: ${it.subject} at ${TimeUtils.formatTo12Hour(it.startTime)}" } ?: "Enjoy your break"
                "${state.timeSummary}\n$next"
            }
            TimetableStatus.BEFORE_CLASSES -> {
                state.nextClass?.let { "First: ${it.subject} at ${TimeUtils.formatTo12Hour(it.startTime)}" } ?: "Classes haven't started yet"
            }
            TimetableStatus.NO_MORE_CLASSES -> "You're all done for today!"
            TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled for today."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(if (showOnLockscreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Notifications permission not granted
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
