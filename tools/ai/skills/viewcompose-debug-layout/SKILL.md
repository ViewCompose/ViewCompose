---
name: viewcompose-debug-layout
description: Diagnose a ViewCompose layout from an allowlisted compiled Preview and its structured post-layout facts. Use for clipping, zero-size, or text-layout issues, not screenshot comparison or arbitrary image analysis.
---

# Debug a ViewCompose Layout

Explain renderer-measured layout facts and, when requested, verify a focused fix against the same
target and configuration.

## Exact version and evidence

1. Identify the evidence owner. For an XML- or screenshot-generated screen, call its generation tool
   in render/compare mode and use the returned `preview.layoutDiagnosis`; the installed package owns
   that generated source and its content-addressed target. Call `render_preview` and
   `diagnose_layout` directly only when the request names an exact separately allowlisted fixed
   target. Rendering an unrelated or arbitrary application target is not evidence.
2. Use only the accepted result's source-aware bounds, clipping ancestor, text metrics, severity,
   structure, and output fingerprint. Do not parse an arbitrary tree path supplied by the caller.
3. Explain only the returned structured facts. Intentional clipping and ellipsis remain facts to
   confirm, not automatic defects. A clean result does not prove overlap, accessibility, touch
   target, design intent, or pixel similarity.
4. If the user asks for a fix, resolve the affected API with `get_component_reference` and
   `get_sample`, make the smallest in-scope change, then use `validate_code` compile mode before
   rendering and diagnosing the same generated or fixed target again.
5. Deliver before/after render fingerprints, diagnostic codes, source locations, configuration,
   and any remaining limitation. Report `compared` only when the owning XML/screenshot workflow
   returned comparison evidence; direct fixed-target diagnosis reaches `rendered`.

## Stop and authority

Diagnosis alone is read-only; modify code only when fixing was requested. Stop when the target is
not allowlisted, the changed code is not covered by that target, or the same diagnostic repeats
without new evidence. Do not substitute a model or screenshot judgment for missing renderer facts,
and do not claim automatic repair success without a new compile and render identity.
