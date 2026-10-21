package com.teju.classnow.util

import java.util.Locale

object TimeInputHelper {

    data class Time12(
        val hour: String,
        val minute: String,
        val isPm: Boolean
    )

    /**
     * Converts a 24-hour time string ("HH:mm") to 12-hour components (hour: "01"-"12", minute: "00"-"59", isPm: Boolean)
     */
    fun from24Hour(time24: String): Time12 {
        if (!time24.contains(":")) {
            return Time12(hour = "09", minute = "00", isPm = false)
        }
        val parts = time24.split(":")
        val h24 = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val isPm = h24 >= 12
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return Time12(
            hour = String.format(Locale.US, "%02d", h12),
            minute = String.format(Locale.US, "%02d", m.coerceIn(0, 59)),
            isPm = isPm
        )
    }

    /**
     * Normalizes an entered hour string (e.g. "19", "7", "0") into 12-hour "01"-"12" and appropriate isPm flag.
     * Returns null if unparseable or out of bounds (h < 0 or h > 23).
     */
    fun normalizeHour(hourStr: String, currentIsPm: Boolean): Pair<String, Boolean>? {
        val h = hourStr.trim().toIntOrNull() ?: return null
        return when {
            h == 0 -> Pair("12", false)
            h in 1..12 -> Pair(String.format(Locale.US, "%02d", h), currentIsPm)
            h in 13..23 -> Pair(String.format(Locale.US, "%02d", h - 12), true)
            else -> null
        }
    }

    /**
     * Normalizes an entered minute string into "00"-"59".
     * Used for blur/submit normalization so typing is never interrupted.
     */
    fun normalizeMinute(minuteStr: String): String? {
        val m = minuteStr.trim().toIntOrNull() ?: return null
        if (m !in 0..59) return null
        return String.format(Locale.US, "%02d", m)
    }

    /**
     * Converts 12-hour components into a valid 24-hour "HH:mm" string.
     * Returns null if hour or minute is invalid.
     */
    fun to24Hour(hourStr: String, minuteStr: String, isPm: Boolean): String? {
        val hRaw = hourStr.trim().toIntOrNull() ?: return null
        val m = minuteStr.trim().toIntOrNull() ?: return null

        if (m !in 0..59) return null

        val (h, effectivePm) = when {
            hRaw == 0 -> Pair(12, false)
            hRaw in 1..12 -> Pair(hRaw, isPm)
            hRaw in 13..23 -> Pair(hRaw - 12, true)
            else -> return null
        }

        val h24 = when {
            effectivePm && h == 12 -> 12
            effectivePm -> h + 12
            !effectivePm && h == 12 -> 0
            else -> h
        }
        return String.format(Locale.US, "%02d:%02d", h24, m)
    }
}
