# Component Appearance Override Convergence Plan

## Status

Completed on 2026-08-15. ADR-0013, the Foundation hard cut, downstream migrations, current Demo
fixtures, compiled samples, public documentation, and repository quality gates are complete.

Last verified: 2026-08-15.

## Goal

Apply [ADR-0013](../architecture/decisions/0013-component-appearance-resolution-boundary.md)
across UI Foundation without growing the normal component DSL, leaking design-system recipes into
Foundation, or retaining parallel color-only override paths.

## Current audit

The current Demo uses a scenario catalog and independently launched scenario Activities. Component
override fixtures now live in the Foundations and Input scenarios; migration must preserve those
routes and automation contracts rather than restoring the former chapter-tab shell.

The production audit found:

- nested `Provide*Colors` scopes replace instead of fieldwise-merging sparse patches;
- filled and tonal TextField error-container override fields are declared but unused;
- Checkbox, Switch, RadioButton, and Slider use one underspecified color model despite different
  native state lists;
- `BasicTextField` takes individual resolved appearance values instead of one complete style;
- Button, IconButton, TextField, input controls, progress indicators, SegmentedControl, TabRow, and
  NavigationBar expose low-frequency appearance parameters or partial color-only exceptions; and
- image loading, keyboard behavior, controlled state, callbacks, navigation, lazy revision, and
  lifecycle parameters are behavior contracts and must not enter appearance overrides.

## Phases

### Phase 0: contract and inventory

- [x] Accept the sparse high-level Overrides and complete Basic Style boundary.
- [x] Re-audit the refactored Demo entry points and current override fixtures.
- [x] Record Q levels, precedence, nesting, and hard-cut compatibility policy.

### Phase 1: override core and correctness hard cut

- [x] Replace color-only models/providers with component-specific `XxxOverrides` and
  `ProvideXxxOverrides` APIs.
- [x] Use named locals and merge nested patches field by field.
- [x] Split Checkbox, Switch, RadioButton, and Slider state models.
- [x] Make TextField error-container slots observable and deterministic.
- [x] Add instance-level overrides with precedence over scoped providers.

### Phase 2: Basic style and large-signature convergence

- [x] Add complete `BasicTextFieldStyle` and remove individual appearance parameters from
  `BasicTextField`.
- [x] Move low-frequency appearance inputs for Button, IconButton, TextField, SegmentedControl,
  progress indicators, TabRow, and NavigationBar into their override models.
- [x] Keep controlled state, callbacks, image request options, keyboard/IME behavior, keys, and
  modifiers explicit.
- [x] Migrate Foundation composites, Material 3, One UI, Preview, compiled samples, and the current
  Demo.

### Phase 3: remaining component audit

- [x] Keep this audit unactivated and move it to the
  [unified roadmap](../project/roadmap.md#42-deferred-design-system-enhancement-candidates). FAB,
  Scaffold, app bars, Badge, Dialog, and bottom-sheet retain their current contracts until
  repository usage or another real design system meets the recorded activation trigger.
- [x] Preserve `Text` style/color, `Icon` tint/size, `Image` loading semantics, `Surface` content
  color, and simple Divider values as direct or existing-policy inputs; no speculative override was
  introduced.

### Phase 4: validation and closure

- [x] Cover fallback, nested merge, restoration, instance precedence, state resolution, environment
  change, and Basic style-to-NodeSpec mapping.
- [x] Pass Foundation, Material 3, One UI, Preview, app, API documentation, documentation, and
  development-tooling gates.
- [x] Record the immutable release changeset and update owning module and theming manuals.
- [x] Move the unactivated audit to the unified roadmap and archive this completed plan.

## Acceptance criteria

1. No production `*ColorOverride` or `Provide*Colors` API remains.
2. Inner scopes preserve every unspecified outer field and instance overrides win field by field.
3. Every input-control family exposes state slots matching its renderer contract.
4. `BasicTextField` reads no Theme or component Local and accepts one complete resolved style.
5. The refactored Demo's Foundations and Input scenarios visibly exercise nesting, state-specific
   values, and instance precedence.
6. Public signatures, KDoc, compiled samples, module manuals, and English/Chinese public docs agree.

## Completion evidence

- `:viewcompose-ui-foundation:testDebugUnitTest`, `:viewcompose-material3:testDebugUnitTest`,
  `:viewcompose-oneui7:testDebugUnitTest`, `:viewcompose-preview:testDebugUnitTest`, and
  `:app:testDebugUnitTest` passed together after downstream migration.
- `:viewcompose-ui-foundation:dokkaGenerateModuleHtml` passed with the Q2/Q3 override, provider,
  complete-style, and affected-component contracts documented.
- `qaQuick` passed 1,619 Gradle tasks. It covered repository compilation and unit tests, local
  publication metadata, compiled samples, documentation structure and language checks, release
  intent, module boundaries, and development-tooling isolation.
- Deterministic tests prove fallback, fieldwise nested merge, provider restoration, instance
  precedence, input-control state resolution, environment fallback, validation, and direct
  `BasicTextFieldStyle` to NodeSpec mapping. The current Foundations and Input Demo scenarios expose
  the same cases for manual verification.
- Conclusion: **improved** for API ownership and deterministic appearance resolution. The default
  `None` path avoids constructing a merged override value by identity, but no frame or allocation
  benchmark was run, so runtime performance change is **inconclusive** and no quantitative claim is
  made.
- Limitation and next action: device visual verification remains useful but is not required to
  establish the pure resolution contract. Any future component-family expansion starts only after
  the roadmap activation trigger is met and must bring its own visual and performance evidence.

## Maven release changesets

- `release/changes/20260815-component-appearance-convergence.json`
