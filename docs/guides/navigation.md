---
schema_version: 2
document_id: guide.navigation-production-host
doc_type: guide
owner:
  kind: capability
  id: navigation.host
version_lane: released
capability_ids:
  - navigation.destination-context
  - navigation.host
  - navigation.presentation-retention
  - navigation.scene-projection
artifact_ids:
  - viewcompose-navigation-android
  - viewcompose-navigation-core
sample_ids:
  - module.navigation-core-execution-plan
  - tutorial.navigation
task: Configure one production NavHost with restoration, system Back, and explicit failure handling.
success_checks:
  - Destination state survives host recreation and valid process-state restoration.
  - Programmatic and system Back operations observe the same committed stack.
  - Failed destination preparation preserves the previously visible destination.
  - Hidden presentation resources follow one explicit bounded retention policy without clearing entry state.
  - Destination content observes the nearest stable entry context without treating transition progress as lifecycle.
failure_checks:
  - A controller is attached to more than one active NavHost or receives commands while detached.
  - NavHost is mounted without a LocalViewModelStoreOwner boundary.
  - Route or graph changes silently reuse incompatible restored ownership state.
  - A queued command is treated as committed completion.
  - RetainAll is selected without application-specific memory and rebuild evidence.
  - Resource work is started from destination presentation state instead of AndroidX Lifecycle.
---

# Configure a production navigation host

Use this guide after completing the [navigation tutorial](../tutorials/navigation.md). It turns the
two-destination example into one host with explicit restoration, Back, ownership, and failure
policy. For the runtime reasons behind these rules, see the
[navigation architecture](../architecture/navigation.md). Exhaustive signatures and optional
motion, deep-link, multi-stack, graph-owner, and adaptive-pane APIs remain in the
[Navigation Android module manual](../modules/viewcompose-navigation-android/README.md).

## Choose one controller owner

Create the controller with `rememberNavHostController` in the same UI owner that mounts `NavHost`.
Do not cache it in a process singleton or attach it to two hosts. The remembered controller saves
the committed stack, entry and graph identities, route arguments, and destination-owned saved
state through the nearest ViewCompose saveable-state registry.

Mount `NavHost` below both `LocalLifecycleOwner` and `LocalViewModelStoreOwner`. Standard Activity
and Fragment `setUiContent` hosts do this automatically. A low-level `renderInto` integration must
provide both explicitly; `NavHost` intentionally refuses to create a private fallback store.

Use a stable `NavGraph` when routes need typed arguments, nested ownership, or deep links. Restore
fails closed when the current graph no longer accepts the saved route hierarchy. Treat that as a
safe restart at the configured start destination, not as a partially restored stack.

## Restore state and connect platform Back

Leave `systemBackEnabled = true` for the ordinary Activity or Fragment host. `NavHost` registers
with the nearest AndroidX Back dispatcher only while the controller can consume Back; at the active
root it follows the configured retained-stack history or delegates outward.

Call `popBackStack` for an in-UI Back action. System Back and predictive Back then use the same
transaction boundary. Predictive Back previews the previous destination without publishing the
candidate stack; cancellation restores the committed scene, while completion commits through the
same path as a programmatic pop.

## Keep route rendering exhaustive

Render every accepted route in the `NavHost` content block and reject unknown routes immediately.
The content block runs inside the destination's framework-owned lifecycle, ViewModelStore,
SavedStateRegistry, saveable-state namespace, and child render session. A second push of the same
route still creates a distinct entry owner unless the selected launch mode explicitly reuses it.

Destination and graph ViewModelStores come from one shared Lifecycle 2.11 scoped-owner provider.
They survive Activity configuration recreation when the parent store and remembered controller
state survive, but a permanent pop or graph removal clears the corresponding scope. Process
recreation restores state into new ViewModel instances rather than serializing live models.

Change `contentKey` only when destination content closes over a non-observable parent value.
Observable ViewCompose state invalidates the owning destination session directly. Changing the
controller, lifecycle owner, parent ViewModelStore owner, overlay factory, debug identity, or host
`key` changes ownership and therefore recreates the native host.

## Observe destination presentation without duplicating Lifecycle

Read `LocalNavDestinationContext.current` during destination DSL declaration when content needs to
distinguish hidden, visible, covered, interactive, transitioning, pane, or overlay roles. Capture
the returned context holder for a later callback; do not perform a global current-page lookup and
do not read the Local from an effect callback. A nested `NavHost` supplies its own nearest holder.

The holder's `entry` is stable for its retained lifetime. Its `presentation.value` may change when
the semantic scene changes and may therefore invalidate content that reads it. The holder remains
the same when `DisposeWhenHidden` releases and later rebuilds the native presentation. Permanent
removal stops presentation updates and drives the standard destination Lifecycle to `DESTROYED`.

