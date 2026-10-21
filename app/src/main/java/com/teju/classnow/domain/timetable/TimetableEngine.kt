package com.teju.classnow.domain.timetable

import com.teju.classnow.data.model.CustomClassSchedule
import com.teju.classnow.data.model.CustomSoundInterval
import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.util.CustomScheduleHelper
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TimetableStatus {
    CURRENT_CLASS,
    BREAK,
    LUNCH,
    FREE_PERIOD,
    BEFORE_CLASSES,
    NO_MORE_CLASSES,
    NO_CLASSES_TODAY
}

enum class SilentModeAction {
    SILENT,
    NORMAL,
    IGNORE
}

data class ScheduleState(
    val status: TimetableStatus,
    val currentClass: TimetableEntry? = null,
    val nextClass: TimetableEntry? = null,
    val upcomingClasses: List<TimetableEntry> = emptyList(),
    val elapsedMinutes: Long = 0,
    val remainingMinutes: Long = 0,
    val totalMinutes: Long = 0,
    val progressFraction: Float = 0f,
    val progressPercentage: Int = 0,
    val nextTransitionLocalTime: LocalTime? = null,
    val periodKey: String = "",
    val silentModeAction: SilentModeAction = SilentModeAction.IGNORE,
    val statusSummary: String = "",
    val timeSummary: String = ""
)

object TimetableEngine {

