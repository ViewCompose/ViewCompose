# Migrate Compose Layout, Modifier, and Environment Code

This page compares Jetpack Compose layout, modifier, and composition-local semantics with
ViewCompose. It is an engineering migration reference, not an API-name parity table. Similar syntax
does not imply equivalent measurement, lifecycle, invalidation, or Android integration behavior.

## Baseline, status vocabulary, and verification date

| Baseline | Version | Purpose |
| --- | --- | --- |
| ViewCompose target modules | runtime `0.1.0-alpha02`; UI Contract and Host `0.1.0-alpha03`; UI Foundation and Renderer `0.1.0-alpha01` | Target of this migration guide |
| Compose Runtime, UI, and Foundation | `1.11.4` stable | Upstream semantic reference |
| Repository Compose dependencies | `1.7.8` | Executable comparison baseline in this repository |
| Repository Kotlin toolchain | `2.0.21` | Compilation baseline for comparison code |

The upstream baseline is confirmed by the official AndroidX release notes for
[Compose Runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime),
[Compose UI](https://developer.android.com/jetpack/androidx/releases/compose-ui), and
[Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation).
The repository baseline is declared in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), lines 3 and 22.

This page uses exactly four capability states:

- **Supported**: the migration target protects the relevant observable behavior, although names or
  implementation details can differ.
- **Partially supported**: a practical replacement exists, but an important part of the Compose
  contract is absent or narrower.
- **Intentionally different**: ViewCompose provides a deliberate alternative contract; code must be
  redesigned rather than renamed.
- **Unsupported**: no public equivalent exists in the verified baseline.

Last verified: **2026-08-06**.

Re-verification owner: **ViewCompose UI Contract, UI Foundation, and Android Renderer maintainers**.

## Evidence model

The comparison has two evidence layers that must not be conflated:

1. **Official semantic review** uses Android Developers API documentation, behavior guides, and
   AndroidX release notes for Compose 1.11.4. Those sources define the upstream behavior described
   here.
2. **Local executable evidence** uses the independently versioned ViewCompose target set above and repository
   tests. The repository's Compose 1.7.8 dependency allows compiled comparisons, but it is not used
   to override a documented Compose 1.11.4 semantic change.

No performance equivalence is claimed. This review did not establish comparable benchmark
conditions for Compose layout nodes and Android Views.

## Compiled side-by-side starting point

This pair keeps one horizontal layout, one ordered Modifier chain, and one scoped environment
value visible on both sides. The snippets are extracted from the compiled
`:samples:compose-migration` module and are checked for exact source agreement by `qaQuick`.

Compose source:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ComposeLayoutSample.kt" region="compose-layout" */}
```kotlin
private val LocalContentPadding = compositionLocalOf { 8.dp }

@Composable
fun ComposeProfileRow(name: String) {
    CompositionLocalProvider(LocalContentPadding provides 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalContentPadding.current),
        ) {
            BasicText(name)
        }
    }
}
```
{/* paired-sample-end */}

ViewCompose target:

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/layout/ViewComposeLayoutSample.kt" region="viewcompose-layout" */}
```kotlin
private val LocalContentPadding = uiLocalOf { 8.dp }

fun UiTreeBuilder.ViewComposeProfileRow(name: String) {
    ProvideLocal(LocalContentPadding, 16.dp) {
        Row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiLocals.current(LocalContentPadding)),
        ) {
            Text(name)
        }
    }
}
```
{/* paired-sample-end */}

The similar shape does not make the engines equivalent. Compose measures layout nodes and tracks
`CompositionLocal` reads; ViewCompose renders Android Views, folds Modifier elements by renderer
rules, and treats `UiLocal` as scoped lookup rather than an invalidation subscription.

## Capability matrix

