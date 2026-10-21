package com.teju.classnow.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val timeFormatter12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    /**
     * Converts a 24-hour format string (HH:mm) to a 12-hour display string (h:mm a).
     */
    fun formatTo12Hour(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return ""
        return try {
            val localTime = LocalTime.parse(timeStr, timeFormatter24)
            localTime.format(timeFormatter12)
        } catch (e: Exception) {
            timeStr
        }
    }

    /**
     * Formats a LocalTime object to 12-hour display string.
     */
    fun formatLocalTimeTo12Hour(time: LocalTime): String {
        return time.format(timeFormatter12)
    }
}
