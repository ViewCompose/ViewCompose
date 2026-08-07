package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.modifier.MinHeightModifierElement
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.PopupAlignment
import com.viewcompose.ui.foundation.PopupBounds
import com.viewcompose.ui.foundation.PopupOverflowPolicy
import com.viewcompose.ui.foundation.PopupPositioner
import com.viewcompose.ui.foundation.PopupSize
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiStateColor
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.foundation.createSaveableStateRegistry

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
            Button(text = "Confirm", onClick = {})
        }
    }.single()
    val spec = node.spec as ButtonNodeProps

    check(spec.minHeight == 48.dp)
    check(spec.visualHeight == 40.dp)
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
