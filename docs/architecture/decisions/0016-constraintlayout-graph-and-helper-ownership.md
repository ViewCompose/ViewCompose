# ADR-0016: ConstraintLayout graph and helper ownership

- Status: Accepted
- Date: 2026-08-18
- Extends: [ADR-0008](./0008-transactional-effect-lifecycle.md)

## Context

The Alpha ConstraintLayout integration separated renderer-neutral transport, public authoring DSL,
and AndroidX rendering, but its native reconciliation did not have one authoritative candidate
model. Each rebuild cloned the live `ConstraintLayout`, cleared entries, recreated part of the
graph, and asked `ConstraintSet.applyTo` to mutate the container. Flow, Group, Layer, and
Placeholder Views were renderer-owned while Guideline and Barrier Views were native side effects.

That split produced three release-blocking properties. Removing a helper declaration did not prove
that its native View was removed; a missing reference could be skipped while the rest of an invalid
candidate was applied; and a native exception could occur after part of the live tree changed. The
public dimension contract also represented contradictory combinations through independent fields,
accepted `match_parent`, and exposed AndroidX's raw ratio-string grammar.

The first Maven release needs a single correctness model. Alpha compatibility is less valuable
than eliminating ambiguous states before consumers depend on them.

## Decision

ViewCompose hard-cuts the ConstraintLayout integration around one immutable candidate graph and one
native helper owner.

`viewcompose-ui-contract` owns Android-free transport. A dimension is exactly one of wrap content,
constrained wrap content, fixed dp, or match constraints with one spread/wrap/percent mode and
optional min/max bounds. Ratios contain positive width and height terms plus an optional constrained
axis. `match_parent`, independent min/max/percent/constrained flags, and raw ratio strings are not
part of the contract.

`viewcompose-constraintlayout-androidx` owns Q3 authoring. Blank references, duplicate declarations,
invalid local ranges, one-member or duplicate-member chains, competing baseline/edge declarations,
and circle/edge combinations fail synchronously. Relational validity that requires mounted content
remains a renderer preflight responsibility.

Android Renderer merges inline and reusable declarations into a `ResolvedConstraintGraph` without
mutating Views. The compiler validates the complete child/helper namespace, reference existence,
anchor planes, chain ownership, helper dependencies, dimensions, ratios, and numeric ranges. Every
direct content child requires a non-empty semantic ID. Flow and Placeholder are constraint-capable
helper nodes; Guideline, Barrier, Group, and Layer are not ordinary constraint-item sources.

One renderer registry owns the View instance, stable generated View ID, reference array, type, and
removal of every Guideline, Barrier, Flow, Group, Layer, and Placeholder. AndroidX does not create an
untracked helper as a `ConstraintSet.applyTo` side effect. A helper type change is a remove/create
operation inside the same native commit.

An accepted graph is applied from a clean native set, not cloned from the live layout. Before
mutation, the renderer snapshots affected IDs, LayoutParams, helper membership, visibility,
accessibility, and transform properties. It publishes the candidate graph only after native apply,
helper configuration, stale-helper removal, and runtime-property restoration succeed. A failure
restores the previous helper registry, View state, environment, and accepted graph, then emits a
bounded structured rejection keyed by attempted revision, identity, and reason.

Every retained programmatic helper also resolves environment ownership from its container before
graph apply. In particular, its native `layoutDirection` must match the container so AndroidX can
resolve logical Guideline and Barrier semantics correctly after an in-place LTR/RTL transition on
older Android releases.

Layer post-layout work has one generation-checked pre-draw owner and is cancelled on replacement or
detach. A second legacy reconciliation engine, compatibility flag, partial-link recovery branch, or
unbounded string warning cache is prohibited.

The AndroidX runtime baseline moves to stable ConstraintLayout `2.2.2`. The accepted JVM and rooted
Android 9 device evidence covers exact retained-helper geometry, lifecycle, high-risk configuration
changes, and the LTR/RTL transition invariant. The dependency may not silently fall back to `2.2.1`
if later compatibility gates expose a defect.

## Consequences

- Invalid candidates become atomic rejections instead of partially visible layouts.
- Helper View count, IDs, diagnostics, and callbacks have explicit bounded ownership and can be
  stress-tested.
- The Alpha source migration is breaking but removes combinations that had no reliable solver
  meaning.
- Graph compilation allocates an immutable candidate before native work. First-release performance
  acceptance is no-material-regression; topology/scalar fast paths remain post-release work.
- Flow and Placeholder constraints are explicit graph nodes, preserving their AndroidX layout role
  without exposing native Views through the DSL.
- AndroidX-specific strings, constants, LayoutParams, and helper instances remain confined to
  Android Renderer.

## Rejected alternatives

### Keep clone-and-clear and add more catches

Rejected because an exception catch cannot undo native mutations that already occurred, and cloning
the live tree makes prior side effects the source of truth for the next candidate.

### Let ConstraintSet create Guideline and Barrier

Rejected because creation without symmetric ownership and pruning cannot prove removal, stable type,
or bounded retained child count.

### Preserve the old Alpha API with deprecations

Rejected because deprecated independent fields still represent contradictory dimensions and would
require two interpretation paths through the renderer.

### Skip only invalid links

Rejected because the resulting graph differs from the authored graph and can make an unrelated
constraint appear to succeed while geometry is wrong.

### Ship old and new reconciliation behind a flag

Rejected because two production engines double the lifecycle and rollback surface. Revision-to-
revision controls and separate APKs provide comparison without shipping dual ownership.

## Public API and module impact

- `viewcompose-ui-contract` owns Q3 `ConstraintDimension`, `ConstraintMatchMode`,
  `ConstraintRatioSide`, `ConstraintRatio`, and the unified baseline link transport.
- `viewcompose-constraintlayout-androidx` owns Q3 validated references, child builders, helper
  builders, and compiled migration samples.
- `viewcompose-renderer-android` owns graph compilation, stable IDs, complete helper lifecycle,
  native commit/rollback, bounded diagnostics, and exact geometry tests.
- Demo fixtures and module documentation must use only the hard-cut contract and must not preserve
  legacy examples.

## Validation and rollout

Implementation and acceptance evidence is recorded in the completed ConstraintLayout
first-release hardening plan, archived in the repository as
`docs/archive/constraintlayout-native-engine-hardening.md`.
Release requires pure graph and DSL tests, Robolectric exact geometry and rollback, 1,000-switch
helper stress, focused physical-device and warning-free Demo evidence, interpreted performance-safety
controls, Q3 compiled samples, API/documentation gates, Chinese mirrors, and immutable Changesets.
