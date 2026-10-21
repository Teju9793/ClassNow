package com.teju.classnow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AquaPrimaryDark,
    onPrimary = AquaOnPrimaryDark,
    primaryContainer = AquaContainerDark,
    onPrimaryContainer = AquaOnContainerDark,
    secondary = SlateSecondaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = AquaOnContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = AquaPrimaryLight,
    onPrimary = AquaOnPrimaryLight,
    primaryContainer = AquaContainerLight,
    onPrimaryContainer = AquaOnContainerLight,
    secondary = SlateSecondaryLight,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = AquaOnContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

@Composable
fun ClassNowTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val dynamicColor = (themeMode == "SYSTEM") && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}