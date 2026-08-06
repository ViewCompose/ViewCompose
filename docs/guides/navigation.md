# System Navigation

## 1. Scope

ViewCompose navigation treats an Android `Activity`/`Window` as the root platform host only.
Destinations are framework-owned page sessions and do not map to an `Activity` or `Fragment`.

The navigation subsystem is split into two layers:

1. `viewcompose-navigation-core`
   - pure Kotlin/JVM
   - immutable routes and back-stack snapshots
   - two-phase navigation transactions
   - page lifecycle planning
   - persistence contracts
2. `viewcompose-navigation-android`
   - destination `RenderSession` ownership
   - AndroidX `LifecycleOwner`, `ViewModelStoreOwner`, and SavedState adapters
   - back dispatch, transitions, and destination container Views

The Android integration remains isolated from existing application entry points during incubation.
The public surface delegates to the same coordinator used by the internal transaction tests.

Current feature-branch status:

- Stage 1 navigation kernel: complete
- Stage 2 lifecycle kernel: complete
- Stage 3 Android page owner cluster: complete
- Stage 3 destination `RenderSession`: complete
- Stage 4 transactional coordinator: complete
- Stage 4 transition retention protocol: complete
- Stage 4 native View transition driver: complete
- Stage 4 public `NavHost`: complete
- Stage 5 complete host restoration: complete
- Stage 5 platform back and predictive back: complete
- Stage 6 Android 13 real-device back baseline: complete
- Stage 6 Android 14+ platform gesture-progress validation: complete
- Stage 6 P0 process-death, lifecycle-race, and resource-release certification: complete
- Stage 7A nested graph kernel and transactional leaf resolution: complete
- Stage 7B graph-scoped lifecycle and state ownership: complete
- Stage 8 independently retained tab back stacks: complete
- Stage 9 strict URI deep links and Android `VIEW` intents: complete
- Stage 10 adaptive native View panes: complete

## 2. P0 delivery plan

### Stage 1: navigation kernel

- stable `NavEntryId`
- typed, persistence-safe route values
- non-empty immutable back stack
- `push`, `pop`, `replaceTop`, and `reset`
- single-top behavior
- prepare/commit/rollback transaction protocol
- deterministic stack snapshot restoration

### Stage 2: page lifecycle kernel

- host lifecycle caps destination lifecycle
- every interactive destination in the settled pane scene is `RESUMED`
- visible transition participants remain at least `STARTED`
- retained hidden destinations remain `CREATED`
- removed destinations become `DESTROYED`
- downward transitions are applied before upward transitions

### Stage 3: Android page ownership

- one child `RenderSession` per `NavEntry`
- one child saveable-state namespace per entry
- one `ViewModelStore` per entry
- one AndroidX `LifecycleRegistry` per entry
- captured CompositionLocal snapshot per entry
- entry resources survive hiding and are cleared only after permanent removal

Destination sessions are rendered into an unattached candidate container first. A successful
candidate can then be staged hidden, committed, and presented; a failed render or explicit rollback
disposes its composition and destroys its owner without publishing the page. Committed sessions
refresh both their captured CompositionLocal snapshot and latest content closure without replacing
the entry owner or container.

### Stage 4: transactional `NavHost`

- render a candidate destination before publishing the new stack
- commit stack and lifecycle state only after successful candidate render
- roll back the candidate session when rendering fails
- retain outgoing and incoming sessions during transitions
- serialize re-entrant navigation commands on the main thread

The internal `TransactionalNavHostCoordinator` now owns the settled-state transaction boundary.
It attaches the initial stack, executes `push/pop/replaceTop/reset`, refreshes a page before it is
revealed by `pop`, applies host lifecycle caps, and serializes navigation requested while another
page is rendering. A destination render failure rolls back the pure back-stack transaction and
discards commands emitted by that failed candidate.

The coordinator also owns transition retention. After the stack commits, it publishes immutable
before/after pane scenes plus retained entries, their visibility union, and layer order. Every
destination in the committed after-scene is interactive and `RESUMED`; transition-only destinations
remain `STARTED`. Permanently removed sessions are destroyed only when the transition reaches a
terminal result.

Transition drivers are cancellable policy adapters. Completion settles the committed target;
explicit cancellation also settles that target and never rolls the stack back. A newer navigation
command redirects the active transition by cancelling its visual work, settling its committed
target, and then preparing the next transaction. Stale completion callbacks are ignored by
transition ID. Host destruction cancels visual work and destroys every retained page immediately.

