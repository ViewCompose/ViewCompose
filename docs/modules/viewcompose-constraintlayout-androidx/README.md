---
schema_version: 2
document_id: module.viewcompose-constraintlayout-androidx
doc_type: module
owner:
  kind: module
  id: viewcompose-constraintlayout-androidx
version_lane: released
capability_ids:
  - constraintlayout.core
  - constraintlayout.helpers
artifact_ids:
  - viewcompose-constraintlayout-androidx
sample_ids:
  - module.constraintlayout-dependency
  - module.constraintlayout-inline
  - module.constraintlayout-dimensions
  - module.constraintlayout-set
  - module.constraintlayout-helpers
coordinate: com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01
minimal_usage_sample_id: module.constraintlayout-dependency
---

# AndroidX ConstraintLayout Integration

`viewcompose-constraintlayout-androidx` adds a declarative ConstraintLayout node, typed child
constraints, immutable constraint sets, and AndroidX-backed virtual helpers. Its public package is
`com.viewcompose.constraintlayout`; the artifact suffix identifies the AndroidX backend.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="constraintlayout-dependency" sample_id="module.constraintlayout-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The source-breaking typed DSL and atomic renderer contract are established;
  later alphas may add capabilities but must not restore partial graph application or string helper
  grammars.
- Platform: Android 7.0 (API 24) and newer; AndroidX ConstraintLayout `2.2.2`.
- Dependency boundary: UI Contract and UI Foundation are API dependencies because their Modifier,
  unit, and builder types appear publicly. Runtime and AndroidX are implementation dependencies.
- Optionality: UI Foundation does not depend on this module. Add it only where the native
  constraint solver or helpers are useful.

## Inline constraints and typed scope

Create references inside `ConstraintLayout`, attach one to each child with `constrainAs`, and link
typed anchors in the constraint scope:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-inline" sample_id="module.constraintlayout-inline" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text(
        "Title",
        modifier = Modifier.constrainAs(title) {
            startToStart(parent)
            topToTop(parent)
        },
    )
    Text(
        "Body",
        modifier = Modifier.constrainAs(body) {
            startToStart(title)
            topToBottom(title, margin = 8.dp)
        },
    )
}
```

References are non-blank identities local to one layout. Duplicate child IDs, helper IDs, and
child/helper collisions reject the complete candidate. A repeated source-anchor declaration
replaces its earlier link. Start/end are logical; left/right, top/bottom, and baseline are physical.
One item cannot mix logical and physical horizontal links. Baseline excludes top/bottom, and circle
placement excludes every edge or baseline link.

`ConstraintLayoutScope` and `ConstraintConstrainScope` are dedicated `@UiDslMarker` receivers.
Horizontal helpers implement only horizontal target types, vertical helpers only vertical target
types, and ordinary child references implement every applicable plane. Cross-axis links and helper
calls leaked through nested scopes therefore fail during Kotlin compilation. A retained scope is
invalid after its content evaluation finishes.

## Dimensions and positioning

Width and height use one mutually exclusive algebra:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-dimensions" sample_id="module.constraintlayout-dimensions" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
width = ConstraintDimension.MatchConstraints(
    mode = ConstraintMatchMode.Percent(0.6f),
    min = 120.dp,
    max = 360.dp,
)
height = ConstraintDimension.Fixed(180.dp)
ratio = ConstraintRatio(width = 16f, height = 9f, constrainedSide = ConstraintRatioSide.Width)
```

Available dimensions are `WrapContent`, `ConstrainedWrapContent`, `Fixed`, and
`MatchConstraints(Spread|Wrap|Percent, min, max)`. Bounds and percentages validate eagerly. A typed
ratio requires positive finite terms and at least one match-constraint axis. Bias and Guideline
fractions use `0f..1f`; circular angles use the finite Android clockwise range `0f..<360f`.
`wrapBehaviorInParent` independently selects contribution to each wrap-content parent axis.

## Reusable constraint sets

`constraintSet` builds an immutable graph without emitting UI. Pass it to
`ConstraintLayout(constraintSet = set)`. Inline constraints and helpers merge afterward and win for
the same constraint ID or same-kind helper ID; duplicate entries inside one source and cross-kind
helper collisions fail before native mutation.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-set" sample_id="module.constraintlayout-set" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
val set = constraintSet {
    val (title, body) = createRefs("title", "body")
    constrain(title) {
        startToStart(parent)
        topToTop(parent)
    }
    constrain(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    }
}
```

Use `Modifier.constrainAs(reference)` for reference-based inline children.
`Modifier.constrain(id, ...)` remains the explicit XML-migration shortcut; the removed string-based
constraint-set builder is not restored.

## Typed virtual helpers

Helpers are declared inside the current layout or reusable set and share the same typed references:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ConstraintLayoutModuleSamples.kt" region="constraintlayout-helpers" sample_id="module.constraintlayout-helpers" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
ConstraintLayout {
    val (hero, metric, status, center, orbit) = createRefs(
        "hero", "metric", "status", "center", "orbit",
    )
    val start = createGuidelineFromStart(0.1f)
    createGrid(
        hero,
        metric,
        status,
        rows = 2,
        columns = 2,
        orientation = ConstraintGridOrientation.Horizontal,
        spans = listOf(ConstraintGridSpan(hero, index = 0, columnSpan = 2)),
        skips = listOf(ConstraintGridSkip(index = 2)),
    )
    createCircularFlow(
        center,
        ConstraintCircularFlowItem(orbit, radius = 48.dp, angle = 90f),
    )
    Text("Hero", modifier = Modifier.constrainAs(hero) { startToStart(start) })
    Text("Metric", modifier = Modifier.constrainAs(metric) {})
    Text("Status", modifier = Modifier.constrainAs(status) {})
    Text("Center", modifier = Modifier.constrainAs(center) {})
    Text("Orbit", modifier = Modifier.constrainAs(orbit) {})
}
```

