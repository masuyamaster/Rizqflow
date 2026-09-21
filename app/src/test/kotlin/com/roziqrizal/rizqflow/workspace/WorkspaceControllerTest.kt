package com.roziqrizal.rizqflow.workspace

import com.roziqrizal.rizqflow.domain.ledger.OnboardingDraft
import com.roziqrizal.rizqflow.domain.ledger.RoomTemplate
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceRepository
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSnapshot
import com.roziqrizal.rizqflow.domain.model.AccountKind
import com.roziqrizal.rizqflow.domain.money.Money
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceControllerTest {

    private class FakeWorkspace(var snapshot: WorkspaceSnapshot? = null) : WorkspaceRepository {
        var initializeCalls = 0
        var readFails: Throwable? = null
        var writeFails: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun isEmpty(): Boolean {
            readFails?.let { throw it }
            return snapshot == null
        }

        override suspend fun initialize(snapshot: WorkspaceSnapshot) {
            initializeCalls++
            gate?.await()
            writeFails?.let { throw it }
            this.snapshot = snapshot
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var counter = 0
    private val workspace = FakeWorkspace()

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun controller(repo: FakeWorkspace = workspace) =
        WorkspaceController(repo, WorkspaceSetup(repo) { "id-${++counter}" }, scope)

    private val ready = WorkspaceSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun `sebelum dimulai statusnya memuat`() {
        assertEquals(WorkspaceState.Loading, controller().state.value)
    }

    @Test
    fun `akun tanpa ruang kerja melihat onboarding`() {
        val c = controller()
        c.start()
        assertEquals(WorkspaceState.NeedsOnboarding(), c.state.value)
    }

    @Test
    fun `akun yang sudah punya ruang kerja langsung siap`() {
        val c = controller(FakeWorkspace(ready))
        c.start()
        assertEquals(WorkspaceState.Ready, c.state.value)
    }

    @Test
    fun `database yang tidak terbaca tidak ditebak dan diberi kesempatan mengulang`() {
        val repo = FakeWorkspace().apply { readFails = IllegalStateException("rusak") }
        val c = controller(repo)
        c.start()
        assertEquals(WorkspaceState.NeedsOnboarding(failed = true), c.state.value)
    }

    @Test
    fun `menyelesaikan onboarding menyimpan isian lalu siap`() {
        val c = controller()
        c.start()
        val draft = OnboardingDraft().setPercent(0, 20).chooseKind(AccountKind.BANK).typeName("Bank Jago").pressKey("5").pressKey("000")

        c.finish(draft)

        assertEquals(WorkspaceState.Ready, c.state.value)
        val disimpan = assertNotNull(workspace.snapshot)
        assertEquals(listOf("Memberi", "Diri", "Keluarga"), disimpan.rooms.map { it.name })
        assertEquals(listOf(2_000, 3_000, 5_000), disimpan.rules.map { it.share.value })
        assertEquals("Bank Jago", disimpan.accounts.single().name)
        assertEquals(Money.rupiah(5_000), disimpan.accounts.single().openingBalance)
    }

    @Test
    fun `pola Kosong menyimpan akun tanpa ruang`() {
        val c = controller()
        c.start()

        c.finish(OnboardingDraft().chooseTemplate(RoomTemplate.KOSONG))

        assertEquals(WorkspaceState.Ready, c.state.value)
        assertTrue(assertNotNull(workspace.snapshot).rooms.isEmpty())
        assertEquals(1, workspace.snapshot!!.accounts.size)
    }

    @Test
    fun `selama menyimpan status sibuk dan ketukan kedua diabaikan`() {
        val c = controller()
        c.start()
        workspace.gate = CompletableDeferred()

        c.finish(OnboardingDraft())
        assertEquals(WorkspaceState.NeedsOnboarding(busy = true), c.state.value)
        c.finish(OnboardingDraft())

        workspace.gate!!.complete(Unit)
        assertEquals(WorkspaceState.Ready, c.state.value)
        assertEquals(1, workspace.initializeCalls)
    }

    @Test
    fun `gagal menyimpan tidak mengubah data dan bisa dicoba lagi`() {
        val c = controller()
        c.start()
        workspace.writeFails = IllegalStateException("disk penuh")

        c.finish(OnboardingDraft())
        assertEquals(WorkspaceState.NeedsOnboarding(failed = true), c.state.value)
        assertNull(workspace.snapshot)

        workspace.writeFails = null
        c.finish(OnboardingDraft())
        assertEquals(WorkspaceState.Ready, c.state.value)
        assertNotNull(workspace.snapshot)
    }

    @Test
    fun `ruang kerja yang ternyata sudah terisi dianggap siap, bukan galat`() {
        val c = controller()
        c.start()
        // Proses lain sempat mengisi database antara pemeriksaan awal dan tombol ditekan.
        workspace.snapshot = ready

        c.finish(OnboardingDraft())

        assertEquals(WorkspaceState.Ready, c.state.value)
        assertEquals(0, workspace.initializeCalls, "tidak menimpa isian yang sudah ada")
    }

    @Test
    fun `menyelesaikan saat belum waktunya atau nama kosong diabaikan`() {
        val c = controller()
        c.finish(OnboardingDraft()) // masih Loading
        assertEquals(WorkspaceState.Loading, c.state.value)

        c.start()
        c.finish(OnboardingDraft().typeName("  "))
        assertEquals(WorkspaceState.NeedsOnboarding(), c.state.value)
        assertEquals(0, workspace.initializeCalls)
    }

    @Test
    fun `setelah siap panggilan selesai berikutnya diabaikan`() {
        val c = controller(FakeWorkspace(ready))
        c.start()
        c.finish(OnboardingDraft())
        assertEquals(WorkspaceState.Ready, c.state.value)
    }
}
