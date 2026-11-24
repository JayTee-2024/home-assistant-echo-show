package net.badacid.homeassist.echoshow.devices.mediaplayer

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/media-player/media-player.test.ts. */
class MediaPlayerTest {
    private val sonos = sampleEntity("media_player.living_room_sonos") // playing, 32%, features 18493
    private val none = JsonObject(emptyMap())

    private fun call(action: Action?) = action?.let { it.call.service to it.call.data }

    @Nested
    inner class ReadMediaPlayer {
        @Test
        fun `reads a speaker that is playing`() {
            val view = readMediaPlayer(sonos)
            assertEquals("playing", view.state)
            assertTrue(view.isPlaying)
            assertEquals("Clair de Lune", view.title)
            assertEquals("Claude Debussy", view.artist)
            assertEquals(32, view.volumePercent)
            assertFalse(view.isMuted)
            assertEquals("Radio", view.source)
            assertEquals(listOf("Radio", "TV", "Line-in"), view.sources)
        }

        @Test
        fun `reads what the speaker supports from its features`() {
            val view = readMediaPlayer(sonos)
            assertTrue(view.canPlayPause)
            assertTrue(view.canSetVolume)
            assertTrue(view.canMute)
            assertTrue(view.canSkip)
            assertTrue(view.canSelectSource)
            assertFalse(view.canTurnOn)
            assertFalse(view.canTurnOff)
        }

        @Test
        fun `reads a player that is off and reports nothing else`() {
            val view = readMediaPlayer(EntityState("media_player.tv", "off", none))
            assertFalse(view.isPlaying)
            assertNull(view.title)
            assertNull(view.volumePercent)
            assertFalse(view.canPlayPause)
            assertEquals(emptyList<String>(), view.sources)
        }

        @Test
        fun `reads an unavailable player as not available`() {
            assertFalse(readMediaPlayer(changed(sonos, emptyMap(), "unavailable")).isAvailable)
        }

        @Test
        fun `counts only true as muted`() {
            assertFalse(readMediaPlayer(changed(sonos, mapOf("is_volume_muted" to "true"))).isMuted)
            assertTrue(readMediaPlayer(changed(sonos, mapOf("is_volume_muted" to true))).isMuted)
        }
    }

    @Test
    fun `nextVolume moves in steps of 5, between 0 and 100`() {
        assertEquals(35, nextVolume(32, 1))
        assertEquals(30, nextVolume(32, -1))
        assertEquals(100, nextVolume(100, 1))
        assertEquals(0, nextVolume(0, -1))
        assertEquals(5, nextVolume(null, 1))
    }

    @Nested
    inner class MediaPlayerActions {
        @Test
        fun `plays and pauses, predicting the other state`() {
            assertEquals("media_play_pause" to none, call(playPause(sonos)))
            assertEquals("paused", playPause(sonos)?.predicted?.state)
            assertEquals("playing", playPause(changed(sonos, emptyMap(), "paused"))?.predicted?.state)
        }

        @Test
        fun `skips forward and back`() {
            assertEquals("media_next_track" to none, call(next(sonos)))
            assertEquals("media_previous_track" to none, call(previous(sonos)))
        }

        @Test
        fun `sets the volume as a fraction, and predicts it`() {
            assertEquals("volume_set" to jsonObjectOf("volume_level" to 0.45), call(setVolume(sonos, 45)))
            assertEquals(45, readMediaPlayer(setVolume(sonos, 45).predicted).volumePercent)
        }

        @Test
        fun `mutes, chooses a source, and turns on and off`() {
            assertEquals("volume_mute" to jsonObjectOf("is_volume_muted" to true), call(mute(sonos, true)))
            assertEquals("select_source" to jsonObjectOf("source" to "TV"), call(selectSource(sonos, "TV")))
            assertEquals("turn_on" to none, call(turnOn(sonos)))
            assertEquals("turn_off" to none, call(turnOff(sonos)))
            assertEquals("media_player", turnOff(sonos).call.domain)
        }
    }

    @Nested
    inner class PlayAndPauseOnAPlayerThatSupportsOnlyOneOfThem {
        private val playOnly = changed(sonos, mapOf("supported_features" to 16384), "paused")
        private val pauseOnly = changed(sonos, mapOf("supported_features" to 1), "playing")

        @Test
        fun `sends play to a paused player that can only play`() {
            assertEquals("media_play", playPause(playOnly)?.call?.service)
        }

        @Test
        fun `sends pause to a playing player that can only pause`() {
            assertEquals("media_pause", playPause(pauseOnly)?.call?.service)
        }

        @Test
        fun `has nothing to send when the one it supports is not the one needed`() {
            assertNull(playPause(changed(playOnly, emptyMap(), "playing")))
            assertEquals(OkAction.OpenPanel, MediaPlayerLogic.okAction(changed(playOnly, emptyMap(), "playing")))
        }
    }

    @Nested
    inner class TheMediaPlayerType {
        @Test
        fun `is a wide card that plays or pauses on OK`() {
            assertEquals(CardSize.WIDE, MediaPlayerLogic.cardSize)
            assertEquals(OkAction.Run(playPause(sonos)!!), MediaPlayerLogic.okAction(sonos))
        }

        @Test
        fun `opens its panel on OK when it cannot play or pause, and does nothing when unavailable`() {
            assertEquals(OkAction.OpenPanel, MediaPlayerLogic.okAction(changed(sonos, mapOf("supported_features" to 4))))
            assertEquals(OkAction.Nothing, MediaPlayerLogic.okAction(changed(sonos, emptyMap(), "unavailable")))
        }

        @Test
        fun `is shown as on while playing`() {
            assertTrue(MediaPlayerLogic.isActive(sonos))
            assertFalse(MediaPlayerLogic.isActive(changed(sonos, emptyMap(), "paused")))
        }

        @Test
        fun `sums itself up as what is playing`() {
            assertEquals("Playing · Clair de Lune", MediaPlayerLogic.summary(sonos))
            assertEquals("Idle", MediaPlayerLogic.summary(changed(sonos, mapOf("media_title" to null), "idle")))
        }
    }
}
