package net.badacid.homeassist.echoshow.core

/**
 * How long to wait before reconnecting attempt number `attempt` (counted from 0): at once, then one second
 * more each time, up to five. The same as Home Assistant's own JavaScript library, which the LG app used.
 */
fun reconnectDelayMs(attempt: Int): Long = attempt.coerceIn(0, 5) * 1000L
