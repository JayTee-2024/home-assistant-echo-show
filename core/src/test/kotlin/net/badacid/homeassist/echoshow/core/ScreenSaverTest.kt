package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ported from the original Echo app's ScreenSaverTest. */
class ScreenSaverTest {
    private var now = 1_000L
    private fun saver(ms: Long = 30_000) = ScreenSaver(ms) { now }

    @Test
    fun `blanks after the time with no touch`() {
        val saver = saver()
        now += 29_999
        assertFalse(saver.isBlank())
        assertEquals(1L, saver.msUntilBlank())
        now += 1
        assertTrue(saver.isBlank())
    }

    @Test
    fun `a touch restarts the time, and reports when it only woke the screen`() {
        val saver = saver()
        now += 20_000
        assertFalse(saver.onTouch())
        now += 20_000
        assertFalse(saver.isBlank())
        now += 10_000
        assertTrue(saver.isBlank())
        assertTrue(saver.onTouch())
        assertFalse(saver.isBlank())
    }

    @Test
    fun `zero never blanks`() {
        val saver = saver(0)
        now += 10_000_000
        assertFalse(saver.isBlank())
        assertNull(saver.msUntilBlank())
    }

    @Test
    fun `while active it stays awake, and the time starts again when it ends`() {
        val saver = saver()
        saver.onActive(true)
        now += 100_000
        assertFalse(saver.isBlank())
        assertNull(saver.msUntilBlank())
        saver.onActive(false)
        now += 29_000
        assertFalse(saver.isBlank())
        now += 1_000
        assertTrue(saver.isBlank())
    }

    @Test
    fun `changing the time keeps the last touch`() {
        val saver = saver()
        now += 20_000
        saver.setBlankAfter(10_000)
        assertTrue(saver.isBlank())
    }
}