Keep resource thresholds on standard AndroidX Lifecycle: use lifecycle effects or lifecycle-aware
Flow collection for cameras, sensors, players, network collection, and other active work. Use the
destination context for coarse presentation decisions only. It deliberately excludes per-frame
ordinary-transition and predictive-Back progress.

## Choose presentation retention deliberately

Keep the default `NavPresentationRetentionPolicy.DisposeWhenHidden` unless device evidence for a
specific destination shows that rebuilding its native View tree is unacceptable. The default
disposes a fully hidden `RenderSession`, View tree, effects, focus, accessibility state, and native
resources after transition settlement. It preserves the destination owner, ViewModels,
SavedStateRegistry, `rememberSaveable` values, route arguments, and graph identity. Reveal then
rebuilds the presentation transactionally before publishing the new scene.

Use `NavPresentationRetentionPolicy.Bounded(maxHiddenPresentations = n)` when an application needs
a measured cache across several recently hidden surfaces. The positive maximum counts only hidden
presentations; visible panes and transition participants are outside the bound. Use `RetainAll`
only as an explicit, measured opt-in. It is unbounded across deep and multiple stacks and therefore
is not a safe general default.

Changing the policy does not recreate `NavHost` or its entry owners. Tightening a policy evicts
excess hidden presentations immediately; relaxing it affects future presentations and does not
eagerly compose hidden stacks. Initial, configuration-restored, and process-restored attachment
materializes only the visible pane set.

## Keep one policy source in custom integrations

Normal applications should use `NavHost`; it already executes the Core plan. A custom platform
host may call `NavExecutionReducer.settled`, `transition`, or `predictivePreview` for its matching
event. These are three ergonomic inputs into one implementation, not three lifecycle systems. Each
returns the same `NavExecutionPlan`, and `reconcile` reapplies host-lifecycle or retention changes
to an existing semantic phase.

Execute the plan as a whole. Prepare and refresh every required presentation before committing its
candidate stack; publish the planned scene, lifecycle, interaction, and Back ownership together;
then perform planned eviction or terminal cleanup at its stated boundary. Do not derive lifecycle
from View attachment, run a second retention cache, or calculate Back eligibility separately. The
compiled Core sample shows plan construction; Android's built-in executor remains internal because
platform mutation is a host responsibility, not an application DSL API.

## Handle command outcomes

Every controller command returns `NavResult`:

| Result | Application action |
| --- | --- |
| `Committed` | Read its stack snapshot or let observable `navigationState` update the UI. |
| `NoChange` | Keep the current UI; the command was valid but already effective. |
| `Queued` | Wait for `navigationState`; this is accepted work, not completion. |
| `Failed` | Report its structured `NavFailure` and retain the committed stack it carries. |

Pass `onFailure` to `NavHost` when the application has logging, fallback, or test policy. Without a
handler, the host raises `NavHostException`. Do not catch an arbitrary destination failure and
mutate a second application-owned back stack: failures before stack commit already preserve the
old stack and visible page, while failures after commit identify that boundary with
`NavFailure.stackCommitted`.

## Verify the task

Run the compiled tutorial and the Navigation Android tests:

```bash
./gradlew :samples:tutorials:assembleDebug :viewcompose-navigation-android:testDebugUnitTest
```

Then verify one real host journey:

1. Push two destinations and change saveable state in each one.
2. Press the in-UI Back action and system Back; both must expose the same previous entry.
3. Recreate the Activity and confirm the current route, entry identity, saveable values, and
   destination ViewModel instance remain.
4. On Android 13 or newer, cancel and then complete an edge Back gesture. Cancellation must leave
   the stack unchanged; completion must pop exactly once.
5. Inject a destination-render failure through the application's test seam. The previous page must
   remain visible when `stackCommitted` is false, and `onFailure` must receive the exact phase.
6. Build a deep stack under the selected presentation policy. Verify the native presentation count,
   then reveal an evicted page and confirm owner, ViewModel, and saveable-state identity are stable.

The task is complete only when all six checks pass. A detached-command exception, changed entry
identity after ordinary Activity recreation, duplicate pop, visible candidate after failed render,
premature ViewModel clear, unbounded hidden Views without explicit policy, or treating `Queued` as
completion is a failed configuration.

## Choose the next focused task

- Use strict deep-link declarations and inspect `NavDeepLinkResult` before accepting external URIs.
- Use `NavStackConfiguration` for independently retained tab stacks and derive tab selection from
  `navigationState.activeStackId`.
- Use `NavPanePolicy.Adaptive` only when multiple visible destinations are intended to share the
  validated pane scene.
- Use `ProvideNavGraphOwner` only inside active destination content when state must belong to a
  graph rather than one leaf destination.

These features extend the same controller and transaction model; they are not alternative
navigation paths.
