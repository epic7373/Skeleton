package com.example.ui.viewmodel

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class TrackingMode {
    SKELETON_ONLY,
    HUD_VISION,
    THERMAL_CYBER_SCAN
}

enum class SystemStatus {
    INITIALIZING,
    SCANNING,
    LOCK_CONFIRMED,
    CALIBRATING,
    SYSTEM_ALERT
}

data class TrackedJoint(
    val name: String,
    val x: Float, // 0f to 1f
    val y: Float, // 0f to 1f
    val z: Float = 0f,
    val confidence: Float = 1f
)

data class BodySkeleton(
    val joints: Map<String, TrackedJoint> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TacticalLog(
    val timestamp: String,
    val category: String,
    val value: String
)

data class TrackingStats(
    val latencyMs: Long = 12,
    val trackingEfficiency: Float = 99.4f,
    val framesPerSecond: Int = 60,
    val activeNodesCount: Int = 12,
    val targetIntegrity: Float = 100.0f
)

class NeuroViewModel : ViewModel() {

    private val _currentMode = MutableStateFlow(TrackingMode.HUD_VISION)
    val currentMode: StateFlow<TrackingMode> = _currentMode.asStateFlow()

    private val _status = MutableStateFlow(SystemStatus.INITIALIZING)
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    private val _realtimeSkeleton = MutableStateFlow<BodySkeleton?>(null)
    val realtimeSkeleton: StateFlow<BodySkeleton?> = _realtimeSkeleton.asStateFlow()

    private val _simulatedSkeleton = MutableStateFlow(BodySkeleton())
    val simulatedSkeleton: StateFlow<BodySkeleton> = _simulatedSkeleton.asStateFlow()

    private val _useSimulation = MutableStateFlow(true)
    val useSimulation: StateFlow<Boolean> = _useSimulation.asStateFlow()

    private val _stats = MutableStateFlow(TrackingStats())
    val stats: StateFlow<TrackingStats> = _stats.asStateFlow()

    private val _tacticalLogs = MutableStateFlow<List<TacticalLog>>(emptyList())
    val tacticalLogs: StateFlow<List<TacticalLog>> = _tacticalLogs.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _showFaceGrid = MutableStateFlow(true)
    val showFaceGrid: StateFlow<Boolean> = _showFaceGrid.asStateFlow()

    private val _capturedAnalyses = MutableStateFlow<List<String>>(emptyList())
    val capturedAnalyses: StateFlow<List<String>> = _capturedAnalyses.asStateFlow()

    private var animTime = 0f

    // Available joints in NeuroSkeleton
    val jointNames = listOf(
        "head", "neck", 
        "l_shoulder", "r_shoulder", 
        "l_elbow", "r_elbow", 
        "l_wrist", "r_wrist", 
        "torso_mid", "pelvis",
        "l_hip", "r_hip", 
        "l_knee", "r_knee", 
        "l_ankle", "r_ankle"
    )

    init {
        startSystemSimulationLoop()
        generateLogsLoop()
    }

    fun setMode(mode: TrackingMode) {
        _currentMode.value = mode
        triggerStatusUpdate(mode)
    }

    private fun triggerStatusUpdate(mode: TrackingMode) {
        viewModelScope.launch {
            _status.value = SystemStatus.CALIBRATING
            delay(800)
            _status.value = when(mode) {
                TrackingMode.SKELETON_ONLY -> SystemStatus.SCANNING
                TrackingMode.HUD_VISION -> SystemStatus.LOCK_CONFIRMED
                TrackingMode.THERMAL_CYBER_SCAN -> SystemStatus.SYSTEM_ALERT
            }
        }
    }

    fun toggleSimulation(enable: Boolean) {
        _useSimulation.value = enable
    }

    fun toggleSound() {
        _soundEnabled.value = !_soundEnabled.value
    }

    fun toggleFaceGrid() {
        _showFaceGrid.value = !_showFaceGrid.value
    }

    fun setRealtimeSkeleton(skeleton: BodySkeleton?) {
        _realtimeSkeleton.value = skeleton
        if (skeleton != null && !_useSimulation.value) {
            // Compute real stats based on detection
            val detectedCount = skeleton.joints.size
            if (detectedCount > 4) {
                _status.value = SystemStatus.LOCK_CONFIRMED
            } else {
                _status.value = SystemStatus.SCANNING
            }
            _stats.value = TrackingStats(
                latencyMs = (8..15).random().toLong(),
                trackingEfficiency = 95.0f + (Random.nextFloat() * 4.9f),
                framesPerSecond = (55..60).random(),
                activeNodesCount = detectedCount,
                targetIntegrity = (calculatedIntegrity(skeleton))
            )
        }
    }

    private fun calculatedIntegrity(skeleton: BodySkeleton): Float {
        val totalJoints = jointNames.size.toFloat()
        val detected = skeleton.joints.size.toFloat()
        return (detected / totalJoints * 100f).coerceAtMost(100f)
    }

    fun exportTacticalCapture() {
        val id = "SCAN-" + (1000..9999).random() + "-NEURO"
        val newList = _capturedAnalyses.value.toMutableList()
        newList.add(0, id)
        _capturedAnalyses.value = newList
    }

    private fun Random.randomFloat(): Float {
        return nextFloat()
    }

    private fun startSystemSimulationLoop() {
        viewModelScope.launch {
            while (true) {
                animTime += 0.05f
                
                // Update simulated human body joints (simulating a smooth cyberpunk waving/breathing action)
                // Center body coordinates around x = 0.5, y = 0.5
                val breathingY = sin(animTime) * 0.015f
                val limbSwingLeft = sin(animTime * 1.5f) * 0.06f
                val limbSwingRight = cos(animTime * 1.5f) * 0.06f

                // Base spine coordinates
                val headX = 0.5f + sin(animTime * 0.5f) * 0.02f
                val headY = 0.22f + breathingY
                
                val neckX = 0.5f + sin(animTime * 0.5f) * 0.015f
                val neckY = 0.28f + breathingY

                val shoulderLeftX = 0.38f
                val shoulderLeftY = 0.33f + breathingY

                val shoulderRightX = 0.62f
                val shoulderRightY = 0.33f + breathingY

                // Left arm waving/moving
                val elbowLeftX = 0.30f + sin(animTime) * 0.03f
                val elbowLeftY = 0.45f + costOffset(animTime) * 0.02f
                
                val wristLeftX = 0.22f + limbSwingLeft
                val wristLeftY = 0.55f + sin(animTime) * 0.04f

                // Right arm waving/moving
                val elbowRightX = 0.70f + cos(animTime) * 0.03f
                val elbowRightY = 0.45f + sinOffset(animTime) * 0.02f
                
                val wristRightX = 0.78f + limbSwingRight
                val wristRightY = 0.55f + cos(animTime) * 0.04f

                // Torso and Hips
                val torsoMidX = 0.5f
                val torsoMidY = 0.48f + breathingY

                val pelvisX = 0.5f
                val pelvisY = 0.58f

                val hipLeftX = 0.42f
                val hipLeftY = 0.61f
                
                val hipRightX = 0.58f
                val hipRightY = 0.61f

                // Legs moving in a breathing/tracking pose
                val kneeLeftX = 0.40f + sin(animTime * 0.8f) * 0.015f
                val kneeLeftY = 0.76f + breathingY
                
                val ankleLeftX = 0.39f + sin(animTime * 0.8f) * 0.02f
                val ankleLeftY = 0.90f

                val kneeRightX = 0.60f + cos(animTime * 0.8f) * 0.015f
                val kneeRightY = 0.76f + breathingY

                val ankleRightX = 0.61f + cos(animTime * 0.8f) * 0.02f
                val ankleRightY = 0.90f

                val simulatedMap = mapOf(
                    "head" to TrackedJoint("head", headX, headY, sin(animTime) * 0.1f, 0.99f),
                    "neck" to TrackedJoint("neck", neckX, neckY, 0f, 0.99f),
                    "l_shoulder" to TrackedJoint("l_shoulder", shoulderLeftX, shoulderLeftY, 0.05f, 0.98f),
                    "r_shoulder" to TrackedJoint("r_shoulder", shoulderRightX, shoulderRightY, -0.05f, 0.98f),
                    "l_elbow" to TrackedJoint("l_elbow", elbowLeftX, elbowLeftY, 0.11f, 0.95f),
                    "r_elbow" to TrackedJoint("r_elbow", elbowRightX, elbowRightY, -0.11f, 0.95f),
                    "l_wrist" to TrackedJoint("l_wrist", wristLeftX, wristLeftY, 0.2f, 0.92f),
                    "r_wrist" to TrackedJoint("r_wrist", wristRightX, wristRightY, -0.2f, 0.92f),
                    "torso_mid" to TrackedJoint("torso_mid", torsoMidX, torsoMidY, 0f, 0.99f),
                    "pelvis" to TrackedJoint("pelvis", pelvisX, pelvisY, 0f, 0.99f),
                    "l_hip" to TrackedJoint("l_hip", hipLeftX, hipLeftY, 0.02f, 0.97f),
                    "r_hip" to TrackedJoint("r_hip", hipRightX, hipRightY, -0.02f, 0.97f),
                    "l_knee" to TrackedJoint("l_knee", kneeLeftX, kneeLeftY, 0.08f, 0.96f),
                    "r_knee" to TrackedJoint("r_knee", kneeRightX, kneeRightY, -0.08f, 0.96f),
                    "l_ankle" to TrackedJoint("l_ankle", ankleLeftX, ankleLeftY, 0.12f, 0.90f),
                    "r_ankle" to TrackedJoint("r_ankle", ankleRightX, ankleRightY, -0.12f, 0.90f)
                )

                _simulatedSkeleton.value = BodySkeleton(joints = simulatedMap)

                if (_useSimulation.value) {
                    _stats.value = TrackingStats(
                        latencyMs = 1,
                        trackingEfficiency = 100f,
                        framesPerSecond = 60,
                        activeNodesCount = simulatedMap.size,
                        targetIntegrity = 100.0f
                    )
                    if (_status.value == SystemStatus.INITIALIZING || _status.value == SystemStatus.SCANNING) {
                        _status.value = SystemStatus.LOCK_CONFIRMED
                    }
                }

                delay(16) // ~60 FPS update
            }
        }
    }

    private fun sinOffset(time: Float): Float = sin(time)
    private fun costOffset(time: Float): Float = cos(time)

    private fun generateLogsLoop() {
        viewModelScope.launch {
            _status.value = SystemStatus.INITIALIZING
            delay(1500)
            _status.value = SystemStatus.SCANNING
            
            val categories = listOf("SYS", "NEURO", "TRACE", "KINETIC", "CORE")
            val rawMsgs = listOf(
                "Loading ML modules...",
                "Calibrating optical threshold",
                "Establishing frame capture rate: 60fps",
                "Analyzing kinetic nodes...",
                "Skeletal locking active",
                "Thermal scanners online",
                "Synapse routing running...",
                "Latency optimized below 12ms",
                "Body tracking angle calculated",
                "Facial scan landmarks locking",
                "Matrix overlay: dynamic Light",
                "Ready for holographic visualizer"
            )

            var index = 0
            while (true) {
                val stamp = String.format("%02d:%02d:%02d.%03d", 
                    (0..23).random(), (0..59).random(), (0..59).random(), (0..999).random()
                )
                val newLog = TacticalLog(
                    timestamp = stamp,
                    category = categories.random(),
                    value = rawMsgs[index % rawMsgs.size]
                )
                index++

                val currentList = _tacticalLogs.value.toMutableList()
                if (currentList.size > 25) {
                    currentList.removeAt(currentList.size -1)
                }
                currentList.add(0, newLog)
                _tacticalLogs.value = currentList

                delay((800..2500).random().toLong())
            }
        }
    }
}
