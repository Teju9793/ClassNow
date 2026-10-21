package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.SilentModeAction
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class TimetableEngineTest {

    private val sampleEntries = listOf(
        TimetableEntry(
            id = 1,
            dayOfWeek = "Monday",
            subject = "Mathematics",
            startTime = "09:00",
            endTime = "10:00",
            faculty = "Dr. Euler"
        ),
        TimetableEntry(
            id = 2,
            dayOfWeek = "Monday",
            subject = "Physics",
            startTime = "10:00",
            endTime = "11:00",
            faculty = "Dr. Newton"
        ),
        TimetableEntry(
            id = 3,
            dayOfWeek = "Monday",
            subject = "Chemistry Lab",
            startTime = "11:15",
            endTime = "12:15",
            faculty = "Dr. Curie"
        ),
        TimetableEntry(
            id = 4,
            dayOfWeek = "Monday",
            subject = "Computer Science",
            startTime = "13:30",
            endTime = "15:00",
            faculty = "Prof. Turing"
        )
    )

    @Test
    fun test_EmptyTimetable_ReturnsNoClassesToday() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 0),
            entries = emptyList()
        )
        assertEquals(TimetableStatus.NO_CLASSES_TODAY, state.status)
        assertNull(state.currentClass)
        assertNull(state.nextClass)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_BeforeFirstClass_ReturnsBeforeClassesStatus() {
        // First class starts at 09:00. Time is 08:30.
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(8, 30),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.BEFORE_CLASSES, state.status)
        assertNull(state.currentClass)
        assertNotNull(state.nextClass)
        assertEquals("Mathematics", state.nextClass?.subject)
        assertEquals(30L, state.remainingMinutes)
        assertEquals(LocalTime.of(9, 0), state.nextTransitionLocalTime)
        // Outside college hours before first class: do not silence
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_ActiveFirstClass_CalculatesCorrectElapsedRemainingAndProgress() {
        // Class is 09:00 to 10:00. Time is 09:30 (halfway).
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Mathematics", state.currentClass?.subject)
        assertEquals("Physics", state.nextClass?.subject)
        assertEquals(30L, state.elapsedMinutes)
        assertEquals(30L, state.remainingMinutes)
        assertEquals(60L, state.totalMinutes)
        assertEquals(0.5f, state.progressFraction, 0.01f)
        assertEquals(50, state.progressPercentage)
        assertEquals(LocalTime.of(10, 0), state.nextTransitionLocalTime)
        assertEquals(SilentModeAction.SILENT, state.silentModeAction)
    }

    @Test
    fun test_ClassStart_CalculatesZeroProgress() {
        // Exactly at start time 09:00
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 0),
            entries = sampleEntries
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(0L, state.elapsedMinutes)
        assertEquals(60L, state.remainingMinutes)
        assertEquals(0f, state.progressFraction, 0.01f)
        assertEquals(0, state.progressPercentage)
    }

    @Test
    fun test_ClassNearEnd_CalculatesNearFullProgress() {
        // At 09:59 (59 mins into 60 min class)
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 59),
            entries = sampleEntries
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(59L, state.elapsedMinutes)
        assertEquals(1L, state.remainingMinutes)
        assertTrue(state.progressPercentage in 98..99)
    }

    @Test
    fun test_AdjacentClassesWithNoGap_TransitionsExactlyAtBoundary() {
        // Mathematics ends at 10:00, Physics starts at 10:00.
        // At 09:59:59 -> Mathematics
        val stateBefore = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 59),
            entries = sampleEntries
        )
        assertEquals("Mathematics", stateBefore.currentClass?.subject)

        // At exactly 10:00:00 -> Physics
        val stateBoundary = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 0),
            entries = sampleEntries
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, stateBoundary.status)
        assertEquals("Physics", stateBoundary.currentClass?.subject)
        assertEquals("Chemistry Lab", stateBoundary.nextClass?.subject)
        assertEquals(0L, stateBoundary.elapsedMinutes)
    }

    @Test
    fun test_ShortBreakBetweenClasses_IdentifiesBreak() {
        // Physics ends at 11:00, Chemistry starts at 11:15 (15 min gap).
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(11, 5),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.BREAK, state.status)
        assertNull(state.currentClass)
        assertEquals("Chemistry Lab", state.nextClass?.subject)
        assertEquals(5L, state.elapsedMinutes)
        assertEquals(10L, state.remainingMinutes)
        assertEquals(LocalTime.of(11, 15), state.nextTransitionLocalTime)
        // Keep silent during breaks within college day
        assertEquals(SilentModeAction.SILENT, state.silentModeAction)
    }

    @Test
    fun test_LunchGap_IdentifiesLunchBreak() {
        // Chemistry ends at 12:15, Computer Science starts at 13:30 (75 min gap across midday).
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(12, 45),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.LUNCH, state.status)
        assertNull(state.currentClass)
        assertEquals("Computer Science", state.nextClass?.subject)
        assertEquals(LocalTime.of(13, 30), state.nextTransitionLocalTime)
        // Keep silent during lunch within college day
        assertEquals(SilentModeAction.SILENT, state.silentModeAction)
    }

    @Test
    fun test_AfterLastClass_ReturnsClassesCompletedAndRestoresNormalSound() {
        // Computer Science ends at 15:00. Time is 15:01.
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(15, 1),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.NO_MORE_CLASSES, state.status)
        assertNull(state.currentClass)
        assertNull(state.nextClass)
        // Master enabled: turns sound mode back to NORMAL after final class ends
        assertEquals(SilentModeAction.NORMAL, state.silentModeAction)
    }

    @Test
    fun test_SmartSilentMode_MasterToggleOff_AlwaysIgnoresSound() {
        // During class, but master toggle is disabled
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = sampleEntries,
            smartSilentMasterEnabled = false
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_CustomRule_ForceSound_KeepsSoundNormal() {
        val entryWithForceSound = listOf(
            TimetableEntry(
                id = 10,
                dayOfWeek = "Tuesday",
                subject = "Music Class",
                startTime = "14:00",
                endTime = "15:00",
                silentModeRule = TimetableEntry.RULE_FORCE_SOUND
            )
        )

        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(14, 30),
            entries = entryWithForceSound,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.NORMAL, state.silentModeAction)
    }

    @Test
    fun test_CustomRule_Ignore_LeavesSoundUntouched() {
        val entryWithIgnore = listOf(
            TimetableEntry(
                id = 11,
                dayOfWeek = "Wednesday",
                subject = "Study Hall",
                startTime = "10:00",
                endTime = "11:00",
                silentModeRule = TimetableEntry.RULE_IGNORE
            )
        )

        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 30),
            entries = entryWithIgnore,
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_ManualOverride_IgnoresDuringOverriddenPeriod() {
        val stateNormal = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = sampleEntries,
            smartSilentMasterEnabled = true
        )
        assertEquals(SilentModeAction.SILENT, stateNormal.silentModeAction)

        // User manually turned sound ON during this class
        val stateOverridden = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = sampleEntries,
            smartSilentMasterEnabled = true,
            manualOverridePeriodKey = stateNormal.periodKey
        )
        // Should ignore and not re-silence
        assertEquals(SilentModeAction.IGNORE, stateOverridden.silentModeAction)

        // When time transitions to the next class (10:15) with different periodKey, automation resumes
        val stateNextClass = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 15),
            entries = sampleEntries,
            smartSilentMasterEnabled = true,
            manualOverridePeriodKey = stateNormal.periodKey // old period key
        )
        assertEquals(SilentModeAction.SILENT, stateNextClass.silentModeAction)
    }

    @Test
    fun test_UnsortedInputEntries_SortedCorrectly() {
        // Pass entries in reverse order
        val reversed = sampleEntries.reversed()
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = reversed
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Mathematics", state.currentClass?.subject)
        assertEquals("Physics", state.nextClass?.subject)
    }

    @Test
    fun test_CustomSchedule_IntervalsAndTransitions() {
        val customJson = """
            {
                "intervals": [
                    {"startTime": "10:00", "endTime": "10:30", "mode": "SILENT"},
                    {"startTime": "10:30", "endTime": "11:00", "mode": "NORMAL"}
                ],
                "endAction": "FORCE_NORMAL"
            }
        """.trimIndent()

        val customEntry = TimetableEntry(
            id = 10,
            dayOfWeek = "Monday",
            subject = "Workshop",
            startTime = "10:00",
            endTime = "11:00",
            customSilentSchedule = customJson
        )

        // At 10:15 -> inside first interval -> SILENT, next transition is 10:30
        val state1 = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 15),
            entries = listOf(customEntry),
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, state1.status)
        assertEquals(SilentModeAction.SILENT, state1.silentModeAction)
        assertEquals(LocalTime.of(10, 30), state1.nextTransitionLocalTime)

        // At 10:45 -> inside second interval -> NORMAL, next transition is 11:00
        val state2 = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 45),
            entries = listOf(customEntry),
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, state2.status)
        assertEquals(SilentModeAction.NORMAL, state2.silentModeAction)
        assertEquals(LocalTime.of(11, 0), state2.nextTransitionLocalTime)

        // At 11:05 -> after class -> end action FORCE_NORMAL applied
        val state3 = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(11, 5),
            entries = listOf(customEntry),
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.NO_MORE_CLASSES, state3.status)
        assertEquals(SilentModeAction.NORMAL, state3.silentModeAction)
    }
}
