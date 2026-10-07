package com.ferdousmunny.cgpacalculator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Core brand palette -- a rich indigo/violet paired with teal and warm amber
// accents for a modern, professional, education-app feel.
val BrandPrimary = Color(0xFF4550CA)
val BrandPrimaryDark = Color(0xFF2F3999)
val BrandOnPrimaryContainer = Color(0xFF1A1F5C)
val BrandSecondary = Color(0xFF00A896)
val BrandSecondaryContainer = Color(0xFFD3F5EF)
val BrandTertiary = Color(0xFFFFA726)
val BrandTertiaryContainer = Color(0xFFFFE7C2)
val LightBackground = Color(0xFFF6F7FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEDFA)
val DarkBackground = Color(0xFF14141C)
val DarkSurface = Color(0xFF1E1E29)

// Universal signal colors used for CGPA up/down indicators across the app.
val SuccessGreen = Color(0xFF2E7D32)
val DangerRed = Color(0xFFC62828)

// Kept for backward compatibility with any older references.
val Indigo = BrandPrimary
val Teal = BrandSecondary
val Amber = BrandTertiary

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = Color(0xFF00433C),
    tertiary = BrandTertiary,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = Color(0xFF7A4A00),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49495C),
    outlineVariant = Color(0xFFDADCF0),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFBDEDD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF1A1F3D),
    primaryContainer = Color(0xFF2A2F55),
    onPrimaryContainer = Color(0xFFDCE0FF),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF00514A),
    tertiary = BrandTertiary,
    tertiaryContainer = Color(0xFF5C3D00),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2A2A38),
    outlineVariant = Color(0xFF3A3A4A),
    error = Color(0xFFFF6E6E),
    errorContainer = Color(0xFF5C1A1A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CGPATheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
