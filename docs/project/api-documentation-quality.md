# API Documentation Quality Standard

## Purpose

This document defines what counts as acceptable KDoc or Javadoc for ViewCompose published APIs.
It converts the general API documentation contract in
[Documentation Governance](documentation-governance.md#kdoc-and-javadoc-contract) into a
repeatable authoring, review, and automation standard.

The goal is not to maximize comment volume. The goal is to let a consumer use an API correctly
without reading its implementation, guessing its lifecycle, or discovering failure behavior in
production.

## Scope

The standard applies to declarations shipped by a published Maven artifact and rendered by Dokka:

- public types, type aliases, annotations, constructors, properties, functions, and extension
  functions;
- protected declarations that are intentional subclass or implementation extension points;
- public Java declarations consumed from the same artifacts;
- deprecated declarations until their supported removal version.

It does not require independent comments for compiler-generated or obvious members suppressed by
Dokka, private and internal implementation details, test fixtures, demo-only APIs, or an override
whose inherited contract is complete and unchanged.

An undocumented protected member is not solved by hiding it from Dokka. Either document it as a
supported extension point or reduce its visibility.

## Canonical language

Public API comments use English as their canonical language. Do not maintain complete English and
Chinese copies in the same KDoc block. Existing bilingual blocks may be migrated when their owning
API family is reviewed, but a touched public comment must move toward the canonical form rather
than adding another parallel paragraph.

Chinese tutorials and module manuals may explain the API in Chinese and link to the canonical
generated reference. Identifiers, code, units, and platform terms remain unchanged.

## Quality levels

Each documentable declaration has one of four levels:

| Level | Meaning | Acceptance |
| --- | --- | --- |
| Q0 — Missing | No useful documentation, or text merely repeats the declaration name | Never acceptable for a new published API |
| Q1 — Discoverable | States the purpose and observable result | Acceptable only for self-evident constants, enum entries, marker types, and equivalent low-risk declarations |
| Q2 — Contract complete | Covers all applicable behavioral fields in this standard | Minimum for normal public and protected APIs |
| Q3 — Guided | Q2 plus a compiled sample, decision guidance, or operational example | Required for high-risk or non-trivial API families |

The owning type can document a group of self-evident enum entries or constants together. A group
description is valid only when each member's meaning is unambiguous from that mapping.

## Contract fields

Every declaration starts with a concise summary that completes the sentence “This API…”. Add only
the fields that apply, but never omit a field that changes correct use:

| Concern | Required information |
| --- | --- |
| Behavior | Observable result, important invariants, idempotence, and ordering |
| Inputs | Meaning, units, coordinate space, valid range, defaults, and sentinel values |
| Outputs | Ownership, mutability, snapshot/live behavior, nullability beyond the type, and identity guarantees |
| State | Owner, retention, restoration, observation, and interaction with recomposition |
| Lifecycle | Start, attachment, disposal, reuse, and behavior after destruction |
| Concurrency | Thread confinement, synchronization, reentrancy, cancellation, and last-writer policy |
| Callbacks | Invocation timing, thread, frequency, ordering, and whether re-entry is supported |
| Failure | Validation, thrown exceptions, partial effects, rollback, retry, and fallback behavior |
| Android | API level, host requirements, configuration changes, resource/theme behavior, and platform differences |
| Performance | Complexity, allocation, caching, blocking work, and costs that affect API choice |
| Compatibility | Stability level, experimental requirements, deprecation replacement, and migration constraints |

High-risk APIs require Q3. An API is high-risk when it owns mutable state or resources, crosses an
Android host boundary, launches asynchronous work, exposes callbacks or flows, participates in
transactions, accepts units or coordinates, performs I/O, has non-obvious failure recovery, or has
material performance trade-offs.

## Declaration-specific rules

### Types and constructors

- Explain the abstraction's role, owner, lifetime, and non-goals.
- Document every public constructor parameter with `@param`, or every promoted primary-constructor
  property with `@property`.
- State whether callers should construct the type directly or use a factory or `remember` API.
- Interfaces and abstract classes define implementation obligations, callback ordering, and
  threading expectations.

### Functions and properties

- Use an imperative summary for an action and a noun phrase or “Returns…” summary for a query.
- Document all parameters whose meaning is not completely expressed by their name and type. For a
  uniform public contract, prefer documenting every public parameter.
- Use `@return` when ownership, identity, caching, sentinel values, or failure cannot be inferred
  from the return type.
- Mutable properties state who may write them, how changes are observed, and any thread or
  lifecycle restriction.
- Boolean properties describe what `true` means; avoid summaries that only repeat `is...`.

### Suspend functions, flows, and callbacks

- State when execution starts and which dispatcher or thread constraint applies.
- Describe cancellation propagation and whether cancellation leaves partial effects.
- For `Flow`, state cold/hot behavior, replay, completion, error propagation, and collection
  lifecycle where applicable.
- For callbacks, state invocation timing, ordering, multiplicity, thread, and reentrancy.

### Deprecated and experimental APIs

- `@Deprecated` declarations name a usable replacement and link it when possible.
- Explain any semantic difference that prevents mechanical replacement.
- Experimental APIs state the unstable contract and required opt-in annotation.
- Removal timelines belong in migration or release documentation, linked from the API comment.

## KDoc and Javadoc form

- Put the summary in the first paragraph; Dokka uses it in indexes and search results.
- Use paragraphs and short lists for contracts. Do not encode structure through manual spacing.
- Link symbols with KDoc links such as `[RenderSession]`; use backticks for literals, Gradle
  coordinates, and values such as `null` or `0`.
- Use `@param`, `@property`, `@return`, `@throws`, `@sample`, `@see`, and `@since` consistently.
- Every `@param` or `@property` name must resolve to the declaration.
- `@throws` documents observable contract failures, not impossible implementation details.
- Avoid implementation narration unless the implementation technique is itself a supported
  performance or interoperability contract.
- Avoid marketing claims, unsupported parity claims, and statements that tests do not protect.

The following comment is intentionally contract-focused:

```kotlin
/**
 * Applies [block] to the current composition and commits its state when rendering succeeds.
 *
 * Only one prepared composition may be active at a time. If [block] fails, the previous slot and
 * observation state remains active and the exception is propagated to the caller.
 *
 * @param block computation executed in the current composition context.
 * @return the value produced by [block] after a successful commit.
 * @throws IllegalStateException if another prepared composition is still active.
 */
fun <T> composeRoot(block: () -> T): T
```

## Samples

Use `@sample` for Q3 documentation. The referenced function must compile in a maintained sample or
test source set and use public APIs. It must demonstrate one focused contract rather than duplicate
an entire tutorial.

Inline snippets are allowed for a short literal, command, or signature fragment. Non-trivial
standalone snippets must be copied from compiled code or covered by a compilation test. A sample
that no longer compiles is an API documentation failure.

## Generated reference requirements

Comment quality is necessary but not sufficient. The generated reference also requires:

- public and protected visibility coverage;
- module name and independently published version;
- source links pinned to the matching release tag or immutable revision;
- resolvable KDoc symbol and external dependency links;
- retained deprecated APIs and replacement guidance;
- suppressed generated and obvious members that add no consumer value.

Source-link pinning and package/module overviews are part of API completeness work. They must not be
simulated by hard-coded links inside every comment.

## Automated and manual gates

Dokka provides two mechanical controls:

- `reportUndocumented` reports visible declarations without KDoc/Javadoc;
- `failOnWarning` turns Dokka warnings, including missing documentation when reporting is enabled,
  into build failures.

Run a non-blocking inventory for all published modules:

```bash
./gradlew auditViewComposeApiDocs
```

Limit local iteration to selected artifacts:

```bash
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime,viewcompose-ui-contract
```

The normal site build does not report all existing omissions. A strict local check is available
for a module after its baseline has been repaired:

```bash
./gradlew assembleViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime \
  -PviewComposeApiDocsReportUndocumented=true \
  -PviewComposeApiDocsFailOnWarning=true
```

Mechanical coverage cannot decide whether a comment is Q1, Q2, or Q3. Reviewers apply the contract
matrix, verify samples, and reject comments that only restate names or signatures.

## Rollout and enforcement

The quality gate advances without hiding current debt:

1. **Inventory:** `auditViewComposeApiDocs` is non-blocking and establishes the per-module backlog.
2. **Core baseline:** repair `viewcompose-runtime`, `viewcompose-ui-contract`,
   `viewcompose-widget-core`, `viewcompose-renderer`, and `viewcompose-host-android` in dependency
   order.
3. **No regression:** after a module reaches its baseline, strict Dokka checking becomes required
   for that module and every new public API must be at least Q2.
4. **Published catalog:** expand strict checking family by family until all published artifacts are
   covered.

Do not enable repository-wide `failOnWarning` before existing warnings are classified and repaired.
Do not maintain a permanent allowlist of undocumented symbols. A temporary exception names an
owner, reason, and removal milestone in the active API documentation plan.

## Review checklist

- The declaration has the correct Q level for its risk.
- The first paragraph explains purpose or observable result.
- All applicable contract fields are present and match tests or implementation.
- Parameter, property, return, exception, and deprecation tags resolve and add useful information.
- Stateful, asynchronous, callback, Android, and resource-owning behavior is explicit.
- Q3 samples compile and demonstrate public usage.
- Links resolve and do not point to a mutable implementation branch for released behavior.
- The comment is canonical English and does not duplicate a complete translation.
- The generated page was inspected when formatting or symbol relationships changed.