| Concept | Compose 1.11.4 behavior | ViewCompose verified-set behavior | Status | Required migration action |
| --- | --- | --- | --- | --- |
| Built-in layout containers | `Row`, `Column`, `Box`, and Foundation layouts measure Compose layout nodes under `Constraints`. | `Row`, `Column`, `Box`, flow layouts, scrolling containers, and ConstraintLayout emit VNodes that become Android `ViewGroup` implementations. | Partially supported | Recheck defaults, overflow, clipping, weight, and intrinsic-size assumptions on the native View implementation. |
| Custom measurement | `Layout`, `MeasurePolicy`, and layout modifier nodes let application code measure and place Compose children. Ordinary measurement permits each child to be measured once. | No public general-purpose measure policy, measurable/placeable contract, or layout modifier was found. Custom multi-child measurement requires a renderer extension or an Android `ViewGroup` hosted through interop. | Unsupported | Redesign custom Compose layouts around a built-in container or a lifecycle-owned Android View implementation. |
| Size and fill | Layout modifiers transform or constrain a chain; `size` remains subject to incoming constraints, and fill APIs can accept fractions where defined. | Exact dp dimensions become pixel LayoutParams; fill helpers become `MATCH_PARENT`. Axis-specific width or height has fixed renderer precedence over `size`. | Partially supported | Replace the final measured contract, not just the function name. Audit constrained, fractional-fill, required-size, and intrinsic-size behavior. |
| Padding and margin | Each layout modifier participates at its position in the modifier chain. Compose normally represents outside space with layout structure or padding rather than a margin property. | Padding is native View content padding. Margin is explicit native parent LayoutParams data. Repeated padding or margin elements resolve to the last element of that type. | Intentionally different | Flatten repeated padding and decide explicitly whether former outer padding belongs in parent structure, View padding, or ViewCompose margin. |
| Scoped parent data | Scope-safe modifiers such as `RowScope.weight`, `ColumnScope.weight`, alignment, and `BoxScope.matchParentSize` provide data to a compatible direct parent. | `RowScope` and `ColumnScope` expose weight and cross-axis alignment; `BoxScope` exposes alignment. Invalid parent-data use is diagnosed with a warning. There is no verified `matchParentSize` equivalent. | Partially supported | Keep scoped modifiers on direct children of the matching container. Redesign `matchParentSize`; do not replace it blindly with `fillMaxSize`. |
| Constraint parent data | Compose ConstraintLayout consumes layout IDs and constraint parent data inside its own measurement model. | The optional ConstraintLayout module maps layout IDs and constraint specs to AndroidX ConstraintLayout LayoutParams and ConstraintSet operations. | Partially supported | Revalidate dimensions, baselines, RTL anchors, and dependency cycles against the AndroidX View implementation. |
| Modifier order | Modifier elements form an ordered wrapping chain; order can change measurement, drawing, input, focus, and semantics. | The source chain is ordered, but the renderer folds it into phase-specific values. Many repeated values are last-wins, some conflicts use fixed precedence, z-index values add, and draw or shadow groups retain order. | Intentionally different | Classify every non-trivial chain by its ViewCompose resolution rule before migrating it. |
| Modifier equality and update | A `ModifierNodeElement` uses equality to decide whether an existing `Modifier.Node` is updated. | Modifier chains compare their ordered element sequences structurally. Renderer diffing can skip a subtree for equal chains. `NativeViewElement` equality uses only its stable key and ignores callback identity. | Supported | Give native configuration a key that changes when its semantic configuration changes, and do not rely on a fresh lambda instance to force an update. |
| Custom `Modifier.Node` lifecycle | Public node APIs provide create/update, attach/detach, invalidation, local reads, and specialized layout, draw, input, or semantics node interfaces. | `ModifierElement` is a renderer marker, not an application lifecycle node. Built-in elements are interpreted by known renderer branches. No public equivalent of custom node attach/detach or capability interfaces exists. | Unsupported | Use a supported modifier, replay-safe `nativeView`, transaction-aware `AndroidView`, or a reviewed renderer feature. Do not publish an unrecognized element from application code. |
| Density and font scale | `LocalDensity` provides dp/sp conversion to layout and drawing code. | `UiDensity` is captured into each VNode environment. Android hosts read density and font scale from resources; renderers convert units at the native boundary. | Supported | Keep logical dp/sp in declarations and avoid retaining converted pixels across a new environment snapshot. |
| Layout direction and locales | Composition locals provide layout direction and locale data; logical start/end APIs resolve from that environment. | Direction and locale lists are captured in the VNode. The renderer applies native View direction and TextView locales, but padding, margin, offset, and inset-side parameters are physical left/right. | Partially supported | Audit every start/end assumption and run real RTL layout checks; do not translate logical Compose edges directly to physical ViewCompose edges. |
| Composition-local propagation | `compositionLocalOf` tracks read sites; changing a provided value invalidates the readers. `staticCompositionLocalOf` invalidates the provider content as a broader unit. | `UiLocal` uses a thread-scoped map while a tree is built. Emits compare a complete local snapshot as an input, but reading `UiLocals.current` does not itself register an invalidation dependency. | Intentionally different | Back changing local values with ViewCompose state or another host invalidation source. Treat local reads as scoped value lookup, not observation. |
| Delayed content locals | Lazy and other subcomposed content observes locals through the Compose composition that owns it. | Lazy, pager, tab, overlay, and navigation sessions explicitly capture opaque local snapshots and restore them when delayed content renders. Snapshot changes participate in content tokens or session updates. | Supported | Preserve stable item/page keys and content tokens, and let the container refresh its captured snapshot rather than retaining a builder. |
| System bars and IME insets | Insets padding is layout-aware, participates in automatic nested consumption, avoids reapplying an already consumed portion, and follows IME updates and animations. | System-bar and IME modifiers install an AndroidX listener on the target View and add selected physical sides to base padding. Nested ViewCompose modifiers do not exchange consumed-inset state; system-bar and IME values on one View are summed. | Partially supported | Assign inset ownership to a deliberate level, avoid duplicate ancestor/descendant application, and avoid combining `adjustResize` with redundant IME padding. |
| Android output and View interop | Compose normally renders Compose nodes; `AndroidView` embeds a platform View with factory/update and optional reuse/release callbacks. | Every first-party node ultimately becomes an Android View. ViewCompose `AndroidView` adds transactional rollback and post-transaction commit semantics; `nativeView` applies replay-safe configuration to the mounted View. | Intentionally different | Separate repeatable configuration from one-shot work and cleanup. Put them in update/native configuration, `onCommit`, and `onRelease` respectively. |

