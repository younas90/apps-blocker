package com.pushgate.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Ink = Color(0xFF0B0F14)
val InkRaised = Color(0xFF141A22)
val InkCard = Color(0xFF1B2430)
val Emerald = Color(0xFF34D399)
val EmeraldDim = Color(0xFF10B981)
val Amber = Color(0xFFFBBF24)
val Crimson = Color(0xFFF87171)
val Mist = Color(0xFF94A3B8)
val Chalk = Color(0xFFE2E8F0)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Ink,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = Ink,
    error = Crimson,
    onError = Ink,
    background = Ink,
    onBackground = Chalk,
    surface = InkRaised,
    onSurface = Chalk,
    surfaceVariant = InkCard,
    onSurfaceVariant = Mist,
    outline = Color(0xFF334155)
)

private val LightColors = lightColorScheme(
    primary = EmeraldDim,
    onPrimary = Color.White,
    secondary = Color(0xFFB45309),
    error = Color(0xFFDC2626),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
)

/**
 * The block screen and the challenge are always dark regardless of system theme: they are
 * interruptions, and a white flash at 11pm is its own kind of hostile.
 */
@Composable
fun PushGateTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = forceDark || isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current

    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}

/** Compose hands us a themed ContextWrapper, not the Activity itself. Walk up to find it. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
