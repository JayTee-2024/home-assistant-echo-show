/*
 * The label on a camera's picture, for cameras whose picture is their last recording.
 *
 * For a Ring doorbell, Home Assistant's picture is the first frame of the camera's last recording (it
 * reports which one as the camera's `last_video_id`), not a current view, so it is labelled "Last
 * recording". With a Ring subscription, Ring records a live view as an event too; once Ring has processed
 * it, 20 seconds to a minute after live view stops, Home Assistant's picture becomes that new recording.
 * Until then the picture says "Updating picture…".
 *
 * Cameras without a `last_video_id` have a current picture, and get no label.
 */
package net.badacid.homeassist.echoshow.devices.camera

/** How long to wait for the new recording before going back to "Last recording". */
const val PICTURE_UPDATE_WAIT_MS = 3 * 60 * 1000L

/** When live view last stopped for a camera, and which recording its picture was then. */
data class LiveStopped(val at: Long, val recordingId: String)

enum class PictureLabel(val words: String) {
    UPDATING("Updating picture…"),
    LAST_RECORDING("Last recording"),
}

/** `recordingId` is the camera's current `last_video_id`, or null when it has none. */
fun labelFor(recordingId: String?, stopped: LiveStopped?, now: Long): PictureLabel? {
    if (recordingId == null) return null
    val isWaiting = stopped != null &&
        stopped.recordingId == recordingId &&
        now - stopped.at < PICTURE_UPDATE_WAIT_MS
    return if (isWaiting) PictureLabel.UPDATING else PictureLabel.LAST_RECORDING
}

/** When live view last stopped for each camera, kept only while the app runs. The app holds one of these. */
class PictureUpdates {
    private val stops = mutableMapOf<String, LiveStopped>()
    private val listeners = mutableListOf<() -> Unit>()

    /** Called by the camera's view when live video stops. */
    fun noteLiveStopped(entityId: String, stopped: LiveStopped) {
        stops[entityId] = stopped
        listeners.toList().forEach { it() }
    }

    fun liveStoppedAt(entityId: String): LiveStopped? = stops[entityId]

    /** Calls `listener` whenever live view stops for any camera. Returns a function that stops it. */
    fun addListener(listener: () -> Unit): () -> Unit {
        val entry = { listener() }
        listeners += entry
        return { listeners.remove(entry) }
    }
}
