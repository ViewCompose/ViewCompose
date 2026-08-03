# Task-list tutorial application

This non-published Android application backs the progressive task-list tutorials. It uses only
public ViewCompose APIs and deliberately keeps each tutorial stage as a compiled screen function.

- `TaskListFoundationsScreen` teaches snapshot state, layout, modifiers, and events.
- `TaskListInputScreen` evolves the same model into text entry and a keyed lazy collection.
- `TaskListThemeNavigationScreen` adds semantic theme tokens and list-detail navigation.
- `TaskListCompleteScreen` integrates overlays, a native `TextView`, animation, gestures, explicit
  collection policies, and sampled host diagnostics.
- `MainActivity` runs the complete stage with explicit overlay and diagnostic host wiring.
- `TaskListAppTest` verifies state, navigation, interop, overlay deletion, and diagnostics through
  real Android Views.

Run the local checks with:

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:task-list:assembleDebug
./gradlew :samples:task-list:compileDebugAndroidTestKotlin
```

Run the behavior check on a connected device or emulator with:

```bash
./gradlew :samples:task-list:connectedDebugAndroidTest
```

The canonical series starts at
[`docs/tutorials/task-list-foundations.md`](../../docs/tutorials/task-list-foundations.md) and ends
at
[`docs/tutorials/task-list-performance-and-diagnostics.md`](../../docs/tutorials/task-list-performance-and-diagnostics.md).
