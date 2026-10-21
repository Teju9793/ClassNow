package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.SilentModeAction
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class SmartSilentStateTest {

    private val entryDefault = TimetableEntry(
        id = 1,
        dayOfWeek = "Monday",
        subject = "Regular Lecture",
        startTime = "09:00",
        endTime = "10:00",
        silentModeRule = TimetableEntry.RULE_DEFAULT
    )

    private val entryForceSound = TimetableEntry(
        id = 2,
        dayOfWeek = "Monday",
        subject = "Presentation / Speaking Session",
        startTime = "10:00",
        endTime = "11:00",
        silentModeRule = TimetableEntry.RULE_FORCE_SOUND
    )

    private val entryIgnore = TimetableEntry(
        id = 3,
        dayOfWeek = "Monday",
        subject = "Free Study Period",
        startTime = "11:00",
        endTime = "12:00",
        silentModeRule = TimetableEntry.RULE_IGNORE
    )

    @Test
    fun test_MasterToggleOff_AlwaysYieldsIgnoreAction() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = listOf(entryDefault),
            smartSilentMasterEnabled = false
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_RuleDefault_SilencesWhenMasterEnabled() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = listOf(entryDefault),
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.SILENT, state.silentModeAction)
    }

    @Test
    fun test_RuleForceSound_RestoresNormalSoundDuringClass() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 30),
            entries = listOf(entryDefault, entryForceSound),
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Presentation / Speaking Session", state.currentClass?.subject)
        assertEquals(SilentModeAction.NORMAL, state.silentModeAction)
    }

    @Test
    fun test_RuleIgnore_DoesNotAlterSoundState() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(11, 30),
            entries = listOf(entryDefault, entryIgnore),
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }

    @Test
    fun test_ManualOverride_StopsAutomationForCurrentClass() {
        val classPeriodKey = "CLASS_${entryDefault.id}_${entryDefault.startTime}_${entryDefault.endTime}"

        val stateWithOverride = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = listOf(entryDefault),
            smartSilentMasterEnabled = true,
            manualOverridePeriodKey = classPeriodKey
        )

        // Overriding a silent class should ignore automated changes so user's manual mode is preserved
        assertEquals(SilentModeAction.IGNORE, stateWithOverride.silentModeAction)
    }

    @Test
    fun test_FinalClassCompleted_RestoresNormalSound() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 15),
            entries = listOf(entryDefault),
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.NO_MORE_CLASSES, state.status)
        assertEquals(SilentModeAction.NORMAL, state.silentModeAction)
    }

    @Test
    fun test_NoClassesScheduledToday_LeavesSoundUntouched() {
        val state = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(14, 0),
            entries = emptyList(),
            smartSilentMasterEnabled = true
        )

        assertEquals(TimetableStatus.NO_CLASSES_TODAY, state.status)
        assertEquals(SilentModeAction.IGNORE, state.silentModeAction)
    }
}
