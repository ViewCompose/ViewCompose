# ViewCompose Counter Sample

This module is the smallest runnable ViewCompose application in the repository. It backs the
[Build your first application](../../docs/tutorials/getting-started.md) tutorial and intentionally
keeps its runtime path to the runtime, UI contract, core widgets, and Android host. Debug builds
also expose the same counter screen through the native ViewCompose preview toolchain.

Build it from the repository root:

```bash
./gradlew :samples:counter:assembleDebug
```

Verify that the compiled preview entry remains discoverable:

```bash
./gradlew :samples:counter:verifyCounterPreview
```

Open `CounterPreview.kt` with the ViewCompose Studio plugin to inspect the light and dark variants.
The repository-wide `qaPreview` gate includes this discovery check alongside the shared Paparazzi
snapshot suite.

Run its device test with a connected device or emulator:

```bash
./gradlew :samples:counter:connectedDebugAndroidTest
```