The public `NavHost` mounts this coordinator through the existing transactional `AndroidView`
interop node. Its configuration is staged during parent rendering and applied only by the node's
commit effect. Parent render rollback therefore cannot attach a controller, publish a destination,
or leak an entry owner. Removing the node or destroying its lifecycle owner tears down every child
session and unbinds the controller.

`NavHostController` is the only application-facing mutation handle. It can bind to exactly one host,
rejects commands while detached, and maps coordinator results to public `Committed`, `NoChange`,
`Queued`, and `Failed` results without exposing transition internals. The same controller can mount a
new host after release while retaining its back stack and destination saveable state.

```kotlin
val navController = rememberNavHostController(
    startDestination = NavRoute("home"),
)

NavHost(
    controller = navController,
) { entry ->
    when (entry.route.name) {
        "home" -> HomePage()
        "details" -> DetailsPage(entry.route)
    }
}
```

The Android View driver uses cancellable property animation after commit. Forward/back motion honors
the host layout direction, supports slide, fade-only, and disabled policies, and resets all mutated
View properties on completion or cancellation. An unlaid-out or detached host settles immediately
so invisible animation can never retain removed page resources indefinitely.

### Stage 5: restoration and platform back

- save and restore the back stack and each entry state across host recreation/process death: complete
- connect Android back dispatch without making Activity/Fragment a destination owner: complete
- define root-pop delegation to the platform host: complete
- preview, cancel, and commit Predictive Back through the same transaction boundary: complete

`rememberNavHostController` now registers one versioned, Bundle-safe state envelope in the current
ViewCompose saveable-state registry. Saving captures the complete committed stack set, active stack,
stack-selection history, stable entry and graph-instance IDs, every typed route argument, and the
Android saved-state bundle for each retained leaf or graph owner. Owner bundles include both
`rememberSaveable` providers and AndroidX `SavedStateHandle` providers.

Restoration creates the pure back-stack controller directly from the saved snapshot before `NavHost`
attaches. Each destination owner then consumes only the bundle matching its restored entry ID. State
for an outgoing page retained solely by a running transition is excluded when that page is no longer
in the committed stack. Releasing and remounting the same controller also preserves destination state
without requiring process recreation.

The format-4 codec rejects unknown formats, empty or duplicate stacks, mismatched stack
configurations, malformed routes, and invalid typed arguments. Invalid restored data falls back
atomically to every configured stack root instead of partially publishing damaged state.

The host-side process-death runner proves the complete Android path instead of simulating it with
Activity recreation. It builds two retained tab stacks with two entries each, records the active
stack and selection history, all leaf-entry and graph-instance IDs, leaf and graph
`rememberSaveable`, `SavedStateHandle`, and graph-route arguments, moves the task to the background,
kills only the application process, and brings the existing task forward. Certification requires a
new PID and an exact match of the complete pre-kill and restored stack set:

```bash
ANDROID_SERIAL=<device> tools/navigation/validate_android_process_death.sh
```

The initial force-stop in this runner only establishes a clean test process. The restoration step
uses a system-style background process kill because Android intentionally does not restore a task
after a user force-stop.

`NavHost` registers one lifecycle-aware callback with the nearest View-tree
`OnBackPressedDispatcherOwner`. The callback is enabled only when `systemBackEnabled` is true, the
host is attached, and the controller can produce a system-Back command. Back pops the active stack
when it contains more than its root. At a root, `NavRootBackBehavior.PreviousStack` returns to the
most recently selected stack; otherwise dispatch continues to an enclosing navigation host, another
application callback, or the platform fallback. Setting `systemBackEnabled = false` opts the host
out without changing any stack.

Ordinary Back executes a transactional `Pop` or `PopStackHistory` command from the same controller.
Predictive Back adds a preview phase before that command:

1. gesture start exposes the current top and either its previous destination or the previous stack's
   top without changing the committed stack set;
2. every destination in the committed before-scene remains interactive and `RESUMED`, while
   destinations visible only in the after-scene remain `STARTED`;
3. progress updates only the native View transition driver;
4. cancellation resets View properties, visibility, and lifecycle to the committed snapshot;
5. completion refreshes the revealed destination, commits the prepared Back command, and continues
   the remaining motion through the normal transition-retention protocol.

A programmatic navigation command redirects an active preview before preparing its own transaction.
Host detachment, callback disablement, and host destruction cancel the preview and restore a settled
scene. This keeps platform Back, gesture Back, and application navigation on one stack and lifecycle
ownership model.

