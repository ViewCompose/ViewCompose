---
title: Migrate converged DSL contracts
---

# Migrate converged DSL contracts

This alpha release removes redundant component aliases and Android-shaped interaction fields. The
hard cut keeps one semantic source of truth per component and one renderer-neutral execution path
for general feedback.

## Interaction feedback

`Box` and `Row` are pure layout primitives. They no longer accept `rippleColor`, and the internal
`StateLayerBox` and `StateLayerRow` pseudo-components are gone. High-level components resolve
pressed, focused, and hovered roles from the active design system and install
`UiInteractionIndication.StateLayer` automatically. Disabled or non-interactive components install
no indication.

Theme construction follows the same hard cut. Remove `ripple` from `UiColors` and
`controlHighlight` from `UiStateColors`; configure pressed, focused, and hovered policy through
`UiThemeTokens.interactions`. Framework light/dark defaults preserve their previous state-layer
opacity, while directly constructed custom themes use the documented neutral interaction defaults
unless they provide `UiInteractionTokens` explicitly.

Custom interactive surfaces combine `Modifier.interactionIndication(...)` with their input
modifier. The indication value is complete and renderer-neutral; Android Renderer alone maps it to
a `RippleDrawable`. Native-backed controls with multiple internal targets, such as
SegmentedControl and NavigationBar, receive selected and unselected state-layer snapshots through
their component contract.

Custom renderers must consume `InteractionIndicationModifierElement` and exhaustively handle every
`UiInteractionIndication` subtype in the UI Contract version they use. There is no legacy
single-color fallback in public NodeSpecs.

## Component aliases

Replace aliases that only selected an existing variant:

| Removed API | Replacement |
| --- | --- |
| `TextButton(...)` | `Button(..., variant = ButtonVariant.Text)` |
| `ElevatedCard(...)` | `Card(..., variant = CardVariant.Elevated)` |
| `OutlinedCard(...)` | `Card(..., variant = CardVariant.Outlined)` |

The replacement retains the same component ownership, design-system resolution, sparse overrides,
and accessibility behavior without a second discoverability or maintenance surface.

## Text input profiles

`PasswordField`, `EmailField`, `NumberField`, and `TextArea` previously duplicated TextField while
allowing callers to replace the behavior that distinguished each wrapper. Use one `TextField` with
enforceable values instead:

| Removed API | Replacement value |
| --- | --- |
| `PasswordField` | `inputProfile = TextFieldInputProfile.Password` |
| `EmailField` | `inputProfile = TextFieldInputProfile.Email` |
| `NumberField` | `inputProfile = TextFieldInputProfile.Number` |
| `TextArea` | `linePolicy = TextFieldLinePolicy.MultiLine(minLines, maxLines)` |

`TextFieldInputProfile` couples keyboard and autofill semantics. `TextFieldLinePolicy.SingleLine`
or validated `MultiLine` owns visual-line policy. Appearance remains in `TextFieldOverrides`, while
editable content, selection, composition, and undo history remain in `TextFieldState`.

## Animation naming

The former `AnimatedContent` implementation performed only an alpha cross-fade. It is removed in
favor of `Crossfade`, whose name matches its contract. Nullable target states remain supported.
Size transforms, slide transitions, content keys, and per-pair transition scopes are not implied.

## Validation and source examples

The compiled UI Foundation samples demonstrate layout primitives, interaction indication, typed
TextField profiles, variants, and retained collection identity. The compiled animation sample uses
`Crossfade`. `verifyDslApiContracts` rejects public `rippleColor`, removed aliases, and public
`UiTreeBuilder` DSL entries without complete parameter KDoc and a compiled sample reference. The
same gate rejects reintroduction of public ripple/highlight theme slots.
