# Document Language Consistency Completion Record

## Status

Completed on 2026-08-03.

## Goal

Restore and permanently enforce one documentation language per locale:

- active canonical titles, headings, and narrative under `docs/` are English;
- active `zh-CN` mirror titles, headings, and narrative are Simplified Chinese;
- code, commands, identifiers, URLs, and real UI literals remain exact;
- every active handwritten public page has a reviewed Chinese mirror;
- local and CI gates reject future language placement, parity, and freshness regressions.

Historical files under `docs/archive/`, temporary execution plans, generated API reference, and
immutable historical module-manual snapshots remain canonical English-only evidence.

## Verified inventory

The repository-wide Markdown-aware inventory found three independent problem classes:

1. 12 canonical pages contained predominantly Simplified Chinese narrative:
   - architecture: lifecycle/SavedState, Modifier, NodeSpec, overview, delayed-session containers,
     and state snapshots;
   - guides: advanced shadows and theming;
   - project: roadmap and development workflow;
   - tooling: diagnostics and performance.
2. 33 existing Chinese mirrors had Chinese narrative but an English-only H1, including architecture,
   guide, module-manual, project, and tooling pages.
3. 14 additional active public pages had correct English canonical content but no Chinese mirror,
   causing the Chinese route to fall back to English. These covered architecture decisions and
   render failures, six guides, and five project-operation pages.

Code fences, inline code, URLs, comments, and marked UI literals were excluded before classification.
The two genuine navigation UI literals were preserved as inline code.

## Delivered outcome

1. Preserved the original Chinese content of all 12 misplaced canonical pages as reviewed locale
   mirrors and replaced their canonical source with English contracts.
2. Added reviewed Chinese mirrors for the remaining 14 active public pages.
3. Corrected all 33 English-only H1 headings in existing Chinese mirrors.
4. Expanded required translation coverage from 15 policy entries to all 67 active handwritten
   public pages; every required mirror is current and has an exact canonical SHA-256 fingerprint.
5. Added `verify-document-languages.mjs` with focused tests. It rejects Han narrative in canonical
   pages, English-only or English-dominant Chinese pages, unregistered public pages, and unregistered
   locale mirrors while ignoring fenced code, inline literals, URLs, and comments.
6. Wired the language verifier into website prebuild/typecheck, `verifyDocumentationStructure`,
   `qaQuick`, pull-request CI, and documentation deployment.
7. Updated `AGENTS.md`, documentation governance, localization workflow, documentation-site
   operations, and the documentation index so future maintainers follow the same contract.

## Validation evidence

- language classifier: all active canonical documents and all 67 Chinese mirrors passed;
- translation verifier: 67 current, 0 stale, 67 required;
- website script tests and TypeScript type-check passed;
- `verifyDocumentationStructure` passed with the language gate attached;
- both production locales built with strict links and anchors;
- 194 site-owned pages passed accessibility checks;
- site budgets passed at 205.4 MiB output, 650 KiB largest JavaScript file, and 11.3 seconds;
- the final `qaQuick` passed 617 tasks (16 executed, 601 up to date) in 13 seconds.

## Durable ownership

The active sources of truth are:

- `docs/project/documentation-governance.md` for the language and required-coverage policy;
- `docs/project/localization.md` for the operational workflow;
- `website/i18n/translation-policy.json` for required public-page parity;
- `website/scripts/verify-document-languages.mjs` for language classification;
- `website/scripts/verify-translations.mjs` for source mapping, status, and fingerprints.
