# Neutral Android Application Aggregate

`viewcompose-android` is the recommended single dependency for an Android application that wants
to choose its design system explicitly. It combines the UI foundation, neutral Android host
engine, Lifecycle integration, and ViewModel integration, and exposes the Activity and Fragment
`setUiContent` entry points.

The aggregate contains no Material dependency or design-system policy. It includes the neutral
`viewcompose-overlay-android` transport as a runtime implementation dependency. Its purpose is dependency
curation and a stable application-facing host boundary; Material applications use the named
[`viewcompose-material3-android`](../viewcompose-material3-android/README.md) aggregate instead.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- Transitive API surface: host engine, UI foundation, Lifecycle integration, ViewModel
  integration, AndroidX Activity, and AndroidX Fragment.
- Material dependency: none.

## Neutral entry points

```kotlin
import com.viewcompose.android.setUiContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            UiTheme(MyDesignTokens.light()) {
                Text("Hello from ViewCompose")
            }
        }
    }
}
```

`ComponentActivity.setUiContent` and `Fragment.setUiContent` create a full-size root and provide:

- Lifecycle and ViewModel owners;
- Android-backed saveable state;
- density, locale, layout direction, and Android context environment values;
- the animation coroutine context and Choreographer frame clock; and
- the neutral Android overlay transport, with an injectable replacement factory.

They do not resolve Material XML, dynamic color, or design tokens. Without an explicit provider,
content reads the deterministic `UiThemeDefaults.light()` framework baseline. The optional
`rootContext` parameter defaults to the Activity or Fragment context and is shared by the root,
native descendants, and default overlays.

When the root design system requires a different Android Context, resolve that Context first and
pass it to `rootContext`. Switching between root design systems must call `setUiContent` again with
the new Context and token provider so Views are reconstructed under one coherent platform/theme
snapshot. Repeated calls dispose the previous session. Fragment sessions follow the current View
lifecycle; Activity sessions end when the Activity is destroyed.

## Dependency rule

Use `viewcompose-android` with a static or application-owned design system such as
`viewcompose-oneui7`. Use `viewcompose-material3-android` for Android Material XML and dynamic-color
integration. Add optional capabilities such as navigation, image adapters, named overlay presenters, or advanced
shadows individually; do not repeat transitive foundation artifacts unless the build intentionally
constrains versions or directly uses their standalone APIs.

## Related documentation

- [Multi-design-system architecture](../../architecture/design-systems.md)
- [Getting started](../../tutorials/getting-started.md)
- [Android host engine](../viewcompose-host-android/README.md)
- [Material 3 Android integration](../viewcompose-material3-android/README.md)
- [One UI 7 design system](../viewcompose-oneui7/README.md)

The generated reference is available in the
[`viewcompose-android` API tree](https://docs.viewcompose.com/api/viewcompose-android/current/).

## Compatibility notes

The alpha API makes a source-level hard cut: the generally named `setUiContent` no longer accepts
`Material3DynamicColorPolicy` or `Material3ThemeRefreshController`. Material callers replace their
dependency with `viewcompose-material3-android`, import `setMaterial3UiContent`, and keep the same
content body. No all-default deprecated forwarding overload is retained because it would be
ambiguous with the neutral zero-argument entry point.
