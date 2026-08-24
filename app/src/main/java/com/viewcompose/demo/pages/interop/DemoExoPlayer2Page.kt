@file:Suppress("DEPRECATION")

package com.viewcompose

import android.graphics.Color
import com.google.android.exoplayer2.Player
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.exoplayer2.ExoPlayerShowBuffering
import com.viewcompose.exoplayer2.ExoPlayerSurfaceType
import com.viewcompose.exoplayer2.ExoPlayerView
import com.viewcompose.exoplayer2.ExoPlayerViewConfiguration
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
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

@ViewComposePreview(name = "Interop · Legacy ExoPlayer", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewExoPlayer2View() {
    ExoPlayer2DemoPage()
}

/** Strict manual fixture for the frozen legacy namespace and native Surface lifecycle. */
internal fun UiTreeBuilder.ExoPlayer2DemoPage(
    scenario: DemoScenarioSpec? = null,
    firstPlayer: Player? = null,
    secondPlayer: Player? = null,
) {
    val useSecondPlayer = remember { mutableStateOf(false) }
    val surfaceType = remember { mutableStateOf(ExoPlayerSurfaceType.SurfaceView) }
    val firstFrameCount = remember { mutableStateOf(0) }
    val player = if (useSecondPlayer.value) secondPlayer else firstPlayer
    val playerLabel = stringResource(
        if (useSecondPlayer.value) R.string.demo_exoplayer2_player_b else R.string.demo_exoplayer2_player_a,
    )
    val surfaceLabel = stringResource(
        when (surfaceType.value) {
            ExoPlayerSurfaceType.SurfaceView -> R.string.demo_exoplayer2_surface_view
            ExoPlayerSurfaceType.TextureView -> R.string.demo_exoplayer2_texture_view
            ExoPlayerSurfaceType.None -> error("The Demo never selects a surface-free player")
        },
    )

    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_exoplayer2_section_title),
        subtitle = stringResource(R.string.demo_exoplayer2_section_summary),
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_exoplayer2_switch_player),
                onClick = { useSecondPlayer.value = !useSecondPlayer.value },
                modifier = Modifier
                    .weight(1f)
                    .exoplayer2ScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_exoplayer2_switch_surface),
                onClick = {
                    surfaceType.value = when (surfaceType.value) {
                        ExoPlayerSurfaceType.SurfaceView -> ExoPlayerSurfaceType.TextureView
                        ExoPlayerSurfaceType.TextureView -> ExoPlayerSurfaceType.SurfaceView
                        ExoPlayerSurfaceType.None -> ExoPlayerSurfaceType.SurfaceView
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .exoplayer2ScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
        }
        Button(
            text = stringResource(R.string.demo_exoplayer2_reset),
            onClick = {
                useSecondPlayer.value = false
                surfaceType.value = ExoPlayerSurfaceType.SurfaceView
                firstFrameCount.value = 0
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .exoplayer2ScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        Text(
            text = stringResource(
                R.string.demo_exoplayer2_status,
                playerLabel,
                surfaceLabel,
                firstFrameCount.value,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .exoplayer2ScenarioTarget(scenario, DemoAutomationRole.State),
        )
        ExoPlayerView(
            player = player,
            surfaceType = surfaceType.value,
            configuration = ExoPlayerViewConfiguration(
                useController = true,
                showBuffering = ExoPlayerShowBuffering.WhenPlaying,
                shutterBackgroundColor = Color.BLACK,
                contentDescription = stringResource(R.string.demo_exoplayer2_content_description),
                keepScreenOn = true,
            ),
            onRenderedFirstFrame = { firstFrameCount.value++ },
            key = "demo_exoplayer2_player",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .backgroundColor(Color.BLACK)
                .exoplayer2ScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        Text(
            text = stringResource(R.string.demo_exoplayer2_manual_check),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun Modifier.exoplayer2ScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
