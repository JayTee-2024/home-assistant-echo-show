package net.badacid.homeassist.echoshow.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.LIVE_PICTURE_TIMEOUT_MS
import net.badacid.homeassist.echoshow.core.LiveEvent
import net.badacid.homeassist.echoshow.core.LiveState
import net.badacid.homeassist.echoshow.core.WebRtcMessage
import net.badacid.homeassist.echoshow.core.nextLiveState
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.io.Closeable

private const val TAG = "LiveVideo"

/** How long to wait for ICE gathering before sending the offer anyway (as the LG app). */
private const val GATHERING_LIMIT_MS = 3_000L

/** One WebRTC set-up for the whole process: the GL context and the factory are costly to make. */
private object WebRtcEngine {
    private var made: Pair<EglBase, PeerConnectionFactory>? = null

    fun get(context: Context): Pair<EglBase, PeerConnectionFactory> = made ?: run {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        val egl = EglBase.create()
        val factory = PeerConnectionFactory.builder()
            // Hardware decoders first: the Echo's H.264 decoder plays a Ring's 720×720 at 25 fps.
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            // Receive-only, so no encoder is ever used.
            .setVideoEncoderFactory(HardwareVideoEncoderFactory(egl.eglBaseContext, false, false))
            .createPeerConnectionFactory()
        (egl to factory).also { made = it }
    }
}

/**
 * Live video from a Home Assistant camera by WebRTC (camera/webrtc/offer), as the live video test on the
 * Echo proved (spike/RESULTS.md): receive-only audio and video, H.264 only, the offer sent once ICE
 * gathering ends. Everything here runs on the main thread. Session descriptions are never logged.
 */
class LiveVideoSession(
    context: Context,
    private val connection: HomeConnection,
    private val entityId: String,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val stateFlow = MutableStateFlow(LiveState.IDLE)
    val state: StateFlow<LiveState> = stateFlow.asStateFlow()

    private val engine = WebRtcEngine.get(context)
    private val renderer = SurfaceViewRenderer(context).apply {
        init(engine.first.eglBaseContext, object : RendererCommon.RendererEvents {
            override fun onFirstFrameRendered() {
                main.post { move(LiveEvent.PICTURE) }
            }
            override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                Log.i(TAG, "picture ${width}x$height")
            }
        })
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }
    val videoView: View get() = renderer

    private var peer: PeerConnection? = null
    private var offer: Closeable? = null
    private var audio: AudioTrack? = null
    private var video: VideoTrack? = null
    private var isMuted = true
    private var answered = false
    private val waitingCandidates = mutableListOf<IceCandidate>()
    private var offerSent = false
    private var setUp: Job? = null
    private var released = false

    private val timeout = Runnable { move(LiveEvent.TIMED_OUT) }

    fun start() {
        // One session per object: a second start would make a second peer.
        if (setUp != null || released) return
        move(LiveEvent.START)
        main.postDelayed(timeout, LIVE_PICTURE_TIMEOUT_MS)
        setUp = scope.launch {
            val servers = connection.webRtcIceServers(entityId).map {
                PeerConnection.IceServer.builder(it.urls).setUsername(it.username ?: "").setPassword(it.credential ?: "").createIceServer()
            }
            if (released) return@launch
            createPeer(servers)
        }
    }

    private fun createPeer(servers: List<PeerConnection.IceServer>) {
        val config = PeerConnection.RTCConfiguration(servers).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val created = engine.second.createPeerConnection(config, observer) ?: return move(LiveEvent.REFUSED)
        peer = created
        val recvOnly = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        created.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, recvOnly)
        val video = created.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, recvOnly)
        // H.264 only: offered anything, a Ring sends 1536×1536 H.265, which the Echo cannot decode smoothly.
        val h264 = engine.second.getRtpReceiverCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
            .filter { it.mimeType.equals("video/H264", ignoreCase = true) }
        if (h264.isNotEmpty()) video.setCodecPreferences(h264)
        created.createOffer(sdp { description ->
            // Posted: the session may have been released (and the peer freed) in the meantime.
            if (released) return@sdp
            created.setLocalDescription(sdp { }, description)
            main.postDelayed({ sendOffer() }, GATHERING_LIMIT_MS)
        }, MediaConstraints())
    }

    private fun sendOffer() {
        if (offerSent || released) return
        val sdp = peer?.localDescription?.description ?: return
        offerSent = true
        offer = connection.webRtcOffer(entityId, sdp) { message -> main.post { onMessage(message) } }
        if (offer == null) move(LiveEvent.REFUSED)
    }

    private fun onMessage(message: WebRtcMessage) {
        if (released) return
        when (message) {
            is WebRtcMessage.Session -> Unit
            is WebRtcMessage.Answer -> peer?.setRemoteDescription(sdp {
                answered = true
                waitingCandidates.forEach { peer?.addIceCandidate(it) }
                waitingCandidates.clear()
            }, SessionDescription(SessionDescription.Type.ANSWER, message.sdp))
            is WebRtcMessage.Candidate -> {
                val candidate = IceCandidate(message.sdpMid ?: "0", message.sdpMLineIndex ?: 0, message.candidate)
                if (answered) peer?.addIceCandidate(candidate) else waitingCandidates += candidate
            }
            is WebRtcMessage.Error -> {
                Log.i(TAG, "Home Assistant refused live video: ${message.message}")
                move(LiveEvent.REFUSED)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        audio?.setEnabled(!muted)
    }

    fun stop() = move(LiveEvent.STOP)

    private fun move(event: LiveEvent) {
        val next = nextLiveState(stateFlow.value, event)
        if (next == stateFlow.value) return
        Log.i(TAG, "$entityId ${stateFlow.value} → $next ($event)")
        stateFlow.value = next
        if (next == LiveState.PLAYING) main.removeCallbacks(timeout)
        if (next == LiveState.IDLE || next == LiveState.ENDED || next == LiveState.FAILED) release()
    }

    private fun release() {
        if (released) return
        released = true
        main.removeCallbacks(timeout)
        setUp?.cancel()
        try {
            offer?.close()
        } catch (_: Exception) {
        }
        offer = null
        video?.removeSink(renderer)
        video = null
        audio = null
        // close() only ends the call; dispose() frees the native peer and its hold on this object (and so on
        // the connection and the activity). Without it every session stays in memory.
        peer?.let {
            it.close()
            it.dispose()
        }
        peer = null
        // The renderer must let go of its decoder surface, or the next session's picture can stay black.
        renderer.release()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) main.post { sendOffer() }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "$entityId ICE $state")
            if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.CLOSED) {
                main.post { move(LiveEvent.CONNECTION_LOST) }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> main.post {
                    if (released) return@post
                    video = track
                    track.addSink(renderer)
                }
                is AudioTrack -> main.post {
                    if (released) return@post
                    audio = track
                    track.setEnabled(!isMuted)
                }
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    }

    private fun sdp(onSuccess: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {
            main.post { onSuccess(description) }
        }

        override fun onSetSuccess() {
            main.post { onSuccess(SessionDescription(SessionDescription.Type.OFFER, "")) }
        }

        override fun onCreateFailure(error: String) {
            Log.i(TAG, "could not make the offer: $error")
            main.post { move(LiveEvent.REFUSED) }
        }

        override fun onSetFailure(error: String) {
            Log.i(TAG, "could not use a session description: $error")
            main.post { move(LiveEvent.REFUSED) }
        }
    }
}
