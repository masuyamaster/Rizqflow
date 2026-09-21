package com.roziqrizal.rizqflow.workspace

import com.roziqrizal.rizqflow.domain.ledger.LedgerError
import com.roziqrizal.rizqflow.domain.ledger.LedgerResult
import com.roziqrizal.rizqflow.domain.ledger.OnboardingDraft
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceRepository
import com.roziqrizal.rizqflow.domain.ledger.WorkspaceSetup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WorkspaceState {
    /** Memeriksa apakah akun ini sudah punya ruang kerja. */
    data object Loading : WorkspaceState

    /** Akun baru yang belum onboarding. [failed]: percobaan menyimpan terakhir gagal (data tidak berubah). */
    data class NeedsOnboarding(val busy: Boolean = false, val failed: Boolean = false) : WorkspaceState

    data object Ready : WorkspaceState
}

/**
 * Alur setelah masuk: akun yang belum punya ruang kerja melewati onboarding (S02 sampai S04),
 * sisanya langsung ke menu utama. Tidak memakai kelas Android sehingga bisa dites di JVM.
 */
class WorkspaceController(
    private val workspace: WorkspaceRepository,
    private val setup: WorkspaceSetup,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<WorkspaceState>(WorkspaceState.Loading)
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            _state.value = try {
                if (workspace.isEmpty()) WorkspaceState.NeedsOnboarding() else WorkspaceState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Database tidak terbaca: jangan menebak; tampilkan onboarding dengan pesan gagal supaya bisa dicoba lagi.
                WorkspaceState.NeedsOnboarding(failed = true)
            }
        }
    }

    /** Menyimpan isian onboarding. Ketukan kedua saat masih berjalan diabaikan. */
    fun finish(draft: OnboardingDraft) {
        val current = _state.value as? WorkspaceState.NeedsOnboarding ?: return
        if (current.busy || !draft.canFinish) return
        _state.value = WorkspaceState.NeedsOnboarding(busy = true)
        scope.launch {
            _state.value = try {
                when (val result = setup.setUp(draft.template, draft.firstAccount(), draft.shares())) {
                    is LedgerResult.Success -> WorkspaceState.Ready
                    // Sudah terisi (misalnya proses sebelumnya selesai di belakang layar): lanjut saja.
                    is LedgerResult.Failure ->
                        if (result.error == LedgerError.WORKSPACE_NOT_EMPTY) WorkspaceState.Ready else WorkspaceState.NeedsOnboarding(failed = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                WorkspaceState.NeedsOnboarding(failed = true)
            }
        }
    }
}
