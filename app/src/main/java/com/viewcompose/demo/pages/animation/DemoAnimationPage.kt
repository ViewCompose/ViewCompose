package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.Crossfade
import com.viewcompose.animation.MutableTransitionState
import com.viewcompose.animation.animateColorAsState
import com.viewcompose.animation.animateContentSize
import com.viewcompose.animation.animateFloat
import com.viewcompose.animation.animateFloatAsState
import com.viewcompose.animation.animateIntAsState
import com.viewcompose.animation.animateDpAsState
import com.viewcompose.animation.animateValueAsState
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.expandHorizontally
import com.viewcompose.animation.expandIn
import com.viewcompose.animation.expandVertically
import com.viewcompose.animation.fadeIn
import com.viewcompose.animation.fadeOut
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.keyframe
import com.viewcompose.animation.core.keyframes
import com.viewcompose.animation.rememberInfiniteTransition
import com.viewcompose.animation.rememberAnimatable
import com.viewcompose.animation.core.repeatable
import com.viewcompose.animation.shrinkHorizontally
import com.viewcompose.animation.shrinkOut
import com.viewcompose.animation.shrinkVertically
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.animation.updateTransition
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LocalAnimationCoroutineContext
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp
import kotlinx.coroutines.withContext

@ViewComposePreview(name = "Animation · Core", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationCore() {
    AnimationPage(AnimationFixture.Core)
}

@ViewComposePreview(name = "Animation · Content", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationContent() {
    AnimationPage(AnimationFixture.Content)
}

@ViewComposePreview(name = "Animation · List motion", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationListMotion() {
    AnimationPage(AnimationFixture.ListMotion)
}

@ViewComposePreview(name = "Animation · Specs", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationSpecs() {
    AnimationPage(AnimationFixture.Specs)
}

@ViewComposePreview(name = "Animation · Transition", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationTransition() {
    AnimationPage(AnimationFixture.Transition)
}

@ViewComposePreview(name = "Animation · Infinite", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationInfinite() {
    AnimationPage(AnimationFixture.Infinite)
}

internal enum class AnimationFixture(
    val scenarioId: DemoScenarioId,
    val sectionKey: String,
) {
    Core(DemoScenarioIds.AnimationCore, "core"),
    Content(DemoScenarioIds.AnimationContent, "transition"),
    ListMotion(DemoScenarioIds.AnimationListMotion, "list"),
    Specs(DemoScenarioIds.AnimationSpecs, "specs"),
    Transition(DemoScenarioIds.AnimationTransition, "transition_matrix"),
    Infinite(DemoScenarioIds.AnimationInfinite, "infinite_animatable"),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): AnimationFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported animation scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.AnimationPage(
    fixture: AnimationFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val visibleState = if (fixture == AnimationFixture.Core) remember { mutableStateOf(true) } else null
    val taskCompletedState = if (fixture == AnimationFixture.Core) remember { mutableStateOf(false) } else null
    val pulseState = if (fixture == AnimationFixture.Core) remember { mutableStateOf(false) } else null
    val contentState = if (fixture == AnimationFixture.Content) remember { mutableStateOf(false) } else null
    val crossfadeState = if (fixture == AnimationFixture.Content) remember { mutableStateOf(false) } else null
    val listItemsState = if (fixture == AnimationFixture.ListMotion) {
        remember { mutableStateOf(initialAnimationListItems()) }
    } else {
        null
    }
    val listSeedState = if (fixture == AnimationFixture.ListMotion) remember { mutableStateOf(0) } else null
    val specKindState = if (fixture == AnimationFixture.Specs) {
        remember { mutableStateOf(AnimationSpecKind.Tween) }
    } else {
        null
    }
    val specTargetState = if (fixture == AnimationFixture.Specs) remember { mutableStateOf(false) } else null
    val easingLinearState = if (fixture == AnimationFixture.Specs) remember { mutableStateOf(false) } else null
    val repeatModeReverseState = if (fixture == AnimationFixture.Specs) remember { mutableStateOf(false) } else null
    val vectorTargetState = if (fixture == AnimationFixture.Specs) remember { mutableStateOf(false) } else null
    val sizeExpandedState = if (fixture == AnimationFixture.Specs) remember { mutableStateOf(false) } else null
    val transitionState = if (fixture == AnimationFixture.Transition) remember { mutableStateOf(false) } else null
    val mutableVisibilityState = if (fixture == AnimationFixture.Transition) {
        remember { MutableTransitionState(false) }
    } else {
        null
    }
    val rowAxisVisibleState = if (fixture == AnimationFixture.Transition) remember { mutableStateOf(false) } else null
    val columnAxisVisibleState = if (fixture == AnimationFixture.Transition) remember { mutableStateOf(false) } else null
    val infinitePulseState = if (fixture == AnimationFixture.Infinite) remember { mutableStateOf(false) } else null
    val infiniteReverseState = if (fixture == AnimationFixture.Infinite) remember { mutableStateOf(false) } else null
    val animatableCommandState = if (fixture == AnimationFixture.Infinite) {
        remember { mutableStateOf(AnimatableCommand.None) }
    } else {
        null
    }
    val animatableCommandNonceState = if (fixture == AnimationFixture.Infinite) {
        remember { mutableStateOf(0) }
    } else {
        null
    }
    val animatable = if (fixture == AnimationFixture.Infinite) {
        rememberAnimatable(initialValue = 0f, converter = AnimationConverters.Float)
    } else {
        null
    }
    val animationCoroutineContext = if (fixture == AnimationFixture.Infinite) {
        LocalAnimationCoroutineContext.current
    } else {
        null
    }

    if (
        animatableCommandState != null &&
        animatableCommandNonceState != null &&
        animatable != null &&
        animationCoroutineContext != null
    ) {
        LaunchedEffect(
            animatableCommandState.value,
            animatableCommandNonceState.value,
            animationCoroutineContext,
        ) {
            val command = animatableCommandState.value
            withContext(animationCoroutineContext) {
                when (command) {
                    AnimatableCommand.None -> Unit

                    AnimatableCommand.Stop -> animatable.stop()

                    AnimatableCommand.AnimateToHigh -> {
                        animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 420),
                        )
                    }

                    AnimatableCommand.AnimateToLow -> {
                        animatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(durationMillis = 520),
                        )
                    }

                    AnimatableCommand.SnapToHigh -> {
                        animatable.snapTo(1f)
                    }

                    AnimatableCommand.SnapToLow -> {
                        animatable.snapTo(0f)
                    }
                }
            }
        }
    }

    LazyColumn(
        items = listOf(fixture.sectionKey),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "core" -> {
                val visibleState = checkNotNull(visibleState)
                val taskCompletedState = checkNotNull(taskCompletedState)
                val pulseState = checkNotNull(pulseState)
                ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_animation_core_title),
                subtitle = stringResource(R.string.demo_animation_core_summary),
            ) {
                val scale = animateFloatAsState(
                    targetValue = if (pulseState.value) 1.08f else 0.92f,
                    animationSpec = spring(),
                )
                Text(
                    text = stringResource(
                        R.string.demo_animation_core_state,
                        visibleState.value,
                        pulseState.value,
                        taskCompletedState.value,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (visibleState.value) {
                                R.string.demo_animation_core_hide
                            } else {
                                R.string.demo_animation_core_show
                            },
                        ),
                        onClick = { visibleState.value = !visibleState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE)
                            .animationScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(
                            if (pulseState.value) {
                                R.string.demo_animation_core_scale_low
                            } else {
                                R.string.demo_animation_core_scale_high
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { pulseState.value = !pulseState.value },
                        modifier = Modifier
                            .weight(1f)
                            .animationScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_animation_reset),
                    onClick = {
                        visibleState.value = true
                        pulseState.value = false
                        taskCompletedState.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                AnimatedVisibility(
                    visible = visibleState.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_TARGET)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = scale.value,
                                scaleY = scale.value,
                            )
                            .padding(12.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_animation_core_surface))
                    }
                }
                Text(
                    text = stringResource(R.string.demo_animation_core_footer),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_FOOTER),
                )
                LazyColumn(
                    items = listOf(
                        DemoAnimationTask(
                            id = 1L,
                            title = stringResource(R.string.demo_animation_task_title),
                            completed = taskCompletedState.value,
                        ),
                    ),
                    key = DemoAnimationTask::id,
                    contentType = { "task" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) { task ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                text = task.title,
                                style = TextDefaults.titleMediumStyle(),
                            )
                            AnimatedVisibility(
                                visible = task.completed,
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_TASK_STATUS),
                            ) {
                                Text(text = stringResource(R.string.demo_animation_task_completed))
                            }
                            Button(
                                text = if (task.completed) {
                                    stringResource(R.string.demo_animation_task_reopen, task.title)
                                } else {
                                    stringResource(R.string.demo_animation_task_complete, task.title)
                                },
                                onClick = {
                                    taskCompletedState.value = !taskCompletedState.value
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DemoTestTags.ANIMATION_TASK_TOGGLE),
                            )
                            Row(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DemoTestTags.ANIMATION_TASK_ACTIONS),
                            ) {
                                Button(
                                    text = stringResource(R.string.demo_animation_task_details, task.title),
                                    variant = ButtonVariant.Outlined,
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    text = stringResource(R.string.demo_animation_task_delete, task.title),
                                    variant = ButtonVariant.Outlined,
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                }
            }

            "transition" -> {
                val contentState = checkNotNull(contentState)
                val crossfadeState = checkNotNull(crossfadeState)
                ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_animation_content_title),
                subtitle = stringResource(R.string.demo_animation_content_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_animation_content_state,
                        contentState.value,
                        crossfadeState.value,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (contentState.value) {
                                R.string.demo_animation_content_to_primary
                            } else {
                                R.string.demo_animation_content_to_alternative
                            },
                        ),
                        onClick = { contentState.value = !contentState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE)
                            .animationScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(
                            if (crossfadeState.value) {
                                R.string.demo_animation_crossfade_to_primary
                            } else {
                                R.string.demo_animation_crossfade_to_alternative
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { crossfadeState.value = !crossfadeState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_CROSSFADE_TOGGLE)
                            .animationScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_animation_reset),
                    onClick = {
                        contentState.value = false
                        crossfadeState.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                Crossfade(
                    targetState = contentState.value,
                    animationSpec = tween(260),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 10.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                ) { alt ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (alt) {
                                    R.string.demo_animation_content_alternative
                                } else {
                                    R.string.demo_animation_content_primary
                                },
                            ),
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_CONTENT_LABEL),
                        )
                    }
                }
                Crossfade(
                    targetState = crossfadeState.value,
                    animationSpec = tween(300),
                    modifier = Modifier.fillMaxWidth(),
                ) { alt ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (alt) {
                                    R.string.demo_animation_crossfade_alternative
                                } else {
                                    R.string.demo_animation_crossfade_primary
                                },
                            ),
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_CROSSFADE_LABEL),
                        )
                    }
                }
                }
            }

            "list" -> {
                val listItemsState = checkNotNull(listItemsState)
                val listSeedState = checkNotNull(listSeedState)
                ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_animation_list_title),
                subtitle = stringResource(R.string.demo_animation_list_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_animation_list_state,
                        listSeedState.value,
                        animationListItemLabel(listItemsState.value.firstOrNull()),
                        listItemsState.value.size,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_animation_list_insert),
                        onClick = {
                            val nextSeed = listSeedState.value + 1
                            listSeedState.value = nextSeed
                            listItemsState.value = listOf(AnimationListItem.New(nextSeed)) + listItemsState.value
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_LIST_ADD)
                            .animationScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_animation_list_rotate),
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            val current = listItemsState.value
                            if (current.size > 1) {
                                listItemsState.value = current.drop(1) + current.first()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_LIST_REORDER)
                            .animationScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_animation_reset),
                    onClick = {
                        listSeedState.value = 0
                        listItemsState.value = initialAnimationListItems()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                LazyColumn(
                    items = listItemsState.value,
                    key = AnimationListItem::stableKey,
                    spacing = 8.dp,
                    motionPolicy = CollectionMotionPolicy(
                        animateInsert = true,
                        animateRemove = true,
                        animateMove = true,
                        animateChange = true,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                ) { item ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(
                            text = animationListItemLabel(item),
                            style = UiTextStyle(fontSizeSp = 14.sp),
                            modifier = if (item == listItemsState.value.firstOrNull()) {
                                Modifier.testTag(DemoTestTags.ANIMATION_LIST_FIRST)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.demo_animation_list_first,
                        animationListItemLabel(listItemsState.value.firstOrNull()),
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 8.dp),
                )
                }
            }

            "specs" -> {
                val specKindState = checkNotNull(specKindState)
                val specTargetState = checkNotNull(specTargetState)
                val easingLinearState = checkNotNull(easingLinearState)
                val repeatModeReverseState = checkNotNull(repeatModeReverseState)
                val vectorTargetState = checkNotNull(vectorTargetState)
                val sizeExpandedState = checkNotNull(sizeExpandedState)
                ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_animation_specs_title),
                subtitle = stringResource(R.string.demo_animation_specs_summary),
            ) {
                val easing = if (easingLinearState.value) EasingDefaults.Linear else EasingDefaults.FastOutSlowIn
                val repeatMode = if (repeatModeReverseState.value) RepeatMode.Reverse else RepeatMode.Restart
                val typedSpec = when (specKindState.value) {
                    AnimationSpecKind.Tween -> tween(
                        durationMillis = 420,
                        easing = easing,
                    )

                    AnimationSpecKind.Spring -> spring(
                        dampingRatio = 0.78f,
                        stiffness = 260f,
                        durationMillis = 520,
                    )

                    AnimationSpecKind.Keyframes -> keyframes(
                        durationMillis = 460,
                        keyframe(0, 0f),
                        keyframe(150, 0.24f),
                        keyframe(320, 0.76f),
                        keyframe(460, 1f),
                    )

                    AnimationSpecKind.Snap -> snap()

                    AnimationSpecKind.Repeatable -> repeatable(
                        iterations = 2,
                        animation = tween(
                            durationMillis = 200,
                            easing = easing,
                        ),
                        repeatMode = repeatMode,
                    )
                }
                val typedTarget = specTargetState.value
                val floatValueState = animateFloatAsState(
                    targetValue = if (typedTarget) 1f else 0f,
                    animationSpec = typedSpec,
                )
                val intValueState = animateIntAsState(
                    targetValue = if (typedTarget) 96 else 18,
                    animationSpec = typedSpec,
                )
                val colorValueState = animateColorAsState(
                    targetValue = if (typedTarget) 0xFF1B5E20.toInt() else 0xFFBF360C.toInt(),
                    animationSpec = typedSpec,
                )
                val dpValueState = animateDpAsState(
                    targetValue = if (typedTarget) 24.dp else 8.dp,
                    animationSpec = typedSpec,
                )
                val vectorValueState = animateValueAsState(
                    targetValue = if (vectorTargetState.value) {
                        DemoVector2(x = 1f, y = 28f)
                    } else {
                        DemoVector2(x = 0f, y = 8f)
                    },
                    converter = DemoVector2Converter,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = easing,
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.demo_animation_specs_state,
                        specKindState.value.name,
                        specTargetState.value,
                        easingLinearState.value,
                        repeatModeReverseState.value,
                        vectorTargetState.value,
                        sizeExpandedState.value,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Column(
                    spacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            text = stringResource(
                                R.string.demo_animation_specs_kind,
                                stringResource(specKindState.value.labelResource),
                            ),
                            onClick = {
                                specKindState.value = nextAnimationSpecKind(specKindState.value)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_KIND_TOGGLE)
                                .animationScenarioTarget(
                                    scenario,
                                    DemoAutomationRole.SecondaryAction,
                                ),
                        )
                        Button(
                            text = stringResource(
                                if (specTargetState.value) {
                                    R.string.demo_animation_specs_target_end
                                } else {
                                    R.string.demo_animation_specs_target_start
                                },
                            ),
                            variant = ButtonVariant.Outlined,
                            onClick = { specTargetState.value = !specTargetState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_TARGET_TOGGLE)
                                .animationScenarioTarget(
                                    scenario,
                                    DemoAutomationRole.PrimaryAction,
                                ),
                        )
                    }
                    Row(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            text = stringResource(
                                if (easingLinearState.value) {
                                    R.string.demo_animation_specs_easing_linear
                                } else {
                                    R.string.demo_animation_specs_easing_fast_out_slow_in
                                },
                            ),
                            variant = ButtonVariant.Outlined,
                            onClick = { easingLinearState.value = !easingLinearState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_EASING_TOGGLE),
                        )
                        Button(
                            text = stringResource(
                                if (repeatModeReverseState.value) {
                                    R.string.demo_animation_specs_repeat_reverse
                                } else {
                                    R.string.demo_animation_specs_repeat_restart
                                },
                            ),
                            variant = ButtonVariant.Outlined,
                            onClick = { repeatModeReverseState.value = !repeatModeReverseState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_REPEAT_MODE_TOGGLE),
                        )
                    }
                    Button(
                        text = stringResource(R.string.demo_animation_reset),
                        onClick = {
                            specKindState.value = AnimationSpecKind.Tween
                            specTargetState.value = false
                            easingLinearState.value = false
                            repeatModeReverseState.value = false
                            vectorTargetState.value = false
                            sizeExpandedState.value = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                    ) {
                        Column(
                            spacing = 4.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.demo_animation_specs_float,
                                    floatValueState.value.format2(),
                                ),
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE),
                            )
                            Text(
                                text = stringResource(
                                    R.string.demo_animation_specs_int,
                                    intValueState.value,
                                ),
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_INT_VALUE),
                            )
                            Text(
                                text = stringResource(
                                    R.string.demo_animation_specs_dp,
                                    dpValueState.value.toString(),
                                ),
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_DP_VALUE),
                            )
                            Text(
                                text = stringResource(
                                    R.string.demo_animation_specs_color,
                                    colorValueState.value.toUInt().toString(16).uppercase(),
                                ),
                                color = colorValueState.value,
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_COLOR_VALUE),
                            )
                        }
                    }
                    Row(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            text = stringResource(
                                if (vectorTargetState.value) {
                                    R.string.demo_animation_specs_vector_reset
                                } else {
                                    R.string.demo_animation_specs_vector_target
                                },
                            ),
                            onClick = { vectorTargetState.value = !vectorTargetState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_VECTOR_TOGGLE),
                        )
                        Text(
                            text = stringResource(
                                R.string.demo_animation_specs_vector_value,
                                vectorValueState.value.x.format2(),
                                vectorValueState.value.y.format2(),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE),
                        )
                    }
                    Button(
                        text = stringResource(
                            if (sizeExpandedState.value) {
                                R.string.demo_animation_specs_size_collapse
                            } else {
                                R.string.demo_animation_specs_size_expand
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { sizeExpandedState.value = !sizeExpandedState.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DemoTestTags.ANIMATION_SPEC_SIZE_TOGGLE),
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring())
                            .padding(10.dp),
                    ) {
                        Column(
                            spacing = 6.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.demo_animation_specs_size_title))
                            if (sizeExpandedState.value) {
                                Text(
                                    text = stringResource(R.string.demo_animation_specs_size_content_a),
                                    modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_SIZE_PROBE),
                                )
                                Text(text = stringResource(R.string.demo_animation_specs_size_content_b))
                                Text(text = stringResource(R.string.demo_animation_specs_size_content_c))
                            }
                        }
                    }
                }
                }
            }

            "transition_matrix" -> {
                val transitionState = checkNotNull(transitionState)
                val mutableVisibilityState = checkNotNull(mutableVisibilityState)
                val rowAxisVisibleState = checkNotNull(rowAxisVisibleState)
                val columnAxisVisibleState = checkNotNull(columnAxisVisibleState)
                ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_animation_transition_title),
                subtitle = stringResource(R.string.demo_animation_transition_summary),
            ) {
                val transition = updateTransition(
                    targetState = transitionState.value,
                    label = DEMO_TRANSITION_LABEL,
                )
                val transitionAlphaState = transition.animateFloat(
                    animationSpec = { tween(260) },
                ) { toggled ->
                    if (toggled) 1f else 0.35f
                }
                val transitionIntState = transition.animateInt(
                    animationSpec = { spring(durationMillis = 460) },
                ) { toggled ->
                    if (toggled) 9 else 2
                }
                val transitionDpState = transition.animateDp(
                    animationSpec = { tween(260) },
                ) { toggled ->
                    if (toggled) 14.dp else 4.dp
                }
                val transitionColorState = transition.animateColor(
                    animationSpec = { tween(300) },
                ) { toggled ->
                    if (toggled) 0xFF2E7D32.toInt() else 0xFFAD1457.toInt()
                }
                Text(
                    text = stringResource(
                        R.string.demo_animation_transition_state,
                        transitionState.value,
                        mutableVisibilityState.targetState,
                        rowAxisVisibleState.value,
                        columnAxisVisibleState.value,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        if (transitionState.value) {
                            R.string.demo_animation_transition_to_primary
                        } else {
                            R.string.demo_animation_transition_to_alternative
                        },
                    ),
                    onClick = { transitionState.value = !transitionState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_TRANSITION_TOGGLE)
                        .animationScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.demo_animation_transition_alpha,
                                transitionAlphaState.value.format2(),
                            ),
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_ALPHA),
                        )
                        Text(
                            text = stringResource(
                                R.string.demo_animation_transition_int,
                                transitionIntState.value,
                            ),
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_INT),
                        )
                        Text(
                            text = stringResource(
                                R.string.demo_animation_transition_dp,
                                transitionDpState.value.toString(),
                            ),
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_DP),
                        )
                        Text(
                            text = stringResource(
                                R.string.demo_animation_transition_color,
                                transitionColorState.value.toUInt().toString(16).uppercase(),
                            ),
                            color = transitionColorState.value,
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_COLOR),
                        )
                    }
                }
                Button(
                    text = stringResource(R.string.demo_animation_reset),
                    onClick = {
                        transitionState.value = false
                        mutableVisibilityState.targetState = false
                        rowAxisVisibleState.value = false
                        columnAxisVisibleState.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                Button(
                    text = stringResource(
                        if (mutableVisibilityState.targetState) {
                            R.string.demo_animation_visibility_target_false
                        } else {
                            R.string.demo_animation_visibility_target_true
                        },
                    ),
                    onClick = {
                        mutableVisibilityState.targetState = !mutableVisibilityState.targetState
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp, bottom = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_TOGGLE)
                        .animationScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                )
                Text(
                    text = stringResource(
                        R.string.demo_animation_visibility_status,
                        mutableVisibilityState.currentState,
                        mutableVisibilityState.targetState,
                        mutableVisibilityState.isIdle,
                    ),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_STATUS),
                )
                AnimatedVisibility(
                    visibleState = mutableVisibilityState,
                    enter = fadeIn(tween(220)) + expandIn(tween(260)),
                    exit = shrinkOut(tween(220)) + fadeOut(tween(180)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_TARGET),
                ) {
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_animation_visibility_content))
                    }
                }
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (rowAxisVisibleState.value) {
                                R.string.demo_animation_row_hide
                            } else {
                                R.string.demo_animation_row_show
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { rowAxisVisibleState.value = !rowAxisVisibleState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE),
                    )
                    AnimatedVisibility(
                        visible = rowAxisVisibleState.value,
                        enter = fadeIn(tween(180)) + expandHorizontally(tween(260)),
                        exit = shrinkHorizontally(tween(240)) + fadeOut(tween(160)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ROW_AXIS_TARGET),
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_animation_row_content))
                        }
                    }
                }
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (columnAxisVisibleState.value) {
                                R.string.demo_animation_column_hide
                            } else {
                                R.string.demo_animation_column_show
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { columnAxisVisibleState.value = !columnAxisVisibleState.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TOGGLE),
                    )
                    AnimatedVisibility(
                        visible = columnAxisVisibleState.value,
                        enter = fadeIn(tween(180)) + expandVertically(tween(260)),
                        exit = shrinkVertically(tween(240)) + fadeOut(tween(160)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TARGET),
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_animation_column_content))
                        }
                    }
                }
                }
            }

            "infinite_animatable" -> {
                val infinitePulseState = checkNotNull(infinitePulseState)
                val infiniteReverseState = checkNotNull(infiniteReverseState)
                val animatableCommandState = checkNotNull(animatableCommandState)
                val animatableCommandNonceState = checkNotNull(animatableCommandNonceState)
                val animatable = checkNotNull(animatable)
                ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_animation_infinite_title),
                subtitle = stringResource(R.string.demo_animation_infinite_summary),
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = DEMO_INFINITE_TRANSITION_LABEL)
                val infiniteScaleState = infiniteTransition.animateFloat(
                    initialValue = if (infinitePulseState.value) 0.86f else 1f,
                    targetValue = if (infinitePulseState.value) 1.14f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 520,
                            easing = EasingDefaults.LinearOutSlowIn,
                        ),
                        repeatMode = if (infiniteReverseState.value) {
                            RepeatMode.Reverse
                        } else {
                            RepeatMode.Restart
                        },
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.demo_animation_infinite_state,
                        infinitePulseState.value,
                        infiniteReverseState.value,
                    ),
                    modifier = Modifier.animationScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (infinitePulseState.value) {
                                R.string.demo_animation_infinite_disable
                            } else {
                                R.string.demo_animation_infinite_enable
                            },
                        ),
                        onClick = { infinitePulseState.value = !infinitePulseState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_INFINITE_RUN_TOGGLE)
                            .animationScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(
                            if (infiniteReverseState.value) {
                                R.string.demo_animation_infinite_repeat_reverse
                            } else {
                                R.string.demo_animation_infinite_repeat_restart
                            },
                        ),
                        variant = ButtonVariant.Outlined,
                        onClick = { infiniteReverseState.value = !infiniteReverseState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_INFINITE_REPEAT_MODE),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_animation_reset),
                    onClick = {
                        infinitePulseState.value = false
                        infiniteReverseState.value = false
                        animatableCommandState.value = AnimatableCommand.SnapToLow
                        animatableCommandNonceState.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = infiniteScaleState.value,
                            scaleY = infiniteScaleState.value,
                        )
                        .padding(12.dp)
                        .animationScenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Text(
                        text = stringResource(
                            R.string.demo_animation_infinite_scale,
                            infiniteScaleState.value.format2(),
                        ),
                        modifier = Modifier.testTag(DemoTestTags.ANIMATION_INFINITE_VALUE),
                    )
                }
                Text(
                    text = stringResource(R.string.demo_animation_animatable_panel),
                    style = UiTextStyle(fontSizeSp = 15.sp),
                    modifier = Modifier.margin(top = 10.dp, bottom = 6.dp),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        text = stringResource(R.string.demo_animation_animatable_to_high),
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.AnimateToHigh
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_TO_HIGH),
                    )
                    Button(
                        text = stringResource(R.string.demo_animation_animatable_to_low),
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.AnimateToLow
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_TO_LOW),
                    )
                }
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_animation_animatable_snap_high),
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.SnapToHigh
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_SNAP_HIGH)
                            .animationScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_animation_animatable_snap_low),
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.SnapToLow
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_SNAP_LOW),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_animation_animatable_stop),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        animatableCommandState.value = AnimatableCommand.Stop
                        animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_ANIMATABLE_STOP),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .padding(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.demo_animation_animatable_value,
                            animatable.asState.value.format2(),
                        ),
                        modifier = Modifier.testTag(DemoTestTags.ANIMATION_ANIMATABLE_VALUE),
                    )
                }
                }
            }

            else -> error("Unsupported animation section: $section")
        }
    }
}

