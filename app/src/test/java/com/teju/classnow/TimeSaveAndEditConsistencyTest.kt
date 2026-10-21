package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.util.TimeInputHelper
import com.teju.classnow.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Regression tests for ClassNow class time saving, editing consistency,
 * 12-hour/24-hour roundtrip fidelity, weekday retention, and timetable engine updates.
 */
class TimeSaveAndEditConsistencyTest {

    @Test
    fun test_SaveAndReopen_0900PM_to_1015PM() {
        // Step 1: User enters 09:00 PM to 10:15 PM in form
        val startHour = "09"
        val startMinute = "00"
        val startIsPm = true

        val endHour = "10"
        val endMinute = "15"
        val endIsPm = true

        // Step 2: Conversion to database 24-hour representation
        val start24 = TimeInputHelper.to24Hour(startHour, startMinute, startIsPm)
        val end24 = TimeInputHelper.to24Hour(endHour, endMinute, endIsPm)

        assertEquals("21:00", start24)
        assertEquals("22:15", end24)

        // Step 3: Validate end follows start
        val startL = LocalTime.parse(start24)
        val endL = LocalTime.parse(end24)
        assertTrue("End time must be after start time", endL.isAfter(startL))

        // Step 4: Persist in Room model
        val entry = TimetableEntry(
            id = 100L,
            dayOfWeek = "Wednesday",
            subject = "test",
            startTime = start24!!,
            endTime = end24!!
        )

        assertEquals("21:00", entry.startTime)
        assertEquals("22:15", entry.endTime)
        assertEquals("Wednesday", entry.dayOfWeek)
        assertEquals("test", entry.subject)

        // Step 5: Format for display in timetable card
        val displayStart = TimeUtils.formatTo12Hour(entry.startTime)
        val displayEnd = TimeUtils.formatTo12Hour(entry.endTime)
        val displayRange = "$displayStart – $displayEnd"
        assertEquals("09:00 PM – 10:15 PM", displayRange)

        // Step 6: Simulate reopening record in AddEditClassDialog
        val reopenedStart = TimeInputHelper.from24Hour(entry.startTime)
        val reopenedEnd = TimeInputHelper.from24Hour(entry.endTime)

        assertEquals("09", reopenedStart.hour)
        assertEquals("00", reopenedStart.minute)
        assertTrue(reopenedStart.isPm)

        assertEquals("10", reopenedEnd.hour)
        assertEquals("15", reopenedEnd.minute)
        assertTrue(reopenedEnd.isPm)
    }

    @Test
    fun test_SaveAndReopen_1011PM_to_1012PM() {
        // Step 1: User enters 10:11 PM to 10:12 PM in form
        val startHour = "10"
        val startMinute = "11"
        val startIsPm = true

        val endHour = "10"
        val endMinute = "12"
        val endIsPm = true

        // Step 2: Convert to 24-hour representation
        val start24 = TimeInputHelper.to24Hour(startHour, startMinute, startIsPm)
        val end24 = TimeInputHelper.to24Hour(endHour, endMinute, endIsPm)

        assertEquals("22:11", start24)
        assertEquals("22:12", end24)

        // Step 3: Validate end follows start
        val startL = LocalTime.parse(start24)
        val endL = LocalTime.parse(end24)
        assertTrue("End time must be after start time", endL.isAfter(startL))

        // Step 4: Persist in Room model
        val entry = TimetableEntry(
            id = 101L,
            dayOfWeek = "Wednesday",
            subject = "test",
            startTime = start24!!,
            endTime = end24!!
        )

        assertEquals("22:11", entry.startTime)
        assertEquals("22:12", entry.endTime)

        // Step 5: Format for display in timetable card
        val displayStart = TimeUtils.formatTo12Hour(entry.startTime)
        val displayEnd = TimeUtils.formatTo12Hour(entry.endTime)
        val displayRange = "$displayStart – $displayEnd"
        assertEquals("10:11 PM – 10:12 PM", displayRange)

        // Step 6: Reopening record in edit dialog returns exact fields
        val reopenedStart = TimeInputHelper.from24Hour(entry.startTime)
        val reopenedEnd = TimeInputHelper.from24Hour(entry.endTime)

        assertEquals("10", reopenedStart.hour)
        assertEquals("11", reopenedStart.minute)
        assertTrue(reopenedStart.isPm)

        assertEquals("10", reopenedEnd.hour)
        assertEquals("12", reopenedEnd.minute)
        assertTrue(reopenedEnd.isPm)
    }

