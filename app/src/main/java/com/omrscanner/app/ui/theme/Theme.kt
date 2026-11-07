package com.omrscanner.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm paper + ink palette
val Paper = Color(0xFFF6F4EF)
val CardBg = Color(0xFFFFFEFB)
val Ink = Color(0xFF1C1B19)
val Muted = Color(0xFF6F6B64)
val Faint = Color(0xFFA8A39A)
val Line = Color(0xFFE6E2DA)
val Divider = Color(0xFFEFECE5)
val SegBg = Color(0xFFEBE8E1)
val Accent = Color(0xFFF97316)
val Good = Color(0xFF2F8F57)
val GoodBg = Color(0xFFE6F4EA)
val Warn = Color(0xFFB7791F)
val WarnBg = Color(0xFFFBF2E1)
val WarnLine = Color(0xFFEBD3A6)
val Bad = Color(0xFFC0432F)
val BadBg = Color(0xFFFBE9E5)
val Teal = Color(0xFF1F6F6A)

val Mono = FontFamily.Monospace

private val scheme = lightColorScheme(
    primary = Ink, onPrimary = Paper, secondary = Accent, onSecondary = Ink,
    background = Paper, onBackground = Ink, surface = CardBg, onSurface = Ink,
    surfaceVariant = SegBg, onSurfaceVariant = Muted, outline = Line, error = Bad,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = Muted),
    labelSmall = TextStyle(fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.2.sp),
)

@Composable
fun OmrTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
