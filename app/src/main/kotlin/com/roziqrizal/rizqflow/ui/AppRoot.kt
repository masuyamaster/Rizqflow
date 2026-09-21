package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.roziqrizal.rizqflow.auth.AuthController
import com.roziqrizal.rizqflow.auth.AuthUiState
import com.roziqrizal.rizqflow.ui.login.LoginScreen

/**
 * Akar tampilan: splash (sistem) lalu halaman masuk, atau langsung menu utama bila sudah punya
 * riwayat masuk. Selama [AuthUiState.Loading] splash sistem masih menutupi layar.
 */
@Composable
fun AppRoot(controller: AuthController, showDebugLogin: Boolean) {
    val state by controller.state.collectAsState()
    when (val s = state) {
        AuthUiState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        is AuthUiState.SignedOut -> LoginScreen(
            busy = s.busy,
            message = s.message,
            showDebugLogin = showDebugLogin,
            onGoogle = { controller.signInWithGoogle(alsoConnectGmail = false) },
            onGoogleWithGmail = { controller.signInWithGoogle(alsoConnectGmail = true) },
            onDebug = controller::signInDebug,
        )

        is AuthUiState.SignedIn -> RizqflowApp(
            account = AccountUi(
                email = s.session.email,
                displayName = s.session.displayName,
                gmailConnected = s.session.gmailConnected,
                busy = s.busy,
                notice = s.notice,
            ),
            onConnectGmail = controller::connectGmail,
            onSignOut = controller::signOut,
            onDismissNotice = controller::dismissNotice,
        )
    }
}
