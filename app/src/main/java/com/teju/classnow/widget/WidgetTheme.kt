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
    val currentAccent: ColorProvider = ColorProvider(if (isDark) Color(0xFFFF6584) else Color(0xFFE11D48)), // Coral Pink
    val nextAccent: ColorProvider = ColorProvider(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)),    // Neon Aqua
    val currentCardBg: ColorProvider = ColorProvider(if (isDark) Color(0xFF1F171B) else Color(0xFFFFF1F2)), // Subtle coral dark
    val nextCardBg: ColorProvider = ColorProvider(if (isDark) Color(0xFF131E26) else Color(0xFFF0F9FF)),    // Subtle aqua dark
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val progressTrack: ColorProvider,
    val buttonBackground: ColorProvider,
    val buttonContent: ColorProvider,
    val silentOnBackground: ColorProvider,
    val silentOnContent: ColorProvider,
    val silentOffBackground: ColorProvider,
    val silentOffContent: ColorProvider,
    val warningBackground: ColorProvider,
    val warningContent: ColorProvider,
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
                    currentAccent = ColorProvider(if (isDark) Color(0xFFFF6584) else Color(0xFFE11D48)),
                    nextAccent = ColorProvider(if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)),
                    currentCardBg = ColorProvider(if (isDark) Color(0xFF1F171B) else Color(0xFFFFF1F2)),
                    nextCardBg = ColorProvider(if (isDark) Color(0xFF131E26) else Color(0xFFF0F9FF)),
                    textPrimary = ColorProvider(dyn.onSurface),
                    textSecondary = ColorProvider(dyn.onSurfaceVariant),
                    progressTrack = ColorProvider(dyn.surfaceVariant),
                    buttonBackground = ColorProvider(dyn.surfaceVariant),
                    buttonContent = ColorProvider(dyn.primary),
                    silentOnBackground = ColorProvider(dyn.primaryContainer),
                    silentOnContent = ColorProvider(dyn.onPrimaryContainer),
                    silentOffBackground = ColorProvider(dyn.surfaceVariant),
                    silentOffContent = ColorProvider(dyn.onSurfaceVariant),
                    warningBackground = ColorProvider(dyn.errorContainer),
                    warningContent = ColorProvider(dyn.onErrorContainer),
                    dividerColor = ColorProvider(dyn.outlineVariant)
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return if (isDark) {
            WidgetColors(
                isDark = true,
                background = ColorProvider(Color(0xFF121214)),       // Deep black/charcoal
                surface = ColorProvider(Color(0xFF1E1E22)),          // Elevated dark surface
                surfaceVariant = ColorProvider(Color(0xFF28282E)),   // Surface variant
                cardBackground = ColorProvider(Color(0xFF1A1A1E)),   // Dark card base
                primaryCyan = ColorProvider(Color(0xFF00E5FF)),      // Neon Aqua
                cyanAccent = ColorProvider(Color(0xFF80EFFF)),       // Light Aqua
                currentAccent = ColorProvider(Color(0xFFFF6584)),    // Vibrant Coral Pink
                nextAccent = ColorProvider(Color(0xFF00E5FF)),       // Crisp Neon Aqua
                currentCardBg = ColorProvider(Color(0xFF1F171B)),    // Subtle coral dark container
                nextCardBg = ColorProvider(Color(0xFF131E26)),       // Subtle aqua dark container
                textPrimary = ColorProvider(Color(0xFFF8FAFC)),      // Crisp white
                textSecondary = ColorProvider(Color(0xFF94A3B8)),    // Muted slate
                progressTrack = ColorProvider(Color(0xFF33333E)),    // Dark progress track
                buttonBackground = ColorProvider(Color(0xFF222228)), // Button surface
                buttonContent = ColorProvider(Color(0xFF00E5FF)),    // Button icon/text
                silentOnBackground = ColorProvider(Color(0xFF004D61)),// Deep aqua container
                silentOnContent = ColorProvider(Color(0xFFBAE6FD)),  // Crisp light aqua text
                silentOffBackground = ColorProvider(Color(0xFF26262E)),// Neutral dark
                silentOffContent = ColorProvider(Color(0xFF94A3B8)), // Muted text
                warningBackground = ColorProvider(Color(0xFF78350F)),// Dark amber container
                warningContent = ColorProvider(Color(0xFFFDE68A)),   // Light amber text
                dividerColor = ColorProvider(Color(0xFF33333E))
            )
        } else {
            WidgetColors(
                isDark = false,
                background = ColorProvider(Color(0xFFF8FAFC)),       // Clean crisp light
                surface = ColorProvider(Color(0xFFFFFFFF)),          // White surface
                surfaceVariant = ColorProvider(Color(0xFFE2E8F0)),   // Soft gray variant
                cardBackground = ColorProvider(Color(0xFFF1F5F9)),   // Light card surface
                primaryCyan = ColorProvider(Color(0xFF0284C7)),      // Ocean Cyan
                cyanAccent = ColorProvider(Color(0xFF0369A1)),       // Accessible Cyan
                currentAccent = ColorProvider(Color(0xFFE11D48)),    // Vibrant Coral Rose/Pink
                nextAccent = ColorProvider(Color(0xFF0284C7)),       // Deep Aqua
                currentCardBg = ColorProvider(Color(0xFFFFF1F2)),    // Soft coral tint
                nextCardBg = ColorProvider(Color(0xFFF0F9FF)),       // Soft aqua tint
                textPrimary = ColorProvider(Color(0xFF0F172A)),      // Dark charcoal
                textSecondary = ColorProvider(Color(0xFF475569)),    // Slate gray
                progressTrack = ColorProvider(Color(0xFFE2E8F0)),    // Soft light track
                buttonBackground = ColorProvider(Color(0xFFE2E8F0)), // Light button surface
                buttonContent = ColorProvider(Color(0xFF0284C7)),    // Deep cyan icon/text
                silentOnBackground = ColorProvider(Color(0xFFAAF0FF)),// Soft aqua container
                silentOnContent = ColorProvider(Color(0xFF001F26)),  // Deep dark aqua
                silentOffBackground = ColorProvider(Color(0xFFE2E8F0)),// Soft neutral
                silentOffContent = ColorProvider(Color(0xFF475569)), // Slate gray
                warningBackground = ColorProvider(Color(0xFFFEF08A)),// Soft amber container
                warningContent = ColorProvider(Color(0xFF713F12)),   // Deep amber text
                dividerColor = ColorProvider(Color(0xFFE2E8F0))
            )
        }
    }
}
