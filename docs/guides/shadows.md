---
schema_version: 2
document_id: guide.advanced-shadows
doc_type: guide
owner:
  kind: capability
  id: shadow.modifiers
version_lane: released
capability_ids:
  - shadow.modifiers
  - shadow.android-backend
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-shadow-android
sample_ids:
  - guide.shadow-card
task: Add exact outer and inner shadows without changing layout, input, or elevation semantics.
success_checks:
  - Content and shadow use the same explicit shape when their outlines must match.
  - The parent reserves enough visual space and only deliberate viewports clip overflowing shadows.
  - Stable size and shadow specifications reuse raster work while transforms and alpha remain draw-time properties.
  - The optional Android backend is packaged before expecting shadow modifiers to render.
failure_checks:
  - Exact shadows are used as a substitute for Material or platform elevation semantics.
  - A shadow is expected to expand measured bounds or intercept input.
  - Blur, spread, shape, or size is animated every frame without device and memory evidence.
  - RenderNode replay is made the default without same-device paired benchmark evidence.
---

# Add exact outer and inner shadows

Use `elevation` for Material or platform elevation semantics and `zIndex` for sibling order. Use
`dropShadow(s)` or `innerShadow(s)` only when the design needs exact color, blur, spread, offset,
shape, or ordered layers.

## Build one exact shadow surface

Give the content and shadows the same explicit shape when their outlines must match. Singular
modifiers are one-layer conveniences; plural modifiers retain list order. An empty list is a
no-op.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ShadowGuideSamples.kt" region="shadow-card" sample_id="guide.shadow-card" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.ShadowCard() {
    val cardShape = UiShape.rounded(20.dp)

    Surface(
        modifier = Modifier
            .shape(cardShape)
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
                shape = cardShape,
            )
            .innerShadow(
                shadow = UiShadow(
                    color = 0x44000000,
                    blurRadius = 8.dp,
                    offsetY = 3.dp,
                ),
                shape = cardShape,
            )
            .padding(20.dp),
    ) {
        Text("Exact outer and inner shadows")
    }
}
```

Outer shadows draw immediately before native child content. Inner shadows draw after the child's
background, content, subtree, and foreground, but never enter hit testing. Neither form changes
measure or layout, so reserve margin or parent spacing for visible outer blur. Ordinary framework
containers permit overflow; Lazy containers intentionally clip at their viewport.

## Keep raster work stable

Translation, scale, rotation, alpha, ordinary invalidation, and an unchanged size reuse the same
raster identity. Size, density, layout direction, shape, or layer changes create a new identity.
Prefer transform and alpha animation; continuously changing blur, spread, shape, or dimensions
requires a measured device and memory budget.

## Enable and verify the Android backend

Package `viewcompose-shadow-android`; its service registration enables rendering on first use.
Without that artifact, the modifiers are intentional no-ops and the rest of the tree continues to
render. Explicit installation is only needed when application startup must determine backend
availability before the first decorated node.

Verify `Catalog -> Graphics -> Outer shadows`, `Inner shadows`, and `Lazy/diagnostics`. Confirm
layer order, transparent input, expected viewport clipping, and repeated-draw cache hits. Backend
selection, cache budgets, platform fallbacks, diagnostics, and benchmark constraints belong to the
[Shadow Android module manual](../modules/viewcompose-shadow-android/README.md); renderer ordering
and optional-backend ownership belong to [Modifier Architecture](../architecture/modifier.md).
