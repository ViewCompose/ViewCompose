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
2. Before selecting input, declare exactly one migration intent: `capability-probe`, `subtree`, or
   `whole-screen`. For `subtree`, name the owning container and every native sibling that remains.
   For `whole-screen`, inventory the Activity/Fragment root, system chrome, scrolling, overlays,
   advertising/native SDK boundaries, state owners, navigation, animation, application-level
   lifecycle callbacks that query or mutate the root, and every included layout. Unsupported
   conversion may block the declared scope, but it must not silently contract a
   `whole-screen` request into a subtree. Stop or obtain explicit user approval for the narrower
   intent.
3. Select exactly one converter input form:
   - When the layout is inside the project in scope, prefer project form with the absolute project
     root, project-relative layout path, ordered explicit resource roots, and ordered Kotlin/Java
     source roots. Do not guess variants or scan undeclared roots.
   - Use source form for pasted or standalone XML with its bounded logical path. Record that
     resources, styles, ViewBinding, listeners, and imperative call sites were not resolved from a
     project.
4. Call `convert_xml_to_viewcompose` in `generate` mode. Inspect its Design IR, unsupported
   diagnostics, resource/state bindings, source mapping, and call-site review before changing the
   project. For project form, also inspect the context fingerprint, coverage, resource/style
   evidence, layout-dependency graph and include-edge provenance, confidence on every call site,
   and the explicit `not-proven` completeness result. Confirm that every included file came from an
   explicitly ordered default `layout/` root and that an expanded `merge` retained child order.
   Never fabricate, guess, or substitute an API or behavior to bypass a blocked fragment.
5. For an unsupported component or usage shape, use `get_component_reference` and `get_sample` only
   to prepare an explicit manual migration plan. Retrieval does not turn unsupported XML semantics
   into an automatic-conversion claim.
6. If project writes were requested, integrate only the generated function and the caller bindings
   needed by the declared scope. A `whole-screen` migration uses the Activity/Fragment
   `setUiContent` host, accounts for every visible root region and behavior, and removes or clearly
   retires the duplicate legacy screen after acceptance. Native SDK content may remain only as an
   inventoried `AndroidView` boundary; its presence does not permit the surrounding page shell to
   remain undeclared XML. If the declared migration is an explicit `subtree`,
   wrap the `renderInto` content with
   `AndroidResourceEnvironment(context = container.context)`, retain the returned `RenderSession`,
   and dispose it with the host lifecycle. Before editing, inventory every caller-owned state
   source and its update cadence, initial value, throttling, completion, and error behavior. Then
   choose the state boundary explicitly. UI-local transient state uses ViewCompose
   `remember`/`mutableStateOf`; existing ViewModel-owned business state in a standard whole-screen
   host resolves through ViewCompose `viewModel()` and is observed with
   `collectAsStateWithLifecycle()`. Do not mirror the returned state into a second `MutableState`.
   External Activity/Fragment collection is reserved for an explicit embedded subtree or a proven
   owner constraint; in that case retain one session, store the latest immutable snapshot, and call
   `RenderSession.render()` on the Android main thread after each accepted update. Never create a
   new session for each emission, and stop the collector from rendering after teardown. A fixed
   `UiEnvironment` snapshot is not a substitute because it does not follow configuration changes.
   Preserve resource ownership, stable keys, state restoration decisions, native siblings and
   animations, advertising, navigation, analytics, listeners, ViewBinding references, adapters,
   and imperative mutations as explicit review work; do not invent missing behavior.
7. Call `convert_xml_to_viewcompose` in `compile` mode with the same source or project input. After
   any integration edit, run `validate_code` in static and compile modes over the final bounded
   code. Converter compilation proves the isolated generated function, not an unvalidated call
   site.
8. When the generated function has only supported Preview parameter types, call
   `convert_xml_to_viewcompose` in `render` mode with the same input and one explicit ordered
   `previewBindings` entry for every reported parameter. Never guess a resource value or initial
   state. Missing, extra, reordered, or mismatched bindings must remain blocked. An `ImageSource`
   may render only from explicitly provided exact PNG bytes with matching size, SHA-256, and
   dimensions; never pass a path, URL, project resource ID, XML drawable, or invented substitute.
   Use the separate `render_preview` and `diagnose_layout` tools only for their own allowlisted
   repository Preview targets.
9. Deliver the declared migration intent plus a coverage ledger of migrated, native-boundary,
   retained, blocked, and unverified regions and behaviors. A whole-screen result is incomplete if
   any Activity-owned UI remains only in a hidden legacy layout or if its state path bypasses the
   declared architecture without a recorded exception. Also deliver the source and optional
   project-context fingerprints, generated-code, render-tree, and
   comparison fingerprints, framework and compiler identity, preserved bindings, per-category
   semantic and geometry checks, unsupported fragments, call-site inventory and completeness,
   diagnostics, and maximum evidence actually achieved. Never collapse failed checks into one
   similarity score or upgrade `rendered` to `compared` after a mismatch.
10. For a dynamic surface, verify the initial state, at least one later state, and the original
    completion or navigation behavior. Compilation and one static screenshot do not prove that
    retained updates still render or that legacy side effects remain owned by their original host.

## Existing-project device regression

Before changing an Activity or Fragment, record the exact application variant, unit-test task,
instrumentation task, matched application/test APK pair, device/user/storage/permission state, and
the critical baseline flow. Preserve the exact fixture bytes or their reproducible generator plus a
SHA-256 manifest. After migration, repeat the same flow on the same device state and verify initial
UI, at least one later state, completion or navigation, source-fixture integrity, and crash/ANR
absence. A project-owned Debug no-ad seam may provide deterministic UI evidence only when its
activation remains uncommitted; verify real-ad behavior separately. If the baseline, fixture, APK
pair, or device state differs, label the result unverified rather than claiming regression parity.

## Stop and authority

Project writes are authorized only by the user's conversion or migration request. Do not replace
the original XML, remove ViewBinding usage, add dependencies, or broaden the migration without that
authority. Stop when conversion is blocked, when a caller-owned behavior needs a product decision,
or when the same diagnostic repeats without new evidence. Return the preserved source and manual
work instead of weakening the evidence label or claiming migration success.
