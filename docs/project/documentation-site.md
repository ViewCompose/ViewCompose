---
schema_version: 2
document_id: project.documentation-site
doc_type: project
owner:
  kind: project
  id: documentation-site
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: Build, verify, deploy, and recover the hosted documentation system.
validation:
  - npm run build
  - npm run verify:deployment
lifecycle: Update with documentation pipeline, hosting, route, or budget changes.
---

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
   fingerprints the maintained generator inputs, and verifies a per-revision integrity record
   containing the exact entry set plus every generated file's size and SHA-256 digest. A valid
   restored revision group is reused; a missing, stale, malformed, extra, deleted, symlinked, or
   digest-mismatched group is removed and regenerated in a temporary workspace before route,
   alias, manifest, and pinned-source-link verification continues. Missing frozen commits are
   fetched by exact full SHA; movable references are never substituted. Historical workspaces get
   only the release records for their source-revision group, and revisions predating current build
   contracts receive temporary configuration shims that never enter published output.
5. the website generators read publishing metadata, the immutable release registry, and
   `docs/modules/README.md`. They generate the catalog plus one module-manual snapshot per released
   artifact/version from the same frozen Git revision; they do not maintain a second registry.
   Every unique frozen revision is resolved by exact full SHA before any snapshot read, independent
   of whether immutable API output was restored or rebuilt.
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

The quality report lives at `build/reports/documentation/site-quality-report.json`, outside the
deployed/budgeted tree, so rechecking `website/build/` reproduces the build result.

During local iteration, `-PviewComposeDocsModules=artifact-a,artifact-b` limits Dokka assembly to an
explicit subset. A production build never uses this shortcut.

`build/versioned-api-cache/integrity-manifest.json` is generated cache state, not a second release
registry or a deployable API resource.
Its complete key is derived from per-revision fingerprints; each revision fingerprint covers its
immutable artifact/version/source triple set and the current generator implementation. Aliases and
unpublished working-tree `current` output are deliberately outside immutable reuse and are rebuilt
for every assembly. `VIEWCOMPOSE_API_DOCS_MAX_PARALLEL_REVISIONS` accepts only `1` or `2`; CI keeps
it at `1` until an accepted hosted-runner process-tree memory measurement justifies two concurrent
2 GiB Gradle/Dokka processes.

Governance V2 assets are repository inputs, not another site registry: Phase 0A freezes schemas,
Phase 0B reports through compiled quality ownership, and Phase 2 blocks new debt. The committed
`website/src/data/capability-reference.json` dataset is intentionally rewritten with
`./gradlew updateDocumentationCapabilityReference`; verification independently derives and
byte-compares the expected model. The localized `/reference/` page consumes that one tree, while
`/api/` remains the exhaustive per-artifact, per-version Dokka output.

Run `npm run write-translations` when React, navbar, footer, or sidebar messages gain new keys. It
adds missing JSON messages without overwriting reviewed Chinese translations. Markdown mirror
layout, source fingerprints, required-page tiers, and stale recovery are defined in the
[localization workflow](localization.md).

## Search, redirects, and quality budgets

The site builds a local full-text index for English and Simplified Chinese. Search needs no hosted
service, credentials, analytics, or network request after deployment. Search UI messages are
reviewed in the standard `zh-CN` message catalog, while the index is generated from the locale's
rendered documents during every production build.

Search keeps page summaries, headings, public contracts, and command guidance indexed. Exhaustive
defect-evidence tables and dated measurement ledgers remain rendered and directly linkable, but use
`search-partition-detail` so repeated historical detail does not dominate the local index. Every
excluded block must retain an adjacent searchable heading and summary; API contracts, command
references, and reader-facing guides must not use this partition.

Exceptionally large temporary execution plans remain repository-only production drafts when the
active-plan index retains a searchable purpose and scope summary and every durable public contract
and command remains in its searchable owning documentation. Canonical indexes keep repository-
relative source links so the documentation graph remains complete; the strict Markdown-link hook
rewrites only a verified `draft: true` target to its exact GitHub source URL during the site build.
The target is therefore reviewable from the public index without adding temporary execution state
to rendered output, localized fallbacks, search, or the sitemap. A missing target, a non-draft
broken link, or any other unresolved route still fails the build.

