package com.teju.classnow.widget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.teju.classnow.MainActivity
import com.teju.classnow.data.local.SettingsRepository
import com.teju.classnow.util.SmartSilentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClassNowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassNowWidget()

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.teju.classnow.action.REFRESH_WIDGET"
        const val ACTION_TOGGLE_SMART_SILENT = "com.teju.classnow.action.TOGGLE_SMART_SILENT"

        private val toggleMutex = Mutex()

        fun showToast(context: Context, message: String, length: Int = Toast.LENGTH_SHORT) {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(context.applicationContext, message, length).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * Authoritative master toggle execution pipeline.
         * Ensures:
         * 1. Mutex concurrency protection against rapid repeated taps.
         * 2. Persisted state is updated first.
         * 3. Widget UI is re-rendered synchronously before broadcast finish.
         * 4. System audio, alarms, and notifications are synchronized.
         */
        suspend fun executeMasterToggle(context: Context, targetEnabled: Boolean? = null): Boolean {
            return toggleMutex.withLock {
                val appContext = context.applicationContext
                val settings = SettingsRepository(appContext)
                val current = settings.isSmartSilentModeEnabled.first()
                val nextState = targetEnabled ?: !current

                if (nextState) {
                    if (!SmartSilentManager.hasDndPermission(appContext)) {
                        showToast(appContext, "DND access required — grant in ClassNow", Toast.LENGTH_LONG)
                        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navigate_to", "permissions")
                        }
                        try {
                            appContext.startActivity(launchIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return@withLock false
                    }
                }

                try {
                    // 1. Safely persist next state
                    settings.setSmartSilentModeEnabled(nextState)

                    // 2. Clear overrides if turned OFF
                    if (!nextState) {
                        SmartSilentManager.onMasterToggleDisabled(settings)
                    }

                    // 3. Update widget, system audio, alarms, and notifications in one authoritative pipeline
                    WidgetUpdateHelper.triggerUpdate(appContext)

                    showToast(appContext, if (nextState) "Smart Silent Mode: ON" else "Smart Silent Mode: OFF")
                    return@withLock true
                } catch (e: Exception) {
                    e.printStackTrace()
                    showToast(appContext, "Failed to update Smart Silent Mode")
                    return@withLock false
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        when (action) {
            ACTION_REFRESH_WIDGET -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdateHelper.triggerUpdate(context)
                        showToast(context, "Timetable refreshed")
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            ACTION_TOGGLE_SMART_SILENT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        executeMasterToggle(context)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            WidgetAlarmScheduler.ACTION_UPDATE_TIMETABLE,
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.appwidget.action.APPWIDGET_UPDATE" -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdateHelper.triggerUpdate(context)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
}
