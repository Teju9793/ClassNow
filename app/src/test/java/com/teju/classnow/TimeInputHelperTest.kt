package com.teju.classnow

import com.teju.classnow.util.TimeInputHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeInputHelperTest {

    @Test
    fun testMidnightConversions() {
        // 12:00 AM -> 00:00
        val t24 = TimeInputHelper.to24Hour("12", "00", isPm = false)
        assertEquals("00:00", t24)

        // 12:30 AM -> 00:30
        val t24Half = TimeInputHelper.to24Hour("12", "30", isPm = false)
        assertEquals("00:30", t24Half)

        // Inverse
        val t12Midnight = TimeInputHelper.from24Hour("00:00")
        assertEquals("12", t12Midnight.hour)
        assertEquals("00", t12Midnight.minute)
        assertFalse(t12Midnight.isPm)

        val t12Half = TimeInputHelper.from24Hour("00:30")
        assertEquals("12", t12Half.hour)
        assertEquals("30", t12Half.minute)
        assertFalse(t12Half.isPm)
    }

    @Test
    fun testNoonConversions() {
        // 12:00 PM -> 12:00
        val t24Noon = TimeInputHelper.to24Hour("12", "00", isPm = true)
        assertEquals("12:00", t24Noon)

        // 12:45 PM -> 12:45
        val t24AfterNoon = TimeInputHelper.to24Hour("12", "45", isPm = true)
        assertEquals("12:45", t24AfterNoon)

        // Inverse
        val t12Noon = TimeInputHelper.from24Hour("12:00")
        assertEquals("12", t12Noon.hour)
        assertEquals("00", t12Noon.minute)
        assertTrue(t12Noon.isPm)
    }

    @Test
    fun testAfternoonAndEveningConversions() {
        // 01:15 PM -> 13:15
        val t24 = TimeInputHelper.to24Hour("01", "15", isPm = true)
        assertEquals("13:15", t24)

        // 1:15 PM with single digit hour -> 13:15
        val t24Single = TimeInputHelper.to24Hour("1", "15", isPm = true)
        assertEquals("13:15", t24Single)

        // 11:59 PM -> 23:59
        val t24Night = TimeInputHelper.to24Hour("11", "59", isPm = true)
        assertEquals("23:59", t24Night)

        // Inverse
        val t12 = TimeInputHelper.from24Hour("13:15")
        assertEquals("01", t12.hour)
        assertEquals("15", t12.minute)
        assertTrue(t12.isPm)
    }

    @Test
    fun testMorningConversions() {
        // 11:15 AM -> 11:15
        val t24 = TimeInputHelper.to24Hour("11", "15", isPm = false)
        assertEquals("11:15", t24)

        // 09:05 AM -> 09:05
        val t24Morning = TimeInputHelper.to24Hour("09", "05", isPm = false)
        assertEquals("09:05", t24Morning)

        // Inverse
        val t12 = TimeInputHelper.from24Hour("09:05")
        assertEquals("09", t12.hour)
        assertEquals("05", t12.minute)
        assertFalse(t12.isPm)
    }

    @Test
    fun testNormalizeHour() {
        // Hour 0 converts to 12 AM
        val norm0 = TimeInputHelper.normalizeHour("0", currentIsPm = true)
        assertEquals(Pair("12", false), norm0)

        // Hour 1-12 preserves currentIsPm
        val norm7Am = TimeInputHelper.normalizeHour("7", currentIsPm = false)
        assertEquals(Pair("07", false), norm7Am)

        val norm7Pm = TimeInputHelper.normalizeHour("07", currentIsPm = true)
        assertEquals(Pair("07", true), norm7Pm)

        // Hour 13-23 automatically converts to 12-hour and sets isPm = true
        val norm19 = TimeInputHelper.normalizeHour("19", currentIsPm = false)
        assertEquals(Pair("07", true), norm19)

        val norm23 = TimeInputHelper.normalizeHour("23", currentIsPm = false)
        assertEquals(Pair("11", true), norm23)

        // Invalid hours return null
        assertNull(TimeInputHelper.normalizeHour("24", currentIsPm = false))
        assertNull(TimeInputHelper.normalizeHour("-1", currentIsPm = false))
        assertNull(TimeInputHelper.normalizeHour("abc", currentIsPm = false))
    }

    @Test
    fun testForgiving24HourInput() {
        // Entering 19 with AM selected converts to 19:30 (7:30 PM)
        val t19 = TimeInputHelper.to24Hour("19", "30", isPm = false)
        assertEquals("19:30", t19)

        // Entering 0 with PM selected converts to 00:15 (12:15 AM)
        val t0 = TimeInputHelper.to24Hour("0", "15", isPm = true)
        assertEquals("00:15", t0)
    }

    @Test
    fun testInvalidInputsReturnNull() {
        // Hour > 23 or < 0
        assertNull(TimeInputHelper.to24Hour("24", "00", isPm = false))
        assertNull(TimeInputHelper.to24Hour("25", "00", isPm = false))
        assertNull(TimeInputHelper.to24Hour("-1", "00", isPm = false))

        // Minute > 59 or < 0
        assertNull(TimeInputHelper.to24Hour("10", "60", isPm = false))
        assertNull(TimeInputHelper.to24Hour("10", "-5", isPm = false))

        // Non-numeric
        assertNull(TimeInputHelper.to24Hour("ab", "00", isPm = false))
        assertNull(TimeInputHelper.to24Hour("10", "xx", isPm = false))
        assertNull(TimeInputHelper.to24Hour("", "", isPm = false))
    }

    @Test
    fun testNormalizeMinute() {
        assertEquals("00", TimeInputHelper.normalizeMinute("0"))
        assertEquals("05", TimeInputHelper.normalizeMinute("5"))
        assertEquals("30", TimeInputHelper.normalizeMinute("30"))
        assertEquals("59", TimeInputHelper.normalizeMinute("59"))
        assertNull(TimeInputHelper.normalizeMinute("60"))
        assertNull(TimeInputHelper.normalizeMinute("-1"))
        assertNull(TimeInputHelper.normalizeMinute("abc"))
    }

    @Test
    fun testPhase1RequiredTimes() {
        // 09:00 AM
        val t0900 = TimeInputHelper.to24Hour("09", "00", isPm = false)
        assertEquals("09:00", t0900)
        val t0900Recovered = TimeInputHelper.from24Hour("09:00")
        assertEquals("09", t0900Recovered.hour)
        assertEquals("00", t0900Recovered.minute)
        assertFalse(t0900Recovered.isPm)

        // 10:30 AM
        val t1030 = TimeInputHelper.to24Hour("10", "30", isPm = false)
        assertEquals("10:30", t1030)
        val t1030Recovered = TimeInputHelper.from24Hour("10:30")
        assertEquals("10", t1030Recovered.hour)
        assertEquals("30", t1030Recovered.minute)
        assertFalse(t1030Recovered.isPm)

        // 12:00 PM
        val t1200 = TimeInputHelper.to24Hour("12", "00", isPm = true)
        assertEquals("12:00", t1200)
        val t1200Recovered = TimeInputHelper.from24Hour("12:00")
        assertEquals("12", t1200Recovered.hour)
        assertEquals("00", t1200Recovered.minute)
        assertTrue(t1200Recovered.isPm)

        // 07:15 PM
        val t0715 = TimeInputHelper.to24Hour("07", "15", isPm = true)
        assertEquals("19:15", t0715)
        val t0715Recovered = TimeInputHelper.from24Hour("19:15")
        assertEquals("07", t0715Recovered.hour)
        assertEquals("15", t0715Recovered.minute)
        assertTrue(t0715Recovered.isPm)
    }

    @Test
    fun testTypingAndBlurBehavior() {
        // User types single digit "1" intending to type "10"
        // Raw typing should be accepted without premature modification
        val rawInput = "1".filter { it.isDigit() }.take(2)
        assertEquals("1", rawInput)

        // If user leaves focus with "1", normalizeHour pads to "01"
        val blurred1 = TimeInputHelper.normalizeHour(rawInput, currentIsPm = false)
        assertEquals(Pair("01", false), blurred1)

        // User deletes '9' from '09': remaining raw string is "0"
        val rawBackspace = "0".filter { it.isDigit() }.take(2)
        assertEquals("0", rawBackspace)

        // On blur, hour 0 normalizes to "12" AM
        val blurred0 = TimeInputHelper.normalizeHour(rawBackspace, currentIsPm = false)
        assertEquals(Pair("12", false), blurred0)

        // User typed "10", minute "3"
        // On blur, minute "3" becomes "03"
        assertEquals("03", TimeInputHelper.normalizeMinute("3"))
    }

    @Test
    fun testRoundtripConversion() {
        val sampleTimes = listOf("00:00", "00:15", "07:30", "11:59", "12:00", "12:30", "13:00", "17:45", "23:59")
        for (time24 in sampleTimes) {
            val t12 = TimeInputHelper.from24Hour(time24)
            val recovered = TimeInputHelper.to24Hour(t12.hour, t12.minute, t12.isPm)
            assertEquals("Roundtrip failed for $time24", time24, recovered)
        }
    }
}
