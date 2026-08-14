package com.viewcompose.ui.foundation.samples

import com.viewcompose.graphics.core.Brush
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.BasicButton
import com.viewcompose.ui.foundation.BasicButtonStyle
import com.viewcompose.ui.foundation.BasicSurface
import com.viewcompose.ui.foundation.BasicSurfaceStyle
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.CompositionEffectContext
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.InputControlDefaults
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.PopupAlignment
import com.viewcompose.ui.foundation.PopupBounds
import com.viewcompose.ui.foundation.PopupOverflowPolicy
import com.viewcompose.ui.foundation.PopupPositioner
import com.viewcompose.ui.foundation.PopupSize
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.ProvideSaveableStateRegistry
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiStateColor
import com.viewcompose.ui.foundation.UiSwitchSizing
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.foundation.createSaveableStateRegistry
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.rememberCoroutineScope
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberUpdatedState
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.MinHeightModifierElement
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope

fun emittedContentClosureSample() {
    val status = "Ready"
    val node = buildVNodeTree {
        emit(
            type = NodeType.Box,
            key = "status-container",
            spec = BoxNodeProps(contentAlignment = BoxAlignment.Center),
        ) {
            Text(status)
        }
    }.single()

    check(node.type == NodeType.Box)
    check(node.key == "status-container")
    check(node.children.single().type == NodeType.Text)
}

private data class RevisionSampleRow(
    val id: Long,
    val version: Int,
    val label: String,
)

fun lazyCollectionRevisionSample() {
    val rows = listOf(RevisionSampleRow(id = 7L, version = 3, label = "Ready"))
    val list = buildVNodeTree {
        LazyColumn(
            items = rows,
            key = RevisionSampleRow::id,
            contentType = { "status-row" },
            contentRevision = RevisionSampleRow::version,
        ) { row ->
            Text(row.label)
        }
    }.single()
    val item = (list.spec as LazyColumnNodeProps).items.single()

    check(item.key == 7L)
    check(item.contentType == "status-row")
    check(item.contentRevision == 3)
}

fun pagerAndTabIdentitySample() {
    val tree = buildVNodeTree {
        HorizontalPager(currentPage = 0, onPageChanged = {}) {
            Page(key = "account", contentType = "account-page", contentRevision = 4) {
                Text("Account")
            }
        }
        TabRow(selectedIndex = 0, onTabSelected = {}) {
            Tab(key = "overview", contentRevision = 2) { selected ->
                Text(if (selected) "Overview selected" else "Overview")
            }
        }
    }
    val page = (tree.first().spec as HorizontalPagerNodeProps).pages.single()

    check(page.key == "account")
    check(page.contentRevision == 4)
    check(tree.last().children.single().key == "overview")
}

fun themeStateColorSample() {
    val colors = UiStateColor(
        defaultColor = 0xFF333333.toInt(),
        disabledColor = 0x61333333,
        pressedColor = 0xFF111111.toInt(),
        selectedColor = 0xFF0066CC.toInt(),
    )

    check(colors.resolve(selected = true) == 0xFF0066CC.toInt())
    check(colors.resolve(enabled = false, pressed = true) == 0x61333333)
}

fun themeProviderSample() {
    val customPrimary = 0xFF336699.toInt()
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(colors = defaults.colors.copy(primary = customPrimary))
    }
    var observedPrimary = 0

    buildVNodeTree {
        UiTheme(tokens = tokens) {
            observedPrimary = Theme.colors.primary
        }
    }

    check(observedPrimary == customPrimary)
}

fun buttonSample() {
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(
                button = defaults.controls.button.copy(
                    mediumHeight = 48.dp,
                    mediumVisualHeight = 40.dp,
                ),
            ),
        )
    }

    val node = buildVNodeTree {
        UiTheme(tokens) {
            Button(
                text = "Confirm",
                onClick = {},
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1AFFFFFF,
                    focusedColor = 0x1AFFFFFF,
                    hoveredColor = 0x14FFFFFF,
                ),
            )
        }
    }.single()
    val spec = node.spec as ButtonNodeProps

    check(spec.minHeight == 48.dp)
    check(spec.visualHeight == 40.dp)
    check(spec.stateLayerColors?.hoveredColor == 0x14FFFFFF)
}

