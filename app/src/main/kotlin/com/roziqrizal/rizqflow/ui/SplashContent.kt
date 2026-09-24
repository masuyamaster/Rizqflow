package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R

/**
 * Layar splash: wordmark di tengah layar, lebar penuh dengan margin 10dp kiri-kanan.
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
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .aspectRatio(5440f / 1110f),
            contentScale = ContentScale.FillWidth,
        )
    }
}
