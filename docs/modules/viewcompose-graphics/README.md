# Graphics

`viewcompose-graphics` exposes ViewCompose custom drawing to the UI tree. It provides a dedicated
`Canvas` node, draw-behind and content-aware modifiers, cache-aware command building, and convenient
aliases for the platform-neutral graphics contracts. Android execution is supplied by the renderer.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Node and modifier contracts are reviewed and tested; renderer fidelity and
  advanced drawing APIs may evolve between alphas.
- Platform: Android library and ViewCompose composition integration.
- It exposes graphics core, UI contracts, and widget core transitively.
- Use `viewcompose-graphics-core` alone for platform-neutral command/model code.

## Draw execution model

Draw callbacks record `DrawCommand` values during the Android View draw pass on the UI thread. They
are not composition callbacks and may run many times without recomposition. Keep uncached callbacks
allocation-light and never perform blocking I/O or launch per-frame work.

`DrawContext.size` is measured in physical pixels and `density` is pixels per dp. A `Canvas` node has
no intrinsic size derived from its commands; parent constraints or layout modifiers must size it.

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) { context ->
    drawCircle(
        center = Offset(context.size.width / 2f, context.size.height / 2f),
        radius = minOf(context.size.width, context.size.height) / 2f,
        paint = DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())),
    )
}
```

## Draw modifiers

`drawBehind` records commands before the node's normal content. `drawWithContent` gives explicit
control over the downstream content call: omit `drawContent()` to suppress it, or place commands on
both sides to create background and foreground layers. Modifier order remains significant.

The `key` on these functions identifies the modifier element during reconciliation. It does not
cache commands and should remain stable while the logical draw behavior remains the same. `draw`
is a concise alias for `drawBehind`.

## Cache-aware drawing

`drawWithCache` owns one renderer cache for the mounted modifier. Its outer modifier key identifies
the element; the inner `cache(key) { ... }` key controls command reuse. Include every input that can
change the result, especially physical size, density, theme tokens, resource identity, and layout
direction.

```kotlin
val modifier = Modifier.drawWithCache { context ->
    cache(key = context.size) {
        val outline = path {
            moveTo(0f, 0f)
            lineTo(context.size.width, 0f)
            lineTo(context.size.width, context.size.height)
            close()
        }
        listOf(DrawCommand.DrawPath(outline))
    }
}
```

The cache is single-entry and UI-thread confined. State read only inside a builder does not
automatically invalidate it; put observable values in the semantic cache key. Renderer disposal
releases the mounted cache. `drawCache` is an alias for `drawWithCache`.

## Android renderer behavior

The Android executor replays commands with `android.graphics.Canvas` and a new `Paint` request per
operation. Alpha is clamped to `0f..1f`. On Android 10 and newer, framework `BlendMode` is used;
older versions use the closest `PorterDuff` mapping. Blur filter chains are reduced to combined x/y
radii and applied through `BlurMaskFilter`, so they are not a general RenderEffect pipeline.

`ImageRef.stableId` currently resolves when it is an Android `Bitmap` or `Drawable`; unsupported IDs
draw nothing. Bitmap source and destination rectangles are converted to integer bounds. Drawable
bounds are temporarily replaced and restored. `DrawText` uses Android `Canvas.drawText` at a baseline
origin and does not add wrapping or rich-text layout.

These details describe the current renderer and are not promises for a custom backend. Use renderer
tests when exact API-level fidelity matters.

## Testing custom drawing

- Test command generation separately from Android pixel output.
- Verify measured size and density are included in cache keys.
- Exercise modifier ordering and omission/placement of `drawContent()`.
- Use renderer or screenshot tests for blend, gradient, blur, bitmap/drawable, and text fidelity.
- Repeatedly invalidate a node to profile callback allocation independently of recomposition.

The module suite verifies Canvas node emission and encoding order for draw-behind, content-aware,
and cache-aware modifier elements.

## Related documentation

- [Graphics Core module](../viewcompose-graphics-core/README.md)
- [Renderer module](../viewcompose-renderer/README.md)
- [Widget Core module](../viewcompose-widget-core/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-graphics` API tree](https://docs.viewcompose.com/api/viewcompose-graphics/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes UI-thread command recording, a no-intrinsic-size Canvas node,
ordered content drawing, explicit single-entry cache keys, Android Canvas replay, and current
API-level fallbacks. It is an immediate recording model rather than a retained vector scene graph.
