# Demo Benchmark and Verification Harness Rearchitecture Plan

## Status

Active. Phase 0 inventory and workload freeze, Phase 1 contract and automation spine, Phase 2 host
and catalog hard cut, Phase 3 localization and content hard cut, and Phase 4 scenario migration are
complete. Phase 5 benchmark rebaseline is device-blocked; release, renderer diagnostics, list revision
3, complex-layout revision 3, diagnostics-theme revision 2, and collection-stress revision 2
replacement baselines are accepted. Shadow-list and shadow-complex-layout revision 2 replacement
baselines are also accepted.

The Demo is being redefined as a deterministic benchmark and framework-verification harness.
Automated validation owns the primary information architecture. Human verification remains a
supported secondary workflow, but explanatory copy must not displace the fixture, become a
selector contract, or enter a measured benchmark hierarchy.

Last verified: 2026-08-15.

Next action: run the Phase 5 navigation-motion revision 6 and design-bundle revision 3 matrices on
a rootable or otherwise clock-controllable reference device. The current Samsung consumer device
cannot produce valid run stability for these workloads.

Performance work from the
[archived Runtime data propagation and Android View patch optimization record](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/runtime-data-propagation-and-view-patch-optimization.md)
is gated by the replacement baselines for the scenarios that can exercise that optimization, not
by unrelated navigation or design-system matrices. The accepted release state-patch, renderer,
list, and complex-layout baselines satisfy that scoped gate for the two retained Runtime/Patch
experiments. Navigation revision 6 and design-bundle revision 3 remain blocked on a
clock-controllable device without delaying unrelated framework work.

## Maven release changesets

- None.

The planned work is initially confined to the Demo application, its test APK, and the internal
Macrobenchmark module. If implementation proves that a published framework API must change, that
slice must receive its own API-quality assessment, owning-module documentation, compiled samples,
and immutable `release/changes/*.json` entry before it is considered part of this plan.

## Objective

Replace the current module-oriented showcase with a long-lived verification harness that provides:

1. one stable, directly launchable route for every independently testable scenario;
2. deterministic setup, ready, action, state, reset, and result targets that do not depend on
   visible text or locale;
3. fixture-first pages whose measured content is isolated from catalog, guidance, diagnostics, and
   other unrelated UI;
4. complete English and Simplified Chinese resources for all visible Demo copy;
5. compact human instructions that are available on demand but are not mounted in benchmark mode;
6. an explicit workload revision for every performance baseline so historical measurements are
   not compared across changed fixtures; and
7. one maintainable registry connecting navigation, automation, manual verification, screenshots,
   and benchmarks without presenting the project roadmap or module architecture inside the app.

The end state is not a documentation browser or a component marketing catalog. Project plans,
future modules, architecture inventories, and known gaps remain in repository documentation.

## Product position and priority

The Demo has two consumers in this strict order:

| Priority | Consumer | Required outcome |
| --- | --- | --- |
| 1 | Instrumentation and Macrobenchmark | Direct launch, stable machine targets, deterministic state, isolated workload, and bounded navigation cost |
| 2 | Framework developers reproducing a defect | Fast search, one-tap launch, visible state/result, reset, and environment controls |
| 3 | Human visual review | Localized goal, steps, and expected result presented outside the primary fixture |

This ordering has concrete consequences:

- automation never navigates by translated copy;
- a scenario is not hidden behind a chapter tab or a long document;
- the primary fixture and its first action/result appear before explanatory prose;
- benchmark mode does not mount human guidance or the general Demo shell;
- a page can still be visually polished, but polish cannot make the workload ambiguous; and
- a feature that needs a special Activity, lifecycle, window, or configuration owns that host
  explicitly instead of inheriting one universal shell.

## Audit method and reproducible baseline

The 2026-08-14 audit combined source inspection, unit/instrumentation/Macrobenchmark inspection,
Android accessibility layout dumps, and screenshots from the current Debug APK.

Running-device baseline:

| Property | Value |
| --- | --- |
| Device | Samsung SM-G991B |
| Android | 13 / API 33 |
| Display | 1080 x 2400, 480 dpi |
| Font scale | 1.0 |
| Locale | `zh-Hans-CN` |
| Build | Current branch Debug APK built on 2026-08-14 |

The capture workflow was:

```bash
./gradlew :app:assembleDebug
android run \
  --device=<device-serial> \
  --apks=app/build/outputs/apk/debug/app-debug.apk \
  --activity=com.viewcompose.MainActivity
android layout --device=<device-serial> -p -o=<layout-output>.json
android screen capture -o=<screenshot-output>.png
```

Screenshots are audit evidence, not a normative UI specification, and are intentionally not stored
as permanent plan assets. The source, scenario contract, executable tests, and future screenshot
baselines remain authoritative.

## Current baseline

### Quantitative source baseline

| Measure | Current value | Interpretation |
| --- | ---: | --- |
| Demo Kotlin files | 52 | Shared infrastructure and feature pages are already a substantial application subsystem. |
| Demo page Kotlin files | 42 | Page ownership is fragmented across broad chapters and partial section files. |
| Demo-related Activity files | 24 | Ordinary fixtures and lifecycle-specific fixtures are not clearly distinguished. |
| Available catalog modules | 18 | The catalog models coarse framework modules rather than independently executable scenarios. |
| App instrumentation test files | 18 | Significant device evidence exists and must be migrated rather than discarded. |
| Macrobenchmark Kotlin files | 11 | Benchmark entry and interaction helpers are already a first-class consumer of the Demo. |
| `DemoTestTags` constants | 385 | Stable in-process identifiers exist, but are manually centralized and disconnected from scenario ownership. |
| Production `testTag` applications | 319 | Many fixtures already expose test hooks. |
| Direct `text = "..."` assignments in Demo/activity/performance source | 660 | This is a lower bound; titles, labels, model fields, and formatted prose add more hard-coded visible copy. |
| App string resources | 1 | Only `app_name` exists in the default `strings.xml`; no second locale resource file exists. |
| Macrobenchmark visible-text helper calls | 136 | Waiting, scrolling, clicking, and state assertions remain coupled to English or Chinese copy. |

`Modifier.testTag` currently reaches Android through the named View tag
`R.id.viewcompose_test_tag`. That is appropriate for in-process test helpers, but it is not exposed
as an Android resource ID in the UiAutomator layout tree. Current Macrobenchmarks therefore fall
back to `By.text(...)` and text-based wrapper functions. Internationalizing the Demo without first
replacing that black-box selector contract would make the benchmark suite unstable by design.

### Captured screen audit

| Screen | Current observation | Required direction |
| --- | --- | --- |
| Home catalog | The first viewport is dominated by catalog rationale, planned chapter prose, manual focus, and benchmark route descriptions. The first actionable module card is only partially visible. | Compact executable scenario rows; search/filter and launch are primary. No roadmap or route prose. |
| Diagnostics / Runtime | A repeated chapter overview, framework-module list, and page-switcher occupy most of the first viewport before the benchmark fixture. | Each diagnostic concern becomes a direct scenario; no repeated chapter preamble. |
| Diagnostics / Theme | Switching the tab rebuilds a long document while the same preamble remains visible. The actual theme fixture begins near the bottom edge. | Direct theme scenario route with snapshot/actions/result first. |
| Diagnostics / Renderer | The renderer controls are preceded by unrelated module and chapter content. | Direct renderer scenario with refresh/reset/result machine targets in the first viewport. |
| Diagnostics / Gaps | The page explicitly presents `roadmap gaps` and says that gaps guide future framework work. | Remove the page. Plans and roadmap own future work. |
| Settings | Theme, resource, Material 3, custom-token, and multi-design-system verification are mixed into one long settings document. | Separate global environment controls from independently launchable verification scenarios. |
| About | Hard-coded module names, DSL counts, Modifier counts, Defaults counts, NodeType counts, and placeholder version/link values can become stale without a code failure. | Remove architecture/statistics content. Show generated build identity only when useful to reproduce a run. |
| State | The benchmark controls are reachable, but route prose and a stable-target checklist consume most of the first screen; the actual state/effect fixture starts below it. | Keep action, reset, state, and result first; route metadata belongs to the scenario contract, not visible UI. |
| Widget showcase | The catalog is relatively compact, but each entry is still a human-oriented description and automation must select a visible component name. | Remove the nested chooser; assign each detail to its existing owning fixture and create direct routes only for uncovered component families. |
| Performance comparison / List | The dedicated Activity already has a compact ready marker, mutation/reset actions, and the measured list in the first viewport, with no general Demo shell. | Preserve this isolation pattern and add locale-independent machine targets plus workload revision. |

### Structural findings

1. **The catalog model has the wrong unit of identity.** `DemoModule` combines display title,
   subtitle, availability, manual focus, benchmark path, and Activity class. A module can contain
   several independent chapter tabs and fixtures, so its key cannot precisely identify the state or
   workload under test.
2. **Navigation and fixture identity are coupled.** The home uses a four-page
   `HorizontalPager`; many module Activities then use another page selector inside a long
   `LazyColumn`. Automation often has to navigate and scroll before it reaches the scenario.
3. **Visible prose is executable test infrastructure.** Macrobenchmark helpers use localized text
   for readiness, actions, state changes, tab selection, and scrolling. Copy editing can therefore
   invalidate performance tests without changing behavior.
4. **Human guidance is mounted as workload content.** `ScenarioSection`,
   `BenchmarkRouteCallout`, `ChapterPageOverviewSection`, `ChapterPageFilterSection`, and
   `VerificationNotesSection` place instructions, route data, module lists, and expected results in
   the same hierarchy as the fixture.
5. **Planning data has leaked into runtime UI.** Planned modules, roadmap gaps, module layering,
   capability counts, and placeholder links duplicate repository documentation and inevitably
   become stale.
6. **The existing tag registry is broad but not ownership-safe.** One 410-line object contains
   targets for unrelated scenarios. It does not encode roles, guarantee per-scenario completeness,
   or provide an external-process resource selector.
7. **Dedicated benchmark screens demonstrate a better boundary.**
   `PerformanceComparisonActivity` selects a strict engine/scenario contract, fails on unknown
   values, and mounts only the measured screen. This design should become the baseline for all
   measured fixtures, not remain an exception.

## Frozen scenario inventory

Phase 0 replaces the old module/page identity with the following immutable scenario IDs. The
legacy page index is recorded only to classify and migrate existing callers; it is not part of the
new launch contract.

