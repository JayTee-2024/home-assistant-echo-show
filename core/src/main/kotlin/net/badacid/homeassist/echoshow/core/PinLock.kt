/*
 * The rules of the optional PIN lock: four digits, five wrong tries and then a wait.
 * The lock screen and when it shows are the app's; these are only the rules.
 */
package net.badacid.homeassist.echoshow.core

private val FOUR_DIGITS = Regex("^[0-9]{4}$")

/** True for exactly four digits 0-9. */
fun isValidPin(text: String): Boolean = FOUR_DIGITS.matches(text)
