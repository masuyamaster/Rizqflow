package com.roziqrizal.rizqflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Jarak (dp, tidak ikut ukuran font), dari docs/design/tokens.css. */
@Immutable
data class Spacing(
    val s1: Dp = 4.dp,
    val s2: Dp = 8.dp,
    val s3: Dp = 12.dp,
    val s4: Dp = 16.dp,
    val s5: Dp = 24.dp,
    val s6: Dp = 32.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Bentuk: kecil 8, sedang 16, besar 20 dp. */
val RizqflowShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/**
 * Tema aplikasi. Mengikuti mode gelap sistem; tidak ada pengaturan tema sendiri di versi ini
 * (pilihan Terang, Gelap, Otomatis ada di layar Tampilan, Tahap 6).
 */
@Composable
fun RizqflowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRizqflowColors provides if (darkTheme) RizqflowDarkExtra else RizqflowLightExtra,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RizqflowDarkScheme else RizqflowLightScheme,
            typography = RizqflowTypography,
            shapes = RizqflowShapes,
            content = content,
        )
    }
}

/** Warna identitas ruang dan status: `MaterialTheme.rizqflow.room(1)`. */
val MaterialTheme.rizqflow: RizqflowExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalRizqflowColors.current

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

/**
 * Membatasi skala font pengguna untuk isi [content]. Angka hero sudah besar sehingga dibatasi
 * 1,25x, dan label navigasi 1,3x, supaya tidak pecah di layar sempit (docs/design/README.md).
 * Teks lain tetap mengikuti ukuran font pengguna penuh.
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, max)),
        content = content,
    )
}

const val HERO_MAX_FONT_SCALE = 1.25f
const val NAV_LABEL_MAX_FONT_SCALE = 1.3f
