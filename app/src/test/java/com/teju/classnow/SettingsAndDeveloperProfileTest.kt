package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.SilentModeAction
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.ui.settings.DeveloperInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class SettingsAndDeveloperProfileTest {

    @Test
    fun test_DeveloperProfileInfo_Integrity() {
        assertEquals("K. R. Tejas Kumar", DeveloperInfo.NAME)
        assertEquals("tteju97933@gmail.com", DeveloperInfo.EMAIL)
        assertTrue(DeveloperInfo.LINKEDIN_URL.contains("linkedin.com/in/k-r-tejas-kumar-733b26379"))
        assertTrue(DeveloperInfo.INSTAGRAM_URL.contains("instagram.com/tejaspatel_07"))
        assertTrue(DeveloperInfo.BIO.contains("Passionate Android Developer"))
        assertFalse(DeveloperInfo.ROLE.isEmpty())
    }

    @Test
    fun test_WidgetState_TwoClassesDisplayed_CurrentAndNext() {
        val entries = listOf(
            TimetableEntry(id = 1, dayOfWeek = "Wednesday", subject = "Algorithms", startTime = "09:00", endTime = "10:00"),
            TimetableEntry(id = 2, dayOfWeek = "Wednesday", subject = "Data Structures", startTime = "10:15", endTime = "11:15"),
            TimetableEntry(id = 3, dayOfWeek = "Wednesday", subject = "Database Systems", startTime = "11:30", endTime = "12:30")
        )

        // During First Class (09:30): Current is Algorithms, Next is Data Structures
        val inClassState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(9, 30),
            entries = entries,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, inClassState.status)
        assertEquals("Algorithms", inClassState.currentClass?.subject)
        assertEquals("Data Structures", inClassState.nextClass?.subject)
        assertEquals(SilentModeAction.SILENT, inClassState.silentModeAction)
        assertEquals(30L, inClassState.remainingMinutes)

        // During Break (10:05): Status is BREAK, Next is Data Structures
        val inBreakState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 5),
            entries = entries,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.BREAK, inBreakState.status)
        assertNull(inBreakState.currentClass)
        assertEquals("Data Structures", inBreakState.nextClass?.subject)
        assertEquals(10L, inBreakState.remainingMinutes) // 10 min remaining in break

        // Before Classes (08:30): Status is BEFORE_CLASSES, Next is Algorithms
        val beforeState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(8, 30),
            entries = entries,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.BEFORE_CLASSES, beforeState.status)
        assertNull(beforeState.currentClass)
        assertEquals("Algorithms", beforeState.nextClass?.subject)
        assertEquals(30L, beforeState.remainingMinutes)

        // During Final Class (12:00): Current is Database Systems, Next is null (last class of the day)
        val finalClassState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(12, 0),
            entries = entries,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.CURRENT_CLASS, finalClassState.status)
        assertEquals("Database Systems", finalClassState.currentClass?.subject)
        assertNull(finalClassState.nextClass) // No further class today!

        // After All Classes (13:00): Status is NO_MORE_CLASSES, Next is null
        val afterState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(13, 0),
            entries = entries,
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.NO_MORE_CLASSES, afterState.status)
        assertNull(afterState.currentClass)
        assertNull(afterState.nextClass)
        assertEquals(SilentModeAction.NORMAL, afterState.silentModeAction)
    }

    @Test
    fun test_WidgetState_NoClassesToday() {
        val emptyDayState = TimetableEngine.getScheduleState(
            currentTime = LocalTime.of(10, 0),
            entries = emptyList(),
            smartSilentMasterEnabled = true
        )
        assertEquals(TimetableStatus.NO_CLASSES_TODAY, emptyDayState.status)
        assertNull(emptyDayState.currentClass)
        assertNull(emptyDayState.nextClass)
        assertEquals(SilentModeAction.IGNORE, emptyDayState.silentModeAction)
    }
}
