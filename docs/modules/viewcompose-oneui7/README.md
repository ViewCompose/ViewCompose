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
- Runtime dependencies: `viewcompose-animation` and `viewcompose-gesture`, resolved transitively;
  their types are not exposed by the One UI 7 public signatures.
- Material dependency: none.
- Reference: One UI 7 and Samsung Developer One UI guidance, pinned and reviewed on 2026-08-09.
- Public component-set identity: `one-ui-7-five-component-alpha`.

The reference baseline is Samsung's public
[One UI 7 design story](https://design.samsung.com/global/contents/one-ui-7/) and
[One UI component guidance](https://developer.samsung.com/one-ui/index.html). Navigation follows
the public [bottom-navigation guidance](https://developer.samsung.com/one-ui/comp/bottom-navigation.html)
for text-only destinations. These links define the visual reference, not a promise that all One UI
components or Samsung-private values are reproduced.

## Public-guidance audit

The 2026-08-09 audit separates published numeric guidance from visual interpretation:

| Area | Samsung public evidence | ViewCompose result |
| --- | --- | --- |
| Contained Button shape | The published Button drawable uses an `18dp` radius | Medium Button and field shape token is `18dp`; the 48dp target contains a 36dp visual Button |
| Button emphasis | Flat, gray contained, and colored contained treatments | `Flat`, `Neutral`, and `Primary` variants map to low, medium, and high emphasis |
| Primary Button color | `#0072DE` in Light and `#3E91FF` in Dark | Static primary action roles use those values |
| Activated control color | `#3E91FF` in Light and Dark | Switch checked track resolves through `stateColors.controlActivated` |
| Switch geometry | No complete public numeric geometry | The overrideable interpretation uses a `44dp` by `24dp` track, `18dp` thumb, `3dp` inset, and separate `48dp` effective target |
| Horizontal screen margin | At least `24dp` | The verification fixture uses `24dp`; layout remains an application responsibility |
| Bottom navigation | Text only, normally fewer than four and at most five; no swipe switching | The selected item uses text plus an underline, without a Material-style pill |
| Snackbar | Short feedback with an optional action on the right | Supplied by the explicit One UI Android overlay adapter with a full-height pill outline |

Samsung does not publish exact public values for this alpha's Surface/Card padding, Switch bounds,
TextField padding, complete typography scale, or One UI 7 overlay chrome. Those remain
ViewCompose-owned interpretation values and require screenshot acceptance; they are not presented
as Samsung tokens.

Component geometry now resolves from the provided snapshot. Copying and overriding
`tokens.controls.button`, `tokens.controls.textField`, `tokens.controls.navigationBar`,
`tokens.controls.switch`,
`tokens.shapes.medium`, or `tokens.stateColors.controlActivated` changes the corresponding emitted
component instead of being shadowed by private hard-coded values.

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
snapshot in place. The neutral `viewcompose-android` host installs no design system, so applications
depend on this artifact and install `OneUi7Theme` explicitly without an implicit Material Context.

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

`OneUi7Theme` also provides the shared `UiDesignSystemAttribution` snapshot. Diagnostics report
the static `viewcompose-oneui7/static` token producer plus recipe identity, neutral backend,
Equivalent conformance, and capability path for all five families. This metadata is evidence only;
the snapshot reports `FrameworkDefault` until an application applies an explicit override. The
private typed recipes remain separate and no One UI policy enters UI Foundation or Renderer.

## Behavior and fallback contract

- Button and Switch expose at least a 48dp effective target. Navigation destinations expose a 52dp
  target inside a 68dp bar.
- The default Switch keeps a compact 24dp visible track inside that 48dp target. Track, thumb,
  inset, and label spacing remain interpreted, overrideable sizing tokens rather than claimed
  Samsung numeric tokens.
- Switch and NavigationBar state is caller-owned. Their callbacks request replacement state and do
  not mutate caller data. Switch supports whole-row click plus bounded follow-finger drag with
  position/velocity settling, cancellation rollback, and mirrored physical travel in RTL. Release
  settlement continues from the last visible drag position rather than restarting at an endpoint.
- TextField preserves ViewCompose's native Android editing core for IME, selection, composition,
  autofill, accessibility, and saved-state behavior.
- RTL reverses visual destination order without changing caller indices or keys.
- NavigationBar exposes one single-selection accessibility row and each destination's logical
  column. Android can therefore announce collection positions without a One UI renderer branch;
  the logical positions remain stable when RTL reverses physical placement.
- Backdrop blur is not part of this alpha public API. A product requiring that decoration must use
  an opaque or translucent tinted Surface fallback; no content, input, or semantics may depend on
  blur availability.
- Shape morph is not part of this component set. Framework shape-transition contracts may choose a
  discrete or static endpoint when shapes are incompatible.

The module owns token and component policy only. It does not install Android window behavior,
system-bar policy, a renderer branch, or a mutable global registry. The optional
[`viewcompose-overlay-oneui7-android`](../viewcompose-overlay-oneui7-android/README.md) artifact owns
One UI Snackbar and modal-bottom-sheet presenters. Default theme attribution keeps those optional
capabilities `Unsupported`. An explicitly installed adapter supplies a root-owned attribution list
that upgrades its presenters plus neutral Android Dialog/Popup transport and degraded Android
Toast fallback; neither path reports or selects a Material fallback.

## Verification and limitations

Unit and gesture-contract tests protect light/dark snapshots, component structure, validation,
real-touch click dispatch, controlled drag bounds/settling/cancellation, release continuity, and
callback behavior.
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
- [One UI 7 Android overlay adapter](../viewcompose-overlay-oneui7-android/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Design-system resolution boundary](../../architecture/decisions/0004-design-system-resolution-boundary.md)
