package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CameraPreview
import com.example.ui.components.SkeletonOverlay
import com.example.ui.theme.*
import com.example.ui.viewmodel.NeuroViewModel
import com.example.ui.viewmodel.SystemStatus
import com.example.ui.viewmodel.TrackingMode
import com.example.ui.viewmodel.TrackedJoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainTrackerScreen(
    viewModel: NeuroViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemStatus by viewModel.status.collectAsState()
    val activeMode by viewModel.currentMode.collectAsState()
    val useSim by viewModel.useSimulation.collectAsState()
    val systemStats by viewModel.stats.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val showFaceGrid by viewModel.showFaceGrid.collectAsState()
    val activeLogs by viewModel.tacticalLogs.collectAsState()
    val snapshots by viewModel.capturedAnalyses.collectAsState()

    val liveSkeleton by viewModel.realtimeSkeleton.collectAsState()
    val simSkeleton by viewModel.simulatedSkeleton.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Setup physical terminal Tone Generator
    val synthesizer = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            null
        }
    }

    fun playSynthTone(toneId: Int, duration: Int = 110) {
        if (!soundEnabled) return
        coroutineScope.launch {
            try {
                synthesizer?.startTone(toneId, duration)
            } catch (e: Exception) {
                // Safe handle
            }
        }
    }

    // Permission state mapping
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Animated scanline offset
    val infiniteTransition = rememberInfiniteTransition(label = "Scanlines")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanlineOffset"
    )

    // Matrix background grid opacity pulse
    val gridAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GridAlpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Frosted Glass Premium Cosmic Atmospheric Background with Cyber Grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Base background fill (solid #050505 to match design HTML)
                        drawRect(color = Color(0xFF050505))

                        // 1. Blue-900 background gradient (from-transparent via-blue-900/20 to-black)
                        val verticalGlow = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x231E3A8A), // Blue-900/20
                                Color(0xFF000000)  // Black
                            )
                        )
                        drawRect(brush = verticalGlow, alpha = 0.45f)

                        // 2. Camera Simulation Radial Glow at 50% width and 40% height (rgba(0,102,255,0.15))
                        val centralGlow = Brush.radialGradient(
                            colors = listOf(
                                Color(0x230066FF), // rgba(0, 102, 255, 0.15)
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.4f),
                            radius = size.width * 0.85f
                        )
                        drawRect(brush = centralGlow)

                        // 3. Cyber/Biometric monitor grid lines
                        val verticalGaps = 35.dp.toPx()
                        var currentV = 0f
                        while (currentV < size.height) {
                            drawLine(
                                color = GridColor.copy(alpha = gridAlpha),
                                start = Offset(0f, currentV),
                                end = Offset(size.width, currentV),
                                strokeWidth = 1f
                            )
                            currentV += verticalGaps
                        }

                        val horizontalGaps = 35.dp.toPx()
                        var currentH = 0f
                        while (currentH < size.width) {
                            drawLine(
                                color = GridColor.copy(alpha = gridAlpha),
                                start = Offset(currentH, 0f),
                                end = Offset(currentH, size.height),
                                strokeWidth = 1f
                            )
                            currentH += horizontalGaps
                        }
                    }
                    .testTag("cyber_grid_bg")
            )

            if (cameraPermissionState.status.isGranted || useSim) {
                // Interactive Camera Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("interactive_tracker_viewport")
                ) {
                    if (!useSim) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize()
                        ) { actualSkeleton ->
                            viewModel.setRealtimeSkeleton(actualSkeleton)
                        }
                    } else {
                        // Simulated dark camera preview panel inside simulation state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(CyberSlateCard, CyberBlack),
                                        radius = 1200f
                                    )
                                )
                        )
                    }

                    // Simulated Thermal Vision Layer
                    if (activeMode == TrackingMode.THERMAL_CYBER_SCAN) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            ThermalPurple.copy(alpha = 0.35f),
                                            ThermalBlue.copy(alpha = 0.2f),
                                            ThermalGreen.copy(alpha = 0.1f),
                                            ThermalYellow.copy(alpha = 0.15f),
                                            ThermalRed.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                    }

                    // Render Skeletal lines & tracking nodes overlay
                    val activeSkeleton = if (useSim) simSkeleton else liveSkeleton
                    SkeletonOverlay(
                        skeleton = activeSkeleton,
                        mode = activeMode,
                        modifier = Modifier.fillMaxSize().testTag("skeletal_rendering_overlay")
                    )

                    // Virtual Face grid dots
                    if (showFaceGrid && activeSkeleton != null) {
                        val head = activeSkeleton.joints["head"]
                        if (head != null) {
                            drawFaceGridHUD(head, activeMode)
                        }
                    }

                    // Live digital overlay scanning bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .offset(y = scanLineY.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        if (activeMode == TrackingMode.THERMAL_CYBER_SCAN) ThermalRed else NeonCyan,
                                        Color.Transparent
                                    )
                                )
                            )
                            .testTag("moving_raster_scanner")
                    )

                    // HUD Corner Brackets overlay from Frosted Glass theme
                    // Top-Left corner bracket
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 40.dp)
                            .size(32.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset.Zero,
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset.Zero,
                                    end = Offset(0f, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                    )
                    // Top-Right corner bracket
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp, top = 40.dp)
                            .size(32.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                    )
                    // Bottom-Left corner bracket
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 40.dp)
                            .size(32.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                    )
                    // Bottom-Right corner bracket
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 40.dp)
                            .size(32.dp)
                            .drawBehind {
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x8022D3EE),
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                    )

                    // Floating Biometric Data Tags from Design template
                    // Upper-Right Data Tag
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 40.dp, top = 110.dp)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0x4D22D3EE), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ARM_L_EXT: 172°",
                            color = Color(0xFF22D3EE),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Lower-Left Data Tag with dynamic status sync
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 40.dp, bottom = 110.dp)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0x4D22D3EE), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "POS_LOCK: ${if (systemStatus == SystemStatus.LOCK_CONFIRMED) "STABLE" else "ACQUIRING"}",
                            color = Color(0xFF22D3EE),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Cybernetic HUD Controls Dashboard overlying viewport
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Top Bar Header styled in premium glassmorphism
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x5903050C), RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x6622D3EE), // Cyan high-light
                                        Color(0x13FFFFFF)  // Subtle fallback transparent white
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Live pulsing status marker dot matching design HTML live indicator
                                val statusColor = when (systemStatus) {
                                    SystemStatus.INITIALIZING -> NeonYellow
                                    SystemStatus.SCANNING -> NeonBlue
                                    SystemStatus.LOCK_CONFIRMED -> NeonCyan
                                    SystemStatus.CALIBRATING -> NeonPurple
                                    SystemStatus.SYSTEM_ALERT -> NeonRed
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NEUROSKELETON v4.2 //", 
                                    color = Color(0xFF22D3EE),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.testTag("dashboard_title")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when(systemStatus) {
                                        SystemStatus.INITIALIZING -> "INT-INIT"
                                        SystemStatus.SCANNING -> "SEARCHING"
                                        SystemStatus.LOCK_CONFIRMED -> "LOCKED"
                                        SystemStatus.CALIBRATING -> "CALIBRATING"
                                        SystemStatus.SYSTEM_ALERT -> "THERM-LOAD"
                                    },
                                    color = statusColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("telemetry_status_badge")
                                )
                            }
                            Text(
                                "SYSTEM_LIVE // BODY_TRACK", 
                                color = CyberGray, 
                                fontSize = 9.sp, 
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        // Simulator Source Switcher styled as sleek frosted button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (useSim) Color(0x2B22D3EE) else Color(0x1BFFFFFF))
                                .border(
                                    1.dp, 
                                    if (useSim) Color(0x6622D3EE) else Color(0x33FFFFFF), 
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    playSynthTone(ToneGenerator.TONE_PROP_BEEP)
                                    viewModel.toggleSimulation(!useSim)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("toggle_simulation_button")
                        ) {
                            Icon(
                                imageVector = if (useSim) Icons.Default.SmartToy else Icons.Default.Videocam,
                                contentDescription = "Switch Input Source",
                                tint = if (useSim) Color(0xFF22D3EE) else GlowWhite,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (useSim) "DEMO ACTIVE" else "LIVE SENSOR",
                                color = if (useSim) Color(0xFF22D3EE) else GlowWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode tabs selectors styled in premium glassmorphism
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x40000000), RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0x3322D3EE), Color(0x13FFFFFF))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TrackingMode.values().forEach { mode ->
                            val isSelected = activeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x2222D3EE) else Color(0x14FFFFFF))
                                    .border(
                                        width = 1.dp, 
                                        color = if (isSelected) Color(0x6622D3EE) else Color(0x1AFFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        playSynthTone(ToneGenerator.TONE_CDMA_PIP)
                                        viewModel.setMode(mode)
                                    }
                                    .padding(vertical = 12.dp)
                                    .testTag("mode_tab_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name.replace("_", " "),
                                    color = if (isSelected) Color(0xFF22D3EE) else Color(0x80FFFFFF),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Left Information Sidebar & Camera overlay diagnostics
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left Column: AI Diagnostics & Log metrics styled in frosted glass
                        Column(
                            modifier = Modifier
                                .width(125.dp)
                                .fillMaxHeight()
                                .background(Color(0x59000000), RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0x3B22D3EE), Color(0x0FFFFFFF))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                "STREAMS Log",
                                color = Color(0xFF22D3EE),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Box(modifier = Modifier.weight(1f)) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(activeLogs) { log ->
                                        Column {
                                            Row {
                                                Text(
                                                    "[${log.category}]", 
                                                    color = if (log.category == "SYS") NeonPurple else NeonBlue,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    log.timestamp.takeLast(7), 
                                                    color = CyberGray, 
                                                    fontSize = 7.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(
                                                log.value, 
                                                color = GlowWhite, 
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Middle spacer/translucent workspace for video streaming skeleton
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            // High contrast corner framing overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        0.5.dp, 
                                        if (systemStatus == SystemStatus.LOCK_CONFIRMED) NeonCyan.copy(alpha = 0.15f) else NeonBlue.copy(alpha = 0.1f)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Right Column: Telemetry Specs & Joint coordinates values styled in frosted glass
                        Column(
                            modifier = Modifier
                                .width(125.dp)
                                .fillMaxHeight()
                                .background(Color(0x59000000), RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0x3B22D3EE), Color(0x0FFFFFFF))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                "INTELLIGENCE",
                                color = Color(0xFF22D3EE),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Joint coordinate status stream
                            Text(
                                "NODES MONITOR",
                                color = CyberGray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                val activeSkeleton = if (useSim) simSkeleton else liveSkeleton
                                if (activeSkeleton != null && activeSkeleton.joints.isNotEmpty()) {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val itemsList = activeSkeleton.joints.toList().take(5)
                                        items(itemsList) { (key, joint) ->
                                            Column {
                                                Text(
                                                    key.uppercase(), 
                                                    color = NeonYellow, 
                                                    fontSize = 7.sp, 
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    String.format("X %.2f | Y %.2f", joint.x, joint.y),
                                                    color = GlowWhite,
                                                    fontSize = 7.5.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    "C: ${(joint.confidence * 100).toInt()}%",
                                                    color = CyberGray,
                                                    fontSize = 7.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "NO BIOMETRICS\nDETECTED", 
                                            color = NeonRed.copy(alpha = 0.8f), 
                                            fontSize = 8.sp, 
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0x2B22D3EE), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Diagnostics list
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                TelemetryBar("LATENCY", "${systemStats.latencyMs}ms", systemStats.latencyMs / 15f)
                                TelemetryBar("ACCURACY", "${systemStats.trackingEfficiency.toInt()}%", systemStats.trackingEfficiency / 100f)
                                TelemetryBar("FPS", "${systemStats.framesPerSecond}", systemStats.framesPerSecond / 60f)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0x2B22D3EE), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Biometric summaries from design aside specs
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("HEART RATE", color = CyberGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("72 BPM", color = Color(0xFF22D3EE), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("O2 LEVEL", color = CyberGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("98%", color = Color(0xFF22D3EE), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("JOINTS LOCK", color = CyberGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                    Text("${systemStats.activeNodesCount}/18", color = Color(0xFF22D3EE), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Diagnostics telemetry panel styled in frosted glass
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x5903050C), RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0x3322D3EE), Color(0x13FFFFFF))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "CALIBRATED KINETIC STATUS: ${systemStats.activeNodesCount} NODES ACTIVE", 
                                color = GlowWhite, 
                                fontSize = 9.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "STABILITY INTEGRITY DETECTED: ${systemStats.targetIntegrity.toInt()}%",
                                color = if (systemStats.targetIntegrity > 75) Color(0xFF22D3EE) else NeonRed,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Export snapshots capture styled as sleek cyber lock button
                        Button(
                            onClick = {
                                playSynthTone(ToneGenerator.TONE_PROP_BEEP, 300)
                                viewModel.exportTacticalCapture()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF1744)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .border(1.dp, NeonRed, RoundedCornerShape(8.dp))
                                .testTag("capture_analysis_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Export Tactical Capture",
                                tint = GlowWhite,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "CAPTURE ANALYST", 
                                color = GlowWhite, 
                                fontSize = 9.sp, 
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Controller Options (Sound, Grid toggle, captured lists etc)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Sound button
                            ActionButton(
                                iconSelected = Icons.Default.VolumeUp,
                                iconUnselected = Icons.Default.VolumeOff,
                                label = "SYNTH CHIPS",
                                isActive = soundEnabled,
                                modifier = Modifier.testTag("sound_toggle")
                            ) {
                                viewModel.toggleSound()
                                if (!soundEnabled) {
                                    // sound status updated
                                }
                            }

                            // Face grid button
                            ActionButton(
                                iconSelected = Icons.Default.GridOn,
                                iconUnselected = Icons.Default.GridOff,
                                label = "BIOMETRICS DOTS",
                                isActive = showFaceGrid,
                                modifier = Modifier.testTag("face_grid_toggle")
                            ) {
                                playSynthTone(ToneGenerator.TONE_PROP_BEEP)
                                viewModel.toggleFaceGrid()
                            }
                        }

                        // Captured analysis history snapshot carousel
                        if (snapshots.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberSlate)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = "Snapshots", tint = NeonCyan, modifier = Modifier.size(10.dp))
                                Text(
                                    "${snapshots.size} SNAPS SECURED: ${snapshots.first()}", 
                                    color = NeonCyan, 
                                    fontSize = 7.5.sp, 
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Interactive Sci-Fi Permission Request Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyberSlate, CyberBlack),
                                radius = 900f
                            )
                        )
                        .padding(24.dp)
                        .testTag("permissions_request_panel"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(TranslucentBlack, RoundedCornerShape(12.dp))
                            .border(1.5.dp, NeonBlue, RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Access",
                            tint = NeonRed,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("secure_shield_icon")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "BIOMETRIC SENSOR ACCESS",
                            color = GlowWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NeuroSkeleton requires real-time camera tracking authorization to render the digital bio-skeleton node overlay.",
                            color = CyberGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                cameraPermissionState.launchPermissionRequest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("grant_camera_permission_button")
                        ) {
                            Text(
                                "GRANT SCANNER ACCESS",
                                color = CyberBlack,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bypass permission to Demo Simulator button
                        TextButton(
                            onClick = {
                                viewModel.toggleSimulation(true)
                                viewModel.setMode(TrackingMode.HUD_VISION)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan),
                            modifier = Modifier.testTag("demo_simulator_bypass_button")
                        ) {
                            Text(
                                "OR BYPASS TO DEMO SIMULATOR MODEL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryBar(label: String, value: String, fraction: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = CyberGray, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text(value, color = NeonCyan, fontSize = 7.1.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(NeonCyan, RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
fun ActionButton(
    iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    iconUnselected: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0x2B22D3EE) else Color(0x14FFFFFF))
            .border(
                width = 1.dp,
                color = if (isActive) Color(0x6622D3EE) else Color(0x1DFFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isActive) iconSelected else iconUnselected,
            contentDescription = label,
            tint = if (isActive) NeonCyan else CyberGray,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            color = if (isActive) NeonCyan else CyberGray,
            fontSize = 7.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

// Function that draws face matrix nodes on the facial region
@Composable
fun BoxScope.drawFaceGridHUD(headJoint: TrackedJoint, mode: TrackingMode) {
    // Generate a concentric biometric coordinate tracking circle on head
    val pulseTransition = rememberInfiniteTransition(label = "FacialGrid")
    val gridAngle by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "GridAngle"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("face_lockon_hud")
    ) {
        val centerX = headJoint.x * this.size.width
        val centerY = headJoint.y * this.size.height
        val center = Offset(centerX, centerY)

        val trackerColor = when (mode) {
            TrackingMode.THERMAL_CYBER_SCAN -> ThermalYellow
            else -> NeonCyan
        }

        // Concentric scanning rings
        drawCircle(
            color = trackerColor.copy(alpha = 0.3f),
            radius = 28f,
            center = center,
            style = Stroke(width = 1f)
        )

        // Facial crosshairs
        drawLine(
            color = trackerColor.copy(alpha = 0.6f),
            start = Offset(centerX - 35f, centerY),
            end = Offset(centerX + 35f, centerY),
            strokeWidth = 1f
        )
        drawLine(
            color = trackerColor.copy(alpha = 0.6f),
            start = Offset(centerX, centerY - 35f),
            end = Offset(centerX, centerY + 35f),
            strokeWidth = 1f
        )

        // Mini lock vertices
        val r = 18f
        for (i in 0 until 4) {
            val angleRad = Math.toRadians((gridAngle + (i * 90)).toDouble())
            val px = centerX + (cos(angleRad) * r).toFloat()
            val py = centerY + (sin(angleRad) * r).toFloat()
            
            drawCircle(
                color = trackerColor,
                radius = 2.5f,
                center = Offset(px, py)
            )
        }
    }
}
