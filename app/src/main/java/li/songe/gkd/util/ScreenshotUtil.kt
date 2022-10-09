package li.songe.gkd.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.blankj.utilcode.util.ScreenUtils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// https://github.com/npes87184/ScreenShareTile/blob/master/app/src/main/java/com/npes87184/screenshottile/ScreenshotService.kt

@SuppressLint("WrongConstant")
class ScreenshotUtil(private val context: Context, private val screenshotIntent: Intent) {

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaProjection: MediaProjection? = null


    private val mediaProjectionManager by lazy {
        context.getSystemService(
            Activity.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
    }

    fun destroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    suspend fun execute() = suspendCoroutine<Bitmap> { block ->
        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 1
        )
        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(
                RESULT_OK,
                screenshotIntent
            )
        }
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "screenshot",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            imageReader!!.surface,
            null,
            handler
        )
        imageReader!!.setOnImageAvailableListener({ reader ->
            imageReader?.setOnImageAvailableListener(null, null)
            var image: Image? = null
            var bitmapWithStride: Bitmap? = null
            val bitmap: Bitmap?
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    bitmapWithStride = Bitmap.createBitmap(
                        rowStride / pixelStride,
                        height, Bitmap.Config.ARGB_8888
                    )
                    bitmapWithStride.copyPixelsFromBuffer(buffer)
                    bitmap = Bitmap.createBitmap(bitmapWithStride, 0, 0, width, height)
                    block.resume(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                block.resumeWithException(e)
            } finally {
                bitmapWithStride?.recycle()
                image?.close()
                imageReader?.close()
                virtualDisplay?.release()
            }
        }, handler)
    }

    companion object {
        private val width by lazy { ScreenUtils.getScreenWidth() }
        private val height by lazy { ScreenUtils.getScreenHeight() }
        private val dpi by lazy { ScreenUtils.getScreenDensityDpi() }
    }
}