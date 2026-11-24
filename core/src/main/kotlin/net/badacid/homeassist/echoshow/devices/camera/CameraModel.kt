package net.badacid.homeassist.echoshow.devices.camera

import kotlinx.serialization.json.JsonPrimitive
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.LiveState
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.numberOrNull
import net.badacid.homeassist.echoshow.core.numberText
import net.badacid.homeassist.echoshow.core.readText

data class CameraView(
    val isAvailable: Boolean,
    /** The picture's path on the Home Assistant server, or null when there is none. */
    val picturePath: String?,
    /**
     * Which recording the picture comes from (`last_video_id`), for cameras whose picture is their last
     * recording rather than a current view, such as Ring. Null for other cameras. See PictureUpdates.kt.
     */
    val recordingId: String?,
)

fun readCamera(entity: EntityState): CameraView {
    val available = isAvailable(entity)
    return CameraView(
        isAvailable = available,
        picturePath = if (available) readText(entity, "entity_picture") else null,
        recordingId = if (available) readRecordingId(entity) else null,
    )
}

/** Ring ids are large numbers, sent as text or as a number. */
private fun readRecordingId(entity: EntityState): String? {
    val value = entity.attributes["last_video_id"] as? JsonPrimitive ?: return null
    if (value.isString) return value.content
    // A whole number is kept exactly as sent, however large.
    if (Regex("-?\\d+").matches(value.content)) return value.content
    return numberOrNull(value)?.let { numberText(it) }
}

/**
 * The picture's full address, with a count that makes the Echo fetch a fresh one. null when there is no
 * picture, or when the path is not a path on the Home Assistant server: one "/" and then the rest. The
 * address and path are joined as text, so anything else could change which server the picture comes from
 * ("@other.example/x" would turn the address into a username).
 */
fun pictureUrl(homeAddress: String, picturePath: String?, refreshCount: Int): String? {
    if (picturePath == null) return null
    if (!picturePath.startsWith("/") || picturePath.startsWith("//")) return null
    val base = homeAddress.removeSuffix("/")
    val joiner = if ('?' in picturePath) "&" else "?"
    return "$base$picturePath${joiner}_=$refreshCount"
}

/** The words on the live view's buttons and its status line. */
data class CameraControls(
    /** 'Watch live', 'Stop live' or 'Watch again'. */
    val liveLabel: String,
    /** 'Sound on' or 'Sound off'; null when there is no sound to switch: before live video, and after it. */
    val soundLabel: String?,
    val status: String?,
)

fun cameraControls(state: LiveState, isMuted: Boolean): CameraControls {
    val soundLabel = if (isMuted) "Sound on" else "Sound off"
    return when (state) {
        LiveState.IDLE -> CameraControls("Watch live", null, null)
        LiveState.CONNECTING -> CameraControls("Stop live", soundLabel, "Connecting…")
        LiveState.PLAYING -> CameraControls("Stop live", soundLabel, "Live")
        LiveState.ENDED -> CameraControls("Watch again", null, "Live view ended")
        LiveState.FAILED -> CameraControls("Watch again", null, "Live view did not start")
    }
}
