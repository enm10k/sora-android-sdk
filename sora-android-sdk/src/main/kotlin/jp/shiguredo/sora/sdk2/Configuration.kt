package jp.shiguredo.sora.sdk2

import android.content.Context
import android.media.MediaRecorder
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams
import jp.shiguredo.sora.sdk.channel.signaling.message.SimulcastRid
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.audio.AudioDeviceModule

/**
 * サーバーへの接続設定です。
 *
 * @property context コンテキスト
 * @property url サーバーの URL
 * @property channelId チャネル ID
 * @property role ロール
 * @constructor オブジェクトを生成します。
 */
class Configuration(var context: Context,
                    var url: String,
                    var channelId: String,
                    var role: Role) {

    /**
     * 定数を提供します。
     */
    companion object {
        private val TAG = Configuration::class.simpleName!!

        /**
         * デフォルトのタイムアウト秒
         */
        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    /**
     * カメラの撮影モード
     */
    enum class CaptureMode {
        /**
         * 解像度優先
         */
        RESOLUTION,

        /**
         * フレームレート優先
         */
        FRAME_RATE
    }

    /**
     * カメラ初期化時のカメラ位置
     */
    enum class CameraPosition {

        /**
         * 前面カメラ
         */
        FRONT,

        /**
         * 背面カメラ
         */
        REAR

    }

    /**
     * 接続試行時にタイムアウトするまでの秒数。
     * デフォルトは 10 秒です。
     */
    var timeout: Long = DEFAULT_TIMEOUT_SECONDS

    /**
     * 映像の可否。
     * デフォルトは `true` です。
     */
    var videoEnabled = true

    /**
     * 映像コーデック。
     * デフォルトは `VP9` です。
     */
    var videoCodec: VideoCodec = VideoCodec.VP9

    /**
     * 映像のビットレート
     */
    var videoBitRate: Int? = null

    /**
     * 映像キャプチャー
     */
    var videoCapturer: VideoCapturer? = null

    /**
     * 映像フレームのサイズ。
     * デフォルトは [VideoFrameSize.VGA] です。
     */
    var videoFrameSize = VideoFrameSize.VGA

    /**
     * 映像のフレームレート。
     * デフォルトは 30 です。
     */
    var videoFps: Int = 30

    /**
     * カメラの撮影モード。
     * デフォルトはフレームレート優先です。
     */
    var captureMode: CaptureMode = CaptureMode.FRAME_RATE

    /**
     * カメラ初期化時に優先されるカメラ位置。
     * デフォルトは前面カメラが優先されます。
     */
    var cameraPosition: CameraPosition = CameraPosition.FRONT

    /**
     * 映像レンダラーで共有する映像描画コンテキスト
     */
    var videoRenderingContext: VideoRenderingContext? = null

    /**
     * 使用する映像エンコーダー ([org.webrtc.VideoEncoderFactory])
     *
     * @see org.webrtc.VideoEncoderFactory
     */
    var videoEncoderFactory: VideoEncoderFactory? = null

    /**
     * 使用する映像デコーダー ([org.webrtc.VideoDecoderFactory])
     *
     * @see org.webrtc.VideoDecoderFactory
     */
    var videoDecoderFactory: VideoDecoderFactory? = null

    /**
     * 音声の可否。
     * デフォルトは `true` です。
     */
    var audioEnabled = true

    /**
     * 音声コーデック。
     * デフォルトは `OPUS` です。
     */
    var audioCodec: AudioCodec = AudioCodec.OPUS

    /**
     * 音声ビットレート
     */
    var audioBitRate: Int? = null

    /**
     * マルチストリームの可否
     */
    var multistreamEnabled = false

    /**
     * サイマルキャストの可否
     */
    var simulcastEnabled = false

    /**
     * サイマルキャスト有効時の画質
     */
    var simulcastRid: SimulcastRid? = null

    /**
     * スポットライト機能の可否
     */
    var spotlightEnabled = false

    /**
     * 最大同時アクティブ配信者数。
     * スポットライト機能の有効時のみ制限されます。
     */
    var spotlightNumber: Int = 3

    /**
     * シグナリング  `connect` に含めるメタデータ。
     * この値は JSON に変換されて `metadata` メンバーにセットされます。
     */
    var signalingMetadata: Any? = null

    /**
     * シグナリング `connect` に含めるメタデータ。
     * この値は JSON に変換されて `signaling_notify_metadata` メンバーにセットされます。
     */
    var signalingNotifyMetadata: Any? = null

    /**
     * 端末の音響エコーキャンセラーの使用の可否
     */
    var usesHardwareAcousticEchoCanceler: Boolean = true

    /**
     * 端末のノイズサプレッサーの使用の可否
     */
    var usesHardwareNoiseSuppressor: Boolean = true

    /**
     * 使用する音声デバイスモジュール ([org.webrtc.audio.AudioDeviceModule])。
     * このプロパティを設定した場合、以下の設定は無視されます。
     *
     * - [usesHardwareAcousticEchoCanceler]
     * - [usesHardwareNoiseSuppressor]
     *
     * @see org.webrtc.audio.AudioDeviceModule
     */
    var audioDeviceModule: AudioDeviceModule? = null

    /**
     * 入力音声のエコーキャンセル処理の可否
     */
    var audioProcessingEchoCancellationEnabled: Boolean = true

    /**
     * 入力音声の自動ゲイン調整処理の可否
     */
    var audioProcessingAutoGainControlEnabled: Boolean = true

    /**
     * 入力音声のハイパスフィルタ処理の可否
     */
    var audioProcessingHighpassFilterEnabled: Boolean = true

    /**
     * 入力音声のノイズ抑制処理の可否
     */
    var audioProcessingNoiseSuppressionEnabled: Boolean = true

    /**
     * 音声の制約。
     * このプロパティを設定した場合、以下の設定は無視されます。
     *
     * - [audioProcessingEchoCancellationEnabled]
     * - [audioProcessingAutoGainControlEnabled]
     * - [audioProcessingHighpassFilterEnabled]
     * - [audioProcessingNoiseSuppressionEnabled]
     */
    var audioMediaConstraints: MediaConstraints? = null

    /**
     * メディアレコーダーが使用する音声ソース ([android.media.MediaRecorder.AudioSource]) 。
     * デフォルト値は [android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION] です。
     *
     * @see android.media.MediaRecorder.AudioSource
     */
    var mediaRecorderAudioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION

    /**
     * 入力音声のモノラルまたはステレオの設定。
     * デフォルト値はモノラルです。
     */
    var inputAudioSound: AudioSound = AudioSound.MONO

    /**
     * 出力音声のモノラルまたはステレオの設定。
     * デフォルト値はモノラルです。
     */
    var outputAudioSound: AudioSound = AudioSound.MONO

    /**
     * OPUS の設定
     */
    var opusParams: OpusParams? = null

    private var isInitialized: Boolean = false

    private fun initialize() {
        if (isInitialized)
            return

        SoraLogger.d(TAG, "initialize configuration")

        if (role.isSender && videoEnabled) {
            SoraLogger.d(TAG, "create video capturer")
            videoCapturer = CameraCapturerFactory.create(context,
                    captureMode == CaptureMode.RESOLUTION,
                    cameraPosition == CameraPosition.FRONT)
        }

        if (videoRenderingContext == null) {
            videoRenderingContext = VideoRenderingContext()
        }

        isInitialized = true
    }

    internal fun toSoraMediaOption(): SoraMediaOption {
        if (!isInitialized)
            initialize()

        return SoraMediaOption().also {
            it.multistreamEnabled = multistreamEnabled

            if (spotlightEnabled) {
                // FIX
                //it.enableSpotlight(spotlightNumber, simulcastRid)
            }

            if (videoEnabled) {
                it.videoBitrate = videoBitRate

                it.videoCodec = when (videoCodec) {
                    VideoCodec.VP8 -> SoraVideoOption.Codec.VP8
                    VideoCodec.VP9 -> SoraVideoOption.Codec.VP9
                    VideoCodec.H264 -> SoraVideoOption.Codec.H264
                    else -> SoraVideoOption.Codec.VP9
                }

                it.videoEncoderFactory = videoEncoderFactory
                it.videoDecoderFactory = videoDecoderFactory

                SoraLogger.d(TAG, "role $role, ${role.isSender} ${role.isReceiver}")
                if (role.isSender) {
                    SoraLogger.d(TAG, "enable video upstream")
                    it.enableVideoUpstream(videoCapturer!!, VideoRenderingContext.rootEglBase.eglBaseContext)
                }

                if (role.isReceiver) {
                    it.enableVideoDownstream(VideoRenderingContext.rootEglBase.eglBaseContext)
                }
            }

            if (audioEnabled) {
                it.audioUpstreamEnabled = role.isSender
                it.audioDownstreamEnabled = role.isReceiver

                it.audioBitrate = audioBitRate

                it.audioCodec = when (audioCodec) {
                    AudioCodec.OPUS -> SoraAudioOption.Codec.OPUS
                }

                it.audioOption.audioDeviceModule = audioDeviceModule
                it.audioOption.audioSource = mediaRecorderAudioSource
                it.audioOption.useStereoInput = inputAudioSound == AudioSound.STEREO
                it.audioOption.useStereoOutput = outputAudioSound == AudioSound.STEREO
                it.audioOption.useHardwareAcousticEchoCanceler = usesHardwareAcousticEchoCanceler
                it.audioOption.useHardwareNoiseSuppressor = usesHardwareNoiseSuppressor
                it.audioOption.audioProcessingAutoGainControl = audioProcessingAutoGainControlEnabled
                it.audioOption.audioProcessingEchoCancellation = audioProcessingEchoCancellationEnabled
                it.audioOption.audioProcessingHighpassFilter = audioProcessingHighpassFilterEnabled
                it.audioOption.audioProcessingNoiseSuppression = audioProcessingNoiseSuppressionEnabled
                it.audioOption.mediaConstraints = audioMediaConstraints?.toNative()
                it.audioOption.opusParams = opusParams
            }

            if (simulcastEnabled) {
                it.enableSimulcast(simulcastRid)
            }
        }
    }

    internal fun printDebug(tag: String, message: String) {
        SoraLogger.d(tag, """$message: Configuration:
            |url                     = $url
            |channelId               = $channelId
            |role                    = ${role.name}
            |multistreamEnabled      = $multistreamEnabled
            |simulcastEnabled        = $simulcastEnabled
            |spotlightEnabled        = $spotlightEnabled
            |spotlightNumber         = $spotlightNumber
            |videoEnabled            = $videoEnabled
            |videoCodec              = $videoCodec
            |videoBitRate            = $videoBitRate
            |videoCapturer           = $videoCapturer
            |videoFrameSize          = $videoFrameSize
            |videoFps                = $videoFps
            |videoEncoderFactory     = $videoEncoderFactory
            |videoDecoderFactory     = $videoDecoderFactory
            |audioCodec              = $audioCodec
            |audioBitRate            = $audioBitRate
            |signalingMetadata       = $signalingMetadata
            |signalingNotifyMetadata = ${this.signalingNotifyMetadata}""".trimMargin())
    }

}