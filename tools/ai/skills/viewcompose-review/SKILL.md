---
name: viewcompose-review
description: Review ViewCompose Kotlin for real API use, lifecycle, accessibility, and validation evidence. Use for review or diagnosis; do not modify files unless the user also asks for fixes.
---

# Review ViewCompose Code

Return evidence-backed findings ordered by impact, with source locations and concrete corrections.

## Exact version and evidence

1. Preserve the exact framework identity. Use `analyze_project` when repository-wide coordinates,
   artifacts, or migration context affect the review.
2. Resolve questioned symbols with `get_api_reference`. Use `get_component_reference` and
   `get_sample` when overload defaults, receiver rules, or correct usage shape are relevant.
3. Run `validate_code` in static mode for bounded rule findings. Run compile mode when the supplied
   code is a supported complete snippet and exact artifact ownership is known.
4. Separate proven compile errors and stable rule findings from architectural suggestions. Do not
   infer layout geometry, performance, runtime lifecycle behavior, or visual correctness from a
   static scan.
5. Report each finding with severity, diagnostic code, source, affected artifact/capability when
   available, and the evidence level. If no finding is proven, say so and retain the stated
   limitations.

## Stop and authority

A review request is read-only. Offer a patch, but edit only after the user asks for a fix. Do not
run a project-selected build or add dependencies to make a snippet pass. Stop if the same
diagnostic repeats without new evidence, or if correctness depends on unavailable application
behavior; report the missing evidence rather than guessing or upgrading static evidence to
compiled.
