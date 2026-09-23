package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.domain.auth.AccountStorage
import com.roziqrizal.rizqflow.domain.auth.AppLockService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.roziqrizal.rizqflow.auth.AuthController
import com.roziqrizal.rizqflow.auth.AuthUiState
import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.ui.login.LoginScreen
import com.roziqrizal.rizqflow.ui.login.RegisterScreen
import com.roziqrizal.rizqflow.ui.security.AppLockScreen
import com.roziqrizal.rizqflow.ui.theme.ThemePreference
import kotlinx.coroutines.launch

/**
 * Akar tampilan: splash (sistem) lalu halaman masuk, atau langsung menu utama bila sudah punya
 * riwayat masuk. Selama [AuthUiState.Loading] tampil wordmark penuh (splash sistem hanya latar).
 */
@Composable
fun AppRoot(
    controller: AuthController,
    appLock: AppLockService,
    themePreference: ThemePreference,
    showDebugLogin: Boolean,
    quickCatatRequest: Int = 0,
) {
    val state by controller.state.collectAsState()
    var registering by rememberSaveable { mutableStateOf(false) }
    var demo by rememberSaveable { mutableStateOf(false) }
    fun openRegister(open: Boolean) {
        controller.clearMessage()
        registering = open
    }
    // Setelah masuk, halaman daftar ditutup; kalau tidak, Keluar akan kembali ke halaman daftar.
    LaunchedEffect(state is AuthUiState.SignedIn) {
        if (state is AuthUiState.SignedIn) registering = false else demo = false
    }
    // Surface menentukan warna teks bawaan (onBackground). Tanpanya teks tanpa warna eksplisit
    // memakai hitam dan tak terbaca di mode gelap.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when (val s = state) {
            // Warna latar disamakan dengan ic_launcher_background (bukan token tema) supaya
            // menyatu tanpa kedipan dengan splash sistem (latar kosong), yang memakai warna itu juga.
            AuthUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_rizqflow_wordmark),
                    contentDescription = null,
                    // Rasio dikunci (viewport wordmark 5440x1110) supaya gambar mengisi lebar penuh, bukan ukuran bawaan drawable.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp).aspectRatio(5440f / 1110f),
                    contentScale = ContentScale.FillWidth,
                )
            }

            is AuthUiState.SignedOut -> if (registering) {
                BackHandler(enabled = s.busy == null) { openRegister(false) }
                RegisterScreen(
                    busy = s.busy,
                    message = s.message,
                    onRegister = controller::register,
                    onBackToLogin = { openRegister(false) },
                )
            } else {
                LoginScreen(
                    busy = s.busy,
                    message = s.message,
                    showDebugLogin = showDebugLogin,
                    onGoogle = { controller.signInWithGoogle(alsoConnectGmail = false) },
                    onGoogleWithGmail = { controller.signInWithGoogle(alsoConnectGmail = true) },
                    onPasswordSignIn = controller::signInWithPassword,
                    onOpenRegister = { openRegister(true) },
                    onDebug = controller::signInDebug,
                )
            }

            // Setiap akun membuka database sendiri; akun baru melewati onboarding dulu.
            is AuthUiState.SignedIn -> {
                // Kunci aplikasi (S19): berlaku untuk aplikasi, bukan akun tertentu. null = belum
                // diperiksa (tanpa PIN tidak pernah null lama, disamakan dengan terkunci supaya
                // konten tidak sempat berkedip sebelum pemeriksaan selesai).
                var locked by rememberSaveable { mutableStateOf<Boolean?>(null) }
                var backgroundedAt by rememberSaveable { mutableStateOf<Long?>(null) }
                val lockScope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(appLock) { locked = appLock.shouldLock(null) }

                DisposableEffect(lifecycleOwner, appLock) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                            Lifecycle.Event.ON_START -> backgroundedAt?.let { at ->
                                lockScope.launch { if (appLock.shouldLock(at)) locked = true }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Box(Modifier.fillMaxSize()) {
                    WorkspaceHost(
                        accountId = if (demo) AccountStorage.DEMO_ACCOUNT_ID else s.session.accountId,
                        onDemoFailed = { demo = false },
                    ) { workspace, restoreFromBackup ->
                        // Berganti antara data asli dan demo (atau setelah pulihkan cadangan) memulai
                        // menu utama dari awal (tab, layar terbuka): ruang kerjanya objek yang berbeda.
                        key(workspace) {
                            MainHost(
                                workspace = workspace,
                                onRestoreBackup = restoreFromBackup,
                                appLock = appLock,
                                themePreference = themePreference,
                                account = AccountUi(
                                    identifier = s.session.identifier,
                                    local = s.session.provider == AuthProviderType.PASSWORD,
                                    displayName = s.session.displayName,
                                    gmailConnected = s.session.gmailConnected,
                                    busy = s.busy,
                                    notice = s.notice,
                                ),
                                onConnectGmail = controller::connectGmail,
                                onSignOut = {
                                    demo = false
                                    controller.signOut()
                                },
                                onDismissNotice = controller::dismissNotice,
                                quickCatatRequest = quickCatatRequest,
                                demo = demo,
                                onEnterDemo = { demo = true },
                                onExitDemo = { demo = false },
                            )
                        }
                    }
                    if (locked != false) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            AppLockScreen(service = appLock, onUnlocked = { locked = false })
                        }
                    }
                }
            }
        }
    }
}
