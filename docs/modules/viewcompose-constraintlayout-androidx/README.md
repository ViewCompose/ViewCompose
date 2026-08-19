# AndroidX ConstraintLayout Integration

`viewcompose-constraintlayout-androidx` adds a declarative ConstraintLayout node, child-constraint
modifiers, reusable constraint sets, and AndroidX virtual helpers to ViewCompose.

Its public API root is `com.viewcompose.constraintlayout`; the Maven suffix records the AndroidX
backend without retaining the retired `com.viewcompose.widget.constraintlayout` taxonomy.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-constraintlayout-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The current source contains the first-release API and renderer hard cut,
  but its complete Robolectric, physical-device, Demo, AndroidX `2.2.2`, and performance-safety
  evidence is still release-blocking under the active
  [first-release hardening plan](../../project/plans/constraintlayout-native-engine-hardening.md).
  Broader parity and optimization are owned by a separate
  [post-release expansion plan](../../project/plans/constraintlayout-parity-performance-expansion.md)
  that remains Changeset-free until the first release is published and tagged.
- Platform: Android 7.0 (API 24) and newer.
- Optional: `viewcompose-ui-foundation` does not depend on this artifact.
- UI Contract and UI Foundation are exposed transitively because their modifier, unit, and builder
  types appear in the public DSL; runtime remains an implementation dependency.
- Native engine: AndroidX ConstraintLayout `2.2.2` and its Guideline, Barrier, Flow, Group, Layer,
  and Placeholder helpers.

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

References are non-blank string identities local to one layout. Duplicate child IDs, helper IDs,
and child/helper collisions reject the complete candidate. A repeated source-anchor call replaces
its earlier link. `start` and `end` follow layout direction; top, bottom, and baseline are
physical/native anchors. A baseline link is mutually exclusive with top/bottom positioning, and a
circle is mutually exclusive with all edge/baseline links.

`ConstraintLayoutScope` is a dedicated `@UiDslMarker` receiver rather than a `UiTreeBuilder` type
alias. It still exposes every ordinary widget, but helper declarations belong directly to the
current layout and an outer ConstraintLayout receiver is hidden inside a nested layout scope. The
scope freezes its helper specification after content completes; no thread-local collector or
mutable post-emission helper payload is part of the public behavior.

Anchor targets are separated by capability. Logical start/end APIs accept only
`ConstraintHorizontalAnchorTarget`; top/bottom APIs accept only
`ConstraintVerticalAnchorTarget`; baseline-to-baseline accepts only
`ConstraintBaselineAnchorTarget`. Ordinary child references implement all three planes. A
start/end Guideline or Barrier implements only the horizontal plane, while a top/bottom Guideline
or Barrier implements only the vertical plane. Group and Layer return identity-only helper
references. Cross-axis links therefore fail during Kotlin compilation rather than graph preflight.

## Dimensions and positioning

Width and height use one mutually exclusive algebra:

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
`MatchConstraints(Spread|Wrap|Percent, min, max)`. Bounds and percentages validate eagerly;
`MatchParent`, independent min/max/percent/constrained fields, and raw ratio strings are absent.
Typed ratios require positive finite terms and at least one match-constraint axis. Biases and
guideline percentages use `0f..1f`. Circular angles use the finite `0f..<360f` Android clockwise
convention.

## Reusable constraint sets

`constraintSet { ... }` builds an immutable `ConstraintSetSpec` without emitting UI. Pass it to
`ConstraintLayout(constraintSet = set)`. Inline constraints and helpers are merged afterward and win
when the same constraint ID or same-kind helper ID exists in both sources. Duplicate constraints or
helpers inside one source fail immediately; a cross-kind helper collision rejects graph preflight.

Reusable entries use the same typed reference for declaration and links:

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

The removed `constrain(id: String)` builder overload cannot drift away from a separately created
reference. `Modifier.constrain(id, ...)` remains as the explicit XML-migration shortcut for an
inline child; `Modifier.constrainAs(ref, ...)` is the reference-based form.

