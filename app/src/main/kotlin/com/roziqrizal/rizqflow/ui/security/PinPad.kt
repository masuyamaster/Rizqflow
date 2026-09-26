package com.roziqrizal.rizqflow.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.ui.theme.spacing

/** Enam titik menunjukkan berapa angka PIN yang sudah diketik. */
@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3), modifier = modifier) {
        repeat(length) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private const val BACK = "back"

/** Papan tombol 0 sampai 9 dan hapus satu angka, dipakai layar kunci dan pengaturan PIN. */
@Composable
fun PinKeypad(enabled: Boolean, onKey: (String) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(null, "0", BACK))
    val deleteLabel = stringResource(R.string.keypad_delete)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s2)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    if (key == null) {
                        Box(modifier = Modifier.weight(1f).height(52.dp))
                    } else {
                        OutlinedButton(
                            onClick = { if (key == BACK) onBackspace() else onKey(key) },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .semantics { if (key == BACK) contentDescription = deleteLabel },
                        ) {
                            Text(if (key == BACK) "⌫" else key, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
