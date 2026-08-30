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
4. `verifyCompleteViewComposeApiDocs` groups immutable releases by source revision and verifies the
   exact entry set plus every generated file's size and SHA-256. A valid group is reused; any stale,
   malformed, incomplete, extra, symlinked, or digest-mismatched group is rebuilt in a temporary
   workspace before route, alias, manifest, and pinned-source verification. Missing revisions are
   fetched only by full SHA. Historical workspaces receive only their group's release records and
   non-published compatibility shims when their source predates current build contracts.
5. the website generators read publishing metadata, the immutable release registry, and
   `docs/modules/README.md`. They generate the catalog plus one module-manual snapshot per released
   artifact/version from the same frozen Git revision; they do not maintain a second registry.
   Every unique frozen revision is resolved by exact full SHA before any snapshot read, independent
   of whether immutable API output was restored or rebuilt.
6. Docusaurus type-checks and builds the handwritten documents, site presentation, generated API
   output, localized search indexes, and compatibility redirects for both `en` and `zh-CN` into
   `website/build/`, with broken links and anchors treated as errors. After Docusaurus resolves
   presentation fields such as `slug`, remark transforms remove Governance V2 ownership fields
   and translation-review fingerprints from browser page chunks, and rewrite verified links to
   repository files outside `docs/` as GitHub source URLs. Source Markdown remains repository-
   relative and authoritative, while production and test sources are not duplicated in the site
   artifact; the generated Capability Reference remains the public relationship model.
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

Governance V2 assets are repository inputs, not another site registry: schemas and deterministic
discovery feed the compiled zero-exception strict gate, where every issue blocks. The committed
`website/src/data/capability-reference.json` dataset is intentionally rewritten with
`./gradlew updateDocumentationCapabilityReference`; verification independently derives and
byte-compares the expected model. The localized `/reference/` page consumes that one tree, while
`/api/` remains the exhaustive per-artifact, per-version Dokka output.

Run `npm run write-translations` when React, navbar, footer, or sidebar messages gain new keys. It
adds missing JSON messages without overwriting reviewed Chinese translations. Markdown mirror
layout, source fingerprints, required-page tiers, and stale recovery are defined in the
[localization workflow](localization.md).

## Search, redirects, and quality budgets

Each locale builds a credential-free local search index from rendered documents. It keeps page
summaries, headings, public contracts, and command guidance. Exhaustive evidence tables and dated
ledgers may use `search-partition-detail` when an adjacent searchable heading and summary remain;
API contracts, command references, and reader-facing guides may not use that partition. Search UI
messages remain reviewed in the standard `zh-CN` catalog.

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

Rendered code blocks remain complete on their owning pages and keep their compiled-source links,
but local full-text search indexes the surrounding explanation instead of duplicating every code
token. Exact public symbols remain discoverable through module API inventories and the generated
Reference. This boundary reduces repeated bilingual index material without hiding a page, sample,
command contract, or migration route; if a command or symbol exists only inside a fence, add its
name to the owning prose rather than returning all code bodies to the index.

Compatibility redirects preserve `/docs`, `/getting-started`, `/compose-migration`,
`/migrate-from-compose`, and previously published active-plan routes after those plans move to the
archive, including their locale-prefixed forms. Add a redirect only for an intentional historical
or campaign route; canonical document paths remain the source of truth.

The versioned thresholds live in `website/site-budgets.json`. Immutable Dokka output is canonical
at `/api/**`; the supported build removes locale-prefixed API copies and the redundant locale social
card because localized pages use the canonical API tree and one absolute social-card URL.

Immutable module-manual snapshots retain localized routes, server-rendered content, styles, links,
and color-mode bootstrap as read-only static HTML, but not redundant hydration scripts or route
chunks. Current manuals remain hydrated. The gate enforces full-page navigation, the static marker,
and script removal. Post-build Dokka compaction removes generated indentation while preserving
literal element bodies byte for byte; immutable source manifests and cache integrity remain
upstream.