### Stage 6: device validation and P0 merge gate

The debug application contains an isolated `NavigationBackTestActivity` and connected-device suite.
The Android 13/API 33 compatibility suite validates:

- real system Back pops the framework stack and delegates at the root;
- predictive progress updates, cancellation, and commit drive real native destination Views through
  AndroidX's dispatcher test surface;
- programmatic navigation redirects an active preview before the next system Back;
- `systemBackEnabled` changes callback participation without replacing the stack;
- Activity recreation restores stable entry IDs before system Back;
- 30 consecutive push/immediate-Back-dispatch rounds preserve the transaction and ownership
  invariants;
- Activity stop/resume and recreation during active transitions preserve the committed stack and
  release redirected transition work.

The connected run also validates that a `NavHost` can be mounted normally from `Activity.onCreate`.
At that point AndroidX still reports the host as `INITIALIZED`; destination sessions now remain
`INITIALIZED` until the Activity advances, instead of requiring the application to delay first
content until `CREATED`.

API 33 does not expose real platform predictive-gesture progress callbacks. The AndroidX dispatcher
surface therefore remains the compatibility baseline for that version. The original Samsung
SM-G991B device established this baseline; the expanded suite completed 7/7 on the Android 13
`ViewCompose_API_33` AVD, which also passed the host-side real process-death restoration runner.

The complete platform path is certified separately on a Pixel 9a Android Emulator running Android
15/API 35 in gesture-navigation mode. The API 34+ cases sample the destination Views on every frame
and assert that:

- a real OS edge gesture exposes both destinations and continuously updates translation and alpha;
- reversing the same touch stream back to the edge cancels, restores settled View properties, and
  preserves `[home, details]`;
- completing the OS edge gesture removes `details`, presents `home`, and leaves no navigation
  failure.

Android's `input` command does not provide a repeatable predictive-gesture stream across emulator
and system-image versions. The host runner therefore uses the emulator's authenticated
hardware-touch channel for both commit and cancellation, waits for the instrumented Activity to
enter its sampling window, and runs the two platform-gesture cases in isolated instrumentation
sessions:

```bash
tools/navigation/validate_android_predictive_back.sh
```

The runner requires a single API 34+ Android Emulator with gesture navigation enabled. It
temporarily enables the system predictive-back animation switch and restores its original value on
every exit path. A regular `connectedDebugAndroidTest` run skips the two host-assisted edge-gesture
cases while retaining the deterministic AndroidX progress/commit/cancel coverage and ordinary
system-Back coverage. The script above is the real platform-gesture certification command and
completed 2/2 on the Android 15 Pixel 9a AVD. The same AVD also passed the host-side real
process-death restoration runner.

The JVM/Robolectric suite additionally runs 100 lifecycle/transaction race rounds and verifies that
stale transition completions cannot change the settled stack. Public-host integration tests retain
the actual destination owners, ViewModels, saved-state providers, and container Views so a pop or
host disposal must destroy/clear/detach the old resources; remounting may restore committed state
only through fresh resource instances.

Every navigation change must pass this P0 merge gate:

1. the complete `viewcompose-navigation-android` unit suite;
2. API 33 compatibility Back/lifecycle tests and real process-death restoration;
3. API 35 platform predictive-gesture tests and real process-death restoration;
4. the repository-wide quick checks and connected device suite through `qaFull`.

Reference commands:

```bash
./gradlew :viewcompose-navigation-android:testDebugUnitTest --no-configuration-cache

ANDROID_SERIAL=<api33-device> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.NavigationBackDeviceTest \
  --no-configuration-cache
ANDROID_SERIAL=<api33-device> tools/navigation/validate_android_process_death.sh

ANDROID_SERIAL=<api35-emulator> tools/navigation/validate_android_predictive_back.sh
ANDROID_SERIAL=<api35-emulator> tools/navigation/validate_android_process_death.sh
ANDROID_SERIAL=<api35-emulator> ./gradlew qaFull --no-configuration-cache
```

## 3. P1 delivery plan

P1 continues to use the P0 transaction, page ownership, restoration, and platform Back paths. A
graph, tab, deep link, or adaptive placement policy may select a destination, but none of them may
publish a second navigation state outside `NavBackStackController`.

The implementation order is:

1. nested graphs and graph-scoped ownership;
2. multiple retained tab back stacks — complete;
3. URI deep-link matching through the graph resolver — complete;
4. adaptive multi-pane placement over the same committed entries — complete.

