---
schema_version: 2
document_id: module.viewcompose-preview-gradle-plugin
doc_type: module
owner:
  kind: module
  id: viewcompose-preview-gradle-plugin
version_lane: released
capability_ids:
  - preview.gradle
artifact_ids:
  - viewcompose-preview-gradle-plugin
sample_ids:
  - module.preview-gradle-apply
  - tooling.preview-native-install
coordinate: com.viewcompose:viewcompose-preview-gradle-plugin:0.1.0-alpha03
minimal_usage_sample_id: module.preview-gradle-apply
---

# Preview Gradle Plugin

`viewcompose-preview-gradle-plugin` connects debuggable Android Gradle Plugin variants to the static
preview protocol. The plugin ID is `com.viewcompose.preview`; version `0.1.0-alpha03` targets the AGP
9.1 line. The plugin bytecode remains Java 11 compatible, while its isolated Paparazzi worker
requires JDK 21 or newer for rendering.

{/* compiled-region source="viewcompose-preview-gradle-plugin/src/test/samples/com/viewcompose/preview/gradle/samples/PreviewGradlePluginSamples.kt" region="preview-gradle-apply" sample_id="module.preview-gradle-apply" build_target=":viewcompose-preview-gradle-plugin:compileTestKotlin" */}
```kotlin
fun applyPreviewPluginSample(project: Project) {
    project.pluginManager.apply("com.viewcompose.preview")
    project.tasks.named("viewComposePreviewDescriptors")
}
```

## Variant, discovery, and task contract

The plugin may be applied before or after an Android application/library plugin and configures each
project once. Debuggable variants receive discovery, render, and refresh tasks. Non-debuggable
variants receive only bytecode instrumentation that removes root and composed preview annotations
while preserving unrelated annotations and stack frames.

Discovery scans compiled project directories and JARs without loading application classes into the
Gradle daemon. It combines source locations with canonical runtime/boot classpaths, manifests,
resources, assets, resource packages, and project bytecode. One full fingerprint invalidates render
output; a narrower Layoutlib compatibility fingerprint excludes reloadable project code so a warm
worker can retain platform state while every render receives a fresh application class loader.

`viewComposePreviewDescriptors` aggregates descriptor export. Variant tasks include
`discoverDebugViewComposePreviews`, `renderDebugViewComposePreview`, and
`refreshDebugViewComposePreview`. Single rendering selects one preview/variant; gallery rendering
uses a target file. The modes are exclusive, batches are protocol-bounded, and response paths stay
isolated. `--rerender=true` bypasses only the response cache.

Fast refresh reuses the last complete discovery/resource baseline after source-only changes.
Signature, resource, manifest, or dependency changes require full discovery. Missing or
incompatible baselines request that full path instead of guessing. Content-addressed Layoutlib and
resource-symbol inputs remain outside the application classpath; optional worker-reuse verification
compares warm and cold pixels/structure.

## IDE and operational boundary

The Gradle plugin does not install Android Studio UI. Install `ViewCompose Preview` from Marketplace
separately for gutters, galleries, source navigation, refresh, and diagnostics. IDE and Gradle
plugin versions are independent.

- Keep preview artifacts in debug/tooling configurations and run release builds to verify stripping.
- Treat task input annotations and fingerprints as incremental-correctness contracts.
- Use fast refresh only for known descriptors after source-only changes.
- Run plugin unit/functional tests and worker-reuse verification when discovery, classpaths, or
  Layoutlib compatibility inputs change.

See [Preview tooling](../../tooling/preview.md), [Preview Core](../viewcompose-preview-core/README.md),
and the [generated API reference](https://docs.viewcompose.com/api/viewcompose-preview-gradle-plugin/current/).
