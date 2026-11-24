package net.badacid.homeassist.echoshow.devices.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/camera/picture-updates.test.ts. */
class PictureUpdatesTest {
    private val stopped = LiveStopped(at = 1_000_000, recordingId = "rec-1")

    @Test
    fun `says the picture is updating after live view, until the camera has a new recording`() {
        assertEquals(PictureLabel.UPDATING, labelFor("rec-1", stopped, stopped.at + 5000))
        assertEquals(PictureLabel.LAST_RECORDING, labelFor("rec-2", stopped, stopped.at + 5000))
    }

    @Test
    fun `gives up waiting after a while`() {
        assertEquals(PictureLabel.UPDATING, labelFor("rec-1", stopped, stopped.at + PICTURE_UPDATE_WAIT_MS - 1))
        assertEquals(PictureLabel.LAST_RECORDING, labelFor("rec-1", stopped, stopped.at + PICTURE_UPDATE_WAIT_MS))
    }

    @Test
    fun `labels a recording when live view has not been watched`() {
        assertEquals(PictureLabel.LAST_RECORDING, labelFor("rec-1", null, stopped.at))
    }

    @Test
    fun `leaves cameras whose picture is not a recording unlabelled`() {
        assertNull(labelFor(null, stopped, stopped.at))
        assertNull(labelFor(null, null, stopped.at))
    }

    @Test
    fun `the labels read as on the TV`() {
        assertEquals("Updating picture…", PictureLabel.UPDATING.words)
        assertEquals("Last recording", PictureLabel.LAST_RECORDING.words)
        assertEquals(180_000L, PICTURE_UPDATE_WAIT_MS)
    }

    @Test
    fun `keeps the latest stop for each camera and tells listeners`() {
        val updates = PictureUpdates()
        var calls = 0
        val stop = updates.addListener { calls += 1 }

        updates.noteLiveStopped("camera.front_door", LiveStopped(1, "rec-1"))
        updates.noteLiveStopped("camera.front_door", LiveStopped(2, "rec-1"))
        stop()
        updates.noteLiveStopped("camera.front_door", LiveStopped(3, "rec-1"))

        assertEquals(2, calls)
        assertEquals(LiveStopped(3, "rec-1"), updates.liveStoppedAt("camera.front_door"))
        assertNull(updates.liveStoppedAt("camera.garden"))
    }

    @Test
    fun `a listener that stops itself while being told does not upset the others`() {
        val updates = PictureUpdates()
        var second = 0
        lateinit var stopFirst: () -> Unit
        stopFirst = updates.addListener { stopFirst() }
        updates.addListener { second += 1 }

        updates.noteLiveStopped("camera.front_door", LiveStopped(1, "rec-1"))
        updates.noteLiveStopped("camera.front_door", LiveStopped(2, "rec-1"))

        assertEquals(2, second)
    }
}
