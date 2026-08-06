# Android Application Aggregate

`viewcompose-android` is the recommended single dependency for a standard Android application. It
combines the UI foundation, Android host engine, Material 3 theme adapter, Lifecycle integration,
and ViewModel integration, and exposes the Activity and Fragment `setUiContent` entry points.

The aggregate contains no second implementation of those capabilities. Its purpose is dependency
curation and a stable application-facing entry boundary; advanced consumers may still depend on
the narrower artifacts directly.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- Transitive API surface: host engine, UI foundation, Material 3 adapter, Lifecycle integration,
  ViewModel integration, AndroidX Activity, and AndroidX Fragment.
- Material Components is supplied transitively by the Material 3 adapter; applications add it
  directly only when compiling against Material classes themselves.

## Standard entry points

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            Text("Hello from ViewCompose")
        }
    }
}
```

`ComponentActivity.setUiContent` and `Fragment.setUiContent` create a full-size root and provide:

```kotlin
import com.viewcompose.android.setUiContent
```

- Lifecycle and ViewModel owners;
- Android-backed saveable state;
- density, locale, layout direction, and Android context environment values;
- Material 3 theme tokens and dynamic-color policy;
- the animation coroutine context and Choreographer frame clock;
- an injectable overlay host.

Repeated calls dispose the previous session. Fragment sessions follow the current View lifecycle;
Activity sessions end when the Activity is destroyed.

## Dependency rule

Treat `viewcompose-android` as the base application dependency and add optional capabilities such as
navigation, image adapters, overlays, or advanced shadows individually. Do not repeat its
transitive foundation artifacts unless the build intentionally constrains versions or directly
uses their standalone APIs.

## Related documentation

- [Five-layer architecture](../../architecture/decisions/0002-five-layer-runtime-module-architecture.md)
- [Getting started](../../tutorials/getting-started.md)
- [Android host engine](../viewcompose-host-android/README.md)
- [Material 3 adapter](../viewcompose-material3/README.md)

The generated reference is available in the
[`viewcompose-android` API tree](https://docs.viewcompose.com/api/viewcompose-android/current/).

## Compatibility notes

This artifact begins at `0.1.0-alpha01`. The earlier multi-dependency application setup is replaced
by this aggregate in one hard cut. Its entry points exclusively use `com.viewcompose.android`; no
deprecated aggregate predecessor or forwarding package in `com.viewcompose.host.android` exists.
