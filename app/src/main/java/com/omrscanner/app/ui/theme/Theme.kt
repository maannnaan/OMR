package com.omrscanner.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// One calm palette, the same as Lecturer.bd's: white cards on a warm grey page, and one green for
// what to do next. Every text colour reads at 4.5:1 or better on the page and on the cards.
val PageBg = Color(0xFFF6F5F1)
val CardBg = Color(0xFFFFFFFF)
val InsetBg = Color(0xFFF1F0EB)
val Ink = Color(0xFF17191C)
val Muted = Color(0xFF5B6068)
val Line = Color(0xFFE3E1DA)
val FieldLine = Color(0xFFC9C6BC)
val Brand = Color(0xFF0B6B4F)
val BrandSoft = Color(0xFFE3F1EA)
val Warn = Color(0xFF8A5300)
val WarnSoft = Color(0xFFFFF3CC)
val Bad = Color(0xFFB4361F)
val BadSoft = Color(0xFFFCE8E4)

private val scheme = lightColorScheme(
    primary = Brand, onPrimary = Color.White, primaryContainer = BrandSoft, onPrimaryContainer = Brand,
    secondary = Brand, onSecondary = Color.White, tertiary = Brand,
    background = PageBg, onBackground = Ink, surface = CardBg, onSurface = Ink,
    surfaceVariant = InsetBg, onSurfaceVariant = Muted, outline = FieldLine, outlineVariant = Line,
    surfaceContainerLowest = CardBg, surfaceContainerLow = CardBg, surfaceContainer = CardBg,
    surfaceContainerHigh = CardBg, surfaceContainerHighest = CardBg, surfaceTint = Color.Transparent,
    error = Bad, onError = Color.White,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Muted),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun OmrTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