| Legacy owner | Page or variant | Decision | Replacement scenario ID |
| --- | --- | --- | --- |
| Home | Catalog | Retain as the unmeasured launcher/catalog | `catalog` |
| Home | Diagnostics, Settings, About pager pages | Split diagnostics and environment controls; remove About | Direct scenarios below; no page IDs |
| Foundations | Guide, 0 | Remove architecture prose; retain the business-Local fixture | `foundations.locals` |
| Foundations | Theme, Media, Typography, 1-3 | Split | `foundations.theme`, `foundations.media`, `foundations.typography` |
| State | Core, Identity, Patch, 0-2 | Split | `runtime.state`, `runtime.key-identity`, `runtime.view-patch` |
| State | Checklist, 3 | Remove copied capability inventory | None |
| Layouts | Linear, Stack, Edges, Flow, Scroll, Constraint, 0-5 | Split | `layout.linear`, `layout.stack`, `layout.edges`, `layout.flow`, `layout.scroll`, `layout.constraint` |
| Layouts | Checklist, 6 | Remove copied capability inventory | None |
| Input | Fields, Selection, Stress, Search, Summary, 0-4 | Split | `input.fields`, `input.selection`, `input.stress`, `input.search`, `input.derived-summary` |
| Feedback | Transient, Dialog, Menu, 0-2 | Split; retain overlay-capable host | `overlay.transient`, `overlay.dialog`, `overlay.menu` |
| Collections | Controls, List, Stress, Interop, Row, Grid, Refresh, 0-6 | Split | `collection.controls`, `collection.lazy-list`, `collection.stress`, `collection.android-view`, `collection.lazy-row`, `collection.grid`, `collection.pull-refresh` |
| Interop | Android View and Local propagation | Retain | `interop.android-view` |
| Diagnostics | Runtime, Theme, Renderer, 0-2 | Split | `diagnostics.runtime`, `diagnostics.theme`, `diagnostics.renderer` |
| Diagnostics | Gaps, 3 | Remove roadmap content | None |
| Preview | Bridge, Overlay mock, Snapshot, 0-2 | Remove the on-device proxies; retain the actual debug bridge entrypoints, PreviewCatalog overlay spec, and `qaPreview` snapshot gate | None |
| Actions | Card, FAB, Chip, List item, 0-3 | Split | `component.card`, `component.fab`, `component.chip`, `component.list-item` |
| Modifiers | Visual, Sizing, Accessibility/native View, 0-2 | Split | `modifier.visual`, `modifier.sizing`, `modifier.accessibility` |
| Gestures | Tap, Drag/swipe, Transform, 0-2 | Split | `gesture.tap`, `gesture.drag-swipe`, `gesture.transform` |
| Animation | Core, Content, List motion, Specs, Transition, Infinite, 0-5 | Split | `animation.core`, `animation.content`, `animation.list-motion`, `animation.specs`, `animation.transition`, `animation.infinite` |
| Graphics | Drawing, Outer shadow, Inner shadow, Lazy/diagnostics, 0-3 | Split | `graphics.drawing`, `graphics.outer-shadow`, `graphics.inner-shadow`, `graphics.shadow-list` |
| Navigation components | App bars, Navigation bar, Scaffold, 0-2 | Split | `component.app-bars`, `component.navigation-bar`, `component.scaffold` |
| System navigation | NavHost, stacks, deep links, predictive Back, adaptive panes | Retain dedicated lifecycle host | `navigation.system` |
| Theme switch | Cross-Activity propagation | Retain dedicated host | `environment.cross-activity-theme` |
| Resource configuration | Locale, night, direction, font scale, density | Retain dedicated configuration host | `environment.resources` |
| Material 3 verification | Android XML, static baseline, custom tokens | Split by declared source | `design.material3-xml`, `design.material3-static`, `design.material3-custom` |
| Multi-design-system verification | Material 3 and contrast bundles | Split by bundle | `design.bundle-material3`, `design.bundle-contrast` |
| One UI 7 verification | Five-component slice | Retain dedicated design-system host | `design.oneui7` |
| Widget showcase | 20 legacy widget keys | Remove the chooser and deduplicate by owning fixture; retain only uncovered component families | `component.button`, `component.icon-button`, `component.segmented-control`, `component.divider`, `component.progress` |
| Performance comparison | List, complex layout, shadow list, shadow complex layout | Retain isolated host; engine and shadow policy remain workload dimensions | `performance.list`, `performance.complex-layout`, `performance.shadow-list`, `performance.shadow-complex-layout` |

The original 20-key widget list was corrected during implementation after fixture ownership was
available. Text and Image belong to `foundations.typography` and `foundations.media`; text fields,
selection controls, and SearchBar belong to `input.*`; Chip, FAB, Badge, ListItem, and Card belong
to the existing action-component fixtures. The six uncovered legacy details become five strict
component scenarios because linear and circular progress are two presentations of one progress
state contract. The retired chooser and all duplicate detail pages have no compatibility route.

### Existing automation ownership

| Existing owner | Frozen disposition |
| --- | --- |
| App instrumentation using `DemoTestTags` | Retain behavior assertions; migrate launch and scenario-level targets to the owning scenario contract. Fine-grained in-process fixture tags may remain beside the fixture. |
| `DemoInteractionBenchmark` | Split into direct scenario workloads; remove catalog and chapter-tab navigation. |
| `ReleaseBaselineBenchmark` | Retain startup and `runtime.view-patch`; replace text synchronization with role targets. |
| Diagnostics long-fling benchmark | Retain as `diagnostics.theme`; direct launch makes the old tab-switch prelude non-comparable. |
| List and complex-layout comparison benchmarks | Retain paired engines and data shape under explicit workload revisions. |
| Navigation motion benchmarks | Retain `navigation.system` and its dedicated Activity boundary. |
| Design-system vertical slice | Split by `design.bundle-*`; keep bundle kind as declared scenario identity rather than visible copy. |
| Shadow comparison benchmarks | Retain the performance scenario plus engine/backend dimensions. |
| Screenshot tests and preview snapshots | Retain visual assertions; launch the direct scenario or preview fixture without catalog copy. |

### Frozen workload revisions

These revisions describe the pre-migration workloads. A direct-route or hierarchy change that
alters measured work advances the corresponding revision and establishes a new baseline instead of
claiming a performance delta against this table.

| Scenario/workload | Frozen revision | Invalidating dimensions |
| --- | ---: | --- |
| `catalog` cold startup | 1 | Launcher shell, first-frame catalog hierarchy, startup data set |
| `runtime.view-patch` state patch | 1 | Patched node set, state fan-out, action sequence, host chrome |
| `diagnostics.theme` long fling | 1 | Full item tree, tab-switch prelude, fling bounds, host chrome |
| `interop.android-view` state patch | 1 | Native target, declarative mirror, host hierarchy, action/reset sequence |
| `collection.stress` mutation | 1 | Item count/order, key/content revisions, action sequence |
| `performance.list` | 1 | 1,000-row model, row tree/content shape, rotation/update rule, engine |
| `performance.complex-layout` | 1 | Dashboard-card model, nested tree, update rule, engine |
| `navigation.system` motion | 1 | Stack seed, destinations, transition duration, gesture/action sequence |
| `design.bundle-material3` and `design.bundle-contrast` | 2 | Component slice, state fan-out, overlay sequence, bundle |
| `performance.shadow-list` | 1 | Row model, shadow layers, engine, backend policy |
| `performance.shadow-complex-layout` | 1 | Dashboard model, shadow layers, engine, backend policy |

Phase 0 is complete: every current catalog module, page selector, dedicated host, instrumentation
owner, and Macrobenchmark owner now has a retained, split, or removed disposition. The migration is
allowed to change UI only through the scenario and workload contracts above.

## Locked design principles

### 1. Scenario, not module or page tab, is the unit of verification

A scenario has one stable business identity, one deterministic initial state, one launch contract,
and one independently assertable outcome. Categories are catalog filters only; they do not define
runtime ownership or benchmark identity.

Chapter tabs are removed unless tab behavior itself is the feature under test. Existing chapter
pages are split into direct scenarios instead of being retained behind an initial-page index.

### 2. The fixture is a reusable workload, not a whole screen document

Each scenario separates:

- the **fixture**, which owns the DSL and state being tested;
- the **host**, which supplies Activity/window/lifecycle/environment boundaries;
- the **automation contract**, which supplies machine targets and readiness;
- the **benchmark contract**, which supplies workload revision and measured actions; and
- the **human guide**, which supplies localized goal, steps, and expected result outside benchmark
  mode.

The same fixture may run in an interactive host and a benchmark host only when host differences do
not change its semantics. A lifecycle-, window-, overlay-, navigation-, resource-, or system-UI
scenario keeps a dedicated host.

### 3. Machine identity is locale-independent and role-based

Every scenario declares stable roles instead of ad hoc labels:

| Role | Required | Meaning |
| --- | --- | --- |
| `root` | Always | The mounted scenario identity and bounds. |
| `ready` | Always | Initial composition and deterministic setup are complete. |
| `primary_action` | Interactive scenarios | The canonical state transition. |
| `reset` | Mutable scenarios | Restores the declared initial state. |
| `state` | Stateful scenarios | Machine-readable current state/result. |
| `target` | Visual, input, or gesture scenarios | The principal View under verification. |
| `secondary_*` | Only when necessary | Additional actions or results explicitly named by the scenario. |

Names follow `demo.<scenario-id>.<role>` in the in-process bridge and
`demo_<scenario_id>_<role>` for Android resource IDs. IDs are immutable after a baseline is
published. Renaming display copy never renames a scenario or target.

### 4. Black-box automation uses Android resource IDs, not accessibility copy

The implementation creates an app-internal `DemoAutomationTarget` abstraction that applies both:

- the existing `testTag` for in-process instrumentation and diagnostics; and
- an app `R.id` through replay-safe `Modifier.nativeView` configuration for UiAutomator
  `By.res(...)` queries.

`contentDescription` remains localized accessibility content. It is never overloaded with an
automation protocol. The Demo phase does not add a public framework API merely to solve an
application-test selector problem.

The exact helper shape may vary, but one target declaration must be the source used by both test
bridges. A second hand-maintained 385-constant registry is not accepted.

### 5. Visible text is a resource; wire values are not display text

