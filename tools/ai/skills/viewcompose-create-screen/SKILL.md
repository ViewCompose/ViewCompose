---
name: viewcompose-create-screen
description: Create or change an Android screen using ViewCompose with exact API retrieval and compile-backed delivery. Use only when the user chose ViewCompose, not Jetpack Compose or XML as the target framework.
---

# Create a ViewCompose Screen

Produce project-conforming ViewCompose code that reaches the deepest evidence level actually
available for that code.

## Exact version and evidence

1. When a project is in scope, call `analyze_project` to establish exact ViewCompose coordinates,
   configuration, owning artifacts, and migration signals without executing the project build.
2. Discover components with `search_component`, resolve every selected component with
   `get_component_reference`, and obtain at least one relevant compiled example through
   `get_sample`. Retrieve before writing; never infer a ViewCompose API from a similar Compose API.
3. Implement only the screen and resources requested by the user, preserving local architecture,
   resource usage, state ownership, accessibility decisions, and existing unrelated changes.
4. Run `validate_code` in static mode while iterating, then in compile mode with only the exact
   governed artifact allowlist. A parsing or static-only pass is not delivery success.
5. Use `render_preview` and then `diagnose_layout` only when an allowlisted compiled Preview target
   actually covers the changed code. Never use an unrelated Preview as evidence for the screen.
6. Deliver the code with artifact/version, bundle fingerprint, compiler lane, diagnostics, and any
   render fingerprint. State the maximum achieved evidence: compiled by default, rendered only
   when the changed UI was truly rendered.

## Stop and authority

Project writes are authorized only by the user's create/change request. Do not add dependencies,
resources, screens, or migrations outside that scope. Repair from structured diagnostics, but stop
when the same diagnostic repeats without new evidence or when the next step needs a user product
choice, unsupported API, arbitrary project build, or non-allowlisted render target. Report that
boundary instead of weakening the evidence label or fabricating an API.
