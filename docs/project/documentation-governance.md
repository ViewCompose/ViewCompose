# Documentation Governance

## Purpose

This document defines where ViewCompose documentation belongs, which files are authoritative, and
how documents move through their lifecycle. The same rules apply to maintainers, contributors, and
AI agents.

The goals are:

1. keep the repository root readable;
2. provide one discoverable source of truth for each subject;
3. distinguish current contracts from temporary plans and historical records;
4. make documentation structure mechanically verifiable;
5. let future documentation-site generation consume a stable directory tree.

## Directory contract

| Location | Content | Lifecycle |
| --- | --- | --- |
| Repository root | `README.md`, `README.zh-CN.md`, community governance, and `AGENTS.md` | Stable, intentionally small |
| `docs/README.md` | Canonical documentation index and reading routes | Always current |
| `docs/architecture/` | Long-lived architecture, state, rendering, and module contracts | Update with implementation |
| `docs/guides/` | User-facing feature behavior and Android integration guides | Update with public behavior |
| `docs/tooling/` | Preview, diagnostics, benchmark, and developer-tool documentation | Update with tooling behavior |
| `docs/project/` | Workflow, roadmap, release, verification, and documentation governance | Update with project process |
| `docs/project/plans/` | Active multi-step execution plans that must survive across sessions | Remove or archive when complete |
| `docs/archive/` | Completed plans, audits, snapshots, and superseded documents | Historical; do not maintain as current truth |

The root Markdown allowlist is deliberately limited to:

- `README.md`
- `README.zh-CN.md`
- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`
- `THIRD_PARTY_NOTICES.md`
- `AGENTS.md`

Adding another root Markdown file requires changing this governance document and the automated
structure check in the same pull request. Convenience is not sufficient justification.

## Choosing where a document belongs

Use the following decision order:

1. If the information changes an existing contract, update the existing active document.
2. If it explains how to use a framework capability, place it under `docs/guides/`.
3. If it defines runtime or module boundaries, place it under `docs/architecture/`.
4. If it describes developer tooling, place it under `docs/tooling/`.
5. If it governs maintenance, releases, verification, or roadmap state, place it under
   `docs/project/`.
6. If it is a temporary multi-step plan, place it under `docs/project/plans/`.
7. If its implementation is complete or its information is superseded, move it to
   `docs/archive/`.

Do not create a second roadmap, architecture overview, or “current status” document. Extend the
existing source of truth and link to it from narrower documents.

## Naming and linking

Active documents use lowercase kebab-case names, except directory indexes named `README.md`.
Examples: `state-snapshots.md`, `text-input.md`, and `documentation-governance.md`.

All links must be repository-relative:

```markdown
[Architecture](../architecture/overview.md)
```

Never commit `file://` links or paths rooted at `/Users`, `/home`, a drive letter, or another local
workspace. Links to moved documents must be updated in the same commit.

Every active document must appear in [`docs/README.md`](../README.md). Archive files are represented
by the archive index rather than individually listed in the active index.

## Active plan lifecycle

A plan is appropriate only when work crosses multiple meaningful steps or sessions. Small changes
belong in an issue, pull request, or the relevant active document.

Create active plans under `docs/project/plans/` using a descriptive lowercase name such as
`layoutlib-worker-reuse.md`. Each plan must record:

- status: proposed, active, blocked, or complete;
- scope and non-goals;
- current baseline;
- completion criteria;
- ordered steps and validation;
- last verified date;
- the next concrete action.

Update the plan as implementation progresses. When complete, copy durable conclusions into the
appropriate architecture, guide, tooling, or project document, then move the plan to
`docs/archive/`. Archived plans are not reopened; a materially new effort receives a new active
plan.

## Rules for AI-assisted maintenance

AI agents must:

1. read [`docs/README.md`](../README.md) and the relevant active documents before changing a
   documented contract;
2. treat repository code and tests as evidence, then update stale active documentation in the same
   change instead of silently following it;
3. ignore `docs/archive/` during normal context recovery unless a historical decision is required;
4. search for an existing source of truth before creating a new document;
5. keep temporary investigation notes out of the repository;
6. update the documentation index and all incoming links when adding, moving, or deleting a file;
7. run the documentation structure check before handoff.

The root [`AGENTS.md`](../../AGENTS.md) repeats the shortest machine-discoverable form of these
rules and points back here. This document remains the complete source of truth.

## Review checklist

For any documentation change, verify:

- the document is in the correct category;
- no active source of truth is duplicated;
- behavior and status statements match current code and tests;
- links are relative and resolve;
- new active documents are listed in `docs/README.md`;
- completed plans moved to the archive and durable conclusions moved to active docs;
- `./gradlew verifyDocumentationStructure` passes.

Structural documentation changes should be isolated in a dedicated commit so moves, content edits,
and broken-link repairs remain reviewable.
