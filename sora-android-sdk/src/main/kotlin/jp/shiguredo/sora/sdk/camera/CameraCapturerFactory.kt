package jp.shiguredo.sora.sdk.camera

import android.content.Context
import android.os.Build
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer

/**
 * カメラからの映像を取得するための `CameraVideoCapturer` のファクトリクラスです。
 *
 * Camera1, Camera2 を統一的に扱うことが出来ます。
 * cf:
 * - `org.webrtc.CameraVideoCapturer`
 */
class CameraCapturerFactory {

    companion object {

        /**
         * `CameraVideoCapturer` のインスタンスを生成します。
         *
         * 複数のカメラがある場合はフロントのカメラを優先します。
         *
         * @param context application context
         * @param fixedResolution true の場合は解像度維持を優先、false の場合は
         * フレームレート維持を優先する。デフォルト値は false 。
         * @return 生成された `CameraVideoCapturer`
         */
        @JvmOverloads
        fun create(context: Context, fixedResolution: Boolean = false) : CameraVideoCapturer? {
            var videoCapturer: CameraVideoCapturer? = null
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Camera2Enumerator.isSupported(context)) {
                    videoCapturer = createCapturer(Camera2Enumerator(context))
                }
            }
            if (videoCapturer == null) {
                videoCapturer = createCapturer(Camera1Enumerator(true))
            }

            if (videoCapturer == null) {
                return null
            }
            val underlyingFixedResolution = videoCapturer.isScreencast
            when (underlyingFixedResolution) {
                fixedResolution ->
                    return videoCapturer
                else ->
                    return CameraVideoCapturerWrapper(videoCapturer, fixedResolution)
            }

        }

        private fun createCapturer(enumerator: CameraEnumerator): CameraVideoCapturer? {
            var capturer : CameraVideoCapturer? = null
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, true)
                }
            }
            if (capturer != null) {
                return capturer
            }
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, false)
                }
            }
            return capturer
        }

        private fun findDeviceCamera(enumerator: CameraEnumerator,
                                     deviceName: String, frontFacing: Boolean) : CameraVideoCapturer? {
            var capturer: CameraVideoCapturer? = null
            if (enumerator.isFrontFacing(deviceName) == frontFacing) {
                capturer = enumerator.createCapturer(deviceName, null)
            }
            return capturer
        }

    }

}

