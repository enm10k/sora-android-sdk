package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.Encoding
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*
import java.util.concurrent.Executors

interface PeerChannel {

    var conn:    PeerConnection?
    var factory: PeerConnectionFactory?
    val senders: List<RtpSender>
    val receivers: List<RtpReceiver>
    val transceivers: List<RtpTransceiver>

    fun handleInitialRemoteOffer(offer: String, encodings: List<Encoding>?): Single<SessionDescription>
    fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription>

    // 失敗しても問題のない処理が含まれる (client offer SDP は生成に失敗しても問題ない) ので、
    // その場合に Result 型でエラーを含めて返す
    fun requestClientOfferSdp(): Single<Result<SessionDescription>>

    fun disconnect()

    fun getStats(statsCollectorCallback: RTCStatsCollectorCallback)
    fun getStats(handler: (RTCStatsReport?) -> Unit)

    interface Listener {
        fun onRemoveRemoteStream(label: String)
        fun onAddRemoteStream(ms: MediaStream)
        fun onAddLocalStream(ms: MediaStream, videoSource: VideoSource?)
        fun onAddSender(sender: RtpSender, ms: Array<out MediaStream>)
        fun onAddReceiver(receiver: RtpReceiver, ms: Array<out MediaStream>)
        fun onRemoveReceiver(id: String)
        fun onLocalIceCandidateFound(candidate: IceCandidate)
        fun onConnect()
        fun onDisconnect()
        fun onSenderEncodings(encodings: List<RtpParameters.Encoding>)
        fun onError(reason: SoraErrorReason)
        fun onError(reason: SoraErrorReason, message: String)
        fun onWarning(reason: SoraErrorReason)
        fun onWarning(reason: SoraErrorReason, message: String)
    }
}

