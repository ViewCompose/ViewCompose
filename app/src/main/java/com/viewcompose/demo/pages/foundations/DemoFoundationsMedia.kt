package com.viewcompose

import android.widget.ImageView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

private val DemoDelayedImageLoader = UiImageLoader { target, request ->
    val imageView = (target as? PlatformUiImageTarget)?.target as? ImageView
        ?: error("DemoDelayedImageLoader requires an ImageView target")
    val resultResource = when (val source = request.source) {
        is ImageSource.Resource -> source.resId
        is ImageSource.Model -> source.value as? Int
            ?: error("Demo model must contain a drawable resource ID")
        else -> error("Demo loader supports only Resource and Model sources")
    }
    val delayMillis = if (request.source is ImageSource.Model) 320L else 120L
    val completion = Runnable { imageView.setImageResource(resultResource) }
    imageView.postDelayed(completion, delayMillis)
    UiImageLoadHandle { imageView.removeCallbacks(completion) }
}

internal fun UiTreeBuilder.FoundationsMediaFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val pipelineMode = remember { mutableStateOf(0) }
    FoundationsFixtureList(
        generation = generation,
        sections = listOf("pipeline", "resource", "fallback", "icon"),
    ) { section ->
        when (section) {
            "pipeline" -> FoundationsMediaPipeline(
                scenario = scenario,
                pipelineMode = pipelineMode.value,
                onAdvance = { pipelineMode.value = (pipelineMode.value + 1) % 3 },
                onReset = onReset,
            )

            "resource" -> FoundationsMediaResource()
            "fallback" -> FoundationsMediaFallback(scenario)
            "icon" -> FoundationsMediaIcon()
            else -> error("Unsupported foundations media section: $section")
        }
    }
}

private fun UiTreeBuilder.FoundationsMediaPipeline(
    scenario: DemoScenarioSpec?,
    pipelineMode: Int,
    onAdvance: () -> Unit,
    onReset: () -> Unit,
) {
    Column(spacing = 10.dp, modifier = Modifier.fillMaxWidth()) {
        FoundationsSummary(scenario)
        Text(
            text = stringResource(R.string.demo_foundations_media_pipeline_title),
            style = Theme.typography.titleMedium,
        )
        ProvideImageLoader(DemoDelayedImageLoader) {
            val source = when (pipelineMode) {
                0 -> ImageSource.Resource(R.drawable.demo_media_image)
                1 -> null
                else -> ImageSource.Model(
                    value = R.drawable.demo_media_icon,
                    stableKey = "demo-delayed-model-v1",
                )
            }
            Image(
                source = source,
                contentDescription = stringResource(
                    R.string.demo_foundations_media_pipeline_content_description,
                ),
                contentScale = ImageContentScale.Crop,
                placeholder = ImageSource.Resource(R.drawable.demo_media_icon),
                fallback = ImageSource.Resource(R.drawable.demo_media_image),
                requestOptions = UiImageRequestOptions(
                    decodeSize = UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                    .shape(Theme.shapes.medium)
                    .foundationsScenarioTarget(scenario, DemoAutomationRole.Target),
            )
            Text(
                text = stringResource(mediaStateResource(pipelineMode)),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .foundationsScenarioTarget(scenario, DemoAutomationRole.State),
            )
            Button(
                text = stringResource(R.string.demo_foundations_media_advance),
                onClick = onAdvance,
                modifier = Modifier
                    .fillMaxWidth()
                    .foundationsScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_foundations_reset),
                variant = ButtonVariant.Outlined,
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .foundationsScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

private fun UiTreeBuilder.FoundationsMediaResource() {
    Surface(
        variant = SurfaceVariant.Variant,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.demo_foundations_media_resource_title),
                style = Theme.typography.titleMedium,
            )
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    source = ImageSource.Resource(R.drawable.demo_media_image),
                    contentDescription = stringResource(
                        R.string.demo_foundations_media_resource_content_description,
                    ),
                    contentScale = ImageContentScale.Crop,
                    requestOptions = UiImageRequestOptions(
                        decodeSize = UiImageDecodeSize.Fixed(width = 64.dp, height = 64.dp),
                    ),
                    modifier = Modifier.size(64.dp, 64.dp).shape(Theme.shapes.medium),
                )
                Text(
                    text = stringResource(R.string.demo_foundations_media_resource_summary),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun UiTreeBuilder.FoundationsMediaFallback(scenario: DemoScenarioSpec?) {
    Surface(
        variant = SurfaceVariant.Variant,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.demo_foundations_media_fallback_title),
                style = Theme.typography.titleMedium,
            )
            Row(spacing = 12.dp, modifier = Modifier.fillMaxWidth()) {
                Image(
                    source = ImageSource.Url("https://picsum.photos/seed/viewcompose-demo/640/360"),
                    contentDescription = stringResource(
                        R.string.demo_foundations_media_remote_content_description,
                    ),
                    contentScale = ImageContentScale.Crop,
                    placeholder = ImageSource.Resource(R.drawable.demo_media_image),
                    error = ImageSource.Resource(R.drawable.demo_media_image),
                    fallback = ImageSource.Resource(R.drawable.demo_media_image),
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(Theme.shapes.medium),
                )
                Image(
                    source = null,
                    contentDescription = stringResource(
                        R.string.demo_foundations_media_fallback_content_description,
                    ),
                    contentScale = ImageContentScale.Crop,
                    fallback = ImageSource.Resource(R.drawable.demo_media_image),
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(Theme.shapes.medium)
                        .foundationsScenarioTarget(
                            scenario,
                            DemoAutomationRole.SecondaryTarget,
                        ),
                )
            }
        }
    }
}

private fun UiTreeBuilder.FoundationsMediaIcon() {
    Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = stringResource(R.string.demo_foundations_media_icon_title),
            style = Theme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.demo_foundations_media_icon_summary),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        Row(
            spacing = 12.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(modifier = Modifier.padding(8.dp)) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(
                        R.string.demo_foundations_media_default_icon_content_description,
                    ),
                )
            }
            UiThemeOverride(colors = { copy(onSurface = secondary) }) {
                Surface(modifier = Modifier.padding(8.dp)) {
                    Icon(
                        source = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(
                            R.string.demo_foundations_media_secondary_icon_content_description,
                        ),
                    )
                }
            }
        }
    }
}

private fun mediaStateResource(mode: Int): Int = when (mode) {
    0 -> R.string.demo_foundations_media_state_resource
    1 -> R.string.demo_foundations_media_state_fallback
    else -> R.string.demo_foundations_media_state_model
}
