/*
 * The rules of the optional PIN lock: four digits; after five wrong tries a 30-second wait, which doubles
 * with each further wrong try up to 15 minutes, so guessing every PIN would take months. "Forgot PIN?" signs out (see SavedState.afterSignOut). The lock screen and when it
 * shows are the app's; these are only the rules.
 */
package net.badacid.homeassist.echoshow.core

import kotlin.math.ceil

const val PIN_LENGTH = 4

/** After this many wrong tries in a row, every further wrong try brings a wait. */
const val MAX_WRONG_TRIES = 5
const val WAIT_AFTER_WRONG_MS = 30_000L

/** The longest wait. Also what a wait counts as when the clock has changed and it looks longer. */
const val MAX_WAIT_MS = 15 * 60 * 1000L

/** The wait after the given number of wrong tries in a row: 0 below five, then 30 s, doubling to the limit. */
fun waitAfter(wrongTries: Int): Long {
    if (wrongTries < MAX_WRONG_TRIES) return 0
    val doublings = minOf(wrongTries - MAX_WRONG_TRIES, 10)
    return minOf(MAX_WAIT_MS, WAIT_AFTER_WRONG_MS shl doublings)
}

/** Milliseconds of the wait still to go; never more than the longest wait, whatever the clock says. */
private fun waitLeft(entry: PinEntry, now: Long): Long = (entry.waitUntil - now).coerceIn(0, MAX_WAIT_MS)

private val FOUR_DIGITS = Regex("^[0-9]{4}$")
private val ONE_DIGIT = Regex("^[0-9]$")

/** What has been typed on the lock screen so far. */
data class PinEntry(
    val digits: String,
    /** Wrong tries in a row. Kept across restarts (SavedState), so unplugging the Echo does not reset them. */
    val wrongTries: Int,
    /** No try is accepted before this time (milliseconds since 1970). 0 for no wait. */
    val waitUntil: Long,
)

val EMPTY_PIN_ENTRY = PinEntry("", 0, 0)

enum class PinResult {
    /** Fewer than four digits so far. */
    MORE,
    UNLOCKED,
    WRONG,

    /** Too many wrong tries: nothing is accepted until the wait is over. */
    WAITING,
}

data class PinStep(val entry: PinEntry, val result: PinResult)

/** True for exactly four digits 0-9. */
fun isValidPin(text: String): Boolean = FOUR_DIGITS.matches(text)

/** Adds one digit. When that makes four, compares them with `pin`. */
fun typeDigit(entry: PinEntry, digit: String, pin: String, now: Long): PinStep {
    if (!ONE_DIGIT.matches(digit)) return PinStep(entry, PinResult.MORE)
    if (waitLeft(entry, now) > 0) return PinStep(entry.copy(digits = ""), PinResult.WAITING)

    val digits = entry.digits + digit
    if (digits.length < PIN_LENGTH) return PinStep(entry.copy(digits = digits), PinResult.MORE)
    if (digits == pin) return PinStep(EMPTY_PIN_ENTRY, PinResult.UNLOCKED)

    val wrongTries = entry.wrongTries + 1
    val wait = waitAfter(wrongTries)
    val waitUntil = if (wait > 0) now + wait else 0
    return PinStep(PinEntry("", wrongTries, waitUntil), PinResult.WRONG)
}

/**
 * A wait that looks longer than the longest wait (the Echo's clock was changed, or lost on a power cut) is
 * brought down to the longest wait from now, so it still ends.
 */
fun settleWait(entry: PinEntry, now: Long): PinEntry =
    if (entry.waitUntil - now > MAX_WAIT_MS) entry.copy(waitUntil = now + MAX_WAIT_MS) else entry

fun deleteDigit(entry: PinEntry): PinEntry =
    if (entry.digits.isEmpty()) entry else entry.copy(digits = entry.digits.dropLast(1))

/** Whole seconds left of the wait, rounded up. 0 when there is none. */
fun secondsToWait(entry: PinEntry, now: Long): Int =
    ceil(waitLeft(entry, now) / 1000.0).toInt()
