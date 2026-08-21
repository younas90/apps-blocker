package com.pushgate.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** One analysed frame, in both spaces the counter and the overlay need. */
data class PoseFrame(
    /** Normalised [0,1] image coordinates — what the skeleton overlay draws. */
    val normalized: List<P3>,
    /** Metric, hip-centred coordinates — what the joint-angle maths uses. */
    val world: List<P3>,
    /** Normalised coordinates scaled to pixels — used for orientation checks. */
    val pixels: List<P3>,
    val imageWidth: Int,
    val imageHeight: Int,
    val timestampMs: Long,
    /** Rolling analysis rate, so a stalled pipeline is visible in the UI rather than silent. */
    val fps: Float = 0f
) {
    val hasPose: Boolean get() = normalized.isNotEmpty()
}

/**
 * Thin, lifecycle-safe wrapper over MediaPipe's PoseLandmarker in LIVE_STREAM mode.
 *
 * Everything runs on-device: no frame, no landmark and no image ever leaves the phone, which is
 * the only defensible way to ship a feature that asks people to point a camera at themselves.
 */
class PoseAnalyzer(
    context: Context,
    private val onFrame: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)

    @Volatile private var landmarker: PoseLandmarker? = null
    @Volatile private var lastImageWidth = 0
    @Volatile private var lastImageHeight = 0
    @Volatile var usingGpu: Boolean = false
        private set
    @Volatile var ready: Boolean = false
        private set

    /**
     * LIVE_STREAM mode rejects any frame whose timestamp is not strictly greater than the last.
     * Wall-clock milliseconds repeat whenever two frames land inside the same millisecond, and
     * MediaPipe then throws the frame away — the preview looks alive while the skeleton never
     * appears. A monotonic counter makes that impossible.
     */
    private val timestamp = AtomicLong(0L)

    @Volatile var fps: Float = 0f
        private set
    private var lastFrameAt = 0L

    /**
     * Built off the caller's thread: creating the landmarker unpacks a 6 MB model and initialises
     * a GPU delegate, which visibly janks the challenge screen if done on the main thread.
     */
    fun initialize() {
        if (closed.get() || landmarker != null) return

        // GPU first for battery and latency; plenty of devices have a delegate that reports
        // available and then fails to compile the graph, so CPU is a real fallback, not a formality.
        val gpu = runCatching { build(useGpu = true) }
        gpu.getOrNull()?.let {
            landmarker = it
            usingGpu = true
            ready = true
            return
        }
        Log.w(TAG, "GPU delegate unavailable, falling back to CPU", gpu.exceptionOrNull())

        val cpu = runCatching { build(useGpu = false) }
        cpu.getOrNull()?.let {
            landmarker = it
            usingGpu = false
            ready = true
            return
        }

        val why = cpu.exceptionOrNull() ?: gpu.exceptionOrNull()
        onError(
            "The pose model would not start on this device.\n\n" +
                (why?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "Unknown cause.")
        )
    }

    private fun build(useGpu: Boolean): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(if (useGpu) Delegate.GPU else Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(false)
            .setResultListener { result, _ -> publish(result) }
            .setErrorListener { e -> Log.w(TAG, "PoseLandmarker runtime error", e) }
            .build()

        return PoseLandmarker.createFromOptions(appContext, options)
    }

    override fun analyze(image: ImageProxy) {
        val marker = landmarker
        if (marker == null || closed.get()) {
            image.close()
            return
        }

        try {
            val rotation = image.imageInfo.rotationDegrees
            // MediaPipe rotates internally, so the landmark frame matches what the user sees.
            val rotated90 = rotation == 90 || rotation == 270
            lastImageWidth = if (rotated90) image.height else image.width
            lastImageHeight = if (rotated90) image.width else image.height

            val bitmap = image.toArgbBitmap()
            if (bitmap == null) {
                image.close()
                return
            }

            val mpImage = BitmapImageBuilder(bitmap).build()
            val processing = ImageProcessingOptions.builder()
                .setRotationDegrees(rotation)
                .build()

            marker.detectAsync(mpImage, processing, timestamp.incrementAndGet())
        } catch (t: Throwable) {
            Log.w(TAG, "analyze failed", t)
        } finally {
            image.close()
        }
    }

    private fun publish(result: PoseLandmarkerResult) {
        if (closed.get()) return

        val now = System.currentTimeMillis()
        if (lastFrameAt != 0L) {
            val dt = (now - lastFrameAt).coerceAtLeast(1L)
            val instant = 1000f / dt
            fps = if (fps == 0f) instant else fps * 0.8f + instant * 0.2f
        }
        lastFrameAt = now

        val w = lastImageWidth
        val h = lastImageHeight

        val landmarkList = result.landmarks().firstOrNull()
        if (landmarkList.isNullOrEmpty()) {
            onFrame(PoseFrame(emptyList(), emptyList(), emptyList(), w, h, now, fps))
            return
        }

        val normalized = landmarkList.map { lm ->
            P3(lm.x(), lm.y(), lm.z(), lm.visibility().orElse(0f))
        }

        val worldList = result.worldLandmarks().firstOrNull()
        val world = worldList?.map { lm ->
            P3(lm.x(), lm.y(), lm.z(), lm.visibility().orElse(0f))
        } ?: normalized

        val pixels = normalized.map { P3(it.x * w, it.y * h, it.z, it.visibility) }

        onFrame(PoseFrame(normalized, world, pixels, w, h, now, fps))
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            ready = false
            runCatching { landmarker?.close() }
            landmarker = null
        }
    }

    private companion object {
        const val TAG = "PushGate/Pose"
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}

/**
 * CameraX is configured for RGBA_8888 output, so the plane is already a straight ARGB buffer.
 *
 * The row-stride dance matters: many devices pad each row out to a hardware alignment, and
 * copying such a buffer into a tightly-packed bitmap shears the image diagonally — which the pose
 * model reads as a person who is not there.
 */
private fun ImageProxy.toArgbBitmap(): Bitmap? = runCatching {
    val plane = planes.firstOrNull() ?: return@runCatching null
    val buffer = plane.buffer
    buffer.rewind()

    val pixelStride = plane.pixelStride.coerceAtLeast(1)
    val rowStride = plane.rowStride
    val paddedWidth = rowStride / pixelStride
    if (paddedWidth < width || height <= 0) return@runCatching null

    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    if (buffer.remaining() < rowStride * height) return@runCatching null
    padded.copyPixelsFromBuffer(buffer)

    if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
}.getOrNull()
