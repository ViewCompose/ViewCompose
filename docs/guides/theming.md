---
schema_version: 2
document_id: guide.theming-mode-switch
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
  - viewcompose-ui-foundation
sample_ids:
  - guide.theming-mode-switch
task: Apply one application-owned System, Light, or Dark preference consistently across independent Activity roots.
success_checks:
  - Every active Activity root updates after the shared application preference changes.
  - System mode resolves configuration from each root Context while explicit modes use deterministic tokens.
  - Hidden retained navigation destinations receive the latest inherited theme before becoming visible.
failure_checks:
  - Theme preference or provider state is stored in one Activity or framework process singleton.
  - Context.setTheme is treated as an observable cross-Activity preference change.
  - An Activity reaches a mixed snapshot containing values from two modes.
---

# Switch application theme mode

Use this guide after the [theme tutorial](../tutorials/theming.md) when an application exposes a
System, Light, or Dark preference. The long-lived snapshot and precedence rules live in the
[theme architecture](../architecture/theming.md); dynamic Android resources and local subtree
customization have separate guides.

## Own the preference in the application

Persist the selected mode in an application-owned repository or observable state holder. Every
Activity root reads that same state, but each root keeps its own `RenderSession` and theme provider.
Do not make the framework theme a process singleton and do not let one Activity address another
Activity's session.

The compiled helper below selects the outer Material snapshot already resolved for System mode or
one deterministic static snapshot for an explicit mode:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="application-theme-mode" sample_id="guide.theming-mode-switch" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
enum class AppThemeMode { System, Light, Dark }

object AppThemePreference {
    val mode = mutableStateOf(AppThemeMode.System)
}

fun UiTreeBuilder.ApplicationTheme(content: UiTreeBuilder.() -> Unit) {
    val systemTokens = Theme.current
    val selectedTokens = when (AppThemePreference.mode.value) {
        AppThemeMode.System -> systemTokens
        AppThemeMode.Light -> Material3ThemeDefaults.light()
        AppThemeMode.Dark -> Material3ThemeDefaults.dark()
    }
    Material3Theme(tokens = selectedTokens, content = content)
}
```

Call `ApplicationTheme { ... }` inside every `setMaterial3UiContent` root. A production preference
repository should persist the enum and expose one observable value; the top-level state in the
sample only keeps the ownership boundary visible.

## Keep System and explicit modes distinct

System mode uses `Theme.current` from each root because configuration, locale, window, and vendor
resources belong to that root Context. Explicit Light and Dark modes use deterministic token
producers here. If explicit modes must also apply Android XML resources to native widgets, recreate
or explicitly refresh each host after applying the matching Context theme; replacing tokens alone
does not mutate Android resources.

`Context.setTheme` and `applyStyle` affect one Context. They neither persist the user's selection
nor notify other Activity sessions. Keep the application preference as the source of truth and use
the [dynamic-color and refresh guide](./theming-dynamic-color.md) for resource lifecycle changes.

## Verify the task

Compile the registered sample with `./gradlew :samples:tutorials:compileDebugKotlin`, then run this
manual journey:

1. Open two Activities that both install `ApplicationTheme`.
2. Select Light in the second Activity and return to the first; both roots must show the Light
   snapshot without recreation-dependent stale values.
3. Select Dark and repeat the check.
4. Select System, change the device mode, and confirm each root resolves its own current Context.
5. Keep one themed navigation destination hidden, change the mode, then reveal it by Back or stack
   selection; it must render with the new snapshot before becoming visible.

The task fails if only the Activity that changed the preference updates, if System mode reuses a
stale Context snapshot, or if a destination briefly displays the old theme on re-entry.
