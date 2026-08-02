# Source Documentation and API Comment Standard

## Purpose

This document defines the normative source-documentation style for ViewCompose. It covers public
KDoc/Javadoc, protected extension points, and durable implementation comments. It converts the
general API documentation contract in
[Documentation Governance](documentation-governance.md#kdoc-and-javadoc-contract) into a
repeatable authoring, review, and automation standard.

The goal is not to maximize comment volume. The goal is to let a consumer use an API correctly
without reading its implementation, guessing its lifecycle, or discovering failure behavior in
production.

## Normative basis and precedence

ViewCompose follows the documentation practices used by Kotlin libraries, AndroidX, Jetpack
Compose, and Android framework APIs, with project-specific rules for stateful declarative UI:

- [Kotlin KDoc syntax](https://kotlinlang.org/docs/kotlin-doc.html) defines supported syntax and
  tags.
- [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments)
  define general library documentation style.
- [AndroidX KDoc guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/docs/kdoc_guidelines.md)
  define Kotlin-library expectations such as documenting every public parameter/property and
  using compiled `@sample` functions.
- [Compose component API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md#documentation-for-the-component)
  define the summary, behavioral detail, sample, and parameter ordering expected for declarative
  UI components.
- [Android API guidelines](https://android.googlesource.com/platform/developers/docs/+/master/api-guidelines/index.md)
  require implemented, tested, and documented APIs and consumer-visible parameter, result, and
  failure contracts.

When these sources differ, this document wins for ViewCompose. In particular, the general Kotlin
convention allows concise prose without `@param`/`@return`, while AndroidX optimizes published
reference pages for complete parameter/property tables. ViewCompose adopts the AndroidX library
rule: every public API element is documented explicitly, without repeating information that the
signature already expresses.

The requirement terms **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are normative.
This baseline was last reviewed against the upstream documents on 2026-08-02.

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

Private and internal declarations do not need coverage comments. They do follow the
[implementation comment rules](#implementation-comments) when a non-obvious invariant,
workaround, algorithm, concurrency rule, or performance decision would otherwise be lost.

## Documentation definition of done

Documentation is part of implementation, not follow-up cleanup. A change is incomplete unless the
same pull request:

1. documents every new published declaration and every changed public/protected contract;
2. updates comments affected by renamed parameters, changed defaults, state ownership, lifecycle,
   threading, failure behavior, performance, or Android interoperability;
3. adds or updates a compiled sample for Q3 APIs;
4. updates the owning module manual and migration/release material required by the
   [change impact matrix](documentation-governance.md#change-impact-matrix);
5. runs the owning module's documentation audit and relevant behavioral tests.

Existing untouched documentation debt may be repaired incrementally according to the active plan.
It is never a reason to merge new debt. Placeholder KDoc, `TODO` documentation, copied signature
text, or “document later” follow-ups do not satisfy this definition.

## Canonical language

Public API comments and durable implementation comments use English as their canonical language.
Do not maintain complete English and Chinese copies in the same KDoc block. Existing bilingual
blocks MUST be converted to one accurate English contract whenever their declaration is changed or
its API family is reviewed; do not add another parallel paragraph.

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

- The first sentence MUST define the abstraction in consumer terms, not say only “Represents” or
  restate the type name.
- Explain the abstraction's role, owner, lifetime, and important non-goals.
- Document every type parameter with `@param`, every promoted primary-constructor property with
  `@property`, and every other public constructor parameter with `@param`, in declaration order.
- Use `@constructor` only when construction has behavior, validation, ownership, or side effects
  not already explained by the type and parameter contracts.
- State whether callers construct the type directly or use a factory, builder, or `remember` API.
- Interfaces and abstract classes MUST define implementation obligations, callback ordering,
  lifecycle, threading, and allowed default behavior.
- Sealed hierarchies MUST explain whether consumers should exhaustively handle known subtypes and
  whether new subtypes may appear in compatible releases.

### Functions and properties

- Start action functions with an active present-tense verb such as “Creates”, “Updates”, “Applies”,
  “Registers”, or “Disposes”. Start queries with “Returns”. Do not begin with “This function”.
- Document every type parameter, extension receiver, and value parameter. Parameter descriptions
  MUST explain semantic meaning, units, coordinate space, range, defaults or sentinel values when
  applicable; they MUST NOT merely repeat the type or parameter name.
- Every non-`Unit` function MUST document its result with `@return`, except a self-evident property
  accessor that is not independently documented by Dokka. Describe ownership, identity, mutability,
  caching, snapshot/live behavior, null/sentinel meaning, and failure where applicable.
- Mutable properties MUST state who may write them, how changes are observed, and any thread or
  lifecycle restriction. Read-only properties MUST still state whether their value is live,
  snapshotted, cached, or derived when that distinction matters.
- Boolean properties describe what `true` enables or indicates; avoid summaries that only repeat
  the `is...` name.
- Overloads MUST document their semantic difference. Do not copy identical prose if default
  arguments, coercion, allocation, or failure behavior differs.

### Constants, enum entries, annotations, and type aliases

- Constants MUST define units, format, valid use, and special/sentinel meaning. Do not repeat only
  the constant name or literal value.
- Every public enum entry or sealed subtype MUST have an unambiguous semantic meaning. The owning
  type MAY document entries as one mapping when each name is self-evident and Dokka renders the
  mapping next to the entries.
- Annotations MUST explain their behavioral effect, valid targets, retention implications, whether
  tooling or runtime consumes them, and what happens when they are absent.
- Type aliases MUST state that they are source-level aliases rather than distinct runtime types and
  explain the consumer-facing naming or migration purpose.
- Default values MUST be documented by meaning when they select policy or behavior; do not merely
  copy the literal already visible in the signature.

### Suspend functions, flows, and callbacks

- State when execution starts and which dispatcher or thread constraint applies.
- Describe cancellation propagation and whether cancellation leaves partial effects.
- For `Flow`, state cold/hot behavior, replay, completion, error propagation, and collection
  lifecycle where applicable.
- For callbacks, state invocation timing, ordering, multiplicity, thread, and reentrancy.

### Overrides and inherited documentation

- An override with an unchanged inherited contract SHOULD omit duplicate KDoc.
- An override that narrows behavior, changes scheduling, adds side effects, changes failure
  handling, or defines Android-specific behavior MUST document that delta and link to the base
  contract.
- Never copy inherited prose and silently change one sentence; describe only the supported delta
  unless the generated reference cannot expose the inherited contract correctly.
- A protected override point MUST document what subclasses may call, when `super` is required, and
  which invariants implementations must preserve.

### Deprecated and experimental APIs

- `@Deprecated` declarations name a usable replacement and link it when possible.
- Explain any semantic difference that prevents mechanical replacement.
- Experimental APIs state the unstable contract and required opt-in annotation.
- Removal timelines belong in migration or release documentation, linked from the API comment.

## KDoc and Javadoc form

### Structure and voice

- Put a concise, standalone summary in the first paragraph; Dokka uses it in indexes and search.
  Prefer one sentence.
- Write from the API consumer's perspective in active present tense. Describe observable behavior,
  not the implementation sequence.
- Use a blank line between the summary, detailed paragraphs, examples, and block tags.
- Use paragraphs and short Markdown lists for KDoc. Do not encode structure with manual spacing,
  decorative separators, or ASCII tables.
- Keep one source of truth: link to shared contracts instead of copying long text between overloads
  or related types.
- Use canonical Android/Kotlin terminology and preserve exact casing for `View`, `ViewGroup`,
  `VNode`, `NodeSpec`, `Modifier`, `Flow`, lifecycle names, and API identifiers.

### KDoc tags and ordering

Use supported KDoc tags in this order when they apply:

1. `@sample` for compiled examples after the behavioral prose;
2. `@param T` for type parameters in declaration order;
3. `@receiver` for an extension receiver;
4. `@property` and `@param` for constructor elements, or `@param` for function parameters, in
   declaration order;
5. `@return`;
6. `@throws` ordered by the point at which the caller can encounter each failure;
7. `@see` with a class-qualified symbol;
8. `@since` only when the first released artifact version is known; never use `Unreleased`, a Git
   branch, or an aggregate repository version.

Every tag name MUST resolve to the declaration. KDoc type parameters use `@param T`, never
Javadoc-style `@param <T>`. Do not use Javadoc-only `@deprecated`; use Kotlin's `@Deprecated` with a
useful message and `ReplaceWith` where replacement can be expressed safely. `@suppress` MUST NOT be
used to hide an accidental public API from the generated reference; fix the visibility or API
boundary.

One-line tag descriptions may be sentence fragments without a period. Multi-sentence descriptions
use normal sentence punctuation. All descriptions within one declaration SHOULD use the same
style.

### Links, literals, and examples

- Link related symbols with KDoc links such as `[RenderSession]`; never create a self-link.
- Use `[label][Qualified.symbol]` when a qualified target improves resolution. A standalone `@see`
  target includes its class name.
- Do not put a Markdown link inside `@see`; use prose with a Markdown link or a plain symbol target.
- Use backticks for literals, Gradle coordinates, parameter names in prose when a link is not
  useful, and values such as `null`, `true`, or `0`.
- External links MUST target authoritative, stable documentation. Released behavior MUST NOT rely
  on a mutable source-code link to `main`.
- Avoid inline examples that can drift. Non-trivial usage belongs in a compiled `@sample`.

### Javadoc-specific form

Java public APIs follow the same contract content but use Javadoc syntax. Use `{@link Type}` and
`{@code value}`; use `<p>`, `<ul>`, `<ol>`, and `<pre>{@code ...}</pre>` for rendered structure.
Do not copy KDoc Markdown syntax into Javadoc or HTML formatting into KDoc.

### Prohibited content

- comments that only say “Represents”, “Handles”, “Gets”, “Sets”, or “Callback for” without a
  consumer-visible contract;
- bilingual duplicate paragraphs, author biographies, change logs, issue history, or marketing;
- implementation narration unless the technique is a supported performance/interoperability
  contract;
- undocumented abbreviations, ambiguous pronouns, or phrases such as “normally”, “etc.”, “safe”,
  and “thread-safe” without defining the exact boundary;
- promises that tests and implementation do not protect;
- commented-out code, stale debug notes, and documentation placeholders.

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

## ViewCompose-specific contracts

### DSL components and layout containers

Public `UiTreeBuilder` DSL functions follow the Compose component documentation order:

1. one-sentence purpose and the native UI role;
2. capabilities and observable behavior;
3. controlled/uncontrolled state ownership and event ordering;
4. measurement, placement, clipping, scrolling, and child/content-slot expectations;
5. how `Modifier`, `Environment`, theme tokens, density, layout direction, and accessibility are
   consumed or propagated;
6. Android `View` mapping only when it is a stable interoperability contract;
7. at least one compiled sample for a standalone component or non-trivial state;
8. every parameter and content receiver.

Do not claim Compose parity merely because an API has a similar name. Document semantic
differences or link to the maintained comparison/migration page.

### Modifiers and parent data

Modifier documentation MUST state:

- which phase it affects: construction, measurement, layout, drawing, input, semantics, or
  platform binding;
- whether order in the chain changes behavior;
- the coordinate space and units of numeric values;
- no-op, coercion, conflict, and parent-scope conditions;
- state, allocation, invalidation, and performance consequences that affect API choice.

Scope-specific parent data MUST identify the consuming parent and behavior outside that scope.

### State, environment, and effects

State-related APIs MUST identify the owner, observation boundary, equality policy, invalidation
behavior, retention, restoration, and disposal. `remember`-style APIs also document key comparison
and when values are recreated. Environment/local APIs document default resolution, nesting,
snapshot behavior, and which descendants observe changes.

Effects and asynchronous APIs document start/commit/dispose order, cancellation, rollback or
partial effects, callback thread, and behavior when composition or the Android host is destroyed.

### Android interop and native resources

Interop APIs MUST document main-thread requirements, native `View` ownership/reuse, attach/detach
behavior, lifecycle/SavedState owners, configuration changes, theme/resource lookup, minimum API
level, and fallback behavior where applicable. A mapping to a specific native class is documented
only if consumers may rely on that mapping as a compatibility contract.

## Authoring templates

Use the smallest template that fully describes the contract. Remove non-applicable paragraphs;
never leave placeholder headings.

### Stateful DSL component

```kotlin
/**
 * Displays one selectable destination and reports user requests through [onSelected].
 *
 * Selection is controlled by [selected]. The component does not mutate caller state; invoke a
 * state update from [onSelected] to reflect the new selection. The callback runs on the Android
 * main thread after the click is accepted and before the next render pass.
 *
 * [modifier] is applied to the component's root node. The component resolves colors and shape
 * tokens from the current [Environment] during rendering.
 *
 * @sample com.viewcompose.samples.navigationDestination
 * @param selected whether this destination is rendered as selected
 * @param onSelected callback invoked for an accepted user selection request
 * @param modifier modifiers applied to the root node in chain order
 * @param content content rendered inside the destination
 */
fun UiTreeBuilder.NavigationDestination(
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
)
```

### Stateful resource owner

```kotlin
/**
 * Creates a render session that owns the native view tree until [RenderSession.dispose].
 *
 * Calls are confined to the Android main thread. Disposing the session detaches observations and
 * releases host references; subsequent render attempts throw [IllegalStateException].
 *
 * @param host Android host that owns the rendered view hierarchy
 * @return a new session with no rendered root
 * @throws IllegalStateException if [host] is already bound to an active session
 */
fun createRenderSession(host: RenderHost): RenderSession
```

Templates demonstrate form, not contracts to copy. Replace every statement with verified behavior
from the owning implementation and tests.

## Implementation comments

Implementation comments preserve reasoning that code cannot express. They SHOULD explain:

- why an Android platform workaround or unusual ordering is required;
- invariants spanning multiple functions or modules;
- concurrency, ownership, rollback, and lifecycle assumptions;
- algorithmic or allocation trade-offs and the threshold behind them;
- why an apparently simpler implementation is incorrect.

Use `//` for a local reason and a short block comment for an algorithm or invariant applying to the
following region. Do not narrate statements line by line. Prefer a named function/type and a test
over a comment when the concept can be expressed structurally.

A `TODO` MUST include a tracked issue URL or number, the concrete missing outcome, and the condition
for removal. It MUST NOT be used as public API documentation. Delete obsolete comments in the same
change that makes them false.

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

Run the repository-wide strict audit for all published modules:

```bash
./gradlew auditViewComposeApiDocs
```

Limit local iteration to selected artifacts:

```bash
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime,viewcompose-ui-contract
```

All published modules are strict. Use the selected verifier for a faster local check while retaining
undocumented-symbol, warning, route, and immutable-source-link enforcement:

```bash
./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime
```

Mechanical coverage cannot decide whether a comment is Q1, Q2, or Q3. Reviewers apply the contract
matrix, verify samples, and reject comments that only restate names or signatures.

## Enforcement

The initial rollout is complete. Enforcement is now permanent:

1. every new or changed public/protected declaration follows this standard in the same pull request;
2. every published module remains in `apiDocs.strictModules`; publication verification rejects a
   module that is absent from the strict set;
3. `auditViewComposeApiDocs` runs repository-wide with `reportUndocumented` and `failOnWarning`;
4. selected local generation remains strict and also verifies aliases and immutable source links;
5. production documentation uses `verifyCompleteViewComposeApiDocs`, which rejects a partial module
   selection or incomplete catalog;
6. no permanent undocumented-symbol allowlist is permitted. A temporary exception must name an
   owner, reason, and removal milestone in an active plan.

## Review checklist

- Documentation was authored with the API change, not deferred to a cleanup task.
- The declaration has the correct Q level for its risk.
- The first paragraph explains purpose or observable result.
- All applicable contract fields are present and match tests or implementation.
- Every public type parameter, receiver, property, parameter, and non-`Unit` result is documented in
  the required order; exception and deprecation guidance is complete.
- Stateful, asynchronous, callback, Android, and resource-owning behavior is explicit.
- DSL components document state ownership, layout/content behavior, Modifier/environment usage,
  accessibility, and stable native mapping where applicable.
- Q3 samples compile and demonstrate public usage.
- Links resolve and do not point to a mutable implementation branch for released behavior.
- The comment is canonical English and does not duplicate a complete translation.
- Implementation comments explain reasons or invariants and contain no stale narration/TODOs.
- The generated page was inspected when formatting or symbol relationships changed.