    val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /**
     * Single source of truth calculation for timetable state, progress, transitions,
     * and Smart Silent Mode automation decisions.
     */
    fun getScheduleState(
        currentTime: LocalTime,
        entries: List<TimetableEntry>,
        smartSilentMasterEnabled: Boolean = false,
        manualOverridePeriodKey: String = ""
    ): ScheduleState {
        if (entries.isEmpty()) {
            return ScheduleState(
                status = TimetableStatus.NO_CLASSES_TODAY,
                nextTransitionLocalTime = LocalTime.MIDNIGHT.plusMinutes(1),
                silentModeAction = SilentModeAction.IGNORE,
                statusSummary = "No Classes Scheduled Today",
                timeSummary = "Enjoy your day off"
            )
        }

        // Sort entries strictly by start time
        val sortedEntries = entries.mapNotNull { entry ->
            val start = tryParseTime(entry.startTime)
            val end = tryParseTime(entry.endTime)
            if (start != null && end != null && end.isAfter(start)) {
                entry to (start to end)
            } else null
        }.sortedBy { it.second.first }

        if (sortedEntries.isEmpty()) {
            return ScheduleState(
                status = TimetableStatus.NO_CLASSES_TODAY,
                nextTransitionLocalTime = LocalTime.MIDNIGHT.plusMinutes(1),
                silentModeAction = SilentModeAction.IGNORE,
                statusSummary = "No Valid Classes",
                timeSummary = "Check timetable configuration"
            )
        }

        val firstStart = sortedEntries.first().second.first
        val lastEnd = sortedEntries.maxOf { it.second.second }

        // 1. Before scheduled classes of the day
        if (currentTime.isBefore(firstStart)) {
            val next = sortedEntries.first().first
            val remainingToStart = Duration.between(currentTime, firstStart).toMinutes().coerceAtLeast(0)
            return ScheduleState(
                status = TimetableStatus.BEFORE_CLASSES,
                nextClass = next,
                upcomingClasses = sortedEntries.map { it.first },
                remainingMinutes = remainingToStart,
                nextTransitionLocalTime = firstStart,
                periodKey = "BEFORE_${firstStart}",
                silentModeAction = SilentModeAction.IGNORE,
                statusSummary = "Before College Hours",
                timeSummary = formatTimeUntil("First class starts in", remainingToStart)
            )
        }

        // 2. After all scheduled classes of the day
        if (!currentTime.isBefore(lastEnd)) {
            val midnightRefresh = LocalTime.MIDNIGHT.plusMinutes(1)
            return ScheduleState(
                status = TimetableStatus.NO_MORE_CLASSES,
                nextTransitionLocalTime = midnightRefresh,
                periodKey = "AFTER_${lastEnd}",
                silentModeAction = if (smartSilentMasterEnabled) SilentModeAction.NORMAL else SilentModeAction.IGNORE,
                statusSummary = "Classes Completed",
                timeSummary = "Done for today"
            )
        }

        // 3. Check for an active class
        val activeIndex = sortedEntries.indexOfFirst { (_, times) ->
            val (start, end) = times
            !currentTime.isBefore(start) && currentTime.isBefore(end)
        }

        if (activeIndex != -1) {
            val (currentEntry, times) = sortedEntries[activeIndex]
            val (start, end) = times
            val total = Duration.between(start, end).toMinutes().coerceAtLeast(1)
            val elapsed = Duration.between(start, currentTime).toMinutes().coerceIn(0, total)
            val remaining = Duration.between(currentTime, end).toMinutes().coerceAtLeast(0)
            val fraction = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val percentage = (fraction * 100f).toInt().coerceIn(0, 100)

            val nextClass = if (activeIndex + 1 < sortedEntries.size) sortedEntries[activeIndex + 1].first else null
            val upcoming = sortedEntries.drop(activeIndex + 1).map { it.first }

            val customSchedule = currentEntry.customSilentSchedule?.let { CustomScheduleHelper.fromJson(it) }
            var nextTransition = end
            var periodKey = "CLASS_${currentEntry.id}_${currentEntry.startTime}_${currentEntry.endTime}"

            val silentAction = if (!smartSilentMasterEnabled) {
                SilentModeAction.IGNORE
            } else if (customSchedule != null && customSchedule.intervals.isNotEmpty()) {
                val currentInterval = customSchedule.intervals.find { interval ->
                    val iStart = tryParseTime(interval.startTime)
                    val iEnd = tryParseTime(interval.endTime)
                    iStart != null && iEnd != null && !currentTime.isBefore(iStart) && currentTime.isBefore(iEnd)
                }

                if (currentInterval != null) {
                    val iEnd = tryParseTime(currentInterval.endTime) ?: end
                    nextTransition = iEnd
                    periodKey = "CLASS_${currentEntry.id}_INT_${currentInterval.startTime}_${currentInterval.endTime}_${currentInterval.mode}"
                    if (manualOverridePeriodKey == periodKey) {
                        SilentModeAction.IGNORE
                    } else {
                        when (currentInterval.mode) {
                            CustomSoundInterval.MODE_NORMAL -> SilentModeAction.NORMAL
                            else -> SilentModeAction.SILENT
                        }
                    }
                } else {
                    val nextUpcomingInterval = customSchedule.intervals.mapNotNull { interval ->
                        val iStart = tryParseTime(interval.startTime)
                        if (iStart != null && iStart.isAfter(currentTime)) interval to iStart else null
                    }.minByOrNull { it.second }

                    if (nextUpcomingInterval != null) {
                        nextTransition = nextUpcomingInterval.second
                        periodKey = "CLASS_${currentEntry.id}_PRE_INT_${nextUpcomingInterval.first.startTime}"
                    } else {
                        nextTransition = end
                        periodKey = "CLASS_${currentEntry.id}_POST_INT"
                    }

                    if (manualOverridePeriodKey == periodKey) {
                        SilentModeAction.IGNORE
                    } else {
                        SilentModeAction.NORMAL
                    }
                }
            } else {
                if (manualOverridePeriodKey == periodKey) {
                    SilentModeAction.IGNORE
                } else {
                    when (currentEntry.silentModeRule) {
                        TimetableEntry.RULE_FORCE_SOUND -> SilentModeAction.NORMAL
                        TimetableEntry.RULE_IGNORE -> SilentModeAction.IGNORE
                        else -> SilentModeAction.SILENT
                    }
                }
            }

            return ScheduleState(
                status = TimetableStatus.CURRENT_CLASS,
                currentClass = currentEntry,
                nextClass = nextClass,
                upcomingClasses = upcoming,
                elapsedMinutes = elapsed,
                remainingMinutes = remaining,
                totalMinutes = total,
                progressFraction = fraction,
                progressPercentage = percentage,
                nextTransitionLocalTime = nextTransition,
                periodKey = periodKey,
                silentModeAction = silentAction,
                statusSummary = currentEntry.subject,
                timeSummary = formatRemaining(remaining)
            )
        }

        // 4. Non-class interval within college hours (Break, Lunch, or Free Period)
        val prevClassPair = sortedEntries.filter { (_, times) ->
            !currentTime.isBefore(times.second)
        }.lastOrNull()

        val nextClassPair = sortedEntries.firstOrNull { (_, times) ->
            currentTime.isBefore(times.first)
        }

        val gapStart = prevClassPair?.second?.second ?: firstStart
        val gapEnd = nextClassPair?.second?.first ?: lastEnd

        val totalGap = Duration.between(gapStart, gapEnd).toMinutes().coerceAtLeast(1)
        val elapsedGap = Duration.between(gapStart, currentTime).toMinutes().coerceIn(0, totalGap)
        val remainingGap = Duration.between(currentTime, gapEnd).toMinutes().coerceAtLeast(0)
        val gapFraction = (elapsedGap.toFloat() / totalGap.toFloat()).coerceIn(0f, 1f)
        val gapPercentage = (gapFraction * 100f).toInt().coerceIn(0, 100)

        // Determine if this interval represents Lunch, Break, or Free Period
        val isLunchHours = (gapStart.hour in 11..14 || gapEnd.hour in 12..15) && totalGap >= 30
        val isExplicitLunch = nextClassPair?.first?.subject?.contains("lunch", ignoreCase = true) == true ||
                prevClassPair?.first?.subject?.contains("lunch", ignoreCase = true) == true

        val intervalStatus = when {
            isLunchHours || isExplicitLunch -> TimetableStatus.LUNCH
            totalGap <= 25 -> TimetableStatus.BREAK
            else -> TimetableStatus.FREE_PERIOD
        }

        val periodKey = "GAP_${intervalStatus.name}_${gapStart}_${gapEnd}"

        val prevCustom = prevClassPair?.first?.customSilentSchedule?.let { CustomScheduleHelper.fromJson(it) }
        val silentAction = if (!smartSilentMasterEnabled) {
            SilentModeAction.IGNORE
        } else if (manualOverridePeriodKey == periodKey) {
            SilentModeAction.IGNORE
        } else if (prevCustom?.endAction == CustomClassSchedule.END_ACTION_NORMAL) {
            SilentModeAction.NORMAL
        } else if (prevCustom?.endAction == CustomClassSchedule.END_ACTION_KEEP) {
            SilentModeAction.IGNORE
        } else {
            // Keep silent mode ON during breaks, lunch, and free periods within college day
            SilentModeAction.SILENT
        }

        val summaryLabel = when (intervalStatus) {
            TimetableStatus.LUNCH -> "Lunch Break"
            TimetableStatus.BREAK -> "Short Break"
            TimetableStatus.FREE_PERIOD -> "Free Period"
            else -> "Break"
        }

        val upcoming = if (nextClassPair != null) {
            val idx = sortedEntries.indexOf(nextClassPair)
            if (idx != -1) sortedEntries.drop(idx).map { it.first } else emptyList()
        } else emptyList()

        return ScheduleState(
            status = intervalStatus,
            currentClass = null,
            nextClass = nextClassPair?.first,
            upcomingClasses = upcoming,
            elapsedMinutes = elapsedGap,
            remainingMinutes = remainingGap,
            totalMinutes = totalGap,
            progressFraction = gapFraction,
            progressPercentage = gapPercentage,
            nextTransitionLocalTime = gapEnd,
            periodKey = periodKey,
            silentModeAction = silentAction,
            statusSummary = summaryLabel,
            timeSummary = formatTimeUntil("Next class starts in", remainingGap)
        )
    }

    private fun tryParseTime(timeStr: String): LocalTime? {
        return try {
            LocalTime.parse(timeStr.trim(), timeFormatter)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatRemaining(minutes: Long): String {
        return if (minutes <= 1) {
            "Ending now"
        } else if (minutes < 60) {
            "$minutes min remaining"
        } else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h hr remaining" else "$h hr $m min remaining"
        }
    }

    private fun formatTimeUntil(prefix: String, minutes: Long): String {
        return if (minutes <= 1) {
            "$prefix <1 min"
        } else if (minutes < 60) {
            "$prefix $minutes min"
        } else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$prefix $h hr" else "$prefix $h hr $m min"
        }
    }
}
