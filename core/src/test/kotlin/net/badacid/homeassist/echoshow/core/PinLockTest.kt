package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/pin-lock.test.ts. */
class PinLockTest {
    private val now = 1_000_000L

    /** Types every digit of `text` and returns the last step. */
    private fun type(entry: PinEntry, text: String, pin: String, at: Long = now): PinStep {
        var step = PinStep(entry, PinResult.MORE)
        for (digit in text) step = typeDigit(step.entry, digit.toString(), pin, at)
        return step
    }

    @Test
    fun `isValidPin accepts exactly four digits`() {
        assertTrue(isValidPin("0412"))
        for (bad in listOf("", "123", "12345", "12a4", " 123", "１２３４", "٠٤١٢")) assertFalse(isValidPin(bad), bad)
    }

    @Test
    fun `waits for more digits until there are four`() {
        val step = type(EMPTY_PIN_ENTRY, "123", "1234")
        assertEquals(PinResult.MORE, step.result)
        assertEquals("123", step.entry.digits)
    }

    @Test
    fun `unlocks on the right PIN`() {
        assertEquals(PinResult.UNLOCKED, type(EMPTY_PIN_ENTRY, "1234", "1234").result)
    }

    @Test
    fun `clears the digits and counts the try on a wrong PIN`() {
        val step = type(EMPTY_PIN_ENTRY, "9999", "1234")
        assertEquals(PinResult.WRONG, step.result)
        assertEquals(PinEntry("", 1, 0), step.entry)
    }

    @Test
    fun `ignores anything that is not a single digit`() {
        assertEquals(EMPTY_PIN_ENTRY, typeDigit(EMPTY_PIN_ENTRY, "a", "1234", now).entry)
        assertEquals(EMPTY_PIN_ENTRY, typeDigit(EMPTY_PIN_ENTRY, "12", "1234", now).entry)
    }

    @Test
    fun `makes the person wait after too many wrong tries, then allows one more go`() {
        var entry = EMPTY_PIN_ENTRY
        repeat(MAX_WRONG_TRIES) { entry = type(entry, "9999", "1234").entry }
        assertEquals(now + WAIT_AFTER_WRONG_MS, entry.waitUntil)
        assertEquals(30, secondsToWait(entry, now))

        // Even the right PIN is refused during the wait.
        val during = type(entry, "1234", "1234", now + 1000)
        assertEquals(PinResult.WAITING, during.result)
        assertEquals("", during.entry.digits)

        assertEquals(0, secondsToWait(entry, now + WAIT_AFTER_WRONG_MS))
        assertEquals(PinResult.UNLOCKED, type(entry, "1234", "1234", now + WAIT_AFTER_WRONG_MS).result)
    }

    @Test
    fun `waits again after each further wrong try`() {
        var entry = EMPTY_PIN_ENTRY
        repeat(MAX_WRONG_TRIES) { entry = type(entry, "9999", "1234").entry }
        val later = now + WAIT_AFTER_WRONG_MS
        entry = type(entry, "9999", "1234", later).entry
        assertEquals(later + 2 * WAIT_AFTER_WRONG_MS, entry.waitUntil)
    }

    @Test
    fun `the wait doubles with each further wrong try, up to 15 minutes`() {
        var entry = EMPTY_PIN_ENTRY
        var time = now
        val waits = mutableListOf<Long>()
        repeat(MAX_WRONG_TRIES + 7) {
            entry = type(entry, "9999", "1234", time).entry
            if (entry.waitUntil > 0) waits += entry.waitUntil - time
            time = maxOf(time, entry.waitUntil)
        }
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L, 900_000L), waits)
    }

    @Test
    fun `a wait far in the future, as after the clock changed, counts as the longest wait`() {
        val entry = PinEntry("", 9, now + 10 * 60 * 60 * 1000L)
        assertEquals(900, secondsToWait(entry, now))
        assertEquals(PinResult.WAITING, type(entry, "1234", "1234", now).result)
        // The lock screen settles such a wait when it appears, so it does end.
        val settled = settleWait(entry, now)
        assertEquals(now + 900_000, settled.waitUntil)
        assertEquals(PinResult.UNLOCKED, type(settled, "1234", "1234", now + 900_000).result)
        assertEquals(PinEntry("", 3, now + 5_000), settleWait(PinEntry("", 3, now + 5_000), now))
    }

    @Test
    fun `deleteDigit removes the last digit, and does nothing when there are none`() {
        assertEquals("1", deleteDigit(PinEntry("12", 2, 0)).digits)
        assertEquals(EMPTY_PIN_ENTRY, deleteDigit(EMPTY_PIN_ENTRY))
    }

    @Test
    fun `the rules are four digits, five tries and a 30 second wait`() {
        assertEquals(4, PIN_LENGTH)
        assertEquals(5, MAX_WRONG_TRIES)
        assertEquals(30_000L, WAIT_AFTER_WRONG_MS)
    }
}
