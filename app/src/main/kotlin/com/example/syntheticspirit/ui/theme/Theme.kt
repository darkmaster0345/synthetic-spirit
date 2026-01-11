package com.example.syntheticspirit.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Recovery Palette: Growth (Green) and Trust (Navy)
private val ForestGreen = Color(0xFF2E7D32)
private val DeepNavy = Color(0xFF001F3F)
private val OffWhite = Color(0xFFFAFAFA)
private val DeepCharcoal = Color(0xFF121212)
private val MutedSage = Color(0xFF637B64)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784), 
    secondary = Color(0xFF9FA8DA), 
    tertiary = MutedSage,
    background = DeepCharcoal,
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    secondaryContainer = Color(0xFF283593),
    onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFF388E3C),
    onTertiaryContainer = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE2E2E2),
    onSurface = Color(0xFFE2E2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    secondary = DeepNavy,
    tertiary = MutedSage,
    background = OffWhite,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    secondaryContainer = Color(0xFFE8EAF6),
    onSecondaryContainer = DeepNavy,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = ForestGreen,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DeepNavy,
    onSurface = DeepNavy
)

// Large, High-Contrast Typography for Accessibility
private val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun SyntheticSpiritTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}