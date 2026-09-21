package com.roziqrizal.rizqflow.ui.theme

import androidx.compose.ui.graphics.Color
import com.roziqrizal.rizqflow.ui.TopTab
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Menjaga hasil validasi warna di docs/design/README.md agar tidak rusak diam-diam bila token
 * diubah: kontras teks WCAG 4,5:1 dan kontras warna identitas ruang 3:1 terhadap permukaan.
 */
class RizqflowTokensTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val light = RizqflowLightScheme
    private val dark = RizqflowDarkScheme

    @Test
    fun `teks utama dan sekunder terbaca di kartu dan latar mode terang`() {
        listOf(light.surfaceContainerLowest, light.background).forEach { surface ->
            assertTrue(contrast(light.onSurface, surface) >= 4.5, "teks utama")
            assertTrue(contrast(light.onSurfaceVariant, surface) >= 4.5, "teks sekunder")
            assertTrue(contrast(light.primary, surface) >= 4.5, "warna utama")
        }
    }

    @Test
    fun `teks utama dan sekunder terbaca di kartu dan latar mode gelap`() {
        listOf(dark.surfaceContainerLowest, dark.surfaceContainer, dark.background).forEach { surface ->
            assertTrue(contrast(dark.onSurface, surface) >= 4.5, "teks utama")
            assertTrue(contrast(dark.onSurfaceVariant, surface) >= 4.5, "teks sekunder")
            assertTrue(contrast(dark.primary, surface) >= 4.5, "warna utama")
        }
    }

    @Test
    fun `teks di atas tombol utama terbaca di kedua mode`() {
        assertTrue(contrast(light.onPrimary, light.primary) >= 4.5)
        assertTrue(contrast(dark.onPrimary, dark.primary) >= 4.5)
        assertTrue(contrast(light.onPrimaryContainer, light.primaryContainer) >= 4.5)
        assertTrue(contrast(dark.onPrimaryContainer, dark.primaryContainer) >= 4.5)
    }

    @Test
    fun `warna identitas ruang punya kontras 3 banding 1 terhadap permukaan`() {
        listOf(RizqflowLightExtra.room1, RizqflowLightExtra.room2, RizqflowLightExtra.room3).forEach {
            assertTrue(contrast(it, light.surfaceContainerLowest) >= 3.0)
        }
        listOf(RizqflowDarkExtra.room1, RizqflowDarkExtra.room2, RizqflowDarkExtra.room3).forEach {
            assertTrue(contrast(it, dark.surfaceContainer) >= 3.0)
        }
    }

    @Test
    fun `tiga ruang pertama punya warna berbeda dan ruang lain netral`() {
        val extra = RizqflowLightExtra

        assertEquals(3, setOf(extra.room(1), extra.room(2), extra.room(3)).size)
        assertEquals(extra.roomNeutral, extra.room(4))
        assertEquals(extra.roomNeutral, extra.room(9))
        assertNotEquals(extra.room(3), extra.room(4))
    }

    @Test
    fun `warna status sama di kedua mode`() {
        assertEquals(RizqflowLightExtra.statusGood, RizqflowDarkExtra.statusGood)
        assertEquals(RizqflowLightExtra.statusWarning, RizqflowDarkExtra.statusWarning)
        assertEquals(RizqflowLightExtra.statusSerious, RizqflowDarkExtra.statusSerious)
        assertEquals(RizqflowLightExtra.statusCritical, RizqflowDarkExtra.statusCritical)
    }

    @Test
    fun `batas skala font angka utama dan label navigasi sesuai desain`() {
        assertEquals(1.25f, HERO_MAX_FONT_SCALE)
        assertEquals(1.3f, NAV_LABEL_MAX_FONT_SCALE)
    }

    @Test
    fun `empat tab utama dan tombol Catat hanya di Denah dan Transaksi`() {
        assertEquals(listOf("denah", "transaksi", "ruang", "lainnya"), TopTab.entries.map { it.route })
        assertEquals(listOf(TopTab.Denah, TopTab.Transaksi), TopTab.entries.filter { it.showsFab })
    }

    @Test
    fun `rute tidak dikenal kembali ke Denah`() {
        assertEquals(TopTab.Denah, TopTab.fromRoute(null))
        assertEquals(TopTab.Denah, TopTab.fromRoute("tidak-ada"))
        assertEquals(TopTab.Ruang, TopTab.fromRoute("ruang"))
    }
}
