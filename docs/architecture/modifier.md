# Modifier Architecture

## 1. Scope

This document defines the current boundaries between `Modifier`, component `NodeSpec`, and
`Theme/Defaults`. New capabilities must have one unambiguous owner instead of mixing semantics
across layers.

## 2. Current baseline (2026-08)

1. The identity entry is `Modifier`; `Modifier.Empty` has been removed.
2. Historical text-semantic modifiers such as `textColor/textSize` have been removed.
3. `weight/align/FlexibleSpacer` are exposed only through `RowScope/ColumnScope/BoxScope`.
4. System-bar and IME adaptation uses `Modifier.systemBarsInsetsPadding(...)` and
   `Modifier.imeInsetsPadding(...)`. An Activity using `adjustResize` normally does not add
   `imeInsetsPadding`, which would move content twice.
5. Collection policies are container parameters: `reusePolicy` (`sharePool`) and `motionPolicy`
   (`disableItemAnimator/animateInsert/animateRemove/animateMove/animateChange`).
6. Keyboard focus following is the vertical-container parameter `focusFollowKeyboard`, implemented
   by `LazyColumn`, `LazyVerticalGrid`, `VerticalPager`, and `ScrollableColumn`.
7. `LazyRow`, `HorizontalPager`, and `ScrollableRow` do not expose `focusFollowKeyboard`; an API that
   can be called but has no effect is prohibited.
8. `Modifier.backgroundDrawableRes(resId)` installs a drawable background. It takes precedence over
   `backgroundColor`, clips automatically with `cornerRadius`, and can still be forced through the
   general `clip()` switch.
9. `Modifier.animateContentSize(...)` causes the renderer to insert an `AnimatedSizeHost` before
   patching. It interpolates measured dimensions and participates in parent layout rather than
   applying a graphicsLayer scale. Easing, spring, keyframes, repeat, and reverse terminal semantics
   are preserved.
10. Constraint parent data uses `Modifier.layoutId(...)`, `Modifier.constrainAs(...)`, and
    `Modifier.constrain(...)`, and is meaningful only for `ConstraintLayout` children.
11. Drawing modifiers include `drawBehind`, `drawWithContent`, and `drawWithCache`, plus the `draw`
    and `drawCache` shorthands. Chain order is stable, `drawWithContent` controls content forwarding,
    and the executor preserves four-corner `DrawRoundRect` and `Drawable + DrawPaint` semantics.
12. Declarative focus and hardware-key input uses
    `focusable/focusRequester/focusProperties/focusGroup/onFocusChanged/onPreviewKeyEvent/onKeyEvent`.
    It maps to native View focus search, while `LocalFocusManager` supplies session-scoped move and
    clear operations.
13. `Modifier.nestedScroll(connection, dispatcher)` maps the unified protocol through a transparent
    AndroidX nested-scrolling parent/child host. It covers pre/post scroll, pre/post fling,
    Lazy/Pager/ordinary scrolling containers, and custom drag or transform pan.
14. Advanced `dropShadow(s)` layers draw before node content and `innerShadow(s)` layers draw after
    complete content. Both support ordered layers, independent shapes, blur, spread, offset, and
    color, without coupling to `elevation` or `zIndex`.
15. `Modifier.semantics` transports design-system-neutral accessibility state. Collection parents
    expose logical dimensions and selection cardinality; children expose logical positions and
    spans. RTL changes physical placement, never these indexes, and item `selected`/`heading`
    values remain single-source properties on the same semantic configuration.
16. Native View padding has one renderer owner. Container-specific content padding, resolved
    `Modifier.padding`, and selected system-bar/IME inset edges are composed before writing the
    View; binders must not overwrite another layer's contribution during a patch or environment
    rebind.

## 3. API inventory

### 3.1 Scan baseline (`src/main`)

The inventory is derived from these repository scans:

```bash
rg "^\s*(public\s+)?(internal\s+)?fun\s+(<[^>]+>\s*)?Modifier\.([A-Za-z0-9_]+)\(" --glob "**/src/main/**/*.kt"
rg "^\s*(public\s+)?(internal\s+)?fun\s+(RowScope|ColumnScope|BoxScope|ConstraintLayoutScope)\."
```

