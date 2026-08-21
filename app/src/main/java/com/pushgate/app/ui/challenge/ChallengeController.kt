package com.pushgate.app.ui.challenge

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.db.RepSession
import com.pushgate.app.data.prefs.Settings
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.pose.Coaching
import com.pushgate.app.pose.PoseFrame
import com.pushgate.app.pose.PushUpCounter
import com.pushgate.app.pose.RepState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class ChallengeStage { LOADING, NEEDS_CAMERA, RUNNING, SUCCESS, GAVE_UP, ERROR }

/**
 * Holds one push-up challenge from "camera on" to "grant written".
 *
 * Plain Compose state rather than a ViewModel: the activity is portrait-locked and handles its own
 * config changes, so there is no recreation to survive, and a challenge that silently resumed
 * across a process death would be a way to bank reps you never did.
 */
class ChallengeController(
    private val context: Context,
    private val scope: CoroutineScope,
    val packageName: String,
    val appLabel: String,
    val repsRequired: Int,
    val minutesOffered: Int
) {

    private val repo = BlockRepository.get(context)

    var stage by mutableStateOf(ChallengeStage.LOADING)
        private set
    var state by mutableStateOf(RepState(required = repsRequired))
        private set
    var frame by mutableStateOf<PoseFrame?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(Settings())
        private set
    var mirrored by mutableStateOf(true)
        private set
    var fps by mutableStateOf(0f)
        private set

    private var counter: PushUpCounter? = null
    private var startedAt = 0L
    private var lastRepCount = 0
    private var finished = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    fun begin() {
        scope.launch {
            val s = repo.settingsStore.current()
            settings = s
            mirrored = s.cameraFacingFront
            counter = PushUpCounter(s, repsRequired)
            state = RepState(required = repsRequired, coaching = Coaching.GET_IN_FRAME)
            startedAt = System.currentTimeMillis()
            stage = ChallengeStage.RUNNING
        }
    }

    fun onCameraDenied() {
        stage = ChallengeStage.NEEDS_CAMERA
    }

    fun onPoseError(message: String) {
        errorMessage = message
        stage = ChallengeStage.ERROR
    }

    fun toggleCamera() {
        mirrored = !mirrored
        scope.launch { repo.settingsStore.setCameraFront(mirrored) }
    }

    fun onFrame(f: PoseFrame) {
        if (stage != ChallengeStage.RUNNING || finished) return

        frame = f
        fps = f.fps
        val c = counter ?: return

        val next = if (f.hasPose) {
            c.onPose(f.world, f.pixels, f.timestampMs)
        } else {
            c.onNoPose(f.timestampMs)
        }
        state = next

        if (next.reps > lastRepCount) {
            lastRepCount = next.reps
            if (settings.vibrateOnRep) buzz(if (next.complete) 120 else 35)
        }

        if (next.complete) finishSuccessfully(next)
    }

    private fun finishSuccessfully(finalState: RepState) {
        if (finished) return
        finished = true
        stage = ChallengeStage.SUCCESS

        val endedAt = System.currentTimeMillis()
        scope.launch {
            repo.saveRepSession(
                RepSession(
                    packageName = packageName,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    repsRequired = repsRequired,
                    repsCompleted = finalState.reps,
                    secondsEarned = minutesOffered * 60,
                    avgDepthDegrees = finalState.avgDepth.orZero(),
                    avgFormScore = finalState.avgForm.orZero(),
                    rejectedReps = finalState.rejected,
                    completed = true
                )
            )
            repo.grantEarnedUnlock(packageName, minutesOffered, repsRequired)
        }
    }

    /** Called when the user walks away. The partial set is still recorded — the stats are honest. */
    fun abandon() {
        if (finished) return
        finished = true
        stage = ChallengeStage.GAVE_UP

        val snapshot = state
        val endedAt = System.currentTimeMillis()
        scope.launch {
            repo.saveRepSession(
                RepSession(
                    packageName = packageName,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    repsRequired = repsRequired,
                    repsCompleted = snapshot.reps,
                    secondsEarned = 0,
                    avgDepthDegrees = snapshot.avgDepth.orZero(),
                    avgFormScore = snapshot.avgForm.orZero(),
                    rejectedReps = snapshot.rejected,
                    completed = false
                )
            )
            repo.logEvent(
                EventLog.CHALLENGE_ABANDONED,
                packageName,
                "${snapshot.reps}/$repsRequired"
            )
        }
    }

    private fun buzz(ms: Long) {
        val v = vibrator ?: return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun Float.orZero(): Float = if (isNaN()) 0f else this
}
