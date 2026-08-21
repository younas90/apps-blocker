package com.pushgate.app.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
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
     * Skeleton edges to draw. The 468 face points are deliberately omitted: they add nothing to a
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

    /** Joints drawn as dots, and the set that must be visible for a rep to count. */
    val KEY_JOINTS = listOf(
        L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
        L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE
    )

    val REQUIRED_FOR_REP = listOf(L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST, L_HIP, R_HIP)
}

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
        minOf(a.visibility, b.visibility)
    )

    /**
     * How far the body is from a plank, in degrees. 0 is a perfect line shoulder-hip-knee;
     * positive means the hips are sagging or piked. This is the check that kills the
     * "bob your head and call it a rep" cheat.
     */
    fun bodyLineDeviation(shoulder: P3, hip: P3, knee: P3): Float {
        val angle = angleAt(shoulder, hip, knee)
        return if (angle.isNaN()) Float.NaN else abs(180f - angle)
    }

    /**
     * True when the torso reads as horizontal in the *image*, which is what separates a push-up
     * from a squat, a sit-up or someone nodding at their phone.
     *
     * Uses pixel-space coordinates so the frame's aspect ratio is already accounted for.
     */
    fun isTorsoHorizontal(shoulderPx: P3, hipPx: P3, tolerance: Float = 1.15f): Boolean {
        val dx = abs(shoulderPx.x - hipPx.x)
        val dy = abs(shoulderPx.y - hipPx.y)
        if (dx < 1e-3f && dy < 1e-3f) return false
        return dx > dy * tolerance
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
