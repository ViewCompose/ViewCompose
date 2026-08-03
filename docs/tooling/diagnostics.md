# ViewCompose Diagnostics

## 1. Data entry point

The host receives a structured `RenderTreeResult` through `onRenderResult`. Diagnostics are
collected only when `debug = true`, `onRenderStats` is registered, or `onRenderResult` is
registered. Normal release paths do not build tree snapshots or per-node patch details.

`RenderTreeResult` currently contains:

1. `stats / structure / warnings`: aggregate binding work, tree size, and warnings;
2. `tree`: the node tree consumed by the renderer, including node type, key, and hierarchy;
3. `patches`: ordered `Insert / Remove / Rebind / Patch / SkipSelf / SkipSubtree` records for the
   frame, including parent key, position, moves, and patch type;
4. `composition`: invalidated, recomposed, and skipped scope counts, plus each scope path,
   signature, recomposition reason, and Local snapshot.

## 2. Recomposition reasons

The runtime distinguishes:

1. `InitialComposition`
2. `StateInvalidation`
3. `AncestorInvalidation`
4. `InputsChanged`
5. `ExplicitRequest`
6. `StructureChanged`

Scope diagnostics are capped at 500 records and signatures are truncated so diagnostic cost does
not grow without bound with page size.

## 3. CompositionLocal diagnostics

`uiLocalOf(debugName = ..., debugValueFormatter = ...)` supplies a stable name and safe summary.
Built-in core Locals such as Theme, Environment, LifecycleOwner, SavedState, and ContentColor have
explicit names.

The default summary displays only Strings, numbers, Booleans, Chars, and enums directly. Other
objects display only their type and do not invoke an arbitrary application `toString()`. Crop a
sensitive application value deliberately through a custom formatter, or omit the formatter.

## 4. Demo inspector

`Diagnostics -> Renderer` currently provides:

1. a render-tree list;
2. a patch timeline;
3. recomposition reasons and scope counts;
4. a CompositionLocal browser;
5. existing aggregate render/layout metrics.

It does not yet provide real View-boundary highlighting, cross-RenderSession correlation, or
per-node timing.
