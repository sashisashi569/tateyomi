package app.tateyomi

import app.tateyomi.reader.needsManualVerticalRotation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalGlyphsTest {

    @Test
    fun ellipsesAndDashesAreRotatedManually() {
        assertTrue(needsManualVerticalRotation('…'))
        assertTrue(needsManualVerticalRotation('‥'))
        assertTrue(needsManualVerticalRotation('—'))
        assertTrue(needsManualVerticalRotation('ー'))
    }

    @Test
    fun normalJapanesePunctuationIsLeftToTheFont() {
        assertFalse(needsManualVerticalRotation('。'))
        assertFalse(needsManualVerticalRotation('、'))
        assertFalse(needsManualVerticalRotation('「'))
        assertFalse(needsManualVerticalRotation('あ'))
        assertFalse(needsManualVerticalRotation('A'))
    }
}
