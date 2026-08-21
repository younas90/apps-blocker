package com.pushgate.app.pose

import com.pushgate.app.data.prefs.Settings
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Phase { UNKNOWN, UP, DOWN }

enum class Coaching {
    NO_BODY,
    GET_IN_FRAME,
    STAND_DOWN,
    GO_LOWER,
    STRAIGHTEN_BODY,
    TOO_FAST,
    HOLD_IT,
    READY,
    GOOD,
    LOST_TRACKING
}

/** Everything the challenge screen shows, including the live diagnostics strip. */
data class RepState(
    val reps: Int = 0,
    val required: Int = 0,
    val rejected: Int = 0,
    val phase: Phase = Phase.UNKNOWN,
    val elbowAngle: Float = Float.NaN,
    val bodyDeviation: Float = Float.NaN,
    val depthThisRep: Float = Float.NaN,
    val coaching: Coaching = Coaching.NO_BODY,
    val personVisible: Boolean = false,
    val holdRequired: Boolean = false,
    val holdProgress: Float = 0f,
    val lastRepAccepted: Boolean? = null,
    val complete: Boolean = false,
    val avgDepth: Float = Float.NaN,
    val avgForm: Float = Float.NaN,

    // --- diagnostics, shown on screen so a failure is never silent ---
    val sideUsed: Side? = null,
    val sideVisibility: Float = 0f,
    val torsoTilt: Float = Float.NaN,
    val torsoMeasurable: Boolean = false,
    val fps: Float = 0f
) {
    val progress: Float get() = if (required <= 0) 0f else (reps.toFloat() / required).coerceIn(0f, 1f)
}

/**
 * Turns a stream of pose frames into a trustworthy rep count.
 *
 * Rewritten after the first on-device test counted precisely zero reps in every position tried.
 * Two gates were contradicting each other and the instructions:
 *
 *  - The old code demanded that **both** shoulders, elbows, wrists and hips be visible at 0.55
 *    confidence, while the app told the user to lie side-on. Seen from the side the far arm is
 *    behind the near one, so the model reports it as low-confidence by definition and the gate
 *    could never pass. It now judges the rep on whichever side the camera can actually see.
 *
 *  - The old orientation check required the torso to be wider than it was tall in the image.
 *    Facing the phone head-on, shoulders and hips project nearly on top of each other, so that
 *    test read noise and refused the rep. Orientation is now only used to *reject* a clearly
 *    upright body, and is skipped entirely when the torso is too foreshortened to measure.
 *
 * The anti-cheat rules that survived are the ones that still work from one side: range of motion,
 * plank integrity, cadence, tracking continuity, and the random bottom hold.
 */
