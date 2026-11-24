package net.badacid.homeassist.echoshow.core

/** How long the screen waits before blanking, as the original Echo app: 30 seconds. */
const val DEFAULT_BLANK_AFTER_SECONDS = 30

/** The choices in Settings, in seconds. 0 is "Never". */
val BLANK_AFTER_CHOICES = listOf(0, 30, 60, 120, 300, 600)

/**
 * Decides when the screen goes black, as the original Echo app's ScreenSaver: `blankAfterMs` after the last
 * touch, and never while something `active` is on screen (live video). A touch on a blank screen only wakes
 * it (onTouch returns true: the touch should not reach the app). 0 never blanks. Use from one thread.
 */
class ScreenSaver(private var blankAfterMs: Long, private val clock: () -> Long) {
    private var lastActivity = clock()
    private var active = false
    private var blank = false

    /** A new time, as when it is changed in Settings. The last touch still counts. */
    fun setBlankAfter(ms: Long) {
        blankAfterMs = ms
    }

    /** Whether something that should keep the screen awake is showing. */
    fun onActive(isActive: Boolean) {
        if (isActive) {
            active = true
            blank = false
        } else if (active) {
            active = false
            lastActivity = clock() // The time starts again when it ends.
        }
    }

    /** True when the touch only woke the screen and should not reach the app. */
    fun onTouch(): Boolean {
        val wasBlank = isBlank()
        blank = false
        lastActivity = clock()
        return wasBlank
    }

    fun isBlank(): Boolean {
        if (blankAfterMs <= 0 || active) return false
        if (!blank && clock() - lastActivity >= blankAfterMs) blank = true
        return blank
    }

    /** Milliseconds until the screen blanks, or null when it will not (active, or never). */
    fun msUntilBlank(): Long? {
        if (blankAfterMs <= 0 || active) return null
        return maxOf(0L, blankAfterMs - (clock() - lastActivity))
    }
}
