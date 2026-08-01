# ViewCompose Android Studio Preview Plugin

This is an independent IntelliJ Platform build for the ViewCompose static-preview IDE integration.
It is intentionally excluded from the repository root Gradle build, so `qaQuick` never downloads or
compiles Android Studio.

The initial target is pinned exactly to:

```text
AI-261.25134.95.2612.15914620
```

By default, the build searches:

```text
~/Applications/Android Studio.app
/Applications/Android Studio.app
```

Override the location with `ANDROID_STUDIO_HOME` or
`-PviewComposeAndroidStudioPath=/absolute/path/to/Android Studio.app`.

Use Android Studio's bundled JBR 21 to verify and package the plugin:

```text
export ANDROID_STUDIO_HOME="$HOME/Applications/Android Studio.app"
export JAVA_HOME="$ANDROID_STUDIO_HOME/Contents/jbr/Contents/Home"

./gradlew verifyTargetStudio
./gradlew test verifyPluginProjectConfiguration buildPlugin
```

The installable ZIP is written to `build/distributions/`. The plugin currently provides:

- bounded ViewCompose project detection without loading Gradle or Android plugin internals;
- a lazily initialized, dockable `ViewCompose Preview` tool window;
- Kotlin gutter markers for direct and source meta-annotated ViewCompose preview functions;
- project-scoped source selection that opens the matching symbol in the preview tool window;
- a project-scoped Gradle Tooling API connection with cancellation, avoiding a new wrapper client
  JVM for every discovery/render operation while keeping Layoutlib in a bounded external worker;
- controlled Layoutlib worker reuse across compatible refreshes, with fresh application class
  loaders, content-addressed invalidation, failure/capacity/memory/idle retirement, isolated-process
  fallback, and an opt-in cold-versus-warm equivalence gate;
- a globally bounded Studio-owned disk cache that restores previews before invoking Gradle
  (64 detailed entries, 30 days, and 256 MiB across projects, with least-recently-used cleanup);
- a lightweight all-previews gallery that immediately shows placeholders, prioritizes the visible
  viewport, then fills the remainder in a second bounded batch; cached thumbnails decode lazily,
  and the separate thumbnail tier retains up to 1024 entries for 30 days within 128 MiB;
- an in-window selector for every declared preview configuration;
- manifest-derived automatic refresh for the selected module and its real project dependencies;
- a source-save fast path for the selected Kotlin/Java preview file: compile the current variant,
  rescan current project bytecode, and reuse the last validated resource/toolchain contract;
  signature, descriptor, or baseline changes fail closed and fall back to complete discovery;
- no save-triggered compilation while the preview tool window is collapsed or hidden; changed
  saved-input fingerprints are retained and refreshed once when the panel becomes visible again;
- separate incremental-refresh and full-update title actions; full update reruns the complete
  compile/discovery/render task graph and rediscovers if the known entry identity changed; both
  actions save the selected source first and suppress the duplicate save-triggered refresh;
- low-memory release of gallery thumbnails and high-resolution quick-look images;
- phase timings for Gradle, Layoutlib setup/teardown, mount/layout, artifact export, and Studio
  decoding, including a distinct `gradle-fast-refresh` phase, exposed in the rendered-header
  tooltip and structured IDE log;
- clickable structured diagnostics that navigate back to their source location;
- bounded render-snapshot inspection with VNode structure, patch, skip, and recomposition details;
- a native Android View tree with final measured sizes and root-relative layout coordinates;
- an optional color-coded layout-bound overlay drawn directly over the static preview;
- adaptive trackpad axis locking that filters diagonal noise but switches horizontal/vertical
  intent without waiting for inertial-scroll or scrollbar fade timers;
- bounded PNG loading plus structured render diagnostics and Gradle failure output;
- an explicit AI-261 minimum build and exact local-build verification.

The current alpha deterministically prefers the `debug` descriptor catalog. Initial rendering uses
the first declared configuration; the selected configuration is then retained across source-save
refreshes when it still exists.
