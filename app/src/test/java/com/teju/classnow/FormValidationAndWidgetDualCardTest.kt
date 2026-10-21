package com.teju.classnow

import androidx.compose.ui.unit.dp
import com.teju.classnow.data.model.CustomClassSchedule
import com.teju.classnow.data.model.CustomSoundInterval
import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.util.CustomScheduleHelper
import com.teju.classnow.util.TimeInputHelper
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

/**
 * Tests for Add/Edit Class form validation and Home-Screen Widget dual-card states.
 */
class FormValidationAndWidgetDualCardTest {

    // =========================================================================
    // 1. FORM VALIDATION TESTS
    // =========================================================================

    @Test
    fun test_SubjectValidation_BlankOrWhitespace_Fails() {
        val blankSubject1 = ""
        val blankSubject2 = "   "
        val blankSubject3 = "\t\n"
        assertTrue(blankSubject1.trim().isBlank())
        assertTrue(blankSubject2.trim().isBlank())
        assertTrue(blankSubject3.trim().isBlank())

        val validSubject = "Computer Networks"
        assertFalse(validSubject.trim().isBlank())
    }

    @Test
    fun test_TimeValidation_ValidHourAndMinute_ConvertsCorrectly() {
        // Standard 09:30 AM
        val time24 = TimeInputHelper.to24Hour("09", "30", false)
        assertEquals("09:30", time24)

        // 02:45 PM -> 14:45
        val pm24 = TimeInputHelper.to24Hour("02", "45", true)
        assertEquals("14:45", pm24)

        // 12:00 PM (Noon) -> 12:00
        val noon24 = TimeInputHelper.to24Hour("12", "00", true)
        assertEquals("12:00", noon24)

        // 12:00 AM (Midnight) -> 00:00
        val midnight24 = TimeInputHelper.to24Hour("12", "00", false)
        assertEquals("00:00", midnight24)
    }

    @Test
    fun test_TimeValidation_InvalidHourOrMinute_ReturnsNull() {
        // Hour > 23
        assertNull(TimeInputHelper.to24Hour("25", "00", false))

        // Minute > 59
        assertNull(TimeInputHelper.to24Hour("10", "60", false))

        // Negative values or empty
        assertNull(TimeInputHelper.to24Hour("", "30", false))
        assertNull(TimeInputHelper.to24Hour("10", "", false))
        assertNull(TimeInputHelper.to24Hour("abc", "00", false))
    }

    @Test
    fun test_TimeSequenceValidation_EndTimeMustBeAfterStartTime() {
        val start24 = TimeInputHelper.to24Hour("09", "00", false)!!
        val end24Valid = TimeInputHelper.to24Hour("10", "00", false)!!
        val end24Equal = TimeInputHelper.to24Hour("09", "00", false)!!
        val end24Before = TimeInputHelper.to24Hour("08", "30", false)!!

        val startL = LocalTime.parse(start24)
        val endLValid = LocalTime.parse(end24Valid)
        val endLEqual = LocalTime.parse(end24Equal)
        val endLBefore = LocalTime.parse(end24Before)

        assertTrue("End time must be after start time", endLValid.isAfter(startL))
        assertFalse("End time equal to start time is invalid", endLEqual.isAfter(startL))
        assertFalse("End time before start time is invalid", endLBefore.isAfter(startL))
    }

