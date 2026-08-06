# Capability tutorial samples

This non-published Android application backs the independently readable ViewCompose tutorials.
Every capability has one self-contained Activity file; tutorial files do not share application
models or build a progressive showcase application.

The base application depends only on
`com.viewcompose:viewcompose-android:0.1.0-alpha01`. Runtime, UI contract, UI Foundation, engine,
Material 3 theme, Lifecycle, and ViewModel APIs are available transitively. The module adds only
the optional feature artifacts used across the tutorials. `qaQuick` publishes the current checkout
to `build/maven-repository` before compiling these Maven-coordinate consumers, so new artifact
names and generated POM edges are verified before their first Central release. Each tutorial page
lists only the complete subset needed for that Activity.

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
