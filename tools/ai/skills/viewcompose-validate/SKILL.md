---
name: viewcompose-validate
description: Validate ViewCompose Kotlin before delivery using deterministic static and hermetic compile evidence, with Preview diagnosis only for covered allowlisted targets. Do not use for generic Kotlin that does not depend on ViewCompose.
---

# Validate ViewCompose Before Delivery

Decide whether supplied ViewCompose code is deliverable and return the exact evidence identity for
that decision.

## Exact version and evidence

1. Select the exact framework identity and derive the smallest governed artifact allowlist from
   retrieved ownership facts. Never use the inspected project's classpath or a movable version.
2. Run `validate_code` in static mode first, repair actionable stable diagnostics when fixes are in
   scope, and then run compile mode. Static success alone is not a valid delivery verdict.
3. Treat compilation as successful only when the result names the pinned compiler lane and output
   fingerprint. Preserve every warning or unsupported boundary.
4. Call `render_preview` and `diagnose_layout` only for an allowlisted target that covers the exact
   code. Otherwise finish at compiled evidence and state why render evidence is unavailable.
5. Return pass/fail, maximum achieved evidence, framework and bundle identity, artifact allowlist,
   stable diagnostics, compiler fingerprint, and render fingerprint when applicable.

## Stop and authority

This workflow is read-only unless it is nested inside an already authorized implementation or fix
request. Never add dependencies, broaden the snippet, execute a project-selected build, or weaken
validation to obtain a pass. Stop when the same diagnostic repeats without new evidence or the
input requires unsupported resources, artifacts, or render targets; return the boundary as the
result rather than fabricating success or implicitly upgrading evidence.
