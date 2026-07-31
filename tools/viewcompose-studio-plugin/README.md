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
- cancellable Gradle-wrapper discovery and isolated single-preview rendering;
- an in-window selector for every declared preview configuration;
- automatic refresh when the selected preview source is saved;
- clickable structured diagnostics that navigate back to their source location;
- bounded render-snapshot inspection with VNode structure, patch, skip, and recomposition details;
- bounded PNG loading plus structured render diagnostics and Gradle failure output;
- an explicit AI-261 minimum build and exact local-build verification.

The current alpha deterministically prefers the `debug` descriptor catalog. Initial rendering uses
the first declared configuration; the selected configuration is then retained across source-save
refreshes when it still exists.
