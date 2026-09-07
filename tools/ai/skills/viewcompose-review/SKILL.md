---
name: viewcompose-review
description: Review ViewCompose Kotlin for real API use, lifecycle, accessibility, and validation evidence. Use for review or diagnosis; do not modify files unless the user also asks for fixes.
---

# Review ViewCompose Code

Return evidence-backed findings ordered by impact, with source locations and concrete corrections.

## Exact version and evidence

1. Preserve the exact framework identity. Use `analyze_project` when repository-wide coordinates,
   artifacts, or migration context affect the review.
2. Resolve questioned symbols with `get_api_reference`. Use `get_component_reference` and
   `get_sample` when overload defaults, receiver rules, or correct usage shape are relevant.
3. Run `validate_code` in static mode for bounded rule findings. Run compile mode when the supplied
   code is a supported complete snippet and exact artifact ownership is known.
4. Separate proven compile errors and stable rule findings from architectural suggestions. Do not
   infer layout geometry, performance, runtime lifecycle behavior, or visual correctness from a
   static scan.
5. For migrations, compare the claimed `capability-probe`, `subtree`, or `whole-screen` intent with
   the actual host root and produce a coverage ledger. Report hidden legacy duplicates, undeclared
   native siblings, or Activity-owned chrome/scrolling/overlay behavior outside a claimed whole
   screen. Include application-level lifecycle callbacks that query or mutate the root before the
   screen host installs content. Review state ownership separately: UI-local state belongs in ViewCompose state, existing
   ViewModel business state should use `viewModel()` plus lifecycle-aware collection under a
   standard host, and an external collector/manual `RenderSession.render()` path requires an
   explicit embedded-boundary rationale.
6. Report each finding with severity, diagnostic code, source, affected artifact/capability when
   available, and the evidence level. If no finding is proven, say so and retain the stated
   limitations.

## Existing-project regression evidence

For an existing Activity or Fragment, check whether baseline and candidate use the same exact
application variant, unit-test task, instrumentation task, matched application/test APK pair,
device/user/storage/permission state, fixture bytes or reproducible generator with SHA-256 manifest,
and critical flow. Candidate evidence must cover initial UI, at least one later state, completion or
navigation, source-fixture integrity, and crash/ANR absence. Treat a project-owned Debug no-ad seam
as deterministic UI evidence only when its activation stays uncommitted, and keep real-ad behavior
as a separate check. Missing or changed inputs make regression parity unverified even when code
compiles or one screenshot matches.

## Stop and authority

A review request is read-only. Offer a patch, but edit only after the user asks for a fix. Do not
run a project-selected build or add dependencies to make a snippet pass. Stop if the same
diagnostic repeats without new evidence, or if correctness depends on unavailable application
behavior; report the missing evidence rather than guessing or upgrading static evidence to
compiled.
