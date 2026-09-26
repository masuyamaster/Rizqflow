package com.roziqrizal.rizqflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.roziqrizal.rizqflow.domain.auth.AccountStorage
import com.roziqrizal.rizqflow.domain.ledger.FirstAccount
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Keputusan 2026-09-21: satu database per akun. Yang diuji di sini adalah janjinya: berganti
 * akun tidak mencampur data, akun yang sama kembali ke data yang sama, dan email tidak menjadi
 * nama berkas.
 *
 * Nama fungsi tes sengaja pendek: Robolectric memasukkannya ke jalur berkas database, dan di
 * Windows jalur di atas 260 karakter membuat SQLite gagal membuka berkas (SQLITE_CANTOPEN).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountDatabasesTest {

    private lateinit var context: Context
    private val opened = mutableListOf<LocalLedger>()
    private var counter = 0

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        // Di Android sungguhan folder databases dibuat otomatis; Robolectric kadang belum membuatnya.
        context.getDatabasePath("probe").parentFile!!.mkdirs()
    }

    @After
    fun closeAll() {
        opened.forEach { runCatching { it.close() } }
    }

    private fun open(accountId: String): LocalLedger = LocalLedger.open(context, accountId).also(opened::add)

    private fun onboard(ledger: LocalLedger, accountName: String) = runBlocking {
        val setup = WorkspaceSetup(ledger.workspace) { "id-${++counter}" }
        val result = setup.setUp(RoomTemplate.TIGA_HAK, FirstAccount(accountName, AccountKind.CASH, Money.rupiah(100_000)))
        assertIs<LedgerResult.Success<Unit>>(result)
    }

    @Test
    fun `dua akun tidak berbagi data`() = runBlocking {
        val a = open("roziq@example.com")
        val b = open("lain@example.com")

        onboard(a, "Dompet A")

        assertFalse(a.workspace.isEmpty())
        assertTrue(b.workspace.isEmpty(), "akun lain masih kosong")
        assertEquals(listOf("Dompet A"), a.accounts.activeAccounts().map { it.name })
        assertTrue(b.accounts.activeAccounts().isEmpty())
    }

    @Test
    fun `akun sama kembali ke data sama`() {
        val pertama = open("roziq@example.com")
        onboard(pertama, "Dompet A")
        pertama.close()

        val lagi = open("roziq@example.com")

        assertEquals(listOf("Dompet A"), runBlocking { lagi.accounts.activeAccounts() }.map { it.name })
        assertEquals(3, runBlocking { lagi.rooms.activeRooms() }.size)
    }

    @Test
    fun `berganti akun tidak menghapus data`() {
        val a = open("a@example.com")
        onboard(a, "Dompet A")
        a.close()

        val b = open("b@example.com")
        onboard(b, "Dompet B")
        b.close()

        assertEquals(listOf("Dompet A"), runBlocking { open("a@example.com").accounts.activeAccounts() }.map { it.name })
        assertEquals(listOf("Dompet B"), runBlocking { open("b@example.com").accounts.activeAccounts() }.map { it.name })
    }

    @Test
    fun `nama berkas dari hash tanpa email`() {
        val email = "roziqrizal881992@gmail.com"
        val ledger = open(email)
        onboard(ledger, "Dompet")

        val nama = AccountStorage.databaseName(email)
        assertTrue(context.getDatabasePath(nama).exists(), "berkas $nama harus ada")
        assertTrue(context.databaseList().none { it.contains("roziq") || it.contains("gmail") }, context.databaseList().joinToString())
    }

    @Test
    fun `pengenal berbeda berkas berbeda`() {
        val google = open("roziq@example.com")
        val lokal = open("b4a00324-cf1f-49a4-b01a-11ab4ec8e88e")
        val uji = open("debug")
        onboard(google, "G")
        onboard(lokal, "L")
        onboard(uji, "U")

        assertEquals(setOf("G"), runBlocking { google.accounts.activeAccounts() }.map { it.name }.toSet())
        assertEquals(setOf("L"), runBlocking { lokal.accounts.activeAccounts() }.map { it.name }.toSet())
        assertEquals(setOf("U"), runBlocking { uji.accounts.activeAccounts() }.map { it.name }.toSet())
    }
}
