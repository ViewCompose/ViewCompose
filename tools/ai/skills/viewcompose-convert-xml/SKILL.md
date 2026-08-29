---
name: viewcompose-convert-xml
description: Convert supported Android layout XML or explicit Android project context to deterministic ViewCompose Kotlin, then integrate it with compile-backed evidence. Use only when ViewCompose is the requested target; do not claim automatic conversion for custom Views, Data Binding, or behavior outside the supported subset.
---

# Convert Android XML to ViewCompose

Produce reviewable ViewCompose code while preserving every unsupported boundary and caller-owned
resource, state, and behavior decision.

## Exact version and evidence

1. Select the exact framework version lane and bundle identity. When a repository is in scope, use
   `analyze_project` to confirm its ViewCompose coordinates and migration context without executing
   the project build.
2. Select exactly one converter input form:
   - When the layout is inside the project in scope, prefer project form with the absolute project
     root, project-relative layout path, ordered explicit resource roots, and ordered Kotlin/Java
     source roots. Do not guess variants or scan undeclared roots.
   - Use source form for pasted or standalone XML with its bounded logical path. Record that
     resources, styles, ViewBinding, listeners, and imperative call sites were not resolved from a
     project.
3. Call `convert_xml_to_viewcompose` in `generate` mode. Inspect its Design IR, unsupported
   diagnostics, resource/state bindings, source mapping, and call-site review before changing the
   project. For project form, also inspect the context fingerprint, coverage, resource/style
   evidence, layout-dependency graph and include-edge provenance, confidence on every call site,
   and the explicit `not-proven` completeness result. Confirm that every included file came from an
   explicitly ordered default `layout/` root and that an expanded `merge` retained child order.
   Never fabricate, guess, or substitute an API or behavior to bypass a blocked fragment.
4. For an unsupported component or usage shape, use `get_component_reference` and `get_sample` only
   to prepare an explicit manual migration plan. Retrieval does not turn unsupported XML semantics
   into an automatic-conversion claim.
5. If project writes were requested, integrate only the generated function and the caller bindings
   needed by that layout. Preserve resource ownership, stable keys, state restoration decisions,
   listeners, ViewBinding references, adapters, and imperative mutations as explicit review work;
   do not invent missing behavior.
6. Call `convert_xml_to_viewcompose` in `compile` mode with the same source or project input. After
   any integration edit, run `validate_code` in static and compile modes over the final bounded
   code. Converter compilation proves the isolated generated function, not an unvalidated call
   site.
7. When the generated function has only supported `String` and `TextFieldState` parameters, call
   `convert_xml_to_viewcompose` in `render` mode with the same input and one explicit ordered
   `previewBindings` entry for every reported parameter. Never guess a resource value or initial
   state. Missing, extra, reordered, or mismatched bindings must remain blocked. An `ImageSource`
   may render only from explicitly provided exact PNG bytes with matching size, SHA-256, and
   dimensions; never pass a path, URL, project resource ID, XML drawable, or invented substitute.
   Use the separate `render_preview` and `diagnose_layout` tools only for their own allowlisted
   repository Preview targets.
8. Deliver the source and optional project-context fingerprints, generated-code fingerprint,
   framework and compiler identity, preserved bindings, unsupported fragments, call-site inventory
   and completeness, diagnostics, and maximum evidence actually achieved.

## Stop and authority

Project writes are authorized only by the user's conversion or migration request. Do not replace
the original XML, remove ViewBinding usage, add dependencies, or broaden the migration without that
authority. Stop when conversion is blocked, when a caller-owned behavior needs a product decision,
or when the same diagnostic repeats without new evidence. Return the preserved source and manual
work instead of weakening the evidence label or claiming migration success.
