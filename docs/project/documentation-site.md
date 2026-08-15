# Documentation Site Operations

## Purpose

This page is the operating guide for the hosted ViewCompose documentation system. Content rules
remain authoritative in [Documentation Governance](./documentation-governance.md), while platform
selection and trade-offs are recorded in
[ADR-0001](../architecture/decisions/0001-hosted-documentation-platform.md).

## Build pipeline

The production artifact is assembled in seven explicit stages:

1. `verifyDocumentLanguages` checks that canonical and localized titles and narrative use the
   language of their directory and that every active public page has a required locale mirror.
2. `verifyDocumentationStructure` validates source placement, catalog parity, reachability, and
   repository links.
3. `verify:translations` validates required Chinese coverage, canonical source fingerprints,
   explicit stale status, and stale-warning markers.
4. `verifyCompleteViewComposeApiDocs` groups the immutable release registry by source revision,
   reconstructs each revision in a temporary workspace, runs the current maintained Dokka tooling,
   copies every released artifact/version tree to ignored paths under `website/generated/api/`, and
   verifies the complete manifest, immutable routes, aliases, and pinned source links. Artifacts in
   `release.unpublishedModules` instead generate only a mutable `current` API tree from the working
   source; they never receive a fabricated immutable version route.
   When a frozen revision predates the dependency-contract registry, the temporary documentation
   workspace synthesizes empty registry rows only to configure current Dokka tooling; compilation
   still follows that revision's Gradle build and the synthetic rows are never published.
5. the website generators read publishing metadata, the immutable release registry, and
   `docs/modules/README.md`. They generate the catalog plus one module-manual snapshot per released
   artifact/version from the same frozen Git revision; they do not maintain a second registry.
6. Docusaurus type-checks and builds the handwritten documents, site presentation, generated API
   output, localized search indexes, and compatibility redirects for both `en` and `zh-CN` into
   `website/build/`, with broken links and anchors treated as errors.
7. the build wrapper verifies shared site-shell behavior across locales, audits Docusaurus-owned
   HTML accessibility, and enforces build-time, total output, JavaScript, CSS, and per-locale
   search-index budgets. Dokka-generated HTML remains under the API generator's independent
   integrity gate rather than the site-template accessibility gate.

Run the complete local verification from the repository root:

