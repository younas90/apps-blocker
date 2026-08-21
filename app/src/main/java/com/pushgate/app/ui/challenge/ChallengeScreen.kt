package com.pushgate.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pushgate.app.pose.Coaching
import com.pushgate.app.pose.PushUpCounter
import com.pushgate.app.pose.SkeletonOverlay
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.Mist

@Composable
fun ChallengeScreen(
    controller: ChallengeController,
    cameraContent: @Composable (Modifier) -> Unit,
    onGiveUp: () -> Unit,
    onContinue: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Ink)) {

        when (controller.stage) {
            ChallengeStage.NEEDS_CAMERA -> CameraGate(
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
                onGiveUp = onGiveUp
            )

            ChallengeStage.ERROR -> ErrorPanel(
                message = controller.errorMessage ?: "Something went wrong.",
                onRetry = controller::retryCamera,
                onGiveUp = onGiveUp
            )

            else -> {
                cameraContent(Modifier.fillMaxSize())

                SkeletonOverlay(
                    frame = controller.frame,
                    state = controller.state,
                    mirror = controller.mirrored,
                    modifier = Modifier.fillMaxSize()
                )

                Scrims()

                TopBar(
                    label = controller.appLabel,
                    minutes = controller.minutesOffered,
                    onGiveUp = onGiveUp,
                    onFlip = controller::toggleCamera
                )

                DepthMeter(
                    elbowAngle = controller.state.elbowAngle,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(end = 14.dp)
                )

                BottomPanel(controller)

                AnimatedVisibility(
                    visible = controller.stage == ChallengeStage.SUCCESS,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut()
                ) {
                    SuccessPanel(
                        label = controller.appLabel,
                        minutes = controller.minutesOffered,
                        reps = controller.state.reps,
                        onContinue = onContinue
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrims() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.85f), Color.Transparent)))
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Ink.copy(alpha = 0.92f)
                )
            )
    )
}

@Composable
private fun TopBar(
    label: String,
    minutes: Int,
    onGiveUp: () -> Unit,
    onFlip: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { if (armed) onGiveUp() else armed = true }) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = if (armed) Crimson else Mist,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (armed) "Tap again to give up" else "Give up",
                color = if (armed) Crimson else Mist,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = label,
                color = Chalk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$minutes min on the line",
                color = Emerald,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.width(10.dp))

        TextButton(onClick = onFlip, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "Flip camera", tint = Mist)
        }
    }
}

@Composable
private fun BottomPanel(controller: ChallengeController) {
    val state = controller.state
    val holdActive = state.holdRequired && state.holdProgress > 0f && state.holdProgress < 1f

    val coachColor = when (state.coaching) {
        Coaching.GOOD -> Emerald
        Coaching.HOLD_IT -> Amber
        Coaching.STRAIGHTEN_BODY, Coaching.TOO_FAST -> Crimson
        else -> Mist
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (holdActive) {
            Text(
                text = "HOLD AT THE BOTTOM",
                color = Amber,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            HoldBar(progress = state.holdProgress)
            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = PushUpCounter.coachingText(state.coaching, state.holdProgress),
            color = coachColor,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        RepPips(done = state.reps, total = state.required, rejected = state.rejected)

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.reps}",
                color = Emerald,
                fontSize = 68.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = " / ${state.required}",
                color = Mist,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        if (state.rejected > 0) {
            Text(
                text = "${state.rejected} rep${if (state.rejected == 1) "" else "s"} didn't count",
                color = Crimson.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RepPips(done: Int, total: Int, rejected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total.coerceAtMost(20)) { i ->
            val filled = i < done
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (total > 12) 12.dp else 22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (filled) Emerald else Chalk.copy(alpha = 0.18f))
            )
        }
    }
    if (total > 20) {
        Spacer(Modifier.height(4.dp))
        Text("$done of $total", color = Mist, fontSize = 12.sp)
    }
}

@Composable
private fun HoldBar(progress: Float) {
    val animated by animateFloatAsState(progress, tween(120), label = "hold")
    Box(
        Modifier
            .fillMaxWidth(0.7f)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Chalk.copy(alpha = 0.16f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(Amber)
        )
    }
}

/** Live range-of-motion feedback: fills as the chest drops, so depth is visible without numbers. */
@Composable
private fun DepthMeter(elbowAngle: Float, modifier: Modifier = Modifier) {
    val depth = if (elbowAngle.isNaN()) 0f else (1f - ((elbowAngle - 60f) / 120f)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(depth, tween(90), label = "depth")

    Box(
        modifier
            .width(8.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Chalk.copy(alpha = 0.14f))
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(animated)
                .background(
                    Brush.verticalGradient(listOf(Emerald, Emerald.copy(alpha = 0.55f)))
                )
        )
    }
}

@Composable
private fun SuccessPanel(label: String, minutes: Int, reps: Int, onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(30.dp)
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Emerald.copy(alpha = 0.14f))
                    .border(2.dp, Emerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$reps", color = Emerald, fontSize = 38.sp, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Paid in full",
                color = Chalk,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$label is open for $minutes minute${if (minutes == 1) "" else "s"}. " +
                    "When it runs out, the price goes up.",
                color = Mist,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Open $label", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CameraGate(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onGiveUp: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "PushGate needs the camera",
            color = Chalk,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "It watches you do the reps and nothing else. Frames are processed on this phone and " +
                "are never recorded, saved or sent anywhere.",
            color = Mist,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Allow camera", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenAppSettings) {
            Text("Already denied? Open app settings", color = Mist)
        }
        TextButton(onClick = onGiveUp) {
            Text("Back", color = Mist)
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, onGiveUp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "The camera did not start",
            color = Chalk,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Your quota is untouched — nothing was counted against you.",
            color = Mist,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(20.dp))

        // The exact cause, verbatim. A generic "camera unavailable" on a phone whose camera works
        // is impossible to act on, and impossible for anyone to report usefully.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(14.dp)
        ) {
            Text(
                message,
                color = Mist,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Try again", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onGiveUp) { Text("Back", color = Mist) }
    }
}
