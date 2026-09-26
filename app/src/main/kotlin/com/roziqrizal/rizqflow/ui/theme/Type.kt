package com.roziqrizal.rizqflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.roziqrizal.rizqflow.R

/*
 * Tipografi dari docs/design/tokens.css: Manrope untuk UI dan semua angka, Libre Caslon Text
 * hanya untuk judul layar dan judul bagian. Keduanya font variabel (lisensi OFL, salinan lisensi
 * di assets/licenses); minSdk 26 mendukung font variabel.
 */

private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun caslon(weight: Int) = Font(
    resId = R.font.libre_caslon_text,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** UI dan semua angka. */
val ManropeFamily = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700))

/** Hanya judul; jangan dipakai untuk angka. */
val CaslonFamily = FontFamily(caslon(400), caslon(700))

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * Skala teks (sp, ikut ukuran font pengguna):
 * hero 40, judul layar 28, judul bagian 18, isi 14, label 12.
 * Angka hero dibatasi 1,25x lewat [CappedFontScale], bukan di sini.
 */
val RizqflowTypography: Typography = Typography().withFamily(ManropeFamily).copy(
    // Angka besar (Rezeki bulan ini, dan sejenisnya): Manrope, angka selebar sama.
    displayMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = "tnum",
    ),
    // Judul layar.
    headlineMedium = TextStyle(
        fontFamily = CaslonFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    // Judul bagian.
    titleLarge = TextStyle(
        fontFamily = CaslonFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
