package com.roziqrizal.rizqflow.auth

import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import com.roziqrizal.rizqflow.domain.auth.GmailConnectResult
import com.roziqrizal.rizqflow.domain.auth.SessionStore
import com.roziqrizal.rizqflow.domain.auth.SignInResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthControllerTest {

    private class FakeStore(var stored: AuthSession? = null) : SessionStore {
        override suspend fun current() = stored

        override suspend fun save(session: AuthSession) {
            stored = session
        }

        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeProvider(
        var signIn: suspend () -> SignInResult = { SignInResult.Cancelled },
        var gmail: suspend () -> GmailConnectResult = { GmailConnectResult.Connected },
    ) : AuthProvider {
        override suspend fun signInWithGoogle() = signIn()

        override suspend fun connectGmail() = gmail()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val sesi = AuthSession("g-1", "roziq@example.com", "Roziq", AuthProviderType.GOOGLE)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun controller(store: FakeStore = FakeStore(), provider: FakeProvider = FakeProvider()) =
        AuthController(store, provider, scope)

    @Test
    fun `sebelum dimulai status masih memuat sehingga splash tetap tampil`() {
        assertEquals(AuthUiState.Loading, controller().state.value)
    }

    @Test
    fun `tanpa riwayat masuk pengguna melihat halaman masuk`() {
        val c = controller()
        c.start()

        assertEquals(AuthUiState.SignedOut(), c.state.value)
    }

    @Test
    fun `dengan riwayat masuk pengguna langsung ke menu utama`() {
        val c = controller(FakeStore(sesi))
        c.start()

        assertEquals(AuthUiState.SignedIn(sesi), c.state.value)
    }

    @Test
    fun `masuk dengan Google menyimpan riwayat lalu ke menu utama`() {
        val store = FakeStore()
        val c = controller(store, FakeProvider(signIn = { SignInResult.Success(sesi) }))
        c.start()

        c.signInWithGoogle()

        assertEquals(AuthUiState.SignedIn(sesi), c.state.value)
        assertEquals(sesi, store.stored)
    }

    @Test
    fun `selama masuk berjalan tombol dinonaktifkan lewat status sibuk`() {
        val menunggu = CompletableDeferred<SignInResult>()
        val c = controller(provider = FakeProvider(signIn = { menunggu.await() }))
        c.start()

        c.signInWithGoogle()
        assertEquals(AuthUiState.SignedOut(busy = AuthBusy.GOOGLE), c.state.value)

        menunggu.complete(SignInResult.Success(sesi))
        assertIs<AuthUiState.SignedIn>(c.state.value)
    }

    @Test
    fun `ketukan kedua saat masih sibuk diabaikan`() {
        var panggilan = 0
        val menunggu = CompletableDeferred<SignInResult>()
        val c = controller(provider = FakeProvider(signIn = { panggilan++; menunggu.await() }))
        c.start()

        c.signInWithGoogle()
        c.signInWithGoogle()

        assertEquals(1, panggilan)
    }

    @Test
    fun `menutup dialog akun tidak menampilkan pesan`() {
        val c = controller(provider = FakeProvider(signIn = { SignInResult.Cancelled }))
        c.start()

        c.signInWithGoogle()

        assertEquals(AuthUiState.SignedOut(), c.state.value)
    }

    @Test
    fun `build tanpa client ID menampilkan pesan belum disiapkan`() {
        val c = controller(provider = FakeProvider(signIn = { SignInResult.NotConfigured }))
        c.start()

        c.signInWithGoogle()

        assertEquals(AuthUiState.SignedOut(message = AuthMessage.NOT_CONFIGURED), c.state.value)
    }

    @Test
    fun `gagal masuk menampilkan pesan lembut dan tidak menyimpan apa pun`() {
        val store = FakeStore()
        val c = controller(store, FakeProvider(signIn = { SignInResult.Failed("boom") }))
        c.start()

        c.signInWithGoogle()

        assertEquals(AuthUiState.SignedOut(message = AuthMessage.FAILED), c.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `masuk sekaligus Gmail menandai Gmail terhubung`() {
        val store = FakeStore()
        val c = controller(store, FakeProvider(signIn = { SignInResult.Success(sesi) }))
        c.start()

        c.signInWithGoogle(alsoConnectGmail = true)

        val masuk = assertIs<AuthUiState.SignedIn>(c.state.value)
        assertTrue(masuk.session.gmailConnected)
        assertTrue(store.stored!!.gmailConnected)
        assertNull(masuk.notice)
    }

    @Test
    fun `izin Gmail ditolak tetap masuk tanpa Gmail dan ada catatan`() {
        val c = controller(provider = FakeProvider(signIn = { SignInResult.Success(sesi) }, gmail = { GmailConnectResult.Denied }))
        c.start()

        c.signInWithGoogle(alsoConnectGmail = true)

        val masuk = assertIs<AuthUiState.SignedIn>(c.state.value)
        assertFalse(masuk.session.gmailConnected)
        assertEquals(AuthMessage.GMAIL_DENIED, masuk.notice)
    }

    @Test
    fun `menghubungkan Gmail belakangan dari menu utama`() {
        val store = FakeStore(sesi)
        val c = controller(store, FakeProvider(gmail = { GmailConnectResult.Connected }))
        c.start()

        c.connectGmail()

        assertTrue((c.state.value as AuthUiState.SignedIn).session.gmailConnected)
        assertTrue(store.stored!!.gmailConnected)
    }

    @Test
    fun `Gmail yang sudah terhubung tidak diminta lagi`() {
        var panggilan = 0
        val c = controller(FakeStore(sesi.copy(gmailConnected = true)), FakeProvider(gmail = { panggilan++; GmailConnectResult.Connected }))
        c.start()

        c.connectGmail()

        assertEquals(0, panggilan)
    }

    @Test
    fun `catatan dihapus setelah dibaca`() {
        val c = controller(provider = FakeProvider(signIn = { SignInResult.Success(sesi) }, gmail = { GmailConnectResult.Denied }))
        c.start()
        c.signInWithGoogle(alsoConnectGmail = true)

        c.dismissNotice()

        assertNull((c.state.value as AuthUiState.SignedIn).notice)
    }

    @Test
    fun `keluar menghapus riwayat sehingga berikutnya kembali ke halaman masuk`() {
        val store = FakeStore(sesi)
        val c = controller(store)
        c.start()

        c.signOut()

        assertEquals(AuthUiState.SignedOut(), c.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `masuk uji menyimpan sesi debug`() {
        val store = FakeStore()
        val c = controller(store)
        c.start()

        c.signInDebug()

        assertEquals(AuthProviderType.DEBUG, store.stored!!.provider)
        assertIs<AuthUiState.SignedIn>(c.state.value)
    }
}