The per-locale search budget is 6.25 MiB. Reviewed bilingual architecture and contract additions
moved it from 4 through 6 MiB; the lazy-collection branch then partitioned exhaustive plan and
benchmark detail before the final 6.25 MiB ceiling. Exact transition evidence is consolidated
below. Reaching this ceiling again requires structural index segmentation rather than another
content-only partition or threshold increase; API and command guidance remains searchable.

Compatibility redirects preserve `/docs`, `/getting-started`, `/compose-migration`,
`/migrate-from-compose`, and previously published active-plan routes after those plans move to the
archive, including their locale-prefixed forms. Add a redirect only for an intentional historical
or campaign route; canonical document paths remain the source of truth.

The versioned thresholds live in `website/site-budgets.json`. Immutable Dokka output is canonical
at `/api/**`; after Docusaurus finishes its locale builds, the supported build entry point removes
locale-prefixed static copies such as `/zh-CN/api/**`. Localized pages link to the canonical API
tree, so those copies add storage but no localized content or supported route. The shared social
card likewise uses one absolute root URL, and the supported build removes its locale copy.

The budget model separates expected release-history growth from regressions. Current ceilings are
46.9 MiB for non-API output, 4.5 MiB average and 24 MiB maximum per API tree, 1 MiB for API routing
overhead, 8 MiB total and 768 KiB largest-file JavaScript, 128 KiB CSS, 6.25 MiB per locale search
index, and 120 seconds for the Docusaurus build. Locale-prefixed API copies remain forbidden.

The ceiling rose from 41 MiB to 46.9 MiB only after paired attribution and consolidation. In the
2026-08-26 Governance V2 gate change, one same-corpus build reached 49,185,235 B, 7,020.6 B
(+0.0143%) over the limit. Consolidating repeated prose while retaining the active ratchet contract
and pruning the unused locale social-card copy reduced it by 769,236 B (-1.5640%) to 48,415,999 B,
leaving 762,215.4 B (1.5499%) headroom: the result is `improved`. The measurement covers one local
production build; the stop condition remains unchanged: do not raise the threshold, and make the
next failure consolidate representation again. Current public contracts and valid immutable API
history must not be deleted merely to recover budget.

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

Relative links from a historical manual to another released module are rewritten to that module's
versioned route. Links to temporary execution plans are instead pinned to the manual source
revision on GitHub, so completing or archiving a plan cannot break an immutable manual snapshot.

The append-only release registry pairs every version with a full immutable source SHA; missing or
movable links fail. Freeze source and manuals first, then append registry/version metadata in a
second commit. The frozen SHA must stay reachable and cannot be replaced by a squash commit.

`release.retiredModules` preserves superseded history outside the active catalog.
`release.unpublishedModules` permits only pre-release working-tree `current` output and must remove
an artifact when its first immutable entry is appended.

`verifyAssembledViewComposeApiDocs` accepts an explicit local subset; deployment uses the complete
verifier and checks every API/manual route in both locales. Current prereleases emit no `latest`.

Generated output is never committed. A clean checkout restores history from registered revisions
and fetches only an otherwise-missing exact SHA, independent of temporary branches.

For each module release:

1. freeze the releasable source, source comments, compiled samples, and module manual in a commit;
2. append an immutable registry record and update the module's publishing version and
   `sourceRevision` in a metadata-only commit;
3. run the publishing configuration gate, complete API verifier, and production site build before
   publishing. The configuration gate rejects current metadata without an exact registry match.

## Continuous integration and deployment

`.github/workflows/documentation.yml` remains present for every pull request. Its standalone impact
job configures only `tools/viewcompose-quality-build`, publishes the source-owned classification in
the job summary, and selects the expensive documentation child only for documentation, website,
published-production, or conservative full-fallback inputs. The stable `Build documentation`
context is an `always()` result facade: an intentional skip succeeds only after a successful
unselected plan, while a planning or selected-child failure remains fatal. A push to `main`, or a
manual run on `main`, always selects the complete child; only its verified Pages artifact can deploy
through the protected `github-pages` environment.