class PushUpCounter(
    private val settings: Settings,
    private val requiredReps: Int,
    private val random: Random = Random.Default
) {

    private var phase: Phase = Phase.UNKNOWN
    private var reps = 0
    private var rejected = 0

    private var repStartedAt = 0L
    private var minAngleThisRep = Float.MAX_VALUE
    private var worstBendThisRep = 0f
    private var repInvalidated = false
    private var lastRepAccepted: Boolean? = null

    private var lastSeenAt = 0L
    private var coaching = Coaching.NO_BODY

    private var holdRepIndex = if (requiredReps > 1) random.nextInt(requiredReps) else 0
    private var holdEnteredAt = 0L
    private var holdSatisfied = false

    private val depths = mutableListOf<Float>()
    private val forms = mutableListOf<Float>()

    /** Smoothed elbow angle. Raw per-frame angles jitter by several degrees and cause double counts. */
    private var smoothedElbow = Float.NaN

    private var lastSide: Side? = null
    private var lastVisibility = 0f
    private var lastTilt = Float.NaN
    private var lastMeasurable = false
    private var lastFps = 0f

    fun currentRequiredHold(): Boolean = reps == holdRepIndex

    /** Called when the model reports no usable body in the frame. */
    fun onNoPose(now: Long, fps: Float): RepState {
        lastFps = fps
        if (phase == Phase.DOWN && now - lastSeenAt > TRACKING_GRACE_MS) {
            repInvalidated = true
        }
        coaching = if (now - lastSeenAt > TRACKING_GRACE_MS) Coaching.NO_BODY else Coaching.LOST_TRACKING
        lastSide = null
        lastVisibility = 0f
        return build(Float.NaN, Float.NaN, personVisible = false)
    }

    /**
     * @param world  landmarks in metres, hip-centred — used for joint angles.
     * @param pixels landmarks in image pixels — used for orientation and visibility.
     */
    fun onPose(
        world: List<P3>,
        pixels: List<P3>,
        imageWidth: Int,
        imageHeight: Int,
        now: Long,
        fps: Float
    ): RepState {
        lastSeenAt = now
        lastFps = fps

        if (world.size <= Lm.R_HIP || pixels.size <= Lm.R_HIP) {
            coaching = Coaching.GET_IN_FRAME
            return build(Float.NaN, Float.NaN, personVisible = true)
        }

        val (side, visibility) = PoseMath.bestSide(pixels)
        lastSide = side
        lastVisibility = visibility

        if (visibility < MIN_VISIBILITY) {
            coaching = Coaching.GET_IN_FRAME
            if (phase == Phase.DOWN) repInvalidated = true
            return build(Float.NaN, Float.NaN, personVisible = true)
        }

        val sh = PoseMath.shoulderOf(side)
        val el = PoseMath.elbowOf(side)
        val wr = PoseMath.wristOf(side)
        val hp = PoseMath.hipOf(side)
        val kn = PoseMath.kneeOf(side)

        val rawElbow = PoseMath.angleAt(world[sh], world[el], world[wr])
        if (rawElbow.isNaN()) {
            coaching = Coaching.GET_IN_FRAME
            return build(Float.NaN, Float.NaN, personVisible = true)
        }

        // Exponential smoothing: enough to kill jitter, light enough not to lag a fast rep.
        smoothedElbow = if (smoothedElbow.isNaN()) rawElbow else smoothedElbow * 0.6f + rawElbow * 0.4f
        val elbow = smoothedElbow

        val bend = if (pixels[kn].visibility >= MIN_VISIBILITY) {
            PoseMath.bodyLineDeviation(world[sh], world[hp], world[kn])
        } else {
            Float.NaN // Knees out of frame is fine; the plank check just sits this one out.
        }

        val shoulderPx = pixels[sh]
        val hipPx = pixels[hp]
        lastMeasurable = PoseMath.torsoMeasurable(shoulderPx, hipPx, imageWidth, imageHeight)
        lastTilt = if (lastMeasurable) PoseMath.torsoTiltDegrees(shoulderPx, hipPx) else Float.NaN

        // Only reject a body that is clearly upright — a squat or someone standing looking down.
        if (lastMeasurable && lastTilt > MAX_TORSO_TILT) {
            coaching = Coaching.STAND_DOWN
            if (phase == Phase.DOWN) repInvalidated = true
            return build(elbow, bend, personVisible = true)
        }

        if (!bend.isNaN() && bend > settings.maxBodyBend) {
            worstBendThisRep = maxOf(worstBendThisRep, bend)
            if (phase == Phase.DOWN) repInvalidated = true
            coaching = Coaching.STRAIGHTEN_BODY
        }

        when (phase) {
            Phase.UNKNOWN -> {
                // Seed from wherever the arms already are rather than demanding a full lockout
                // first. Requiring lockout meant a model that never reported a straight-enough arm
                // left the state machine stuck and no rep could ever begin.
                phase = if (elbow >= midAngle()) Phase.UP else Phase.DOWN
                if (phase == Phase.DOWN) {
                    repStartedAt = now
                    minAngleThisRep = elbow
                    holdEnteredAt = now
                    holdSatisfied = !currentRequiredHold()
                }
                coaching = Coaching.READY
            }

            Phase.UP -> {
                if (elbow <= settings.downAngleThreshold) {
                    phase = Phase.DOWN
                    repStartedAt = now
                    minAngleThisRep = elbow
                    worstBendThisRep = if (bend.isNaN()) 0f else bend
                    repInvalidated = false
                    holdEnteredAt = now
                    holdSatisfied = !currentRequiredHold()
                    coaching = if (currentRequiredHold()) Coaching.HOLD_IT else Coaching.GOOD
                } else {
                    coaching = if (elbow < settings.upAngleThreshold) Coaching.GO_LOWER else Coaching.GOOD
                }
            }

            Phase.DOWN -> {
                minAngleThisRep = minOf(minAngleThisRep, elbow)
                if (!bend.isNaN()) worstBendThisRep = maxOf(worstBendThisRep, bend)

                if (currentRequiredHold() && !holdSatisfied) {
                    if (elbow <= settings.downAngleThreshold) {
                        if (now - holdEnteredAt >= HOLD_DURATION_MS) {
                            holdSatisfied = true
                            coaching = Coaching.GOOD
                        } else {
                            coaching = Coaching.HOLD_IT
                        }
                    } else {
                        holdEnteredAt = now
                        coaching = Coaching.HOLD_IT
                    }
                }

                if (elbow >= settings.upAngleThreshold) closeRep(now)
            }
        }

        return build(elbow, bend, personVisible = true)
    }

    private fun midAngle() = (settings.downAngleThreshold + settings.upAngleThreshold) / 2f

    private fun closeRep(now: Long) {
        phase = Phase.UP
        val duration = now - repStartedAt

        val tooFast = duration < settings.minRepMillis
        val badForm = worstBendThisRep > settings.maxBodyBend
        val shallow = minAngleThisRep > settings.downAngleThreshold
        val missedHold = currentRequiredHold() && !holdSatisfied

        val accepted = !tooFast && !badForm && !shallow && !repInvalidated && !missedHold

        if (accepted) {
            reps++
            depths += minAngleThisRep
            forms += (100f - worstBendThisRep.coerceIn(0f, 100f))
            lastRepAccepted = true
            coaching = Coaching.GOOD
        } else {
            rejected++
            lastRepAccepted = false
            coaching = when {
                tooFast -> Coaching.TOO_FAST
                shallow -> Coaching.GO_LOWER
                badForm -> Coaching.STRAIGHTEN_BODY
                missedHold -> Coaching.HOLD_IT
                else -> Coaching.GET_IN_FRAME
            }
        }

        minAngleThisRep = Float.MAX_VALUE
        worstBendThisRep = 0f
        repInvalidated = false
        holdSatisfied = false
    }

    private fun build(elbow: Float, bend: Float, personVisible: Boolean): RepState {
        val holdActive = phase == Phase.DOWN && currentRequiredHold() && !holdSatisfied
        val holdProgress = when {
            holdActive -> ((System.currentTimeMillis() - holdEnteredAt).toFloat() / HOLD_DURATION_MS)
                .coerceIn(0f, 1f)
            currentRequiredHold() && holdSatisfied -> 1f
            else -> 0f
        }

        return RepState(
            reps = reps,
            required = requiredReps,
            rejected = rejected,
            phase = phase,
            elbowAngle = elbow,
            bodyDeviation = bend,
            depthThisRep = if (minAngleThisRep == Float.MAX_VALUE) Float.NaN else minAngleThisRep,
            coaching = coaching,
            personVisible = personVisible,
            holdRequired = currentRequiredHold(),
            holdProgress = holdProgress,
            lastRepAccepted = lastRepAccepted,
            complete = reps >= requiredReps,
            avgDepth = depths.averageOrNaN(),
            avgForm = forms.averageOrNaN(),
            sideUsed = lastSide,
            sideVisibility = lastVisibility,
            torsoTilt = lastTilt,
            torsoMeasurable = lastMeasurable,
            fps = lastFps
        )
    }

    private fun List<Float>.averageOrNaN(): Float = if (isEmpty()) Float.NaN else (sum() / size)

    companion object {
        /**
         * Deliberately low. The far side of a body seen edge-on sits around 0.3-0.5, and the old
         * 0.55 floor applied to both sides at once is what made the counter unusable.
         */
        const val MIN_VISIBILITY = 0.35f
        const val TRACKING_GRACE_MS = 1_500L
        const val HOLD_DURATION_MS = 1_200L

        /** Above this many degrees from horizontal the body is standing, not planking. */
        const val MAX_TORSO_TILT = 62f

        fun coachingText(c: Coaching, holdProgress: Float = 0f): String = when (c) {
            Coaching.NO_BODY -> "Point the camera at yourself"
            Coaching.GET_IN_FRAME -> "Move back so more of you is in shot"
            Coaching.STAND_DOWN -> "Get down into a push-up position"
            Coaching.GO_LOWER -> "Lower — chest toward the floor"
            Coaching.STRAIGHTEN_BODY -> "Straighten up — hips in line"
            Coaching.TOO_FAST -> "Too fast, that one did not count"
            Coaching.HOLD_IT -> "HOLD IT — ${(holdProgress * 100).roundToInt()}%"
            Coaching.READY -> "Got you. Start when ready"
            Coaching.GOOD -> "Good — keep going"
            Coaching.LOST_TRACKING -> "Lost you for a second"
        }
    }
}
