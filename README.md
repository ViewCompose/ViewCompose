# ViewCompose

<p align="center">
  <strong>A declarative Android UI framework powered by the native View system.</strong>
</p>

<p align="center">
  <a href="./README.md"><img alt="English" src="https://img.shields.io/badge/English-6E56CF?style=for-the-badge"></a>
  <a href="./README.zh-CN.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-3C4043?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.viewcompose/viewcompose-host-android"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.viewcompose/viewcompose-host-android?label=Maven%20Central"></a>
  <a href="https://github.com/ViewCompose/ViewCompose/actions/workflows/ci.yml"><img alt="Build" src="https://github.com/ViewCompose/ViewCompose/actions/workflows/ci.yml/badge.svg"></a>
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg"></a>
  <img alt="Android API" src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84">
  <img alt="Status" src="https://img.shields.io/badge/Status-Alpha-orange">
</p>

<p align="center">
  <a href="https://docs.viewcompose.com/tutorials/getting-started">Get started</a> ·
  <a href="./docs/README.md">Documentation</a> ·
  <a href="https://central.sonatype.com/artifact/com.viewcompose/viewcompose-host-android">Maven Central</a> ·
  <a href="https://plugins.jetbrains.com/plugin/33290-viewcompose-preview">Android Studio plugin</a> ·
  <a href="./CONTRIBUTING.md">Contributing</a> ·
  <a href="./docs/project/roadmap.md">Roadmap</a>
</p>

ViewCompose brings a Compose-inspired, state-driven Kotlin DSL to applications that use the
Android View rendering engine. It owns its runtime, composition, diffing, and tooling model while
preserving native Views as the final UI tree.

This is not Jetpack Compose, a compatibility layer, or a compiler-plugin reimplementation. The
project deliberately focuses on a practical declarative framework that can interoperate with the
existing Android View ecosystem and platform services.

ViewCompose is maintained as a complete open-source project. The first public Alpha is available
from Maven Central. Alpha APIs may still change, so evaluate the current release against your
application requirements before adopting it in production.

## Why ViewCompose

- **Native View engine** — output is an Android View tree, with platform accessibility, input,
  IME, lifecycle, theming, and `AndroidView` interoperability.
- **Declarative runtime** — observable state, incremental recomposition, keyed reuse, transactional
  rendering, structured effects, saveable state, and environment propagation.
- **System-level UI capabilities** — complete text editing, lazy collections, nested scrolling,
  focus and hardware keys, overlays, animation, gestures, graphics, and navigation that does not
  require Fragment destinations.
- **Android theme integration** — native theme resolution, Material roles, dynamic color,
  configuration changes, shape tokens, and consistent preview inputs.
- **Independent modules** — platform-neutral cores and Android feature artifacts can evolve and be
  consumed independently, following an AndroidX-style release model.
- **First-party tooling** — static previews, source navigation, diagnostics, snapshot regression,
  and performance comparison are developed alongside the framework.

## How it works

```text
Kotlin DSL
   ↓
VNode / NodeSpec tree
   ↓
State tracking + incremental composition
   ↓
Diff / patch renderer
   ↓
Native Android View tree
```

The runtime and core policy modules remain free of Android where practical. Android modules bridge
the semantic tree to Views and platform services. Optional capabilities such as advanced shadows,
Coil image loading, navigation, and ConstraintLayout do not become core requirements.

## Get started

The current public version is `0.1.0-alpha01` and is available from Maven Central.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    val viewComposeVersion = "0.1.0-alpha01"

    implementation("com.viewcompose:viewcompose-runtime:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-ui-contract:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-widget-core:$viewComposeVersion")
    implementation("com.viewcompose:viewcompose-host-android:$viewComposeVersion")
}
```

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUiContent {
            val count = remember { mutableStateOf(0) }

            Column(spacing = 16.dp) {
                Text(text = "Count: ${count.value}")
                Button(
                    text = "Increment",
                    onClick = { count.value += 1 },
                )
            }
        }
    }
}
```

Follow [Build your first application](https://docs.viewcompose.com/tutorials/getting-started) for
the complete setup, imports, theme, explanation, and verification commands. The complete counter is
kept compilable in [`samples/counter`](./samples/counter).

Feature artifacts bring their platform-neutral core where appropriate and may also be consumed
separately:

```kotlin
implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha01")
implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha01")

// Pure Kotlin/JVM policy and state models are available independently.
implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha01")
implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha01")
```

Every published artifact includes sources so IDE navigation can open the framework implementation.
See [Publishing](docs/project/publishing.md) for the complete artifact and versioning contract.

## Modules at a glance

| Area | Modules | Purpose |
| --- | --- | --- |
| Runtime | `viewcompose-runtime`, `viewcompose-text-core`, `viewcompose-ui-contract` | State, composition, editing, and semantic contracts |
| Android UI | `viewcompose-widget-core`, `viewcompose-renderer`, `viewcompose-host-android` | DSL, native View mapping, and host sessions |
| Android integration | `viewcompose-lifecycle`, `viewcompose-viewmodel`, `viewcompose-overlay-android`, `viewcompose-image-coil` | Platform lifecycle and services |
| Feature pairs | `viewcompose-navigation*`, `viewcompose-animation*`, `viewcompose-gesture*`, `viewcompose-graphics*` | Independently evolving core + Android capabilities |
| Optional UI | `viewcompose-shadow-android`, `viewcompose-widget-constraintlayout` | Advanced rendering and layout integrations |
| Tooling | `viewcompose-preview*`, `viewcompose-benchmark` | Preview, diagnostics, snapshots, and performance testing |

Module versions are intentionally independent. Depending on a feature does not require adopting
unrelated ViewCompose modules or moving the entire project on one atomic release train.

## ViewCompose Preview for Android Studio

[ViewCompose Preview](https://plugins.jetbrains.com/plugin/33290-viewcompose-preview) is the
companion Android Studio plugin for static ViewCompose previews. It provides:

- gutter preview actions and two-way source/render selection;
- light/dark themes, locale, direction, density, font scale, and device configurations;
- native View, layout, VNode, composition, patch, and recomposition diagnostics;
- incremental refresh, full rebuild, bounded caches, zoom/pan, and an all-previews gallery;
- isolated Layoutlib workers so application code is not loaded into the Android Studio process.

The plugin has its own release lifecycle and version. Its source and local installation guide live
under [`tools/viewcompose-studio-plugin`](./tools/viewcompose-studio-plugin).

## Documentation

The hosted documentation system is maintained in this repository and publishes to
[`docs.viewcompose.com`](https://docs.viewcompose.com). [`docs/README.md`](docs/README.md) remains
the canonical source entrance and offline fallback for architecture, APIs, guides, performance,
tooling, and roadmap material. The README intentionally stays focused on project identity and
first-time adoption.

## Build and contribute

```bash
git clone https://github.com/ViewCompose/ViewCompose.git
cd ViewCompose
./gradlew qaQuick
```

Issues and pull requests are welcome. Please read the [contribution guide](./CONTRIBUTING.md),
[architecture boundaries](docs/architecture/overview.md), and [development workflow](docs/project/workflow.md) before a
substantial change.

## License

ViewCompose is available under the [MIT License](./LICENSE).
