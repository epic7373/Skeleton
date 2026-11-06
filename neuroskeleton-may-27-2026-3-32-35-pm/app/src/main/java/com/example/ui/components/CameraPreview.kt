package com.example.ui.components

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.viewmodel.BodySkeleton
import com.example.ui.viewmodel.TrackedJoint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPoseUpdated: (BodySkeleton?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val options = remember {
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    }
    val poseDetector = remember { PoseDetection.getClient(options) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, PoseAnalyzer(poseDetector) { jointsList, imgWidth, imgHeight ->
                    // Map detected joints
                    val mappedJoints = mutableMapOf<String, TrackedJoint>()
                    for (landmark in jointsList) {
                        val name = getJointName(landmark.landmarkType) ?: continue
                        
                        // Mirrored horizontal tracking for natural front camera look
                        var xNorm = landmark.position.x / imgWidth
                        val yNorm = landmark.position.y / imgHeight
                        xNorm = 1.0f - xNorm // mirror horizontal
                        
                        mappedJoints[name] = TrackedJoint(
                            name = name,
                            x = xNorm,
                            y = yNorm,
                            z = landmark.position3D.z / imgWidth, // Approximate relative depth
                            confidence = landmark.inFrameLikelihood
                        )
                    }

                    // Synthesized virtual joints for better connectivity
                    val lShoulder = mappedJoints["l_shoulder"]
                    val rShoulder = mappedJoints["r_shoulder"]
                    if (lShoulder != null && rShoulder != null) {
                        // Neck is mid-point of shoulders
                        val neckX = (lShoulder.x + rShoulder.x) / 2f
                        val neckY = (lShoulder.y + rShoulder.y) / 2f
                        mappedJoints["neck"] = TrackedJoint("neck", neckX, neckY, 0f, 0.99f)

                        // Head can be derived from nose or offset upwards
                        val nose = jointsList.find { it.landmarkType == PoseLandmark.NOSE }
                        if (nose != null) {
                            var headX = nose.position.x / imgWidth
                            headX = 1f - headX
                            val headY = nose.position.y / imgHeight
                            mappedJoints["head"] = TrackedJoint("head", headX, headY, 0f, 0.99f)
                        } else {
                            val headX = neckX
                            val headY = (neckY - 0.08f).coerceAtLeast(0f)
                            mappedJoints["head"] = TrackedJoint("head", headX, headY, 0f, 0.85f)
                        }

                        // Torso point
                        val lHip = mappedJoints["l_hip"]
                        val rHip = mappedJoints["r_hip"]
                        if (lHip != null && rHip != null) {
                            val hipX = (lHip.x + rHip.x) / 2f
                            val hipY = (lHip.y + rHip.y) / 2f
                            mappedJoints["pelvis"] = TrackedJoint("pelvis", hipX, hipY, 0f, 0.99f)

                            val midX = (neckX + hipX) / 2f
                            val midY = (neckY + hipY) / 2f
                            mappedJoints["torso_mid"] = TrackedJoint("torso_mid", midX, midY, 0f, 0.99f)
                        }
                    }

                    if (mappedJoints.isNotEmpty()) {
                        onPoseUpdated(BodySkeleton(joints = mappedJoints))
                    } else {
                        onPoseUpdated(null)
                    }
                })

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseDetector.close()
        }
    }
}

private class PoseAnalyzer(
    private val poseDetector: com.google.mlkit.vision.pose.PoseDetector,
    private val onPoseDetected: (List<PoseLandmark>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // For correct orientation ratios, width/height must account for rotation
            val rotation = imageProxy.imageInfo.rotationDegrees
            val isRotated = rotation == 90 || rotation == 270
            val width = if (isRotated) imageProxy.height else imageProxy.width
            val height = if (isRotated) imageProxy.width else imageProxy.height

            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    val landmarks = pose.allPoseLandmarks
                    onPoseDetected(landmarks, width, height)
                }
                .addOnFailureListener { e ->
                    Log.e("PoseAnalyzer", "ML Kit extraction error", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

private fun getJointName(landmarkType: Int): String? {
    return when (landmarkType) {
        PoseLandmark.LEFT_SHOULDER -> "l_shoulder"
        PoseLandmark.RIGHT_SHOULDER -> "r_shoulder"
        PoseLandmark.LEFT_ELBOW -> "l_elbow"
        PoseLandmark.RIGHT_ELBOW -> "r_elbow"
        PoseLandmark.LEFT_WRIST -> "l_wrist"
        PoseLandmark.RIGHT_WRIST -> "r_wrist"
        PoseLandmark.LEFT_HIP -> "l_hip"
        PoseLandmark.RIGHT_HIP -> "r_hip"
        PoseLandmark.LEFT_KNEE -> "l_knee"
        PoseLandmark.RIGHT_KNEE -> "r_knee"
        PoseLandmark.LEFT_ANKLE -> "l_ankle"
        PoseLandmark.RIGHT_ANKLE -> "r_ankle"
        else -> null
    }
}
