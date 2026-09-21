package com.roziqrizal.rizqflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roziqrizal.rizqflow.ui.RizqflowApp
import com.roziqrizal.rizqflow.ui.theme.RizqflowTheme

/**
 * Titik masuk. Layar sungguhan dikerjakan di Tahap 3 dan seterusnya mengikuti prototipe di docs/design/.
 *
 * Target SDK 35 ke atas memaksa tampilan edge-to-edge; Scaffold di [RizqflowApp] yang menghormati
 * inset sistem (status bar dan bar navigasi). Warna ikon bar sistem mengikuti tema terang atau gelap
 * (`enableEdgeToEdge` memilihnya otomatis), yang menyelesaikan ikon status bar pucat di emulator gelap.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RizqflowTheme {
                RizqflowApp()
            }
        }
    }
}
