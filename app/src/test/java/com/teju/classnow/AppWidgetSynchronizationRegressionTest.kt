package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.SilentModeAction
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
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
}