private sealed class AnimationListItem(
    val stableKey: String,
) {
    data object A : AnimationListItem("a")

    data object B : AnimationListItem("b")

    data object C : AnimationListItem("c")

    data class New(
        val seed: Int,
    ) : AnimationListItem("new-$seed")
}

private fun initialAnimationListItems(): List<AnimationListItem> = listOf(
    AnimationListItem.A,
    AnimationListItem.B,
    AnimationListItem.C,
)

private fun UiTreeBuilder.animationListItemLabel(item: AnimationListItem?): String = when (item) {
    AnimationListItem.A -> stringResource(R.string.demo_animation_list_item_a)
    AnimationListItem.B -> stringResource(R.string.demo_animation_list_item_b)
    AnimationListItem.C -> stringResource(R.string.demo_animation_list_item_c)
    is AnimationListItem.New -> stringResource(R.string.demo_animation_list_item_new, item.seed)
    null -> ""
}

private enum class AnimationSpecKind(
    val labelResource: Int,
) {
    Tween(R.string.demo_animation_specs_kind_tween),
    Spring(R.string.demo_animation_specs_kind_spring),
    Keyframes(R.string.demo_animation_specs_kind_keyframes),
    Snap(R.string.demo_animation_specs_kind_snap),
    Repeatable(R.string.demo_animation_specs_kind_repeatable),
    ;
}