### Stage 7A: nested graph kernel

The pure core now exposes an immutable `navGraph` DSL. Graph and destination route names are globally
unique, every graph start destination must be a direct child, and entering a graph route recursively
resolves to its leaf start destination before a transaction allocates or publishes an entry.
Arguments supplied to the graph route override defaults declared by each nested start route.

Every committed `NavEntry` records its complete `graphEntries`. Each `NavGraphEntry` contains the
graph route, typed graph arguments, and a stable instance ID; `graphHierarchy` remains available as
the route-only projection. Direct navigation between leaves reuses the common graph-instance prefix.
Explicitly entering a graph route allocates a fresh instance for that graph and its descendants, and
`reset` creates a wholly fresh graph path. `SingleTop`, replace, reset, rollback, and transition
retention compare the resolved leaf plus its graph path.

The saved-state envelope first introduced graph ownership in format version 3 and is now hard-cut to
format version 4 for complete retained stack sets. It persists graph instance IDs, routes, and
arguments with each leaf route. A graph-backed controller restores only when every saved graph path
is valid and shared instance IDs describe the same graph entry; changed, damaged, or colliding data
falls back atomically to the configured stack roots.

```kotlin
val graph = navGraph(
    route = "app",
    startDestination = NavRoute("home"),
) {
    destination("home")
    navigation(
        route = "account",
        startDestination = NavRoute("profile"),
    ) {
        destination("profile")
        destination("security")
    }
}

val navController = rememberNavHostController(graph)
navController.navigate(NavRoute("account"))
```

### Stage 7B: graph-scoped Android ownership

Every live `NavGraphEntry` now owns one stable `NavGraphOwner`, which implements AndroidX
`LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` and exposes its own
ViewCompose saveable-state namespace. Sibling leaves in the same graph instance therefore share
graph-scoped ViewModels and state without sharing their leaf owners. Popping the last referencing
leaf, resetting the stack, or destroying the host releases the graph owner exactly once.

`LocalNavGraphOwnerScope` exposes the graph owners belonging to the rendered entry. Applications can
look up an owner by graph route and use `ProvideNavGraphOwner` to install its lifecycle, ViewModel,
saved-state, and saveable-state locals for a subtree:

```kotlin
NavHost(controller = navController) { entry ->
    val accountOwner = LocalNavGraphOwnerScope.current?.get("account")
    if (accountOwner == null) {
        HomePage()
    } else {
        ProvideNavGraphOwner("account") {
            AccountPage(entry.route)
        }
    }
}
```

Preparing a candidate creates only graph owners missing from the committed stack. A render or setup
failure destroys only those candidate owners, while reused committed graph owners keep their
lifecycle and state. After commit, lifecycle reconciliation starts parent graphs before children and
destroys leaf/child owners before parents. Visible transition graph paths remain at least `STARTED`;
hidden retained graphs remain `CREATED`.

The JVM and public-host suites verify sibling sharing, release boundaries, lifecycle ordering,
rollback cleanup, graph `SavedStateHandle` recreation, and full host/controller restoration. The
real-device process-death runner additionally verifies the restored nested graph instance ID,
graph-scoped `rememberSaveable`, graph-scoped `SavedStateHandle`, and graph-route argument.

### Stage 8: independently retained tab back stacks

`NavStackConfiguration` declares stable stack IDs, one start route per stack, the initial selection,
and root-Back policy. The controller owns all stacks in one immutable `NavStackSetSnapshot` and
allows only one prepared transaction across the whole set. A route mutation always targets the
active stack; `selectStack` switches atomically without rebuilding another controller.

```kotlin
val homeStack = NavStackId("home")
val searchStack = NavStackId("search")
val tabs = NavStackConfiguration(
    initialStackId = homeStack,
    stacks = listOf(
        NavStackSpec(homeStack, NavRoute("home")),
        NavStackSpec(searchStack, NavRoute("search")),
    ),
    rootBackBehavior = NavRootBackBehavior.PreviousStack,
)
val navController = rememberNavHostController(
    stackConfiguration = tabs,
    graph = graph,
)

val selectedStack = navController.navigationState.value.activeStackId
navController.selectStack(searchStack)
navController.selectStack(
    stackId = homeStack,
    selectionMode = NavStackSelectionMode.PopToRoot,
)
```

