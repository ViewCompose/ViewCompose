# ViewCompose Preview Worker Host

This module is the standalone JVM process boundary for static preview rendering. It accepts one
`PreviewWorkerCommand` JSON file, reconstructs the Android resource environment from the exported
build manifest, starts one pinned Layoutlib SDK session, invokes the Android runner reflectively,
and atomically writes one `PreviewRenderResponse`.

The host intentionally has no dependency on Gradle, Android Studio, or the Android runner binary.
Those artifacts belong on the launched process classpath. This keeps application classes and
Layoutlib out of both the Gradle daemon and the future IDE plugin classloader.

The executable requires JDK 17 or newer because the pinned Paparazzi/Layoutlib distribution targets
Java 17.
