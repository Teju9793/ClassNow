package com.teju.classnow.util

import com.teju.classnow.data.model.CustomClassSchedule
import com.teju.classnow.data.model.CustomSoundInterval
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object CustomScheduleHelper {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toJson(schedule: CustomClassSchedule): String {
        val sb = StringBuilder()
        sb.append("{\"intervals\":[")
        schedule.intervals.forEachIndexed { i, interval ->
            if (i > 0) sb.append(",")
            sb.append("{\"startTime\":\"").append(interval.startTime)
                .append("\",\"endTime\":\"").append(interval.endTime)
                .append("\",\"mode\":\"").append(interval.mode).append("\"}")
        }
        sb.append("],\"endAction\":\"").append(schedule.endAction).append("\"}")
        return sb.toString()
    }

    fun fromJson(jsonStr: String?): CustomClassSchedule? {
        if (jsonStr.isNullOrBlank()) return null
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        if (!trimmed.contains("\"intervals\"") && !trimmed.contains("\"startTime\"")) return null
        return try {
            val intervals = mutableListOf<CustomSoundInterval>()
            val itemRegex = Regex("""\{([^{}]+)\}""")
            for (match in itemRegex.findAll(jsonStr)) {
                val block = match.groupValues[1]
                val start = Regex(""""startTime"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                val end = Regex(""""endTime"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1)
                val mode = Regex(""""mode"\s*:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: CustomSoundInterval.MODE_SILENT
                if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                    intervals.add(CustomSoundInterval(start, end, mode))
                }
            }

            val endActionMatch = Regex(""""endAction"\s*:\s*"([^"]+)"""").find(jsonStr)
            val endAction = endActionMatch?.groupValues?.get(1) ?: CustomClassSchedule.END_ACTION_DEFAULT

            CustomClassSchedule(
                intervals = intervals.sortedBy { it.startTime },
                endAction = endAction
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates that all custom intervals fall within the class start/end times and do not overlap.
     * Returns an error description, or null if all intervals are valid.
     */
    fun validate(
        intervals: List<CustomSoundInterval>,
        classStartTime: String,
        classEndTime: String
    ): String? {
        val classStart = try { LocalTime.parse(classStartTime, timeFormatter) } catch (e: Exception) { null }
            ?: return "Invalid class start time."
        val classEnd = try { LocalTime.parse(classEndTime, timeFormatter) } catch (e: Exception) { null }
            ?: return "Invalid class end time."

        val sorted = mutableListOf<Pair<LocalTime, LocalTime>>()

        for ((index, interval) in intervals.withIndex()) {
            val iStart = try { LocalTime.parse(interval.startTime, timeFormatter) } catch (e: Exception) { null }
                ?: return "Interval ${index + 1} has an invalid start time '${interval.startTime}'."
            val iEnd = try { LocalTime.parse(interval.endTime, timeFormatter) } catch (e: Exception) { null }
                ?: return "Interval ${index + 1} has an invalid end time '${interval.endTime}'."

            if (!iEnd.isAfter(iStart)) {
                return "Interval ${index + 1}: End time must be after start time."
            }

            if (iStart.isBefore(classStart) || iEnd.isAfter(classEnd)) {
                return "Interval ${index + 1} (${TimeUtils.formatTo12Hour(interval.startTime)} – ${TimeUtils.formatTo12Hour(interval.endTime)}) must fall within class time (${TimeUtils.formatTo12Hour(classStartTime)} – ${TimeUtils.formatTo12Hour(classEndTime)})."
            }

            sorted.add(iStart to iEnd)
        }

        // Check for overlaps among intervals
        sorted.sortBy { it.first }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            if (current.second.isAfter(next.first)) {
                return "Custom intervals overlap: ${current.first}–${current.second} and ${next.first}–${next.second}."
            }
        }

        return null
    }
}
