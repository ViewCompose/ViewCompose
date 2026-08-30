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
requires JDK 21 or newer for rendering. The resolvable worker-host configuration requests JVM 21
bytecode explicitly, so dependency selection matches that process boundary instead of the plugin's
own Java 11 bytecode target. Its native Layoutlib runtime and resources are pinned to the same
16.2.1 release loaded by Paparazzi 2.0.0-alpha05; mixing native and Java Layoutlib releases is not a
supported lane.

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
output; a narrower Layoutlib compatibility fingerprint still owns reusable materialization such as
resource symbols. Persistent worker compatibility additionally includes the full build-input
fingerprint, so Layoutlib process state is reused only within one exact build while every render
also receives a fresh application class loader. A project bytecode or resource change starts a new
worker instead of inheriting process-global AndroidX or Layoutlib caches from another build.

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

The 2026-08-29 dependency-resolution acceptance reproduced the former JVM 17 consumer/JVM 21 worker
mismatch with the published local worker artifact and then exposed a stale 15.2.3 native Layoutlib
pin against Paparazzi's 16.2.1 Java engine. It passed descriptor discovery and the real
`CounterPreview` render path after aligning the worker-host configuration to JVM 21 and both
Layoutlib sides to 16.2.1. This is **improved** functional compatibility with no
application-runtime change: these configurations are tooling-only and resolved only when a Preview
render or refresh task runs. The local macOS/JBR 21 run is not a cross-host compatibility matrix;
CI and the existing Preview suites remain the release gate. The accepted render produced a
1,079 x 2,339 PNG of 25,755 bytes and a 121,271-byte render tree with zero protocol diagnostics;
the worker reported 220 ms render duration after a 2,315 ms Layoutlib setup. The plugin suite passed
23/23 tests, and documentation, translation, release-intent, and development-tooling-isolation gates
also passed.

The 2026-08-30 cross-build isolation acceptance reproduced a history-dependent render by running
the generated screenshot target before the generated XML target in one persistent worker. The XML
result remained semantically and structurally exact, but its PNG and render-tree identities changed
from the accepted cold render. Binding worker compatibility to the complete build-manifest input
fingerprint now retires the process whenever project code or resources change. From an empty
Preview harness, the installed distribution sequence passed the screenshot, XML, image XML,
comparison, exact-pixel, dual-protocol MCP, and offline install/uninstall gates; XML returned the
accepted output fingerprint
`6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab` after the screenshot build.
The 23 non-TestKit plugin tests passed, including exact sensitivity to build, Layoutlib, and render
runtime identities. Two attempts at the complete 24-test plugin suite reached only the functional
TestKit denominator before the host volume exhausted its remaining space, so those attempts are
**inconclusive** and are not accepted as functional evidence; the cold installed distribution is
the accepted end-to-end denominator. The result is **mixed** operationally: cross-build pixel
determinism is improved, while a changed build now pays cold Layoutlib setup instead of reusing a
potentially contaminated process. Application runtime behavior remains unchanged. If cold setup
latency becomes material, any narrower reuse key must first pass the same cross-build pixel and
render-tree denominator.

See [Preview tooling](../../tooling/preview.md), [Preview Core](../viewcompose-preview-core/README.md),
and the [generated API reference](https://docs.viewcompose.com/api/viewcompose-preview-gradle-plugin/current/).