Each retained entry and graph owner remains attached to the same host. The active stack's resolved
pane scene is visible, interactive, and `RESUMED`; every inactive stack is hidden and capped at
`CREATED`. The default single-pane policy resolves only the top. Selecting a stack in `Preserve`
mode resumes it exactly where it was left. Reselecting with `PopToRoot` destroys only entries above
that stack's root and does not alter other stacks.

Selection history is stable, excludes the active stack, and is saved with the complete stack set.
With `PreviousStack`, system and predictive Back at a stack root reveal and commit the newest
history target; once history is empty, Back delegates to the enclosing host. A failed destination
render, failed stack switch, or cancelled predictive preview preserves the previously committed
selection, histories, sessions, and owners.

The pure-core, Robolectric/public-host, and Android process-death suites cover stack isolation,
reselection, transaction rollback, inactive lifecycle ownership, ordinary and predictive Back, and
format-4 restoration of all stack and owner state.

### Stage 9: strict graph deep links

`NavDeepLink` registers allowlisted URI patterns on graphs or destinations. Matching is strict:
scheme, host, optional port, path segments, and query keys must match the registered pattern.
Placeholders occupy a complete path segment or query value and decode into declared
`Text/Int/Long/Boolean/Float/Double` route arguments. Malformed encoding, fragments, user info,
duplicate query values, invalid typed values, ambiguous patterns, and attempts to fall through from
a more-specific failed pattern to a broader pattern are rejected.

`NavCommand.OpenDeepLink` applies route mutation, target-stack selection, and selection-history
updates in one prepare/commit/rollback transaction. `NavHostController.navigateDeepLink` accepts a
string, Android `Uri`, or Android `ACTION_VIEW` `Intent`; all three use the same graph resolver and
transaction boundary. Render failure therefore restores the complete previous stack set rather
than publishing a partially selected tab.

The pure-core randomized model includes deep-link operations in 128,000 deterministic transactions.
The Android certification launches a real browsable intent, rejects a malformed typed argument
without changing state, switches stacks through another deep link, and returns through system Back:

```bash
ANDROID_SERIAL=<device> tools/navigation/validate_android_deep_links.sh
```

### Stage 10: adaptive native View panes

`NavPaneStrategy` resolves an immutable `NavPaneScene` over the active committed stack. Scenes use
contiguous `Primary`, `Secondary`, and `Tertiary` roles, may reference only entries in that stack,
and must include its top. `NavPaneStrategies.Single` preserves classic behavior;
`NavPaneStrategies.BackStack` exposes the newest one to three entries.

Android `NavPanePolicy` converts native host width and density into a pane count using a minimum pane
width, maximum count, and spacing. `NavHost` defaults to `NavPanePolicy.Single`; applications opt in
with `NavPanePolicy.Adaptive` or a custom policy:

```kotlin
NavHost(
    controller = navController,
    panePolicy = NavPanePolicy(
        minPaneWidthDp = 320f,
        maxPaneCount = 3,
        paneSpacingDp = 8f,
    ),
) { entry ->
    Destination(entry)
}
```

Width changes republish only placement and lifecycle effects over the same committed entries.
They do not create another stack, replace destination containers, or recreate lifecycle, ViewModel,
SavedState, or saveable-state owners. Every settled pane is interactive and `RESUMED`; retained
entries outside the pane scene are hidden and `CREATED`.

Navigation and predictive Back carry both before/after pane scenes. Their visibility union remains
attached through the terminal transition, while the native View driver animates only entries that
actually enter or leave; shared panes are never treated as outgoing pages. The host lays out equal
native panes with exact pixel remainder distribution, spacing, and start-edge mirroring under RTL.

The Android 15 Pixel 9a certification rotates one committed three-entry stack between landscape
three-pane and portrait single-pane layouts, verifies native bounds and lifecycle changes, rotates
back without replacing owners, and commits Back into a two-pane scene:

```bash
ANDROID_SERIAL=<api35-emulator> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.NavigationAdaptivePaneDeviceTest \
  --no-configuration-cache
```

### System navigation acceptance demo

The Demo catalog contains a dedicated `系统导航验收` module backed by the production `NavHost`.
The Activity only owns the Android window and forwards `ACTION_VIEW`; every destination, back stack,
lifecycle, ViewModel, SavedState registry, transition, and adaptive pane remains framework-owned.

The page exposes the current route and arguments, entry/graph owner IDs, lifecycle, all three tab
stacks, selection history, last transaction result, and external deep-link result. Its controls cover:

