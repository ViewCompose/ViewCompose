---
title: Capability tutorials
sidebar_position: 2
slug: /tutorials
---

# ViewCompose capability tutorials

Choose the capability you need. Every tutorial is a standalone starting point: it lists all
required Maven dependencies first, uses one self-contained Activity file, and can be compiled
without completing another chapter.

| I want to… | Tutorial | Extra ViewCompose artifact |
| --- | --- | --- |
| update UI from state | [Use state and events](./state-and-events.md) | none |
| arrange content | [Use layouts and modifiers](./layouts-and-modifiers.md) | none |
| accept editable text | [Use text input](./text-input.md) | `viewcompose-text-core` |
| show a scrolling collection | [Use a lazy list](./lazy-lists.md) | none |
| follow light, dark, and semantic colors | [Use themes](./theming.md) | none |
| move between screens | [Use navigation](./navigation.md) | `viewcompose-navigation-core`, `viewcompose-navigation` |
| show a dialog | [Use overlays](./overlays.md) | `viewcompose-overlay-android` |
| embed a native View | [Use AndroidView](./android-view.md) | none |
| show and hide content with motion | [Use AnimatedVisibility](./animation.md) | `viewcompose-animation` |
| handle taps and long presses | [Use gestures](./gestures.md) | `viewcompose-gesture` |
| tune a large lazy list | [Tune lazy-list performance](./lazy-list-performance.md) | none |
| inspect renderer work | [Read render diagnostics](./render-diagnostics.md) | none |

If you have not created a ViewCompose application yet, [build the first application](./getting-started.md).
That page is optional setup help, not a prerequisite for the capability tutorials.

## Executable sample contract

The [`samples/tutorials`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/tutorials)
module contains one file per tutorial and resolves ViewCompose exclusively from Maven Central.
`verifyTutorialSamples` compiles the module, checks that both languages copy the exact source
region, and verifies each page's dependency declaration.
