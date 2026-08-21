package com.pushgate.app.pose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

private val Good = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)

/**
 * The live green skeleton.
 *
 * Drawn as three passes per bone — a wide translucent glow, the bright core, and a white
 * highlight — because a single flat stroke disappears against a bright floor or a white t-shirt,
 * which is exactly the situation someone doing push-ups on a living-room rug is in.
 */
@Composable
fun SkeletonOverlay(
    frame: PoseFrame?,
    state: RepState,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = when {
        state.coaching == Coaching.STRAIGHTEN_BODY -> Bad
        state.lastRepAccepted == false -> Bad
        state.coaching == Coaching.HOLD_IT -> Warn
        state.coaching == Coaching.GO_LOWER -> Warn
        state.coaching == Coaching.STAND_DOWN -> Warn
        else -> Good
    }

    val animatedTint by animateFloatAsState(
        targetValue = if (tint == Good) 1f else 0f,
        animationSpec = tween(220),
        label = "skeletonTint"
    )

    // Depth glow: the bar brightens as the chest approaches the floor, so the user gets range-of-
    // motion feedback without having to read a number mid-rep.
    val depthGlow = when {
        state.elbowAngle.isNaN() -> 0f
        else -> (1f - ((state.elbowAngle - 60f) / 120f)).coerceIn(0f, 1f)
    }

    Canvas(modifier = modifier) {
        val f = frame ?: return@Canvas
        if (!f.hasPose || f.imageWidth == 0 || f.imageHeight == 0) return@Canvas

        val scale = max(size.width / f.imageWidth, size.height / f.imageHeight)
        val drawnW = f.imageWidth * scale
        val drawnH = f.imageHeight * scale
        val dx = (size.width - drawnW) / 2f
        val dy = (size.height - drawnH) / 2f

        fun project(p: P3): Offset {
            val x = dx + p.x * drawnW
            val y = dy + p.y * drawnH
            return Offset(if (mirror) size.width - x else x, y)
        }

        val pts = f.normalized
        val color = lerpColor(tint, Good, animatedTint)
        val boneWidth = (size.minDimension * 0.011f).coerceIn(4f, 14f)

        // Pass 1: glow
        drawBones(pts, ::project, color.copy(alpha = 0.18f + 0.22f * depthGlow), boneWidth * 3.2f)
        // Pass 2: core
        drawBones(pts, ::project, color.copy(alpha = 0.95f), boneWidth)
        // Pass 3: highlight
        drawBones(pts, ::project, Color.White.copy(alpha = 0.35f), boneWidth * 0.32f)

        // Joints
        val jointRadius = boneWidth * 1.15f
        for (idx in Lm.KEY_JOINTS) {
            val p = pts.getOrNull(idx) ?: continue
            if (p.visibility < 0.25f) continue
            val o = project(p)
            drawCircle(color.copy(alpha = 0.25f), jointRadius * 2.1f, o)
            drawCircle(color, jointRadius, o)
            drawCircle(Color.White.copy(alpha = 0.9f), jointRadius * 0.38f, o)
        }

        // Elbow emphasis: the joint the whole rep is judged on.
        for (idx in listOf(Lm.L_ELBOW, Lm.R_ELBOW)) {
            val p = pts.getOrNull(idx) ?: continue
            if (p.visibility < 0.25f) continue
            val o = project(p)
            drawCircle(
                color = color,
                radius = jointRadius * 2.4f,
                center = o,
                style = Stroke(width = boneWidth * 0.45f)
            )
        }
    }
}

private fun DrawScope.drawBones(
    pts: List<P3>,
    project: (P3) -> Offset,
    color: Color,
    width: Float
) {
    for ((a, b) in Lm.CONNECTIONS) {
        val pa = pts.getOrNull(a) ?: continue
        val pb = pts.getOrNull(b) ?: continue
        if (pa.visibility < 0.25f || pb.visibility < 0.25f) continue
        drawLine(
            color = color,
            start = project(pa),
            end = project(pb),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * k,
        green = from.green + (to.green - from.green) * k,
        blue = from.blue + (to.blue - from.blue) * k,
        alpha = from.alpha + (to.alpha - from.alpha) * k
    )
}
