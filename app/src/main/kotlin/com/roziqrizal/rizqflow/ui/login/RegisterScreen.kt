package com.roziqrizal.rizqflow.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.auth.AuthBusy
import com.roziqrizal.rizqflow.auth.AuthMessage
import com.roziqrizal.rizqflow.domain.auth.CredentialIssue
import com.roziqrizal.rizqflow.domain.auth.CredentialPolicy
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.spacing

/**
 * S31 Daftar (docs/ui-flow.md). Membuat akun lokal: nama panggilan (opsional), nama pengguna,
 * sandi, dan ulangi sandi. Aturannya dicek di sini sebelum dikirim; kesalahan baru tampil setelah
 * tombol Daftar ditekan pertama kali supaya pengguna tidak dimarahi selagi mengetik.
 */
@Composable
fun RegisterScreen(
    busy: AuthBusy?,
    message: AuthMessage?,
    onRegister: (displayName: String, username: String, password: String, confirmation: String) -> Unit,
    onBackToLogin: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val idle = busy == null
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Sandi sengaja tidak masuk penyimpanan state instance (rememberSaveable), supaya tidak ikut tersimpan.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val issues = CredentialPolicy.validateRegistration(username, password, confirmation)
    // Satu pesan per kolom: yang pertama saja, supaya tidak menumpuk.
    val usernameIssue = issues.firstOrNull { it in USERNAME_ISSUES }.takeIf { attempted }
    val passwordIssue = issues.firstOrNull { it in PASSWORD_ISSUES }.takeIf { attempted }
    val mismatchIssue = issues.firstOrNull { it == CredentialIssue.PASSWORD_MISMATCH }.takeIf { attempted }

    fun submit() {
        attempted = true
        if (CredentialPolicy.validateRegistration(username, password, confirmation).isEmpty()) {
            onRegister(displayName, username, password, confirmation)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5, vertical = spacing.s5),
    ) {
        TextButton(onClick = onBackToLogin, enabled = idle) { Text(stringResource(R.string.register_back)) }
        Text(
            text = stringResource(R.string.register_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = spacing.s2),
        )
        Text(
            text = stringResource(R.string.register_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.s2, bottom = spacing.s4),
        )

        AuthTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = stringResource(R.string.register_name),
            enabled = idle,
            contentType = ContentType.PersonFirstName,
        )
        Spacer(Modifier.height(spacing.s2))
        AuthTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.field_username),
            enabled = idle,
            error = usernameIssue?.let { stringResource(issueText(it)) },
            hint = stringResource(R.string.register_username_hint),
            contentType = ContentType.NewUsername,
        )
        Spacer(Modifier.height(spacing.s2))
        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.field_password),
            enabled = idle,
            isPassword = true,
            error = passwordIssue?.let { stringResource(issueText(it)) },
            hint = stringResource(R.string.register_password_hint),
            contentType = ContentType.NewPassword,
        )
        Spacer(Modifier.height(spacing.s2))
        AuthTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = stringResource(R.string.register_confirm),
            enabled = idle,
            isPassword = true,
            error = mismatchIssue?.let { stringResource(issueText(it)) },
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )

        Row(
            modifier = Modifier
                .padding(top = spacing.s3)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(spacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(RizqflowIcons.Peringatan, contentDescription = null, tint = MaterialTheme.rizqflow.statusWarning, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.register_no_recovery), style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = ::submit,
            enabled = idle,
            modifier = Modifier
                .padding(top = spacing.s4)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy == AuthBusy.PASSWORD) Progress() else Text(stringResource(R.string.register_submit))
        }
        MessageBanner(message)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.s3),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.register_have_account), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onBackToLogin, enabled = idle) { Text(stringResource(R.string.login_submit)) }
        }
    }
}

private val USERNAME_ISSUES = setOf(
    CredentialIssue.USERNAME_TOO_SHORT,
    CredentialIssue.USERNAME_TOO_LONG,
    CredentialIssue.USERNAME_INVALID_CHARACTERS,
)

private val PASSWORD_ISSUES = setOf(
    CredentialIssue.PASSWORD_TOO_SHORT,
    CredentialIssue.PASSWORD_TOO_LONG,
    CredentialIssue.PASSWORD_SAME_AS_USERNAME,
)