Default resources are canonical English and `values-zh-rCN` provides Simplified Chinese. Every
title, label, action, status, hint, content description, formatted count, and plural resolves
through `stringResource` or `pluralStringResource`.

Stable scenario IDs, Intent extra names, enum wire values, test tags, trace section names, and
synthetic data keys are deliberately not translated. Visible synthetic benchmark data uses shared
resource format strings so paired ViewCompose/Compose workloads remain semantically identical.

### 6. Benchmark workloads are revisioned and isolated

Every measured scenario declares a monotonically increasing `workloadRevision`. The revision
changes when row count, tree shape, content length class, action sequence, state fan-out, gesture,
host chrome, or measured environment changes. Copy translation alone does not change a revision if
selectors and workload shape remain equivalent; a material layout-length change requires fresh
locale-specific evidence.

Results with different scenario IDs or workload revisions are not longitudinally comparable. The
benchmark report records both values.

### 7. Project management never renders inside the Demo

The following content is removed and prohibited from returning:

- planned or future modules;
- roadmap gaps and phase status;
- manually copied module dependency/layer diagrams;
- handwritten counts of APIs, components, modifiers, or node types;
- benchmark route instructions intended for test authors; and
- placeholder versions or links.

Generated build variant, version, commit, host mode, locale, theme, font scale, density, and
workload revision may be shown in a compact reproduction panel because they describe the running
artifact rather than future work.

## Target architecture

### Scenario contract

The registry is the single source of executable Demo inventory. A representative internal model is:

```kotlin
internal data class DemoScenarioSpec(
    val id: DemoScenarioId,
    val category: DemoScenarioCategory,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val host: DemoHostPolicy,
    val targets: DemoAutomationContract,
    val benchmark: DemoBenchmarkContract?,
    val guide: DemoHumanGuide?,
    val fixture: DemoFixture,
)
```

This is an internal design direction, not a public API signature. The implementation may split
metadata from executable factories to avoid retaining Activity instances, Views, Contexts, or
mutable state in the registry. The following invariants are mandatory:

1. `DemoScenarioId` is immutable, non-localized, unique, and validated at startup/unit-test time.
2. The registry contains executable scenarios only; there is no `Planned` state.
3. Display resources are present in both supported locales.
4. Every scenario declares `root` and `ready`; mutable scenarios declare `reset`.
5. A benchmark contract cannot exist without an action sequence, target set, workload revision,
   and environment policy.
6. Factories do not capture an Activity, root View, Session, or previous scenario state.
7. Unknown route IDs and unsupported extras fail deterministically.

### Host policies

| Host | Use | Examples |
| --- | --- | --- |
| Shared fixture Activity | Ordinary state, layout, component, gesture, graphics, and collection scenarios | Counter, keyed reorder, modifier patch, component state |
| Dedicated Activity | Lifecycle, window, configuration, resource, system navigation, or external Android integration is part of the behavior | Resource configuration, cross-Activity theme, predictive Back |
| Overlay-capable fixture host | Overlay behavior is the scenario and must use the real host integration | Dialog, snackbar, bottom sheet |
| Benchmark fixture host | Release-like measured hierarchy with guidance and catalog disabled | State patch, list, complex layout, shadow comparison |
| Preview/snapshot host | Static preview or deterministic image capture without device navigation | Component visual baselines |

An ordinary scenario does not receive a new Activity merely for catalog organization. Conversely,
a shared Activity is not used when it would erase the Android lifecycle boundary being verified.

### Launch and route contract

`MainActivity` remains the stable launcher package entry used by Macrobenchmark, but its routing
unit changes from `EXTRA_DEMO_MODULE_KEY` to a strict scenario ID. Normal launch opens the catalog;
a scenario extra redirects directly to the correct host and finishes the launcher shell.

The hard cut is atomic:

1. inventory all existing test and benchmark routes;
2. assign their replacement scenario IDs;
3. migrate callers and readiness selectors in the same implementation series;
4. retain a temporary module-key adapter only while both sides are changed in one branch; and
5. remove the adapter and `DemoModuleStatus` before the plan completes.

No long-lived dual route system is permitted. Dedicated performance comparison engine/scenario
extras may remain where they express a real workload dimension, but they are registered under the
same baseline inventory.

### Catalog and global environment

The four-page bottom navigation and home `HorizontalPager` are removed. The root app has one
compact scenario catalog with:

- localized search;
- category and verification-kind filters;
- stable scenario ID, localized title, and small benchmark/manual/visual badges;
- one launch target per row; and
- toolbar access to environment controls and generated build information.

Recommended catalog categories are test domains, not Maven or roadmap modules:

1. Runtime and state;
2. Rendering and layout;
3. Collections and reuse;
4. Input and interaction;
5. Android integration;
6. Navigation and lifecycle;
7. Design systems and visual behavior; and
8. Performance comparisons.

Theme, locale, layout direction, font scale, density, reduced motion, and other supported global
controls live in an Environment panel. Verification slices such as Material 3 defaults,
multi-design-system switching, resource propagation, and One UI behavior remain direct scenarios,
not settings entries.

The About page is removed. A build-information panel may show values generated from the running
build and scenario launch, never handwritten framework inventories.

### Fixture-first page hierarchy

Interactive mode uses this order:

```text
localized title + stable scenario ID + optional guide action
ready/state strip + reset
primary fixture
primary action and observable result when not intrinsic to the fixture
optional secondary fixture controls
collapsed localized verification guide
optional reproduction/build facts
```

Benchmark mode uses:

```text
ready target + workload revision
measured controls required by the script
measured fixture
```

The guide contains goal, steps, and expected result in a consistent structure. It is collapsed by
default in interactive mode and absent from benchmark mode. Route strings, selector names, module
lists, and project gaps never appear in it.

For the 360 x 800 dp reference viewport at font scale 1.0, `ready`, the primary action, the primary
observable result, and the beginning of the target fixture must be visible without scrolling. At
font scale 1.3, the same targets must remain directly addressable without text-based search even if
the visual layout scrolls.

### Source ownership

The target package shape is:

```text
demo/
  contract/       scenario IDs, specs, host policies, workload revisions
  registry/       validated executable inventory
  automation/     target roles and app resource-ID bridge
  catalog/        search, filters, environment entry, build facts
  host/           shared, dedicated, benchmark, and preview hosts
  guidance/       localized optional guide presentation
  scenarios/
    runtime/
    rendering/
    collections/
    input/
    interop/
    navigation/
    designsystem/
    performance/
```

Files may be grouped further when a domain becomes large, but `core/` must not become another
catch-all containing unrelated test metadata and UI.

## Benchmark baseline model

The rearchitecture distinguishes three kinds of evidence:

| Evidence | Purpose | Shell policy |
| --- | --- | --- |
| Release baseline | Detect delivered-binary startup and state-patch regressions | Benchmark host only; strict scenario/revision |
| Comparative performance fixture | Compare ViewCompose and Compose under the same data and actions | Dedicated comparison host; paired resources and models |
| Interactive regression scenario | Reproduce behavior and collect device evidence | Shared/dedicated fixture host; optional guide outside measured interval |

Each benchmark inventory row records at least:

- scenario ID and workload revision;
- host mode and engine where applicable;
- build type and compilation mode;
- deterministic setup and reset;
- ready, action, state, and target resource IDs;
- action or gesture sequence;
- locale, theme, direction, font scale, density, and reduced-motion policy;
- measured interval boundaries; and
- invalidating changes since the previous revision.

`DemoBenchmarkScope` is split by responsibility. Launching and querying resource targets remain
generic; scenario-specific action scripts live beside their benchmark specs. General helpers no
longer search an arbitrary document up and down for text.

Existing `PerformanceComparisonActivity` data models and paired engine screens are retained as a
positive baseline. Their visible strings and machine targets are migrated, but the data shape and
measurement semantics do not change without a workload revision.

## Execution plan

| Phase | Status | Primary output | Exit gate |
| --- | --- | --- | --- |
| 0. Inventory and freeze | Completed | Scenario map, current selector map, current workload revisions, same-device baseline | Every existing automated path has an owner and replacement scenario ID before UI movement. |
| 1. Contract and automation spine | Completed | Scenario registry, strict direct route, role-based targets, Android resource-ID bridge | Instrumentation and Macrobenchmark can launch/query a pilot scenario without visible text. |
| 2. Host and catalog hard cut | Completed | Shared/dedicated/benchmark host policies, compact catalog, environment/build panels | Catalog contains executable scenarios only; top-level pager, About, planned modules, and gaps are removed. |
| 3. Localization and content policy | Completed | Canonical English and Simplified Chinese resources, hard-coded-copy gate, localized guide model | Both locales pass; selectors and benchmark scripts are unchanged between locales. |
| 4. Scenario migration | Completed | Fixture-first routes for every retained capability, chapter tabs split or explicitly justified | Primary fixture/action/result are directly reachable; old module/page wrappers have no callers. |
| 5. Benchmark rebaseline | In progress | Revisioned release/comparison/interaction baselines and reports on the reference device | Same-device results pass the performance policy and record scenario/revision metadata. |
| 6. Cleanup and Runtime-plan unlock | Not started | Old route/tag/section infrastructure removed; durable docs updated | Completion criteria pass, this plan is archived, then the Runtime/Patch plan is re-audited against the new baseline. |

## Phase 0: Inventory and contract freeze

Create one checked inventory row for every current catalog module, internal page selection,
dedicated Activity, instrumentation launch, Macrobenchmark launch, and screenshot baseline.

For each row decide:

1. retain as one scenario;
2. split into several independently launchable scenarios;
3. merge because the difference is presentation-only;
4. keep a dedicated host because Android ownership is under test; or
5. remove because it is roadmap, documentation, placeholder, or duplicate content.

Record current benchmark workloads before layout migration. At minimum capture cold startup, state
patch, diagnostics long fling, list scroll/mutation, complex layout scroll/update, navigation
motion, design-system slice, and advanced shadow comparisons under their existing scripts.

Phase 0 changes no framework production behavior. It completes only when test owners approve the
mapping and every historical comparison can be classified as retained, revised, or retired.

## Phase 1: Contract and automation spine

Implement the registry and target model with one pilot from each host class:

- ordinary state fixture;
- dedicated resource/configuration fixture;
- overlay fixture;
- system-navigation fixture; and
- performance comparison fixture.

