package com.roziqrizal.rizqflow.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.roziqrizal.rizqflow.domain.auth.AuthProviderType
import com.roziqrizal.rizqflow.domain.auth.AuthSession
import com.roziqrizal.rizqflow.domain.auth.GmailConnectResult
import com.roziqrizal.rizqflow.domain.auth.SignInResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Masuk dengan Google lewat Credential Manager, dan izin baca Gmail lewat AuthorizationClient.
 *
 * Butuh **Web client ID** dari proyek Google Cloud pemilik (resource `google_web_client_id`),
 * lihat docs/auth-google.md. Selama kosong, keduanya mengembalikan `NotConfigured`.
 *
 * Aplikasi tidak menyimpan ID token maupun token akses: identitas dipakai untuk mengenali akun,
 * dan tidak ada server Rizqflow yang menerimanya. Harus dibuat di `onCreate` Activity karena
 * mendaftarkan peluncur hasil aktivitas.
 */
class GoogleAuthProvider(
    private val activity: ComponentActivity,
    private val webClientId: String,
) : AuthProvider {

    private val credentialManager = CredentialManager.create(activity)

    /** Izin yang tertunda menunggu layar persetujuan Google selesai. */
    private var pendingConsent: CancellableContinuation<GmailConnectResult>? = null

    private val consentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val continuation = pendingConsent ?: return@registerForActivityResult
        pendingConsent = null
        if (result.resultCode != Activity.RESULT_OK) {
            continuation.resume(GmailConnectResult.Denied)
            return@registerForActivityResult
        }
        val outcome = try {
            val granted = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
            if (granted.grantedScopes.contains(GMAIL_READONLY)) GmailConnectResult.Connected else GmailConnectResult.Denied
        } catch (e: ApiException) {
            GmailConnectResult.Failed(e.message ?: "gagal membaca hasil izin")
        }
        continuation.resume(outcome)
    }

    override suspend fun signInWithGoogle(): SignInResult {
        if (webClientId.isBlank()) return SignInResult.NotConfigured
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(
                    AuthSession(
                        accountId = google.id,
                        email = google.id,
                        displayName = google.displayName,
                        provider = AuthProviderType.GOOGLE,
                    ),
                )
            } else {
                SignInResult.Failed("jenis kredensial tidak dikenal")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.Failed("tidak ada akun Google di perangkat")
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e.message ?: "gagal masuk")
        }
    }

    override suspend fun connectGmail(): GmailConnectResult {
        if (webClientId.isBlank()) return GmailConnectResult.NotConfigured
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY)))
            .build()
        return suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(activity).authorize(request)
                .addOnSuccessListener { result: AuthorizationResult ->
                    val intent = result.pendingIntent
                    if (result.hasResolution() && intent != null) {
                        pendingConsent = continuation
                        consentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    } else {
                        continuation.resume(GmailConnectResult.Connected)
                    }
                }
                .addOnFailureListener { e -> continuation.resume(GmailConnectResult.Failed(e.message ?: "gagal meminta izin")) }
            continuation.invokeOnCancellation { pendingConsent = null }
        }
    }

    private companion object {
        /** Izin baca saja. Ini cakupan terbatas (restricted) di Google: lihat docs/auth-google.md. */
        const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
    }
}