- `Push`, `SingleTop`, `Pop`, `ReplaceTop`, and `Reset`;
- independently retained Home, Discover, and Account stacks plus `PopToRoot`;
- `PreviousStack` system Back and Android 13+ predictive Back;
- nested checkout/account graphs and graph-scoped SavedState;
- entry-scoped `rememberSaveable`, `SavedStateHandle`, and `ViewModel`;
- strict deep-link accept, typed-argument rejection, no-match, reset/push/replace modes;
- live transition, system-Back, one-to-three-pane policy switches, and a one-tap three-pane seed;
- Activity recreation, rotation, and owner identity preservation.

For an external intent, install the debug app and run:

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -d 'viewcompose://demo/account/42' \
  com.gzq.uiframework
```

Recommended manual journey:

1. Increment every state counter, push a page in each bottom tab, and return to verify independent
   stacks and retained owner IDs.
2. From a tab root, press system Back to verify selection-history traversal; on Android 13+, slowly
   drag from the edge once to cancel and once to commit predictive Back.
3. In Discover, run the valid, invalid typed-argument, and unregistered deep links. Invalid/no-match
   operations must leave every entry ID unchanged.
4. Launch the external account URI and verify the Account stack becomes active with `userId=42`.
5. Rotate after changing the entry and graph counters. Saveable values and owner IDs must survive;
   the ViewModel instance ID may change while its `SavedStateHandle`-backed counter is restored.
6. Tap `准备三窗格样例`, keep adaptive panes enabled, and rotate to landscape. The newest three
   native destination Views must appear side-by-side without changing their entry/graph owner IDs.

The device smoke suite for the same journeys is:

```bash
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.SystemNavigationDemoDeviceTest \
  --no-configuration-cache
```

## 4. Transaction invariants

Navigation is a two-phase operation:

1. `prepare` creates an immutable candidate snapshot without publishing it.
2. The Android integration prepares/renders any required page sessions.
3. `commit` publishes the candidate snapshot.
4. `rollback` abandons it and preserves the previous snapshot.

Only one prepared transaction may exist for a controller. A prepared transaction must be committed
or rolled back before another command can be prepared.

Entry IDs allocated by an abandoned transaction are never reused. This prevents stale SavedState,
ViewModel, overlay, or result ownership from being attached to a later page.

Failures before back-stack commit preserve the old stack, visible page, and lifecycle ownership.
An unexpected failure while applying effects after stack commit marks the coordinator `Failed`;
further commands are rejected until the host is destroyed instead of continuing on partial state.

A visual transition begins only after the candidate page and back-stack transaction commit. Visual
cancellation cannot undo application state: all terminal paths converge on the committed target
snapshot. Removed entry resources remain addressable during the transition and are cleared in
top-first order at its terminal boundary. Only the active transition ID may complete; callbacks from
redirected or destroyed transitions have no effect.

## 5. Lifecycle invariants

The destination lifecycle is framework-owned but capped by the root host lifecycle:

| Destination role | Desired state |
| --- | --- |
| graph on an interactive pane path | `RESUMED` |
| graph on a visible transition path | `STARTED` |
| retained hidden graph | `CREATED` |
| interactive settled-pane destination | `RESUMED` |
| visible non-interactive/transition destination | `STARTED` |
| retained hidden destination | `CREATED` |
| prepared but not committed destination | `INITIALIZED` while the host is `INITIALIZED`; otherwise `CREATED` |
| permanently removed destination | `DESTROYED` |

When ownership changes, current interactive destinations are downgraded before new destinations are
upgraded. Multiple leaf destinations may be `RESUMED` only when they belong to the same validated
settled pane scene. Every graph on an interactive leaf's path may be `RESUMED`; parent graphs
advance before their children.

Mounting during `Activity.onCreate` is valid while the platform lifecycle is still `INITIALIZED`.
Host `DESTROYED` destroys every leaf and graph owner. A destroyed leaf or graph instance ID cannot
be reintroduced.

## 6. Remaining scope

The planned P1 system-navigation capability set is implemented. Before the feature branch is merged,
the remaining work is stabilization rather than another navigation model:

- repeat the complete API 33/API 35 device matrix after the final branch rebase;
- collect release-mode navigation transition and adaptive relayout benchmarks;
- keep the branch isolated until the full repository and device soak gates remain consistently green.

The following remains an explicit non-goal:

- compiler-generated route serialization

These features must build on the same transaction and ownership contracts instead of adding a
parallel navigation path.