Add strict validation tests for duplicate scenario IDs, duplicate target roles, missing resources,
missing reset on mutable fixtures, missing workload revision on benchmarks, and unsupported host
configuration.

Migrate pilot instrumentation and Macrobenchmark scripts to `By.res(...)`. Add a source gate that
rejects Demo-owned `By.text(...)`, text-scroll helpers, and direct visible-copy action selectors.
System UI, IME, and third-party surfaces may use a narrow documented allowlist because the Demo
does not own their resource IDs.

Completed on 2026-08-14. Six pilot scenarios now cover shared state, dedicated resource,
overlay, system-navigation, and comparative-performance hosts. Registry tests enforce identity,
route determinism, resource naming, mutable reset, and workload completeness. The migrated
instrumentation and Macrobenchmark paths use `By.res(...)`; `verifyDemoAutomationSelectors`
freezes every remaining visible-copy selector as exact migration debt so new files cannot add one
and Phase 4 must reduce the baseline. A Samsung SM-G991B running Android 13 passed the black-box
test that launches and queries all five host classes through `MainActivity` without visible copy.

Phase 1 is a prerequisite for localization. Do not translate the current text-selector protocol
and then replace it later.

## Phase 2: Host and catalog hard cut

Replace `DemoModule`, `DemoModuleStatus`, `AVAILABLE_DEMO_MODULES`, `PLANNED_DEMO_MODULES`, the home
pager, and the four bottom destinations with the validated scenario registry and compact catalog.

Move global configuration controls into the Environment panel. Move theme/token/design-system and
resource checks into direct scenarios. Replace About with generated build facts. Delete the gaps
page and all planning/module statistics.

Keep the current stable launcher component for external benchmark tooling. Validate that launcher
redirect adds no measured frame after the scenario host begins and leaves no empty Activity in the
task stack.

Completed on 2026-08-14. The root `HorizontalPager`, bottom navigation, `DemoModule` inventory,
About, placeholder, and diagnostics-gap surfaces were removed. The validated scenario registry now
drives one searchable and filterable executable catalog; global theme/runtime facts and generated
package/build facts live in separate toolbar panels. `MainActivity` accepts strict scenario IDs and
no longer owns a module-key adapter. The remaining module-key bridge is confined to legacy
Macrobenchmark test code until Phase 4 migrates those workloads.

Registry and catalog-filter unit tests, AndroidTest and Macrobenchmark compilation, and the
visible-copy selector gate pass. On the Samsung SM-G991B running Android 13, seven black-box tests
verified catalog recreation, both toolbar panels, theme propagation across independent sessions,
strict catalog launch, every pilot host class, resource-ID readiness, and a foreground task history
containing the scenario host but no launcher shell. Visual inspection of the installed default-
English catalog confirmed that the first executable scenario and its launch action remain in the
initial viewport without the removed project-management content.

## Phase 3: Localization and content hard cut

Move visible source literals to resources domain by domain. Use canonical default English plus
`values-zh-rCN`; format dynamic values through placeholders and plurals.

Add mechanical gates for:

1. resource-key parity between the two locales;
2. format-argument and plural-item parity;
3. hard-coded human-language literals in Demo production source;
4. visible resource use in Activity titles, content descriptions, overlays, and native AndroidView
   updates; and
5. selector invariance across locale changes without Activity-process state leakage.

Stable test data that is intentionally not language must be declared in a small allowlist with a
reason. The allowlist cannot contain explanatory prose.

In progress on 2026-08-14. Default-English and `values-zh-rCN` resources now have mechanical key,
selector, plural, and format-signature parity checks. The catalog, shared hosts, scenario contract,
theme/source labels, Activity titles, resource-configuration fixture, and State fixture resolve
visible copy through resources. A cross-locale device test verifies that the same scenario-owned
Android resource ID remains usable after an in-process locale switch without leaking the changed
locale into later tests. `verifyDemoLocalizedVisibleCopy` prevents direct visible assignments from
returning to already migrated source domains and expands as each domain is converted; it is not a
legacy-literal count allowlist. The State, Diagnostics, Collections, Layouts, and Input domains are
now fully resource-backed. The Samsung SM-G991B Android 13 reference device passed all 97 app
instrumentation tests at the localization-spine milestone; after the Input slice, both locales pass
all five action-reset contracts and all six focused Input visual and keyboard-follow regressions.

## Phase 4: Scenario migration and page simplification

Migrate fixtures by risk rather than file order:

1. state, renderer diagnostics, collections, and layouts because they anchor the upcoming Runtime
   and View patch baseline;
2. input, gestures, graphics, animation, and modifiers;
3. resources, AndroidView interop, overlays, navigation, and lifecycle;
4. Material 3, custom-token, multi-design-system, and One UI verification; and
5. component visual/showcase scenarios.

Remove `ChapterPageOverviewSection`, `ChapterPageFilterSection`, `BenchmarkRouteCallout`, visible
framework-module lists, visible stable-target lists, and repeated scenario-kind hints as their last
callers migrate. Retain a small reusable guide presentation only for localized human verification.

Do not preserve a chapter tab merely to reduce diff size. A hard cut is preferred when direct
scenario identity produces a simpler and more reliable model.

The State domain now has three strict direct fixtures: `runtime.state`,
`runtime.key-identity`, and `runtime.view-patch`. `StateActivity` requires immutable scenario
identity; the chapter page index, local page switcher, overview, checklist, verification prose,
and visible benchmark route metadata were deleted. Each fixture creates only its own runtime
state and observers. Mutable fixtures expose deterministic resets: Core resets benchmark, local,
and ViewModel state; Key Identity recreates its keyed subtree; View Patch resets text, selection,
and both pager positions. The Core and View Patch hierarchy changes advance their workload
revisions to 2. Their Macrobenchmarks now use scenario resource roles, the old `state` module
bridge has no callers, and the legacy selector baseline fell from 27 to 16. Eight focused State
device tests plus the registry-wide root/ready device sweep pass on the reference device.

In progress on 2026-08-14. `diagnostics.runtime`, `diagnostics.theme`, and
`diagnostics.renderer` now have strict registry identities, direct Activity routes, and
scenario-owned root, ready, state/action where applicable, and fixture-boundary targets. The
Diagnostics chapter overview, page switcher, benchmark route callout, copied renderer-model
description, manual-probe checklist, and verification/gaps branches were deleted rather than
translated or retained behind a compatibility mode. The theme long-fling workload now launches
the direct scenario and synchronizes against first/last Android resource IDs; its hierarchy change
advances the workload revision from 1 to 2. The obsolete tab-switch workload was removed because
chapter tabs no longer exist in the target information architecture. Runtime, renderer, and theme
visible copy now use paired locale resources. The theme fixture resolves human-facing labels,
notes, sample values, and accessibility descriptions through resources while keeping framework
token and API identifiers as stable diagnostic data. The complete Diagnostics domain is in the
hard-coded-copy gate, and all three strict diagnostics routes pass on the reference device. The
Collections domain is also fully resource-backed and covered by that gate; localized prose never
serves as a selector or workload boundary.

All seven retained collection fixtures now have strict `collection.*` identities and direct
Activity routes. The chapter overview, page switcher, verification checklist, and visible route
callouts were deleted. Mutable list, stress, grid, and refresh fixtures expose deterministic reset
roles. The collection-controls interaction and collection-stress mutation workloads use Android
resource roles and advance to revision 2 because the direct fixture hierarchy replaces the former
chapter page. The old `collections` Macrobenchmark module bridge has no callers and was removed.
Nine focused device tests covering strict routes, collection behavior, localization-safe targets,
and component smoke checks pass on the reference device.

The six retained layout fixtures now use strict `layout.*` identities and direct Activity routes.
The checklist page, chapter overview, page switcher, verification copy, visible route callouts, and
the `layouts_page_index` contract were deleted. Linear layout advances to workload revision 2 and
uses resource-ID roles for benchmark mutation, observation, reset, and fixture targeting. Stack,
edge, flow, and constraint fixtures expose deterministic reset roles; scroll remains immutable.
Layout UI tests launch scenario identities directly and assert geometry or state transitions rather
than localized labels. The old `layouts` Macrobenchmark module bridge has no callers and was
removed. All retained layout fixture copy, including dynamic counts, constraint-mode diagnostics,
and accessibility descriptions, now resolves through paired locale resources, and the complete
Layouts domain is protected by the hard-coded visible-copy gate.

The Input domain now has five strict direct fixtures: `input.fields`, `input.selection`,
`input.stress`, `input.search`, and `input.derived-summary`. `InputActivity` requires immutable
scenario identity; the chapter overview, page switcher, verification copy, visible route callout,
and `input_page_index` contract were deleted. Each fixture creates only its own state, and every
mutable fixture publishes deterministic action, state, target, and reset roles. The fields workload
advances to revision 2, uses scenario resource IDs end to end, and no longer needs the old `input`
Macrobenchmark bridge. Strict automation IDs also replace overlapping legacy test tags, so one
native view has one unambiguous automation identity. The text-selector baseline fell from 16 to 12.
Paired resources cover all Input fixture copy, and the hard-coded-copy gate now owns the domain.

The Gestures domain now has three strict direct fixtures: `gesture.tap`,
`gesture.drag-swipe`, and `gesture.transform`. `GesturesActivity` requires immutable scenario
identity; the chapter overview, page switcher, verification copy, and `gestures_page_index`
contract were deleted. The hard cut also fixes the former lifecycle shape in which every route
created all three fixtures' state, the drag frame callback, and the transform recognizer. Each
route now enters one fixture function, so inactive gesture state and effects never join its
Session. Every fixture publishes deterministic action, state, target, and reset roles; tap and
drag/swipe expose a secondary physical target without reusing a legacy tag identity. Paired
resources cover the complete domain, and the hard-coded-copy gate now owns it. On the Samsung
SM-G991B Android 13 reference device, all five registry automation tests passed, including the
three gesture action-reset contracts in English and Simplified Chinese, followed by four focused
real-input tests for pointer consumption, tap fallback, drag/anchor settling, and two-pointer
transform.

