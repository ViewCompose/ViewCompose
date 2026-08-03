# ViewCompose Counter Sample

This module is the smallest runnable ViewCompose application in the repository. It backs the
[Build your first application](../../docs/tutorials/getting-started.md) tutorial and intentionally
uses only the runtime, UI contract, core widgets, and Android host.

Build it from the repository root:

```bash
./gradlew :samples:counter:assembleDebug
```

Run its device test with a connected device or emulator:

```bash
./gradlew :samples:counter:connectedDebugAndroidTest
```
