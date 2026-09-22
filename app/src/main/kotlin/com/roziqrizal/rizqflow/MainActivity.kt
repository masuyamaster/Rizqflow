package com.roziqrizal.rizqflow

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.roziqrizal.rizqflow.auth.AuthController
import com.roziqrizal.rizqflow.auth.AuthUiState
import com.roziqrizal.rizqflow.auth.GoogleAuthProvider
import com.roziqrizal.rizqflow.auth.GoogleClientId
import com.roziqrizal.rizqflow.auth.SharedPrefsAccountStore
import com.roziqrizal.rizqflow.auth.SharedPrefsSecurityStore
import com.roziqrizal.rizqflow.auth.SharedPrefsSessionStore
import com.roziqrizal.rizqflow.domain.auth.AppLockService
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
class MainActivity : FragmentActivity() {
    /** Bertambah setiap ada permintaan Catat kilat (pintasan atau tile); layar membandingkannya dengan yang sudah ditangani. */
    private val quickCatatRequests = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteQuickCatat(intent)
    }

    private fun noteQuickCatat(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_CATAT) quickCatatRequests.value += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Harus dipanggil sebelum super.onCreate: mengganti tema splash ke tema aplikasi.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Data keuangan: nominal tidak boleh terlihat di tampilan aplikasi terakhir atau tangkapan layar (S19).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Hanya saat dibuat baru: setelah layar diputar, intent lama tidak boleh membuka sheet lagi.
        if (savedInstanceState == null) noteQuickCatat(intent)

        val controller = AuthController(
            store = SharedPrefsSessionStore(this),
            provider = GoogleAuthProvider(this, GoogleClientId.clean(getString(R.string.google_web_client_id))),
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

        val appLock = AppLockService(SharedPrefsSecurityStore(this), Pbkdf2PasswordHasher())

        setContent {
            RizqflowTheme {
                AppRoot(controller = controller, appLock = appLock, showDebugLogin = BuildConfig.DEBUG, quickCatatRequest = quickCatatRequests.collectAsState().value)
            }
        }
    }
}
