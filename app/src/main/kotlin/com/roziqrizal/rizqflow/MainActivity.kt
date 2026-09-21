package com.roziqrizal.rizqflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.roziqrizal.rizqflow.auth.AuthController
import com.roziqrizal.rizqflow.auth.AuthUiState
import com.roziqrizal.rizqflow.auth.GoogleAuthProvider
import com.roziqrizal.rizqflow.auth.SharedPrefsAccountStore
import com.roziqrizal.rizqflow.auth.SharedPrefsSessionStore
import com.roziqrizal.rizqflow.domain.auth.LocalAccountService
import com.roziqrizal.rizqflow.domain.auth.Pbkdf2PasswordHasher
import com.roziqrizal.rizqflow.ui.AppRoot
import com.roziqrizal.rizqflow.ui.theme.RizqflowTheme
import java.util.UUID

/**
 * Titik masuk. Alur: splash sistem, lalu halaman masuk (bila belum pernah masuk) atau langsung
 * menu utama (bila sudah punya riwayat masuk).
 *
 * Target SDK 35 ke atas memaksa tampilan edge-to-edge; Scaffold di RizqflowApp yang menghormati
 * inset sistem. Warna ikon bar sistem mengikuti tema terang atau gelap (`enableEdgeToEdge`).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Harus dipanggil sebelum super.onCreate: mengganti tema splash ke tema aplikasi.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = AuthController(
            store = SharedPrefsSessionStore(this),
            provider = GoogleAuthProvider(this, getString(R.string.google_web_client_id)),
            accounts = LocalAccountService(
                store = SharedPrefsAccountStore(this),
                hasher = Pbkdf2PasswordHasher(),
                newAccountId = { UUID.randomUUID().toString() },
            ),
            scope = lifecycleScope,
        )
        // Splash tetap tampil sampai riwayat masuk terbaca, supaya tidak ada kedipan halaman masuk.
        splash.setKeepOnScreenCondition { controller.state.value is AuthUiState.Loading }
        controller.start()

        setContent {
            RizqflowTheme {
                AppRoot(controller = controller, showDebugLogin = BuildConfig.DEBUG)
            }
        }
    }
}
