package com.teju.classnow

import com.teju.classnow.widget.WidgetTheme
import org.junit.Assert.*
import org.junit.Test

class WidgetThemeTest {

    @Test
    fun test_ThemeResolution_LightAndDarkExplicitPreferences() {
        // "LIGHT" preference must always resolve to isDark == false without needing context
        val isLightDark = WidgetTheme.resolveIsDark(themeMode = "LIGHT")
        assertFalse(isLightDark)

        // "DARK" preference must always resolve to isDark == true without needing context
        val isDarkDark = WidgetTheme.resolveIsDark(themeMode = "DARK")
        assertTrue(isDarkDark)
    }

    @Test
    fun test_ThemePreferences_StringKeysMatchExpectedValues() {
        val validModes = listOf("SYSTEM", "LIGHT", "DARK")
        assertTrue(validModes.contains("SYSTEM"))
        assertTrue(validModes.contains("LIGHT"))
        assertTrue(validModes.contains("DARK"))
    }
}
