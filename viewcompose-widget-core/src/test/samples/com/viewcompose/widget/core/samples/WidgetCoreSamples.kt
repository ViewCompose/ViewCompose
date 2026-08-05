package com.viewcompose.widget.core.samples

import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.Image
import com.viewcompose.widget.core.PopupAlignment
import com.viewcompose.widget.core.PopupBounds
import com.viewcompose.widget.core.PopupOverflowPolicy
import com.viewcompose.widget.core.PopupPositioner
import com.viewcompose.widget.core.PopupSize
import com.viewcompose.widget.core.ProvideImageLoader
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiStateColor
import com.viewcompose.widget.core.UiTheme
import com.viewcompose.widget.core.UiThemeDefaults
import com.viewcompose.widget.core.buildVNodeTree
import com.viewcompose.widget.core.createSaveableStateRegistry

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
            )
        }
    }

    check(nodes.size == 3)
    check((nodes.first().spec as ImageNodeSpec).imageLoader === loader)
}
