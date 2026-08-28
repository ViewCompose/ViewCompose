---
schema_version: 2
document_id: module.viewcompose-preview-worker-host
doc_type: module
owner:
  kind: module
  id: viewcompose-preview-worker-host
version_lane: released
capability_ids:
  - preview.worker
artifact_ids:
  - viewcompose-preview-worker-host
sample_ids:
  - module.preview-worker-execute
coordinate: com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha03
minimal_usage_sample_id: module.preview-worker-execute
---

# Preview Worker Host

`viewcompose-preview-worker-host` is the standalone JDK 21+ process boundary that owns mutable
Layoutlib state outside Gradle and Android Studio. The Gradle plugin resolves it on the dedicated
worker configuration; applications must not place it on an Android runtime classpath.

{/* compiled-region source="viewcompose-preview-worker-host/src/test/samples/com/viewcompose/preview/worker/samples/PreviewWorkerHostSamples.kt" region="preview-worker-execute" sample_id="module.preview-worker-execute" build_target=":viewcompose-preview-worker-host:compileTestKotlin" */}
```kotlin
fun executeWorkerCommandSample(commandJsonFile: File): PreviewRenderResponse {
    return PreviewWorkerHost.execute(commandJsonFile)
}
```

## Process and isolation contract

One-shot mode accepts one command JSON file; a top-level command collection selects a bounded,
sequential batch. Each command validates protocol, module/variant/fingerprint, Layoutlib roots, and
exported build inputs, prepares one Paparazzi/Layoutlib SDK, invokes the runner reflectively, and
tears down in `finally`.

Reloadable project code enters a fresh child `URLClassLoader` for each command. It becomes the
thread context loader only during that render, then the previous loader is restored and the child
is closed on success or failure. Permanent host/Layoutlib classpaths contain no application code.
Non-fatal failures become structured responses after decoding; malformed JSON, filesystem
publication failure, thread death, and out-of-memory conditions may terminate the process.

Server mode uses an ephemeral loopback socket plus a random token, exact protocol version, and
Layoutlib compatibility fingerprint. It retires after 120 seconds idle, 24 commands, 768 MiB used
heap, any failed render/invalid request, or explicit shutdown. Endpoint replacement is token-safe.
Each batched render still receives a fresh application class loader and response file.

- Stability: **Alpha** executable tooling infrastructure.
- Treat endpoint files as credentials and require loopback plus token.
- Test class-loader restoration/closure, atomic response publication, compatibility fingerprints,
  idle/count/heap/failure retirement, and explicit shutdown with
  `:viewcompose-preview-worker-host:test`.

See [Preview Core](../viewcompose-preview-core/README.md),
[Gradle Plugin](../viewcompose-preview-gradle-plugin/README.md), and the
[generated API reference](https://docs.viewcompose.com/api/viewcompose-preview-worker-host/current/).