## Virtual helpers

- Guidelines use finite non-negative dp offsets or inclusive `0f..1f` parent fractions.
- Barriers track logical/physical extremes with margins and gone-widget policy.
- Chains require at least two unique members, own their members' anchors on the chain axis, and
  validate finite positive weights plus bias.
- Flow maps orientation, wrapping, styles, biases, alignment, gaps, padding, and maximum wrap count.
- Group propagates visibility and elevation.
- Layer propagates visibility, elevation, rotation, scale, translation, and optional pivots.
- Placeholder hosts one referenced child and defines empty visibility.

Inline helper functions exist only on `ConstraintLayoutScope`; unrelated builders cannot call them.
The reusable builder variants use `ConstraintSetBuilder`. Barrier, Flow, Group, and Layer require
layout-local references. Flow and Placeholder are constraint-capable graph nodes, so a reusable set
may constrain their helper reference; Guideline, Barrier, Group, and Layer are not ordinary
constraint-item sources.

## Native reconciliation and failures

The native container coalesces rebuild requests and preflights the complete merged graph before
mutation. Child and helper strings map to stable Android View IDs. One registry creates, reuses,
retypes, and removes Guideline, Barrier, Flow, Group, Layer, and Placeholder Views; AndroidX no
longer creates unowned helpers as an `applyTo` side effect.

An accepted candidate is built from a clean native set. The renderer snapshots touched IDs,
LayoutParams, helper membership, accessibility, visibility, and transforms before apply. Missing
references, duplicate/colliding IDs, invalid anchor planes, competing chain/item ownership, helper
cycles, and invalid dimensions/ranges reject the whole candidate. Native failure restores the
previous helper registry and View state. Diagnostics are structured and bounded by graph revision,
identity, and reason; invalid links are never dropped individually.

This transaction follows
[ADR-0016](../../architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md).
A focused 2026-08-18 API 35 Robolectric run against cached ConstraintLayout `2.2.1` passed 16/16
renderer tests. Under the same harness, the trailing-Barrier control changed from an expected
`125 px`/actual `0 px` before the ID-index and direction fixes to exact `125 px` afterward, reducing
coordinate error from `125 px` to zero; the result is **improved**. The 1,000-retype case retained
exactly one managed helper and two total children on every iteration. The run also covers all-six-
kind retyping, Layer transform/removal/detach/reattach, Placeholder release, invalid-candidate
retention, injected mid-commit rollback, valid retry, and stable native identity when declarations
of every retained helper kind reorder. This is focused correctness evidence, not release
acceptance: it used a manual classpath and `2.2.1`, emitted Robolectric-only resource-name
diagnostics for generated IDs. A follow-up Gradle 8.13 run resolved ConstraintLayout `2.2.2` plus
core `1.1.2` and passed 75/75 UI Contract tests, 11/11 DSL tests, and 451/451 Renderer tests,
including the 12 graph and 16 focused ConstraintLayout cases; `verifyDocumentationStructure` also
passed. The formal JVM compatibility conclusion remains **improved**. It still does not cover the
exhaustive device/helper matrix, visuals, memory, or performance. Release closure therefore still
requires warning-free Demo/device evidence and the no-material-regression controls. Classified
update fast paths, Grid, CircularFlow, and broader parity remain post-release work.

The 2026-08-19 DSL safety follow-up passed 17/17 ConstraintLayout module tests: 12 behavior tests
and five Kotlin 2.0.21 compiler fixtures. The positive typed-axis/reference sample compiled; a
vertical helper used as a horizontal target, a horizontal helper used as a vertical target, an
outer ConstraintLayout helper call leaked through a nested Column, and a string ConstraintSet
entry all failed compilation as required. The prior generic target/type-alias surface admitted
those four invalid forms, so the compile-safety conclusion is **improved**. The same run proved
nested helper snapshots remain independent and a retained scope rejects late declarations.
`verifyDslApiContracts`, the UI Foundation scoped-container sample, Demo compilation, and Preview
compilation also passed. This is source-contract evidence only; it does not replace the remaining
device or performance gates.

