package com.roziqrizal.rizqflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roziqrizal.rizqflow.ui.theme.rizqflow

/** Ikon ruang menurut kunci yang disimpan di database; kunci tak dikenal memakai ikon Ruang umum. */
internal fun roomIcon(key: String): ImageVector = when (key) {
    "heart" -> RizqflowIcons.Hati
    "sprout" -> RizqflowIcons.Tunas
    "home" -> RizqflowIcons.Rumah
    else -> RizqflowIcons.Ruang
}

/** Petak ikon ruang berwarna identitas ruangnya (slot 1 sampai 3; slot lain netral). */
@Composable
internal fun RoomTile(iconKey: String, colorSlot: Int, size: Int = 36) {
    val color = MaterialTheme.rizqflow.room(colorSlot)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(roomIcon(iconKey), contentDescription = null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}
