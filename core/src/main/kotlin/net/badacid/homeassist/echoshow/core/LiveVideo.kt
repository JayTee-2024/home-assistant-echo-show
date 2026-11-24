/*
 * Live camera video: the states a live session goes through.
 *
 *   IDLE        showing the still picture; no session
 *   CONNECTING  a session has been asked for; no picture yet
 *   PLAYING     live video is showing
 *   ENDED       a playing session was cut off (Ring ends live views after a few minutes)
 *   FAILED      the session never produced a picture
 *
 * The WebRTC work itself is in the app module, designed with the live-view plan.
 */
package net.badacid.homeassist.echoshow.core

enum class LiveState { IDLE, CONNECTING, PLAYING, ENDED, FAILED }

enum class LiveEvent {
    /** The person asked for live video. */
    START,

    /** The video arrived. */
    PICTURE,

    /** The network link to the camera dropped or failed. */
    CONNECTION_LOST,

    /** Home Assistant, or the camera behind it, said no or reported an error. */
    REFUSED,

    /** No picture arrived within LIVE_PICTURE_TIMEOUT_MS. */
    TIMED_OUT,

    /** The person stopped it, closed the view, or left the app. */
    STOP,
}

/** How long to wait for the picture before giving up. A Ring doorbell can take 10 seconds or so. */
const val LIVE_PICTURE_TIMEOUT_MS = 30_000L

fun nextLiveState(state: LiveState, event: LiveEvent): LiveState {
    if (event == LiveEvent.STOP) return LiveState.IDLE

    val isRunning = state == LiveState.CONNECTING || state == LiveState.PLAYING
    if (event == LiveEvent.START) return if (isRunning) state else LiveState.CONNECTING
    // Anything else only matters while a session is running.
    if (!isRunning) return state

    if (event == LiveEvent.PICTURE) return LiveState.PLAYING
    if (state == LiveState.CONNECTING) return LiveState.FAILED
    // Playing: the time limit no longer matters; anything else cut it off.
    return if (event == LiveEvent.TIMED_OUT) LiveState.PLAYING else LiveState.ENDED
}