fun switchSizingTokenSample() {
    val compactSwitch = UiSwitchSizing(
        trackWidth = 44.dp,
        trackHeight = 24.dp,
        thumbDiameter = 18.dp,
        trackPadding = 3.dp,
        labelSpacing = 14.dp,
    )
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(
                switch = compactSwitch,
                minimumInteractiveHeight = 48.dp,
            ),
        )
    }

    var resolvedSizing = UiSwitchSizing.default()
    buildVNodeTree {
        UiTheme(tokens) {
            resolvedSizing = InputControlDefaults.switchSizing()
        }
    }

    check(resolvedSizing == compactSwitch)
    check(tokens.controls.minimumInteractiveHeight == 48.dp)
}

fun basicSurfaceSample() {
    val node = buildVNodeTree {
        BasicSurface(
            style = BasicSurfaceStyle(
                fill = Brush.SolidColor(0xFF1E4D5A.toInt()),
                shape = UiShape.continuous(16.dp),
                borderWidth = 1.dp,
                borderColor = 0xFF8FD8E8.toInt(),
                clipContent = true,
            ),
            contentColor = 0xFFFFFFFF.toInt(),
            onClick = {},
            stateLayerColors = UiStateLayerColors(
                pressedColor = 0x33FFFFFF,
                focusedColor = 0x2AFFFFFF,
                hoveredColor = 0x1FFFFFFF,
            ),
            minimumHeight = 48.dp,
            visualHeight = 40.dp,
        ) {
            Text("Open")
        }
    }.single()

    val surface = node.spec as SurfaceNodeProps
    check(surface.minimumHeight == 48.dp)
    check(surface.visualHeight == 40.dp)
}

fun basicButtonSample() {
    val tree = buildVNodeTree {
        BasicButton(
            text = "Continue",
            onClick = {},
            style = BasicButtonStyle(
                surface = BasicSurfaceStyle(
                    fill = Brush.SolidColor(0xFF244C5A.toInt()),
                    shape = UiShape.continuous(20.dp),
                    clipContent = true,
                ),
                contentColor = 0xFFFFFFFF.toInt(),
                textStyle = com.viewcompose.ui.foundation.UiTextStyle(fontSizeSp = 14.sp),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x33FFFFFF,
                    focusedColor = 0x2AFFFFFF,
                    hoveredColor = 0x1FFFFFFF,
                ),
                minimumHeight = 48.dp,
                visualHeight = 40.dp,
                paddingHorizontal = 16.dp,
            ),
        )
    }

    check(tree.single().type == com.viewcompose.ui.node.NodeType.Surface)
}

fun compactInputTargetSample() {
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(minimumInteractiveHeight = 48.dp),
        )
    }

    val nodes = buildVNodeTree {
        UiTheme(tokens) {
            Checkbox(text = "Share diagnostics", checked = true, onCheckedChange = {})
            Slider(
                value = 50,
                onValueChange = {},
                thumbColor = 0xFF6750A4.toInt(),
                trackColor = 0xFF6750A4.toInt(),
                inactiveTrackColor = 0xFFE8DEF8.toInt(),
            )
        }
    }

    nodes.forEach { node ->
        val target = node.modifier.elements.first() as MinHeightModifierElement
        check(target.minHeight == 48.dp)
    }
}

fun popupPositioningSample() {
    val position = PopupPositioner.calculate(
        anchorBounds = PopupBounds(left = 40, top = 40, right = 80, bottom = 64),
        popupSize = PopupSize(width = 72, height = 48),
        viewportBounds = PopupBounds(left = 0, top = 0, right = 120, bottom = 100),
        alignment = PopupAlignment.BelowStart,
        layoutDirection = UiLayoutDirection.Ltr,
        overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
        windowMargin = 8,
    )

    check(position.x == 40)
    check(position.resolvedAlignment == PopupAlignment.AboveStart)
}