The selected child computes the immutable generator and complete-history fingerprints before
restoring `website/generated/api`. Pull requests use restore-only access; only a successful `main`
child may save a cache. The primary key is unique per run so a verified recovery can supersede a
corrupt archive, while ordered restore prefixes first select the same complete fingerprint and then
the most recent cache produced by the same generator. A restore is never trusted by key alone: the
assembler verifies every revision group and publishes hit, partial, miss, recovery, generated-group,
invalid-group, parallelism, and duration telemetry in the job summary. The source/language/
translation gate runs once, the catalog is generated once, and CI then uses prepared type-check and
site-build entry points so npm lifecycle hooks do not repeat the same prebuild work. Cache-service
restore or save failures degrade to full generation or a skipped write rather than bypassing the
verifier or blocking an otherwise valid Pages artifact.

Deployment succeeds only after production smoke tests fetch both catalogs and every current manual
in both locales, including representative no-trailing-slash routes. HTTP, rendered not-found,
wrong-plugin, or missing-catalog failures remain fatal after bounded CDN retries.

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
- If an API cache group fails integrity, keep the automatic group-level regeneration. Do not edit
  the manifest, accept a key-only hit, save caches from pull requests, or bypass the complete API
  verifier. A recovered `main` run writes a newer unique key for the same fingerprint.
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

<div className="search-partition-detail">

- **2026-08-26, immutable API cache local acceptance:** the complete 100-entry deployable history
  occupied `427 MiB` across five source revisions; its non-deployable integrity state occupied
  `6.7 MiB`. A cold run generated all five groups sequentially in `411.7 s`; the complete Gradle
  verifier took `6 min 58 s`. The unchanged rerun integrity-checked all 26,096 immutable files,
  reused `5/5` groups with zero historical Gradle/Dokka generation in `2.1 s`, and completed the
  verifier in `5.42 s`, a `98.7%` reduction. During the 31-entry group,
  sampled active-process RSS reached approximately `1.75 GiB`; because this is a local point sample,
  not a hosted-runner peak measurement, parallelism remains `1`. A two-revision
  `viewcompose-image-glide` fault test then changed one generated HTML file: the next run reused the
  valid group, rejected and regenerated only the damaged group in `32.2 s`, and passed the existing
  manifest, route, alias, and immutable-source checks. The local cache result is **improved**;
  hosted restore/save and hit evidence remains the next acceptance action.

- **2026-08-26, immutable API cache hosted acceptance:** the first complete `main` run missed by
  design, spent `1139.4 s` assembling five historical groups, completed the API step in `21 min`,
  then built, uploaded, saved a `39.3 MB` cache, and deployed successfully. After the cache became
  visible, an exact `main` rerun restored it in `7 s`, verified and reused `5/5` groups with zero
  generation or invalid groups in `5.7 s`, and completed the API step in `2 min 9 s`, an `89.8%`
  reduction. The immutable-cache conclusion is **improved**. That hot run exposed one separate
  limitation: versioned manual generation had implicitly relied on cold API reconstruction to fetch
  otherwise unreachable frozen commits. The generator now resolves every unique full SHA before
  reading snapshots; the next action is a complete hosted hot-path rerun through the site build.

- **2026-08-25, Governance V2 Phase 0A:** the initial bilingual contract candidate exceeded the
  unchanged 46.9 MiB non-API limit by 42,041 bytes. Consolidating repeated normative prose and
  moving the generated quality report outside the deploy tree reduced it to 49,175,712 bytes,
  leaving 2,502 bytes; build and post-build recheck both pass. The result is **mixed**: the first
  representation regressed, while consolidation and repeatable verification corrected it without
  removing contracts or raising the limit. This local build excludes deployment/CDN/network
  behavior and separately budgeted API output; Phase 0B must reuse the compiled model and preserve
  the ceiling.

Git history preserves earlier Paging and site checkpoints. They do not authorize deleting current
contracts, raising the limit without evidence, or re-expanding completed copies.

</div>