The budget model separates expected release-history growth from regressions. Current ceilings are
47.1 MiB for non-API output, 4.5 MiB average and 24 MiB maximum per API tree, 1 MiB for API routing
overhead, 8 MiB total and 768 KiB largest-file JavaScript, 128 KiB CSS, 6.25 MiB per locale search
index, and 120 seconds for the Docusaurus build. Locale-prefixed API copies remain forbidden.

The ceiling moved from 41 MiB to 46.9 MiB only after paired attribution and consolidation. A
reviewed 2026-08-30 exception moved it to 47.1 MiB after the required top-level bilingual AI
Integration chapter was consolidated from two routes to one and still exceeded the prior ceiling.
The ratchet resumes at 47.1 MiB: recover capacity structurally before adding another route, remove
redundant deployed representations, and never remove current contracts or valid release history.
Existing guarded transforms remove unused locale copies, machine-only governance/translation front
matter, immutable-manual hydration, and generated indentation without changing routes or readable
content. Historical same-corpus measurements and limitations remain in source below rather than
being repeated in the public operating contract.

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
the most recent cache produced by the same generator. Because the generator fingerprint includes
the actual Java and Node runtimes, the workflow pins their complete distribution versions instead
of floating major selectors; changing either version is an explicit cache migration. A restore is
never trusted by key alone: the
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

The current production contract serves 133 immutable API versions, module manuals, and Chinese
fallback routes. The coordinated-release closeout audited 522 pages at 468.9 MiB total and
46.7/46.9 MiB non-API; two exact-cache-hit runs reused `6/6` groups and took `47.3 s` and `43.0 s`.
Cache correctness and latency are **no material change**, while added history and narrower headroom
are **mixed**. A later same-corpus consolidation reduced non-API output from `49,208,553` to
`49,086,492` bytes (`-122,061`, `-0.248%`), an **improved** representation with 524 accessible
pages. These heterogeneous local/hosted observations are not a steady-state benchmark; the local
cache also lacked six groups. Full-cache CI remains the version-route acceptance gate. Detailed
evidence remains in the [pull-request gate plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/pull-request-gate-scaling-and-build-logic-modularization.md)
and [Governance V2 archive](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/documentation-system-governance-v2.md).

On 2026-08-29, a dedicated bilingual XML-migration route produced 49,373,569 non-API bytes,
195,354.6 bytes above the unchanged ceiling. Removing that duplicate of the linked local tooling
contract and consolidating this operating page reduced the same corpus to 49,171,339 bytes:
`-202,230` bytes (`-0.4096%`) from the rejected candidate and `-24,110` bytes (`-0.0490%`) from the
49,195,449-byte route-free attempt. An accepted run left 6,875.4 bytes headroom and audited 526
pages; accepted warm retries completed in `34.2–59.8 s`. The representation is **improved**
with **no material change** to routes, search contracts, or tooling behavior. This is local warm
build evidence; a future dedicated route must recover its measured capacity first.

On 2026-08-30, the required top-level bilingual AI Integration chapter produced 49,238,608 non-API
bytes after its overview and setup were consolidated into one route. The last accepted full-site
output was 49,042,390 bytes, so the chapter adds 196,218 bytes (`+0.4001%`) and is **regressed** for
output size; functional, version-history, accessibility, language, translation, and route checks
all remained successful. The ceiling is therefore reviewed at 47.1 MiB, leaving 149,321.6 bytes of
headroom. This is one local production build and measures uncompressed output rather than transfer
size, runtime, or query latency; it makes no performance-improvement claim. The next action is to
hold this ceiling, reuse the single AI route, and reclaim measured capacity before adding another
AI documentation page.

