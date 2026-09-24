package com.roziqrizal.rizqflow.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R

/**
 * Layar splash: wordmark di tengah layar, lebar penuh dengan margin 10dp kiri-kanan. Cluster
 * lingkaran oranye-biru-hijau (pengganti huruf O di "FLOW") dipisah dari teksnya
 * ([R.drawable.ic_rizqflow_wordmark_mark] + [R.drawable.ic_rizqflow_wordmark_orbit], ditumpuk pas
 * di posisi aslinya) supaya bisa berputar sekaligus melebar-mengecil sendiri selama splash
 * bertahan ([SPLASH_ROTATION_MILLIS], disamakan dengan AppRoot.SPLASH_MIN_MILLIS) tanpa memutar
 * atau menskalakan teksnya. Rotasi: satu putaran penuh searah jarum jam, linear. Skala: naik ke
 * puncak [ORBIT_PEAK_SCALE] di tengah durasi lalu turun lagi, jadi keduanya genap kembali ke
 * posisi dan ukuran semula bersamaan.
 *
 * Warna latar disamakan dengan ic_launcher_background (bukan token tema) supaya menyatu tanpa
 * kedipan dengan splash sistem, yang memakai warna itu juga.
 */
@Composable
fun SplashContent() {
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        rotation.animateTo(360f, animationSpec = tween(SPLASH_ROTATION_MILLIS, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = SPLASH_ROTATION_MILLIS
                1f at 0
                ORBIT_PEAK_SCALE at SPLASH_ROTATION_MILLIS / 2 using LinearOutSlowInEasing
                1f at SPLASH_ROTATION_MILLIS
            },
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                // Rasio dikunci (viewport wordmark 5440x1110) supaya gambar mengisi lebar penuh, bukan ukuran bawaan drawable.
                .aspectRatio(5440f / 1110f),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_rizqflow_wordmark_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painter = painterResource(R.drawable.ic_rizqflow_wordmark_orbit),
                contentDescription = null,
                // Ukuran & posisi harus sinkron dengan bounding box yang dipotong di
                // ic_rizqflow_wordmark_orbit.xml (860x1040, offset 3710,20 dari viewport 5440x1110).
                modifier = Modifier
                    .size(width = maxWidth * ORBIT_WIDTH_FRACTION, height = maxHeight * ORBIT_HEIGHT_FRACTION)
                    .offset(x = maxWidth * ORBIT_OFFSET_X_FRACTION, y = maxHeight * ORBIT_OFFSET_Y_FRACTION)
                    .rotate(rotation.value)
                    .scale(scale.value),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

private const val SPLASH_ROTATION_MILLIS = 3000
private const val ORBIT_PEAK_SCALE = 1.35f
private const val ORBIT_OFFSET_X_FRACTION = 3710f / 5440f
private const val ORBIT_OFFSET_Y_FRACTION = 20f / 1110f
private const val ORBIT_WIDTH_FRACTION = 860f / 5440f
private const val ORBIT_HEIGHT_FRACTION = 1040f / 1110f
