package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/live-video.test.ts: the states a live session goes through. */
class LiveVideoTest {
    @Test
    fun `starts connecting from idle, and again after an end or a failure`() {
        assertEquals(LiveState.CONNECTING, nextLiveState(LiveState.IDLE, LiveEvent.START))
        assertEquals(LiveState.CONNECTING, nextLiveState(LiveState.ENDED, LiveEvent.START))
        assertEquals(LiveState.CONNECTING, nextLiveState(LiveState.FAILED, LiveEvent.START))
    }

    @Test
    fun `plays once the picture arrives`() {
        assertEquals(LiveState.PLAYING, nextLiveState(LiveState.CONNECTING, LiveEvent.PICTURE))
        assertEquals(LiveState.PLAYING, nextLiveState(LiveState.PLAYING, LiveEvent.PICTURE))
    }

    @Test
    fun `fails when the session breaks before any picture arrived`() {
        for (event in listOf(LiveEvent.CONNECTION_LOST, LiveEvent.REFUSED, LiveEvent.TIMED_OUT)) {
            assertEquals(LiveState.FAILED, nextLiveState(LiveState.CONNECTING, event), event.name)
        }
    }

    @Test
    fun `ends when a playing session is cut off`() {
        assertEquals(LiveState.ENDED, nextLiveState(LiveState.PLAYING, LiveEvent.CONNECTION_LOST))
        assertEquals(LiveState.ENDED, nextLiveState(LiveState.PLAYING, LiveEvent.REFUSED))
    }

    @Test
    fun `ignores the time limit once the picture is playing`() {
        assertEquals(LiveState.PLAYING, nextLiveState(LiveState.PLAYING, LiveEvent.TIMED_OUT))
    }

    @Test
    fun `goes back to idle when stopped, from anywhere`() {
        for (state in LiveState.entries) assertEquals(LiveState.IDLE, nextLiveState(state, LiveEvent.STOP), state.name)
    }

    @Test
    fun `ignores late events once the session is over`() {
        for (state in listOf(LiveState.IDLE, LiveState.ENDED, LiveState.FAILED)) {
            assertEquals(state, nextLiveState(state, LiveEvent.PICTURE))
            assertEquals(state, nextLiveState(state, LiveEvent.CONNECTION_LOST))
        }
    }

    @Test
    fun `does not start a second session while one is running`() {
        assertEquals(LiveState.CONNECTING, nextLiveState(LiveState.CONNECTING, LiveEvent.START))
        assertEquals(LiveState.PLAYING, nextLiveState(LiveState.PLAYING, LiveEvent.START))
    }

    @Test
    fun `waits 30 seconds for the picture`() {
        assertEquals(30_000L, LIVE_PICTURE_TIMEOUT_MS)
    }
}
