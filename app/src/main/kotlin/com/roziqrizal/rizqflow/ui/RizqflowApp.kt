package com.roziqrizal.rizqflow.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.roziqrizal.rizqflow.R
import com.roziqrizal.rizqflow.ui.theme.CappedFontScale
import com.roziqrizal.rizqflow.ui.theme.NAV_LABEL_MAX_FONT_SCALE
import com.roziqrizal.rizqflow.ui.theme.spacing

/**
 * Empat tab utama (docs/ui-flow.md, "Kerangka navigasi"). Tombol Catat hanya muncul di Denah
 * dan Transaksi.
 */
enum class TopTab(
    val route: String,
    @StringRes val label: Int,
    @StringRes val placeholder: Int,
    val icon: ImageVector,
    val showsFab: Boolean,
) {
    Denah("denah", R.string.tab_denah, R.string.placeholder_denah, RizqflowIcons.Denah, showsFab = true),
    Transaksi("transaksi", R.string.tab_transaksi, R.string.placeholder_transaksi, RizqflowIcons.Transaksi, showsFab = true),
    Ruang("ruang", R.string.tab_ruang, R.string.placeholder_ruang, RizqflowIcons.Ruang, showsFab = false),
    Lainnya("lainnya", R.string.tab_lainnya, R.string.placeholder_lainnya, RizqflowIcons.Lainnya, showsFab = false),
    ;

    companion object {
        fun fromRoute(route: String?): TopTab = entries.firstOrNull { it.route == route } ?: Denah
    }
}

/** Kerangka aplikasi: bottom navigation, tombol Catat, dan isi tiap tab (sementara masih kerangka). */
@Composable
fun RizqflowApp(onCatat: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = TopTab.fromRoute(backStack?.destination?.route)

    Scaffold(
        bottomBar = {
            CappedFontScale(NAV_LABEL_MAX_FONT_SCALE) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            // Label sudah menjelaskan ikon, jadi ikon tidak perlu deskripsi sendiri.
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            // Penanda tab aktif hijau seperti prototipe, bukan biru bawaan secondaryContainer.
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryFixed,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (current.showsFab) {
                ExtendedFloatingActionButton(
                    onClick = onCatat,
                    icon = { Icon(RizqflowIcons.Tambah, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_catat)) },
                    // Hijau tua dengan teks putih seperti prototipe (bawaan Material memakai primaryContainer).
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TopTab.Denah.route,
            modifier = Modifier.padding(padding),
        ) {
            TopTab.entries.forEach { tab ->
                composable(tab.route) {
                    PlaceholderScreen(title = stringResource(tab.label), note = stringResource(tab.placeholder))
                }
            }
        }
    }
}

/** Isi sementara tiap tab sampai layarnya dikerjakan (Tahap 3 dan 4). */
@Composable
private fun PlaceholderScreen(title: String, note: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.s4, vertical = MaterialTheme.spacing.s5),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.s2),
        )
    }
}
