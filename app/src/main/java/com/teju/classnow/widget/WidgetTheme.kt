package com.teju.classnow.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

data class WidgetColors(
    val isDark: Boolean,
    val background: ColorProvider,
    val surface: ColorProvider,
    val surfaceVariant: ColorProvider,
    val cardBackground: ColorProvider,
    val primaryCyan: ColorProvider,
    val cyanAccent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val progressTrack: ColorProvider,
    val buttonBackground: ColorProvider,
    val buttonContent: ColorProvider,
    val silentOnBackground: ColorProvider,
    val silentOnContent: ColorProvider,
    val silentOffBackground: ColorProvider,
    val silentOffContent: ColorProvider,
    val dividerColor: ColorProvider
)

object WidgetTheme {

    fun resolveIsDark(context: Context? = null, themeMode: String): Boolean {
        return when (themeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val nightMode = context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun getColors(context: Context, themeMode: String): WidgetColors {
        val isDark = resolveIsDark(context, themeMode)
        val useDynamic = (themeMode == "SYSTEM") && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        if (useDynamic) {
            try {
                val dyn = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                return WidgetColors(
                    isDark = isDark,
                    background = ColorProvider(dyn.background),
                    surface = ColorProvider(dyn.surface),
                    surfaceVariant = ColorProvider(dyn.surfaceVariant),
                    cardBackground = ColorProvider(dyn.surfaceContainer),
                    primaryCyan = ColorProvider(dyn.primary),
                    cyanAccent = ColorProvider(dyn.secondary),
                    textPrimary = ColorProvider(dyn.onSurface),
                    textSecondary = ColorProvider(dyn.onSurfaceVariant),
                    progressTrack = ColorProvider(dyn.surfaceVariant),
                    buttonBackground = ColorProvider(dyn.surfaceVariant),
                    buttonContent = ColorProvider(dyn.primary),
                    silentOnBackground = ColorProvider(dyn.primaryContainer),
                    silentOnContent = ColorProvider(dyn.onPrimaryContainer),
                    silentOffBackground = ColorProvider(dyn.surfaceVariant),
                    silentOffContent = ColorProvider(dyn.onSurfaceVariant),
                    dividerColor = ColorProvider(dyn.outlineVariant)
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return if (isDark) {
            WidgetColors(
                isDark = true,
                background = ColorProvider(Color(0xFF0F172A)),       // Slate Navy 900
                surface = ColorProvider(Color(0xFF1E293B)),          // Slate Navy 800
                surfaceVariant = ColorProvider(Color(0xFF273549)),   // Slate Navy 700
                cardBackground = ColorProvider(Color(0xFF182234)),   // Dark card
                primaryCyan = ColorProvider(Color(0xFF00B4D8)),      // Clear Aqua / Cyan
                cyanAccent = ColorProvider(Color(0xFF90E0EF)),       // Light Cyan
                textPrimary = ColorProvider(Color(0xFFF8FAFC)),      // Crisp white
                textSecondary = ColorProvider(Color(0xFF94A3B8)),    // Muted slate
                progressTrack = ColorProvider(Color(0xFF334155)),    // Dark progress track
                buttonBackground = ColorProvider(Color(0xFF1E293B)), // Button surface
                buttonContent = ColorProvider(Color(0xFF90E0EF)),    // Button icon/text
                silentOnBackground = ColorProvider(Color(0xFF004D61)),// Deep aqua container
                silentOnContent = ColorProvider(Color(0xFFBAE6FD)),  // Crisp light aqua text
                silentOffBackground = ColorProvider(Color(0xFF2A3447)),// Neutral dark
                silentOffContent = ColorProvider(Color(0xFF94A3B8)), // Muted text
                dividerColor = ColorProvider(Color(0xFF334155))
            )
        } else {
            WidgetColors(
                isDark = false,
                background = ColorProvider(Color(0xFFF8FAFC)),       // Clean crisp light
                surface = ColorProvider(Color(0xFFFFFFFF)),          // White surface
                surfaceVariant = ColorProvider(Color(0xFFE2E8F0)),   // Soft gray variant
                cardBackground = ColorProvider(Color(0xFFF1F5F9)),   // Light card surface
                primaryCyan = ColorProvider(Color(0xFF00687A)),      // Deep Cyan for contrast
                cyanAccent = ColorProvider(Color(0xFF00838F)),       // Accessible Cyan
                textPrimary = ColorProvider(Color(0xFF0F172A)),      // Dark charcoal
                textSecondary = ColorProvider(Color(0xFF475569)),    // Slate gray
                progressTrack = ColorProvider(Color(0xFFE2E8F0)),    // Soft light track
                buttonBackground = ColorProvider(Color(0xFFE2E8F0)), // Light button surface
                buttonContent = ColorProvider(Color(0xFF00687A)),    // Deep cyan icon/text
                silentOnBackground = ColorProvider(Color(0xFFAAF0FF)),// Soft aqua container
                silentOnContent = ColorProvider(Color(0xFF001F26)),  // Deep dark aqua
                silentOffBackground = ColorProvider(Color(0xFFE2E8F0)),// Soft neutral
                silentOffContent = ColorProvider(Color(0xFF475569)), // Slate gray
                dividerColor = ColorProvider(Color(0xFFE2E8F0))
            )
        }
    }
}
