package com.viewcompose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Dedicated application owner for CameraX permission and provider initialization. */
class CameraXActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_camerax_title

    private val providerState = mutableStateOf<ProcessCameraProvider?>(null)
    private val providerFailureState = mutableStateOf<String?>(null)
    private val permissionGrantedState = mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGrantedState.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        permissionGrantedState.value = hasCameraPermission()
        super.onCreate(savedInstanceState)
        resolveCameraProvider()
    }

    override fun onResume() {
        super.onResume()
        permissionGrantedState.value = hasCameraPermission()
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.CameraXDemoPage(
            scenario = checkNotNull(currentScenario()) {
                "CameraXActivity requires the registered CameraX scenario"
            },
            cameraProvider = providerState.value,
            providerFailure = providerFailureState.value,
            permissionGranted = permissionGrantedState.value,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }

    private fun resolveCameraProvider() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching(future::get)
                    .onSuccess { provider ->
                        providerFailureState.value = null
                        providerState.value = provider
                    }
                    .onFailure { error ->
                        providerState.value = null
                        providerFailureState.value = error.javaClass.simpleName
                    }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
