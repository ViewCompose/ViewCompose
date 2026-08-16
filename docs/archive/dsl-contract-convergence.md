# DSL Contract Convergence Plan

## Status

Completed and archived. The accepted hard cut covers the public DSL, interaction and
theme-feedback contracts, retained API documentation, migration guidance, and recurrence gates
defined below.

Last updated: 2026-08-15.

## Maven release changesets

- `release/changes/20260815-dsl-contract-convergence.json`

## Objective

Converge the public component DSL on one compact, renderer-neutral contract. Layout primitives
must describe layout rather than Android ripple implementation; component interaction feedback
must resolve through typed design-system appearance and travel through one general indication
channel; aliases that only pin an already-public enum value must disappear; behavior-profile
wrappers must either own an enforceable semantic contract or collapse into one typed base API; and
every retained non-trivial DSL must meet the repository's Q3 source-documentation standard.

This is an alpha-line hard cut. Compatibility overloads are prohibited when they preserve a second
source of truth, a platform-shaped public field, or two precedence paths for the same appearance.

## Quality levels

| Contract family | Quality level | Acceptance requirement |
| --- | --- | --- |
| Interaction indication and its Modifier API | Q3 | Renderer-neutral KDoc, compiled sample, ordering/shape/state tests, module and architecture documentation |
| Immutable indication/state-layer value contracts and NodeSpec fields | Q2 | Complete property contracts, equality/patch tests, custom-renderer migration note |
| Retained layout, component, collection, input, and overlay DSLs | Q3 | Consumer-oriented KDoc, every receiver and parameter, compiled sample, applicable lifecycle/layout/performance contracts |
| Typed text-input profiles and line policy | Q3 when introduced | Enforced defaults, override precedence, IME/autofill tests, migration sample |
| Removed aliases | Breaking removal | Call-site migration, API dump update, release changeset, no deprecated compatibility wrapper |

## Architectural principles

1. `Box`, `Row`, and other layout primitives own measurement, placement, child scope, and ordered
   caller modifiers. They do not expose interaction-feedback colors.
2. Android `RippleDrawable` and a single `rippleColor` remain renderer implementation details, not
   platform-neutral public contracts.
3. A component resolves pressed, focused, and hovered feedback from its semantic content role,
   typed sparse overrides, complete Basic style, and active design-system recipe before emission.
4. General interaction feedback travels through one ordered Modifier indication element. A
   high-level component installs its resolved indication internally; application code may use the
   low-level modifier only when building a custom interactive surface.
5. Components whose native backend owns multiple internal targets, including tabs, destinations,
   segmented items, and native toggles, carry typed per-target interaction values in their
   component NodeSpec. They never fall back to a parallel single-color contract.
6. ADR-0013 remains authoritative for appearance ownership and precedence. Using Modifier as the
   renderer-neutral execution channel does not move semantic component override precedence into a
   caller modifier.
7. A public alias is retained only when it adds an observable semantic, state, lifecycle,
   measurement, accessibility, performance, or design-system contract. Pinning one enum value is
   insufficient.
8. Documentation work follows the final retained API. APIs removed in the same hard cut do not
   receive temporary Q3 prose or samples.

## Baseline findings

1. Public `Box` exposes `rippleColor` and delegates to internal uppercase `StateLayerBox`; public
   `Row` delegates to the analogous `StateLayerRow`.
2. `BasicSurface` exposes both `stateLayerColors` and a value-only `rippleColor` fallback.
3. FAB and SegmentedControl overrides expose both legacy and multi-state paths; TabRow,
   NavigationBar, Checkbox, Switch, and RadioButton still expose only a single interaction color.
4. Nine public NodeSpec families carry `rippleColor`, including generic Box, Row, and Surface
   containers and component-specific action, selection, toggle, tab, and navigation nodes.
5. `TextButton`, `ElevatedCard`, and `OutlinedCard` add only a fixed variant value.
6. Password, email, number, and multiline field wrappers duplicate most of TextField while allowing
   callers to replace the defaults that supposedly distinguish them.