The focused 2026-08-19 physical-device rerun on a Samsung SM-G991B / Android 13 accepted the
revised Guideline/Barrier fixture in light theme, LTR, and font scale 1.0. The Barrier marker
center moved from `596 px` for the short copy to `782 px` for the long copy, an absolute `186 px`
delta (17.2% of the 1080 px screen width), while the visible 55% Guideline stayed fixed and the
complete marker remained inside its container. The exact geometry instrumentation passed 1/1,
the warning-free Demo APK assembled successfully, and filtered logs contained no app-fatal,
ConstraintSet, renderer-layout, or helper-layer failure. The focused visual/geometry conclusion
is **improved**. This is one default-configuration fixture, so the all-helper dark-theme, RTL,
enlarged-font, memory, and performance gates remain release-blocking.

## Alpha source migration

| Removed Alpha source | Replacement |
| --- | --- |
| `ConstraintDimension.FillToConstraints` | `ConstraintDimension.MatchConstraints()` |
| `ConstraintDimension.MatchParent` | Opposing anchors plus `MatchConstraints()` |
| `widthMin` / `widthMax` | `width = MatchConstraints(min = ..., max = ...)` |
| `heightMin` / `heightMax` | `height = MatchConstraints(min = ..., max = ...)` |
| `widthPercent` / `heightPercent` | `MatchConstraints(mode = ConstraintMatchMode.Percent(...))` |
| `constrainedWidth` / `constrainedHeight` | `ConstraintDimension.ConstrainedWrapContent` |
| `dimensionRatio = "W,16:9"` | `ratio = ConstraintRatio(16f, 9f, ConstraintRatioSide.Width)` |
| circle plus edge declarations | Put circle and edge placement in separate constraint-set states |
| repeated constraint/helper IDs in one builder | Declare each ID once; use inline-over-set precedence only for intentional state overlay |
| `ConstraintLayoutScope` as a `UiTreeBuilder` alias | Use the dedicated receiver supplied by `ConstraintLayout { ... }`; do not retain or construct it |
| one generic anchor-target type for every helper | Link start/end only to horizontal targets, top/bottom only to vertical targets, and baselines only to baseline-capable children |
| `constraintSet { constrain(ref.id) { ... } }` | `constraintSet { constrain(ref) { ... } }` |

The changed public surface is Q3: transport invariants, defaults, failure timing, DSL scope,
merge precedence, native mapping, and replacement samples are contract fields. There is no
deprecated compatibility alias or raw AndroidX escape hatch.

## Performance guidance

- Reuse a constraint set when the graph is stable and only child content changes.
- Keep reference IDs and helper kinds stable to reuse their generated View IDs and instances.
- Prefer simpler containers when constraints do not add value; ConstraintLayout incurs a solver pass.
- Avoid rebuilding large helper graphs from rapidly changing state.

There is no accepted ConstraintLayout-specific direct-native benchmark yet. The first-release plan
must prove no material regression, but it does not establish performance leadership. Do not
describe the adapter as the fastest ViewCompose layout path until the post-release expansion plan
records reproducible 10/50/100-node direct-native, published-baseline, and candidate results in the
performance documentation.

## Related documentation

- [UI Foundation module](../viewcompose-ui-foundation/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-constraintlayout-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-constraintlayout-androidx/current/).

## Compatibility notes

Source snapshots before the first-release hard cut used warning-based partial recovery and split
helper ownership. Current source intentionally breaks that behavior and does not provide a second
constraint solver or compatibility engine. The first-release plan still owns acceptance and release
closure; the post-release expansion plan separately owns optimization, broader parity, and
performance-leadership evidence.
