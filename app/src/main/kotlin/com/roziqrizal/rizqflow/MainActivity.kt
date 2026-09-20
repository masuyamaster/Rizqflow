package com.roziqrizal.rizqflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

/**
 * Kerangka awal. Layar sungguhan dikerjakan di Tahap 3 mengikuti prototipe di docs/design/.
 *
 * Target SDK 35 ke atas memaksa tampilan edge-to-edge, jadi konten harus menghormati inset
 * sistem (status bar dan bar navigasi) sejak awal.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = "Rizqflow", modifier = Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