7. `AnimatedContent` currently performs only a cross-fade and `Crossfade` is a parameter wrapper
   over the same implementation.
8. In the scanned public component-entry surface, 30 declarations have neither parameter
   documentation nor a compiled sample. The repository Dokka audit passes because it detects
   missing comments but cannot classify Q1, Q2, or Q3 quality.

## Phase 0: Decision and inventory lock

Status: completed.

1. Add an ADR for resolved interaction indication and layout-primitive purity.
2. Record every public DSL alias, every public `rippleColor` field, every NodeSpec consumer, and
   every Android binder/patch path before changing production source.
3. Classify retained APIs, removals, family redesigns, and intentional overloads.
4. Assign Q levels and identify affected module manuals, architecture pages, migration pages,
   compiled samples, API dumps, and release artifacts.

Keep criteria:

- no affected public or custom-renderer contract is omitted;
- the decision remains independent of Material 3 and One UI 7;
- no layout primitive gains a larger interaction parameter surface.

## Phase 1: Interaction indication hard cut

Status: completed.

1. Add one immutable renderer-neutral interaction-indication contract and ordered Modifier
   element in UI Contract.
2. Remove `rippleColor` and parallel nullable fallback fields from generic Box, Row, and Surface
   NodeSpecs and from action/component NodeSpecs that already have complete state-layer values.
3. Replace single-color selection and native-control contracts with typed selected/unselected or
   checked/unchecked interaction values where the backend owns multiple internal targets.
4. Migrate Android Renderer style resolution and background creation to the new indication while
   retaining Android ripple creation strictly inside the renderer.
5. Preserve pressed-before-focused-before-hovered precedence, transparent inactive/disabled
   states, shape masking, retained-View patching, rollback, and reuse.

Keep criteria:

- unchanged indications do not rebuild native backgrounds;
- changed indications patch the retained View without rebuilding the logical node;
- disabled or non-clickable nodes install no active feedback;
- custom renderer obligations are explicit and mechanically tested.

## Phase 2: Layout and component migration

Status: completed.

1. Remove `rippleColor` from public `Box` and from `BasicSurface`'s loose parameter list.
2. Move complete Basic-surface interaction appearance into its resolved style contract.
3. Migrate FAB, extended FAB, Card, ListItem, Chip, DropdownMenuItem, Surface, Button, IconButton,
   SegmentedControl, TabRow, NavigationBar, Checkbox, Switch, and RadioButton.
4. Delete `StateLayerBox` and `StateLayerRow`; interactive composites use ordinary layout plus the
   resolved indication channel without adding an unnecessary native wrapper.
5. Keep Material 3, One UI 7, and internal contrast recipes independent and prove all three can
   resolve the same neutral contract.

## Phase 3: Public DSL redundancy removal

Status: completed.

1. Remove `TextButton`; migrate to `Button(variant = ButtonVariant.Text)`.
2. Remove `ElevatedCard` and `OutlinedCard`; migrate to `Card(variant = ...)`.
3. Replace duplicated TextField wrappers with one enforceable typed input-profile and line-policy
   model, preserving password security, autofill, IME, transformation, and accessibility behavior.
4. Retain `Crossfade` as the accurately named alpha-only API and remove the misleading current
   `AnimatedContent` entry until a genuinely broader transition contract exists.
5. Keep intentional Basic/high-level, leaf/composite, raw/styled overlay, data/scope collection,
   and design-system-specific pairs.

## Phase 4: Source documentation and automated gates

Status: completed.

1. Rewrite retained weak DSL KDoc in consumer terms, including measurement, state ownership,
   callbacks, environment/theme propagation, accessibility, Modifier ordering, performance, and
   failure behavior where applicable.
2. Add or update compiled Q3 samples before linking them from KDoc.
3. Add a structural DSL documentation verifier for receiver coverage, every public parameter, and
   the registered Q3 sample requirement. The gate supplements Dokka and does not pretend to judge
   prose quality mechanically.