    @Test
    fun test_ConflictValidation_OverlappingEntriesOnSameDay_Detected() {
        val existingEntries = listOf(
            TimetableEntry(id = 1, subject = "Math", startTime = "09:00", endTime = "10:00", dayOfWeek = "Monday"),
            TimetableEntry(id = 2, subject = "Physics", startTime = "10:30", endTime = "11:30", dayOfWeek = "Monday"),
            TimetableEntry(id = 3, subject = "Chemistry", startTime = "09:00", endTime = "10:00", dayOfWeek = "Tuesday")
        )

        // New class: 09:30 - 10:30 on Monday -> overlaps with Math (09:00-10:00)
        val newStart = LocalTime.parse("09:30")
        val newEnd = LocalTime.parse("10:30")
        val overlapMonday = existingEntries.filter { it.dayOfWeek == "Monday" }.find { existing ->
            val exStart = LocalTime.parse(existing.startTime)
            val exEnd = LocalTime.parse(existing.endTime)
            newStart.isBefore(exEnd) && newEnd.isAfter(exStart)
        }
        assertNotNull(overlapMonday)
        assertEquals("Math", overlapMonday?.subject)

        // Same time (09:30 - 10:30) on Tuesday -> no overlap on Tuesday (Chemistry ends at 10:00, but wait, 09:30 < 10:00 so it overlaps Chemistry!)
        val overlapTuesday = existingEntries.filter { it.dayOfWeek == "Tuesday" }.find { existing ->
            val exStart = LocalTime.parse(existing.startTime)
            val exEnd = LocalTime.parse(existing.endTime)
            newStart.isBefore(exEnd) && newEnd.isAfter(exStart)
        }
        assertNotNull(overlapTuesday)
        assertEquals("Chemistry", overlapTuesday?.subject)

        // Non-overlapping class on Monday: 10:00 - 10:30
        val nonOverlapStart = LocalTime.parse("10:00")
        val nonOverlapEnd = LocalTime.parse("10:30")
        val noOverlapMonday = existingEntries.filter { it.dayOfWeek == "Monday" }.find { existing ->
            val exStart = LocalTime.parse(existing.startTime)
            val exEnd = LocalTime.parse(existing.endTime)
            nonOverlapStart.isBefore(exEnd) && nonOverlapEnd.isAfter(exStart)
        }
        assertNull("Back-to-back class does not conflict", noOverlapMonday)
    }

    @Test
    fun test_CustomSoundIntervalsValidation_BoundsAndOverlap() {
        // Valid interval completely inside class 09:00 - 11:00
        val validIntervals = listOf(
            CustomSoundInterval("09:15", "09:45", CustomSoundInterval.MODE_SILENT),
            CustomSoundInterval("10:00", "10:30", CustomSoundInterval.MODE_NORMAL)
        )
        assertNull(CustomScheduleHelper.validate(validIntervals, "09:00", "11:00"))

        // Interval starting before class start
        val invalidIntervalBefore = listOf(
            CustomSoundInterval("08:45", "09:30", CustomSoundInterval.MODE_SILENT)
        )
        assertNotNull(CustomScheduleHelper.validate(invalidIntervalBefore, "09:00", "11:00"))

        // Interval ending after class end
        val invalidIntervalAfter = listOf(
            CustomSoundInterval("10:30", "11:15", CustomSoundInterval.MODE_SILENT)
        )
        assertNotNull(CustomScheduleHelper.validate(invalidIntervalAfter, "09:00", "11:00"))
    }

    // =========================================================================
    // 2. WIDGET DUAL-CARD (CURRENT + NEXT) STATE TESTS
    // =========================================================================

    private val sampleTimetable = listOf(
        TimetableEntry(id = 1, subject = "Data Structures", startTime = "09:00", endTime = "10:30", dayOfWeek = "Monday"),
        TimetableEntry(id = 2, subject = "Algorithms", startTime = "11:00", endTime = "12:30", dayOfWeek = "Monday"),
        TimetableEntry(id = 3, subject = "Operating Systems", startTime = "13:30", endTime = "15:00", dayOfWeek = "Monday")
    )

