# Documentation Site Operations

## Purpose

This page is the operating guide for the hosted ViewCompose documentation system. Content rules
remain authoritative in [Documentation Governance](./documentation-governance.md), while platform
selection and trade-offs are recorded in
[ADR-0001](../architecture/decisions/0001-hosted-documentation-platform.md).

## Build pipeline

The production artifact is assembled in six explicit stages:

1. `verifyDocumentationStructure` validates source placement, catalog parity, reachability, and
   repository links.
2. `verify:translations` validates required Chinese coverage, canonical source fingerprints,
   explicit stale status, and stale-warning markers.
3. `verifyCompleteViewComposeApiDocs` runs Dokka 2 for each published artifact, copies the result to
   ignored, versioned paths under `website/generated/api/`, and verifies the complete manifest,
   immutable version route, aliases, and pinned source links.
4. the website catalog generator reads publishing metadata and `docs/modules/README.md`; it does
   not maintain a second module registry.
5. Docusaurus type-checks and builds the handwritten documents, site presentation, generated API
   output, localized search indexes, and compatibility redirects for both `en` and `zh-CN` into
   `website/build/`, with broken links and anchors treated as errors.
6. the build wrapper audits Docusaurus-owned HTML accessibility and enforces build-time, total
   output, JavaScript, CSS, and per-locale search-index budgets. Dokka-generated HTML remains under
   the API generator's independent integrity gate rather than the site-template accessibility gate.

Run the complete local verification from the repository root:

```bash
./gradlew verifyDocumentationStructure verifyCompleteViewComposeApiDocs
cd website
npm ci
npm run test:translations
npm run verify:translations
npm run typecheck
npm run build
```

`npm run build` includes the accessibility and budget gates. Run `npm run verify:site` to recheck an
existing `website/build/` artifact without rebuilding it.

During local iteration, `-PviewComposeDocsModules=artifact-a,artifact-b` limits Dokka assembly to an
explicit subset. A production build never uses this shortcut.

Run `npm run write-translations` when React, navbar, footer, or sidebar messages gain new keys. It
adds missing JSON messages without overwriting reviewed Chinese translations. Markdown mirror
layout, source fingerprints, required-page tiers, and stale recovery are defined in the
[localization workflow](localization.md).

## Search, redirects, and quality budgets

The site builds a local full-text index for English and Simplified Chinese. Search needs no hosted
service, credentials, analytics, or network request after deployment. Search UI messages are
reviewed in the standard `zh-CN` message catalog, while the index is generated from the locale's
rendered documents during every production build.

Compatibility redirects preserve `/docs`, `/getting-started`, `/compose-migration`, and
`/migrate-from-compose`, including their locale-prefixed forms. Add a redirect only for an
intentional historical or campaign route; canonical document paths remain the source of truth.

The versioned thresholds live in `website/site-budgets.json`. The current ceilings are 120 seconds
for the Docusaurus build, 260 MiB for the complete output, 8 MiB total and 768 KiB largest-file for
JavaScript, 128 KiB for CSS, and 4 MiB for each locale's search index. Raise a threshold only with a
measured explanation of the reader or release value that requires the additional cost.

The accessibility audit covers the site-owned English and localized pages and checks document
language, title and main landmarks, heading order, accessible names, image alternatives, table
headers, iframe titles, and duplicate IDs. It deliberately excludes redirect stubs and
Dokka-generated implementation pages. Changes to the Dokka template require a separate generated
API accessibility review rather than weakening this gate.

## API versions and aliases

Immutable API trees use `/api/<artifact>/<version>/`. The mutable `current` alias follows the
version currently registered by the repository. The `latest` alias is generated only for stable
versions; alpha, beta, release-candidate, snapshot, preview, development, and EAP versions must not
silently become `latest`.

Each `module.<artifact>.version` has a matching `module.<artifact>.sourceRevision` containing a full
40-character Git commit SHA. Dokka maps the module root to that immutable revision, and output
verification rejects missing or movable source links. Because recording a commit changes the
metadata commit, release preparation uses two steps: freeze module source in one commit, then update
its version and source revision in a metadata-only release commit.

`verifyAssembledViewComposeApiDocs` validates an explicit local subset selected with
`-PviewComposeDocsModules`. Deployment and complete-catalog CI must use
`verifyCompleteViewComposeApiDocs`, which rejects a partial selection. All current modules are
prereleases, so no `latest` route is emitted yet.

Generated HTML and catalogs are never committed. Released API snapshots must eventually be restored
from release artifacts or an immutable documentation archive before repository versions advance.

## Continuous integration and deployment

`.github/workflows/documentation.yml` builds affected pull requests but never deploys them. A push
to `main`, or a manual run on `main`, produces the complete site and deploys it through the protected
`github-pages` environment.

GitHub repository settings must use **GitHub Actions** as the Pages source. The checked-in `CNAME`
declares `docs.viewcompose.com`; DNS should point the `docs` CNAME to `viewcompose.github.io` and
HTTPS enforcement is enabled only after GitHub validates the domain.

No Maven Central credentials, signing material, domain registrar credentials, analytics keys, or
search administration keys belong in the repository. Deployment uses GitHub's short-lived Pages
identity token.

## Failure recovery

- If source verification fails, fix the canonical document or catalog rather than weakening the
  gate.
- If Dokka fails, reproduce with a selected module and correct its source/API configuration.
- If Docusaurus reports a broken link or anchor, preserve strict checking; generated static API
  links are the only links explicitly exempted from its route graph.
- If the accessibility gate fails, fix the rendered page or theme component. Do not suppress a
  rule because a minifier formats otherwise valid HTML differently.
- If a site budget fails, inspect the reported output class and remove the regression or document
  and review an intentional threshold change.
- If translation verification reports source drift, review and update the Chinese meaning before
  recording the new fingerprint. A tracked page may be explicitly marked stale; a required page
  may not.
- If deployment fails after a successful build, keep the last Pages deployment live and rerun only
  after checking repository Pages settings and the `github-pages` environment.
- If the custom domain fails while the Pages artifact is healthy, diagnose DNS and domain
  verification separately from the documentation build.

## Last verified

2026-08-03: all 25 published artifacts pass strict KDoc/Javadoc generation, complete-catalog route
verification, and immutable source-link verification. The English and Simplified Chinese builds,
translation freshness, local search, compatibility redirects, site-page accessibility, and size
and build-time budgets are active. The measured site output is about 204 MiB; the largest JavaScript
asset is 650 KiB, and the Docusaurus build takes about ten seconds on the current development
machine.
