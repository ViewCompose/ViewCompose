package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.shape

/**
 * Emits a dot or labeled badge using one resolved appearance snapshot.
 *
 * A `null` [count] emits a dot, a non-positive count emits nothing, and values above 99 display
 * `99+`. Appearance resolves from [BadgeDefaults], nested [ProvideBadgeOverrides] scopes, and
 * instance [overrides].
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the badge when [count] permits one
 * @param count optional numeric content; `null` selects a dot and non-positive values omit output
 * @param overrides sparse instance appearance applied after scoped Badge overrides
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after resolved badge geometry and clipping
 */
fun UiTreeBuilder.Badge(
    count: Int? = null,
    overrides: BadgeOverrides = BadgeOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    if (count != null && count <= 0) return
    val appearance = BadgeDefaults.resolve(overrides)
    if (count == null) {
        Box(
            key = key,
            modifier = Modifier
                .size(width = appearance.dotSize, height = appearance.dotSize)
                .backgroundColor(appearance.containerColor)
                .shape(appearance.shape)
                .clip()
                .then(modifier),
        ) {}
    } else {
        val displayText = if (count > 99) "99+" else count.toString()
        Box(
            key = key,
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .height(appearance.pillHeight)
                .minWidth(appearance.pillMinWidth)
                .backgroundColor(appearance.containerColor)
                .shape(appearance.shape)
                .clip()
                .padding(horizontal = appearance.pillHorizontalPadding)
                .then(modifier),
        ) {
            Text(
                text = displayText,
                style = appearance.textStyle,
                color = appearance.contentColor,
            )
        }
    }
}

/**
 * Overlays badge content at the logical top-end corner of primary content.
 *
 * Both regions remain eager children of one [Box]. The badge is declared last and therefore draws
 * above primary content without affecting its measurement.
 *
 * @sample com.viewcompose.ui.foundation.samples.contentDslSample
 * @receiver active tree builder receiving the badged composite
 * @param badge content emitted into the top-end overlay region
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration applied to the shared overlay container
 * @param content primary content measured by the shared box
 */
fun UiTreeBuilder.BadgedBox(
    badge: UiTreeBuilder.() -> Unit,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    Box(key = key, modifier = modifier) {
        content()
        Box(
            modifier = Modifier.align(BoxAlignment.TopEnd),
        ) {
            badge()
        }
    }
}