Current 2026-08 result:

1. `fun Modifier.*` declarations, including overloads and scoped internals: `76`;
2. unique `fun Modifier.*` API names: `62`;
3. scoped modifier declarations: `5` across `RowScope/ColumnScope/BoxScope`;
4. renderer-internal modifier extensions: `1`, used only for resolution.

### 3.2 Architecture groups

1. `ui-contract general decoration`: platform-neutral `Modifier` contracts.
2. `gesture input`: gesture DSL backed by gesture state and the policy core.
3. `graphics drawing`: drawing-stage APIs, including `draw*` shorthands.
4. `graphics shadow decoration`: platform-neutral shadow specifications executed by the Android
   decoration layer.
5. `animation size`: the layout-level `animateContentSize` transition.
6. `host-android interop`: Android escape hatches such as `nativeView/android*`.
7. `renderer internal resolution`: framework-only APIs that application code cannot depend on.

### 3.3 Global Modifier APIs, including internal entries

| API | Module / namespace | Visibility | Purpose | Scope | Notes |
| --- | --- | --- | --- | --- | --- |
| `padding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Content padding | Global | Three overloads: all, horizontal/vertical, four edges |
| `margin` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Outer layout-param margin | Global | Three overloads |
| `size` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Width and height | Global | Fixed framework-unit semantics |
| `width` / `height` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | One dimension | Global | Cooperates with parent layout rules |
| `minWidth` / `minHeight` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Minimum dimension | Global | Maps to native minimum size |
| `fillMaxWidth` / `fillMaxHeight` / `fillMaxSize` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Fill parent dimension(s) | Global | Maps to `MATCH_PARENT` semantics |
| `offset` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Translation offset | Global | Maps to `translationX/translationY` |
| `layoutId` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Child layout identity | Container-specific | Primarily matches `ConstraintLayout` children |
| `systemBarsInsetsPadding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | System-bar inset padding | Container-aware | Individual edge switches |
| `imeInsetsPadding` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | IME inset padding | Container-aware | Bottom only by default |
| `backgroundColor` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Color background | Global | Lower priority than drawable background |
| `backgroundDrawableRes` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Drawable resource background | Global | Auto-clips with corner radius |
| `border` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Border width and color | Global | Uses the surface-style pipeline |
| `cornerRadius` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Rounded corners | Global | Uniform, top/bottom, or four-corner overloads |
| `clip` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Force content clipping | Global | Common with shapes and custom drawing |
| `alpha` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Opacity | Global | `graphicsLayer.alpha` wins on conflict |
| `elevation` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Platform elevation | Global | Maps to `View.elevation` |
| `zIndex` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Sibling drawing order | Global | Currently maps to `translationZ` |
| `graphicsLayer` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Unified transform/layer properties | Global | Advanced visual-transform entry |
| `visibility` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Visible/Invisible/Gone | Global | Participates in layout occupancy |
| `clickable` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Basic click callback | Global | Cooperates with gesture dispatch |
| `focusable` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Receive focus | Global | Can be overridden by `focusProperties.canFocus` |
| `focusRequester` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Bind stable focus requester | Global | Rebound during reuse, rollback, and release |
| `focusProperties` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Focusability and directional targets | Global | next/previous/four directions |
| `focusGroup` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Keyboard focus group | Container | Native descendant focus/navigation cluster |
| `onFocusChanged` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Observe self/descendant focus | Global | Receives `FocusState` |
| `onPreviewKeyEvent` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Key capture before target | Global | Declarative ancestor to target |
| `onKeyEvent` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Key bubbling after target | Global | Target to declarative ancestor |
| `semantics` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Accessibility and testing state | Global | Includes collection dimensions, logical item positions, selection, and heading state |
| `contentDescription` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Accessibility description | Global | Maps to native semantics |
| `testTag` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Test locator | Global | UI-test targeting |
| `overlayAnchor` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Overlay anchor ID | Capability-specific | Popup/Tooltip/Dropdown anchor |
| `drawBehind` | `ui-contract` and `graphics` | public | Draw before content | Global | Prefer `com.viewcompose.graphics` in applications |
| `drawWithContent` | `ui-contract` and `graphics` | public | Control content/drawing order | Global | Supports foreground/content composition |
| `drawWithCache` | `ui-contract` and `graphics` | public | Build reusable drawing cache | Global | Avoids repeated command construction |
| `draw` / `drawCache` | `ui-contract` and `graphics` | public | Drawing shorthands | Global | Aliases for behind/cache entries |
| `dropShadow` / `dropShadows` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Exact outer shadow layer(s) | Global | Before content; independent of elevation |
| `innerShadow` / `innerShadows` | `viewcompose-ui-contract` / `com.viewcompose.ui.modifier` | public | Exact inner shadow layer(s) | Global | After complete content, clipped to shape |
| `pointerInput` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Raw pointer events | Global | `Consumed` short-circuits later gestures |
| `combinedClickable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Click/double/long click | Global | No callbacks means no-op |
| `draggable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Continuous drag | Global | Delivers deltas to `DraggableState` |
| `anchoredDraggable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Anchored drag/settle | Global | Horizontal or Vertical only |
| `transformable` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Multi-pointer zoom/rotate/pan | Global | Deltas consumed by `TransformableState` |
| `gesturePriority` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Gesture arbitration priority | Global | Resolves nested competition |
| `nestedScroll` | `viewcompose-gesture` / `com.viewcompose.gesture` | public | Parent/child scroll and fling protocol | Global | AndroidX transparent host |
| `animateContentSize` | `viewcompose-animation` / `com.viewcompose.animation` | public | Layout size transition | Layout-aware | Real parent re-layout, not visual scaling |
| `constrainAs` / `constrain` | `viewcompose-constraintlayout-androidx` | public | Constraint parent data | Container-specific | `ConstraintLayout` children only |
| `nativeView` | `viewcompose-host-android` / `com.viewcompose.host.android` | public | Configure native Android View | Android interop | Escape hatch around general semantics |
| `androidAnimation` | `viewcompose-host-android` / animation namespace | public | Android animation interop | Android interop | Alias over `nativeView` |
| `androidGraphics` | `viewcompose-host-android` / graphics namespace | public | Android graphics interop | Android interop | Alias over `nativeView` |
| `resolve` | `viewcompose-renderer-android` / `com.viewcompose.renderer.modifier` | internal | Resolve to `ResolvedModifiers` | Renderer internal | Not an application dependency |

### 3.4 Scoped Modifier APIs

| API | Scope | Module / namespace | Purpose | Constraints |
| --- | --- | --- | --- | --- |
| `weight` | `RowScope` | `viewcompose-ui-foundation` | Horizontal linear weight | `weight > 0` |
| `align` | `RowScope` | `viewcompose-ui-foundation` | Cross-axis vertical alignment | `VerticalAlignment` |
| `weight` | `ColumnScope` | `viewcompose-ui-foundation` | Vertical linear weight | `weight > 0` |
| `align` | `ColumnScope` | `viewcompose-ui-foundation` | Cross-axis horizontal alignment | `HorizontalAlignment` |
| `align` | `BoxScope` | `viewcompose-ui-foundation` | Child position in Box | `BoxAlignment` |
| `constrainAs / constrain` | `ConstraintLayout` child context | `viewcompose-constraintlayout-androidx` | Constraint parent data | Global extension, meaningful only under `ConstraintLayout` |

### 3.5 Advanced-shadow example and constraints

```kotlin
val cardShape = UiShape.rounded(20.dp)

