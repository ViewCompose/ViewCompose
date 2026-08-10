# Changelog

## Unreleased

### Added

- Add an independent **Locate Device DSL** toolbar and Tools-menu action for opening the
  ViewCompose source currently visible in a debuggable app.
- Show a device chooser when multiple Android devices are online and a source chooser for
  equally visible multi-pane DSL sessions.
- Resolve bounded source candidates so shared scaffold chrome does not hide the content DSL.

## 1.0.1 — 2026-08-03

### Fixed

- Restrict Marketplace compatibility to Android Studio by declaring
  `com.intellij.modules.androidstudio` explicitly.
- Verify the advertised `261.*` window against Quail 2 Patch 1, Quail 3, and the latest Quail 4
  Canary within that window before packaging a release.

## 1.0.0 — 2026-08-01

The first stable ViewCompose Preview release, targeting Android Studio
`AI-261.25134.95.2612.15914620`.

### Highlights

- Static previews rendered by Android Layoutlib without loading application code into Studio.
- Source-to-preview and preview-to-source navigation for DSL nodes.
- Light/dark, locale, layout-direction, density, font-scale, and device-size configurations.
- Detailed and all-previews views with bounded high-resolution disk caches.
- Incremental source-save refresh, explicit incremental refresh, and full rebuild controls.
- Native View, layout, VNode structure, composition, and patch diagnostics.
- Layout bounds, clipping diagnostics, source-aware selection, zoom, pan, and trackpad gestures.
- Controlled Layoutlib worker reuse with isolation, invalidation, retirement, and fallback guards.
- Hidden-panel invalidation retention and duplicate-save compilation suppression.
- Preview annotations and tooling tasks excluded from non-debuggable Android output.

### Compatibility

- Requires Android Studio build `261.25134` or newer; this release is validated against
  `AI-261.25134.95.2612.15914620`.
- Uses preview protocol version 1.
