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

`build-manifest.json` is the resolved input contract for the isolated render worker. It keeps local,
project-module, and external-AAR resources/assets separate, records every resource package name,
and resolves the compile SDK from the Android boot classpath so Layoutlib can recreate the variant
without consulting Gradle internals.
`descriptors.json` contains valid previews plus structured discovery diagnostics. Application
classes are parsed as bytecode and are never loaded into the Gradle daemon.

Configure the standalone worker host and Android runner distributions:

```kotlin
dependencies {
    add(
        "viewComposePreviewWorkerHost",
        project(":viewcompose-preview-worker-host"),
    )
    add(
        "viewComposePreviewRunner",
        project(":viewcompose-preview-runner"),
    )
}
```

Render one preview with JDK 17 or newer:

```text
./gradlew :app:renderDebugViewComposePreview \
    --preview-id com.example.SamplePreview \
    --variant-id default
```

`--variant-id` may be omitted when the preview declares exactly one variant. Pass `--rerender` to
ignore an existing successful result. The task launches Layoutlib in a standalone JVM and writes
the request, response, PNG, and render-tree snapshot under:

```text
app/build/viewcompose-preview/debug/render-cache/
└── <build-fingerprint>/<preview-id>/<variant-id>/
```

The cache key includes the exported build fingerprint, preview id, and configuration variant id.
The task and worker both reject stale or mismatched manifests before rendering.

After one complete render has established a validated manifest and toolchain contract, IDE tooling
can refresh a known preview after a Kotlin/Java source save with:

```text
./gradlew :app:refreshDebugViewComposePreview \
    -PviewComposePreviewId=com.example.SamplePreview \
    -PviewComposePreviewVariantId=default
```

This task still runs the Android variant compiler, then rescans the current project bytecode. It
does not resolve the complete preview resource/dependency graph or rerun full discovery. Missing or
incompatible baseline data, a renamed preview, and signature/configuration changes emit the
`VIEWCOMPOSE_FAST_REFRESH_FALLBACK` marker so callers can safely retry through full discovery.
Resolved worker/Layoutlib inputs and content-addressed archive materialization are reused only while
their validated files and runtime fingerprint remain unchanged.
