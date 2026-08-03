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

## 4. New-node checklist

Every new first-party node must include:

1. a node-specific `NodeSpec`;
2. DSL parameters mapped to that `NodeSpec`, with modifier metadata where necessary;
3. corresponding renderer binder and patch behavior;
4. unit coverage for stable structure, field changes, and interaction changes;
5. a Demo verification path and instrumentation where required.

## 5. Application and third-party extension path

Extensions must also remain spec-only:

1. define a custom `NodeSpec`;
2. define custom binder/patch behavior;
3. never pass semantics through a dynamic map.

## 6. Regression prevention

1. Unit tests cover strict `requireSpec<T>()` reads and failure diagnostics.
2. Static guard tests scan framework production source and reject a returning `Props` system.
3. Architecture and workflow reviews treat NodeSpec-only as a required checkpoint.

## 7. Related documents

1. [Architecture overview](overview.md)
2. [Development workflow](../project/workflow.md)
3. [Modifier architecture](modifier.md)
