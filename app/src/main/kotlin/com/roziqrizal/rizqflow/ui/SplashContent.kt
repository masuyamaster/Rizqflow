package com.roziqrizal.rizqflow.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R

/**
 * Layar splash: wordmark besar di sepertiga atas dan tiga titik berdenyut di bawah, meniru splash
 * Al-Kaukaba (logo lebar tetap, titik bergeser 150 ms). Splash sistem hanya menampilkan ikon aplikasi
 * karena ikonnya dipotong topeng lingkaran; wordmark selebar layar hanya bisa di sini.
 *
 * Warna latar disamakan dengan ic_launcher_background (bukan token tema) supaya menyatu tanpa
 * kedipan dengan splash sistem, yang memakai warna itu juga.
 */
@Composable
fun SplashContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_rizqflow_wordmark),
            contentDescription = null,
            // Rasio dikunci (viewport wordmark 5440x1110) supaya gambar mengisi lebar penuh, bukan ukuran bawaan drawable.
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = 0f, verticalBias = LOGO_VERTICAL_BIAS))
                .padding(horizontal = 5.dp)
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .aspectRatio(5440f / 1110f),
            contentScale = ContentScale.FillWidth,
        )
        PulsingDots(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    }
}

// Setara layout_constraintVertical_bias 0,36 di Al-Kaukaba (0,36 -> 2 * 0,36 - 1 di Compose).
private const val LOGO_VERTICAL_BIAS = -0.28f

/** Tiga titik berwarna tiga bulatan wordmark, berdenyut bergantian. */
@Composable
private fun PulsingDots(modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFC4653A), Color(0xFF3B6FA8), Color(0xFF2A9A80))
    val transition = rememberInfiniteTransition(label = "splash-dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { index, color ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.25f at 0 using LinearEasing
                        1f at 600 using LinearEasing
                        0.25f at 1200 using LinearEasing
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "splash-dot-$index",
            )
            Box(Modifier.size(7.dp).alpha(alpha).background(color, CircleShape))
        }
    }
}
