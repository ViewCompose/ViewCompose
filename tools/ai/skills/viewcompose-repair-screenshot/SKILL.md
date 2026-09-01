---
name: viewcompose-repair-screenshot
description: Diagnose a verified ViewCompose screenshot regression and prepare one attended single-property source repair with explicit recovery. Use when the user asks to repair generated ViewCompose Kotlin from accepted visual evidence; do not use for arbitrary Kotlin edits, unattended writes, or general screenshot-to-UI generation.
---

# Repair a ViewCompose Screenshot Regression

Use `prepare_screenshot_repair` for evidence and request preparation. Project source may change only
through the separately invoked `viewcompose-repair` command after exact controlling-terminal
confirmation.

## Exact version and evidence

1. Confirm that the installed AI tooling matches the project's exact ViewCompose dependency profile.
   Do not upgrade to a newer Knowledge Bundle to obtain a repair. Use `analyze_project` when the
   dependency identity is uncertain, `get_component_reference` for the selected property contract,
   and `validate_code` for a separately requested compile check. Use
   `generate_screenshot_viewcompose` only to reproduce the unchanged screenshot lineage; do not
   substitute its output for the six repair gates.
2. Preserve the user's accepted baseline screenshot reference and its exact resolved Design IR. Call
   `prepare_screenshot_repair` with `operation: evaluate` for that baseline and for the current
   candidate, using unchanged generation, Preview binding, and canonical pixel-reference lineage.
3. Continue only when both records contain all six gates and the baseline represents an explicit
   human-accepted design. Call the tool with `operation: propose`. The released repair subset must
   select exactly one generated literal `text` or `hint` property and must strictly improve the same
   pixel denominator without regressing safety, compilation, rendering, semantics, or structure.
4. Show the proposal, both evidence fingerprints, changed node/property, and complete bounded diff.
   Obtain explicit human acceptance of the baseline and this exact proposal. Build the versioned
   authorization record from those real review identities and receipts; never invent an approval,
   reviewer, source revision, receipt, or fingerprint.

## Prepare and apply

5. Call `prepare_screenshot_repair` with `operation: prepare`, the exact authorization and evidence,
   the current resolved result, project root, root-relative generated Kotlin path, generation request,
   Preview bindings, and pixel reference. The call may store owner-only recovery state outside the
   project, but it must report `sourceWritePerformed: false` and return one content-addressed request.
6. Give the user the returned `viewcompose-repair show <request> --pretty` command. After review, give
   them `viewcompose-repair apply <request> --pretty`. Do not run the apply command through MCP, type
   its confirmation for the user, pipe approval through stdin, set an approval variable, or add
   `--yes`, `--force`, a token, or a reusable grant.
7. Interpret the immutable receipt. `applied-verified` means the exact committed bytes passed static,
   compile, Preview, semantic/geometry, and eligible pixel checks. A validation or evidence failure
   leaves the candidate in place and never authorizes an automatic rollback. Use
   `viewcompose-repair recover <request> --pretty` after interruption; recovery only reconciles
   durable state and current bytes.
8. If the user explicitly requests reversal, show the current receipt and give them
   `viewcompose-repair rollback <request> --pretty`. Rollback has its own terminal confirmation and
   must refuse to overwrite any later user edit.

## Stop and authority

Stop on version, root, symlink, hard-link, preimage, file-identity, span, candidate, diff, evidence,
authorization, expiry, secure-filesystem, concurrency, or recovery drift. Do not broaden the repair
to a whole file, import, declaration, callback, resource guess, raw patch, arbitrary source, second
file, commit, push, or pull request. Return the diagnostic and the new evidence or human decision
needed. Stop when the same diagnostic repeats without new evidence; repeated execution is not
progress.
