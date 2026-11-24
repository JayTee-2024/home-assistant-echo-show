package net.badacid.homeassist.echoshow.core

import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/pending-states.test.ts: showing a press at once, before Home Assistant confirms it. */
class PendingStatesTest {
    private val now = 1_700_000_000_000L
    private val lampOn = sampleEntity("light.lounge_lamp")
    private val lampOff = changed(lampOn, emptyMap(), "off")
    private val none: PendingStates = emptyMap()

    @Nested
    inner class WithPendingAndApplyPending {
        @Test
        fun `lays the predicted state over the real one`() {
            val pending = withPending(none, lampOff, now)
            assertEquals("off", applyPending(sampleHome(), pending).states["light.lounge_lamp"]?.state)
        }

        @Test
        fun `leaves every other entity as it is`() {
            val home = sampleHome()
            val shown = applyPending(home, withPending(none, lampOff, now))
            assertSame(home.states["switch.tv_plug"], shown.states["switch.tv_plug"])
            assertSame(home.entities, shown.entities)
        }

        @Test
        fun `gives back the same home when nothing is pending`() {
            val home = sampleHome()
            assertSame(home, applyPending(home, none))
        }

        @Test
        fun `does not change the home or the pending list it was given`() {
            val home = sampleHome()
            val pending = withPending(none, lampOff, now)
            applyPending(home, pending)
            withPending(pending, changed(sampleEntity("switch.tv_plug"), emptyMap(), "off"), now)
            assertEquals("on", home.states["light.lounge_lamp"]?.state)
            assertEquals(listOf("light.lounge_lamp"), pending.keys.toList())
        }

        @Test
        fun `ignores a pending state for an entity the home no longer has`() {
            val home = sampleHome().let { it.copy(states = it.states - "light.lounge_lamp") }
            assertNull(applyPending(home, withPending(none, lampOff, now)).states["light.lounge_lamp"])
        }
    }

    @Nested
    inner class WithoutPending {
        @Test
        fun `removes one entity, as after a failed command`() {
            val pending = withPending(withPending(none, lampOff, now), sampleEntity("switch.tv_plug"), now)
            assertEquals(listOf("switch.tv_plug"), withoutPending(pending, "light.lounge_lamp").keys.toList())
        }

        @Test
        fun `gives back the same list when the entity was not pending`() {
            val pending = withPending(none, lampOff, now)
            assertSame(pending, withoutPending(pending, "switch.tv_plug"))
        }
    }

    @Nested
    inner class Settle {
        @Test
        fun `drops a prediction once Home Assistant reports a new state for that entity`() {
            val before = sampleHome()
            val after = before.copy(states = before.states + ("light.lounge_lamp" to lampOff))
            val pending = withPending(none, lampOff, now)
            assertEquals(emptyMap<String, Pending>(), settle(pending, before, after, now + 200))
        }

        @Test
        fun `drops a prediction when Home Assistant sends the same state again as a new report`() {
            // A report is a new object, even when it says exactly what the old one said.
            val before = sampleHome()
            val again = lampOn.copy()
            val after = before.copy(states = before.states + ("light.lounge_lamp" to again))
            val pending = withPending(none, lampOff, now)
            assertEquals(emptyMap<String, Pending>(), settle(pending, before, after, now + 200))
        }

        @Test
        fun `keeps a prediction while Home Assistant has reported nothing new for that entity`() {
            val before = sampleHome()
            // Some other entity changed. The lamp's state object is the same one as before.
            val after = before.copy(states = before.states + ("switch.tv_plug" to changed(sampleEntity("switch.tv_plug"), emptyMap(), "off")))
            val pending = withPending(none, lampOff, now)
            assertEquals(listOf("light.lounge_lamp"), settle(pending, before, after, now + 200).keys.toList())
        }

        @Test
        fun `drops a prediction that has waited too long, so a lost command cannot show a wrong state for ever`() {
            val home = sampleHome()
            val pending = withPending(none, lampOff, now)
            assertEquals(listOf("light.lounge_lamp"), settle(pending, home, home, now + PENDING_LIMIT_MS - 1).keys.toList())
            assertEquals(emptyMap<String, Pending>(), settle(pending, home, home, now + PENDING_LIMIT_MS))
        }

        @Test
        fun `gives back the same list when nothing settled`() {
            val home = sampleHome()
            val pending = withPending(none, lampOff, now)
            assertSame(pending, settle(pending, home, home, now + 1))
        }
    }
}