class PeerChannelImpl(
        private val appContext:    Context,
        private val networkConfig: PeerNetworkConfig,
        private val mediaOption:   SoraMediaOption,
        private var listener:      PeerChannel.Listener?,
        private var useTracer:     Boolean = false
): PeerChannel {

    companion object {
        private val TAG = PeerChannelImpl::class.simpleName

        private var isInitialized = false
        fun initializeIfNeeded(context: Context, useTracer: Boolean) {
            if (!isInitialized) {
                val options = PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .setEnableInternalTracer(useTracer)
                        .setFieldTrials("")
                        .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                if(SoraLogger.libjingle_enabled) {
                    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
                }
                isInitialized = true
            }
        }
    }

    private val componentFactory = RTCComponentFactory(mediaOption, listener)

    override var conn:    PeerConnection?        = null
    override var factory: PeerConnectionFactory? = null

    private var _senders: MutableList<RtpSender> = mutableListOf()
    override val senders: List<RtpSender>
        get() = _senders

    private var _receivers: MutableList<RtpReceiver> = mutableListOf()
    override val receivers: List<RtpReceiver>
        get() = _receivers

    private var _transceivers: MutableList<RtpTransceiver> = mutableListOf()
    override val transceivers: List<RtpTransceiver>
        get() = _transceivers

    private val executor =  Executors.newSingleThreadExecutor()

    private val sdpConstraints    = componentFactory.createSDPConstraints()
    private val localAudioManager = componentFactory.createAudioManager()
    private val localVideoManager = componentFactory.createVideoManager()

    private var localStream: MediaStream? = null

    private var videoSender: RtpSender? = null
    private var audioSender: RtpSender? = null

    private var closing = false

    // offer 時に受け取った encodings を保持しておく
    // sender に encodings をセット後、
    // setRemoteDescription() を実行すると encodings が変更前に戻ってしまう
    // そのため re-offer, update 時に再度 encodings をセットする
    private var offerEncodings: List<Encoding>? = null

    private val connectionObserver = object : PeerConnection.Observer {

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            SoraLogger.d(TAG, "[rtc] @onSignalingChange: ${state.toString()}")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            SoraLogger.d(TAG, "[rtc] @onIceCandidate")
            // In this project, server-side always plays a role of WebRTC-initiator,
            // and signaling-channel is must be live while this session.
            // So, we don't need to consider buffering candidates or something like that,
            // pass the candidate to signaling-channel directly.
            candidate?.let { listener?.onLocalIceCandidateFound(candidate) }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            SoraLogger.d(TAG, "[rtc] @onIceCandidatesRemoved")
        }

        override fun onAddStream(ms: MediaStream?) {
            SoraLogger.d(TAG, "[rtc] @onAddStream msid=${ms?.id}")
            ms?.let { listener?.onAddRemoteStream(ms) }
        }

        override fun onAddTrack(receiver: RtpReceiver?, ms: Array<out MediaStream>?) {
            SoraLogger.d(TAG, "[rtc] @onAddTrack")
            if (receiver != null) {
                _receivers.add(receiver!!)
                receiver?.let { listener?.onAddReceiver(receiver!!, ms!!) }
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            SoraLogger.d(TAG, "[rtc] @onRemoveTrack")
            if (receiver != null) {
                _receivers.remove(receiver!!)
                receiver?.let { listener?.onRemoveReceiver(receiver.id()) }
            }

        }

        override  fun onTrack(transceiver: RtpTransceiver) {
            SoraLogger.d(TAG, "[rtc] @onTrack direction=${transceiver.direction}")
            SoraLogger.d(TAG, "[rtc] @onTrack currentDirection=${transceiver.currentDirection}")
            SoraLogger.d(TAG, "[rtc] @onTrack sender.track=${transceiver.sender.track()}")
            SoraLogger.d(TAG, "[rtc] @onTrack receiver.track=${transceiver.receiver.track()}")
            // TODO(shino): Unified plan に onRemoveTrack が来たらこっちで対応する。
            // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
        }

        override fun onDataChannel(channel: DataChannel?) {
            SoraLogger.d(TAG, "[rtc] @onDataChannel")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            SoraLogger.d(TAG, "[rtc] @onIceConnectionChange:$state")
            state?.let {
               SoraLogger.d(TAG, "[rtc] @ice:${it.name}")
               when (it) {
                   PeerConnection.IceConnectionState.CONNECTED -> {
                       listener?.onConnect()
                   }
                   PeerConnection.IceConnectionState.FAILED -> {
                       listener?.onError(SoraErrorReason.ICE_FAILURE)
                       disconnect()
                   }
                   PeerConnection.IceConnectionState.DISCONNECTED -> {
                       if (closing) return

                       // disconnected はなにもしない、ネットワークが不安定な場合は
                       // failed に遷移して上の節で捕まえられるため、listener 通知のみ行う。
                       listener?.onWarning(SoraErrorReason.ICE_DISCONNECTED)
                   }
                   PeerConnection.IceConnectionState.CLOSED -> {
                       if (!closing) {
                            listener?.onError(SoraErrorReason.ICE_CLOSED_BY_SERVER)
                       }
                       disconnect()
                   }
                   else -> {}
               }
            }
        }

        override fun onIceConnectionReceivingChange(received: Boolean) {
            SoraLogger.d(TAG, "[rtc] @onIceConnectionReceivingChange:$received")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            SoraLogger.d(TAG, "[rtc] @onIceGatheringChange:$state")
        }

        override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            super.onStandardizedIceConnectionChange(newState)
            SoraLogger.d(TAG, "[rtc] @onStandardizedIceConnectionChange:$newState")
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            super.onConnectionChange(newState)
            SoraLogger.d(TAG, "[rtc] @onConnectionChange:$newState")
        }

        override fun onRemoveStream(ms: MediaStream?) {
            SoraLogger.d(TAG, "[rtc] @onRemoveStream")
            // When this thread's loop finished, this media-stream object is dead on JNI(C++) side.
            // but in most case, this callback is handled in UI-thread's next loop.
            // So, we need to pick up the label-string beforehand.
            ms?.let { listener?.onRemoveRemoteStream(it.id) }
        }

        override fun onRenegotiationNeeded() {
            SoraLogger.d(TAG, "[rtc] @onRenegotiationNeeded")
        }
    }

    private fun setup(): Single<Boolean> = Single.create(SingleOnSubscribe<Boolean> {
        try {
            setupInternal()
            it.onSuccess(true)
        } catch (e: Exception) {
            SoraLogger.w(TAG, e.toString())
            it.onError(e)
        }
    }).subscribeOn(Schedulers.from(executor))

    override fun handleUpdatedRemoteOffer(offer: String): Single<SessionDescription> {

        val offerSDP =
                SessionDescription(SessionDescription.Type.OFFER, offer)

        return setRemoteDescription(offerSDP).flatMap {
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            return@flatMap setLocalDescription(answer)
        }
    }

    override fun handleInitialRemoteOffer(offer: String, encodings: List<Encoding>?): Single<SessionDescription> {

        val offerSDP = SessionDescription(SessionDescription.Type.OFFER, offer)
        offerEncodings = encodings

        return setup().flatMap {
            SoraLogger.d(TAG, "setRemoteDescription")
            return@flatMap setRemoteDescription(offerSDP)
        }.flatMap {
            // libwebrtc のバグにより simulcast の場合 setRD -> addTrack の順序を取る必要がある。
            // simulcast can not reuse transceiver when setRemoteDescription is called after addTrack
            // https://bugs.chromium.org/p/chromium/issues/detail?id=944821
            val mediaStreamLabels = listOf(localStream!!.id)

            audioSender = localStream!!.audioTracks.firstOrNull()?.let {
                conn!!.addTrack(it, mediaStreamLabels)
            }
            videoSender = localStream!!.videoTracks.firstOrNull()?.let {
                conn!!.addTrack(it, mediaStreamLabels)
            }

            audioSender?.let { _senders.add(it) }
            videoSender?.let { _senders.add(it) }

            if (mediaOption.simulcastEnabled && mediaOption.videoUpstreamEnabled && encodings != null) {
                SoraLogger.d(TAG, "Modify sender.parameters")
                updateVideoSenderOfferEncodings()
            }
            SoraLogger.d(TAG, "createAnswer")
            return@flatMap createAnswer()
        }.flatMap {
            answer ->
            SoraLogger.d(TAG, "setLocalDescription")
            return@flatMap setLocalDescription(answer)
        }
    }

    private fun updateVideoSenderOfferEncodings() {
        videoSender?.let { updateSenderOfferEncodings(it) }
    }

    private fun updateSenderOfferEncodings(sender: RtpSender) {
        if (offerEncodings == null)
            return

        // RtpSender#getParameters はフィールド参照ではなく native から Java インスタンスに
        // 変換するのでここで参照を保持しておく。
        val parameters = sender.parameters
        parameters.encodings.zip(offerEncodings!!).forEach { (senderEncoding, offerEncoding) ->
            offerEncoding.active?.also { senderEncoding.active = it }
            offerEncoding.maxBitrate?.also { senderEncoding.maxBitrateBps = it }
            offerEncoding.maxFramerate?.also { senderEncoding.maxFramerate = it }
            offerEncoding.scaleResolutionDownBy?.also { senderEncoding.scaleResolutionDownBy = it }
        }

        // アプリケーションに一旦渡す, encodings は final なので参照渡しで変更してもらう
        listener?.onSenderEncodings(parameters.encodings)
        parameters.encodings.forEach {
            with(it) {
                SoraLogger.d(TAG, "update sender encoding: " +
                        "id=${sender.id()}, " +
                        "rid=$rid, " +
                        "active=$active, " +
                        "scaleResolutionDownBy=$scaleResolutionDownBy, " +
                        "maxFramerate=$maxFramerate, " +
                        "maxBitrateBps=$maxBitrateBps, " +
                        "ssrc=$ssrc")
            }
        }

        // Java オブジェクト参照先を変更し終えたので RtpSender#setParameters() から JNI 経由で C++ 層に渡す
        sender.parameters = parameters
    }

    override fun requestClientOfferSdp(): Single<Result<SessionDescription>> {
        return setup().flatMap {
            SoraLogger.d(TAG, "requestClientOfferSdp")
            return@flatMap createClientOfferSdp()
        }
    }

    private fun createClientOfferSdp() : Single<Result<SessionDescription>> =
        Single.create(SingleOnSubscribe<Result<SessionDescription>> {

            val directionRecvOnly = RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)

            val audioTransceiver = conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, directionRecvOnly)
            if (audioTransceiver != null) {
                _transceivers.add(audioTransceiver!!)
            }
            val videoTransceiver = conn?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, directionRecvOnly)
            if (videoTransceiver != null) {
                _transceivers.add(videoTransceiver!!)
            }

            conn?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    SoraLogger.d(TAG, "createOffer:onCreateSuccess: ${sdp?.type}")
                    it.onSuccess(Result.success(sdp!!))
                }
                override fun onCreateFailure(error: String) {
                    SoraLogger.d(TAG, "createOffer:onCreateFailure: $error")
                    // Offer SDP は生成に失敗しても問題ないので、エラーメッセージを onSuccess で渡す
                    it.onSuccess(Result.failure(Error(error)))
                }
                override fun onSetSuccess() {
                    it.onError(Error("must not come here"))
                }
                override fun onSetFailure(s: String?) {
                    it.onError(Error("must not come here"))
                }
            }, sdpConstraints)
        }).subscribeOn(Schedulers.from(executor))

    private fun setupInternal() {
        SoraLogger.d(TAG, "setupInternal")

        initializeIfNeeded(appContext, useTracer)
        factory = componentFactory.createPeerConnectionFactory(appContext)

        SoraLogger.d(TAG, "createPeerConnection")
        conn = factory!!.createPeerConnection(
                networkConfig.createConfiguration(),
                connectionObserver)

        SoraLogger.d(TAG, "local managers' initTrack: audio")
        localAudioManager.initTrack(factory!!, mediaOption.audioOption)

        SoraLogger.d(TAG, "local managers' initTrack: video => ${mediaOption.videoUpstreamContext}")
        localVideoManager.initTrack(factory!!, mediaOption.videoUpstreamContext, appContext)

        SoraLogger.d(TAG, "setup local media stream")
        val streamId = UUID.randomUUID().toString()
        localStream = factory!!.createLocalMediaStream(streamId)

        localAudioManager.attachTrackToStream(localStream!!)
        //audioSender = localAudioManager.attachTrackToPeerConnection(conn!!, localStream!!)

        // TODO: add track to peer connection のみだと local stream に追加されない
        localVideoManager.attachTrackToStream(localStream!!)
        //videoSender = localVideoManager.attachTrackToPeerConnection(conn!!, localStream!!)
        SoraLogger.d(TAG, "attached video sender => $videoSender")

        /*
        if (conn != null && localStream!!.videoTracks != null) {
            SoraLogger.d(TAG, "attach video tracks to peer connection")
            attachTracksToPeerConnection(localStream!!.videoTracks!!, localStream!!, conn!!)
        }
        if (conn != null && localStream!!.audioTracks != null) {
            SoraLogger.d(TAG, "attach audio tracks to peer connection")
            attachTracksToPeerConnection(localStream!!.audioTracks!!, localStream!!, conn!!)
        }
         */

        SoraLogger.d(TAG, "localStream.audioTracks.size = ${localStream!!.audioTracks.size}")
        SoraLogger.d(TAG, "localStream.videoTracks.size = ${localStream!!.videoTracks.size}")
        listener?.onAddLocalStream(localStream!!, localVideoManager.source)

        /*
        if (audioSender != null)
            listener?.onAddSender(audioSender!!, arrayOf(localStream!!))
        if (videoSender != null)
            listener?.onAddSender(videoSender!!, arrayOf(localStream!!))
         */
    }

    private fun attachTracksToPeerConnection(tracks: List<MediaStreamTrack>,
                                             stream: MediaStream,
                                             conn: PeerConnection) {
        for (track in tracks) {
            val sender = conn.addTrack(track, listOf(stream.id))
            if (sender != null) {
                _senders.add(sender!!)
            }
        }
    }

    override fun disconnect() {
        if (closing)
            return
        executor.execute {
            closeInternal()
        }
    }

    private fun createAnswer(): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    SoraLogger.d(TAG, """createAnswer:onCreateSuccess: ${sdp!!.type}
                        |${sdp.description}""".trimMargin())
                    it.onSuccess(sdp)
                }
                override fun onCreateFailure(s: String?) {
                    SoraLogger.w(TAG, "createAnswer:onCreateFailure: reason=${s}")
                    it.onError(Error(s))
                }
                override fun onSetSuccess() {
                    it.onError(Error("must not come here"))
                }
                override fun onSetFailure(s: String?) {
                    it.onError(Error("must not come here"))
                }
            }, sdpConstraints)
        }).subscribeOn(Schedulers.from(executor))

    private fun setLocalDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    it.onError(Error("must not come here"))
                }
                override fun onCreateFailure(p0: String?) {
                    it.onError(Error("must not come here"))
                }
                override fun onSetSuccess() {
                    SoraLogger.d(TAG, "setLocalDescription.onSetSuccess ${this@PeerChannelImpl}")
                    it.onSuccess(sdp)
                }
                override fun onSetFailure(s: String?) {
                    SoraLogger.d(TAG, "setLocalDescription.onSetFailure reason=${s} ${this@PeerChannelImpl}")
                    it.onError(Error(s))
                }
            }, sdp)
        }).subscribeOn(Schedulers.from(executor))

    private fun setRemoteDescription(sdp: SessionDescription): Single<SessionDescription> =
        Single.create(SingleOnSubscribe<SessionDescription> {
            conn?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    it.onError(Error("must not come here"))
                }
                override fun onCreateFailure(p0: String?) {
                    it.onError(Error("must not come here"))
                }
                override fun onSetSuccess() {
                    SoraLogger.d(TAG, "setRemoteDescription.onSetSuccess ${this@PeerChannelImpl}")
                    it.onSuccess(sdp)
                }
                override fun onSetFailure(s: String?) {
                    SoraLogger.w(TAG, "setRemoteDescription.onSetFailures reason=${s} ${this@PeerChannelImpl}")
                    it.onError(Error(s))
                }
            }, sdp)
        }).subscribeOn(Schedulers.from(executor))

    private fun closeInternal() {
        if (closing)
            return
        SoraLogger.d(TAG, "disconnect")
        closing = true
        listener?.onDisconnect()
        listener = null
        SoraLogger.d(TAG, "dispose peer connection")
        conn?.dispose()
        conn = null
        localAudioManager.dispose()
        localVideoManager.dispose()
        SoraLogger.d(TAG, "dispose peer connection factory")
        factory?.dispose()
        factory = null

        if (useTracer) {
            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()
        }
    }

    override fun getStats(statsCollectorCallback: RTCStatsCollectorCallback) {
        conn?.getStats(statsCollectorCallback)
    }

    override fun getStats(handler: (RTCStatsReport?) -> Unit) {
        if (conn != null) {
            conn!!.getStats(handler)
        } else {
            handler(null)
        }
    }

}
