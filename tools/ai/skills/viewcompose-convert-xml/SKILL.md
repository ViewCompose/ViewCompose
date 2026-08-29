---
name: viewcompose-convert-xml
description: Convert a supported Android layout XML source to deterministic ViewCompose Kotlin, then integrate it with compile-backed evidence. Use only when ViewCompose is the requested target; do not claim automatic conversion for custom Views, Data Binding, or behavior outside the supported subset.
---

# Convert Android XML to ViewCompose

Produce reviewable ViewCompose code while preserving every unsupported boundary and caller-owned
resource, state, and behavior decision.

## Exact version and evidence

1. Select the exact framework version lane and bundle identity. When a repository is in scope, use
   `analyze_project` to confirm its ViewCompose coordinates and migration context without executing
   the project build.
2. Call `convert_xml_to_viewcompose` in `generate` mode for the exact XML source and logical path.
   Inspect its Design IR, unsupported diagnostics, resource/state bindings, source mapping, and
   call-site review before changing the project. Never fabricate, guess, or substitute an API or
   behavior to bypass a blocked fragment.
3. For an unsupported component or usage shape, use `get_component_reference` and `get_sample` only
   to prepare an explicit manual migration plan. Retrieval does not turn unsupported XML semantics
   into an automatic-conversion claim.
4. If project writes were requested, integrate only the generated function and the caller bindings
   needed by that layout. Preserve resource ownership, stable keys, state restoration decisions,
   listeners, ViewBinding references, adapters, and imperative mutations as explicit review work;
   do not invent missing behavior.
5. Call `convert_xml_to_viewcompose` in `compile` mode for the unchanged generated result. After any
   integration edit, run `validate_code` in static and compile modes over the final bounded code.
   Converter compilation proves the isolated generated function, not an unvalidated call site.
6. Use `render_preview` and `diagnose_layout` only when an allowlisted Preview covers the migrated
   code. Otherwise finish at compiled evidence and state that visual or behavioral parity remains
   unproven.
7. Deliver the source fingerprint, generated-code fingerprint, framework and compiler identity,
   preserved bindings, unsupported fragments, call-site checklist, diagnostics, and maximum
   evidence actually achieved.

## Stop and authority

Project writes are authorized only by the user's conversion or migration request. Do not replace
the original XML, remove ViewBinding usage, add dependencies, or broaden the migration without that
authority. Stop when conversion is blocked, when a caller-owned behavior needs a product decision,
or when the same diagnostic repeats without new evidence. Return the preserved source and manual
work instead of weakening the evidence label or claiming migration success.
