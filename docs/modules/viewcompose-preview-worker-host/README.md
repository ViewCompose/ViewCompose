# Preview Worker Host

`viewcompose-preview-worker-host` is the standalone JVM process boundary that owns Layoutlib for
ViewCompose static previews. It keeps mutable platform rendering state outside Gradle and Android
Studio, validates protocol files, isolates reloadable application classes, and publishes structured
responses atomically.

## Artifact and stability

```kotlin
dependencies {
    runtimeOnly("com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The executable protocol is internal tooling infrastructure.
- Runtime: JDK 17 or newer.
- Normal installation: the ViewCompose preview Gradle plugin resolves this artifact; application
  code should not add it to an Android runtime classpath.
- Boundary: the host depends on preview-core and the platform Layoutlib bridge, not application UI
  modules at compile time.

## One-shot execution

The main entry point accepts one worker-command JSON path. A top-level `commands` field selects a
bounded batch; otherwise the file is one command. Commands validate protocol version, module path,
variant, build fingerprint, Layoutlib roots, and every exported build input before rendering.

Each command configures a Paparazzi/Layoutlib SDK from the canonical manifest, prepares it, invokes
the Android runner reflectively, and tears it down in `finally`. Setup, runner, export, and teardown
timings are preserved in the response.

## Class-loader and failure isolation

Reloadable project bytecode enters a fresh child `URLClassLoader` per command. The host installs it
as the thread context loader only for that render, then restores the previous loader and closes the
child even when rendering fails. Layoutlib and host classes remain in the parent process classpath.

Once a request is decoded, non-fatal validation, setup, runner, and export failures become a
source-aware `RenderFailure` response. Thread death and out-of-memory errors escape. Malformed
command/request JSON and filesystem publication failures may fail the process before a response is
available. Response files use temporary-file replacement so clients never observe partial JSON.

## Warm server lifecycle

Server mode binds an ephemeral loopback-only socket and atomically publishes an endpoint containing
protocol version, process ID, random token, port, and compatibility fingerprint. Every client must
present the matching token and protocol version.

The default server retires after 120 seconds idle, 24 processed commands, 768 MiB used heap, any
failed render, an invalid client request, or an explicit shutdown. The endpoint file is deleted only
if it still carries that server's token, preventing an old process from deleting a replacement
server's endpoint.

## Batch behavior

Commands in one protocol batch execute sequentially. Each render still gets its own reloadable class
loader and response file; the shared process only amortizes JVM and retained Layoutlib startup.
Batch size is bounded by preview-core before execution. A structured render failure is returned for
that command and causes a persistent server to retire before accepting more work.

## Testing and operations

- Never place application bytecode on the permanent worker process classpath.
- Verify the compatibility fingerprint whenever retained Layoutlib inputs change.
- Test context-class-loader restoration and closure on success and every failure phase.
- Treat endpoint files as credentials: require loopback transport and the random token together.
- Exercise idle, command-count, heap-pressure, render-failure, and explicit-shutdown retirement.
- Keep worker stdout/stderr diagnostic-only; protocol results belong in response files.

## Related documentation

- [Preview Core module](../viewcompose-preview-core/README.md)
- [Preview Gradle Plugin module](../viewcompose-preview-gradle-plugin/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-preview-worker-host` API tree](https://docs.viewcompose.com/api/viewcompose-preview-worker-host/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes one-shot and loopback server modes, exact protocol/token checks,
fresh reloadable child class loaders, deterministic Layoutlib teardown, atomic responses, and
bounded retirement. These process-level limits may be tuned across alpha releases.
