# Widget ConstraintLayout

`viewcompose-widget-constraintlayout` adds a declarative ConstraintLayout node, child-constraint
modifiers, reusable constraint sets, and AndroidX virtual helpers to ViewCompose.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-widget-constraintlayout:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The DSL and native mapping are available; advanced helper parity may evolve.
- Platform: Android 7.0 (API 24) and newer.
- Optional: `viewcompose-widget-core` does not depend on this artifact.
- Native engine: AndroidX ConstraintLayout and its Guideline, Barrier, Flow, Group, Layer, and
  Placeholder helpers.

## Inline constraints

Create references inside `ConstraintLayout`, attach them to children with `Modifier.constrainAs`,
and connect source anchors in the constraint scope:

```kotlin
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text("Title", Modifier.constrainAs(title) {
        startToStart(parent)
        topToTop(parent)
    })
    Text("Body", Modifier.constrainAs(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    })
}
```

References are string identities local to one layout. The DSL does not validate empty or duplicate
IDs. A repeated source-anchor call replaces its earlier link. `start` and `end` follow layout
direction; top, bottom, and baseline are physical/native anchors.

## Dimensions and positioning

Width and height support wrap content, fill-to-constraints, match parent, and fixed dp dimensions.
Minimum/maximum dp bounds, percent dimensions, constrained wrap content, bias, dimension ratio,
baseline-to-edge links, and circular positioning map to AndroidX ConstraintSet.

Percent dimensions and guideline fractions are clamped to `0f..1f` by the renderer. Other numeric
values and ratio strings are forwarded to AndroidX without DSL-level validation. Circular angles
use Android ConstraintLayout's clockwise degree convention.

## Reusable constraint sets

`constraintSet { ... }` builds an immutable `ConstraintSetSpec` without emitting UI. Pass it to
`ConstraintLayout(constraintSet = set)`. Inline constraints and helpers are merged afterward and win
when the same ID exists in both sources. Repeating a constraint ID within one builder replaces the
earlier entry; helper lists retain declaration order before ID-based renderer merging.

## Virtual helpers

- Guidelines use fixed dp offsets or clamped parent fractions.
- Barriers track logical/physical extremes with margins and gone-widget policy.
- Chains preserve reference order and validate that a supplied weight list has the same size.
- Flow maps orientation, wrapping, styles, biases, alignment, gaps, padding, and maximum wrap count.
- Group propagates visibility and elevation.
- Layer propagates visibility, elevation, rotation, scale, translation, and optional pivots.
- Placeholder hosts one referenced child and defines empty visibility.

Inline helper functions must execute inside `ConstraintLayout { ... }`; calling them elsewhere fails
fast. The reusable builder variants have no such thread-local scope requirement. Flow, Group, and
Layer require at least one reference. Barrier and chain emptiness is currently forwarded.

## Native reconciliation and failures

The native container coalesces rebuild requests and applies the latest merged specification before
measure/layout when necessary. Child and helper string IDs are mapped to stable Android View IDs.
Virtual helper Views are synchronized to the latest helper set rather than exposed as DSL children.

Missing referenced IDs, duplicate inline IDs, overrides, and circular graphs are logged once. A
missing link is skipped; remaining constraints still apply. Native `ConstraintSet.applyTo` failures
are caught and logged so the render session survives, but the resulting layout may retain partial or
previous native state. Treat warnings as authoring errors and test complex graphs on-device.

## Performance guidance

- Reuse a constraint set when the graph is stable and only child content changes.
- Keep reference IDs and helper declaration order stable to avoid native helper churn.
- Prefer simpler containers when constraints do not add value; ConstraintLayout incurs a solver pass.
- Avoid rebuilding large helper graphs from rapidly changing state.

## Related documentation

- [Widget Core module](../viewcompose-widget-core/README.md)
- [Renderer module](../viewcompose-renderer/README.md)
- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-widget-constraintlayout` API tree](https://docs.viewcompose.com/api/viewcompose-widget-constraintlayout/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes string references, inline-over-external merging, complete anchor
and dimension mapping, virtual helpers, coalesced native rebuilds, and warning-based recovery for
invalid graphs. It does not provide a platform-neutral constraint solver.
