# ViewCompose preview self-host build

This included build makes `com.viewcompose.preview` available to the root build
without publishing the preview Gradle plugin first.

It compiles the canonical sources from:

- `viewcompose-preview-gradle-plugin/src/main/kotlin`
- `viewcompose-preview-core/src/main/kotlin`

No plugin implementation is copied into this directory. Keep the public
subprojects as the source of truth and use this build only as the root
project's plugin bootstrap.