4. Update UI Contract, UI Foundation, Android Renderer, animation, theming, text-input, migration,
   architecture, and reviewed Chinese public documentation in the same change.

## Phase 5: Release and validation closure

Status: completed.

1. Add one immutable release changeset classifying every directly changed published artifact and
   concrete ignored reasons where required.
2. Run focused contract, Foundation, renderer, animation, text-input, design-system, API sample,
   and documentation tests during implementation.
3. Run API dumps, `auditViewComposeApiDocs`, documentation structure/localization/site gates,
   `verifyDevelopmentToolingIsolation`, `qaQuick`, and the applicable device-backed regression
   gate after focused tests pass.
4. Interpret accepted evidence here and in the owning active documentation with comparison
   context, absolute result, normalized change where applicable, conclusion, limitations, and next
   action.
5. Move this plan to `docs/archive/` only after all durable contracts live in architecture, module,
   guide, and migration documentation.

## Implementation evidence

- UI Contract now owns `UiInteractionIndication` and the ordered
  `Modifier.interactionIndication` element. Generic layout and surface NodeSpecs no longer carry
  interaction colors; native multi-target controls carry complete selected/unselected state-layer
  snapshots.
- UI Foundation installs resolved indication modifiers, removes state-layer pseudo-layouts and
  redundant aliases, enforces typed TextField input/line policies, and removes the unused
  `UiColors.ripple` and `UiStateColors.controlHighlight` theme slots in favor of
  `UiInteractionTokens`.
- Android Renderer resolves indication changes as modifier-only patches while retaining
  `RippleDrawable`, state-list, masking, reset, and lifecycle details internally. Material 3,
  One UI 7, and the Demo contrast recipe compile against the same neutral contract.
- `Crossfade` is the sole alpha-only content transition entry and supports nullable target values
  without using `null` as an internal absence sentinel.
- `verifyDslApiContracts` rejects the removed aliases, public ripple/highlight contracts, public
  `AnimatedContent`, and retained public `UiTreeBuilder` entries without complete KDoc parameters
  and a compiled sample reference.
- Focused verification passed on 2026-08-15 for UI Contract, 327 UI Foundation tests, Android
  Renderer, animation, Material 3, One UI 7, Demo compilation, and Demo unit tests.
- `qaQuick` completed 1,620 tasks successfully. API-documentation, localization,
  release-intent, documentation-structure, and development-tooling-isolation gates also passed.
- The targeted Android test
  `material3ActionsAndComposites_resolveStandardStateLayersWithPinnedPrecedence` passed on one
  SM-G991B running Android 13. It verified stateful and focus-aware feedback for Button, Chip, FAB,
  and every SegmentedControl target. This is representative device behavior evidence, not a
  cross-device visual benchmark; no performance comparison is claimed from these behavior tests.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Contract | Modifier/NodeSpec equality, API dump, custom-renderer obligation, compiled sample |
| Interaction states | Pressed/focused/hovered order, selected/unselected and checked/unchecked roles, disabled transparency |
| Android rendering | Shape clipping, native ripple lifecycle, retained-View patch, reset/reuse, rollback |
| DSL | Alias absence, pure Box/Row signatures, typed TextField profiles, Crossfade-only animation entry |
| Multi-design system | Foundation, Material 3, One UI 7, and contrast recipe compilation and resolution tests |
| Documentation | Q2/Q3 KDoc shape gate, Dokka audit, English/Chinese structure and site build |
| Repository | Focused tests, `qaQuick`, relevant device tests, release changeset validation |

## Completion criteria

This plan completes only when public layout primitives have no interaction-color parameters, no
public or NodeSpec contract retains a value-only ripple compatibility path, internal state-layer
pseudo DSLs are gone, approved aliases and duplicated behavior wrappers are removed, every retained
non-trivial component DSL meets Q3, the structural documentation gate prevents recurrence, all
affected module and migration documentation is current, and focused plus repository validation
passes without a known behavioral or performance regression.
