package com.teju.classnow

import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.util.CsvHelper
import org.junit.Assert.*
import org.junit.Test

class CsvHelperTest {

    @Test
    fun test_ParseValidCsv_ReturnsEntries() {
        val csv = """
            Day,Subject,Start,End,Faculty,SilentRule
            Monday,Mathematics,09:00,10:00,Dr. Euler,DEFAULT
            Monday,Physics,10:00,11:00,Dr. Newton,FORCE_SILENT
            Tuesday,Music,14:00,15:00,Prof. Mozart,FORCE_SOUND
        """.trimIndent()

        val result = CsvHelper.parseCsv(csv)
        assertTrue(result.errors.isEmpty())
        assertEquals(3, result.entries.size)

        val math = result.entries[0]
        assertEquals("Monday", math.dayOfWeek)
        assertEquals("Mathematics", math.subject)
        assertEquals("09:00", math.startTime)
        assertEquals("10:00", math.endTime)
        assertEquals("Dr. Euler", math.faculty)
        assertEquals(TimetableEntry.RULE_DEFAULT, math.silentModeRule)

        val physics = result.entries[1]
        assertEquals(TimetableEntry.RULE_FORCE_SILENT, physics.silentModeRule)

        val music = result.entries[2]
        assertEquals(TimetableEntry.RULE_FORCE_SOUND, music.silentModeRule)
    }

    @Test
    fun test_ParseLegacyCsvWithoutSilentRule_DefaultsToDefault() {
        val csv = """
            Day,Subject,Start,End,Faculty
            Wednesday,Chemistry,09:00,10:00,Dr. Curie
        """.trimIndent()

        val result = CsvHelper.parseCsv(csv)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.entries.size)
        assertEquals(TimetableEntry.RULE_DEFAULT, result.entries[0].silentModeRule)
        assertEquals("Dr. Curie", result.entries[0].faculty)
    }

    @Test
    fun test_InvalidDayOrTime_ReturnsErrors() {
        val invalidCsv = """
            Day,Subject,Start,End
            InvalidDay,Math,09:00,10:00
            Monday,Physics,25:00,10:00
            Tuesday,Bio,11:00,10:00
        """.trimIndent()

        val result = CsvHelper.parseCsv(invalidCsv)
        assertFalse(result.errors.isEmpty())
        assertTrue(result.errors.any { it.contains("Invalid day") })
        assertTrue(result.errors.any { it.contains("must be in HH:mm format") })
        assertTrue(result.errors.any { it.contains("must be after start time") })
    }

    @Test
    fun test_OverlappingClasses_GeneratesWarning() {
        val overlapCsv = """
            Day,Subject,Start,End
            Monday,Math,09:00,10:30
            Monday,Physics,10:00,11:00
        """.trimIndent()

        val result = CsvHelper.parseCsv(overlapCsv)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.entries.size)
        assertFalse(result.warnings.isEmpty())
        assertTrue(result.warnings.any { it.contains("Overlaps") })
    }

    @Test
    fun test_ExportAndReImport_MatchesOriginal() {
        val original = listOf(
            TimetableEntry(
                id = 1,
                dayOfWeek = "Monday",
                subject = "History",
                startTime = "08:00",
                endTime = "09:00",
                faculty = "Dr. Herodotus",
                silentModeRule = TimetableEntry.RULE_DEFAULT
            ),
            TimetableEntry(
                id = 2,
                dayOfWeek = "Friday",
                subject = "Art",
                startTime = "14:00",
                endTime = "16:00",
                faculty = "Da Vinci",
                silentModeRule = TimetableEntry.RULE_FORCE_SOUND
            )
        )

        val csvExport = CsvHelper.exportToCsv(original)
        val parseResult = CsvHelper.parseCsv(csvExport)

        assertTrue(parseResult.errors.isEmpty())
        assertEquals(2, parseResult.entries.size)
        assertEquals("History", parseResult.entries[0].subject)
        assertEquals("Art", parseResult.entries[1].subject)
        assertEquals(TimetableEntry.RULE_FORCE_SOUND, parseResult.entries[1].silentModeRule)
    }
}
