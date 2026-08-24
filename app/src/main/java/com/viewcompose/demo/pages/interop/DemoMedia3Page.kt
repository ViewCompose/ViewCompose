package com.viewcompose

import android.graphics.Color
import androidx.media3.common.Player
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.media3.Media3PlayerView
import com.viewcompose.media3.Media3PlayerViewConfiguration
import com.viewcompose.media3.Media3ShowBuffering
import com.viewcompose.media3.Media3SurfaceType
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Interop · Media3", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewMedia3PlayerView() {
    Media3DemoPage()
}

/** Strict manual fixture for caller ownership and native video-Surface lifecycle behavior. */
internal fun UiTreeBuilder.Media3DemoPage(
    scenario: DemoScenarioSpec? = null,
    firstPlayer: Player? = null,
    secondPlayer: Player? = null,
) {
    val useSecondPlayer = remember { mutableStateOf(false) }
    val surfaceType = remember { mutableStateOf(Media3SurfaceType.SurfaceView) }
    val firstFrameCount = remember { mutableStateOf(0) }
    val player = if (useSecondPlayer.value) secondPlayer else firstPlayer
    val playerLabel = stringResource(
        if (useSecondPlayer.value) R.string.demo_media3_player_b else R.string.demo_media3_player_a,
    )
    val surfaceLabel = stringResource(
        when (surfaceType.value) {
            Media3SurfaceType.SurfaceView -> R.string.demo_media3_surface_view
            Media3SurfaceType.TextureView -> R.string.demo_media3_texture_view
            Media3SurfaceType.None -> error("The Demo never selects a surface-free player")
        },
    )

    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_media3_section_title),
        subtitle = stringResource(R.string.demo_media3_section_summary),
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_media3_switch_player),
                onClick = { useSecondPlayer.value = !useSecondPlayer.value },
                modifier = Modifier
                    .weight(1f)
                    .media3ScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_media3_switch_surface),
                onClick = {
                    surfaceType.value = when (surfaceType.value) {
                        Media3SurfaceType.SurfaceView -> Media3SurfaceType.TextureView
                        Media3SurfaceType.TextureView -> Media3SurfaceType.SurfaceView
                        Media3SurfaceType.None -> Media3SurfaceType.SurfaceView
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .media3ScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
        }
        Button(
            text = stringResource(R.string.demo_media3_reset),
            onClick = {
                useSecondPlayer.value = false
                surfaceType.value = Media3SurfaceType.SurfaceView
                firstFrameCount.value = 0
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .media3ScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        Text(
            text = stringResource(
                R.string.demo_media3_status,
                playerLabel,
                surfaceLabel,
                firstFrameCount.value,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .media3ScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Media3PlayerView(
            player = player,
            surfaceType = surfaceType.value,
            configuration = Media3PlayerViewConfiguration(
                useController = true,
                showBuffering = Media3ShowBuffering.WhenPlaying,
                shutterBackgroundColor = Color.BLACK,
                contentDescription = stringResource(R.string.demo_media3_content_description),
                keepScreenOn = true,
            ),
            onRenderedFirstFrame = { firstFrameCount.value++ },
            key = "demo_media3_player",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .backgroundColor(Color.BLACK)
                .media3ScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        Text(
            text = stringResource(R.string.demo_media3_manual_check),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

private fun Modifier.media3ScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
