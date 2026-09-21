package com.roziqrizal.rizqflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ikon garis 24 dp dengan tebal 1,75, persis sama dengan prototipe HTML (docs/design/prototype.js),
 * dibuat dari data path SVG yang sama supaya tidak menambah pustaka ikon. Warna diambil dari
 * `LocalContentColor` lewat `Icon`, jadi warna di sini hanya penanda.
 */
private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object RizqflowIcons {
    val Denah: ImageVector = strokeIcon(
        "Denah",
        "M5.5 4h4A1.5 1.5 0 0 1 11 5.5v4A1.5 1.5 0 0 1 9.5 11h-4A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4z",
        "M14.5 4h4A1.5 1.5 0 0 1 20 5.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 13 9.5v-4A1.5 1.5 0 0 1 14.5 4z",
        "M5.5 13h4A1.5 1.5 0 0 1 11 14.5v4A1.5 1.5 0 0 1 9.5 20h-4A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13z",
        "M14.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5z",
    )

    val Transaksi: ImageVector = strokeIcon(
        "Transaksi",
        "M8 6.5h12M8 12h12M8 17.5h12",
        "M4 6.5h.01M4 12h.01M4 17.5h.01",
    )

    val Ruang: ImageVector = strokeIcon(
        "Ruang",
        "M4 20V5.5A1.5 1.5 0 0 1 5.5 4h9A1.5 1.5 0 0 1 16 5.5V20",
        "M2.5 20h19",
        "M13 12h.01",
        "M16 9h3.5A1.5 1.5 0 0 1 21 10.5V20",
    )

    val Lainnya: ImageVector = strokeIcon("Lainnya", "M5 12h.01M12 12h.01M19 12h.01")

    val Tambah: ImageVector = strokeIcon("Tambah", "M12 5v14M5 12h14")
}
