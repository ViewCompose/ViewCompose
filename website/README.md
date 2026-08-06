# ViewCompose Documentation Website

This directory contains the Docusaurus presentation layer for the public documentation site.
Handwritten documentation remains under [`docs/`](../docs/README.md); do not move canonical content
into React pages merely to change its presentation.

## Local verification

From the repository root, generate selected API references when iterating locally:

```bash
./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime,viewcompose-ui-foundation
```

Then build the site:

```bash
cd website
npm ci
npm run typecheck
npm run build
```

The production build contains both the canonical English site and the Simplified Chinese locale.
When changing localized content, also run:

```bash
npm run test:translations
npm run verify:translations
```

Use `npm run write-translations` to append missing Docusaurus JSON message keys. It does not replace
reviewed Chinese values. Markdown mirrors and their source fingerprints follow the
[localization workflow](../docs/project/localization.md).

Run `./gradlew verifyCompleteViewComposeApiDocs` without a module-selection property before
verifying or deploying the complete published catalog. It also verifies immutable source links and
version/current/latest route policy. Generated catalog data, Dokka HTML, and site output are
intentionally ignored by Git.

## Ownership boundaries

- `docs/` owns prose, diagrams, governance, and module manuals.
- `gradle/viewcompose-publishing.properties` owns published artifact versions and immutable source
  revisions.
- `website/scripts/` derives site data from canonical repository metadata.
- `website/i18n/` owns locale messages, reviewed Markdown mirrors, and translation policy.
- `website/src/` owns presentation components only.
- `website/generated/` is an ephemeral assembly boundary for Dokka output.
- `.github/workflows/documentation.yml` owns verification and Pages deployment.

Architecture and rollout decisions are recorded in
[ADR-0001](../docs/architecture/decisions/0001-hosted-documentation-platform.md) and the
[completed implementation plan](../docs/archive/HOSTED_DOCUMENTATION_SYSTEM_2026-08.md).
