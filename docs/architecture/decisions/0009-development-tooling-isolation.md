# ADR-0009: Development tooling isolation and request-driven inspection

- Status: Accepted
- Date: 2026-08-13

## Context

ViewCompose uses Android View as its rendering engine, so development tooling can accidentally
share the same callbacks and main-thread budget as application rendering. The first running-device
DSL locator demonstrated this failure mode. Its implementation lived in `viewcompose-host-android`,
registered global-layout and scroll listeners for every eligible render session, and rebuilt a
process report after each callback. Although file replacement happened on a worker, view
inspection, session snapshots, and JSON serialization happened synchronously on the UI thread.

On the same Samsung SM-G991B running Android 13 with SurfaceFlinger active at 60 Hz, the Demo
home-list frame CPU P50 moved
from approximately 5--7 ms in the earlier prerelease to 11--12 ms after this tooling entered the
Host. Removing only the scroll callback restored approximately 7 ms. The feature was debug-only in
behavior, but `debuggable` alone did not isolate its cost: every ordinary debug session paid for a
tool that no IDE had requested.

Preview, source navigation, inspectors, diagnostics, and future developer aids remain important.
The architecture must preserve them without allowing optional tooling to become an implicit part
of the rendering engine or its hot paths.

## Decision

1. Development tooling remains downstream of all runtime layers. A runtime artifact may expose a
   platform-neutral, optional inspection port, but it cannot contain a concrete IDE protocol,
   transport, report writer, or developer-tool lifecycle implementation.
2. Tooling activation has three independent gates: the optional tooling artifact is present, the
   application is debuggable, and a valid explicit IDE request is received. `debuggable` grants
   permission; it is not an instruction to perform continuous work.
3. The inactive runtime tax is bounded to neutral nullable-port checks and bounded metadata capture
   explicitly documented by that port. It includes no tooling-owned thread, file I/O,
   serialization, stack capture, View-tree traversal, or listener registered on a scroll, layout,
   draw, touch, animation-frame, or recomposition hot path.
4. The running-device DSL locator is owned by the optional `viewcompose-preview` artifact, which
   applications add with `debugImplementation`. `viewcompose-host-android` discovers only the
   neutral `RenderSessionInspectionTooling` service. Absence, ambiguity, or failure of the service is a
   diagnostic no-op and cannot fail application rendering.
5. `RenderSessionInspectionPolicy` separates passive session registration from source capture.
   Source identity may be captured once, with strict bounds, when an eligible Host, navigation, or
   pager-page session first commits in a debuggable process with the optional artifact installed.
   This is the only request-independent exception. Lazy-item, overlay, and preview sessions may be
   registered with weak mounted-node inspection but capture no source stack. Neither policy retains
   a node tree, installs a View listener, starts a worker, or performs report I/O.
6. Live View state is sampled only after an explicit IDE request. The locator uses a nonce-bearing
   request/response protocol: the IDE sends an explicit debug-only Android request, the process
   snapshots current weakly held sessions once, and the response includes the same nonce. A stale
   response never satisfies a later request.
7. Request validation and View inspection run on the Android main thread. Bounded serialization and
   atomic private-cache replacement may move to a lazily created worker. The request path may be
   temporarily expensive because the developer invoked it, but it cannot leave recurring work
   behind.
8. Tooling transports are least-privilege and debug-scoped. The device locator receiver exists only
   when the optional artifact is packaged, requires the platform `DUMP` permission held by ADB
   shell, verifies the application is debuggable, accepts only a bounded nonce, and writes only to
   the application's private cache.
9. Architecture is enforced mechanically. Runtime modules cannot depend on tooling modules or
   contain concrete locator protocol markers. Tooling production code cannot register
   high-frequency View callbacks without an explicit reviewed allowlist. The Demo release runtime
   classpath cannot contain tooling artifacts.