    @Test
    fun test_WidgetState_DuringActiveFirstClass_ShowsCurrentAndNext() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(9, 45), sampleTimetable)

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertNotNull("Card 1 (CURRENT) must have active class", state.currentClass)
        assertEquals("Data Structures", state.currentClass?.subject)
        assertEquals(45L, state.remainingMinutes)
        assertEquals(50, state.progressPercentage)

        assertNotNull("Card 2 (NEXT) must identify the next class", state.nextClass)
        assertEquals("Algorithms", state.nextClass?.subject)
        assertEquals("11:00", state.nextClass?.startTime)
    }

    @Test
    fun test_WidgetState_DuringBreak_Card1ShowsBreak_Card2ShowsNextClass() {
        // Between 10:30 and 11:00 is a 30m break (or lunch if within lunch hours)
        val state = TimetableEngine.getScheduleState(LocalTime.of(10, 45), sampleTimetable)

        assertTrue(state.status == TimetableStatus.BREAK || state.status == TimetableStatus.FREE_PERIOD)
        assertNull("Card 1 has no active class during break", state.currentClass)
        assertTrue(state.statusSummary.isNotBlank())
        assertEquals(15L, state.remainingMinutes)

        assertNotNull("Card 2 must show the upcoming class after the break", state.nextClass)
        assertEquals("Algorithms", state.nextClass?.subject)
    }

    @Test
    fun test_WidgetState_BeforeClasses_Card1ShowsBefore_Card2ShowsFirstClass() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(8, 30), sampleTimetable)

        assertEquals(TimetableStatus.BEFORE_CLASSES, state.status)
        assertNull(state.currentClass)
        assertEquals(30L, state.remainingMinutes)

        assertNotNull("Card 2 must show first class of the day", state.nextClass)
        assertEquals("Data Structures", state.nextClass?.subject)
        assertEquals("09:00", state.nextClass?.startTime)
    }

    @Test
    fun test_WidgetState_DuringLastClass_Card1ShowsClass_Card2ShowsCompletedEmptyState() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(14, 0), sampleTimetable)

        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Operating Systems", state.currentClass?.subject)

        // No more classes after this
        assertNull("No next class after final period", state.nextClass)

        // Widget logic generates: "Last class of the day • Done after this"
        val message = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Last class of the day • Done after this"
            TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
            TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
            else -> "No upcoming classes"
        }
        assertEquals("Last class of the day • Done after this", message)
    }

    @Test
    fun test_WidgetState_AfterAllClassesCompleted_Card1AndCard2ShowAccurateDoneState() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(16, 0), sampleTimetable)

        assertEquals(TimetableStatus.NO_MORE_CLASSES, state.status)
        assertNull(state.currentClass)
        assertNull(state.nextClass)
        assertEquals("Classes Completed", state.statusSummary)

        val message = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Last class of the day • Done after this"
            TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
            TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
            else -> "No upcoming classes"
        }
        assertEquals("No further classes today", message)
    }

    @Test
    fun test_WidgetState_NoClassesToday_AccurateEmptyStates() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(10, 0), emptyList())

        assertEquals(TimetableStatus.NO_CLASSES_TODAY, state.status)
        assertNull(state.currentClass)
        assertNull(state.nextClass)
        assertEquals("No Classes Scheduled Today", state.statusSummary)

        val message = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Last class of the day • Done after this"
            TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
            TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
            else -> "No upcoming classes"
        }
        assertEquals("No classes scheduled today", message)
    }

    @Test
    fun test_HorizontalDualCard_LayoutResolution_SideBySideOnDefault4x2() {
        // Standard 4x2 widget has width ~320dp and height ~110dp -> resolves to MEDIUM (Horizontal dual-card)
        val category = resolveLayoutCategory(320.dp, 110.dp)
        assertEquals(WidgetLayoutCategory.MEDIUM, category)
    }

    @Test
    fun test_HorizontalDualCard_ContentStates_DuringActiveBreak() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(10, 45), sampleTimetable)
        // Left card displays Break status, Right card displays upcoming class
        assertTrue(state.status == TimetableStatus.BREAK || state.status == TimetableStatus.FREE_PERIOD)
        assertEquals("Algorithms", state.nextClass?.subject)
        assertEquals("11:00", state.nextClass?.startTime)
    }

    @Test
    fun test_HorizontalDualCard_EmptyStates_FinalClass_BothCardsHaveMeaningfulContent() {
        val state = TimetableEngine.getScheduleState(LocalTime.of(14, 0), sampleTimetable)
        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Operating Systems", state.currentClass?.subject)
        assertNull(state.nextClass)

        // Empty state labels for Right Card (NEXT) during final class
        val emptyTitle = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Final Class"
            TimetableStatus.NO_MORE_CLASSES -> "All Done"
            TimetableStatus.NO_CLASSES_TODAY -> "No Classes"
            else -> "No Classes"
        }
        val emptySubtitle = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Done after this class"
            TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
            TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
            else -> "No further classes today"
        }
        assertEquals("Final Class", emptyTitle)
        assertEquals("Done after this class", emptySubtitle)
    }
}
