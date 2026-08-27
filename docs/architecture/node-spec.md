---
schema_version: 2
document_id: architecture.node-spec
doc_type: architecture
owner:
  kind: capability
  id: renderer.reconciliation
version_lane: released
capability_ids:
  - renderer.reconciliation
  - renderer.tree-transactions
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids: []
invariants:
  - Every VNode carries exactly one non-null NodeSpec; no parallel dynamic Props map participates in declaration or rendering.
  - NodeSpec semantic values are immutable, structurally comparable, platform-neutral inputs to equality, patch planning, subtree skipping, diagnostics, and rollback.
  - Component semantics belong to NodeSpec, general ordered decoration belongs to Modifier, and theme policy resolves before either reaches Renderer.
  - DSL emission, descriptor binding, and patching consume the same typed NodeSpec contract and reject mismatched types deterministically.
evidence:
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/guard/PropsRegressionGuardTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/NodeSpecAccessTest.kt
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/node/NodeSpecTextContractTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/widget/layout/ContainerNodeSpecTest.kt
---

# NodeSpec-Only Specification

## 1. Scope

This document defines the ViewCompose node-semantics boundary: only `NodeSpec` is allowed, and the
former parallel `Props` path no longer exists.

Goals:

1. Keep render-pipeline semantics stable, derivable, and testable.
2. Prevent dynamic fields from returning and degrading patch/skip semantics.
3. Provide one integration template for every new node.

For historical context, see
[NODE_PROPS_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/NODE_PROPS_FULL_2026-03-06.md).

## 2. Current hard boundary

1. `VNode` contains only a non-null `spec: NodeSpec`; it has no `props` field.
2. `UiTreeBuilder.emit/emitResolved` requires `spec` and no longer accepts a `props` parameter.
3. The renderer pipeline may read only `NodeSpec + ResolvedModifiers`.
4. Additional metadata such as anchors must travel through modifier elements, for example
   `Modifier.overlayAnchor(...)`.
5. Do not add any `Props/TypedPropKeys/PropKeys/node.props` path.

## 3. Responsibility split

1. Component semantic fields belong in `NodeSpec`.
2. General visual and interaction decoration belongs in `Modifier`.
3. Theme defaults are resolved through `Theme -> Defaults` and injected into `NodeSpec/Modifier`.

## 4. Value admissibility boundary

`NodeSpec` values participate in VNode equality, patch planning, subtree skipping, diagnostics, and
failed-render rollback. Semantic payloads must therefore be immutable, structurally comparable,
and platform-neutral. Do not retain Android framework objects or mutable interface types merely
because a native View setter accepts them.

Text follows this rule explicitly:

1. `TextNodeProps` has one authoritative `TextDocument` for both plain and rich text.
2. `ButtonNodeProps` and `ToggleNodeProps` use nullable `String` labels.
3. Android `CharSequence`, `Spanned`, `Spannable`, and `Editable` values exist only in renderer
   interop code and are converted at the final native binding or input boundary.

This split prevents mutable spans and identity-based platform values from making an unchanged VNode
compare differently or a changed value compare equal for the wrong reason.

## 5. Resolved surface boundary

`NodeType.Surface` pairs with `SurfaceNodeProps`, not the general `BoxNodeProps`. A design-system
component resolves its brush, shape, border, effective dimensions, optional visual height, and
clipping policy before emission. General interaction feedback travels through the ordered
`UiInteractionIndication` modifier contract rather than Surface, Box, or Row NodeSpec fields. The
Android Renderer executes both snapshots without receiving design-system identity or semantic
token roles.

General caller modifiers remain ordered after the resolved surface. A caller background, border,
corner, or shape replaces the component-provided visual surface and uses the complete effective
bounds. Exact shadows and elevation may be supplied by the Basic component as ordinary ordered
modifier contracts because the renderer already executes them generically.

Component NodeSpecs retain interaction values only when the native backend owns multiple internal
targets that one outer modifier cannot address. Segmented controls and navigation bars therefore
carry complete selected and unselected `UiStateLayerColors`; a TabRow instead emits eager keyed
child boxes and gives each child its own indication modifier.

## 6. New-node checklist

Every new first-party node must include:

1. a node-specific `NodeSpec`;
2. immutable, structurally comparable, platform-neutral semantic fields;
3. DSL parameters mapped to that `NodeSpec`, with modifier metadata where necessary;
4. corresponding renderer binder and patch behavior;
5. unit coverage for stable structure, field changes, and interaction changes;
6. a Demo verification path and instrumentation where required.

## 7. Application and third-party extension path

Extensions must also remain spec-only:

1. define a custom `NodeSpec`;
2. define custom binder/patch behavior;
3. never pass semantics through a dynamic map.

## 8. Regression prevention

1. Unit tests cover strict `requireSpec<T>()` reads and failure diagnostics.
2. Static guard tests scan framework production source and reject a returning `Props` system.
3. Architecture and workflow reviews treat NodeSpec-only as a required checkpoint.

## 9. Related documents

1. [Architecture overview](overview.md)
2. [Development workflow](../project/workflow.md)
3. [Modifier architecture](modifier.md)