The Graphics domain now has four strict direct fixtures: `graphics.drawing`,
`graphics.outer-shadow`, `graphics.inner-shadow`, and `graphics.shadow-list`. `GraphicsActivity`
requires immutable scenario identity; the chapter overview, page switcher, verification copy, and
`graphics_page_index` contract were deleted. Drawing state, inner-shadow interaction state, shadow
backend lifecycle, diagnostics, and the 1,000-row lazy workload now exist only in their owning
fixture. The lazy fixture retains stable keys and one shared lazy-row content type without mounting
the drawing or interactive shadow Sessions. Mutable fixtures publish deterministic action, state,
target, and reset roles; cache counters remain visible diagnostics but are deliberately excluded
from the reset identity because they are process-level observations. Paired resources cover all
visible copy, including Canvas labels resolved during composition before draw callbacks, and the
hard-coded-copy gate now owns both Graphics source files. Focused device evidence covers all three
advanced-shadow behaviors and both drawing-control paths, while the strict cross-locale automation
contract covers drawing, inner shadow, and shadow diagnostics.

The Animation domain now has six strict direct fixtures: `animation.core`,
`animation.content`, `animation.list-motion`, `animation.specs`, `animation.transition`, and
`animation.infinite`. `AnimationActivity` requires immutable scenario identity; the chapter page
index and infinite-pulse extras, page switcher, overview, filtering, and verification prose were
deleted. Only the selected fixture creates state, animation objects, coroutine context, or
`LaunchedEffect`, so inactive animation Sessions no longer join an unrelated route. The list-motion
fixture stores stable logical item identities and resolves labels during composition, preventing a
locale change from freezing translated text into reusable state. Every fixture exposes localized,
deterministic action/state/reset roles; the infinite reset state deliberately excludes transient
command bookkeeping while still snapping the controlled value to its initial target. The
hard-coded-copy gate now owns the Animation source, and the old Activity extras have no callers.
On the Samsung SM-G991B Android 13 reference device, the registry-wide root/ready sweep, all six
action-reset contracts in English and Simplified Chinese, and seven focused animation behavior
tests passed.

The Modifiers domain now has three strict direct fixtures: `modifier.visual`,
`modifier.sizing`, and `modifier.accessibility`. `ModifiersActivity` requires immutable scenario
identity; the page-index extra, local page switcher, overview, copied Modifier inventory, and
verification checklist were deleted. These fixtures are intentionally immutable, so they publish
root/ready and visual targets without artificial action or reset roles. Visual verification owns
separate color-only and Drawable-preferred targets, sizing targets the fill-height sample, and the
accessibility route targets both the described Box and the native-patched TextView. Paired
resources cover all visible and accessibility copy, and the hard-coded-copy gate owns the complete
Modifiers directory. On the Samsung SM-G991B Android 13 reference device, all three strict routes
passed the registry-wide root/ready sweep; focused tests also passed for Drawable precedence and
outline clipping, fillMaxHeight parent geometry, localized contentDescription, and replayed
nativeView typeface/letter-spacing patches.

The existing dedicated `environment.resources` route already satisfied the third-risk-group
contract and required no duplicate migration. Android View interop now has one strict direct
fixture, `interop.android-view`; `InteropActivity` requires that immutable scenario identity, and
the chapter overview, page switcher, route callout, verification checklist, and duplicate basic
fixture were deleted. State, action, reset, declarative mirror, and native TextView result use
scenario-owned resource roles. Dynamic state, localized resources, and theme tokens are read
inside the mounted lazy-item Session so a stable item key cannot freeze ordinary captured values;
the same native TextView is patched in place across state and light/dark theme changes. The direct
host and hierarchy advance the workload revision from 1 to 2. Its Macrobenchmark now uses only
resource-ID roles, the legacy `interop` module bridge has no callers and was removed, and the
guarded visible-text selector baseline fell by four usages. Paired resources cover the complete
fixture, and the hard-coded-copy gate owns the Interop domain. On the Samsung SM-G991B Android 13
reference device, two renderer lifecycle regressions, the in-place state/theme fixture, the
English and Simplified Chinese action-reset contract, and the registry-wide root/ready sweep
passed.

The Overlay domain now has three strict direct fixtures: `overlay.transient`, `overlay.dialog`,
and `overlay.menu`. `FeedbackActivity` requires immutable scenario identity; the chapter page
index, local page switcher, filter controls, benchmark-route claim, verification checklist, and
aggregate state object were deleted. Each route creates only the state and overlay requests it
owns, so an inactive dialog, menu, tooltip, Snackbar, Toast, or popup no longer participates in
another route's Session. Modal fixtures expose their `target` and deterministic `reset` inside the
overlay window because Android correctly hides the obscured Activity window from black-box
accessibility queries; non-modal menu state remains observable in the host. All automation now
uses app-owned resource IDs, removing the final two guarded visible-text selectors from the
Feedback tests and all legacy Feedback tags. Paired resources cover the complete domain, and the
hard-coded-copy gate owns the Feedback directory. No workload revision was created because the
former page only displayed a benchmark-shaped callout and had no Macrobenchmark owner. On the
Samsung SM-G991B Android 13 reference device, three focused overlay flows, the English and
Simplified Chinese action-reset contract, and the registry-wide root/ready sweep passed.

The dedicated `navigation.system` fixture now requires its strict scenario identity for ordinary
launches; only an external `ACTION_VIEW` deep link may bind the sole owned scenario implicitly.
The first lazy item owns localized state plus deterministic Push and full-Session Reset roles, and
the old visible manual checklist and `adb` command were removed. Navigation events, result
summaries, and deep-link outcomes are stored as semantic values rather than translated strings, so
an in-process locale change cannot freeze old-language state. Reset advances a generation key and
recreates the controller, entry and graph owners, saveable state, and ViewModels instead of merely
popping the active stack. This host and hierarchy change advances the navigation motion workload
revision from 1 to 2. The hard-coded-copy gate owns both system-navigation source files and the
dedicated Activity while leaving the separate Navigation-components showcase for the fifth-risk
group. On the Samsung SM-G991B Android 13 reference device, the bilingual action/reset contract,
all three focused independent-stack, deep-link, recreation, and owner-lifetime tests, and the
registry host-role sweep passed. The automation text reader also now reacquires a role node after
an intentional full-Session reset instead of assuming that an old `UiObject2` remains valid.

Material 3 theme-source verification now has three strict direct fixtures:
`design.material3-xml`, `design.material3-static`, and `design.material3-custom`. Scenario identity
is the only source selector; the permissive theme-source extra and unknown-value fallback were
deleted. All three routes retain the same component and diagnostic shape so source attribution is
the controlled variable, while localized ready/state/action/reset roles occupy the first item.
Reset advances both the parent composition generation and every lazy-item key. That second
identity boundary is required because each lazy item owns an independent logical Session and must
not retain its old remember, text-field, or callback identity when the fixture is reset. Paired
resources cover all human-facing source, control, diagnostic-label, and accessibility copy; stable
token IDs, producer IDs, recipe IDs, enum names, and color-role abbreviations remain diagnostic
data. The hard-coded-copy gate owns the page and dedicated Activity. These routes have no current
Macrobenchmark owner, so no workload revision was added. On the Samsung SM-G991B Android 13
reference device, the bilingual three-source action/full-reset contract, all five existing theme
source, touch-target, native-switch, and state-layer tests, and the registry-wide host-role sweep
passed.

Multi-design-system verification now has exactly two public scenario identities:
`design.bundle-material3` owns the rounded Material 3 reference bundle, while
`design.bundle-contrast` owns the cut-contrast bundle and its internal `cupertino-pressure`
verification variant. The variant extra is no longer a launcher route or permissive source
selector: it is accepted only by the owning strict scenario, and an unknown or cross-bundle value
fails deterministically. Root replacement updates both scenario and variant identity before
Activity recreation. Caller state intentionally survives that explicit replacement, while Reset
advances a generation embedded in every lazy-item key so remember, effect, field, and callback
identity are recreated throughout the fixture. Paired resources now cover the full page and all
three variant labels. Scenario roles replace the old root, primary-action/state, and secondary
action/state tags; common overlay resource IDs cover black-box dialog interaction across Activity
replacement. This hierarchy and automation hard cut advances both bundle workloads from revision
1 to 2. It removes all 23 fixture-specific visible-copy selectors from the instrumentation and
Macrobenchmark owners, plus the obsolete shared launcher readiness lookup. On the Samsung
SM-G991B Android 13 reference device, both bundles passed the action/full-reset contract in English
and Simplified Chinese, the five-case light/dark, LTR/RTL, font-scale, reduced-motion, native-input,
accessibility, recreation, and screenshot matrix passed, root/lazy/overlay replacement coherence
passed, and the registry-wide host-role sweep passed.

One UI 7 verification now has one strict direct fixture, `design.oneui7`.
`OneUi7VerificationActivity` requires that immutable scenario identity, so direct launches can no
longer bypass the registry contract. The page retains the independent One UI five-component and
overlay-presenter semantics rather than inheriting Material assumptions. Scenario roles own its
root, ready, primary and secondary actions, state, reset, and visual targets; app resource IDs own
the bottom-sheet content and actions. The existing presenter accessibility descriptions remain
framework-owned window identities, while app-visible navigation and overlay copy is no longer an
automation selector. Reset advances both the parent composition generation and every lazy-item
key, recreating button, Switch, text-field, navigation, Snackbar, and bottom-sheet state instead of
leaving independent item Sessions alive. Paired resources cover the complete fixture, and the
hard-coded-copy gate owns both the page and dedicated Activity. The guarded visible-text selector
baseline fell by four usages, and obsolete launcher/root/action/state tags were removed. This
fixture has no Macrobenchmark owner, so no workload revision was added. On the Samsung SM-G991B
Android 13 reference device, the bilingual action/full-reset contract, the complete light/dark,
LTR/RTL, 1.0/1.3 font-scale, Switch drag, navigation accessibility, Snackbar, bottom-sheet, and
screenshot evidence matrix, and the registry-wide host-role sweep passed.

The Actions chapter is now four strict component fixtures: `component.card`, `component.fab`,
`component.chip`, and `component.list-item`. `ActionsActivity` requires one of those immutable
scenario identities; the page-index extra, chapter switcher, copied module overview, fake benchmark
anchor, and manual verification checklist were deleted. Each route mounts only its own state and
component variants. Its first lazy item exposes the real component action, observable state, and a
full-Session reset; the remaining items retain only the visual variants needed to verify the
component family. Reset advances both the parent generation and every lazy-item key. Dynamic state
copy is resolved inside the owning lazy-item Session rather than captured as a parent String, so a
stable key/revision cannot retain stale state. Paired resources cover all visible and accessibility
copy, scenario resource roles replace the two legacy Actions tags, and the hard-coded-copy gate
owns the page and Activity. These fixtures have no Macrobenchmark owner, so no workload revision
was added. On the Samsung SM-G991B Android 13 reference device, all four routes passed the English
and Simplified Chinese action/full-reset contract, the elevated-card shadow regression and
component-family smoke test passed, and the registry-wide host-role sweep passed.
Shared-host ready markers now show a workload revision only when the scenario owns a real
`DemoBenchmarkContract`; visual and manual-only scenarios no longer publish the misleading
`workload r0` label.

