package com.pushgate.app.ui.challenge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pushgate.app.block.AppLabels
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.pose.PoseAnalyzer
import com.pushgate.app.ui.theme.PushGateTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The gate itself: camera on, skeleton drawn, reps counted, time granted.
 *
 * Finishes with [RESULT_OK] only after the counter is satisfied. Everything else — back press,
 * giving up, camera denied — returns cancelled, and the caller keeps the app blocked.
 */
class ChallengeActivity : ComponentActivity() {

    private lateinit var controller: ChallengeController
    private lateinit var analysisExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val reps = intent.getIntExtra(EXTRA_REPS, 0)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)

        if (pkg.isNullOrBlank() || reps <= 0 || minutes <= 0) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        analysisExecutor = Executors.newSingleThreadExecutor()

        controller = ChallengeController(
            context = this,
            scope = lifecycleScope,
            packageName = pkg,
            appLabel = intent.getStringExtra(EXTRA_LABEL) ?: AppLabels.labelFor(this, pkg),
            repsRequired = reps,
            minutesOffered = minutes
        )

        setContent {
            PushGateTheme(forceDark = true) {
                ChallengeHost(
                    controller = controller,
                    analysisExecutor = analysisExecutor,
                    onSucceeded = {
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_MINUTES, minutes))
                        finish()
                    },
                    onGaveUp = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onOpenAppSettings = { openOwnAppSettings() }
                )
            }
        }
    }

    override fun onDestroy() {
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
        GuardBypass.close()
        super.onDestroy()
    }

    private fun openOwnAppSettings() {
        // Opening our own App Info page would normally trip the Strict Mode guard, so declare the
        // detour first.
        GuardBypass.open(minutes = 3, why = "granting camera permission")
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_LABEL = "label"
        const val EXTRA_REPS = "reps"
        const val EXTRA_MINUTES = "minutes"

        fun intent(context: Context, pkg: String, label: String, reps: Int, minutes: Int): Intent =
            Intent(context, ChallengeActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_REPS, reps)
                .putExtra(EXTRA_MINUTES, minutes)
    }
}

@Composable
private fun ChallengeHost(
    controller: ChallengeController,
    analysisExecutor: ExecutorService,
    onSucceeded: () -> Unit,
    onGaveUp: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    var askedOnce = remember { false }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.begin() else controller.onCameraDenied()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            controller.begin()
        } else if (!askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Back must not be a free exit: it counts as giving up, and the app stays blocked.
    BackHandler(enabled = true) {
        controller.abandon()
        onGaveUp()
    }

    ChallengeScreen(
        controller = controller,
        cameraContent = { modifier ->
            CameraSurface(
                controller = controller,
                analysisExecutor = analysisExecutor,
                modifier = modifier
            )
        },
        onGiveUp = {
            controller.abandon()
            onGaveUp()
        },
        onContinue = onSucceeded,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenAppSettings = onOpenAppSettings
    )
}

@Composable
private fun CameraSurface(
    controller: ChallengeController,
    analysisExecutor: ExecutorService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val useFront = controller.mirrored
    val attempt = controller.cameraAttempt

    DisposableEffect(useFront, attempt) {
        val analyzer = PoseAnalyzer(
            context = context,
            onFrame = { frame -> controller.onFrame(frame) },
            onError = { msg -> controller.onPoseError(msg) }
        )

        var boundProvider: ProcessCameraProvider? = null
        var cancelled = false

        // Model load and camera open both block; doing them on the main thread freezes the
        // challenge screen for a second or two right when the user is trying to get into position.
        analysisExecutor.execute {
            analyzer.initialize()
            if (cancelled) return@execute

            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                if (cancelled) return@addListener

                val attempted = runCatching { providerFuture.get() }
                val provider = attempted.getOrNull()
                if (provider == null) {
                    val why = attempted.exceptionOrNull()
                    // Report the real cause. "Camera unavailable" on a phone with a working camera
                    // is almost always CameraX failing to initialise, not missing hardware.
                    controller.onPoseError(
                        buildString {
                            append("CameraX could not start.

")
                            append(
                                why?.let { "${it.javaClass.simpleName}: ${it.message ?: "no message"}" }
                                    ?: "The camera provider returned nothing."
                            )
                        }
                    )
                    return@addListener
                }
                boundProvider = provider

                val hasFront = runCatching {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                }.getOrDefault(false)
                val hasBack = runCatching {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                }.getOrDefault(false)

                if (!hasFront && !hasBack) {
                    controller.onPoseError("This device reports no usable camera.")
                    return@addListener
                }

                // Honour the preference only if that lens actually exists.
                val selector = when {
                    useFront && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    !useFront && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> CameraSelector.DEFAULT_FRONT_CAMERA
                }
                controller.onLensResolved(
                    front = selector == CameraSelector.DEFAULT_FRONT_CAMERA,
                    canFlip = hasFront && hasBack
                )

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(resolution)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                }.onFailure { t ->
                    // Binding preview + analysis together exceeds what a few low-end devices
                    // support. Preview alone is useless for counting, so drop the preview instead.
                    val fallback = runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, analysis)
                    }
                    if (fallback.isFailure) {
                        controller.onPoseError(
                            "Could not open the camera.

" +
                                "${t.javaClass.simpleName}: ${t.message ?: "no message"}"
                        )
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            cancelled = true
            runCatching { boundProvider?.unbindAll() }
            analyzer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
