---
schema_version: 2
document_id: guide.theming-local-overrides
doc_type: guide
owner:
  kind: capability
  id: theme.foundation
version_lane: released
capability_ids:
  - theme.foundation
artifact_ids:
  - viewcompose-ui-foundation
sample_ids:
  - guide.theming-local-override
task: Override selected semantic token families for one subtree without changing the application theme or component behavior.
success_checks:
  - The nested subtree reads the merged immutable snapshot and siblings retain the parent snapshot.
  - Replacing colors without stateColors re-derives state roles from the new colors.
  - Instance and component-family appearance overrides remain more specific than the theme override.
failure_checks:
  - A local appearance need mutates a process-global token object.
  - Behavior, state, callbacks, identity, or lifecycle are moved into an appearance override.
  - Basic primitives or Renderer receive sparse semantic overrides.
---

# Override theme tokens for one subtree

Use `UiThemeOverride` when one semantic section needs a different color, typography, shape, control
sizing, interaction, or overlay family. It derives a nested immutable theme snapshot and restores
the parent after the subtree. Read the [theme architecture](../architecture/theming.md) for the full
precedence and renderer boundary.

## Transform only the required families

The transforming overload starts from the current family. Unspecified families preserve their
parent values:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="theme-local-override" sample_id="guide.theming-local-override" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.AccentPanel() {
    UiThemeOverride(
        colors = { copy(primary = 0xFF6750A4.toInt()) },
        shapes = { copy(medium = large) },
    ) {
        Column {
            Text("Only this subtree uses the accent theme")
            Button("Continue", onClick = {})
        }
    }
}
```

Use the value overload when the application already owns complete replacement families. Replacing
`colors` without an explicit `stateColors` replacement re-derives state colors, preventing an old
pressed, selected, or disabled palette from leaking into the new scheme.

## Choose the correct override level

`UiThemeOverride` changes semantic defaults for every participating component in a subtree. A
component-owned `XxxOverrides` provider changes sparse appearance slots for only that component
family. An instance appearance remains the most specific value.

Use a component override for one Button border, TextField decoration, or control interaction layer.
Use an application-defined `uiLocalOf` when the value is an application semantic concept that does
not belong in the framework theme. Do not put controlled state, callbacks, keyboard policy,
navigation, lifecycle, resource handles, or renderer platform types into either appearance model.

Basic primitives accept complete resolved styles and do not consume sparse overrides. Renderer
receives resolved NodeSpec values and never reads `UiThemeOverride` directly.

## Verify the task

Compile with `./gradlew :samples:tutorials:compileDebugKotlin`, then verify one page containing the
parent theme, the nested panel, and a sibling after the panel:

1. Change the parent theme and confirm inherited fields inside the panel update.
2. Confirm the panel's primary color and medium shape remain overridden.
3. Press, focus, select, and disable controls in the panel; their state colors must match the
   overridden semantic palette.
4. Confirm the sibling after the panel uses the unchanged parent snapshot.
5. Add a component-scope and instance override inside the panel and confirm the documented
   precedence remains instance, component scope, then theme scope.

Leaking the panel theme to a sibling, retaining parent state colors, or making a behavior change
through an appearance object fails the task.
