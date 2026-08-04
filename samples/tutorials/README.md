# Capability tutorial samples

This non-published Android application backs the independently readable ViewCompose tutorials.
Every capability has one self-contained Activity file; tutorial files do not share application
models or build a progressive showcase application.

All ViewCompose dependencies use the published `com.viewcompose:*:0.1.0-alpha01` Maven
coordinates. The module includes the union of tutorial dependencies so the repository can compile
all files in one quality-gate task. Each tutorial page lists only the complete subset needed for
that specific Activity.

Run the local checks with:

```bash
./gradlew verifyTutorialSamples
./gradlew :samples:tutorials:assembleDebug
./gradlew :samples:tutorials:compileDebugAndroidTestKotlin
```

Run the state behavior check on a connected device or emulator with:

```bash
./gradlew :samples:tutorials:connectedDebugAndroidTest
```

Start at [`docs/tutorials/README.md`](../../docs/tutorials/README.md) and choose any capability.