```bash
./gradlew verifyDocumentationStructure verifyCompleteViewComposeApiDocs
cd website
npm ci
npm run test:scripts
npm run verify:languages
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

The per-locale search-index budget is 6 MiB. It was first raised from 4 MiB after the searchable
[multi-design-system architecture standard](../architecture/design-systems.md), ADR-0005, and its
evidence-heavy [active execution plan](./plans/multi-design-system-high-fidelity.md) measured about
4.1 MiB for English and 4.4 MiB for Chinese. After the complete One UI and overlay architecture
record plus nine additional Chinese mirrors were indexed, the complete build measured 4.4 MiB for
English and 4.7 MiB for Chinese, so the reviewed ceiling moved to 5 MiB. Adding the host-owned
Android resource environment and transactional effect lifecycle contracts measured 4.7 MiB for
English and 5.1 MiB for Chinese, so the reviewed ceiling moved to 5.5 MiB. The component-appearance
convergence then added one searchable ADR plus expanded theming, overlay, and module contracts. A
clean `main` build contained 103 canonical documents and measured 5,349,372 bytes for English and
5,757,926 bytes for Chinese; the failing candidate contained 104 and measured 5,422,767 and
5,838,085 bytes. That is an absolute increase of 73,395 bytes (1.37%) for English and 80,159 bytes
(1.39%) for Chinese, while Chinese bytes per canonical document rose by about 0.42%. The conclusion
is **regressed** in absolute payload but without anomalous index amplification: the new contracts have
direct reader value, and `main` had only 9,242 bytes of headroom. The reviewed ceiling therefore
moves to 6 MiB; that measured candidate leaves 453,371 bytes, or about 7.2% of the budget, as
Chinese headroom. If either locale reaches 6 MiB, the next action is to review search partitioning
or index representation before another increase; code and command content remains indexed because
API and build-command search has direct reader value.

Compatibility redirects preserve `/docs`, `/getting-started`, `/compose-migration`, and
`/migrate-from-compose`, including their locale-prefixed forms. Add a redirect only for an
intentional historical or campaign route; canonical document paths remain the source of truth.

The versioned thresholds live in `website/site-budgets.json`. Immutable Dokka output is canonical
at `/api/**`; after Docusaurus finishes its locale builds, the supported build entry point removes
locale-prefixed static copies such as `/zh-CN/api/**`. Localized pages link to the canonical API
tree, so those copies add storage but no localized content or supported route.

The budget model separates expected release-history growth from regressions. Non-API output is
limited to 42 MiB. Before the Demo verification-harness plan was added, a clean `main` build already
measured 39.999791 MiB. Publishing that searchable English plan, its `zh-CN` fallback route, and
both locale search entries measured 40.427350 MiB, so the reviewed ceiling moved from 40 MiB to
41 MiB instead of removing reader-value planning evidence. The native-widget contract convergence
then expanded required bilingual architecture, migration, guide, and module contracts. Before the
budget evidence itself was added, the clean candidate increased non-API output from 42,829,400 to
43,024,465 bytes: 195,065 bytes, or 0.46%, and exceeded the old ceiling by 32,849 bytes. The
conclusion is **regressed** in absolute payload, but the growth is attributable to reviewed
reader-facing contracts and no other site ceiling failed. The reviewed ceiling therefore moves to
42 MiB, leaving approximately 1 MiB of measured headroom; the final build including this evidence
passed at 41.1 MiB against that 42.0 MiB ceiling. Reaching 42 MiB requires a representation or
content-partitioning review before another increase. The Android Views performance-control plan,
its generated route, and the reviewed bilingual physical-result tables remain inside that same
42 MiB ceiling; they provide the durable interpretation needed to distinguish accepted, rejected,
and device-blocked benchmark evidence. Immutable artifact/version trees and working-tree `current`
Dokka for unpublished artifacts share the API-tree
budget: they may average at most 4.5 MiB and no individual tree may exceed 24 MiB. Only manifests
and redirect aliases use the separate 1 MiB routing allowance. The other ceilings remain 120
seconds for the Docusaurus build, 8 MiB total and 768 KiB largest-file for JavaScript, 128 KiB for
CSS, and 6 MiB for each locale's search index. The gate also rejects any locale-prefixed API copy.
Raise a threshold only with a measured explanation of the reader or release value that requires the
additional cost.

The accessibility audit covers the site-owned English and localized pages and checks document
language, title and main landmarks, heading order, accessible names, image alternatives, table
headers, iframe titles, and duplicate IDs. It deliberately excludes redirect stubs and
Dokka-generated implementation pages. Changes to the Dokka template require a separate generated
API accessibility review rather than weakening this gate.

The site-shell verifier requires both locale homepages to use one explicit browser-storage
namespace, so switching languages preserves the reader's light or dark color-mode choice. It also
rejects the removed standalone Maven-coordinate banner on either homepage.

## Released versions and aliases

Immutable API trees use `/api/<artifact>/<version>/`. The mutable `current` alias follows the
version currently registered by the repository. Before an artifact's first release, its `current`
route contains Dokka generated from the working source and no versioned route exists. The `latest`
alias is generated only for stable versions; alpha, beta, release-candidate, snapshot, preview,
development, and EAP versions must not silently become `latest`.

Immutable module-manual snapshots use `/modules/<artifact>/<version>`; the unversioned
`/modules/<artifact>` page remains the maintained current guide. Historical manuals are generated
as canonical English snapshots, including at the equivalent `zh-CN` route, so the locale path never
pretends that an unreviewed historical translation exists.

Each `module.<artifact>.version` has a matching `module.<artifact>.sourceRevision` containing a full
40-character Git commit SHA. The append-only
`gradle/viewcompose-documentation-releases.properties` registry records every released
artifact/version/revision triple. Dokka maps the module root to that immutable revision, and output
verification rejects missing or movable source links. Because recording a commit changes the
metadata commit, release preparation uses two steps: freeze module source and manual in one commit,
then append the history entry and update version/revision metadata in a metadata-only release
commit. The frozen commit must be pushed and remain reachable from Git history.

`release.retiredModules` keeps superseded coordinates valid in immutable documentation history
without returning them to the active module catalog; the API landing lists them in a separate
Retired history group. `release.unpublishedModules` is allowed only for active artifacts before
their first release; the API landing links their working-tree `current` output and labels them
unreleased. Remove an artifact from that list in the same release metadata change that appends its
first immutable documentation entry.

`verifyAssembledViewComposeApiDocs` validates an explicit local subset selected with
`-PviewComposeDocsModules`. Deployment and complete-catalog CI must use
`verifyCompleteViewComposeApiDocs`, which rejects a partial selection. The site build additionally
verifies every recorded API and module-manual route for both locale trees. All current modules are
prereleases, so no `latest` route is emitted yet.

Generated HTML, catalogs, and module-manual snapshots are never committed. A clean checkout restores
the complete released documentation history from the immutable Git revisions in the registry; the
documentation workflow therefore checks out full history rather than a shallow clone.

For each module release:

1. freeze the releasable source, source comments, compiled samples, and module manual in a commit;
2. append an immutable registry record and update the module's publishing version and
   `sourceRevision` in a metadata-only commit;
3. run the publishing configuration gate, complete API verifier, and production site build before
   publishing. The configuration gate rejects current metadata without an exact registry match.

## Continuous integration and deployment

`.github/workflows/documentation.yml` builds affected pull requests but never deploys them. A push
to `main`, or a manual run on `main`, produces the complete site and deploys it through the protected
`github-pages` environment.

The deployment job is not considered successful until a production-domain smoke test fetches both
module catalogs and every current module manual in English and Simplified Chinese. It also exercises
the no-trailing-slash form of both catalogs and a representative current manual to protect the
GitHub Pages compatibility behavior. The test rejects HTTP failures, Docusaurus not-found content
returned with HTTP 200, pages outside the primary docs plugin, and catalogs missing any current
module link. It retries briefly for CDN propagation, then fails the protected Pages environment
rather than reporting a broken publication as successful.

GitHub repository settings must use **GitHub Actions** as the Pages source. The checked-in `CNAME`
declares `docs.viewcompose.com`; DNS should point the `docs` CNAME to `viewcompose.github.io` and
HTTPS enforcement is enabled only after GitHub validates the domain.

No Maven Central credentials, signing material, domain registrar credentials, analytics keys, or
search administration keys belong in the repository. Deployment uses GitHub's short-lived Pages
identity token.

## Failure recovery

- If source verification fails, fix the canonical document or catalog rather than weakening the
  gate.
- If release-history verification fails, append the missing immutable record or correct unpublished
  metadata. Never rewrite an already released artifact/version entry.
- If Dokka fails, reproduce with a selected module and correct its source/API configuration.
- If Docusaurus reports a broken link or anchor, preserve strict checking; generated static API
  links are the only links explicitly exempted from its route graph.
- If the accessibility gate fails, fix the rendered page or theme component. Do not suppress a
  rule because a minifier formats otherwise valid HTML differently.
- If a site budget fails, inspect whether the regression is non-API output, API-tree average, one
  immutable or unpublished-current API tree, routing overhead, or a locale-prefixed duplicate. Remove the regression or document and
  review an intentional threshold change; do not restore a fixed total-output ceiling that fails
  merely because valid immutable release entries were appended.
- If translation verification reports source drift, review and update the Chinese meaning before
  recording the new fingerprint. A tracked page may be explicitly marked stale; a required page
  may not.
- If language verification fails, correct the misplaced narrative or missing required mirror;
  format a genuine foreign-language UI literal as code instead of weakening the classifier.
- If deployment fails after a successful build, keep the last Pages deployment live and rerun only
  after checking repository Pages settings and the `github-pages` environment.
- If the custom domain fails while the Pages artifact is healthy, diagnose DNS and domain
  verification separately from the documentation build.

## Last verified

2026-08-15: paired Docusaurus locale builds on the same macOS checkout and dependency set compared
`main` at `ca3d7985` with PR #99 before its budget evidence was added. Non-API output measured
42,829,400 and 43,024,465 bytes respectively, a 195,065-byte or 0.46% increase; the candidate was
32,849 bytes above the 41 MiB ceiling. The conclusion is **regressed** in absolute size but accepted
for the required bilingual public contracts, so only the non-API ceiling moved to 42 MiB. The final
remediated build, including this evidence, passed at 41.1 MiB against 42.0 MiB. The baseline worktree
did not reconstruct API history and stopped at API-route verification, which does not affect this
metric because its rendered non-API tree was complete and API bytes are excluded. The next action
at the new ceiling is to review representation or content partitioning before any further increase.

2026-08-14: clean builds of current `main` and the documentation-planning branch measured non-API
output at 39.999791 MiB and 40.427350 MiB respectively. The reviewed non-API ceiling moved to
41 MiB so the directly linked Demo verification-harness plan remains searchable in English and
through its Chinese fallback route. No JavaScript, CSS, search-index, API-tree, routing, or build-time
threshold changed.

2026-08-06: a clean complete-history build reconstructed 69 immutable artifact versions and built
9 unpublished `current` API trees from the working source. It passed immutable source-link,
manifest, retired-history, current/unreleased, and stable-only `latest` verification. The production
site verified 69 English module-manual snapshots, 69 `zh-CN` English-fallback snapshot routes,
language placement, 80 current Chinese mirrors, local search, compatibility redirects, and 310
site-owned accessibility pages. Measured output was 316.3 MiB; non-API output was 32.9 MiB, the 78
API trees averaged 3.6 MiB, routing overhead was below the displayed 0.1 MiB precision, the largest
JavaScript asset was 650 KiB, and the full site build took 24.2 seconds.
