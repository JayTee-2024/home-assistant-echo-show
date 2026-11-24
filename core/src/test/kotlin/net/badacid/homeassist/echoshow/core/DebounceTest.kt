package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/debounce.test.ts: sending one change after a burst of presses. */
class DebounceTest {
    /** A scheduler worked by hand: the test decides when the wait is over. */
    private class HandScheduler : Scheduler {
        private var pending: (() -> Unit)? = null
        val waits = mutableListOf<Long>()
        val isWaiting get() = pending != null

        override fun schedule(delayMs: Long, run: () -> Unit): Cancellable {
            pending = run
            waits += delayMs
            return Cancellable { if (pending === run) pending = null }
        }

        fun fire() {
            val run = pending
            pending = null
            run?.invoke()
        }
    }

    @Test
    fun `runs once, after the last of many triggers`() {
        val timer = HandScheduler()
        var runs = 0
        val debouncer = createDebouncer({ runs += 1 }, 1500, timer)

        debouncer.trigger()
        debouncer.trigger()
        debouncer.trigger()
        assertEquals(0, runs)

        timer.fire()
        assertEquals(1, runs)
        assertEquals(listOf(1500L, 1500L, 1500L), timer.waits)
    }

    @Test
    fun `runs at once on flush, and not again when the time would have passed`() {
        val timer = HandScheduler()
        var runs = 0
        val debouncer = createDebouncer({ runs += 1 }, 1500, timer)

        debouncer.trigger()
        debouncer.flush()
        timer.fire()

        assertEquals(1, runs)
    }

    @Test
    fun `does nothing on flush when nothing is waiting`() {
        val timer = HandScheduler()
        var runs = 0
        createDebouncer({ runs += 1 }, 1500, timer).flush()
        assertEquals(0, runs)
    }

    @Test
    fun `never runs after cancel`() {
        val timer = HandScheduler()
        var runs = 0
        val debouncer = createDebouncer({ runs += 1 }, 1500, timer)

        debouncer.trigger()
        debouncer.cancel()
        timer.fire()
        debouncer.flush()

        assertEquals(0, runs)
        assertFalse(timer.isWaiting)
    }
}