private enum class AnimatableCommand {
    None,
    AnimateToHigh,
    AnimateToLow,
    SnapToHigh,
    SnapToLow,
    Stop,
}

private data class DemoVector2(
    val x: Float,
    val y: Float,
)

private data class DemoAnimationTask(
    val id: Long,
    val title: String,
    val completed: Boolean,
)

private object DemoVector2Converter : AnimationConverter<DemoVector2> {
    override fun toVector(value: DemoVector2): FloatArray {
        return floatArrayOf(value.x, value.y)
    }

    override fun fromVector(vector: FloatArray): DemoVector2 {
        return DemoVector2(
            x = vector.getOrElse(0) { 0f },
            y = vector.getOrElse(1) { 0f },
        )
    }
}

private fun Float.format2(): String {
    return String.format("%.2f", this)
}

private fun nextAnimationSpecKind(kind: AnimationSpecKind): AnimationSpecKind {
    return when (kind) {
        AnimationSpecKind.Tween -> AnimationSpecKind.Spring
        AnimationSpecKind.Spring -> AnimationSpecKind.Keyframes
        AnimationSpecKind.Keyframes -> AnimationSpecKind.Snap
        AnimationSpecKind.Snap -> AnimationSpecKind.Repeatable
        AnimationSpecKind.Repeatable -> AnimationSpecKind.Tween
    }
}

private fun Modifier.animationScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

private const val DEMO_TRANSITION_LABEL = "demo_transition"
private const val DEMO_INFINITE_TRANSITION_LABEL = "demo_infinite"
