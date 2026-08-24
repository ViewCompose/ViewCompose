package com.viewcompose

import androidx.camera.lifecycle.ProcessCameraProvider
import com.viewcompose.camerax.CameraXLensFacing
import com.viewcompose.camerax.CameraXPreviewBindingState
import com.viewcompose.camerax.CameraXPreviewConfiguration
import com.viewcompose.camerax.CameraXPreviewFailureReason
import com.viewcompose.camerax.CameraXPreviewImplementationMode
import com.viewcompose.camerax.CameraXPreviewScaleType
import com.viewcompose.camerax.CameraXPreviewStreamState
import com.viewcompose.camerax.CameraXPreviewView
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Interop · CameraX", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCameraXView() {
    CameraXDemoPage(
        cameraProvider = null,
        providerFailure = null,
        permissionGranted = false,
        onRequestPermission = {},
    )
}

/** Physical-camera fixture with explicit permission and provider ownership. */
internal fun UiTreeBuilder.CameraXDemoPage(
    scenario: DemoScenarioSpec? = null,
    cameraProvider: ProcessCameraProvider?,
    providerFailure: String?,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    val lensFacing = remember { mutableStateOf(CameraXLensFacing.Back) }
    val implementationMode = remember {
        mutableStateOf(CameraXPreviewImplementationMode.Compatible)
    }
    val bindingState = remember { mutableStateOf(CameraXPreviewBindingState.Inactive) }
    val streamState = remember { mutableStateOf(CameraXPreviewStreamState.Idle) }
    val failureReason = remember { mutableStateOf<CameraXPreviewFailureReason?>(null) }

    val permissionLabel = stringResource(
        if (permissionGranted) {
            R.string.demo_camerax_permission_granted
        } else {
            R.string.demo_camerax_permission_missing
        },
    )
    val providerLabel = when {
        providerFailure != null -> stringResource(R.string.demo_camerax_provider_failed)
        cameraProvider != null -> stringResource(R.string.demo_camerax_provider_ready)
        else -> stringResource(R.string.demo_camerax_provider_loading)
    }
    val lensLabel = stringResource(
        if (lensFacing.value == CameraXLensFacing.Back) {
            R.string.demo_camerax_lens_back
        } else {
            R.string.demo_camerax_lens_front
        },
    )
    val implementationLabel = stringResource(
        if (implementationMode.value == CameraXPreviewImplementationMode.Performance) {
            R.string.demo_camerax_implementation_performance
        } else {
            R.string.demo_camerax_implementation_compatible
        },
    )
    val bindingLabel = when (bindingState.value) {
        CameraXPreviewBindingState.Inactive -> stringResource(R.string.demo_camerax_binding_inactive)
        CameraXPreviewBindingState.WaitingForProvider ->
            stringResource(R.string.demo_camerax_binding_waiting)
        CameraXPreviewBindingState.Bound -> stringResource(R.string.demo_camerax_binding_bound)
        CameraXPreviewBindingState.Failed -> stringResource(R.string.demo_camerax_binding_failed)
    }
    val streamLabel = if (streamState.value == CameraXPreviewStreamState.Streaming) {
        stringResource(R.string.demo_camerax_stream_streaming)
    } else {
        stringResource(R.string.demo_camerax_stream_idle)
    }
    val failureLabel = when (failureReason.value) {
        CameraXPreviewFailureReason.PermissionDenied ->
            stringResource(R.string.demo_camerax_failure_permission)
        CameraXPreviewFailureReason.CameraUnavailable ->
            stringResource(R.string.demo_camerax_failure_unavailable)
        CameraXPreviewFailureReason.ConflictingUseCases ->
            stringResource(R.string.demo_camerax_failure_conflict)
        CameraXPreviewFailureReason.UnsupportedProviderState ->
            stringResource(R.string.demo_camerax_failure_provider_state)
        CameraXPreviewFailureReason.Unknown -> stringResource(R.string.demo_camerax_failure_unknown)
        null -> stringResource(R.string.demo_camerax_failure_none)
    }

    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_camerax_section_title),
        subtitle = stringResource(R.string.demo_camerax_section_summary),
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_camerax_switch_lens),
                onClick = {
                    failureReason.value = null
                    lensFacing.value = if (lensFacing.value == CameraXLensFacing.Back) {
                        CameraXLensFacing.Front
                    } else {
                        CameraXLensFacing.Back
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .cameraXScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_camerax_switch_implementation),
                onClick = {
                    failureReason.value = null
                    implementationMode.value =
                        if (
                            implementationMode.value ==
                            CameraXPreviewImplementationMode.Performance
                        ) {
                            CameraXPreviewImplementationMode.Compatible
                        } else {
                            CameraXPreviewImplementationMode.Performance
                        }
                },
                modifier = Modifier
                    .weight(1f)
                    .cameraXScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
        }
        if (!permissionGranted) {
            Button(
                text = stringResource(R.string.demo_camerax_request_permission),
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
            )
        }
        Button(
            text = stringResource(R.string.demo_camerax_reset),
            onClick = {
                lensFacing.value = CameraXLensFacing.Back
                implementationMode.value = CameraXPreviewImplementationMode.Compatible
                bindingState.value = CameraXPreviewBindingState.Inactive
                streamState.value = CameraXPreviewStreamState.Idle
                failureReason.value = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .cameraXScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        Text(
            text = stringResource(
                R.string.demo_camerax_status,
                permissionLabel,
                providerLabel,
                lensLabel,
                implementationLabel,
                bindingLabel,
                streamLabel,
                failureLabel,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .cameraXScenarioTarget(scenario, DemoAutomationRole.State),
        )
        if (cameraProvider == null) {
            Surface(
                variant = SurfaceVariant.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .cameraXScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(
                    text = if (providerFailure == null) {
                        stringResource(R.string.demo_camerax_placeholder_loading)
                    } else {
                        stringResource(
                            R.string.demo_camerax_placeholder_provider_failed,
                            providerFailure,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        } else {
            CameraXPreviewView(
                cameraProvider = cameraProvider,
                lensFacing = lensFacing.value,
                implementationMode = implementationMode.value,
                configuration = CameraXPreviewConfiguration(
                    scaleType = if (
                        implementationMode.value == CameraXPreviewImplementationMode.Performance
                    ) {
                        CameraXPreviewScaleType.FitCenter
                    } else {
                        CameraXPreviewScaleType.FillCenter
                    },
                    contentDescription = stringResource(
                        R.string.demo_camerax_content_description,
                    ),
                ),
                onBindingStateChanged = { state ->
                    bindingState.value = state
                    if (state == CameraXPreviewBindingState.Bound) failureReason.value = null
                },
                onStreamStateChanged = { state -> streamState.value = state },
                onFailure = { failure -> failureReason.value = failure.reason },
                key = "demo-camerax-preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .cameraXScenarioTarget(scenario, DemoAutomationRole.Target),
            )
        }
        Text(
            text = stringResource(R.string.demo_camerax_manual_check),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun Modifier.cameraXScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