The Navigation-components chapter is now three strict component fixtures:
`component.app-bars`, `component.navigation-bar`, and `component.scaffold`. `NavigationActivity`
requires one of those immutable scenario identities; the page-index extra, chapter switcher,
copied module overview, fake benchmark anchor, and manual verification checklist were deleted.
System stack, deep-link, lifecycle-owner, and predictive-Back behavior remains exclusively owned
by the separate `navigation.system` host. Each component fixture creates only its own state and
places a real AppBar action, NavigationBar selection, or Scaffold floating action together with
observable state and full-Session reset in the first lazy item. Reset advances both the parent
generation and every lazy-item key. Paired resources cover all visible and accessibility copy;
scenario resource roles replace the three obsolete Navigation tags and visible-copy selection in
the focused NavigationBar test. These visual fixtures have no Macrobenchmark owner, so no workload
revision was added. On the Samsung SM-G991B Android 13 reference device, all three routes passed
the English and Simplified Chinese action/full-reset contract, the NavigationBar selection
regression and component-family smoke tests passed, visual inspection confirmed a non-overlapping
Scaffold hierarchy, and the registry-wide host-role sweep passed.

The legacy WidgetShowcase audit corrected an over-specified frozen inventory rather than creating
20 duplicate routes. Fourteen detail pages were already fully owned by Foundations, Input, or the
action-component fixtures. The remaining Divider, Button, IconButton, SegmentedControl, and paired
linear/circular Progress coverage now uses five strict `component.*` scenarios under
`ComponentShowcaseActivity`; the chooser, back row, translated-name navigation, and all seven old
section files were deleted. Button, IconButton, SegmentedControl, and Progress expose real action,
state, target, and full-Session reset roles. Divider is intentionally immutable and publishes only
root, ready, and visual target rather than synthetic state. Paired resources cover the entire new
domain, and the hard-coded-copy gate owns both the page and Activity. The former Checkbox detail
touch regression now launches `input.selection` and uses its owning resource target. None of these
visual fixtures has a Macrobenchmark owner, so no workload revision was added. On the Samsung
SM-G991B Android 13 reference device, the four mutable fixtures passed their English and
Simplified Chinese action/full-reset contract, all five visual targets and the SegmentedControl and
Checkbox native interaction regressions passed, the Button matrix was visually inspected, and the
registry-wide host-role sweep passed.

The Foundations chapter is now four strict direct fixtures: `foundations.locals`,
`foundations.theme`, `foundations.media`, and `foundations.typography`. `FoundationsActivity`
requires one of those immutable scenario identities; the page-index extra, chapter switcher,
architecture prose, copied component inventory, cross-chapter jump controls, verification
checklist, and fake benchmark switch were deleted. Locals, Theme, and Typography are intentionally
immutable visual fixtures and therefore do not manufacture action or reset roles. Media retains
the real resource, URL/fallback, delayed-model replacement, cancellation, and icon-content-color
behavior; it alone exposes action, state, target, and full-Session reset roles, with the parent
generation embedded in every lazy-item key. Component Progress and IconButton matrices remain
owned by their `component.*` scenarios instead of being duplicated here. Paired resources cover
the complete domain, and the hard-coded-copy gate owns the page and Activity. The two legacy
Macrobenchmarks were removed rather than rebaselined: one measured an artificial on/off switch,
and the other measured a module-to-scenario launch that is not a registered workload. Neither had
a comparable framework behavior to preserve.

The frozen Preview inventory was corrected after tracing the real preview execution paths. The
three on-device pages were not executable Preview fixtures: Bridge toggled ordinary Activity state,
Overlay Mock duplicated the `feedback-overlay-static` PreviewCatalog spec, and Snapshot rendered
only a Gradle command and report path. `PreviewActivity`, its page-index route, all five test tags,
and the device smoke assertion were therefore deleted instead of publishing misleading
`preview.*` scenario IDs. The debug Compose-preview light/dark entrypoints and the static-runner
light/dark entrypoint now render the real `component.button` fixture directly. Overlay ownership
remains in PreviewCatalog and Paparazzi, while snapshot truth remains the `qaPreview` gate. These
tooling-only execution paths stay outside the on-device Demo registry because a normal Activity
cannot validate Compose Preview session retention, Layoutlib configuration, artifact export, or
Paparazzi baselines.

The performance-comparison audit found that only `performance.list` had entered the registry and
that its caller-supplied Compose engine was overwritten by the route default, so both sides of the
nominal comparison could execute ViewCompose. The host now requires one of four strict workload
identities: `performance.list`, `performance.complex-layout`, `performance.shadow-list`, or
`performance.shadow-complex-layout`. Each identity fixes its scenario shape while declaring only
the engine extra as a caller-overridable dimension; the shadow backend remains an additional
explicit dimension when applicable. The Activity rejects a mismatched scenario ID and wire
scenario, both engines publish the same root/ready/action/reset/state/target resources, and all
comparison Macrobenchmarks use those resources instead of English copy. The obsolete
engine-without-scenario launcher bridge was removed. Performance copy is now paired Android
resources and the whole source domain is under the hard-coded visible-copy gate.

The original frozen table's 240-row claim was corrected to 1,000 rows. Source history shows the
fixture has used 1,000 rows since the comparison workload was introduced; changing it to 240 would
silently create a different benchmark. Workload revision 1 therefore remains accurate because the
canonical English row/card trees, data rules, actions, and measured content were preserved while
route setup and synchronization moved outside the measured block.

On the Samsung SM-G991B Android 13 reference device, the four scenario shapes passed the complete
ViewCompose/Compose action and reset matrix. The audit also found that Compose's
`testTagsAsResourceId` bridge publishes the test tag verbatim: an unqualified tag is not selectable
through the same package-qualified `By.res(package, name)` contract as an Android View. The paired
Compose fixtures now derive the fully qualified resource name from the scenario-owned `R.id`, so
both engines expose one identical black-box selector. The registry-wide root/ready sweep, including
all four default ViewCompose performance routes, passed after this correction.

Cross-Activity theme propagation is now owned by the strict
`environment.cross-activity-theme` Dedicated scenario instead of an unregistered secondary page.
The fixture starts from deterministic Light state, exposes resource-backed primary action,
secondary-Activity action, state, target, and reset roles, and restores the caller's application
theme when the scenario finishes. The second Activity owns an independent render Session and its
own Android resource targets, so the device contract verifies propagation in both directions
rather than inferring it from one Activity. All visible copy is paired English and Simplified
Chinese resources. The old `themeSwitch` Macrobenchmark is intentionally retired: it measured
translated controls inside the general Environment page, did not launch a second Activity, and
was never a frozen framework workload. On the Samsung SM-G991B Android 13 reference device, the
focused lifecycle/appearance test and the bilingual black-box action/reset test both passed.

The Phase 4 selector migration is complete. The final unused module-launch bridge, catalog/chapter
text helpers, visible-text click/wait/scroll helpers, and their dead app-instrumentation wrappers
were deleted. Resource-target scrolling remains as a narrowly named helper and no longer shares a
text-search abstraction. `verifyDemoAutomationSelectors` now enforces a zero-debt contract instead
of preserving exact legacy counts: any `By.text(...)` or named visible-copy selector in Demo
instrumentation or Macrobenchmark source fails the build. There is no current system, IME, or
third-party exception requiring an allowlist.

The Phase 5 preflight removed one misleading synthetic workload before accepting any measurements.
`diagnosticsRefreshAfterPatch` changed state in one Activity and measured launching a different
Activity, so it neither isolated renderer patching nor owned a frozen scenario contract. The real
`diagnostics.renderer` interaction remains and declares Benchmark ownership. Its Phase 5 stability
preflight then advanced the workload to revision 3: one refresh produced too few frames and omitted
reset completion, so each iteration now measures eight complete refresh-and-reset cycles.
Comparative reports persist scenario ID and workload revision beside each measured
action; longitudinal gates require the prior revisioned comparison report and reject cross-revision
rows instead of inferring an old workload revision from current source.

## Phase 5: Benchmark rebaseline and acceptance

Build the release-like target, run the revised scenarios on the same device and thermal policy,
and produce reports containing scenario ID and workload revision.

The 2026-08-15 preflight invalidated both a monolithic suite run and a ten-iteration isolated
state-patch run: each moved the Samsung SM-G991B from `LIGHT` to `SEVERE`, while AndroidX recorded
no thermal sleep. Formal physical interaction methods therefore hard-cut to five clean iterations.
Cold startup retains ten iterations because a five-run retry produced one genuine first-run
cold-cache outlier and failed the `0.15` stability gate. Every method starts at `NONE` or `LIGHT`;
benchmark and target processes are stopped and the screen is turned off between methods; any batch
ending at `SEVERE` is rejected regardless of its apparent variance.

Paired ViewCompose/Compose methods are also isolated from ordering heat: each method starts from the
same accepted thermal state and cools independently. The report tool deterministically merges their
raw JSON only when device, OS, clock-policy, and compilation identities match, and rejects duplicate
method names. Legacy input without an explicit clock policy still requires matching AndroidX
`cpuLocked` snapshots. This preserves one revisioned paired report without making the second engine
inherit the first engine's heat.

The batch installs its APKs once, then starts each cooled method directly through the already
installed instrumentation runner. This ordering is part of the protocol, not an operator
convenience: AndroidX snapshots `cpuLocked` during instrumentation-process initialization, while an
OEM package-install or wake boost may temporarily raise `scaling_min_freq` and produce a false
locked classification. Normal minimum frequencies and the accepted thermal state are verified
before every method. The verified consumer-device runner writes
`clockPolicy=unlocked-dvfs-preflight-v1` into the AndroidX benchmark payload. Compatibility uses that
durable policy instead of the racy launch-time boolean, while the report preserves every raw
`cpuLocked` snapshot and exposes mixed observations. A missing or different policy still fails
closed; report generation never rewrites captured context.

