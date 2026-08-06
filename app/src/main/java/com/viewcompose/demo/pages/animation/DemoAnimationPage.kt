package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.animation.AnimatedContent
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
    AnimationPage(initialPageIndex = 0, initialInfinitePulse = false)
}

@ViewComposePreview(name = "Animation · Content", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationContent() {
    AnimationPage(initialPageIndex = 1, initialInfinitePulse = false)
}

@ViewComposePreview(name = "Animation · List motion", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationListMotion() {
    AnimationPage(initialPageIndex = 2, initialInfinitePulse = false)
}

@ViewComposePreview(name = "Animation · Specs", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationSpecs() {
    AnimationPage(initialPageIndex = 3, initialInfinitePulse = false)
}

@ViewComposePreview(name = "Animation · Transition", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationTransition() {
    AnimationPage(initialPageIndex = 4, initialInfinitePulse = false)
}

@ViewComposePreview(name = "Animation · Infinite", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewAnimationInfinite() {
    AnimationPage(initialPageIndex = 5, initialInfinitePulse = false)
}

internal fun UiTreeBuilder.AnimationPage(
    initialPageIndex: Int = 0,
    initialInfinitePulse: Boolean = true,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 5)) }
    val visibleState = remember { mutableStateOf(true) }
    val taskCompletedState = remember { mutableStateOf(false) }
    val contentState = remember { mutableStateOf(false) }
    val crossfadeState = remember { mutableStateOf(false) }
    val pulseState = remember { mutableStateOf(false) }
    val listItemsState = remember { mutableStateOf(listOf("Item A", "Item B", "Item C")) }
    val listSeedState = remember { mutableStateOf(0) }
    val specKindState = remember { mutableStateOf(AnimationSpecKind.Tween) }
    val specTargetState = remember { mutableStateOf(false) }
    val easingLinearState = remember { mutableStateOf(false) }
    val repeatModeReverseState = remember { mutableStateOf(false) }
    val vectorTargetState = remember { mutableStateOf(false) }
    val sizeExpandedState = remember { mutableStateOf(false) }
    val transitionState = remember { mutableStateOf(false) }
    val mutableVisibilityState = remember { MutableTransitionState(false) }
    val rowAxisVisibleState = remember { mutableStateOf(false) }
    val columnAxisVisibleState = remember { mutableStateOf(false) }
    val infinitePulseState = remember { mutableStateOf(initialInfinitePulse) }
    val infiniteReverseState = remember { mutableStateOf(false) }
    val animatableCommandState = remember { mutableStateOf(AnimatableCommand.None) }
    val animatableCommandNonceState = remember { mutableStateOf(0) }
    val animatable = rememberAnimatable(initialValue = 0f, converter = AnimationConverters.Float)
    val animationCoroutineContext = LocalAnimationCoroutineContext.current

    val sections = when (selectedPageState.value) {
        0 -> listOf("page", "filter", "core", "verify")
        1 -> listOf("page", "filter", "transition", "verify")
        2 -> listOf("page", "filter", "list", "verify")
        3 -> listOf("page", "filter", "specs", "verify")
        4 -> listOf("page", "filter", "transition_matrix", "verify")
        else -> listOf("page", "filter", "infinite_animatable", "verify")
    }

    LaunchedEffect(
        animatableCommandState.value,
        animatableCommandNonceState.value,
        animationCoroutineContext,
    ) {
        val command = animatableCommandState.value
        withContext(animationCoroutineContext) {
            when (command) {
                AnimatableCommand.None,
                AnimatableCommand.Stop,
                -> Unit

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

    LazyColumn(
        items = sections,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "Animation",
                goal = "验证状态驱动动画、内容过渡和列表位移动画在 ViewCompose 中可用且默认行为可控。",
                modules = listOf("viewcompose-animation", "graphicsLayer", "LazyContainer motion policy"),
            )

            "filter" -> ChapterPageFilterSection(
                pages = listOf("Core", "Content", "List Motion", "Specs", "Transition", "Infinite"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "core" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "基础属性动画",
                subtitle = "animateFloatAsState + AnimatedVisibility 组合，验证显隐与缩放状态更新。",
            ) {
                val scale = animateFloatAsState(
                    targetValue = if (pulseState.value) 1.08f else 0.92f,
                    animationSpec = spring(),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = if (visibleState.value) "隐藏块" else "显示块",
                        onClick = { visibleState.value = !visibleState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE),
                    )
                    Button(
                        text = if (pulseState.value) "缩放 0.92x" else "缩放 1.08x",
                        variant = ButtonVariant.Outlined,
                        onClick = { pulseState.value = !pulseState.value },
                        modifier = Modifier.weight(1f),
                    )
                }
                AnimatedVisibility(
                    visible = visibleState.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_TARGET),
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
                        Text(text = "Animation Core Surface")
                    }
                }
                Text(
                    text = "Visibility footer anchor",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_FOOTER),
                )
                LazyColumn(
                    items = listOf(
                        DemoAnimationTask(
                            id = 1L,
                            title = "Read the tutorial",
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
                                Text(text = "Completed")
                            }
                            Button(
                                text = if (task.completed) {
                                    "Reopen ${task.title}"
                                } else {
                                    "Complete ${task.title}"
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
                                    text = "Details ${task.title}",
                                    variant = ButtonVariant.Outlined,
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    text = "Delete ${task.title}",
                                    variant = ButtonVariant.Outlined,
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            "transition" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "内容过渡动画",
                subtitle = "AnimatedContent + Crossfade 同页示例，覆盖内容替换动画。",
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = if (contentState.value) "切到主文案" else "切到替代文案",
                        onClick = { contentState.value = !contentState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE),
                    )
                    Button(
                        text = if (crossfadeState.value) "Crossfade 主文案" else "Crossfade 替代文案",
                        variant = ButtonVariant.Outlined,
                        onClick = { crossfadeState.value = !crossfadeState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_CROSSFADE_TOGGLE),
                    )
                }
                AnimatedContent(
                    targetState = contentState.value,
                    transitionSpec = { tween(260) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 10.dp),
                ) { alt ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (alt) {
                                "替代文案：内容过渡已生效"
                            } else {
                                "主文案：内容过渡待切换"
                            },
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
                            text = if (alt) {
                                "Crossfade 替代文案已生效"
                            } else {
                                "Crossfade 主文案展示中"
                            },
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_CROSSFADE_LABEL),
                        )
                    }
                }
            }

            "list" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "列表位移动画",
                subtitle = "LazyColumn 通过 lazyContainerMotion 启用 item add/move/change 的动画策略。",
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = "头部插入",
                        onClick = {
                            val nextSeed = listSeedState.value + 1
                            listSeedState.value = nextSeed
                            listItemsState.value = listOf("New $nextSeed") + listItemsState.value
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_LIST_ADD),
                    )
                    Button(
                        text = "顺序轮换",
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            val current = listItemsState.value
                            if (current.size > 1) {
                                listItemsState.value = current.drop(1) + current.first()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_LIST_REORDER),
                    )
                }
                LazyColumn(
                    items = listItemsState.value,
                    key = { it },
                    spacing = 8.dp,
                    motionPolicy = CollectionMotionPolicy(
                        animateInsert = true,
                        animateRemove = true,
                        animateMove = true,
                        animateChange = true,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) { item ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(
                            text = item,
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
                    text = "First item: ${listItemsState.value.firstOrNull().orEmpty()}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 8.dp),
                )
            }

            "specs" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "AnimationSpec 与泛型动画",
                subtitle = "覆盖 animateInt/Color/Dp/ValueAsState、Easing、RepeatMode、animateContentSize。",
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
                Column(
                    spacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            text = "Spec: ${specKindState.value.label}",
                            onClick = {
                                specKindState.value = nextAnimationSpecKind(specKindState.value)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_KIND_TOGGLE),
                        )
                        Button(
                            text = if (specTargetState.value) "目标: End" else "目标: Start",
                            variant = ButtonVariant.Outlined,
                            onClick = { specTargetState.value = !specTargetState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_TARGET_TOGGLE),
                        )
                    }
                    Row(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            text = if (easingLinearState.value) "Easing: Linear" else "Easing: FastOutSlowIn",
                            variant = ButtonVariant.Outlined,
                            onClick = { easingLinearState.value = !easingLinearState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_EASING_TOGGLE),
                        )
                        Button(
                            text = if (repeatModeReverseState.value) "Repeat: Reverse" else "Repeat: Restart",
                            variant = ButtonVariant.Outlined,
                            onClick = { repeatModeReverseState.value = !repeatModeReverseState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_REPEAT_MODE_TOGGLE),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Column(
                            spacing = 4.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Float = ${floatValueState.value.format2()}",
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE),
                            )
                            Text(
                                text = "Int = ${intValueState.value}",
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_INT_VALUE),
                            )
                            Text(
                                text = "Dp(px) = ${dpValueState.value}",
                                modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_DP_VALUE),
                            )
                            Text(
                                text = "Color = #${colorValueState.value.toUInt().toString(16).uppercase()}",
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
                            text = if (vectorTargetState.value) "Vector: Reset" else "Vector: Target",
                            onClick = { vectorTargetState.value = !vectorTargetState.value },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_VECTOR_TOGGLE),
                        )
                        Text(
                            text = "Vec2(x=${vectorValueState.value.x.format2()}, y=${vectorValueState.value.y.format2()})",
                            modifier = Modifier
                                .weight(1f)
                                .testTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE),
                        )
                    }
                    Button(
                        text = if (sizeExpandedState.value) "收起 Size 块" else "展开 Size 块",
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
                            Text(text = "animateContentSize 兼容入口")
                            if (sizeExpandedState.value) {
                                Text(
                                    text = "展开内容 A",
                                    modifier = Modifier.testTag(DemoTestTags.ANIMATION_SPEC_SIZE_PROBE),
                                )
                                Text(text = "展开内容 B")
                                Text(text = "展开内容 C")
                            }
                        }
                    }
                }
            }

            "transition_matrix" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Transition + VisibilityState",
                subtitle = "覆盖 updateTransition、MutableTransitionState 与 Row/Column 轴向显隐。",
            ) {
                val transition = updateTransition(
                    targetState = transitionState.value,
                    label = "demo_transition",
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
                Button(
                    text = if (transitionState.value) "切到主状态" else "切到替代状态",
                    onClick = { transitionState.value = !transitionState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_TRANSITION_TOGGLE),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "alpha=${transitionAlphaState.value.format2()}",
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_ALPHA),
                        )
                        Text(
                            text = "int=${transitionIntState.value}",
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_INT),
                        )
                        Text(
                            text = "dp(px)=${transitionDpState.value}",
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_DP),
                        )
                        Text(
                            text = "color=#${transitionColorState.value.toUInt().toString(16).uppercase()}",
                            color = transitionColorState.value,
                            modifier = Modifier.testTag(DemoTestTags.ANIMATION_TRANSITION_COLOR),
                        )
                    }
                }
                Button(
                    text = if (mutableVisibilityState.targetState) "VisibilityState 目标=false" else "VisibilityState 目标=true",
                    onClick = {
                        mutableVisibilityState.targetState = !mutableVisibilityState.targetState
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp, bottom = 8.dp)
                        .testTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_TOGGLE),
                )
                Text(
                    text = "current=${mutableVisibilityState.currentState}, target=${mutableVisibilityState.targetState}, idle=${mutableVisibilityState.isIdle}",
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
                        Text(text = "MutableTransitionState 驱动的内容")
                    }
                }
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 10.dp),
                ) {
                    Button(
                        text = if (rowAxisVisibleState.value) "隐藏 Row 轴向块" else "显示 Row 轴向块",
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
                            Text(text = "Row Axis")
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
                        text = if (columnAxisVisibleState.value) "隐藏 Column 轴向块" else "显示 Column 轴向块",
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
                            Text(text = "Column Axis")
                        }
                    }
                }
            }

            "infinite_animatable" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "Infinite + Animatable",
                subtitle = "覆盖 rememberInfiniteTransition/infiniteRepeatable 与 Animatable 控制链路。",
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "demo_infinite")
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
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = if (infinitePulseState.value) "关闭 Infinite 脉冲" else "开启 Infinite 脉冲",
                        onClick = { infinitePulseState.value = !infinitePulseState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_INFINITE_RUN_TOGGLE),
                    )
                    Button(
                        text = if (infiniteReverseState.value) "Repeat=Reverse" else "Repeat=Restart",
                        variant = ButtonVariant.Outlined,
                        onClick = { infiniteReverseState.value = !infiniteReverseState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_INFINITE_REPEAT_MODE),
                    )
                }
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = infiniteScaleState.value,
                            scaleY = infiniteScaleState.value,
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Infinite scale = ${infiniteScaleState.value.format2()}",
                        modifier = Modifier.testTag(DemoTestTags.ANIMATION_INFINITE_VALUE),
                    )
                }
                Text(
                    text = "Animatable 控制面板",
                    style = UiTextStyle(fontSizeSp = 15.sp),
                    modifier = Modifier.margin(top = 10.dp, bottom = 6.dp),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        text = "animateTo 1.0",
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.AnimateToHigh
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_TO_HIGH),
                    )
                    Button(
                        text = "animateTo 0.0",
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
                        text = "snapTo 1.0",
                        variant = ButtonVariant.Outlined,
                        onClick = {
                            animatableCommandState.value = AnimatableCommand.SnapToHigh
                            animatableCommandNonceState.value = animatableCommandNonceState.value + 1
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.ANIMATION_ANIMATABLE_SNAP_HIGH),
                    )
                    Button(
                        text = "snapTo 0.0",
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
                    text = "停止当前 Animatable 任务",
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
                        text = "Animatable value = ${animatable.asState.value.format2()}",
                        modifier = Modifier.testTag(DemoTestTags.ANIMATION_ANIMATABLE_VALUE),
                    )
                }
            }

            else -> VerificationNotesSection(
                what = "Animation 页已覆盖 viewcompose-animation 对业务暴露的全部平台无关 API。",
                howToVerify = listOf(
                    "Core：animateFloatAsState + AnimatedVisibility(visible) 默认语义。",
                    "Content：AnimatedContent + Crossfade 文案切换。",
                    "List Motion：lazyContainerMotion 行为保持稳定。",
                    "Specs：animateInt/Color/Dp/ValueAsState + AnimationSpec/Easing/RepeatMode + animateContentSize。",
                    "Transition：updateTransition + MutableTransitionState + Row/Column 轴向显隐 + enter/exit 组合。",
                    "Infinite：rememberInfiniteTransition + infiniteRepeatable + Animatable animateTo/snapTo。",
                ),
                expected = listOf(
                    "0/1/2 旧标签行为不回退，索引兼容。",
                    "每个 API 族均有 demo 锚点与可自动化断言的 testTag。",
                    "动画值更新在状态变更后可预测且无同帧写后读异常。",
                ),
            )
        }
    }
}

private enum class AnimationSpecKind(
    val label: String,
) {
    Tween("Tween"),
    Spring("Spring"),
    Keyframes("Keyframes"),
    Snap("Snap"),
    Repeatable("Repeatable"),
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
