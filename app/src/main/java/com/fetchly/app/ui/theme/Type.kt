package com.fetchly.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fetchly.app.R

// Bundled, static weights keep the approved typography available offline on Android 10+.
val DisplayFontFamily = FontFamily(
    Font(R.font.cinzel_semibold, FontWeight.SemiBold),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)
val UiFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private val Defaults = Typography()
val Typography = Typography(
    displayLarge = Defaults.displayLarge.copy(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold),
    displayMedium = Defaults.displayMedium.copy(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold),
    displaySmall = Defaults.displaySmall.copy(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = Defaults.titleSmall.copy(fontFamily = UiFontFamily),
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = UiFontFamily),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = UiFontFamily),
    bodySmall = Defaults.bodySmall.copy(fontFamily = UiFontFamily),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = Defaults.labelMedium.copy(fontFamily = UiFontFamily),
    labelSmall = Defaults.labelSmall.copy(fontFamily = UiFontFamily),
)
