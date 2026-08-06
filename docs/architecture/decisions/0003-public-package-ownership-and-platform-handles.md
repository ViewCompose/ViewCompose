# ADR-0003: Public Package Ownership and Platform Handles

## Status and date

Accepted and implemented — 2026-08-06.

## Context

The five-layer hard cut renamed responsibility-misaligned Maven artifacts, but an implementation
audit found that several Kotlin package roots still expressed the retired module topology:
`com.viewcompose.widget.core`, `com.viewcompose.widget.constraintlayout`, and the generic
`com.viewcompose.overlay.android`. The new application aggregate also contributed APIs to
`com.viewcompose.host.android`, so two independently published artifacts owned one public package.

UI Foundation additionally coordinated composition through Android `ViewGroup`, `Log`, `Trace`,
and a concrete focus manager. That did not create an upward Gradle dependency, but it made Android
execution ownership invisible and contradicted the intended UI Foundation/Android Engine boundary.
ViewCompose still targets Android View; this decision is about explicit ownership, not
multiplatform support.

## Decision

1. Every published artifact owns one canonical public package root. Two artifacts may use nested
   domain roots, but they must not own the same exact root or contribute source to each other's root.
2. UI Foundation owns `com.viewcompose.ui.foundation`. The retired `widget.core` package receives
   no source or compatibility facade.
3. `viewcompose-android` owns `com.viewcompose.android`; `viewcompose-host-android` exclusively owns
   `com.viewcompose.host.android`. Standard `setUiContent` and low-level `renderInto` therefore have
   visibly different import boundaries.
4. ConstraintLayout owns `com.viewcompose.constraintlayout`. Maven keeps the `-androidx` suffix to
   describe its backend without encoding the retired widget taxonomy in source.
5. The Material-backed overlay owns `com.viewcompose.overlay.material3.android`, keeping the design
   system visible in both its artifact and package identity.
6. Maven artifact names describe capability plus distribution/backend. Kotlin packages describe
   the stable API domain; backend suffixes are not copied mechanically when they do not distinguish
   public semantics.
7. UI Foundation may contain Android-only declarative values because ViewCompose targets Android,
   but it coordinates sessions only through `RenderContainerHandle`, engine, focus, scheduling,
   logging, and tracing contracts. Host Android installs and implements those contracts and is the
   only layer that unwraps root handles as `ViewGroup`.
8. Android environment extraction and optional Android overlay service discovery belong to Host
   Android. UI Foundation consumes resolved `UiEnvironmentValues` and retains only the generic
   overlay protocol and no-op implementation.
9. Android namespaces equal their canonical package roots. Completed architecture migrations keep
   no permanent namespace override.

## Alternatives considered

- **Mirror every Maven suffix in every Kotlin package.** Rejected because it would add low-value
  source churn to stable capability packages such as lifecycle and viewmodel without clarifying
  public semantics.
- **Keep legacy packages for source compatibility.** Rejected because the replacement artifacts
  are still before their first release and the authorized hard cut explicitly excludes forwarding
  facades.
- **Move the entire composition coordinator into Host Android.** Rejected because lazy child and
  overlay sessions need the same composition machinery without introducing a forbidden UI
  Foundation-to-Host dependency. Opaque platform handles preserve dependency inversion while Host
  Android retains native execution ownership.
- **Forbid every `android.*` type in UI Foundation.** Rejected because the framework has no
  multiplatform goal and some declarative values are intentionally Android-specific. The enforced
  boundary targets execution and adaptation types instead.

## Consequences

- The package migration is source-breaking but happens before the replacement artifacts' first
  Maven release.
- Imports now reveal whether a caller uses the application aggregate, low-level host, generic UI
  surface, AndroidX ConstraintLayout integration, or Material 3 overlay backend.
- Custom platform installers must provide focus and diagnostics adapters in addition to the render
  engine, coroutine context, and scheduling runtime.
- UI Foundation no longer imports Android Context, View/ViewGroup, Log, Trace, or LocaleList in
  production source; narrow Android-only declaration types remain permitted.
- Service descriptors use the Host Android provider contract, so optional platform discovery no
  longer expands UI Foundation's API responsibility.

## Affected modules and public contracts

- `viewcompose-ui-foundation`
- `viewcompose-host-android`
- `viewcompose-android`
- `viewcompose-constraintlayout-androidx`
- `viewcompose-overlay-material3-android`
- downstream integrations, samples, preview hosts, and compiled API documentation importing those
  public roots

## Validation and rollout

- `verifyModulePackageRoots` rejects legacy roots, prefix-boundary errors, duplicate owners,
  declarations claimed by a different longest registered package prefix, and legacy service
  descriptors.
- `verifyAndroidModuleNamespaces` requires exact namespace/root equality with no override map.
- `verifyUiFoundationPlatformBoundary` rejects Android execution and adaptation imports from UI
  Foundation.
- Focused session, host, overlay, navigation, aggregate, and ConstraintLayout tests run before the
  complete quick, release, publication, and documentation gates.
- The normal release workflow archives the completed hard-cut plan before any affected replacement
  artifact enters Maven staging.

## Relationship to earlier decisions

This record refines, and does not supersede,
[ADR-0002](./0002-five-layer-runtime-module-architecture.md). ADR-0002 defines the five runtime
responsibilities; this record defines how public packages and platform execution contracts make
those responsibilities observable and enforceable.
