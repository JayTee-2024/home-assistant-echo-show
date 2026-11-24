/*
 * Sending one change after a burst of presses: heating's target, for example, is sent once the person
 * stops pressing, not on every press.
 */
package net.badacid.homeassist.echoshow.core

/** Something waiting that can be called off. */
fun interface Cancellable {
    fun cancel()
}

/** Runs something later. The app gives one backed by Android's main-thread Handler; tests work one by hand. */
fun interface Scheduler {
    fun schedule(delayMs: Long, run: () -> Unit): Cancellable
}

interface Debouncer {
    /** Starts the wait again. */
    fun trigger()

    /** Runs now if something is waiting, as when the panel closes before the time is up. */
    fun flush()

    /** Forgets what was waiting. */
    fun cancel()
}

fun createDebouncer(run: () -> Unit, ms: Long, scheduler: Scheduler): Debouncer = object : Debouncer {
    private var waiting: Cancellable? = null

    private fun stop() {
        waiting?.cancel()
        waiting = null
    }

    override fun trigger() {
        stop()
        waiting = scheduler.schedule(ms) {
            waiting = null
            run()
        }
    }

    override fun flush() {
        if (waiting == null) return
        stop()
        run()
    }

    override fun cancel() = stop()
}
