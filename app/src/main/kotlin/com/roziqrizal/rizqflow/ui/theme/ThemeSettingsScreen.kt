package com.roziqrizal.rizqflow.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.roziqrizal.rizqflow.R

/** Layar Tampilan (Tahap 6): tema Otomatis (ikuti sistem), Terang, atau Gelap. */
@Composable
fun ThemeSettingsScreen(preference: ThemePreference, onClose: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val mode by preference.mode.collectAsState()
    BackHandler(onBack = onClose)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TextButton(onClick = onClose, modifier = Modifier.padding(start = spacing.s3, top = spacing.s2)) { Text(stringResource(R.string.onb_back)) }
        Column(modifier = Modifier.padding(horizontal = spacing.s5).padding(bottom = spacing.s5)) {
            Text(stringResource(R.string.tampilan_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.tampilan_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.s2),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s2), modifier = Modifier.padding(top = spacing.s4)) {
                FilterChip(
                    selected = mode == ThemeMode.SYSTEM,
                    onClick = { preference.setMode(ThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.tampilan_system)) },
                    colors = rizqflowFilterChipColors(),
                )
                FilterChip(
                    selected = mode == ThemeMode.LIGHT,
                    onClick = { preference.setMode(ThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.tampilan_light)) },
                    colors = rizqflowFilterChipColors(),
                )
                FilterChip(
                    selected = mode == ThemeMode.DARK,
                    onClick = { preference.setMode(ThemeMode.DARK) },
                    label = { Text(stringResource(R.string.tampilan_dark)) },
                    colors = rizqflowFilterChipColors(),
                )
            }
        }
    }
}
