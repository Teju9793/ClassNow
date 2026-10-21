package com.teju.classnow

import com.teju.classnow.data.model.CustomClassSchedule
import com.teju.classnow.data.model.CustomSoundInterval
import com.teju.classnow.util.CustomScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CustomScheduleHelperTest {

    @Test
    fun testJsonSerializationAndDeserialization() {
        val schedule = CustomClassSchedule(
            intervals = listOf(
                CustomSoundInterval("10:00", "10:30", CustomSoundInterval.MODE_SILENT),
                CustomSoundInterval("10:30", "11:00", CustomSoundInterval.MODE_NORMAL)
            ),
            endAction = CustomClassSchedule.END_ACTION_NORMAL
        )

        val json = CustomScheduleHelper.toJson(schedule)
        assertNotNull(json)

        val parsed = CustomScheduleHelper.fromJson(json)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.intervals.size)
        assertEquals("10:00", parsed.intervals[0].startTime)
        assertEquals("10:30", parsed.intervals[0].endTime)
        assertEquals(CustomSoundInterval.MODE_SILENT, parsed.intervals[0].mode)

        assertEquals("10:30", parsed.intervals[1].startTime)
        assertEquals("11:00", parsed.intervals[1].endTime)
        assertEquals(CustomSoundInterval.MODE_NORMAL, parsed.intervals[1].mode)

        assertEquals(CustomClassSchedule.END_ACTION_NORMAL, parsed.endAction)
    }

    @Test
    fun testMalformedJsonReturnsNull() {
        assertNull(CustomScheduleHelper.fromJson(null))
        assertNull(CustomScheduleHelper.fromJson(""))
        assertNull(CustomScheduleHelper.fromJson("   "))
        assertNull(CustomScheduleHelper.fromJson("{not valid json}"))
    }

    @Test
    fun testValidationSuccess() {
        val intervals = listOf(
            CustomSoundInterval("10:00", "10:30", CustomSoundInterval.MODE_SILENT),
            CustomSoundInterval("10:30", "11:00", CustomSoundInterval.MODE_NORMAL)
        )
        val error = CustomScheduleHelper.validate(intervals, "10:00", "11:00")
        assertNull("Expected valid schedule but got: $error", error)
    }

    @Test
    fun testValidationErrors() {
        // Start time >= end time
        val invalidTime = listOf(
            CustomSoundInterval("10:30", "10:00", CustomSoundInterval.MODE_SILENT)
        )
        assertNotNull(CustomScheduleHelper.validate(invalidTime, "09:00", "12:00"))

        // Interval start before class start
        val beforeClass = listOf(
            CustomSoundInterval("08:30", "10:00", CustomSoundInterval.MODE_SILENT)
        )
        assertNotNull(CustomScheduleHelper.validate(beforeClass, "09:00", "11:00"))

        // Interval end after class end
        val afterClass = listOf(
            CustomSoundInterval("10:00", "11:30", CustomSoundInterval.MODE_SILENT)
        )
        assertNotNull(CustomScheduleHelper.validate(afterClass, "09:00", "11:00"))

        // Overlapping intervals
        val overlapping = listOf(
            CustomSoundInterval("10:00", "10:35", CustomSoundInterval.MODE_SILENT),
            CustomSoundInterval("10:30", "11:00", CustomSoundInterval.MODE_NORMAL)
        )
        assertNotNull(CustomScheduleHelper.validate(overlapping, "10:00", "11:00"))
    }
}
