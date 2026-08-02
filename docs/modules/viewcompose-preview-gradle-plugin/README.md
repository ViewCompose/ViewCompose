# Preview Gradle Plugin

`viewcompose-preview-gradle-plugin` connects Android Gradle Plugin variants to ViewCompose's static
preview protocol. It discovers compiled preview entries, exports deterministic build inputs, plans
content-addressed renders, launches isolated workers, and strips preview metadata from production
artifacts.

## Plugin and stability

```kotlin
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha01"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Task names and protocol version 1 form the current tooling contract.
- Build requirements: Android Gradle Plugin 8.9 line and JDK 17+ for rendering.
- Scope: Android application and library projects.
- Production boundary: non-debuggable variants retain no direct or composed ViewCompose preview
  annotations in project bytecode.

## Variant integration

The plugin may be applied before or after the Android plugin. It configures a project once when an
Android application or library plugin appears. Debuggable variants receive discovery, render, and
fast-refresh tasks. Non-debuggable variants receive only ASM instrumentation that removes root
preview annotations and custom meta-annotations while preserving unrelated annotations and frames.

`viewComposePreviewDescriptors` aggregates descriptor export across debuggable variants. Variant
task names follow Gradle's normal naming, for example `discoverDebugViewComposePreviews`,
`renderDebugViewComposePreview`, and `refreshDebugViewComposePreview`.

## Discovery and fingerprints

Discovery scans compiled project directories and JARs without loading application classes into the
Gradle daemon. Source roots supply navigation locations. Runtime/boot classpaths, manifests,
resources, assets, resource packages, and project bytecode are canonicalized into sorted input
groups and lowercase SHA-256 fingerprints.

The full input fingerprint invalidates render output. A narrower Layoutlib compatibility fingerprint
excludes reloadable project classes, annotations, and sources so the worker can reuse platform
state safely while giving every render a fresh application class loader. Manifest and catalog files
are published atomically; unsupported entries become structured discovery diagnostics.

## Single and gallery rendering

Single rendering selects `--preview-id` and optionally `--variant-id`. Gallery rendering selects a
TSV `--preview-targets-file`; the options are mutually exclusive. Duplicate batch targets are
rejected, worker batches are capped by the core protocol, and individual response files remain
isolated.

Successful responses are cached by request content. `--rerender=true` bypasses that response cache
without discarding canonical build inputs. Single failures fail the Gradle task; batch failures are
reported per target so other tiles can finish.

## Fast refresh and worker reuse

The refresh task depends only on source compilation and reuses the last complete discovery/resource
baseline. It rescans current project bytecode, writes fast manifest/catalog artifacts, and uses the
persisted render toolchain. Missing or incompatible baselines deliberately request full discovery
instead of guessing.

Layoutlib archives and generated resource-symbol classpaths are materialized content-addressably.
The worker host stays outside the application classpath. Optional `--verify-worker-reuse=true`
compares warm and cold render pixels/structure and fails when retained platform state changes output.

## Testing and operations

- Keep preview dependencies and theme providers in debug source sets.
- Run release builds in CI to verify preview annotation stripping continuously.
- Treat task input-annotation changes as incremental-build correctness changes.
- Use worker-reuse verification when changing Layoutlib compatibility inputs or class-loader policy.
- Prefer the fast refresh task for known descriptors after a source-only save; fall back to full
  discovery after signature, resource, manifest, or dependency changes.

## Related documentation

- [Preview Core module](../viewcompose-preview-core/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-preview-gradle-plugin` API tree](https://docs.viewcompose.com/api/viewcompose-preview-gradle-plugin/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes compiled-bytecode discovery, deterministic grouped fingerprints,
fast source refresh, bounded gallery batches, content-addressed artifacts, isolated workers, and
non-debuggable annotation stripping. Task and protocol compatibility may still evolve before stable.