The launch boost also affected the measured workload itself: two independently cooled Compose
complex-layout scroll samples made their first iteration roughly 40% faster than the following four
and failed the stability gate. An initial 1.5-second settling trial still left the first
complex-layout update iteration at `4.20 ms` versus `5.70`-`6.89 ms` for later iterations, producing
CV `0.170`. Every paired performance setup therefore waits a conservative 5 seconds after the target
is ready, outside the measured block. Because this changes the timing contract, list and complex
layout advance to revision 3; both shadow comparison scenarios advance to revision 2. Revision 2
list results and partial complex-layout results are retained only as rejected preflight evidence and
are not accepted as replacement baselines.

The Demo registry and report tool must publish those same revisions. A cross-language report-tool
test parses the four registered performance scenarios and rejects any disagreement with
`SCENARIO_CONTRACTS`; a focused registry unit test also pins the accepted values. The registry
correction from list/complex revision 2 and shadow revision 1 changes metadata only: the isolated
performance screens do not render or branch on that field, and the accepted revision 3 raw payloads
already identify the measured workload correctly. The existing list/complex physical results
therefore remain valid rather than being relabeled after collection.

The same launch-boost invariant applies to the remaining warm navigation and design-system
interactions. Their setups now wait 5 seconds after fixture positioning or navigation preloading,
outside measurement. Design-system cold initial-build methods deliberately do not wait because
launch is the measured operation. This timing-contract correction advances `navigation.system`,
`design.bundle-material3`, and `design.bundle-contrast` from revision 2 to revision 3 before any
replacement result is accepted; there is no revision 2 baseline to relabel.

The first isolated revision 3 navigation push preflight still produced too little statistical
weight: frame counts were stable at 51-57, but the five run-P50 values were
`9.779/7.545/4.044/8.708/9.637 ms` and CV was `0.265`. Navigation revision 4 therefore attempted
eight same-direction transitions per iteration while preserving separate push and pop methods. Its
first instrumentation preflight failed before measurement because consecutive standard pushes to
the same route produced identical event copy after the second transition, so the scenario `state`
target could not prove that the active destination changed. Navigation revision 5 makes that
automation state include the active stack depth and strengthens the bilingual device test to
require eight distinct consecutive state changes. Revision 5 then completed five iterations of
eight pushes, but its roughly two-minute measurement window crossed the device thermal boundary:
the first three run-P50 values were `4.504/4.502/4.320 ms`, the last two rose to
`8.356/8.614 ms`, and CV reached `0.327`. Navigation revision 6 therefore measures four
same-direction transitions per iteration. That still contributes roughly 200 transition frames per
run instead of revision 3's roughly 50 while bounding sustained work. Pop setup preloads four
destinations outside measurement, then the measured block returns through all four.

Revision 6 proved that workload size was no longer the blocker. Its uncompiled push run produced
202-216 frames but run-P50 values of `8.134/8.702/9.566/4.572/4.358 ms` (CV `0.308`). Two
profile-guided retries ended at `LIGHT` and `NONE` yet also failed at CV `0.323` and `0.317`.
During the latter run, read-only sampling showed Samsung changing `scaling_max_freq` repeatedly
between full and capped plateaus without a thermal-status change. AndroidX also reported that the
Runtime Image could not be cleared, and direct shell profile reset is denied on this user build.
The platform fixed-performance command held one frequency ceiling but still produced CV `0.372`;
enhanced processing did not create a clock lock. A representative design revision 3 retained-patch
run failed similarly at CV `0.262`. These results are rejected device-capability evidence. Revisions
3-5 remain rejected workload preflights; revision 6 is the retained navigation workload, but neither
navigation revision 6 nor design revision 3 receives a baseline until a clock-controllable reference
device is available.

For structurally unchanged workloads, apply the repository performance policy: P50 fails only when
it regresses by more than both 5% and 0.3 ms; P95 fails only when it regresses by more than both 10%
and 0.8 ms. Unstable runs are rerun rather than interpreted. A changed workload revision receives a
new baseline and is never presented as an optimization against the old revision.

The Runtime/Patch modifier-binding and LocalSnapshot experiments may begin only after state patch,
renderer diagnostics, list scroll/mutation, and complex-layout baselines have stable revisioned
results. Those scoped prerequisites are satisfied by the accepted replacement results above;
navigation and design-system matrices do not exercise either optimization and are not part of this
gate.

The fixture-interaction baseline uses the same launch-boost isolation. Direct routing
removed the old diagnostics tab-switch prelude, and the collection fixture now begins from an
explicitly settled host, so `diagnostics.theme` and `collection.stress` advance to revision 2. Their
Macrobenchmark method names carry `Revision2`, while the physical batch also records scenario,
revision, and clock policy in AndroidX payload metadata.

The accepted interaction workload keeps automation overhead outside measurement. Diagnostics uses
eight fixed long flings per direction and verifies the real bottom and top anchors. Collection
scroll resolves the nested LazyColumn bounds during setup, then executes eight fixed swipes per
direction with a 500 ms settle after every gesture. The settle is explicit because the shared OEM
workaround disables UiAutomator's implicit idle timeout. Collection mutation executes eight complete
rotate/insert/reset cycles and verifies the restored state after every cycle.

The shadow comparison baseline uses the same per-method cooling and launch-settling protocol.
Mutation and update now execute eight complete action/reset cycles per iteration; every reset must
restore the initial state. All eight `Auto` (`ExactBitmap`) methods passed the `0.15` frame-CPU
run-P50 stability gate. The owning performance specification records the paired P50/P95 values and
CVs; the raw batch retains mixed AndroidX `cpuLocked` snapshots and the common host-verified clock
policy rather than rewriting captured metadata.

## Explicit removals and non-goals

This plan removes or rejects:

- using the Demo as the framework roadmap or module catalog;
- planned-module cards and placeholder pages;
- a top-level known-gaps destination;
- static architecture/module/API-count content in About;
- route instructions and selector names rendered as user content;
- one giant Activity or pager holding every scenario Session alive;
- one Activity per ordinary catalog grouping;
- visible copy as a benchmark synchronization protocol;
- content descriptions as hidden test IDs;
- retaining chapter tabs for compatibility after callers migrate;
- making screenshots the source of truth for behavior; and
- changing framework runtime or renderer behavior merely to simplify the Demo.

The plan does not redesign the framework's public diagnostics API, add a public automation-ID
modifier, or decide the Runtime/Patch optimization phases. Those decisions require their own
evidence after the new baseline exists.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Registry | Duplicate/missing ID, missing resource, target-role completeness, host-policy, and workload-revision unit tests |
| Direct routing | Cold and warm launch for every scenario; strict unknown-ID failure; no empty launcher Activity |
| In-process automation | Target lookup and interaction by the scenario contract, not manually copied strings |
| Black-box automation | UiAutomator lookup through package resource IDs in English and Simplified Chinese |
| Reset determinism | Launch, mutate, reset, relaunch, Activity recreation, and process restoration where applicable |
| Configuration | Light/dark, locale, RTL, font scale 1.0/1.3, density override, and reduced motion for affected scenarios |
| Accessibility | Localized content descriptions, logical traversal, touch targets, and no machine identifiers exposed as spoken copy |
| Visual | Representative category screenshots in both locales and themes, plus focused component baselines |
| Benchmark | Scenario/revision in output, deterministic ready/action/state targets, same-device P50/P95/heap/RSS evidence |
| Lifecycle | Shared host disposal, dedicated host recreation, overlay cleanup, and no Session retained across unrelated scenarios |
| Documentation | Active plan/index updated, durable performance/tooling docs updated with implemented contracts, structure/language gates pass |

