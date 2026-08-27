---
schema_version: 2
document_id: architecture.renderer-neutral-interaction-indication
doc_type: architecture
slug: /architecture/decisions/renderer-neutral-interaction-indication
owner:
  kind: capability
  id: modifier.interaction
version_lane: released
capability_ids:
  - modifier.interaction
  - foundation.components
  - material3.components
  - oneui7.components
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-material3
  - viewcompose-oneui7
sample_ids:
  - architecture.modifier-interaction
  - module.ui-foundation-profile-summary
  - module.material3-components
  - module.oneui7-components
invariants:
  - Interaction indication is one ordered renderer-neutral modifier value resolved above the renderer rather than an Android-shaped layout or theme slot.
  - Disabled components install no indication, while native multi-target controls receive complete typed state-layer values.
evidence:
  - UI Contract ordering, renderer state-list, retained-View patch, component disabled-state, multi-target, design-system compilation, API, and source-gate suites.
---

# ADR-0014: Renderer-neutral interaction indication

- Status: Accepted
- Date: 2026-08-15
- Extends: [ADR-0013](./0013-component-appearance-resolution-boundary.md)

## Context

ViewCompose layout primitives and component NodeSpecs exposed Android-shaped `rippleColor`
properties. Several components also exposed complete pressed, focused, and hovered state layers,
creating two precedence paths for the same feedback. `StateLayerBox` and `StateLayerRow` then made
interaction feedback look like a distinct layout primitive even though it did not change
measurement or placement.

This leaked Android's `RippleDrawable` execution model into UI Contract, expanded otherwise simple
layout DSLs, and forced a NodeSpec change and rebind for feedback that can be patched as ordered
modifier appearance. It also made a single pressed color incapable of representing selected versus
unselected internal targets in native-backed controls.

## Decision

UI Contract owns a sealed, immutable `UiInteractionIndication` value and an ordered
`Modifier.interactionIndication` element. The current `StateLayer` subtype carries complete
`UiStateLayerColors`; Android Renderer maps it to platform state and mask drawables. New indication
subtypes require an explicit compatibility decision because renderers must handle every subtype in
the artifact version they consume.

`Box`, `Row`, and other layout primitives own only measurement, placement, child scope, and caller
modifiers. They never accept interaction colors. High-level components resolve semantic roles,
enabled policy, sparse overrides, and the active design-system recipe before installing an
indication. `BasicSurfaceStyle` may carry the complete resolved indication, but application-facing
sparse overrides remain component-specific under ADR-0013.

The theme boundary follows the same model. `UiColors` contains semantic color roles and
`UiStateColors` contains persistent component states; neither exposes the Android-shaped
`ripple`/`controlHighlight` slots. `UiInteractionTokens` owns pressed, focused, and hovered
opacities. A design-system adapter may read a platform highlight internally, but it must resolve
that input into the neutral interaction policy or an explicit component indication before entering
UI Foundation.

Native-backed components with multiple internal interaction targets carry complete typed values in
their component NodeSpec. Segmented controls and navigation destinations therefore retain selected
and unselected state-layer snapshots. Tab rows emit eager keyed child boxes, so each child owns its
indication through the ordinary modifier path.

Disabled or non-interactive high-level components install no indication. An absent low-level
indication leaves feedback fallback to the renderer only when a custom node is otherwise
interactive. Android ripple color, state-list construction, masking, and drawable lifecycle remain
private renderer details.

This is an alpha-line hard cut. `rippleColor`, `UiColors.ripple`,
`UiStateColors.controlHighlight`, `StateLayerBox`, and `StateLayerRow` are removed rather than
deprecated, and indication changes use modifier-only patching instead of logical-node rebind.

## Consequences

- Layout APIs remain small and portable across Android and future renderers.
- Design systems resolve one complete feedback contract without Material 3 becoming framework
  policy.
- Theme snapshots cannot carry an unused Android highlight slot beside the effective interaction
  opacity policy.
- Pressed, focused, and hovered precedence is explicit and testable.
- Native multi-target controls can render distinct selected and unselected feedback.
- Custom components may opt into feedback through one ordered modifier without manufacturing a
  pseudo layout primitive.
- Custom renderers must exhaustively implement the indication types shipped by their UI Contract
  version.
- Modifier equality makes an indication change patch the retained View without re-composing layout
  semantics or rebuilding the node.

## Rejected alternatives

### Keep `rippleColor` as a compatibility fallback

Rejected because it preserves two sources of truth and cannot express focus, hover, or per-target
roles.

### Put feedback fields on every clickable NodeSpec

Rejected because general appearance belongs to the ordered modifier channel and would otherwise
force component-specific binder and differ logic.

### Keep `StateLayerBox` and `StateLayerRow`

Rejected because state-layer drawing does not define a different measurement, placement, state, or
lifecycle primitive.

### Make one design system own indication policy

Rejected because Material 3, One UI 7, application design systems, and future recipes are peer
implementations above the same neutral contract.

## Validation

The contract requires equality and modifier-order tests, pressed/focused/hovered state-list tests,
disabled absence tests, retained-View modifier-only patch tests, native selected/unselected target
tests, multi-design-system compilation, API dumps, compiled samples, and a source gate that rejects
public `rippleColor` and interaction pseudo-layout APIs.
