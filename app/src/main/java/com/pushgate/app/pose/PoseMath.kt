package com.pushgate.app.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** MediaPipe Pose landmark indices. Only the ones a push-up actually needs. */
object Lm {
    const val NOSE = 0
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28

    /**
     * Skeleton edges to draw. The face points are deliberately omitted: they add nothing to a
     * push-up and turn the overlay into noise.
     */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        L_SHOULDER to R_SHOULDER,
        L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST,
        R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST,
        L_SHOULDER to L_HIP, R_SHOULDER to R_HIP,
        L_HIP to R_HIP,
        L_HIP to L_KNEE, L_KNEE to L_ANKLE,
        R_HIP to R_KNEE, R_KNEE to R_ANKLE
    )

    val KEY_JOINTS = listOf(
        L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
        L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE
    )
}

/** One half of the body. A push-up seen side-on only ever shows one of these properly. */
enum class Side { LEFT, RIGHT }

/** A landmark in whatever space the caller is working in, plus how sure the model is about it. */
data class P3(val x: Float, val y: Float, val z: Float, val visibility: Float)

object PoseMath {

    /**
     * Interior angle at [b] formed by a-b-c, in degrees.
     *
     * Fed world landmarks (metres, hip-centred) rather than normalised image coordinates, because
     * image coordinates are stretched by the frame's aspect ratio and would report a straight arm
     * as bent whenever the phone is in portrait.
     */
    fun angleAt(a: P3, b: P3, c: P3): Float {
        val v1x = a.x - b.x; val v1y = a.y - b.y; val v1z = a.z - b.z
        val v2x = c.x - b.x; val v2y = c.y - b.y; val v2z = c.z - b.z

        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val m1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val m2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (m1 < 1e-6f || m2 < 1e-6f) return Float.NaN

        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    fun midpoint(a: P3, b: P3) = P3(
        (a.x + b.x) / 2f,
        (a.y + b.y) / 2f,
        (a.z + b.z) / 2f,
        maxOf(a.visibility, b.visibility)
    )

    /**
     * How far the body is from a plank, in degrees. 0 is a perfect line shoulder-hip-knee.
     * NaN when the knee is not visible, which simply means the check is skipped.
     */
    fun bodyLineDeviation(shoulder: P3, hip: P3, knee: P3): Float {
        val angle = angleAt(shoulder, hip, knee)
        return if (angle.isNaN()) Float.NaN else abs(180f - angle)
    }

    /**
     * Which side of the body the camera can actually see, and how well.
     *
     * A push-up filmed side-on — the position the app itself recommends — hides the far arm behind
     * the near one, so the far-side landmarks are low-confidence by definition. Judging the rep on
     * whichever side is clearer is the only thing that works from that angle.
     */
    fun bestSide(pixels: List<P3>): Pair<Side, Float> {
        fun score(shoulder: Int, elbow: Int, wrist: Int): Float {
            val s = pixels.getOrNull(shoulder)?.visibility ?: 0f
            val e = pixels.getOrNull(elbow)?.visibility ?: 0f
            val w = pixels.getOrNull(wrist)?.visibility ?: 0f
            return (s + e + w) / 3f
        }
        val left = score(Lm.L_SHOULDER, Lm.L_ELBOW, Lm.L_WRIST)
        val right = score(Lm.R_SHOULDER, Lm.R_ELBOW, Lm.R_WRIST)
        return if (left >= right) Side.LEFT to left else Side.RIGHT to right
    }

    fun shoulderOf(side: Side) = if (side == Side.LEFT) Lm.L_SHOULDER else Lm.R_SHOULDER
    fun elbowOf(side: Side) = if (side == Side.LEFT) Lm.L_ELBOW else Lm.R_ELBOW
    fun wristOf(side: Side) = if (side == Side.LEFT) Lm.L_WRIST else Lm.R_WRIST
    fun hipOf(side: Side) = if (side == Side.LEFT) Lm.L_HIP else Lm.R_HIP
    fun kneeOf(side: Side) = if (side == Side.LEFT) Lm.L_KNEE else Lm.R_KNEE

    /** How far the torso is from horizontal in the image, in degrees. 0 flat, 90 upright. */
    fun torsoTiltDegrees(shoulderPx: P3, hipPx: P3): Float {
        val dx = abs(shoulderPx.x - hipPx.x)
        val dy = abs(shoulderPx.y - hipPx.y)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /**
     * Whether the torso is long enough on screen for its tilt to mean anything.
     *
     * Pointing the phone straight at someone squashes shoulders and hips almost on top of each
     * other, and the tilt of a five-pixel line is pure noise. Previously that noise was read as
     * "you are not horizontal" and the rep was refused — so facing the camera never worked.
     */
    fun torsoMeasurable(shoulderPx: P3, hipPx: P3, imageWidth: Int, imageHeight: Int): Boolean {
        val len = hypot(shoulderPx.x - hipPx.x, shoulderPx.y - hipPx.y)
        val shortEdge = min(imageWidth, imageHeight).toFloat()
        return shortEdge > 0f && len > shortEdge * 0.12f
    }

    fun distance(a: P3, b: P3): Float = hypot((a.x - b.x), (a.y - b.y))

    /** Mean of the finite values; NaN when nothing usable was supplied. */
    fun meanFinite(vararg values: Float): Float {
        var sum = 0f
        var n = 0
        for (v in values) if (!v.isNaN()) { sum += v; n++ }
        return if (n == 0) Float.NaN else sum / n
    }
}