Expected implementation gates include:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest
./gradlew qaRelease
./gradlew benchmarkCompare
./gradlew verifyDocumentationStructure
```

Narrow test tasks may be used while developing a phase, but completion requires the relevant full
device and release-like evidence. Failures caused by existing unrelated environment instability
must be recorded with exact commands and must not be silently treated as passing.

## Risks and controls

| Risk | Control |
| --- | --- |
| Direct routes change current benchmark workload | Freeze and revision workloads before moving UI; compare only equal revisions. |
| Shared host erases lifecycle behavior | Dedicated host policy is part of the scenario spec and covered by host-class pilots. |
| Resource migration changes text width and visual cost | Paired resources, locale matrix, screenshot evidence, and fresh locale-specific baseline where shape changes. |
| Android resource IDs conflict with framework/internal View IDs | Use app-owned `R.id` values through one replay-safe helper; test insert, patch, reset, rollback, and reuse. |
| Registry becomes another oversized central file | Keep immutable metadata in the registry and executable fixture definitions in owning scenario packages. |
| Human guidance becomes undiscoverable | Provide one consistent localized guide action/card in interactive mode while keeping it absent from benchmark mode. |
| Removing module chapters hides coverage | Generate coverage and catalog filters from executable scenario metadata; documentation remains the architecture source. |
| Old test helpers survive beside the new contract | Add source guards and delete text/module adapters before completion. |

## Documentation and release impact

The planning-only change adds no public API and no Maven artifact change.

During implementation:

- update [Performance](../../tooling/performance.md) when benchmark identity, workload revision, or
  report output becomes durable;
- update [Capability verification](../capability-verification.md) when the direct-scenario device
  workflow becomes authoritative;
- update the Runtime/Patch plan only after the replacement baseline is recorded;
- update app-internal comments and tests together with any hard-cut route or selector contract; and
- add Maven release changesets only if published production source or publication inputs change.

## Completion criteria

This plan is complete only when all of the following are true:

1. every retained Demo capability is represented by a unique directly launchable scenario;
2. categories contain executable scenarios only and do not model future modules;
3. no ordinary automation path selects Demo-owned UI by visible text;
4. every scenario exposes `root` and `ready`, and every mutable scenario exposes deterministic
   `reset` and observable `state` targets;
5. default English and Simplified Chinese resources cover all visible Demo copy with passing parity
   and format checks;
6. catalog, fixture, environment, guidance, and benchmark host responsibilities are separated;
7. planned modules, roadmap gaps, module architecture, static API counts, route prose, and
   placeholder About content are removed;
8. benchmark mode mounts no catalog, guide, About, or unrelated diagnostics hierarchy;
9. every measured scenario reports a workload revision and has a same-device baseline under the
   repository performance policy;
10. old module-key routing, chapter-wrapper infrastructure, text-search benchmark helpers, and the
    monolithic test-tag registry have no callers and are removed;
11. instrumentation, Macrobenchmark, visual, configuration, lifecycle, documentation, and release
    gates pass; and
12. durable conclusions are moved to the owning active documents before this plan moves to
    `docs/archive/`.

The 2026-08-14 source-and-contract re-audit of the Runtime data propagation and Android View patch
optimization plan is complete. Its performance experiments may begin after their affected-scenario
gate passes, even while this broader Demo plan retains unrelated device-blocked matrices. They must
use the new scenario IDs and workload revisions rather than reconstructing baselines from the
retired Demo layout.

## Evidence ledger

| Date | Evidence | Result |
| --- | --- | --- |
| 2026-08-14 | Source inventory across Demo pages, Activities, app instrumentation, and Macrobenchmark | Confirmed coarse module identity, Activity/page fragmentation, broad tag usage, and visible-text coupling. |
| 2026-08-14 | Resource audit | Confirmed one app string resource and no second locale file versus at least 660 direct `text` literals. |
| 2026-08-14 | Test selector audit | Confirmed 385 centralized tag constants, 319 production tag applications, and 136 Macrobenchmark text-helper calls. |
| 2026-08-14 | Renderer test-tag path audit | Confirmed `testTag` is stored as `R.id.viewcompose_test_tag`, not an external-process Android resource ID. |
| 2026-08-14 | Running-device layout/screenshot audit on SM-G991B | Captured catalog, four Diagnostics states, Settings, About, State, widget showcase, and performance-list comparison; findings are recorded in the screen matrix. |
| 2026-08-14 | Debug APK build | `./gradlew :app:assembleDebug` passed. |
| 2026-08-14 | Phase 0 scenario and workload freeze | Classified every module/page/dedicated host and benchmark owner; assigned direct scenario IDs and revision 1 to each retained measured workload. |
| 2026-08-15 | Phase 5 thermal preflight on SM-G991B / Android 13 | A combined run and an isolated ten-iteration state-patch run both reached Android thermal status `SEVERE`; results were rejected and the formal interaction protocol was hard-cut to five clean iterations with per-method cooldown. |
| 2026-08-15 | Phase 5 cold-start stability preflight | Five cold starts ended below `SEVERE`, but one 391.95 ms first-run outlier versus four 260–284 ms samples produced CV `0.185`; the batch was rejected and cold startup retained ten iterations independently of the five-iteration interaction protocol. |
| 2026-08-15 | Phase 5 renderer-diagnostics stability preflight | The revision 2 one-refresh workload ended below `SEVERE` but produced frame CPU run-P50 CV `0.372`; it also did not await reset completion. The result was rejected, and revision 3 measures eight complete refresh/reset cycles per iteration. |
| 2026-08-15 | Phase 5 renderer-diagnostics revision 3 and report audit | Five clean runs ended below `SEVERE`; frame CPU P50/P95 were 6.944/14.326 ms with run-P50 CV `0.140`. Signed frame overrun crossed zero, proving CV invalid for that metric; the report retains overrun values and regression gates but limits CV stability to positive ratio-scale frame CPU duration. |
| 2026-08-15 | Phase 5 comparison-workload stability preflight | The revision 1 Compose list mutation used one mutate/reset cycle and produced frame CPU run-P50 CV `0.291`; the result and all scenario-level revision 1 list measurements were retired. List mutation and complex-layout update now execute eight complete cycles, and both scenario contracts advance to revision 2 before rebaseline. |
| 2026-08-15 | Phase 5 installed-runner protocol and list revision 2 preflight | Per-method Gradle installation transiently raised OEM CPU minimum frequencies and made AndroidX report `cpuLocked=true` for two otherwise unlocked runs. Installing once, cooling, verifying normal minimum frequencies, and invoking the installed runner produced four same-context `cpuLocked=false` results. All list frame-CPU run-P50 CV values were `0.108` or lower, but the batch remained provisional until the later launch-boost audit rejected revision 2. |
| 2026-08-15 | Phase 5 launch-boost isolation audit | Two cooled Compose complex-layout scroll samples began with frame-CPU run-P50 near `3.14 ms`, followed by four runs near `5.0`-`5.44 ms`; CV remained above `0.19`. A 1.5-second trial still left complex-layout update at CV `0.170`, so the shared paired-performance setup now includes an unmeasured 5-second post-launch settling window. List and complex-layout contracts advance to revision 3, shadow comparisons to revision 2, and the previously provisional revision 2 list report is rejected. |
| 2026-08-15 | Phase 5 explicit clock-policy gate | AndroidX `cpuLocked` alternated on the same non-rooted device because instrumentation launch boosting transiently raised `scaling_min_freq`. Formal runs now persist `clockPolicy=unlocked-dvfs-preflight-v1`; the report compares that host-verified policy, exposes all raw lock snapshots, and retains strict snapshot matching for legacy input. Sixteen report-tool tests pass. |
| 2026-08-15 | Phase 5 list and complex-layout replacement baselines | Five-iteration, per-method-cooled revision 3 batches passed the `0.15` stability gate. List scroll/mutation ViewCompose run-P50 CV values were `0.041/0.009` versus Compose `0.072/0.034`; complex-layout scroll/update values were `0.011/0.079` versus `0.037/0.082`. The owning performance specification records the accepted P50/P95 values. |
| 2026-08-15 | Phase 5 fixture-interaction workload audit | Diagnostics initially failed to return to its top anchor because the reverse fling began in the fixed header instead of the scrolling surface. Collection's original three-swipe workload produced run-P50 CV `0.755`; enlarging it while resolving targets inside measurement produced CV `0.712`. Moving Accessibility lookup to setup still exposed 3.6/7.2/14.7 ms run plateaus. Fixed 120 Hz and full ART compilation trials did not remove them. Perfetto showed stable RecyclerView and draw work but variable `dequeueBuffer` wait with FrameTimeline `Buffer Stuffing`, proving that zero-idle back-to-back gestures contaminated the workload. A fixed 500 ms inter-gesture settle removed the stuffing. |
| 2026-08-15 | Phase 5 diagnostics-theme and collection-stress revision 2 baselines | Five clean, per-method-cooled runs passed the `0.15` stability gate. Diagnostics long-fling, collection scroll, and collection mutation frame-CPU P50/P95 values were `3.067/7.336`, `3.357/6.288`, and `4.358/10.507` ms; their run-P50 CV values were `0.008`, `0.018`, and `0.018`. Every raw result records the scenario, revision, and explicit clock policy. |
| 2026-08-15 | Phase 5 workload-revision consistency gate | Corrected the Demo registry to list/complex revision 3 and shadow revision 2. Kotlin pins the accepted registry values, while the report-tool suite parses the registry and rejects cross-layer drift. The correction is metadata-only for isolated performance screens, so accepted revision 3 list/complex measurements remain valid. |
| 2026-08-15 | Phase 5 shadow-comparison revision 2 baselines | Eight independently cooled `Auto`/`ExactBitmap` methods passed the `0.15` stability gate. Shadow-list scroll/mutation ViewCompose run-P50 CV values were `0.052/0.023` versus Compose `0.044/0.117`; shadow-complex scroll/update values were `0.016/0.049` versus `0.046/0.044`. Mutation/update use eight complete action/reset cycles, and the owning performance specification records all paired P50/P95 values. |
| 2026-08-15 | Phase 5 navigation/design launch-settling audit | The remaining warm navigation and design-system methods did not apply the already-proven 5-second OEM launch-boost isolation. Their setup now settles outside measurement; design initial-build remains an unmodified cold-start workload. Navigation and both design-bundle scenario contracts advance from revision 2 to revision 3 before rebaseline. |
| 2026-08-15 | Phase 5 navigation revision 3 stability preflight | One push per iteration yielded stable 51-57 frame counts but frame-CPU run-P50 values of `9.779/7.545/4.044/8.708/9.637 ms` and CV `0.265`. The result is rejected. Revision 4 keeps push/pop separate and measures eight same-direction transitions per iteration; pop setup preloads the matching depth outside measurement. |
| 2026-08-15 | Phase 5 navigation revision 4 automation preflight | The first eight-push run failed before metric collection: repeated standard pushes to the same route left the event-only scenario state unchanged after the second transition. Revision 5 adds active stack depth to the state target and requires eight distinct transitions in the bilingual device contract test. |
| 2026-08-15 | Phase 5 navigation revision 5 thermal preflight | Eight pushes yielded 417-437 frames per run, but a roughly two-minute method crossed the consumer device's thermal boundary. Frame-CPU run-P50 shifted from `4.504/4.502/4.320 ms` to `8.356/8.614 ms` (CV `0.327`), so the result is rejected. Revision 6 uses four same-direction transitions, retaining about 200 frames per run while halving sustained load. |
| 2026-08-15 | Phase 5 navigation/design reference-device gate | Navigation revision 6 supplied 202-223 frames per run, but unlocked, profile-guided, and platform fixed-performance trials still produced CV `0.308`-`0.372` while ending at `NONE`/`LIGHT`. Read-only sampling proved OEM maximum-frequency plateaus changed without thermal-status changes; shell cannot clear ART profile data. A representative design revision 3 patch run also failed at CV `0.262`. Both remaining matrices now require a clock-controllable reference device; no result was accepted by repetition. |

## Decision history

| Date | Decision |
| --- | --- |
| 2026-08-14 | Define the Demo as benchmark plus framework verification, with automation before human guidance. |
| 2026-08-14 | Use independently launchable scenarios, not modules or chapter tabs, as the stable identity. |
| 2026-08-14 | Remove roadmap, planned-module, known-gap, static architecture-count, and placeholder About content from runtime UI. |
| 2026-08-14 | Add an app-owned Android resource-ID bridge before internationalizing text-dependent Macrobenchmarks. |
| 2026-08-14 | Require explicit workload revisions and a replacement baseline before benchmarking or implementing Runtime/Patch performance work. |
| 2026-08-14 | Permit Runtime/Patch source auditing and focused correctness work now, while keeping performance experiments blocked on this plan's replacement baseline. |
| 2026-08-14 | Freeze the replacement inventory before UI movement; widget scenarios use hyphenated wire IDs and paired performance engines remain workload dimensions. |
