# ViewCompose Preview Gradle Plugin

This module is the build-system boundary for static previews. It is pinned to the repository's
Android Gradle Plugin version and uses public Variant/Artifact APIs only.

Apply it to an Android application or library module:

```kotlin
plugins {
    id("com.android.application")
    id("com.viewcompose.preview")
}
```

For a `debug` variant, run:

```text
./gradlew :app:discoverDebugViewComposePreviews
```

The task compiles the variant and writes:

```text
app/build/viewcompose-preview/debug/
├── build-manifest.json
└── descriptors.json
```

`build-manifest.json` is the resolved input contract for the isolated render worker.
`descriptors.json` contains valid previews plus structured discovery diagnostics. Application
classes are parsed as bytecode and are never loaded into the Gradle daemon.
