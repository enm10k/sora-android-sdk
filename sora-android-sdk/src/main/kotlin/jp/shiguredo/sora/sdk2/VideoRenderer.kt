package jp.shiguredo.sora.sdk2

import org.webrtc.RendererCommon
import org.webrtc.VideoFrame
import org.webrtc.VideoTrack

/**
 * 映像を描画するためのインターフェースです。
 */
interface VideoRenderer {

    enum class State {
        NOT_INITIALIZED,
        RUNNING,
        RELEASED
    }

    var state: State

    /**
     * 映像が反転していれば `true`
     */
    var isMirrored: Boolean

    /**
     * ハードウェア映像スケーラーが有効であれば `true`
     */
    var hardwareScalerEnabled: Boolean

    /**
     * フレームレート制限時のフレームレート。
     * [fpsReductionEnabled] が `true` の場合、最大フレームレートがこの値に抑えられます。
     */
    var fpsReduction: Float

    /**
     * フレームレート制限の可否。
     * `true` をセットすると、最大フレームレートを [fpsReduction] に抑えます。
     */
    var fpsReductionEnabled: Boolean

    /**
     * 映像レンダラーを映像トラックに割り当てます。
     *
     * @param track 映像トラック
     *
     * @see detachFromVideoTrack
     */
    fun attachToVideoTrack(track: VideoTrack)

    /**
     * 映像トラックに割り当てた映像レンダラーを取り外します。
     *
     * @param track 映像トラック
     *
     * @see attachToVideoTrack
     */
    fun detachFromVideoTrack(track: VideoTrack)

    /**
     * 初期化すべきであれば `true` を返します。
     */
    val shouldInitialize: Boolean

    val canInitialize: Boolean
        get() = shouldInitialize && state != State.RUNNING

    /**
     * 終了処理をすべきであれば `true` を返します。
     */
    val shouldRelease: Boolean

    val canRelease: Boolean
        get() = shouldRelease && state == State.RUNNING

    /**
     * 映像を描画するための初期化処理を行います。
     *
     * @param videoRenderingContext 描画に使用するコンテキスト
     */
    fun initialize(videoRenderingContext: VideoRenderingContext)

    /**
     * 映像レンダラーの終了処理を行います。
     * 映像レンダラーの使用後に呼ぶ必要があります。
     *
     * @see initialize
     */
    fun release()

    fun setScalingType(scalingTypeMatchOrientation: RendererCommon.ScalingType?,
                       scalingTypeMismatchOrientation: RendererCommon.ScalingType?)

    /**
     * 描画が一時停止されていれば再開します。
     *
     * @see pause
     */
    fun resume()

    /**
     * 描画を一時停止します。
     *
     * @see resume
     */
    fun pause()

    /**
     * 画面をクリアします。
     */
    fun clear()

    /**
     * 映像を描画します。
     *
     * @param frame 描画する映像フレーム
     *
     * @see onFirstFrameRendered
     */
    fun onFrame(frame: VideoFrame?)

    /**
     * 最初の映像フレームが描画されるときに呼ばれます。
     *
     * @see onFrame
     */
    fun onFirstFrameRendered()

    /**
     * 解像度が変更されたときに呼ばれます。
     *
     * @param videoWidth 解像度の幅
     * @param videoHeight 解像度の高さ
     * @param rotation 回転の角度
     */
    fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int)

}