fun saveableStateRegistrySample() {
    val registry = createSaveableStateRegistry(
        restoredValues = mapOf("counter" to 3),
        canBeSaved = { value -> value == null || value is Int },
    )
    val restored = checkNotNull(registry.claimRestored("counter"))
    check(restored.value == 3)
    restored.commit()

    var counter = 4
    val entry = registry.registerProvider("counter") { counter }
    counter = 5
    check(registry.performSave()["counter"] == 5)
    entry.unregister()
}

fun scopedRememberSaveableSample() {
    val registry = createSaveableStateRegistry()
    buildVNodeTree {
        ProvideSaveableStateRegistry(registry) {
            LazyColumn {
                items(
                    items = listOf("inbox", "archive"),
                    key = { mailbox -> mailbox },
                ) { mailbox ->
                    val expanded = rememberSaveable(key = "expanded") {
                        mutableStateOf(false)
                    }
                    Text("$mailbox expanded=${expanded.value}")
                }
            }
        }
    }
}

fun imageLoadingSample() {
    val loader = UiImageLoader { _, _ -> UiImageLoadHandle {} }
    val nodes = buildVNodeTree {
        ProvideImageLoader(loader) {
            Image(
                source = ImageSource.Resource(1),
                contentDescription = "Profile photo",
                requestOptions = UiImageRequestOptions(
                    decodeSize = UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp),
                ),
            )
            Icon(
                source = ImageSource.Url("https://example.com/status.png"),
                contentDescription = "Online",
            )
            IconButton(
                icon = ImageSource.Resource(2),
                contentDescription = "Close",
                onClick = {},
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x1A000000,
                    focusedColor = 0x1A000000,
                    hoveredColor = 0x14000000,
                ),
            )
        }
    }

    check(nodes.size == 3)
    check((nodes.first().spec as ImageNodeSpec).imageLoader === loader)
}

/** Publishes a committed value only when its explicit revision changes. */
fun UiTreeBuilder.sideEffectSample(
    revision: Long,
    publish: (Long) -> Unit,
) {
    SideEffect(revision) {
        publish(revision)
    }
}

/** Registers one listener and pairs it with mandatory cleanup. */
fun UiTreeBuilder.disposableEffectSample(
    source: Any,
    subscribe: () -> (() -> Unit),
) {
    DisposableEffect(source) {
        val unsubscribe = subscribe()
        onDispose(unsubscribe)
    }
}

/** Loads suspending data for one request identity. */
fun UiTreeBuilder.launchedEffectSample(
    requestId: String,
    load: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(requestId, block = load)
}

/** Keeps a long-lived subscription pointed at the latest event callback. */
fun UiTreeBuilder.rememberUpdatedStateSample(
    source: Any,
    onEvent: (String) -> Unit,
    subscribe: ((String) -> Unit) -> (() -> Unit),
) {
    val currentOnEvent = rememberUpdatedState(onEvent)
    DisposableEffect(source) {
        val unsubscribe = subscribe { value ->
            currentOnEvent.value(value)
        }
        onDispose(unsubscribe)
    }
}

/** Exposes a composition-owned scope to an event-binding adapter after commit. */
fun UiTreeBuilder.rememberCoroutineScopeSample(
    bind: (CoroutineScope) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SideEffect(scope) {
        bind(scope)
    }
}

/** Produces observable state from one keyed suspending request. */
fun UiTreeBuilder.produceStateSample(
    requestId: String,
    load: suspend () -> String,
) = produceState(initialValue = "Loading", requestId) {
    value = load()
}

/** Marks a callback owned by a custom effect integration without restoring composition locals. */
fun compositionEffectContextSample(
    callback: () -> Unit,
) {
    CompositionEffectContext.run(callback)
}