Surface(
    modifier = Modifier
        .shape(cardShape)
        .dropShadows(
            shadows = listOf(
                UiShadow(
                    color = 0x33000000,
                    blurRadius = 12.dp,
                    offsetY = 5.dp,
                ),
                UiShadow(
                    color = 0x223B82F6,
                    blurRadius = 18.dp,
                    spreadRadius = 2.dp,
                    offsetX = (-4).dp,
                ),
            ),
            shape = cardShape,
        ),
) {
    Content()
}
```

1. Use `dropShadow(s)` for exact blur, spread, offset, color, or multiple layers. Continue using
   `elevation` for Material elevation semantics.
2. Pass the same `UiShape` to content and shadow when a stable outline matters. Without an explicit
   shadow shape, resolution uses node `shape/cornerRadius`, then a rectangle.
3. Shadows do not expand layout bounds. Reserve visual space and avoid unnecessary clipping on
   ancestors that are not viewports.
4. Prefer animating translation, scale, rotation, or alpha. Animating blur, spread, shape, or size
   creates new raster keys.
5. See [Advanced shadows](../guides/shadows.md) for backend, cache, and diagnostic rules.

### 3.6 Inventory consistency

1. This document covers every scanned `fun Modifier.*`, including the internal entry.
2. Scoped and global capabilities use separate tables and counts.
3. Duplicate `draw*` entries in `ui-contract` and `graphics` identify the preferred namespace.

## 4. Role boundaries

### 4.1 Modifier: general outer decoration

Modifier owns:

1. dimensions and occupancy: `size/width/height/minWidth/minHeight/padding/margin`;
2. appearance: `backgroundColor/backgroundDrawableRes/border/cornerRadius/alpha/elevation`;
3. visibility and layering: `visibility/offset/zIndex`;
4. general interaction, focus, keys, and accessibility;
5. test identity through `testTag`;
6. system-bar and IME padding;
7. the `nativeView` escape hatch;
8. drawing, gesture, nested-scroll, shadow, and layout-size-animation decoration.

Collection reuse/motion and vertical focus following remain container parameters rather than
Modifier entries.

### 4.2 Scoped Modifier: parent-specific data

Parent-specific layout data is exposed through:

1. `RowScope.weight/align`;
2. `ColumnScope.weight/align`;
3. `BoxScope.align`;
4. `ConstraintLayout` child data: `layoutId/constrainAs/constrain`.

### 4.3 NodeSpec: component semantics

Component semantics belong in parameters and `NodeSpec`, for example:

1. `Text`: `color/style/maxLines/overflow/textAlign`;
2. `Image`: `contentScale/tint/placeholder/error/fallback`;
3. `Button`: `variant/size/enabled/leadingIcon/trailingIcon`;
4. `TextField`: `label/placeholder/supportingText/readOnly/imeAction/isError`.

### 4.4 Theme / Defaults: default sources

The fixed path is `Theme -> Defaults -> NodeSpec -> Renderer`.

Do not encode theme defaults as general modifiers or component business defaults inside the
renderer.

## 5. Placement decision

When adding a property, decide in this order:

1. Is it a stable outer decoration applicable to most nodes?
2. Is it parent-specific layout data?
3. Is it semantic state of one component?
4. Is it a theme/default source?

Place it in the first matching layer and do not duplicate it across layers.

## 6. Anti-patterns

1. Component-specific semantics in general `Modifier`.
2. Parent-specific capabilities in global `Modifier`.
3. Returning first-party long-lived semantics to a dynamic map for convenience.
4. Treating a theme override as a replacement for a component parameter.

## 7. Compose alignment

ViewCompose does not reproduce the Compose runtime or compiler, but keeps the API layers aligned:

1. `Modifier` is the general decoration chain.
2. Parent data is a scoped API.
3. Component semantics are parameters and `NodeSpec`.
4. Theme provides defaults.

## 8. Change gate

A Modifier-boundary change includes:

1. an update to this document;
2. regression coverage for the corresponding `NodeSpec/renderer` path;
3. a Demo path and any required UI test.

See [Development workflow](../project/workflow.md).

## 9. Related documents

1. [NodeSpec-only specification](node-spec.md)
2. [Theming](../guides/theming.md)
3. [Architecture overview](overview.md)
4. [Focus and input](../guides/focus-and-input.md)
5. [Nested scrolling](../guides/nested-scroll.md)
