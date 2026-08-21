package com.pushgate.app.pose

import com.pushgate.app.data.prefs.Settings
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Phase { UNKNOWN, UP, DOWN }

enum class Coaching {
    GET_IN_FRAME,
    TURN_SIDEWAYS,
    GO_LOWER,
    STRAIGHTEN_BODY,
    TOO_FAST,
    HOLD_IT,
    LOCK_OUT,
    GOOD,
    LOST_TRACKING
}

data class RepState(
    val reps: Int = 0,
    val required: Int = 0,
    val rejected: Int = 0,
    val phase: Phase = Phase.UNKNOWN,
    val elbowAngle: Float = Float.NaN,
    val bodyDeviation: Float = Float.NaN,
    val depthThisRep: Float = Float.NaN,
    val coaching: Coaching = Coaching.GET_IN_FRAME,
    val personVisible: Boolean = false,
    val holdRequired: Boolean = false,
    val holdProgress: Float = 0f,
    val lastRepAccepted: Boolean? = null,
    val complete: Boolean = false,
    val avgDepth: Float = Float.NaN,
    val avgForm: Float = Float.NaN
) {
    val progress: Float get() = if (required <= 0) 0f else (reps.toFloat() / required).coerceIn(0f, 1f)
}

/**
 * Turns a stream of pose frames into a trustworthy rep count.
 *
 * The counter is deliberately pessimistic. Every rejection rule below exists because it is a
 * cheat somebody would otherwise find in the first five minutes:
 *
 *  - **Range of motion** — a rep only opens below [Settings.downAngleThreshold] and only closes
 *    above [Settings.upAngleThreshold], so half-reps score nothing.
 *  - **Plank integrity** — hips sagging or piked past [Settings.maxBodyBend] voids the rep, which
 *    is what stops a head-bob from reading as a push-up.
 *  - **Horizontal torso** — squats, sit-ups and nodding all fail the orientation gate.
 *  - **Cadence floor** — anything faster than [Settings.minRepMillis] is bouncing, not a rep.
 *  - **Tracking continuity** — if the body leaves frame mid-rep the rep is voided, so you cannot
 *    duck out of shot at the bottom.
 *  - **Random hold** — one rep per set, chosen at random, must be held at the bottom. A
 *    pre-recorded video of yourself cannot know which one, so replay attacks fail the set.
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
    private var coaching = Coaching.GET_IN_FRAME

    /** Which rep index (0-based) carries the surprise bottom hold. */
    private var holdRepIndex = if (requiredReps > 1) random.nextInt(requiredReps) else 0
    private var holdEnteredAt = 0L
    private var holdSatisfied = false

    private val depths = mutableListOf<Float>()
    private val forms = mutableListOf<Float>()

    fun currentRequiredHold(): Boolean = reps == holdRepIndex

    fun snapshot(): RepState = RepState(
        reps = reps,
        required = requiredReps,
        rejected = rejected,
        phase = phase,
        coaching = coaching,
        personVisible = false,
        holdRequired = currentRequiredHold(),
        lastRepAccepted = lastRepAccepted,
        complete = reps >= requiredReps,
        avgDepth = depths.averageOrNaN(),
        avgForm = forms.averageOrNaN()
    )

    /** Called when the model reports no usable body in the frame. */
    fun onNoPose(now: Long): RepState {
        if (phase == Phase.DOWN && now - lastSeenAt > TRACKING_GRACE_MS) {
            // Vanishing at the bottom of a rep is the oldest trick there is.
            repInvalidated = true
        }
        coaching = if (now - lastSeenAt > TRACKING_GRACE_MS) Coaching.GET_IN_FRAME else Coaching.LOST_TRACKING
        return snapshot().copy(personVisible = false, coaching = coaching)
    }

    /**
     * @param world  landmarks in metres, hip-centred — used for joint angles.
     * @param pixels landmarks in image pixels — used for orientation and visibility.
     */
    fun onPose(world: List<P3>, pixels: List<P3>, now: Long): RepState {
        lastSeenAt = now

        if (world.size <= Lm.R_HIP || !hasRequiredLandmarks(pixels)) {
            coaching = Coaching.GET_IN_FRAME
            if (phase == Phase.DOWN) repInvalidated = true
            return snapshot().copy(personVisible = true, coaching = coaching)
        }

        val leftElbow = PoseMath.angleAt(world[Lm.L_SHOULDER], world[Lm.L_ELBOW], world[Lm.L_WRIST])
        val rightElbow = PoseMath.angleAt(world[Lm.R_SHOULDER], world[Lm.R_ELBOW], world[Lm.R_WRIST])
        val elbow = PoseMath.meanFinite(leftElbow, rightElbow)

        val leftBend = PoseMath.bodyLineDeviation(world[Lm.L_SHOULDER], world[Lm.L_HIP], world[Lm.L_KNEE])
        val rightBend = PoseMath.bodyLineDeviation(world[Lm.R_SHOULDER], world[Lm.R_HIP], world[Lm.R_KNEE])
        val bend = PoseMath.meanFinite(leftBend, rightBend)

        val shoulderPx = PoseMath.midpoint(pixels[Lm.L_SHOULDER], pixels[Lm.R_SHOULDER])
        val hipPx = PoseMath.midpoint(pixels[Lm.L_HIP], pixels[Lm.R_HIP])
        val horizontal = PoseMath.isTorsoHorizontal(shoulderPx, hipPx)

        if (!horizontal) {
            coaching = Coaching.TURN_SIDEWAYS
            if (phase == Phase.DOWN) repInvalidated = true
            return buildState(elbow, bend, personVisible = true)
        }

        if (elbow.isNaN()) {
            coaching = Coaching.GET_IN_FRAME
            return buildState(elbow, bend, personVisible = true)
        }

        if (!bend.isNaN() && bend > settings.maxBodyBend) {
            worstBendThisRep = maxOf(worstBendThisRep, bend)
            if (phase == Phase.DOWN) repInvalidated = true
            coaching = Coaching.STRAIGHTEN_BODY
        }

        when (phase) {
            Phase.UNKNOWN -> {
                // Only start counting from a locked-out top position, so a set cannot begin
                // halfway through a rep.
                if (elbow >= settings.upAngleThreshold) {
                    phase = Phase.UP
                    coaching = Coaching.GOOD
                } else {
                    coaching = Coaching.LOCK_OUT
                }
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
                } else if (elbow < settings.upAngleThreshold) {
                    coaching = Coaching.GO_LOWER
                } else {
                    coaching = Coaching.GOOD
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
                        // Came up early during the hold: restart the clock at the next bottom.
                        holdEnteredAt = now
                        coaching = Coaching.HOLD_IT
                    }
                }

                if (elbow >= settings.upAngleThreshold) {
                    closeRep(now, elbow)
                }
            }
        }

        return buildState(elbow, bend, personVisible = true)
    }

    private fun closeRep(now: Long, elbow: Float) {
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

    private fun buildState(elbow: Float, bend: Float, personVisible: Boolean): RepState {
        val holdActive = phase == Phase.DOWN && currentRequiredHold() && !holdSatisfied
        val holdProgress = if (holdActive) {
            ((System.currentTimeMillis() - holdEnteredAt).toFloat() / HOLD_DURATION_MS).coerceIn(0f, 1f)
        } else if (currentRequiredHold() && holdSatisfied) 1f else 0f

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
            avgForm = forms.averageOrNaN()
        )
    }

    private fun hasRequiredLandmarks(pixels: List<P3>): Boolean {
        if (pixels.size <= Lm.R_HIP) return false
        return Lm.REQUIRED_FOR_REP.all { pixels[it].visibility >= MIN_VISIBILITY }
    }

    private fun List<Float>.averageOrNaN(): Float =
        if (isEmpty()) Float.NaN else (sum() / size)

    companion object {
        const val MIN_VISIBILITY = 0.55f
        const val TRACKING_GRACE_MS = 1_500L
        const val HOLD_DURATION_MS = 1_200L

        fun coachingText(c: Coaching, holdProgress: Float = 0f): String = when (c) {
            Coaching.GET_IN_FRAME -> "Get your whole body in frame"
            Coaching.TURN_SIDEWAYS -> "Put the phone on the floor beside you, side on"
            Coaching.GO_LOWER -> "Lower — chest toward the floor"
            Coaching.STRAIGHTEN_BODY -> "Straighten up — hips in line"
            Coaching.TOO_FAST -> "Too fast, that one did not count"
            Coaching.HOLD_IT -> "HOLD — ${(holdProgress * 100).roundToInt()}%"
            Coaching.LOCK_OUT -> "Start at the top, arms straight"
            Coaching.GOOD -> "Good — keep going"
            Coaching.LOST_TRACKING -> "Lost you for a second"
        }
    }
}
