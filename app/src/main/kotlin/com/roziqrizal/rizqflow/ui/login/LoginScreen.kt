package com.roziqrizal.rizqflow.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.auth.AuthBusy
import com.roziqrizal.rizqflow.auth.AuthMessage
import com.roziqrizal.rizqflow.ui.RizqflowIcons
import com.roziqrizal.rizqflow.ui.theme.CappedFontScale
import com.roziqrizal.rizqflow.ui.theme.CaslonFamily
import com.roziqrizal.rizqflow.ui.theme.HERO_MAX_FONT_SCALE
import com.roziqrizal.rizqflow.ui.theme.rizqflow
import com.roziqrizal.rizqflow.ui.theme.rizqflowTonalButtonColors
import com.roziqrizal.rizqflow.ui.theme.spacing

/**
 * S30 Masuk (docs/ui-flow.md). Tampil setelah splash bila belum ada riwayat masuk. Satu tindakan
 * utama (Google); Gmail adalah izin tambahan yang sengaja terpisah dan tidak wajib.
 */
@Composable
fun LoginScreen(
    busy: AuthBusy?,
    message: AuthMessage?,
    showDebugLogin: Boolean,
    onGoogle: () -> Unit,
    onGoogleWithGmail: () -> Unit,
    onDebug: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val idle = busy == null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.s5, vertical = spacing.s6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s3)) {
            RoomTile(RizqflowIcons.Hati, MaterialTheme.rizqflow.room(1))
            RoomTile(RizqflowIcons.Tunas, MaterialTheme.rizqflow.room(2))
            RoomTile(RizqflowIcons.Rumah, MaterialTheme.rizqflow.room(3))
        }
        Spacer(Modifier.height(spacing.s4))
        CappedFontScale(HERO_MAX_FONT_SCALE) {
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(fontFamily = CaslonFamily, fontSize = 40.sp, lineHeight = 44.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.s2),
        )
        Spacer(Modifier.height(spacing.s4))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s2)) {
            listOf(R.string.login_point_local, R.string.login_point_offline, R.string.login_point_no_ads).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
                    Icon(RizqflowIcons.Centang, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(spacing.s5))

        // Cukup simbol G (varian ikon resmi Google); namanya dibacakan pembaca layar.
        GoogleIconButton(onClick = onGoogle, enabled = idle, busy = busy == AuthBusy.GOOGLE)
        Spacer(Modifier.height(spacing.s4))
        FilledTonalButton(
            onClick = onGoogleWithGmail,
            enabled = idle,
            colors = rizqflowTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy == AuthBusy.GMAIL) Progress() else Text(stringResource(R.string.login_gmail))
        }
        Text(
            text = stringResource(R.string.login_gmail_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.s2),
        )

        // Pesan lembut: tidak merah, tidak memblokir. Dibacakan pembaca layar saat muncul.
        Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (message != null) {
                Row(
                    modifier = Modifier
                        .padding(top = spacing.s4)
                        .fillMaxWidth()
                        .background(MaterialTheme.rizqflow.statusWarning.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(spacing.s3),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s3),
                ) {
                    Icon(RizqflowIcons.Peringatan, contentDescription = null, tint = MaterialTheme.rizqflow.statusWarning)
                    Text(stringResource(messageText(message)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (showDebugLogin) {
            TextButton(onClick = onDebug, enabled = idle, modifier = Modifier.padding(top = spacing.s3)) {
                Text(stringResource(R.string.login_debug))
            }
        }
    }
}

internal fun messageText(message: AuthMessage): Int = when (message) {
    AuthMessage.NOT_CONFIGURED -> R.string.msg_not_configured
    AuthMessage.FAILED -> R.string.msg_sign_in_failed
    AuthMessage.GMAIL_DENIED -> R.string.msg_gmail_denied
    AuthMessage.GMAIL_UNAVAILABLE -> R.string.msg_gmail_unavailable
}

@Composable
private fun RoomTile(icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GoogleIconButton(onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    // Pedoman Google: latar putih (gelap #131314 di mode gelap) dengan garis tepi tipis.
    val dark = isSystemInDarkTheme()
    val name = stringResource(R.string.login_google)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (dark) Color(0xFF131314) else Color.White,
        border = BorderStroke(1.dp, if (dark) Color(0xFF8E918F) else Color(0xFF747775)),
        modifier = Modifier
            .size(64.dp)
            .semantics {
                contentDescription = name
                role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (busy) Progress() else Image(GoogleGLogo, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Progress() {
    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
