---
schema_version: 2
document_id: guide.theming-dynamic-color
doc_type: guide
owner:
  kind: capability
  id: theme.material3
version_lane: released
capability_ids:
  - theme.material3
artifact_ids:
  - viewcompose-material3
  - viewcompose-material3-android
sample_ids:
  - guide.theming-dynamic-color
task: Enable Material 3 dynamic color and keep Android resources, native Views, overlays, and framework tokens on one refreshed Context.
success_checks:
  - Supported devices resolve dynamic Material colors while unsupported devices use the deterministic Material fallback.
  - Root Views, AndroidView content, overlays, and Theme tokens agree after configuration change.
  - Imperative resource changes refresh through the host-owned controller on the main thread.
failure_checks:
  - Tokens are resolved from one Context while native Views or overlays use another.
  - A custom host installs a second recurring configuration observer beside the standard host.
  - An imperative resource mutation is assumed to emit a configuration callback when it does not.
---

# Enable Material 3 dynamic color

The standard Material Android host is the preferred integration because it resolves one Context
for the native tree, overlays, and framework tokens. Use this task when an application wants
wallpaper-derived color on supported Android versions. For token ownership and renderer isolation,
see the [theme architecture](../architecture/theming.md).

## Select the host policy

`UseIfAvailable` is the default. Declaring it explicitly makes product policy visible:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="material3-dynamic-color" sample_id="guide.theming-dynamic-color" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
class DynamicColorGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
        ) {
            Text("Dynamic Material color")
        }
    }
}
```

Choose `Disabled` when the product requires a fixed Android XML palette. On platforms without
dynamic-color support, `UseIfAvailable` keeps the resolved Material theme and static fallback
rules; application code does not need a second version branch.

## Keep one resolved Context

`setMaterial3UiContent` resolves the themed Context before creating the root and uses it for native
descendants, `AndroidView`, default overlays, and `Material3Theme`. A low-level custom host must do
the same with `Material3ThemeBridge.resolveContext` and create every related View from
`resolvedTheme.context`. Reading tokens from the resolved wrapper while constructing Views from the
original Activity Context is invalid.

The standard host observes configuration changes and advances `Environment.resourceRevision`.
`Material3Theme` then refreshes its stable wrapper and maps a new immutable snapshot. Do not add a
second configuration callback in the Material layer.

## Refresh imperative resource changes

Some locale wrappers, theme overlays, or calls to `setTheme` change resources without dispatching
the configuration callback observed by the host. Pass one `AndroidResourceRefreshController` to
`setMaterial3UiContent`, apply the resource change, and call its `refresh()` method on the main
thread. That controller refreshes both the host environment and the Material wrapper in one
ordered operation.

Use `Material3ThemeRefreshController` only in a custom low-level host that does not install the
standard Android resource environment. It must not become a parallel refresh path in an ordinary
Material Android host.

## Verify the task

Compile with `./gradlew :samples:tutorials:compileDebugKotlin`, then verify:

1. On Android 12 or newer, change wallpaper colors and recreate the Activity; root surfaces,
   framework controls, `AndroidView`, and overlays must use one coherent dynamic palette.
2. On an older or unsupported device, confirm the configured Material XML/static fallback remains
   readable and no dynamic-color exception occurs.
3. Toggle light/dark configuration and confirm `Theme.current.metadata.revision` advances with no
   mixed old/new token families.
4. Apply one imperative test theme or locale mutation, invoke the host resource controller, and
   confirm both native resources and framework tokens update in the same root.

A split palette between overlays and the page, a stale `AndroidView`, duplicate refresh work, or a
background-thread refresh is a failed integration.
