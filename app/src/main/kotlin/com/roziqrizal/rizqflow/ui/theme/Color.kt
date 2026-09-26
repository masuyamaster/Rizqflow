package com.roziqrizal.rizqflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Warna Rizqflow. Semua nilai berasal dari docs/design/tokens.css (satu-satunya sumber); nama
 * perannya sama dengan Material 3 sehingga langsung dipetakan ke ColorScheme. Mode gelap adalah
 * turunan dari palet sage dan masih menunggu konfirmasi pemilik (lihat docs/design/README.md).
 */

// Warna netral yang dipakai kedua mode.
private val Sage = Color(0xFF4D6359)

internal val RizqflowLightScheme: ColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FA79B),
    onPrimaryContainer = Color(0xFF273C33),
    inversePrimary = Color(0xFFB3CCBF),
    primaryFixed = Color(0xFFCFE8DB),
    secondary = Color(0xFF48626E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBE7F5),
    onSecondaryContainer = Color(0xFF4E6874),
    // Token tidak mendefinisikan tertiary; disamakan dengan secondary supaya tidak muncul warna asing.
    tertiary = Color(0xFF48626E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE7F5),
    onTertiaryContainer = Color(0xFF4E6874),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1C1B),
    surfaceVariant = Color(0xFFE3E2E0),
    onSurfaceVariant = Color(0xFF424845),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F1),
    surfaceContainer = Color(0xFFEFEEEC),
    surfaceContainerHigh = Color(0xFFE9E8E6),
    surfaceContainerHighest = Color(0xFFE3E2E0),
    inverseSurface = Color(0xFF30312F),
    inverseOnSurface = Color(0xFFF2F0EE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF727874),
    outlineVariant = Color(0xFFC2C8C3),
)

internal val RizqflowDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB3CCBF),
    onPrimary = Color(0xFF1F352B),
    primaryContainer = Color(0xFF354B42),
    onPrimaryContainer = Color(0xFFCFE8DB),
    inversePrimary = Sage,
    primaryFixed = Color(0xFF354B42),
    secondary = Color(0xFFAFCBD9),
    onSecondary = Color(0xFF1B3542),
    secondaryContainer = Color(0xFF314A56),
    onSecondaryContainer = Color(0xFFCBE7F5),
    tertiary = Color(0xFFAFCBD9),
    onTertiary = Color(0xFF1B3542),
    tertiaryContainer = Color(0xFF314A56),
    onTertiaryContainer = Color(0xFFCBE7F5),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3E0),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE3E3E0),
    surfaceVariant = Color(0xFF333533),
    onSurfaceVariant = Color(0xFFC2C8C3),
    surfaceContainerLowest = Color(0xFF0D0F0E),
    surfaceContainerLow = Color(0xFF1A1C1A),
    surfaceContainer = Color(0xFF1E201E),
    surfaceContainerHigh = Color(0xFF282A28),
    surfaceContainerHighest = Color(0xFF333533),
    inverseSurface = Color(0xFFE3E3E0),
    inverseOnSurface = Color(0xFF30312F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C928E),
    outlineVariant = Color(0xFF424845),
)

/**
 * Warna di luar ColorScheme Material: identitas ruang (kategorikal, tiga slot pertama sudah
 * divalidasi untuk buta warna dan kontras 3:1) dan status (tetap, tidak ikut tema).
 *
 * Status selalu dipasangkan dengan ikon dan teks; warna hanya penguat.
 */
@Immutable
class RizqflowExtraColors(
    val room1: Color,
    val room2: Color,
    val room3: Color,
    /** Ruang ke-4 dan seterusnya (Pro) belum punya warna tervalidasi, jadi memakai warna utama. */
    val roomNeutral: Color,
    val statusGood: Color,
    val statusWarning: Color,
    val statusSerious: Color,
    val statusCritical: Color,
) {
    /** Warna identitas ruang untuk slot 1, 2, 3; slot lain netral. */
    fun room(slot: Int): Color = when (slot) {
        1 -> room1
        2 -> room2
        3 -> room3
        else -> roomNeutral
    }
}

internal val RizqflowLightExtra = RizqflowExtraColors(
    room1 = Color(0xFF3B6FA8),
    room2 = Color(0xFFC4653A),
    room3 = Color(0xFF2A9A80),
    roomNeutral = Sage,
    statusGood = Color(0xFF0CA30C),
    // Lebih gelap dari statusWarning mode gelap: FAB219 hanya kontras ~1,6:1 terhadap latar
    // warning-nya sendiri di mode terang (di bawah 3:1 WCAG untuk ikon bermakna); B45309 ~4,8:1.
    statusWarning = Color(0xFFB45309),
    statusSerious = Color(0xFFEC835A),
    statusCritical = Color(0xFFD03B3B),
)

internal val RizqflowDarkExtra = RizqflowExtraColors(
    room1 = Color(0xFF4384D0),
    room2 = Color(0xFFCF6A3A),
    room3 = Color(0xFF22A37A),
    roomNeutral = Color(0xFFB3CCBF),
    statusGood = Color(0xFF0CA30C),
    statusWarning = Color(0xFFFAB219),
    statusSerious = Color(0xFFEC835A),
    statusCritical = Color(0xFFD03B3B),
)

internal val LocalRizqflowColors = staticCompositionLocalOf { RizqflowLightExtra }
