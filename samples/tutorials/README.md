# Capability tutorial samples

This non-published Android application backs the independently readable ViewCompose tutorials.
Every capability has one self-contained Activity file; tutorial files do not share application
models or build a progressive showcase application.

The base application depends only on the published
`com.viewcompose:viewcompose-host-android:0.1.0-alpha03` coordinate. Runtime, UI-contract, widget,
and text APIs are available transitively; renderer, lifecycle, and ViewModel integrations remain
host runtime details unless a caller deliberately uses their advanced APIs. The module adds only
the feature artifacts used across the tutorials, each at its independently published version, so
the repository can compile all files in one quality-gate task. Each tutorial page lists only the
complete subset needed for that specific Activity.

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
