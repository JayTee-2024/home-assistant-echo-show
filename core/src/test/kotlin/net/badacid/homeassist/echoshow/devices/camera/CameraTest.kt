package net.badacid.homeassist.echoshow.devices.camera

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.LiveState
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.PanelLayout
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/camera/camera.test.ts. */
class CameraTest {
    private val doorbell = sampleEntity("camera.front_door")
    private val address = "http://192.168.1.100:8123"

    @Nested
    inner class ReadCamera {
        @Test
        fun `reads the picture's path, which carries Home Assistant's own short-lived key`() {
            assertEquals(
                CameraView(true, "/api/camera_proxy/camera.front_door?token=samplecameratoken", "7432109876543"),
                readCamera(doorbell),
            )
        }

        @Test
        fun `has no picture when Home Assistant gives none, or the camera is unavailable`() {
            assertNull(readCamera(changed(doorbell, mapOf("entity_picture" to null))).picturePath)
            assertEquals(CameraView(false, null, null), readCamera(changed(doorbell, emptyMap(), "unavailable")))
        }

        @Test
        fun `reads which recording the picture comes from, for cameras that say`() {
            assertEquals("7432", readCamera(changed(doorbell, mapOf("last_video_id" to "7432"))).recordingId)
            assertNull(readCamera(changed(doorbell, mapOf("last_video_id" to null))).recordingId)
        }

        @Test
        fun `accepts a recording number as well as text, as Ring ids are large numbers`() {
            assertEquals(
                "7432109876543",
                readCamera(changed(doorbell, mapOf("last_video_id" to 7432109876543L))).recordingId,
            )
        }
    }

    @Nested
    inner class PictureUrl {
        @Test
        fun `joins the address and the path, with a count that makes the Echo fetch a fresh picture`() {
            assertEquals(
                "$address/api/camera_proxy/camera.a?token=x&_=3",
                pictureUrl(address, "/api/camera_proxy/camera.a?token=x", 3),
            )
            assertEquals("$address/api/picture?_=0", pictureUrl(address, "/api/picture", 0))
        }

        @Test
        fun `gives null when there is no picture`() {
            assertNull(pictureUrl(address, null, 1))
        }

        @Test
        fun `copes with an address that ends in a slash`() {
            assertEquals("$address/p?_=1", pictureUrl("$address/", "/p", 1))
        }

        @Test
        fun `only accepts a path on the Home Assistant server`() {
            // "@host" after the address would make the address read as a username.
            assertNull(pictureUrl(address, "@other.example/x.jpg", 1))
            assertNull(pictureUrl(address, ".other.example/x.jpg", 1))
            assertNull(pictureUrl(address, "http://other.example/x.jpg", 1))
            assertNull(pictureUrl(address, "//other.example/x.jpg", 1))
            assertNull(pictureUrl(address, "", 1))
            assertEquals(
                "http://192.168.1.100:8123/api/camera_proxy/camera.a?token=t&_=1",
                pictureUrl(address, "/api/camera_proxy/camera.a?token=t", 1),
            )
        }
    }

    @Test
    fun `is a large card that opens its panel on OK, and is never shown as on`() {
        assertEquals(CardSize.LARGE, CameraLogic.cardSize)
        assertEquals(OkAction.OpenPanel, CameraLogic.okAction(doorbell))
        assertFalse(CameraLogic.isActive(doorbell))
        assertNull(CameraLogic.summary(doorbell))
        assertEquals(PanelLayout.FULL_SCREEN, CameraLogic.panelLayout)
        assertEquals(10, CameraLogic.mainOrder)
    }

    @Nested
    inner class CameraControlsLabels {
        @Test
        fun `offers Watch live, and nothing else, before live video is asked for`() {
            assertEquals(CameraControls("Watch live", null, null), cameraControls(LiveState.IDLE, true))
        }

        @Test
        fun `offers Stop live and the sound while connecting and playing`() {
            assertEquals(CameraControls("Stop live", "Sound on", "Connecting…"), cameraControls(LiveState.CONNECTING, true))
            assertEquals(CameraControls("Stop live", "Sound off", "Live"), cameraControls(LiveState.PLAYING, false))
        }

        @Test
        fun `offers Watch again once the session has ended or failed`() {
            assertEquals(CameraControls("Watch again", null, "Live view ended"), cameraControls(LiveState.ENDED, true))
            assertEquals(CameraControls("Watch again", null, "Live view did not start"), cameraControls(LiveState.FAILED, false))
        }
    }
}

class CameraRecordingIdTest {
    @Test
    fun `keeps a recording number larger than a double can hold exactly`() {
        val doorbell = changed(sampleEntity("camera.front_door"), mapOf("last_video_id" to 7432109876543210987L))
        assertEquals("7432109876543210987", readCamera(doorbell).recordingId)
    }
}
