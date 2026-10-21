package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.SilentModeAction
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.widget.WidgetAlarmScheduler
import com.teju.classnow.widget.WidgetTheme
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class AppWidgetSynchronizationRegressionTest {

    private val testEntries = listOf(
        TimetableEntry(
            id = 101,
            dayOfWeek = "Monday",
            subject = "Data Structures",
            startTime = "09:00",
            endTime = "10:00",
            faculty = "Prof. Smith"
        ),
        TimetableEntry(
            id = 102,
            dayOfWeek = "Monday",
            subject = "Short Break",
            startTime = "10:15",
            endTime = "11:15",
            faculty = "Prof. Jones"
        ),
        TimetableEntry(
            id = 103,
            dayOfWeek = "Monday",
            subject = "Database Systems",
            startTime = "12:15",
            endTime = "13:45",
            faculty = "Prof. Taylor"
        )
    )

    @Test
    fun test_CurrentClass_ExactStartBoundary_MarksClassActive() {
        // Just before start (08:59) -> NOT active
        val beforeStart = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(8, 59),
            entries = testEntries
        )
        assertNotEquals(TimetableStatus.CURRENT_CLASS, beforeStart.status)
        assertEquals(TimetableStatus.BEFORE_CLASSES, beforeStart.status)
        assertEquals(101L, beforeStart.nextClass?.id)

        // Exact start boundary (09:00) -> CURRENT_CLASS, class ID 101 active
        val atStart = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 0),
            entries = testEntries
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, atStart.status)
        assertEquals(101L, atStart.currentClass?.id)
        assertEquals(0L, atStart.elapsedMinutes)
        assertEquals(60L, atStart.remainingMinutes)
        assertEquals(0, atStart.progressPercentage)

        // Exact end boundary (10:00) -> Class 101 has ended, enters BREAK interval
        val atEnd = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 0),
            entries = testEntries
        )
        assertNotEquals(TimetableStatus.CURRENT_CLASS, atEnd.status)
        assertEquals(TimetableStatus.BREAK, atEnd.status)
        assertEquals(102L, atEnd.nextClass?.id)
        assertEquals(15L, atEnd.remainingMinutes)
    }

    @Test
    fun test_ExactBoundary_BackToBackClasses_TransitionsSeamlessly() {
        val backToBack = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Tuesday", subject = "Math", startTime = "09:00", endTime = "10:00"),
            TimetableEntry(id = 2L, dayOfWeek = "Tuesday", subject = "Physics", startTime = "10:00", endTime = "11:00")
        )

        // At 09:59 -> Math
        val state1 = TimetableEngine.getScheduleState(LocalTime.of(9, 59), backToBack)
        assertEquals(TimetableStatus.CURRENT_CLASS, state1.status)
        assertEquals(1L, state1.currentClass?.id)

        // At 10:00 -> Physics immediately, no gap
        val state2 = TimetableEngine.getScheduleState(LocalTime.of(10, 0), backToBack)
        assertEquals(TimetableStatus.CURRENT_CLASS, state2.status)
        assertEquals(2L, state2.currentClass?.id)
    }

    @Test
    fun test_GapIntervals_Break_Lunch_FreePeriod() {
        // 1. Short gap (10:00 - 10:15 = 15 min) -> BREAK
        val breakState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 5),
            entries = testEntries
        )
        assertEquals(TimetableStatus.BREAK, breakState.status)
        assertEquals(102L, breakState.nextClass?.id)
        assertEquals(10L, breakState.remainingMinutes)

        // 2. Lunch gap (11:15 - 12:15 = 60 min during 11..14) -> LUNCH
        val lunchState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(11, 45),
            entries = testEntries
        )
        assertEquals(TimetableStatus.LUNCH, lunchState.status)
        assertEquals(103L, lunchState.nextClass?.id)
        assertEquals(30L, lunchState.remainingMinutes)
    }

    @Test
    fun test_SingleClass_NoMisleadingUpcomingCard() {
        val single = listOf(
            TimetableEntry(id = 99L, dayOfWeek = "Wednesday", subject = "Seminar", startTime = "14:00", endTime = "16:00")
        )

        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(14, 30),
            entries = single
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(99L, state.currentClass?.id)
        assertNull(state.nextClass)
        assertTrue(state.upcomingClasses.isEmpty())
    }

    @Test
    fun test_EmptyTimetable_SafeAndConsistent() {
        val emptyState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(12, 0),
            entries = emptyList()
        )
        assertEquals(TimetableStatus.NO_CLASSES_TODAY, emptyState.status)
        assertNull(emptyState.currentClass)
        assertNull(emptyState.nextClass)
        assertEquals(SilentModeAction.IGNORE, emptyState.silentModeAction)
    }

    @Test
    fun test_ThemeMode_Resolution() {
        assertFalse(WidgetTheme.resolveIsDark(null, "LIGHT"))
        assertTrue(WidgetTheme.resolveIsDark(null, "DARK"))
    }

    @Test
    fun test_CSVReplacement_StateRecomputation() {
        // Before import: Old schedule
        val oldEntries = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Friday", subject = "Old Class", startTime = "08:00", endTime = "09:00")
        )
        val beforeState = TimetableEngine.getScheduleState(LocalTime.of(10, 0), oldEntries)
        assertEquals(TimetableStatus.NO_MORE_CLASSES, beforeState.status)

        // After import: New schedule replacing old entries
        val newEntries = listOf(
            TimetableEntry(id = 2L, dayOfWeek = "Friday", subject = "New Class", startTime = "09:30", endTime = "11:00")
        )
        val afterState = TimetableEngine.getScheduleState(LocalTime.of(10, 0), newEntries)
        assertEquals(TimetableStatus.CURRENT_CLASS, afterState.status)
        assertEquals(2L, afterState.currentClass?.id)
        assertEquals("New Class", afterState.currentClass?.subject)
    }

    @Test
    fun test_BatteryOptimization_AlarmSeparationConstants() {
        // Essential boundary transitions and non-waking progress refreshes must have distinct request codes
        assertNotEquals(
            WidgetAlarmScheduler.REQUEST_CODE_BOUNDARY,
            WidgetAlarmScheduler.REQUEST_CODE_PROGRESS
        )
        assertEquals(4412, WidgetAlarmScheduler.REQUEST_CODE_BOUNDARY)
        assertEquals(4413, WidgetAlarmScheduler.REQUEST_CODE_PROGRESS)
        assertEquals(5L, WidgetAlarmScheduler.PROGRESS_CHECKPOINT_MINUTES)
    }

    @Test
    fun test_MinuteUpdates_PrecisionAndMonotonicity() {
        // Verify minute-level clock updates compute monotonically increasing progress
        val singleClass = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Math", startTime = "10:00", endTime = "11:00")
        )

        var previousRemaining = 61L
        var previousProgress = -1

        for (minute in 0..60) {
            val time = LocalTime.of(10, minute.coerceAtMost(59))
            val state = TimetableEngine.getScheduleState(time, singleClass)
            assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
            assertTrue(state.remainingMinutes <= previousRemaining)
            assertTrue(state.progressPercentage >= previousProgress)
            previousRemaining = state.remainingMinutes
            previousProgress = state.progressPercentage
        }
    }

    @Test
    fun test_BeforeAndAfterClass_BoundaryTransitions() {
        val schedule = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Morning Class", startTime = "09:00", endTime = "10:00"),
            TimetableEntry(id = 2L, dayOfWeek = "Monday", subject = "Afternoon Class", startTime = "13:00", endTime = "14:00")
        )

        // 1. Before classes: 08:59
        val before = TimetableEngine.getScheduleState(LocalTime.of(8, 59), schedule)
        assertEquals(TimetableStatus.BEFORE_CLASSES, before.status)
        assertEquals(LocalTime.of(9, 0), before.nextTransitionLocalTime)

        // 2. Class starts: 09:00
        val classStart = TimetableEngine.getScheduleState(LocalTime.of(9, 0), schedule)
        assertEquals(TimetableStatus.CURRENT_CLASS, classStart.status)
        assertEquals(LocalTime.of(10, 0), classStart.nextTransitionLocalTime)

        // 3. Lunch break between morning and afternoon: 11:30
        val lunch = TimetableEngine.getScheduleState(LocalTime.of(11, 30), schedule)
        assertEquals(TimetableStatus.LUNCH, lunch.status)
        assertEquals(LocalTime.of(13, 0), lunch.nextTransitionLocalTime)

        // 4. Afternoon class ends: 14:00 (schedules transition for 00:01 next day)
        val afterAll = TimetableEngine.getScheduleState(LocalTime.of(14, 0), schedule)
        assertEquals(TimetableStatus.NO_MORE_CLASSES, afterAll.status)
        assertEquals(LocalTime.MIDNIGHT.plusMinutes(1), afterAll.nextTransitionLocalTime)
    }

    @Test
    fun test_SmartSilentToggle_OffToOn_UpdatesScheduleStateAction() {
        // Given an active class at 09:30
        val activeClass = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Algorithms", startTime = "09:00", endTime = "10:00")
        )

        // When Smart Silent is OFF
        val stateOff = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = activeClass,
            smartSilentMasterEnabled = false
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, stateOff.status)
        assertEquals(SilentModeAction.IGNORE, stateOff.silentModeAction)

        // When Smart Silent is toggled ON
        val stateOn = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = activeClass,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, stateOn.status)
        assertEquals(SilentModeAction.SILENT, stateOn.silentModeAction)
    }

    @Test
    fun test_SmartSilentToggle_OnToOff_UpdatesScheduleStateAction() {
        val activeClass = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Algorithms", startTime = "09:00", endTime = "10:00")
        )

        val stateOn = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = activeClass,
            smartSilentMasterEnabled = true
        )
        assertEquals(SilentModeAction.SILENT, stateOn.silentModeAction)

        // Toggling OFF must restore action to IGNORE
        val stateOff = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = activeClass,
            smartSilentMasterEnabled = false
        )
        assertEquals(SilentModeAction.IGNORE, stateOff.silentModeAction)
    }

    @Test
    fun test_ThemeChange_UpdatesWidgetColorsResolution() {
        // LIGHT mode
        val lightColors = WidgetTheme.getColors(android.content.ContextWrapper(null), "LIGHT")
        assertFalse(lightColors.isDark)

        // DARK mode
        val darkColors = WidgetTheme.getColors(android.content.ContextWrapper(null), "DARK")
        assertTrue(darkColors.isDark)
        assertNotEquals(lightColors.background, darkColors.background)
    }

    @Test
    fun test_NoClassDay_ContentRemainsValid() {
        val emptyState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 0),
            entries = emptyList(),
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.NO_CLASSES_TODAY, emptyState.status)
        assertNull(emptyState.currentClass)
        assertNull(emptyState.nextClass)
        assertEquals(SilentModeAction.IGNORE, emptyState.silentModeAction)
        assertEquals("No Classes Scheduled Today", emptyState.statusSummary)
        // Automatically schedules rollover check at 00:01 for tomorrow's timetable
        assertEquals(LocalTime.MIDNIGHT.plusMinutes(1), emptyState.nextTransitionLocalTime)
    }

    @Test
    fun test_TimetableMutation_RefreshesScheduleStateImmediately() {
        // Timetable state is purely a function of (time, entries, settings) without hidden caches
        val entries1 = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Math", startTime = "09:00", endTime = "10:00")
        )
        val state1 = TimetableEngine.getScheduleState(LocalTime.of(9, 30), entries1)
        assertEquals("Math", state1.currentClass?.subject)

        // Add class at 10:00 - 11:00
        val entries2 = entries1 + TimetableEntry(id = 2L, dayOfWeek = "Monday", subject = "Physics", startTime = "10:00", endTime = "11:00")
        val state2 = TimetableEngine.getScheduleState(LocalTime.of(9, 30), entries2)
        assertEquals("Math", state2.currentClass?.subject)
        assertEquals("Physics", state2.nextClass?.subject)
    }

    @Test
    fun test_ShortEntry_FF_BoundaryAccuracy() {
        // Entry 'ff' scheduled for 17:20 to 17:22
        val shortEntry = listOf(
            TimetableEntry(id = 999L, dayOfWeek = "Monday", subject = "ff", startTime = "17:20", endTime = "17:22")
        )

        // 1. One minute before start: 17:19
        val before = TimetableEngine.getScheduleState(LocalTime.of(17, 19), shortEntry)
        assertEquals(TimetableStatus.BEFORE_CLASSES, before.status)
        assertEquals("ff", before.nextClass?.subject)
        assertEquals(LocalTime.of(17, 20), before.nextTransitionLocalTime)

        // 2. Exactly at start: 17:20
        val atStart = TimetableEngine.getScheduleState(LocalTime.of(17, 20), shortEntry)
        assertEquals(TimetableStatus.CURRENT_CLASS, atStart.status)
        assertEquals("ff", atStart.currentClass?.subject)
        assertEquals(LocalTime.of(17, 22), atStart.nextTransitionLocalTime)
        assertEquals(0L, atStart.elapsedMinutes)
        assertEquals(2L, atStart.remainingMinutes)

        // 3. During class: 17:21
        val during = TimetableEngine.getScheduleState(LocalTime.of(17, 21), shortEntry)
        assertEquals(TimetableStatus.CURRENT_CLASS, during.status)
        assertEquals("ff", during.currentClass?.subject)
        assertEquals(LocalTime.of(17, 22), during.nextTransitionLocalTime)
        assertEquals(1L, during.elapsedMinutes)
        assertEquals(1L, during.remainingMinutes)

        // 4. Exactly at end: 17:22
        val atEnd = TimetableEngine.getScheduleState(LocalTime.of(17, 22), shortEntry)
        assertEquals(TimetableStatus.NO_MORE_CLASSES, atEnd.status)
        assertNull(atEnd.currentClass)
        assertEquals(LocalTime.MIDNIGHT.plusMinutes(1), atEnd.nextTransitionLocalTime)

        // 5. After class: 17:23
        val after = TimetableEngine.getScheduleState(LocalTime.of(17, 23), shortEntry)
        assertEquals(TimetableStatus.NO_MORE_CLASSES, after.status)
        assertEquals(LocalTime.MIDNIGHT.plusMinutes(1), after.nextTransitionLocalTime)
    }

    @Test
    fun test_ShortEntry_FollowedByNextClass_TransitionsSeamlessly() {
        val schedule = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "ff", startTime = "17:20", endTime = "17:22"),
            TimetableEntry(id = 2L, dayOfWeek = "Monday", subject = "Evening Lab", startTime = "17:22", endTime = "18:00")
        )

        // At 17:20 -> "ff" is current, "Evening Lab" is next
        val at1720 = TimetableEngine.getScheduleState(LocalTime.of(17, 20), schedule)
        assertEquals(TimetableStatus.CURRENT_CLASS, at1720.status)
        assertEquals("ff", at1720.currentClass?.subject)
        assertEquals("Evening Lab", at1720.nextClass?.subject)
        assertEquals(LocalTime.of(17, 22), at1720.nextTransitionLocalTime)

        // At 17:22 -> "Evening Lab" starts immediately without gap
        val at1722 = TimetableEngine.getScheduleState(LocalTime.of(17, 22), schedule)
        assertEquals(TimetableStatus.CURRENT_CLASS, at1722.status)
        assertEquals("Evening Lab", at1722.currentClass?.subject)
        assertNull(at1722.nextClass)
        assertEquals(LocalTime.of(18, 0), at1722.nextTransitionLocalTime)
    }
}
