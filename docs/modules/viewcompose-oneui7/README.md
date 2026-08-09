# One UI 7 Five-Component Alpha

`viewcompose-oneui7` is ViewCompose's first public non-Material design-system artifact. It provides
static light and dark token snapshots plus an intentionally bounded Button, Surface/Card, Switch,
TextField, and text-only NavigationBar component set inspired by Samsung's public One UI 7 design
guidance.

This is an independent ViewCompose implementation. Samsung does not publish, sponsor, or endorse
this artifact, and the values exposed here are ViewCompose interpretations rather than Samsung
internal design tokens. It is not a complete One UI component library.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependency: `viewcompose-ui-foundation`, supplied transitively.
- Material dependency: none.
- Reference: One UI 7 and Samsung Developer One UI guidance, pinned and reviewed on 2026-08-09.
- Public component-set identity: `one-ui-7-five-component-alpha`.

The reference baseline is Samsung's public
[One UI 7 design story](https://design.samsung.com/global/contents/one-ui-7/) and
[One UI component guidance](https://developer.samsung.com/one-ui/index.html). Navigation follows
the public [bottom-navigation guidance](https://developer.samsung.com/one-ui/comp/bottom-navigation.html)
for text-only destinations. These links define the visual reference, not a promise that all One UI
components or Samsung-private values are reproduced.

## Minimal setup

Install one complete immutable snapshot at the root of the content that uses these components:

```kotlin
setUiContent {
    OneUi7Theme(tokens = OneUi7ThemeDefaults.light()) {
        OneUi7Button(text = "Continue", onClick = { continueFlow() })
    }
}
```

Use `OneUi7ThemeDefaults.dark()` for the deterministic dark snapshot. Callers may copy either
`UiThemeTokens` value and replace semantic roles before providing it. To switch between design
systems, replace the root content/session with a new provider snapshot; do not mutate an active
snapshot in place. `viewcompose-android` continues to install Material 3 by default, so applications
must depend on and install this artifact explicitly.

The complete compiled example is
[`OneUi7Samples.kt`](../../../viewcompose-oneui7/src/test/samples/com/viewcompose/oneui7/samples/OneUi7Samples.kt).

## Public component set

| Entry point | Implementation boundary | Alpha conformance |
| --- | --- | --- |
| `OneUi7Button` | Shared `BasicButton` with One UI 7 alpha recipe values | Equivalent |
| `OneUi7Surface` | Shared `BasicSurface` with owned surface recipe | Equivalent |
| `OneUi7Switch` | Design-system-owned renderer-neutral composite | Equivalent |
| `OneUi7TextField` | Owned decoration around the native Android editing core | Equivalent |
| `OneUi7NavigationBar` | Design-system-owned text-only destination composite | Equivalent |

`OneUi7ThemeDefaults`, `OneUi7Theme`, `OneUi7ButtonVariant`, `OneUi7NavigationItem`, and
`OneUi7Reference` complete the public setup and diagnostic surface. The generated reference is
available in the
[`viewcompose-oneui7` API tree](https://docs.viewcompose.com/api/viewcompose-oneui7/current/).

## Behavior and fallback contract

- Button and Switch expose at least a 48dp effective target. Navigation destinations expose a 52dp
  target inside a 68dp bar.
- Switch and NavigationBar state is caller-owned. Their callbacks request replacement state and do
  not mutate caller data.
- TextField preserves ViewCompose's native Android editing core for IME, selection, composition,
  autofill, accessibility, and saved-state behavior.
- RTL reverses visual destination order without changing caller indices or keys.
- Backdrop blur is not part of this alpha public API. A product requiring that decoration must use
  an opaque or translucent tinted Surface fallback; no content, input, or semantics may depend on
  blur availability.
- Shape morph is not part of this component set. Framework shape-transition contracts may choose a
  discrete or static endpoint when shapes are incompatible.

The module owns token and component policy only. It does not install Android window behavior,
overlay presenters, system-bar policy, a renderer branch, or a mutable global registry.

## Verification and limitations

Unit tests protect light/dark snapshots, component structure, validation, and callback behavior.
The demo Settings entry `Verify One UI 7 five-component alpha` exercises Light/LTR/1.0 and
Dark/RTL/1.3 configurations and exports deterministic screenshots with token source, component-set
identity, and conformance labels. API 35 emulator evidence covers state changes, disabled behavior,
native text editing, RTL ordering, and screenshot anchors.

Pixel and physical Samsung screenshot acceptance remains a release-owner gate. The alpha version
must not be described as pixel-identical across Android API levels or OEM builds, as full One UI
support, or as a replacement for Samsung platform components outside the five entries above.

## Related documentation

- [Theming guide](../../guides/theming.md)
- [UI Foundation](../viewcompose-ui-foundation/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Design-system resolution boundary](../../architecture/decisions/0004-design-system-resolution-boundary.md)