## Two layout engines: Compose constraints and Android Views

Compose layout is a node protocol. A parent passes constraints, children report measured sizes, and
the parent places the resulting placeables. The official
[custom-layout documentation](https://developer.android.com/develop/ui/compose/layouts/custom)
also defines the ordinary single-measure rule and the public `Layout` escape hatch.

ViewCompose builds immutable VNodes first. The Android renderer then creates native widgets and
containers. For example, Text becomes `TextView`, Row and Column become an oriented
`DeclarativeLinearLayout`, and Box becomes `DeclarativeBoxLayout`. The definitive mapping is in
[`ViewNodeFactory.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/ViewNodeFactory.kt),
lines 55–126. Row and Column retain Android `LinearLayout` measurement and implement declarative
arrangement during native placement; see
[`DeclarativeLinearLayout.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/container/layout/DeclarativeLinearLayout.kt),
lines 21–92.

Consequently, a Compose custom `Layout` cannot be translated as a normal ViewCompose component.
Choose one of these boundaries:

- express the result with a built-in ViewCompose container;
- use the optional ConstraintLayout module when constraint parent data is sufficient;
- host a custom Android `ViewGroup` through `AndroidView` when application-specific measurement is
  essential; or
- propose a documented renderer feature when the behavior is a reusable framework contract.

The last two choices are not equivalent to receiving Compose `Measurable` values. Android measure
specs, LayoutParams, request-layout propagation, and platform view state remain authoritative.

## Size, padding, margin, and fill semantics

Compose chains layout modifiers as ordered participants in constraint propagation. The official
[constraints and modifier-order guide](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)
is the upstream reference for that model.

ViewCompose resolves dimensions through native LayoutParams. The parent-aware precedence is:

1. a ConstraintLayout dimension, when present;
2. an axis-specific `width` or `height` modifier;
3. the corresponding axis from `size`;
4. the renderer's node-and-parent default.

This precedence is implemented in
[`ViewLayoutParamsFactory.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt),
lines 73–99. Exact dp dimensions are converted with the VNode's captured density. Fill helpers map
to Android `MATCH_PARENT`; they do not preserve every fractional or intrinsic Compose option.

Padding and margin have distinct native destinations:

- padding becomes content padding on the mounted View;
- margin becomes physical left, top, right, and bottom values on the parent LayoutParams;
- offset becomes View translation and does not change sibling measurement or placement; and
- minimum width and height become View minimum dimensions.

Repeated padding does not create nested layout layers. The resolver retains the final padding
element. The same rule applies to repeated margin elements. Migration should therefore normalize a
Compose chain before translating it and preserve the intended outer and inner boundaries in the
container structure.

The public dimension and edge contracts are in
[`ModifierLayoutExtensions.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierLayoutExtensions.kt),
lines 6–187 and 189–290. Their native LayoutParams application is in
[`ViewLayoutParamsFactory.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt),
lines 91–149 and 168–192.

## Row, Column, Box, and scoped parent data

Both frameworks use receiver scopes to keep common parent data near a compatible parent. The
Compose behavior and `matchParentSize` distinction are documented in
[Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers#scope-safety).

ViewCompose exposes these supported scoped operations:

| Scope | Supported parent data | Native destination |
| --- | --- | --- |
| `RowScope` | positive `weight`; vertical `align` | horizontal LinearLayout weight and child gravity |
| `ColumnScope` | positive `weight`; horizontal `align` | vertical LinearLayout weight and child gravity |
| `BoxScope` | box `align` | FrameLayout child gravity |

The declarations and positive-weight check are in
[`LayoutScopes.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/LayoutScopes.kt),
lines 12–96. Parent-data validation is in
[`ModifierParentDataValidator.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/layout/ModifierParentDataValidator.kt),
lines 28–97.

Scope availability is the supported application API, but it is not a complete runtime type-safety
barrier. Contract element classes remain visible to renderer integrations, and an incompatible
parent produces a deduplicated warning rather than a render failure. Treat every scoped modifier as
direct-child data.

Compose `BoxScope.matchParentSize` is deliberately called out as unsupported. Compose uses it to
match the Box's final size without making that child determine the Box size. ViewCompose
`fillMaxSize` maps to `MATCH_PARENT` and must not be documented as an equivalent replacement.

## ConstraintLayout parent data

The optional ConstraintLayout module supplies layout IDs and constraint item specifications as
parent data. The Android renderer consumes those values through AndroidX ConstraintLayout. Fixed
dimensions are converted from the child's captured environment; fill-to-constraints becomes the
Android ConstraintLayout zero-dimension convention.

The contract elements are defined in
[`ModifierElementsLayout.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierElementsLayout.kt),
lines 117–150. Parent-aware conversion is implemented in
[`ViewLayoutParamsFactory.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewLayoutParamsFactory.kt),
lines 91–98 and 247–255.

This is a practical migration path, not proof of Compose ConstraintLayout parity. Recheck
ConstraintSet merging, baseline connections, logical start/end anchors, circular dependencies, and
dimension defaults against the ViewCompose module contract.

## Modifier ordering, folding, and equality

Both Modifier types are immutable ordered chains. ViewCompose appends elements without mutating the
receiver and compares the resulting sequence structurally; see
[`Modifier.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/Modifier.kt),
lines 3–56.

The important difference is execution. Compose layout and behavior nodes retain their positions in
a wrapping node chain. ViewCompose folds elements into a `ResolvedModifiers` snapshot consumed by
separate renderer phases. The verified folding rules include:

| Modifier relationship | ViewCompose rule |
| --- | --- |
| Repeated scalar or single-slot elements | The later element of the same type usually replaces the earlier value. |
| `shape` and legacy `cornerRadius` | They are mutually exclusive; the later one in the chain clears the earlier one. |
| Repeated `zIndex` | Values are added. |
| Draw and advanced-shadow groups | Groups retain declaration order. |
| Axis `width`/`height` and `size` | Axis-specific values win through fixed LayoutParams precedence, regardless of cross-type chain order. |
| `graphicsLayer` and simple alpha, offset, or clip | The graphics-layer value has fixed renderer precedence when supplied. |
| System-bar and IME inset padding | Both values are retained and their selected physical sides are added. |

The fold is implemented in
[`ResolvedModifiers.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/modifier/ResolvedModifiers.kt),
lines 72–172. Do not infer a rule for one modifier family from another family.

Equality also drives reuse. `NodeBindingDiffer` can skip a subtree when the node, environment,
specification, children, and modifier inputs remain equivalent. An environment or modifier change
causes a rebind; see
[`NodeBindingDiffer.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/NodeBindingDiffer.kt),
lines 22–75.

`NativeViewElement` is a special case. Its equality and hash code use only `stableKey`, deliberately
ignoring callback identity. The contract is in
[`ModifierElementsInteraction.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/ModifierElementsInteraction.kt),
lines 220–249. A new lambda with the same key is not an update signal. Change the key when the
semantic native operation changes, or make another observable node input invalidate the binding.

## Why Modifier.Node does not migrate directly

Compose recommends `Modifier.Node` for custom modifier behavior. Its public model includes an
immutable element, a retained node, create/update, attach/detach, automatic or explicit
invalidation, CompositionLocal access, and specialized node interfaces. The upstream references
are [Create custom modifiers](https://developer.android.com/develop/ui/compose/custom-modifiers)
and the [`Modifier.Node` API](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.Node).

ViewCompose has no equivalent public lifecycle-node protocol. `ModifierElement` is a marker for
contracts understood by a renderer; see
[`Modifier.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/modifier/Modifier.kt),
lines 59–65. Application-defined implementations are not discovered as custom behavior.

Use these alternatives according to ownership:

- a supported ViewCompose modifier for framework-defined behavior;
- `nativeView` for repeatable configuration of the already mounted View;
- `AndroidView` when application code owns a native View and its release lifecycle; or
- a documented UI-contract and renderer change for a new reusable modifier capability.

An unrecognized `ModifierElement` is not a safe extension point. `nativeView` is also not a generic
node lifecycle: it has no attach/detach callback and its configuration can be replayed during
rollback.

## Density, locales, and layout direction

Compose exposes density and logical direction through platform CompositionLocals. The official
[Compose platform-local reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary)
defines `LocalDensity`, `LocalLayoutDirection`, and locale-related locals.

ViewCompose captures an immutable `UiEnvironmentValues` on every emitted VNode. It contains:

- `UiDensity`, including density and font scale;
- an ordered `UiLocaleList`;
- `UiLayoutDirection`; and
- a host-owned `resourceRevision` used to rebind equal Android resource IDs after configuration or
  imperative resource changes.

The snapshot contract requires a new tree after a platform configuration change. Standard Android
hosts schedule that tree automatically through their resource environment; custom hosts must
publish a new environment explicitly. See
[`UiEnvironmentValues.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/environment/UiEnvironmentValues.kt),
lines 92–112. The Android bridge reads resources and configuration in
[`AndroidEnvironmentBridge.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/environment/AndroidEnvironmentBridge.kt),
lines 15–29. Unit conversion is defined by
[`UiUnits.kt`](../../viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/unit/UiUnits.kt),
lines 157–223.

At bind time, the renderer stores the environment on the View, applies native layout direction, and
sets TextView locales. That boundary is in
[`ViewModifierApplier.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/ViewModifierApplier.kt),
lines 41–55. A changed environment forces a full node rebind rather than a visual-only patch.

Direction support is incomplete at the modifier API boundary. Row, Column, Box, and Constraint
alignment types can express logical start/end behavior, but these APIs use physical edges:

- padding and margin: left and right;
- offset: positive x moves right; and
- system-bar or IME selection: left and right.

Every migration involving asymmetric horizontal space needs an RTL decision. A value that was
`start` in Compose must not silently become `left`.

## UiLocal versus CompositionLocal

Compose distinguishes tracked `compositionLocalOf` reads from broad
`staticCompositionLocalOf` invalidation. The upstream behavior is documented in
[Locally scoped data with CompositionLocal](https://developer.android.com/develop/ui/compose/compositionlocal).

ViewCompose `UiLocal` is a typed handle into a thread-scoped immutable map used while a VNode tree is
built. `ProvideLocal` installs a value for a nested block and restores the prior map afterward.
`ProvideLocals` performs the same operation for multiple bindings. Binding presence is distinct
from nullability: an explicitly provided `null` for a nullable Local overrides a non-null default
and survives capture, restore, and delayed child-session propagation. The implementation is in
[`UiLocals.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/context/UiLocals.kt),
lines 3–103, and
[`LocalValue.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/context/LocalValue.kt),
lines 35–120.

The crucial migration rule is that `UiLocals.current(local)` is lookup, not observation. It does not
register that call site as a dependent reader. Instead, `UiTreeBuilder.emit` captures the complete
current local snapshot as one of its composition inputs. When another invalidation already causes
composition and that snapshot differs, the node group is rebuilt. See
[`UiTreeBuilder.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/UiTreeBuilder.kt),
lines 66–124 and 192–214.

Therefore:

- store changing source data in ViewCompose state, not only in a plain provided object;
- do not expect changing a mutable field inside an equal local value to schedule rendering;
- prefer immutable local values with meaningful equality;
- remember that a changed local snapshot can invalidate more work than a tracked Compose local
  reader; and
- custom hosts must serialize tree building on the owning renderer thread.

## Delayed content and local snapshots

Lazy collections, pagers, tabs, overlays, and navigation can render content after its declaration
scope returns. ViewCompose preserves locals explicitly for those boundaries.

For lazy lists, `LazyItemCollector` captures a `LocalSnapshot`, includes it in the effective content
token, creates a child session with that snapshot, and refreshes both the snapshot and content
closure on update. See
[`LazyCollectionScope.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/collection/LazyCollectionScope.kt),
lines 147–193, and
[`WidgetLazyListItemSession.kt`](../../viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/WidgetLazyListItemSession.kt),
lines 8–72.

This preserves nested local values across holder reuse, but it does not remove the caller's identity
responsibilities. Keys must remain stable and unique. A content token must change when captured
business values outside the local snapshot change. Do not retain or invoke a `UiTreeBuilder` after
its content block returns.

## System bar and IME insets

Compose inset padding applies current inset values during layout and communicates consumed portions
to nested modifiers. The official
[insets UI guide](https://developer.android.com/develop/ui/compose/system/insets-ui) explains nested
consumption, size modifiers, and IME animation behavior.

ViewCompose offers two focused modifiers:

- `systemBarsInsetsPadding`, which selects physical system-bar sides; and
- `imeInsetsPadding`, which defaults to the physical bottom side.

The renderer installs an AndroidX `WindowInsetsCompat` listener, records base padding, and adds the
selected inset pixels. Removing both modifiers restores base padding and removes the listener. The
implementation is in
[`ModifierInsetsApplier.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/binder/core/modifier/ModifierInsetsApplier.kt),
lines 11–128.

Unlike Compose, the listener returns the incoming insets unchanged and does not communicate how
much an ancestor ViewCompose modifier applied. Nested ViewCompose inset-padding modifiers can
therefore add the same inset again. System-bar and IME padding selected on the same View are also
summed, rather than reduced by a shared consumption model.

Migration rules:

1. Choose one owner for each inset edge whenever possible.
2. Inspect native ancestors and embedded Views for their own inset handling.
3. Do not combine Activity `adjustResize` behavior with redundant `imeInsetsPadding` unless the
   resulting displacement is intentionally verified.
4. Test gesture navigation, three-button navigation, landscape, RTL, display cutouts, and an IME
   transition on a real hosted screen.
5. Do not claim Compose nested-consumption or same-frame layout parity.

The current unit test protects modifier defaults and resolution but not real WindowInsets dispatch,
nested consumption, or animation. That limitation is recorded under executable evidence below.

## Android View output and interop

Compose normally renders its own UI nodes and uses `AndroidView` as an interop boundary. The
official [Views in Compose guide](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)
defines factory, update, reuse, reset, and release behavior.

ViewCompose is different at the root: every first-party VNode becomes an Android View. Its
`AndroidView` API is still a distinct ownership boundary for an application-created View and has a
transaction-aware lifecycle:

| Callback | ViewCompose contract |
| --- | --- |
| `factory` | Runs only when reconciliation needs a new native node. |
| `update` | Repeatable configuration during insertion, patching, or rollback. |
| `onReset` | Optional repeatable reset before a retained View is rebound. |
| `onCommit` | One-shot work published only after the complete View-tree transaction commits. |
| `onRelease` | One-shot cleanup whenever a created View is permanently abandoned, including committed removal, session disposal, or rollback of an uncommitted candidate. |

The public contract is in
[`AndroidInteropDsl.kt`](../../viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidInteropDsl.kt),
lines 11–82. Mounting and commit scheduling are in
[`ViewTreePatchPipeline.kt`](../../viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreePatchPipeline.kt),
lines 527–579.

`update`, `onReset`, and `nativeView` must not start non-repeatable external work. A failed frame can
restore the previously committed native tree and replay configuration. Use `onCommit` for operations
that must happen only after success and `onRelease` for owned-resource cleanup. Renderer tests
release newly created rollback candidates, although the current public `AndroidView` wording names
only committed removal and session disposal. Treat `onRelease` as cleanup for any permanently
abandoned created View until the public contract and implementation are aligned.

## Migration checklist

1. Record the source Compose version and the exact ViewCompose module versions being targeted.
2. Classify each layout as built-in, constraint-based, or custom-measured.
3. Replace layout behavior before translating visual modifier names.
4. Normalize repeated size, padding, margin, graphics-layer, and draw elements according to the
   ViewCompose folding rules.
5. Keep parent-data modifiers on direct children of the matching scope and redesign
   `matchParentSize` uses.
6. Identify every logical start/end edge before mapping physical modifier parameters.
7. Move changing provided values behind ViewCompose state; do not rely on `UiLocal` read tracking.
8. Assign system-bar and IME inset ownership explicitly across View and ViewCompose boundaries.
9. Separate Android View replay-safe configuration, post-commit work, and release cleanup.
10. Add behavior tests for measurement, RTL, local updates, delayed sessions, inset dispatch, and
    interop rollback before declaring the migration complete.

## Source and executable evidence

The following local evidence protects the claims in this page:

- Modifier immutability, structural equality, declaration order, draw order, and parent-data
  construction:
  [`ModifierContractTest.kt`](../../viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/modifier/ModifierContractTest.kt),
  lines 20–49, 85–117, and 170–212.
- Modifier folding, additive z-index, ordered shadows, and ConstraintLayout parent data:
  [`ResolvedModifiersTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/ResolvedModifiersTest.kt),
  lines 38–47, 82–129, and 165–205.
- Compatible and incompatible scoped parent data:
  [`ModifierParentDataValidatorTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/layout/ModifierParentDataValidatorTest.kt),
  lines 31–159.
- Structural modifier equality and environment-driven renderer rebind:
  [`NodeBindingDifferTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/NodeBindingDifferTest.kt),
  lines 115–141.
- Density, locales, direction, nested environment values, and capture into a VNode:
  [`EnvironmentTest.kt`](../../viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/EnvironmentTest.kt),
  lines 15–68.
- Density-sensitive ConstraintLayout resolution:
  [`DeclarativeConstraintLayoutEnvironmentTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/DeclarativeConstraintLayoutEnvironmentTest.kt),
  lines 21–79.
- Nested `UiLocal` provision, restoration, and explicit snapshot restoration:
  [`BusinessLocalApiTest.kt`](../../viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/BusinessLocalApiTest.kt),
  lines 13–103.
- Local-snapshot stability and environment-driven subtree replacement:
  [`SubtreeRecompositionTest.kt`](../../viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SubtreeRecompositionTest.kt),
  lines 59–123.
- Delayed lazy, pager, and tab content tokens changing with captured locals:
  [`LazyContentLocalPropagationTest.kt`](../../viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/context/LazyContentLocalPropagationTest.kt),
  lines 16–90.
- Insets modifier defaults and coexistence:
  [`InsetsPaddingModifierTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/InsetsPaddingModifierTest.kt),
  lines 14–48. This does **not** cover real dispatch, nesting, consumption, or animation.
- Native modifier stable-key equality:
  [`NativeViewElementTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/NativeViewElementTest.kt),
  lines 14–55.
- AndroidView rollback, commit publication, and release failure isolation:
  [`ViewTreeRenderTransactionTest.kt`](../../viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt),
  lines 330–341 and 393–470.

Existing compiled API samples cover Modifier chain construction and AndroidView interop, but no
compiled migration sample currently demonstrates a Compose custom layout replacement or real
nested WindowInsets behavior. This page intentionally avoids embedding a second, non-compiled
source of truth.

## Known gaps and re-verification triggers

The following gaps remain part of the migration contract:

- no public custom measurement or `Modifier.Node` equivalent;
- no verified `BoxScope.matchParentSize` equivalent;
- no tracked-versus-static `UiLocal` variants;
- no logical start/end variants for general padding, margin, offset, or inset selection;
- no nested inset-consumption protocol;
- no end-to-end WindowInsets animation or mixed View/ViewCompose consumption test; and
- public `AndroidView` release wording does not yet cover the rollback-candidate behavior protected
  by renderer tests.

The owner must re-verify this page when any of these events occurs:

1. Compose Runtime, UI, or Foundation advances the selected semantic baseline.
2. The repository Compose or Kotlin executable baseline changes.
3. A public layout, parent-data, modifier, environment, local, inset, or AndroidView contract
   changes.
4. The renderer changes modifier folding, LayoutParams precedence, environment rebinding, or native
   transaction behavior.
5. A new compiled migration sample or instrumentation test closes one of the recorded gaps.

Re-verification must review official upstream documentation first, then the current ViewCompose
source and tests. Passing a repository build against an older Compose artifact is not sufficient
evidence that a newer upstream semantic contract is unchanged.
