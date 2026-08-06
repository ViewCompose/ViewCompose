# Graphics Core

`viewcompose-graphics-core` is ViewCompose's platform-neutral immediate-graphics model. It defines
geometry, paths, brushes, paint and filters, ordered draw commands, validated reusable scenes, a
mutable recorder, and a single-entry draw cache. It contains no Android Canvas, Bitmap, View, or
composition dependency.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha02")
}
```

- Stability: **Alpha**. Command ordering, scene validation, and current geometry/paint models are
  reviewed and tested; richer text, image ownership, and filter capability contracts may evolve.
- Platform: Kotlin/JVM with no Android framework dependency.
- The artifact has no runtime dependencies and can be used in deterministic unit tests and tooling.
- Applications normally receive it transitively through `viewcompose-graphics` or core UI modules.

## Coordinate and color conventions

Coordinates use the current renderer drawing space. Android starts with physical pixels, x to the
right, y downward, and clockwise positive angles; transforms can change that space. Geometry values
are intentionally lightweight and do not reject negative, inverted, or non-finite inputs. Validate
application data before recording when deterministic cross-renderer output matters.

`UiColor` is a source alias for a packed, non-premultiplied `0xAARRGGBB` integer. `DrawPaint.alpha`
is an additional multiplier conventionally in `0f..1f`; the Android renderer clamps it. Gradient
stops are neither sorted nor validated, so provide at least renderer-supported stops ordered by an
offset in `0f..1f`.

## Geometry and matrices

`Offset`, `Size`, `Rect`, `Radius`, and `RoundRect` mirror common Canvas geometry while retaining
the caller's values. `Rect.width` and `height` are signed edge differences. Corner radii are not
pre-clamped against bounds.

`Matrix3` stores nine row-major coefficients and compares them by content. Construction copies the
input array, but its public `values` array remains mutable. Treat a matrix as frozen after placing it
in a command or cache key; mutation can change equality and hash code after insertion.

## Paths

`PathModel` is an ordered command list plus `NonZero` or `EvenOdd` fill rule. The fluent `PathBuilder`
snapshots commands at `build`, so later builder reuse cannot alter an earlier model. Direct
`PathModel` construction retains the provided list and should receive an immutable list.

```kotlin
val triangle = path {
    moveTo(8f, 8f)
    lineTo(56f, 8f)
    lineTo(32f, 48f)
    close()
}
```

Arc angles follow Android's default convention. `forceMoveTo` starts a new contour at the arc start;
otherwise the renderer connects from the current point. Numeric path inputs are not validated.

## Brushes, style, and filters

Brushes support solid color, linear, radial, and sweep gradients. `DrawStyle` selects fill or stroke,
including width, cap, join, and miter limit. Values are renderer requests rather than guarantees;
platform support and API level determine exact blend and filter output.

`ColorFilterModel` supports tint and a 4-by-5 matrix. The matrix must contain 20 elements, but its
array is retained and data-class equality compares the array by identity. Reuse one immutable array
or a stable wrapper when filter equality controls caching. `ImageFilterModel` supports blur and
ordered chains where `inner` runs before `outer`.

`DrawPaint` is shallowly immutable: nested stop lists and filter arrays are not defensively copied.
Freeze them before recording. Alpha, stroke dimensions, radii, and gradient stops are stored without
validation so custom renderers can define their platform policy.

## Commands and scenes

`DrawCommand` separates state operations, transforms, clips, nested scenes, geometry, image, and text
commands. Renderers replay list order exactly. `Save` and `SaveLayer` push state; `Restore` pops it.

`DrawScene` copies its command list and rejects an unmatched restore or remaining save depth.
Nested scenes validate independently and can be reused under different transforms and clips:

```kotlin
val badge = drawScene {
    save()
    clipRect(Rect(0f, 0f, 64f, 48f))
    drawPath(triangle, DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())))
    restore()
}
```

`ImageRef` carries a host-defined stable ID and declared intrinsic pixel size; it does not own or
load pixels. `TextStyle` intentionally covers only pixel size, bold, and italic. It does not provide
font family, shaping, wrapping, locale, alignment, or rich spans.

## Recorder and caching

`DrawRecorder` is a mutable, thread-confined builder. `toCommands` snapshots without validating
save/restore; `toScene` snapshots and validates. `group` builds a separately validated nested scene.
After exporting, `clear` can reuse the recorder without changing prior snapshots.

`DrawCache<T>` retains one non-null value under one equality-based key. A different key replaces the
entry. It does not observe state, synchronize threads, or infer size/density/theme inputs; include
every semantic dependency in the key and clear it when external inputs change. `null` results are
never cache hits. Builder failure propagates and leaves the old entry untouched.

## Testing custom graphics code

- Validate save/restore balance with `toScene`, including nested scene boundaries.
- Test command order and immutable snapshots after recorder and builder reuse.
- Include size, density, theme, and resource identity in cache-key tests.
- Test gradient stop ordering, matrix-array mutation, and filter-array identity explicitly.
- Keep Android BlendMode, shader, bitmap, text, and image-filter fidelity tests in the renderer or
  `viewcompose-graphics` integration layer.

The module suite covers cache hits and replacement, command recording, nested scene reuse,
save/restore rejection, path command order, and fill type.

## Related documentation

- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-graphics-core` API tree](https://docs.viewcompose.com/api/viewcompose-graphics-core/current/).

## Compatibility notes

The `0.1.0-alpha02` line establishes Android-aligned coordinate and color conventions, ordered
command replay, balanced immutable scenes, shallow immutable paint models, lightweight image
references, and single-entry explicit-key caching. Platform execution belongs to the renderer and
composition modifiers belong to `viewcompose-graphics`.
