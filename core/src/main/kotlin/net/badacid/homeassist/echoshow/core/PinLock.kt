/*
 * The rules of the optional PIN lock: four digits, five wrong tries and then a 30-second wait after each
 * further wrong try. "Forgot PIN?" signs out (see SavedState.afterSignOut). The lock screen and when it
 * shows are the app's; these are only the rules.
 */
package net.badacid.homeassist.echoshow.core

import kotlin.math.ceil

const val PIN_LENGTH = 4

/** After this many wrong tries in a row, every further wrong try brings a wait. */
const val MAX_WRONG_TRIES = 5
const val WAIT_AFTER_WRONG_MS = 30_000L

private val FOUR_DIGITS = Regex("^[0-9]{4}$")
private val ONE_DIGIT = Regex("^[0-9]$")

/** What has been typed on the lock screen so far. */
data class PinEntry(
    val digits: String,
    /** Wrong tries since the lock screen appeared. */
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
    if (now < entry.waitUntil) return PinStep(entry.copy(digits = ""), PinResult.WAITING)

    val digits = entry.digits + digit
    if (digits.length < PIN_LENGTH) return PinStep(entry.copy(digits = digits), PinResult.MORE)
    if (digits == pin) return PinStep(EMPTY_PIN_ENTRY, PinResult.UNLOCKED)

    val wrongTries = entry.wrongTries + 1
    val waitUntil = if (wrongTries >= MAX_WRONG_TRIES) now + WAIT_AFTER_WRONG_MS else 0
    return PinStep(PinEntry("", wrongTries, waitUntil), PinResult.WRONG)
}

fun deleteDigit(entry: PinEntry): PinEntry =
    if (entry.digits.isEmpty()) entry else entry.copy(digits = entry.digits.dropLast(1))

/** Whole seconds left of the wait, rounded up. 0 when there is none. */
fun secondsToWait(entry: PinEntry, now: Long): Int =
    maxOf(0, ceil((entry.waitUntil - now) / 1000.0).toInt())
