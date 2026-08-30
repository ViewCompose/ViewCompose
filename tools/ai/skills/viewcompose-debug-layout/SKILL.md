---
name: viewcompose-debug-layout
description: Diagnose a ViewCompose layout from an allowlisted compiled Preview and its structured post-layout facts. Use for clipping, zero-size, or text-layout issues, not screenshot comparison or arbitrary image analysis.
---

# Debug a ViewCompose Layout

Explain renderer-measured layout facts and, when requested, verify a focused fix against the same
target and configuration.

## Exact version and evidence

1. Call `render_preview` for the exact allowlisted target and requested bounded configuration.
   Rendering an unrelated target is not evidence for the affected layout.
2. Call `diagnose_layout` for that same target and configuration. Use its source-aware bounds,
   clipping ancestor, text metrics, severity, and output fingerprint; do not parse an arbitrary tree
   path supplied by the caller.
3. Explain only the returned structured facts. Intentional clipping and ellipsis remain facts to
   confirm, not automatic defects. A clean result does not prove overlap, accessibility, touch
   target, design intent, or pixel similarity.
4. If the user asks for a fix, resolve the affected API with `get_component_reference` and
   `get_sample`, make the smallest in-scope change, then use `validate_code` compile mode before
   rendering and diagnosing the same target again.
5. Deliver before/after render fingerprints, diagnostic codes, source locations, configuration,
   and any remaining limitation. The maximum evidence is rendered, never compared.

## Stop and authority

Diagnosis alone is read-only; modify code only when fixing was requested. Stop when the target is
not allowlisted, the changed code is not covered by that target, or the same diagnostic repeats
without new evidence. Do not substitute a model or screenshot judgment for missing renderer facts,
and do not claim automatic repair success without a new compile and render identity.