10. A tooling change that can execute in an application process must add deterministic inactivity,
    request cardinality, stale-response, lifecycle, and failure-isolation tests. If it observes or
    can affect a hot path, it also requires a same-device debug benchmark. For the same device,
    build, workload, thermal state, and refresh rate, median frame CPU may regress by at most both
    5% and 0.3 ms, and P95 by at most both 10% and 0.8 ms. Idle scrolling must perform zero
    tooling report writes.

## Public API and module impact

- `viewcompose-ui-foundation` owns the Q3 neutral `RenderSessionInspectionTooling`,
  `RenderSessionInspectionPolicy`, and `RenderSessionInspectionRegistration` contract. Its absence
  remains a no-op. This alpha-line hard cut replaces the former source-only port.
- `viewcompose-host-android` owns only Android platform installation and neutral service discovery;
  it no longer owns the device locator implementation or protocol.
- `viewcompose-preview` owns the debuggable-process locator service, explicit request receiver,
  live-session snapshot, response serialization, and private report lifecycle.
- the Android Studio plugin owns request creation, nonce validation, response polling, source
  resolution, and user-facing failure handling.

No application-facing DSL API changes. Consumers that want running-device source navigation must
keep `viewcompose-preview` in a debug, test, or dedicated tooling configuration. Release builds need
no preview artifact.

## Consequences

- Ordinary runtime artifacts cannot silently acquire a concrete developer-tool loop.
- Debug builds that include previews retain the locator, but scrolling and layout no longer trigger
  snapshots or report writes.
- A source-locator click performs one bounded inspection round trip and may take slightly longer
  than reading a continuously refreshed file.
- Service discovery adds a process-start lookup in the Android Host. The result is immutable and
  nullable, so it adds no per-frame discovery.
- The one-time bounded source-candidate capture remains an explicit trade-off for accurate page
  navigation without re-running application composition when the IDE request arrives.
- Tooling that genuinely requires continuous observation must receive a new ADR, a narrow
  activation lifetime, an explicit allowlist entry, and benchmark evidence. Convenience is not an
  exception.

## Rejected alternatives

### Keep the implementation in Host and guard it with `FLAG_DEBUGGABLE`

Rejected because most debug sessions do not have an active IDE inspection request. It would retain
the original coupling and allow recurring work to regress application behavior again.

### Keep the implementation in Host behind a mutable global flag

Rejected because the implementation, transport, callbacks, and failure modes would still ship in
the runtime artifact. A forgotten or incorrectly initialized flag would reactivate the defect, and
release classpath isolation would remain unprovable.

### Patch only the scroll listener

Rejected because global-layout and focus publication would preserve continuous main-thread work,
while removing all listeners without a request protocol would leave Pager and multi-pane reports
stale. It fixes one symptom without defining an ownership boundary.

### Continuously publish on a background thread

Rejected because Android View state must be inspected on the main thread and cross-thread snapshots
would either be unsafe or still require main-thread collection. Moving JSON or file I/O alone does
not remove callback pressure.

### Recompose the page only when the IDE requests a source

Rejected because diagnostic navigation must not re-run application composition, commit effects, or
create candidate remembered resources. A bounded source identity captured at the first successful
commit is less intrusive and makes the later request read-only.

## Validation and rollout

The change is retained only when all of the following stay green:

1. `verifyDevelopmentToolingIsolation` is part of `qaQuick` and validates ownership, prohibited hot
   callbacks, and release-classpath exclusion.
2. unit tests prove that registration, rendering-active changes, layout, and scroll cause no report
   write; one valid request produces one nonce-matched response; invalid requests write nothing;
   stale responses never satisfy a later nonce; and disposal releases weak session state.
3. Android Studio plugin tests cover request command construction, response polling, timeout,
   process/package validation, protocol bounds, and multi-pane/deepest-session selection.
4. the Demo debug APK retains the request-driven **Inspect Device Diagnostics** capability, while
   the release APK contains neither the request receiver nor inspector implementation.
5. same-device debug measurements compare the affected home-list workload before and after the
   change under the thresholds above, with zero inspector writes during idle scrolling.
