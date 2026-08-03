# ViewCompose Advanced Shadows

## 1. Capability boundary

Advanced shadows are drawn around native Android View content without depending on the OEM
implementation of `View.elevation`. Use them when a design requires exact color, blur, spread,
two-dimensional offset, a non-rectangular shape, or ordered multi-layer composition.

Keep these three concepts independent:

1. `elevation`: Material/platform elevation semantics mapped to `View.elevation`;
2. `zIndex`: sibling drawing order;
3. `dropShadow(s)/innerShadow(s)`: pixel-specified visual decoration that does not change layout or
   input behavior.

## 2. Public API

```kotlin
val shape = UiShape.rounded(20.dp)

Surface(
    modifier = Modifier
        .shape(shape)
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
            shape = shape,
        )
        .innerShadow(
            shadow = UiShadow(
                color = 0x44000000,
                blurRadius = 8.dp,
                offsetY = 3.dp,
            ),
            shape = shape,
        ),
) {
    Content()
}
```

`dropShadow` and `innerShadow` are single-layer conveniences; their plural forms accept ordered
lists. A later inner-shadow layer is drawn over an earlier layer. An empty list is a no-op.

## 3. Android drawing model

`viewcompose-shadow-android` is an optional backend. It does not replace TextView, EditText, or
RecyclerView, and it does not allocate one application View per shadow layer. The renderer submits
logical specifications to the Decoration SPI, the shadow backend resolves them to pixels, and the
framework parent draws in this order:

```text
outer shadow decoration
native child background/content/subtree/foreground
inner shadow decoration
```

An outer shadow follows the final child position, matrix, and alpha. Translation, scale, rotation,
or alpha changes reuse the existing raster. An inner shadow is a visual foreground but does not
participate in hit testing, so it cannot intercept clicks, focus, IME, or gestures.

Shadows do not participate in measure/layout. The caller reserves visual space for outer shadows.
Ordinary containers allow child decoration to overflow, while Lazy viewports still clip content
outside their viewport.

The renderer and Android host do not depend on the shadow module. Without
`viewcompose-shadow-android`, shadow modifiers degrade consistently to no-ops and all other render
capabilities continue to work. Adding the module enables ServiceLoader discovery, or an application
can install it explicitly at startup:

```kotlin
ShadowDecorationLayer.install()
```

The required root for `setUiContent` and static Preview remains a plain `FrameLayout`. A general
decoration host is added only when the top-level node itself uses a shadow or a non-zero `zIndex`.
Nested shadows are drawn by the nearest framework container. A parent with no active decorated
child performs one Boolean fast check before native `drawChild`; it does not query a shadow tag for
every child. When decorated children exist, each child performs one parent identity-index lookup
that is reused for the before/after drawing planes. Without a non-zero `zIndex`, custom child
drawing order and its sorting index remain disabled.

## 4. Shape and layer semantics

1. An explicit `shape` defines the shadow outline.
2. Without an explicit shape, resolution uses the node `shape`, then `cornerRadius`, and finally a
   rectangle.
3. Rounded and cut shapes support independent corners; density and RTL are resolved together at the
   Android boundary.
4. Every layer in one `dropShadows/innerShadows` call shares the shape and preserves list order.
5. `spreadRadius` may be negative. If contraction leaves an empty mask, the layer produces no
   visible result.

## 5. Cache and backend

Static outer and inner shadows use separate process-level bounded raster caches. A key includes:

- View dimensions;
- density and layout direction;
- shape;
- every layer color, blur, spread, and offset.

Rasters that exceed the budget are skipped. Eviction and over-budget counts are available through
`ShadowDecorationLayer.cacheStats()` and `innerCacheStats()`. Memory-pressure handlers and tests
may call `clearCache()`; an application screen must not clear these caches during ordinary
recomposition.

Backend policies:

| Policy | Current behavior |
| --- | --- |
| `Auto` | Selects `ExactBitmap` by default |
| `ExactBitmap` | Draws cached bitmaps directly; exact baseline for API 24+ |
| `RenderNodeDisplayList` | Explicit API 29+ experiment; falls back to Bitmap for software Canvas, unsupported API, or runtime failure |

The first release-mode paired benchmark on Samsung SM-G991B / Android 13 did not show a stable
RenderNode benefit for lists or complex layouts, so the experimental strategy must not become the
default. Dynamic `RenderEffect` blur remains a research item.

## 6. Performance rules

1. Static specifications and stable dimensions produce the best cache hit rate. Lazy items use
   stable keys and shared content types.
2. Prefer animating translation, scale, rotation, and alpha; these do not change the raster key.
3. Do not animate blur, spread, shape, or dimensions every frame. Those changes continually create
   new keys and increase off-screen memory pressure.
4. Large, layered, or dynamic blur requires a device benchmark and memory budget before entering a
   default component.
5. Backend conclusions require multiple runs on the same device, build, and workload, normalized by
   a Compose control group.

See [Performance](../tooling/performance.md) and the
[advanced-shadow execution record](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md)
for the benchmark and first data set.

## 7. Diagnostics and verification

Manual paths:

1. `Catalog -> Graphics -> Outer shadows`
2. `Catalog -> Graphics -> Inner shadows`
3. `Catalog -> Graphics -> Lazy/diagnostics`

The diagnostics page can select Auto/Bitmap/RenderNode, clear caches, refresh the actual backend and
selection reason, and display outer/inner cache hits, misses, evictions, oversized skips, and byte
counts. Leaving the page restores `Auto` so an experimental strategy does not leak into another
Demo.

Automated paths:

```bash
./gradlew :app:testDebugUnitTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.AdvancedShadowDemoDeviceTest
```

The device test verifies single/multi-layer specifications, transparent inner-shadow input,
the actual Auto backend, and repeated-draw cache hits in a Lazy container.
