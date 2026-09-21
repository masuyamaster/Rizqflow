package com.roziqrizal.rizqflow.auth

import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import com.roziqrizal.rizqflow.domain.auth.GmailConnectResult
import com.roziqrizal.rizqflow.domain.auth.SessionStore
import com.roziqrizal.rizqflow.domain.auth.SignInResult
import com.roziqrizal.rizqflow.domain.auth.StartDestination
import com.roziqrizal.rizqflow.domain.auth.StartRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sumber izin Google. Implementasi nyata ada di [GoogleAuthProvider]. */
interface AuthProvider {
    suspend fun signInWithGoogle(): SignInResult

    suspend fun connectGmail(): GmailConnectResult
}

/** Pekerjaan yang sedang berjalan; tombol dinonaktifkan selama itu. */
enum class AuthBusy { GOOGLE, GMAIL }

/** Pesan lembut untuk pengguna; tidak pernah menghalangi memakai aplikasi. */
enum class AuthMessage { NOT_CONFIGURED, FAILED, GMAIL_DENIED, GMAIL_UNAVAILABLE }

sealed interface AuthUiState {
    /** Membaca riwayat masuk; splash masih tampil. */
    data object Loading : AuthUiState

    data class SignedOut(val busy: AuthBusy? = null, val message: AuthMessage? = null) : AuthUiState

    /** [notice] ditampilkan sekali di menu utama, misalnya izin Gmail ditolak saat masuk. */
    data class SignedIn(val session: AuthSession, val notice: AuthMessage? = null, val busy: AuthBusy? = null) : AuthUiState
}

/**
 * Alur splash, masuk, dan menu utama. Tidak memakai kelas Android sehingga bisa dites di JVM.
 * Sesi yang tersimpan berarti langsung ke menu utama ([StartRouter]).
 */
class AuthController(
    private val store: SessionStore,
    private val provider: AuthProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Membaca riwayat masuk. Dipanggil sekali saat aplikasi dibuka. */
    fun start() {
        scope.launch {
            val session = store.current()
            _state.value = when (StartRouter.decide(session)) {
                StartDestination.MAIN -> AuthUiState.SignedIn(requireNotNull(session))
                StartDestination.LOGIN -> AuthUiState.SignedOut()
            }
        }
    }

    /** Masuk dengan Google; bila [alsoConnectGmail], sekalian meminta izin baca Gmail. */
    fun signInWithGoogle(alsoConnectGmail: Boolean = false) {
        if (_state.value !is AuthUiState.SignedOut || (_state.value as AuthUiState.SignedOut).busy != null) return
        _state.value = AuthUiState.SignedOut(busy = if (alsoConnectGmail) AuthBusy.GMAIL else AuthBusy.GOOGLE)
        scope.launch {
            when (val result = provider.signInWithGoogle()) {
                is SignInResult.Success -> {
                    var session = result.session
                    var notice: AuthMessage? = null
                    if (alsoConnectGmail) {
                        when (provider.connectGmail()) {
                            GmailConnectResult.Connected -> session = session.copy(gmailConnected = true)
                            GmailConnectResult.Denied -> notice = AuthMessage.GMAIL_DENIED
                            else -> notice = AuthMessage.GMAIL_UNAVAILABLE
                        }
                    }
                    store.save(session)
                    _state.value = AuthUiState.SignedIn(session, notice)
                }

                SignInResult.Cancelled -> _state.value = AuthUiState.SignedOut()
                SignInResult.NotConfigured -> _state.value = AuthUiState.SignedOut(message = AuthMessage.NOT_CONFIGURED)
                is SignInResult.Failed -> _state.value = AuthUiState.SignedOut(message = AuthMessage.FAILED)
            }
        }
    }

    /** Menghubungkan Gmail belakangan, dari menu utama. */
    fun connectGmail() {
        val current = _state.value as? AuthUiState.SignedIn ?: return
        if (current.busy != null || current.session.gmailConnected) return
        _state.value = current.copy(busy = AuthBusy.GMAIL, notice = null)
        scope.launch {
            _state.value = when (provider.connectGmail()) {
                GmailConnectResult.Connected -> {
                    val updated = current.session.copy(gmailConnected = true)
                    store.save(updated)
                    AuthUiState.SignedIn(updated)
                }

                GmailConnectResult.Denied -> AuthUiState.SignedIn(current.session, AuthMessage.GMAIL_DENIED)
                else -> AuthUiState.SignedIn(current.session, AuthMessage.GMAIL_UNAVAILABLE)
            }
        }
    }

    /** Masuk uji tanpa akun Google. Hanya dipanggil dari tombol yang muncul di build debug. */
    fun signInDebug() {
        if (_state.value !is AuthUiState.SignedOut) return
        scope.launch {
            val session = AuthSession("debug", "uji@rizqflow.local", "Pengguna uji", AuthProviderType.DEBUG)
            store.save(session)
            _state.value = AuthUiState.SignedIn(session)
        }
    }

    fun dismissNotice() {
        val current = _state.value as? AuthUiState.SignedIn ?: return
        _state.value = current.copy(notice = null)
    }

    fun signOut() {
        scope.launch {
            store.clear()
            _state.value = AuthUiState.SignedOut()
        }
    }
}