{/* Historical measurement ledger retained in source; the compact summary above is the public representation.

- **2026-08-28, coordinated-release static-history acceptance:** adding the frozen 127-entry
  release history initially produced `50.0 MiB` of non-API output, `8.5 MiB` of JavaScript, a
  `25.4 MiB` `viewcompose-ui-foundation/0.1.0-alpha02` API tree, and a `24.2 MiB`
  `viewcompose-ui-contract/0.1.0-alpha05` tree, so the unchanged site budgets correctly rejected
  the build. The hard cut retained all 127 API versions and 254 localized historical-manual
  routes as complete static HTML while removing exactly 254 redundant hydration chunks. Safe
  Dokka indentation compaction processed 26,892 HTML files and reduced them from 322,180,811 to
  275,273,983 bytes, a 46,906,828-byte (`14.56%`) reduction. The final bilingual production build
  passed at 491,291,403 total bytes, 48,523,762 non-API bytes (`46.3 MiB`), 6,936,903 JavaScript
  bytes (`6.6 MiB`), 23,270,465 bytes for UI Foundation (`22.2 MiB`), and 21,856,201 bytes for UI
  Contract (`20.8 MiB`). It audited 510 site pages, preserved 127 immutable API/manual pairs and
  127 Chinese fallback routes, and completed the Docusaurus wrapper in `36.3 s`. The result is
  **improved**: compared with the rejected output, non-API size fell approximately `7.4%`, total
  JavaScript `22.4%`, UI Foundation `12.6%`, and UI Contract `14.0%`, without increasing any
  threshold or deleting release history. This is one local production build; hosted-runner and
  deployed-route evidence remain the next acceptance action, while current interactive manuals
  are outside the static-history boundary.

- **2026-08-28, code-block search partition acceptance:** the imported lazy-list tail closure and
  its bilingual public contracts produced 49,626,056 non-API bytes with rendered code blocks in
  local full-text search, exceeding the unchanged 46.9 MiB ceiling by 447,842 bytes. On the same
  source corpus, excluding only rendered code-block bodies from the index reduced English search
  from 5,739,133 to 5,455,358 bytes and Chinese search from 6,294,002 to 5,997,618 bytes. Complete
  non-API output fell 580,119 bytes (`1.1690%`) to 49,045,937 bytes, leaving 132,277 bytes; all 456
  bilingual pages, rendered samples, compiled-source links, accessibility checks, routes, and the
  unchanged budget remain in scope. After recording this evidence, the final build produced
  49,058,278 non-API bytes, left 119,936 bytes, and completed the Docusaurus wrapper in `30.6 s`;
  its English and Chinese indexes were 5,457,126 and 5,999,575 bytes. The result is **improved**.
  The measurement is one local production build and does not claim hosted build or query latency;
  future code-only identifiers must also appear in searchable owning prose, while the existing size
  gate detects a lost partition.

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
  reading snapshots. The first correction run then selected Temurin `17.0.20+1` while the seed used
  `17.0.20+8`; its correctly different generator fingerprint caused a cold `1175.9 s` reconstruction,
  after which catalog generation and the complete site build passed. The workflow now pins
  Temurin `17.0.20+8` and Node `24.19.0`. The pinned rerun restored the exact `cb67…/ab01…` seed in
  approximately `4 s`, reused `5/5` groups with zero generation or invalid groups in `5.5 s`, and
  completed the API step in approximately `1 min 58 s`. Versioned manual generation then completed
  in approximately `1 s` without cold reconstruction, and the complete production site job passed
  in `6 min 33 s`. The correction conclusion is **improved**; Phase 4 acceptance is complete.

- **2026-08-26, Governance V2 Navigation/Theming observation:** pull request #176's successful
  documentation child completed in `5 min 21 s`. Source and translation verification took `72 s`,
  complete versioned API generation and verification `112 s`, catalog generation `7 s`, type
  checking `2 s`, and the Docusaurus build `53 s`. Relative to #174's `5 min 10 s` documentation
  child, end-to-end time changed by `+3.55%`; the conclusion is **no material change** from one
  differently restored hosted sample. The Docusaurus step represented `16.5%` of child wall time,
  so a website-stack replacement is not supported as the primary latency action. The first #176
  attempt failed on a moved anchor and the strict link gate rejected it; the repaired retry passed,
  so correctness is **improved**. A local Theming acceptance build later audited 448 bilingual
  site pages, completed the budgeted Docusaurus wrapper in `60.0 s`, and completed the surrounding
  npm lifecycle in `82.73 s`. The limitation is that one content pull request does not establish
  P50/P95 or cache hit rate. Continue collecting the Phase 6 corpus and target source verification,
  immutable API reuse, repeated Gradle configuration, and setup/dependency restoration before
  reconsidering Docusaurus, React, or Node migration.

- **2026-08-26, Governance V2 Theming follow-up:** pull request #177's successful documentation
  child completed in `4 min 47 s`, `34 s` faster than #176 (`-10.6%`). Source and translation
  verification took `68 s`, complete versioned API generation and verification `89 s`, catalog
  generation `1 s`, type checking `2 s`, and the Docusaurus build `46 s`. Docusaurus represented
  `16.0%` of child wall time, again confirming that it is not the dominant stage. The result is
  **improved** for this hosted sample, but P50/P95 and cache-hit conclusions remain
  **inconclusive** because the restored state differs and only two post-stabilization content
  pull requests exist. Continue the corpus without a website-stack migration.

- **2026-08-26, Governance V2 Text Input hosted follow-up:** pull request #178's successful
  documentation child completed in `4 min 34 s`, `13 s` faster than #177 (`-4.5%`). Source and
  translation verification took `65 s`, complete versioned API generation and verification `82 s`,
  catalog generation `1 s`, type checking `2 s`, and Docusaurus `42 s`. Docusaurus represented
  `15.3%` of child wall time and remains a minority stage. The result is **improved** for this
  hosted sample; P50/P95 and cache-hit conclusions remain **inconclusive** with only three
  post-stabilization content pull requests. Keep the existing stack and continue the corpus.

- **2026-08-26, Governance V2 Lazy Collections hosted follow-up:** pull request #179's successful
  documentation child completed in `5 min 12 s`. Source and translation verification took `77 s`,
  complete API generation and verification `115 s`, catalog generation `1 s`, type checking `2 s`,
  and Docusaurus `53 s`. Docusaurus represented `17.0%` of child wall time and remained a minority
  stage. The affected `qaQuick` candidate passed `1,176` actionable tasks in `5 min 32 s`; the
  complete shadow passed `2,342` in `9 min 8 s`, so selected scope was `49.8%` smaller and observed
  duration `39.4%` lower with the same successful conclusion. The result is **mixed** because
  serial shadow observation still extends the required critical path, while scope selection and
  correctness are **improved**. This is only the fourth post-stabilization content sample; retain
  the current website stack and shadow comparison until the required corpus is complete.

- **2026-08-28, documentation/Tutorial-sample corpus acceptance:** eleven comparable pull requests
  (#177, #178, #179, #180, #182, #183, #184, #185, #203, #204, and #205) each selected the same
  1,176-task candidate, no published artifact, and the documentation child. Every candidate and
  following 2,342-task complete shadow agreed on success. The reconstructed no-shadow required
  critical path has nearest-rank P50 `6 min 22 s` and P95 `7 min 17 s`; every documentation child
  verified an exact `5/5` immutable-API cache hit with zero generation or invalid group, for an
  `11/11` (`100%`) hit rate. Scope and cache reuse are **improved**, correctness shows **no material
  change**, and post-cut timing remains **inconclusive** until the first eligible hosted run records
  its actual path. The accepted action is a narrow no-shadow mode for this exact class, not a
  website-stack migration; source verification, API reuse, and the complete site remain unchanged.

- **2026-08-28, module-documentation/compiled-sample corpus acceptance:** eleven scoped pull
  requests (#186, #187, #188, #189, #190, #191, #194, #195, #198, #199, and #200) changed only
  documentation/governance records, published-module `src/test/samples`, bounded Tutorial or
  Counter sample sources, append-only Changesets, Chinese mirrors, and the generated capability
  catalog. Candidate and complete-shadow outcomes agreed on success in all eleven. Reconstructed
  no-shadow execution P50/P95 are `8 min 5 s`/`10 min 4 s`; end-to-end P50/P95 are `9 min 13 s`/
  `11 min 18 s`. Every documentation child reused `5/5` immutable API groups with zero generation
  or invalid group. A separate eleven-run successful `main` corpus has complete `qaQuick` P95
  `20 min 41 s`, `16.2%` below the Phase 0 comparator. Scope, cache reuse, and latency are
  **improved** and correctness is **no material change**. The accepted action removes the full
  shadow only from this exact path class; module production, build scripts, ordinary tests, code
  deletion/rename, and sensitive tooling retain it. Website-stack migration remains unjustified.

- **2026-08-28, post-cut cache invalidation controls:** #219 and #220 restored exact `5/5` API
  groups, generated zero, rejected zero, and completed their documentation children in
  `5 min 23 s` and `6 min 2 s`. Relative to #219, #220 was `39 s` (`+12.1%`) slower, a **no material
  change** result across different content. #221 changed the maintained publishing generator,
  correctly reused `0/5`, regenerated all five groups in `1070.8 s`, and completed in
  `23 min 23 s` (`+334.4%`). #222 expanded the registry to six groups, reused `5/6`, generated one
  in `275.0 s`, and completed in `10 min 12 s` (`+89.5%`). #223 moved six first-release artifacts
  into immutable history, reused `5/6`, regenerated the expanded group in `258.5 s`, and completed
  in `7 min 55 s` (`+47.1%`). No run reported an invalid group, and every production site stayed
  within its unchanged budgets; the #223 `main` workflow then built, deployed, and verified live
  module routes. Correct cache discrimination and deployment are **no material change**; latency is
  **mixed** because intentional full and partial invalidation cost more than exact reuse. These are
  heterogeneous release/tooling inputs with different 100-, 127-, and 133-version outputs, so the
  normalized child times do not estimate steady-state performance. Tooling or history drift must
  continue regenerating only the groups its fingerprint invalidates.

- **2026-08-28, first two post-cut exact-hit observations:** #225 and #226 both restored and
  verified `6/6` immutable API groups with zero generation or invalid group. Their cache work took
  `7.1 s` and `4.5 s`, documentation children took `3 min 58 s` and `3 min 54 s`, and production
  site wrappers took `47.3 s` and `43.0 s`. Both sites remained at 469.0 MiB total and 46.7/46.9
  MiB non-API output with unchanged API, JavaScript, CSS, accessibility, and route budgets. From
  #225 to #226, documentation-child time changed by `-1.7%`, site time by `-9.1%`, and rounded size
  did not change. Cache correctness and exact-hit behavior are **no material change** with a `100%`
  hit rate in this two-run post-cut sample; website latency is also **no material change** because
  runner and dependency state differ. This confirms that both accepted no-shadow classes preserve
  documentation integrity, but it is not a P50/P95 corpus. Continue collecting naturally eligible
  runs and keep group-scoped regeneration for every verified fingerprint miss; a website-stack
  migration remains unjustified.

- **2026-08-26, Governance V2 Text Input local acceptance:** the first four-page task split built
  successfully but produced 49,245,936 non-API bytes, 67,722 bytes above the unchanged 46.9 MiB
  limit. Consolidating adjacent editing/IME and rich/Receive Content tasks into two Guides retained
  all four task boundaries while reducing generated output by 161,958 bytes (`-0.33%`) to
  49,083,978 bytes, leaving 94,236 bytes. The final build audited 452 bilingual pages and completed
  the Docusaurus wrapper in `60.0 s`. The result is **mixed** for the initial representation and
  **improved** after correction. This local sample excludes hosted cache and setup behavior; the
  limit remains unchanged and the next content slice must preserve the same stop condition.

- **2026-08-26, Governance V2 Lazy Collections local acceptance:** the first representation
  exceeded the unchanged limit by 9,033 bytes. Consolidating duplicated pager and module-owned
  detail reduced non-API output to 49,168,958 bytes, leaving 9,256 bytes; 454 bilingual pages
  passed in `36.6 s`. The corrected result is **improved**, but the narrow margin requires the next
  content slice to remove or consolidate existing output before adding another route.

- **2026-08-26, Governance V2 Focus/Nested Scroll local acceptance:** reusing the existing
  Modifier Architecture route avoided a new page, but the first expanded representation still
  exceeded the unchanged limit by 21,103 bytes. Removing a duplicated Pager code display while
  retaining its task contract and consolidating architecture prose reduced non-API output to
  49,165,583 bytes, leaving 12,631 bytes. The corrected build audited 454 bilingual pages and
  completed Docusaurus in `51.7 s`. The result is **improved** after correction; capability,
  compiled-region, route, locale, accessibility, and budget gates passed without changing the
  limit. The remaining margin is narrow, so the Shadows slice must continue the same
  consolidate-before-expand rule.

- **2026-08-26, Governance V2 Shadows local acceptance:** the hard cut retained existing routes,
  moved drawing-plane ownership to Modifier Architecture, concentrated backend/cache/diagnostic
  contracts in the Shadow Android module manual, and reduced duplicated Guide detail. The first
  bilingual attempt completed English output but correctly rejected two Chinese links whose
  relative paths were one level too deep. After fixing those links, the final complete build
  audited 454 pages, produced 49,136,607 non-API bytes, left 41,607 bytes under the unchanged limit
  after including this acceptance evidence, and completed Docusaurus in `42.9 s`. The result is
  **improved**: debt and generated size both fell
  without adding a route or weakening a gate. This local observation does not replace device
  shadow-fidelity or performance evidence; the Overlays slice must preserve the same structural
  budget discipline.

- **2026-08-26, Governance V2 Overlays local acceptance:** the hard cut retained all existing
  routes, split application tasks from ADR/module runtime contracts, registered 21 public entries
  and eight compiled regions, and reduced Governance V2 debt from 625 to 590. The complete build
  still audited 454 pages, produced 49,142,652 non-API bytes, left 35,562 bytes under the unchanged
  limit, and completed Docusaurus in `33.4 s`. The result is **improved** without changing the
  website stack, route count, framework behavior, or the budget. This observation reuses existing
  overlay behavior evidence; the Image Loading slice must preserve the same stop condition.

- **2026-08-26, Governance V2 Image Loading local acceptance:** the hard cut retained the Guide,
  Migration, Coil, and Glide routes, registered four public entries and nine sample decisions, and
  reduced Governance V2 debt from 590 to 571. The complete build audited 454 pages, produced
  49,151,753 non-API bytes, left 26,461 bytes under the unchanged limit, and completed Docusaurus
  in `34.6 s`. The result is **improved** without changing the website stack, route count,
  framework behavior, or budget. This observation validates documentation ownership and build
  output, not device, network, or image-decoder performance.

- **2026-08-26, Governance V2 Modifier Architecture local acceptance:** the hard cut retained the
  Architecture and Tutorial routes, registered 51 Modifier/Gesture entries and nine compiled
  sample decisions, replaced the remaining handwritten API inventory with generated Reference
  ownership, and reduced Governance V2 debt from 571 to 516. The complete build audited 454 pages,
  produced 49,095,993 non-API bytes, left 82,221 bytes under the unchanged limit, and completed
  Docusaurus in `28.0 s`. The result is **improved**: structured coverage increased while repeated
  prose and generated size decreased, without changing the website stack, route count, framework
  behavior, or budget. This observation validates documentation structure and output only; it
  reuses existing Modifier contract and renderer evidence.

- **2026-08-26, Governance V2 ConstraintLayout module local acceptance:** the hard cut retained the
  module route, registered all 43 public core/helper DSL entries and five compiled module sample
  decisions, removed repeated chronological phase logs, and reduced Governance V2 debt from 516 to
  468. The first complete build correctly rejected two relative archive links that are not deployed;
  after replacing them with the repository's established immutable-history link form, the complete
  evidence-bearing build audited 454 pages, produced 48,982,759 non-API bytes, left 195,455 bytes
  under the unchanged limit, and completed Docusaurus in `25.2 s`. The result is **improved**:
  structured ownership increased and generated output decreased without changing the website
  stack, routes, framework
  behavior, or budget. This observation reuses existing ConstraintLayout correctness, device,
  visual, and performance evidence and makes no new runtime claim.

- **2026-08-26, Governance V2 Preview tooling local acceptance:** the hard cut retained the
  tooling and five module routes, registered all 62 public Preview entries and nine compiled sample
  decisions, removed repeated protocol and device-inspector prose, and reduced Governance V2 debt
  from 468 to 390. The complete evidence-bearing build audited 454 pages, produced 48,647,612
  non-API bytes, left 530,602 bytes under the unchanged limit, and completed Docusaurus in
  `27.8 s`. The result is **improved**: structured ownership and compiled workflow coverage
  increased while generated output decreased, without changing the website stack, routes,
  framework behavior, or budget. This observation reuses existing protocol, runner,
  device-diagnostic, and Paparazzi evidence and makes no new runtime, visual, or performance claim.

- **2026-08-26, Governance V2 third-party integration local acceptance:** the hard cut retained
  the CameraX, Google Maps, Media3, and legacy ExoPlayer module routes, registered all 32 public
  entries and nine compiled sample decisions, and reduced Governance V2 debt from 390 to 345. The
  complete evidence-bearing build audited 454 pages, produced 48,715,878 non-API bytes, left
  462,336 bytes under the unchanged limit, and completed Docusaurus in `27.4 s`. This is 68,266
  bytes (`0.1403%`) above the Preview slice because four structured module contracts and full
  compiled samples are now emitted. The result is **improved**: exact ownership and bilingual
  executable coverage increased while the build remains well inside the unchanged budget, without
  changing the website stack, routes, framework behavior, or published artifacts. This observation
  reuses existing module/device evidence and makes no new runtime, network, power, or performance
  claim.

- **2026-08-27, Governance V2 Animation local acceptance:** the hard cut retained the two module,
  Tutorial, Migration, and ADR routes, registered the seven previously orphaned composition,
  content, visibility, and layout-motion entries plus 19 compiled or explicitly non-executable
  sample decisions, and reduced Governance V2 debt from 345 to 313. The final evidence-bearing
  build audited 454 pages, produced 48,745,500 non-API bytes, left 432,714 bytes under the
  unchanged limit, and completed Docusaurus in `25.3 s`. Relative to the third-party slice,
  generated non-API output increased 29,622 bytes (`0.0608%`) while local build time decreased
  `7.6%`; the site result is
  **no material change** for one content-only sample, while exact ownership and bilingual sample
  coverage are **improved**. The existing website stack, route count, framework behavior, and
  published artifacts did not change. This observation validates documentation structure and
  output only; it reuses existing Animation behavior evidence and makes no new motion-fidelity or
  performance claim.

- **2026-08-25, Governance V2 Phase 0A:** the initial bilingual contract candidate exceeded the
  unchanged 46.9 MiB non-API limit by 42,041 bytes. Consolidating repeated normative prose and
  moving the generated quality report outside the deploy tree reduced it to 49,175,712 bytes,
  leaving 2,502 bytes; build and post-build recheck both pass. The result is **mixed**: the first
  representation regressed, while consolidation and repeatable verification corrected it without
  removing contracts or raising the limit. This local build excludes deployment/CDN/network
  behavior and separately budgeted API output; Phase 0B must reuse the compiled model and preserve
  the ceiling.

- **2026-08-28, coordinated-release documentation closeout:** after six first-release artifacts
  moved from `current` to immutable `0.1.0-alpha01` history, the complete verifier reused `5/6`
  source-revision groups, regenerated the expanded `2d37ff2e` group, and rejected zero cached
  groups. The production build served 133 API versions, 133 module manuals, and 133 Chinese
  fallback routes; audited 522 site pages; produced 468.9 MiB total and 46.7 MiB non-API output;
  kept JavaScript at 6.6/8.0 MiB, the largest chunk at 650/768 KiB, CSS at 112/128 KiB, and average
  API trees at 3.2/4.5 MiB; and completed the site wrapper in `38.4 s`. Relative to the pre-release
  127-version, 510-page, 468.5 MiB/46.3 MiB, `42.5 s` comparator, immutable coverage increased by
  six versions (`+4.7%`) and 12 pages (`+2.4%`), rounded total and non-API output each increased by
  approximately 0.4 MiB, and wrapper time decreased by `4.1 s` (`-9.6%`). The result is **mixed**:
  released-route coverage and time improved while size headroom narrowed without exceeding any
  gate. This is one warm local comparison using rounded size summaries and does not establish
  hosted cache, deployment, CDN, or latency behavior. The next action is the post-merge hosted
  build and route verification; further content growth must recover non-API headroom rather than
  raising the limit.

- **2026-08-29, repository-source link externalization:** the ViewModel scoped-owner candidate
  exposed that otherwise valid bilingual contracts and compiled evidence increased non-API output
  from the main comparator's 49,177,136 bytes to 49,309,510 bytes, 131,296 bytes above the
  unchanged 46.9 MiB limit. A before-default remark transform now keeps source Markdown links
  repository-relative for verification but emits links to GitHub instead of copying linked
  production and test files into `assets/files`. On the same main corpus, the first corrected
  candidate produced 47,961,611 non-API bytes, 1,215,525 bytes (`2.47%`) below the comparator and
  left 1,216,603 bytes of headroom. All 77 documentation-script tests, TypeScript, structure and
  translation gates, 133 API versions, 133 module manuals, 524-page accessibility audit, site
  shell, and unchanged size budgets passed; the complete site wrapper took `51.2 s`. The result is
  **improved**: source evidence remains reachable while one redundant deployed representation is
  removed without changing routes, APIs, or the budget. This single warm local build does not
  establish hosted latency; the next action is CI exact-cache and hosted-route verification.

- **2026-08-30, AI-tooling pull-request budget recovery:** the first hosted #253 site build
  produced 49,245,936 non-API bytes, 67,722 bytes above the unchanged 46.9 MiB limit. Losslessly
  re-encoding the single 1200-by-630 social-card PNG retained byte-identical decoded RGB pixels
  while reducing it from 761,036 to 608,989 bytes (`-152,047`, `-20.0%`). The same-corpus local
  rebuild then audited 528 pages, produced 49,042,390 non-API bytes with 135,824 bytes of
  headroom, and completed the Docusaurus portion in `29.3 s`. The result is **improved**: the
  route, content, image pixels, API history, and budget remain unchanged while one deployed
  representation is smaller. Hosted and local Node/platform output differs, so the total
  203,546-byte build delta is not attributed solely to the image; the next action is the hosted
  rerun, which must independently remain below the same ceiling.

Git history preserves earlier Paging and site checkpoints. They do not authorize deleting current
contracts, raising the limit without evidence, or re-expanding completed copies.

*/}
