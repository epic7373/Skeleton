package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.*
import com.example.ui.viewmodel.BodySkeleton
import com.example.ui.viewmodel.TrackedJoint
import com.example.ui.viewmodel.TrackingMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkeletonOverlay(
    skeleton: BodySkeleton?,
    mode: TrackingMode,
    modifier: Modifier = Modifier
) {
    // Pulse animation for joints & HUD scanners
    val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotAngle"
    )

    Canvas(modifier = modifier) {
        if (skeleton == null || skeleton.joints.isEmpty()) {
            return@Canvas
        }

        val canvasW = size.width
        val canvasH = size.height

        val joints = skeleton.joints
        val pointMap = mutableMapOf<String, Offset>()
        
        // Map joints to current pixel coordinates
        joints.forEach { (name, joint) ->
            pointMap[name] = Offset(joint.x * canvasW, joint.y * canvasH)
        }

        // Color definitions based on mode
        val nodeColor: Color
        val lineColor: Color
        val glowColor: Color

        when (mode) {
            TrackingMode.SKELETON_ONLY -> {
                lineColor = NeonCyan
                nodeColor = GlowWhite
                glowColor = NeonCyan.copy(alpha = 0.6f)
            }
            TrackingMode.HUD_VISION -> {
                lineColor = NeonCyan
                nodeColor = NeonYellow
                glowColor = NeonBlue.copy(alpha = 0.5f)
            }
            TrackingMode.THERMAL_CYBER_SCAN -> {
                lineColor = ThermalRed
                nodeColor = ThermalYellow
                glowColor = ThermalYellow.copy(alpha = 0.45f)
            }
        }

        // Segment bones
        val bones = listOf(
            // Spine
            Pair("head", "neck"),
            Pair("neck", "torso_mid"),
            Pair("torso_mid", "pelvis"),
            // Shoulders & neck
            Pair("neck", "l_shoulder"),
            Pair("neck", "r_shoulder"),
            // Left Arm
            Pair("l_shoulder", "l_elbow"),
            Pair("l_elbow", "l_wrist"),
            // Right Arm
            Pair("r_shoulder", "r_elbow"),
            Pair("r_elbow", "r_wrist"),
            // Hip grid
            Pair("pelvis", "l_hip"),
            Pair("pelvis", "r_hip"),
            // Left leg
            Pair("l_hip", "l_knee"),
            Pair("l_knee", "l_ankle"),
            // Right leg
            Pair("r_hip", "r_knee"),
            Pair("r_knee", "r_ankle")
        )

        // Draw simulated thermal heat signature background if Thermal mode is selected
        if (mode == TrackingMode.THERMAL_CYBER_SCAN) {
            drawThermalHeatGlows(pointMap)
        }

        // 1. Draw Skeleton Lines
        bones.forEach { (start, end) ->
            val pStart = pointMap[start]
            val pEnd = pointMap[end]

            if (pStart != null && pEnd != null) {
                // Glow Backdrop (Fuzzy thick line)
                drawLine(
                    color = glowColor,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = 14f,
                    cap = StrokeCap.Round
                )
                
                // Tech secondary glow
                drawLine(
                    color = if (mode == TrackingMode.HUD_VISION) NeonBlue.copy(alpha = 0.4f) else lineColor.copy(alpha = 0.4f),
                    start = pStart,
                    end = pEnd,
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )

                // Crisp Core Line
                drawLine(
                    color = if (mode == TrackingMode.THERMAL_CYBER_SCAN) ThermalYellow else GlowWhite,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Draw Skeleton Joints/Nodes
        pointMap.forEach { (name, pos) ->
            val joint = joints[name] ?: return@forEach
            val jointConfidence = joint.confidence

            // Draw glowing core node
            drawCircle(
                color = glowColor,
                radius = 12f * pulseRatio,
                center = pos
            )
            
            drawCircle(
                color = nodeColor,
                radius = 5f,
                center = pos
            )

            // Draw scanning brackets on hands & head in HUD Vision mode
            if (mode == TrackingMode.HUD_VISION) {
                if (name == "l_wrist" || name == "r_wrist" || name == "head") {
                    drawHudTrackerLock(pos, name, rotAngle, pulseRatio * 15f)
                }
            }
            
            // Draw Thermal scan concentric heat radiation
            if (mode == TrackingMode.THERMAL_CYBER_SCAN && (name == "head" || name == "torso_mid")) {
                drawCircle(
                    color = ThermalRed.copy(alpha = 0.25f * pulseRatio),
                    radius = 35f,
                    center = pos,
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}

private fun DrawScope.drawThermalHeatGlows(points: Map<String, Offset>) {
    // Simulated Infrared thermal aura under major joints to make it super realistic
    val heatCenters = listOf("head", "torso_mid", "l_elbow", "r_elbow", "l_knee", "r_knee")
    
    heatCenters.forEach { name ->
        val pos = points[name] ?: return@forEach
        
        // Render concentric rings representing heat zones
        drawCircle(
            color = ThermalRed.copy(alpha = 0.14f),
            radius = 110f,
            center = pos
        )
        drawCircle(
            color = ThermalYellow.copy(alpha = 0.18f),
            radius = 70f,
            center = pos
        )
        drawCircle(
            color = ThermalGreen.copy(alpha = 0.22f),
            radius = 40f,
            center = pos
        )
        drawCircle(
            color = ThermalBlue.copy(alpha = 0.28f),
            radius = 20f,
            center = pos
        )
    }
}

private fun DrawScope.drawHudTrackerLock(center: Offset, label: String, rotAngle: Float, size: Float) {
    val lockSize = size.coerceIn(12f, 24f)
    
    // Draw outer rotation dashed ring
    drawCircle(
        color = NeonCyan,
        radius = lockSize * 1.5f,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Draw scanning text coordinates
    val paint = Paint().apply {
        color = NeonCyan.toArgb()
        textSize = 21f
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    val displayLabel = when(label) {
        "l_wrist" -> "L-NODE: ${center.x.toInt()},${center.y.toInt()}"
        "r_wrist" -> "R-NODE: ${center.x.toInt()},${center.y.toInt()}"
        "head" -> "SENSORY: ${center.x.toInt()},${center.y.toInt()}"
        else -> label.uppercase()
    }

    drawContext.canvas.nativeCanvas.drawText(
        displayLabel,
        center.x + (lockSize * 2f),
        center.y + 7f,
        paint
    )

    // Draw visual corner ticks representing tactical lock scope
    val ticksLength = 8f
    val offset = lockSize * 1.1f
    
    // Top-Left corner ticks
    drawLine(NeonCyan, Offset(center.x - offset, center.y - offset), Offset(center.x - offset + ticksLength, center.y - offset), 2.5f)
    drawLine(NeonCyan, Offset(center.x - offset, center.y - offset), Offset(center.x - offset, center.y - offset + ticksLength), 2.5f)

    // Top-Right corner ticks
    drawLine(NeonCyan, Offset(center.x + offset, center.y - offset), Offset(center.x + offset - ticksLength, center.y - offset), 2.5f)
    drawLine(NeonCyan, Offset(center.x + offset, center.y - offset), Offset(center.x + offset, center.y - offset + ticksLength), 2.5f)

    // Bottom-Left corner ticks
    drawLine(NeonCyan, Offset(center.x - offset, center.y + offset), Offset(center.x - offset + ticksLength, center.y + offset), 2.5f)
    drawLine(NeonCyan, Offset(center.x - offset, center.y + offset), Offset(center.x - offset, center.y + offset - ticksLength), 2.5f)

    // Bottom-Right corner ticks
    drawLine(NeonCyan, Offset(center.x + offset, center.y + offset), Offset(center.x + offset - ticksLength, center.y + offset), 2.5f)
    drawLine(NeonCyan, Offset(center.x + offset, center.y + offset), Offset(center.x + offset, center.y + offset - ticksLength), 2.5f)
}