- Guidelines support physical or logical offsets/fractions; Barriers support all logical and
  physical directions, margin, and gone-widget policy.
- Chains require at least two unique members and typed boundaries. Positive weights, bias, margins,
  and logical-versus-physical horizontal consistency validate before rendering.
- Typed Grid is bounded to `50 x 50` and supports fixed/inferred axes, weights, gaps, orientation,
  spans, and skips. It expands into renderer-owned solver proxies instead of AndroidX Grid's string
  grammar.
- CircularFlow expands typed radius/angle items into ordinary circle constraints and creates no
  helper View. Flow, Group, Layer, and Placeholder map to managed AndroidX helpers.
- Flow and Placeholder are constraint-capable graph nodes. Guideline, Barrier, Group, and Layer
  expose only the target or identity planes they actually support.

## Native ownership and failure behavior

The renderer preflights the complete merged graph before mutation. One registry owns stable native
IDs and all managed Guideline, Barrier, Flow, Group, Layer, Placeholder, and Grid proxy Views. An
accepted candidate is applied from a clean native set; snapshots cover touched IDs, LayoutParams,
helper membership, accessibility, visibility, and transforms.

Missing references, duplicate or colliding IDs, invalid anchor planes, competing item/helper
ownership, helper cycles, and invalid dimensions reject the whole candidate. A native exception
restores the previous registry and View state. Diagnostics are structured and bounded by graph
revision, identity, and reason; individual invalid links are never silently dropped.

Equal and content-only updates return before graph compilation. Scalar updates preserve helper
instances and IDs, avoid live LayoutParams cloning, and request layout at most once. Environment
updates resolve the environment once while retaining topology; topology updates use the complete
staged commit and rollback path. These classifications are renderer behavior, not public tuning
flags.

## Accepted correctness and performance evidence

The final JVM run passed 75/75 UI Contract, 11/11 module DSL, and 451/451 Renderer tests. Device
acceptance passed 8/8 focused cases on API 24 and API 33, retained the earlier 3/3 API-28 helper
matrix and 200-toggle stress, and reviewed 12/12 pairwise screenshots across size, orientation,
theme, direction, and font scale without overlap, clipping, or helper artifacts. The correctness,
typed-safety, rollback, lifecycle, and configuration conclusion is **improved**. Limits are two
focused physical OEM/API points plus emulator coverage and a pairwise set rather than every visual
Cartesian combination.

The controlled released/candidate/direct-AndroidX matrix covered stable, scalar, helper, and
topology changes at 10/50/100 nodes. Seven comparable pairs passed every timing and peak-heap
regression gate; five remained `inconclusive` after the one permitted repeat. Direct AndroidX was
faster at P95 for all twelve candidate actions and at P50 for eleven. The release-safety conclusion
is **no material change**, not whole-frame leadership. Preserve the zero-work and bounded-write fast
paths; rerun the controlled matrix only when production reconciliation changes. Full absolute
results, normalized deltas, CVs, thermal controls, and limitations remain in
[performance tooling](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix).

## Performance guidance

- Reuse a constraint set while topology is stable and only content or scalar values change.
- Keep reference IDs and helper kinds stable so native IDs and helper instances remain reusable.
- Prefer simpler containers when constraints add no value; ConstraintLayout still incurs a solver
  pass.
- Avoid rebuilding large helper graphs from rapidly changing state.
- Do not add a public optimization mode without stable evidence that the classified automatic paths
  are insufficient.

## Alpha source migration

| Removed Alpha source | Replacement |
| --- | --- |
| `ConstraintDimension.FillToConstraints` / `MatchParent` | Opposing anchors plus `ConstraintDimension.MatchConstraints()` |
| independent min/max/percent/constrained fields | `Fixed`, `ConstrainedWrapContent`, or one typed `MatchConstraints(...)` value |
| raw ratio strings | `ConstraintRatio(width, height, constrainedSide)` |
| circle combined with edge constraints | Separate constraint-set states |
| duplicate constraint/helper IDs | Declare once; use inline-over-set precedence only for intentional overlays |
| `ConstraintLayoutScope` as a `UiTreeBuilder` alias | Use the dedicated receiver supplied by `ConstraintLayout` |
| one generic helper target type | Use horizontal, vertical, baseline, or identity-only references as declared |
| `constraintSet { constrain(ref.id) }` | `constraintSet { constrain(ref) }` |
| AndroidX Grid string spans/skips | `ConstraintGridSpan` and `ConstraintGridSkip` |
| logical start/end used as fixed screen edges | Physical left/right anchors, Guidelines, Barriers, and chain sides |

This is an intentional source hard cut. There is no deprecated compatibility alias, partial-link
recovery, raw AndroidX helper grammar, or second constraint solver.

## Related documentation

- [Constraint graph and helper ownership ADR](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md)
- [Typed helper expansion ADR](../../architecture/decisions/0017-typed-constraint-helper-expansion.md)
- [Modifier architecture](../../architecture/modifier.md)
- [Performance tooling](../../tooling/performance.md#247-constraintlayout-phase-4-controlled-matrix)
- [First-release hardening record](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-native-engine-hardening.md)
- [Parity and performance expansion record](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)
- [Generated API reference](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/)
