package net.badacid.homeassist.echoshow.devices.mediaplayer

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.hasFeature
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.readNumber
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.core.readTextList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val PAUSE = 1
private const val VOLUME_SET = 4
private const val VOLUME_MUTE = 8
private const val PREVIOUS_TRACK = 16
private const val NEXT_TRACK = 32
private const val TURN_ON = 128
private const val TURN_OFF = 256
private const val SELECT_SOURCE = 2048
private const val PLAY = 16384
private const val VOLUME_STEP = 5

data class MediaPlayerView(
    val isAvailable: Boolean,
    /** 'playing', 'paused', 'idle', 'off', 'on', 'standby', 'buffering'. */
    val state: String,
    val isPlaying: Boolean,
    val title: String?,
    val artist: String?,
    /** 0 to 100. null when the player does not report it. */
    val volumePercent: Int?,
    val isMuted: Boolean,
    val source: String?,
    val sources: List<String>,
    /** True when it can do at least one of play and pause. */
    val canPlayPause: Boolean,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canSetVolume: Boolean,
    val canMute: Boolean,
    val canSkip: Boolean,
    val canSelectSource: Boolean,
    val canTurnOn: Boolean,
    val canTurnOff: Boolean,
)

fun readMediaPlayer(entity: EntityState): MediaPlayerView {
    val volume = readNumber(entity, "volume_level")
    val muted = entity.attributes["is_volume_muted"] as? JsonPrimitive
    return MediaPlayerView(
        isAvailable = isAvailable(entity),
        state = entity.state,
        isPlaying = entity.state == "playing",
        title = readText(entity, "media_title"),
        artist = readText(entity, "media_artist"),
        volumePercent = volume?.let { (it * 100).roundToInt() },
        isMuted = muted != null && !muted.isString && muted.booleanOrNull == true,
        source = readText(entity, "source"),
        sources = readTextList(entity, "source_list"),
        canPlayPause = hasFeature(entity, PAUSE) || hasFeature(entity, PLAY),
        canPlay = hasFeature(entity, PLAY),
        canPause = hasFeature(entity, PAUSE),
        canSetVolume = hasFeature(entity, VOLUME_SET),
        canMute = hasFeature(entity, VOLUME_MUTE),
        canSkip = hasFeature(entity, NEXT_TRACK) || hasFeature(entity, PREVIOUS_TRACK),
        canSelectSource = hasFeature(entity, SELECT_SOURCE),
        canTurnOn = hasFeature(entity, TURN_ON),
        canTurnOff = hasFeature(entity, TURN_OFF),
    )
}

/** The volume one step up (direction 1) or down (-1): the next multiple of 5, kept between 0 and 100. */
fun nextVolume(current: Int?, direction: Int): Int {
    val from = (current ?: 0).toDouble()
    val next = if (direction > 0) {
        floor(from / VOLUME_STEP) * VOLUME_STEP + VOLUME_STEP
    } else {
        ceil(from / VOLUME_STEP) * VOLUME_STEP - VOLUME_STEP
    }
    return next.toInt().coerceIn(0, 100)
}