    @Test
    fun test_SaveAmAndPmTimes_WithoutChanging() {
        val testCases = listOf(
            // (hour, minute, isPm, expected24, expectedDisplay)
            Triple("08", "30", false) to Pair("08:30", "08:30 AM"),
            Triple("09", "00", false) to Pair("09:00", "09:00 AM"),
            Triple("11", "45", false) to Pair("11:45", "11:45 AM"),
            Triple("12", "00", true) to Pair("12:00", "12:00 PM"),
            Triple("12", "30", true) to Pair("12:30", "12:30 PM"),
            Triple("01", "15", true) to Pair("13:15", "01:15 PM"),
            Triple("05", "45", true) to Pair("17:45", "05:45 PM"),
            Triple("12", "00", false) to Pair("00:00", "12:00 AM"),
            Triple("11", "59", true) to Pair("23:59", "11:59 PM")
        )

        for ((input, expected) in testCases) {
            val (h, m, isPm) = input
            val (expected24, expectedDisplay) = expected

            // 1. Convert to 24-hr
            val t24 = TimeInputHelper.to24Hour(h, m, isPm)
            assertNotNull("Failed to parse $h:$m isPm=$isPm", t24)
            assertEquals("24-hr mismatch for $h:$m isPm=$isPm", expected24, t24)

            // 2. Persist to entry
            val entry = TimetableEntry(
                id = 1L,
                dayOfWeek = "Monday",
                subject = "Test",
                startTime = t24!!,
                endTime = "23:59"
            )

            // 3. Format to 12-hr display
            val display = TimeUtils.formatTo12Hour(entry.startTime)
            assertEquals("Display mismatch for $expected24", expectedDisplay, display)

            // 4. Reopen back to 12-hr components
            val reopened = TimeInputHelper.from24Hour(entry.startTime)
            assertEquals("Hour roundtrip mismatch", h, reopened.hour)
            assertEquals("Minute roundtrip mismatch", m, reopened.minute)
            assertEquals("AM/PM roundtrip mismatch", isPm, reopened.isPm)
        }
    }

    @Test
    fun test_EditClass_PreservesIdAndUpdatesFieldsCorrectly() {
        val originalEntry = TimetableEntry(
            id = 42L,
            dayOfWeek = "Wednesday",
            subject = "test",
            startTime = "21:00",
            endTime = "22:15",
            faculty = "Prof. Smith"
        )

        // User edits time to 10:11 PM - 10:12 PM
        val newStart24 = TimeInputHelper.to24Hour("10", "11", true)!!
        val newEnd24 = TimeInputHelper.to24Hour("10", "12", true)!!

        val updatedEntry = originalEntry.copy(
            startTime = newStart24,
            endTime = newEnd24
        )

        // Verify ID is strictly preserved (no accidental duplicate or new ID)
        assertEquals("ID must be preserved when editing", 42L, updatedEntry.id)
        assertEquals("Weekday must remain unchanged", "Wednesday", updatedEntry.dayOfWeek)
        assertEquals("Subject must remain unchanged", "test", updatedEntry.subject)
        assertEquals("Start time must update", "22:11", updatedEntry.startTime)
        assertEquals("End time must update", "22:12", updatedEntry.endTime)

        // User changes weekday from Wednesday to Thursday
        val dayChangedEntry = updatedEntry.copy(dayOfWeek = "Thursday")
        assertEquals("ID must remain unchanged", 42L, dayChangedEntry.id)
        assertEquals("Weekday must be Thursday", "Thursday", dayChangedEntry.dayOfWeek)
    }

    @Test
    fun test_EndTimeMustBeLaterThanStartTime_Validation() {
        // Same start and end -> invalid
        val start24 = TimeInputHelper.to24Hour("09", "00", true)!! // 21:00
        val endSame24 = TimeInputHelper.to24Hour("09", "00", true)!! // 21:00
        assertFalse(
            "End time must be strictly after start time",
            LocalTime.parse(endSame24).isAfter(LocalTime.parse(start24))
        )

        // End before start (AM/PM mistake, e.g. 10:00 AM after 09:00 PM)
        val endAm24 = TimeInputHelper.to24Hour("10", "00", false)!! // 10:00
        assertFalse(
            "10:00 AM is before 21:00 PM",
            LocalTime.parse(endAm24).isAfter(LocalTime.parse(start24))
        )

        // Valid end after start
        val endValid24 = TimeInputHelper.to24Hour("10", "15", true)!! // 22:15
        assertTrue(
            "22:15 is after 21:00",
            LocalTime.parse(endValid24).isAfter(LocalTime.parse(start24))
        )
    }

    @Test
    fun test_TimetableEngine_RefreshesAfterClassTimeUpdate() {
        val originalClass = TimetableEntry(
            id = 1L,
            dayOfWeek = "Wednesday",
            subject = "test",
            startTime = "21:00",
            endTime = "22:15"
        )

        // At 21:30 (09:30 PM), originalClass should be CURRENT_CLASS
        val testClock = LocalTime.of(21, 30)
        val stateOriginal = TimetableEngine.getScheduleState(
            currentTime = testClock,
            entries = listOf(originalClass),
            smartSilentMasterEnabled = false
        )

        assertEquals(TimetableStatus.CURRENT_CLASS, stateOriginal.status)
        assertEquals("test", stateOriginal.currentClass?.subject)
        assertEquals("21:00", stateOriginal.currentClass?.startTime)

        // Now edit class time to 22:11 - 22:12
        val editedClass = originalClass.copy(
            startTime = "22:11",
            endTime = "22:12"
        )

        // At 21:30, editedClass has NOT started yet! It should be BEFORE_CLASSES
        val stateUpdated = TimetableEngine.getScheduleState(
            currentTime = testClock,
            entries = listOf(editedClass),
            smartSilentMasterEnabled = false
        )

        assertEquals(TimetableStatus.BEFORE_CLASSES, stateUpdated.status)
        assertEquals(null, stateUpdated.currentClass)
        assertEquals("test", stateUpdated.nextClass?.subject)
        assertEquals("22:11", stateUpdated.nextClass?.startTime)

        // And at 22:11:30, it is now CURRENT_CLASS
        val stateDuringEdited = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(22, 11, 30),
            entries = listOf(editedClass),
            smartSilentMasterEnabled = false
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, stateDuringEdited.status)
        assertEquals("test", stateDuringEdited.currentClass?.subject)
    }
}
