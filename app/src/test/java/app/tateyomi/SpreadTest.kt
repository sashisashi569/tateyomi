package app.tateyomi

import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.SpreadMode
import app.tateyomi.data.usesSpread
import app.tateyomi.reader.SpreadLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadTest {

    @Test
    fun autoSpreadsOnlyInLandscape() {
        val settings = ReaderSettings(spread = SpreadMode.AUTO)
        assertFalse(settings.usesSpread(landscape = false))
        assertTrue(settings.usesSpread(landscape = true))
    }

    @Test
    fun singleNeverSpreads() {
        val settings = ReaderSettings(spread = SpreadMode.SINGLE)
        assertFalse(settings.usesSpread(landscape = false))
        assertFalse(settings.usesSpread(landscape = true))
    }

    @Test
    fun spreadAlwaysSpreads() {
        val settings = ReaderSettings(spread = SpreadMode.SPREAD)
        assertTrue(settings.usesSpread(landscape = false))
        assertTrue(settings.usesSpread(landscape = true))
    }

    @Test
    fun defaultIsAuto() {
        assertEquals(SpreadMode.AUTO, ReaderSettings().spread)
    }

    @Test
    fun spreadGapDefaultsToZeroAndClamps() {
        assertEquals(0, ReaderSettings().spreadGapDp)
        assertEquals(-10, ReaderSettings(spreadGapDp = -10).normalized().spreadGapDp)
        assertEquals(-64, ReaderSettings(spreadGapDp = -999).normalized().spreadGapDp)
        assertEquals(64, ReaderSettings(spreadGapDp = 999).normalized().spreadGapDp)
        assertEquals(24, ReaderSettings(spreadGapDp = 24).normalized().spreadGapDp)
    }

    @Test
    fun spreadCountRoundsUp() {
        assertEquals(1, SpreadLayout.count(0))
        assertEquals(1, SpreadLayout.count(1))
        assertEquals(1, SpreadLayout.count(2))
        assertEquals(2, SpreadLayout.count(3))
        assertEquals(5, SpreadLayout.count(10))
        assertEquals(6, SpreadLayout.count(11))
    }

    @Test
    fun pagesPairInReadingOrder() {
        assertEquals(0, SpreadLayout.startPage(0, 10))
        assertEquals(1, SpreadLayout.secondPage(0, 10))
        assertEquals(8, SpreadLayout.startPage(4, 10))
        assertEquals(9, SpreadLayout.secondPage(4, 10))
    }

    @Test
    fun lastPageCanStandAlone() {
        assertEquals(10, SpreadLayout.startPage(5, 11))
        assertNull(SpreadLayout.secondPage(5, 11))
        assertEquals(8, SpreadLayout.startPage(4, 9))
        assertNull(SpreadLayout.secondPage(4, 9))
    }

    @Test
    fun pageIndexMapsToContainingSpread() {
        assertEquals(0, SpreadLayout.indexOfPage(0))
        assertEquals(0, SpreadLayout.indexOfPage(1))
        assertEquals(1, SpreadLayout.indexOfPage(2))
        assertEquals(4, SpreadLayout.indexOfPage(9))
        assertEquals(5, SpreadLayout.indexOfPage(10))
    }
}
