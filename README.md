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
  <a href="https://docs.viewcompose.com/ai">AI integration</a> ·
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

The standard Material Android dependency is the named `viewcompose-material3-android` aggregate.
Use the neutral `viewcompose-android` aggregate when the application installs One UI or its own
design tokens. The artifact lines start at `0.1.0-alpha01`; this source checkout verifies them
through the generated local Maven repository until those coordinates are released to Maven
Central. ViewCompose modules are independently versioned.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
}
```

The named aggregate exposes the neutral host plus Material 3, runtime, UI Foundation, Lifecycle,
and ViewModel APIs transitively. Add lower-level coordinates only when building an integration or
intentionally using their advanced APIs without the aggregate.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent {
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
implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha03")

// Pure Kotlin/JVM policy and state models are available independently.
implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha02")
implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha03")
```

Every published artifact includes sources so IDE navigation can open the framework implementation.
See [Publishing](docs/project/publishing.md) for the complete artifact and versioning contract.

## Modules at a glance

| Area | Modules | Purpose |
| --- | --- | --- |
| Kernel | `viewcompose-runtime`, `viewcompose-text-core`, `viewcompose-ui-contract`, `*-core` | State, editing, contracts, and platform-neutral policy |
| UI Foundation | `viewcompose-ui-foundation`, `viewcompose-animation`, `viewcompose-gesture`, `viewcompose-graphics` | Platform-neutral UI and capability DSLs |
| Android Engine | `viewcompose-renderer-android`, `viewcompose-host-android` | Native View mapping and low-level host sessions |
| Design System | `viewcompose-material3`, `viewcompose-oneui7` | Named design tokens, recipes, and components |
| Integrations | `viewcompose-*-androidx`, `viewcompose-*-android`, image adapters | AndroidX, Material, decoder, and optional platform integrations |
| Aggregate | `viewcompose-android`, `viewcompose-material3-android` | Neutral and named Material application entry points |
| Tooling | `viewcompose-preview*`, `viewcompose-benchmark` | Preview, diagnostics, snapshots, and performance testing |

Module versions are intentionally independent. Depending on a feature does not require adopting
unrelated ViewCompose modules or moving the entire project on one atomic release train.

## AI integration

ViewCompose publishes a versioned AI Reference, 13 local stdio MCP tools, and six standard Agent
Skills as an installable GitHub Release. Codex, Claude Code, and Cursor can be connected to a new or
existing Android project with one package-install command and one transactional project-init
command—no ViewCompose checkout, local build, provider key, or manual configuration edit is needed
for standalone knowledge, generation, static validation, and project analysis.

Compilation, Preview, and rendered layout diagnosis remain an explicitly reported source-bound
enhancement in `0.1.0`; static evidence is never presented as compiled or rendered evidence. Follow
[AI Integration](./docs/ai/README.md) for the two-command setup, `doctor`, capability boundary,
client checks, upgrades, and safe removal.

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
