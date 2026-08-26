# Modifier Architecture

## 1. Scope

This document defines the current boundaries between `Modifier`, component `NodeSpec`, and
`Theme/Defaults`. New capabilities must have one unambiguous owner instead of mixing semantics
across layers.

## 2. Current baseline (2026-08)

1. The identity entry is `Modifier`; `Modifier.Empty` has been removed.
2. Historical text-semantic modifiers such as `textColor/textSize` have been removed.
3. `weight/align/FlexibleSpacer` are exposed only through `RowScope/ColumnScope/BoxScope`.
4. System-bar and IME adaptation uses physical `Modifier.systemBarsInsetsPadding(...)` /
   `Modifier.imeInsetsPadding(...)` or their direction-aware `Relative` forms. An Activity using
   `adjustResize` normally does not add IME padding, which would move content twice.
5. Collection policies are container parameters: `reusePolicy` (`sharePool`) and `motionPolicy`
   (`disableItemAnimator/animateInsert/animateRemove/animateMove/animateChange`). Pager residency
   and direct user input remain pager parameters.
6. Focused-editor visibility is an invariant of a real Android scroll owner, not a Modifier or
   container Boolean. LazyColumn, LazyVerticalGrid, and ScrollableColumn preserve native
   child-rectangle propagation even when direct user scrolling is disabled.
7. HorizontalPager and VerticalPager own discrete page selection only. A page that can be obscured
   by the IME declares its own page-local scroll owner; the pager never interprets within-page
   coordinates as page motion.
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
17. Physical `padding/margin/offset` and inset selectors remain physical. Their `Relative`
    counterparts resolve start/end from the VNode's captured layout direction on every bind. A
    later physical or relative declaration replaces the earlier declaration for that complete
    modifier family.

## 3. Source-owned capability Reference

The exhaustive application-facing inventory is generated from production source and published in
the [Capability Reference](https://docs.viewcompose.com/reference/). Raw Kotlin KDoc and Java
Javadoc remain available in the [versioned API Reference](https://docs.viewcompose.com/api/). This
architecture page owns behavioral boundaries and invariants;
it does not duplicate a symbol table or an independently maintained count.

The generated model applies these contracts:

1. public and protected DSL, Modifier, component, host, integration, and tooling entries are
   discovered from the published production source sets;
2. every discovered entry belongs to exactly one user-capability group and carries its symbol,
   overload count, artifact, namespace, release lane, module manual, and versioned API root;
3. internal packages, private/internal declarations, Demo code, tests, generated code, and
   renderer-only helpers are excluded from the application catalog;
4. the website, inventory counts, and Governance V2 stale-output gate consume the same committed
   JSON model; changing source, signatures, versions, or structured ownership without regenerating
   that model fails `verifyDocumentationGovernanceV2`; and
5. maintainers intentionally refresh the committed model with
   `./gradlew updateDocumentationCapabilityReference` and review the resulting semantic diff.

Exact capability, sample, and related-document links are populated only by valid Governance V2
records. Until the frozen ownership debt is migrated, the generated page reports structured-owner
coverage separately instead of guessing or hiding the gap.

### 3.1 Advanced-shadow example and constraints


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

### 3.2 Generation and consistency

1. The production scanner and the Reference generator are one model; there is no second Modifier
   scan or handwritten total to reconcile.
2. Each application-facing entry has one generated catalog group. Duplicate or missing structured
   capability ownership remains visible as Governance V2 debt rather than entering this page.
3. The committed catalog is deterministic and byte-compared during documentation verification.
4. Module manuals explain artifact contracts, the Capability Reference supports discovery, and
   Dokka remains the exhaustive signature and KDoc owner.

## 4. Role boundaries

### 4.1 Modifier: general outer decoration

Modifier owns:

1. dimensions and occupancy:
   `size/width/height/minWidth/minHeight/maxWidth/maxHeight/aspectRatio/padding/paddingRelative/margin/marginRelative`;
2. appearance: `backgroundColor/backgroundDrawableRes/border/cornerRadius/alpha/elevation`;
3. visibility and layering: `visibility/offset/offsetRelative/zIndex`;
4. general interaction, including renderer-neutral `interactionIndication`, focus, keys, and
   accessibility;
5. test identity through `testTag`;
6. physical or direction-aware system-bar and IME padding;
7. the `nativeView` escape hatch;
8. drawing, gesture, nested-scroll, shadow, and layout-size-animation decoration.

Collection reuse/motion remains container policy rather than Modifier data. Focused-editor
visibility has no opt-in parameter: it follows the native child-rectangle contract of the nearest
real scroll owner.

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

General feedback does not become a component field merely because a native View draws it.
`Modifier.interactionIndication(UiInteractionIndication.StateLayer(...))` carries complete pressed,
focused, and hovered colors in modifier order. High-level components resolve this value from their
design-system recipe and typed overrides before installing it. Native-backed components with
multiple internal targets may retain typed selected/unselected state-layer snapshots in their
NodeSpec because one outer modifier cannot identify those internal targets.

### 4.4 Theme / Defaults: default sources

The fixed path is `Theme -> design-system recipe or Defaults -> typed overrides ->
NodeSpec/Modifier -> Renderer`.

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

`maxWidth`, `maxHeight`, and `aspectRatio` are portable intent, not raw Android setters. The
Android renderer folds them into one synthetic `LayoutConstraintHost` around the complete node so
their order does not create stacked wrappers. Invalid positive/finite values fail in the contract;
declared exact/minimum values above a declared maximum fail before rendering. During measurement,
an incoming exact parent constraint remains authoritative; otherwise the declared maximum applies,
and an aspect ratio is preserved whenever the resulting min/max interval is feasible. A custom
renderer must provide the same one-boundary behavior before accepting these elements.

## 8. Change gate

A Modifier-boundary change includes:

1. an update to this document;
2. regression coverage for the corresponding `NodeSpec/renderer` path;
3. a Demo path and any required UI test.

See [Development workflow](../project/workflow.md).

## 9. Related documents

1. [NodeSpec-only specification](node-spec.md)
2. [Theme runtime architecture](theming.md)
3. [Architecture overview](overview.md)
4. [Focus and input](../guides/focus-and-input.md)
5. [Nested scrolling](../guides/nested-scroll.md)
