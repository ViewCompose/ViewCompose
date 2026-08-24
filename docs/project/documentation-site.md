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
   and verifies every manifest, route, alias, and pinned source link. Missing frozen commits are
   fetched by exact full SHA; movable references are never substituted. Unpublished artifacts get
   only working-tree `current` output, while revisions predating current build contracts receive
   temporary configuration shims that never enter published output.
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

Search keeps page summaries, headings, public contracts, and command guidance indexed. Exhaustive
defect-evidence tables and dated measurement ledgers remain rendered and directly linkable, but use
`search-partition-detail` so repeated historical detail does not dominate the local index. Every
excluded block must retain an adjacent searchable heading and summary; API contracts, command
references, and reader-facing guides must not use this partition.

Exceptionally large temporary execution plans may use route-level search segmentation only when
the active-plan index retains a searchable purpose and scope summary, every durable public contract
and command remains in its searchable owning documentation, and the plan page remains rendered and
directly linkable. Each excluded route must be named explicitly in the site configuration and
supported by paired size evidence in this page.

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
tree, so those copies add storage but no localized content or supported route.

The budget model separates expected release-history growth from regressions. Current ceilings are
46 MiB for non-API output, 4.5 MiB average and 24 MiB maximum per API tree, 1 MiB for API routing
overhead, 8 MiB total and 768 KiB largest-file JavaScript, 128 KiB CSS, 6.25 MiB per locale search
index, and 120 seconds for the Docusaurus build. Locale-prefixed API copies remain forbidden.

The non-API ceiling evolved from 41 MiB through 46 MiB only after paired builds attributed growth
to durable bilingual contracts and representation reviews removed avoidable duplication. Completed
measurements are consolidated below instead of repeating their execution narrative in this active
contract. Raise any threshold only with same-corpus absolute and normalized results, reader or
release value, a conclusion, limitations, and a next stop condition.

At the 46 MiB boundary, a failing branch must first consolidate completed evidence or change site
representation. Current public API, architecture, migration, tutorial, and module contracts must
not be deleted merely to recover budget, and valid immutable API history remains governed by its
separate per-tree budgets.

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

`.github/workflows/documentation.yml` builds affected pull requests but never deploys them. A push
to `main`, or a manual run on `main`, produces the complete site and deploys it through the protected
`github-pages` environment.

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

Completed checkpoints are condensed here; Git history retains their execution detail.

- **2026-08-24, Android View Phase 2:** the complete 100-version corpus plus one unpublished
  current API tree initially produced 48,276,155 non-API bytes, 41,659 bytes beyond 46 MiB.
  Moving closed Phase 1 and 2 API narratives from the active integration plan to their owning
  manuals reduced the evidence-inclusive corpus to 48,186,584 bytes, an 89,571-byte (0.186%)
  improvement, and left 47,912 bytes of headroom without changing a threshold or deleting a
  durable contract.
  English/Chinese search is 5,574,102/6,072,178 bytes; 430 pages, accessibility, API routing, and
  the 34.2-second build passed. This local macOS comparison does not measure Linux deployment;
  CI remains the next gate, and completed phase detail must continue to converge on owning docs.
- **2026-08-23, Diagnostics Phase 2:** on the complete 100-version corpus plus one unpublished
  current API tree, the Phase 1 baseline, initial Phase 2 output, and consolidated output measured
  48,217,723, 48,480,209, and 47,801,356 non-API bytes. The initial +262,486-byte (+0.544%)
  growth exceeded 46 MiB by 245,713 bytes. Consolidating repeated closed performance narratives
  into current decision tables removed 678,853 bytes (-1.400%) without deleting contracts or
  raising the ceiling; final output is 416,367 bytes (-0.863%) below the baseline and leaves
  433,140 bytes of headroom. English/Chinese search is 5,432,660/5,916,011 bytes. Representation
  **improved**; 432 pages, both locales, compatibility anchors, accessibility, API routing, and the
  28.7-second build passed. This local macOS comparison reused one generated API corpus and does
  not measure network deployment or another host; the next action is to keep the compact decision
  ledger while Phase 3 adds highlighting guidance.
- **2026-08-23, Diagnostics Phase 1:** with complete 100-version output, main, the initial
  candidate, and the consolidated candidate measured 48,209,136, 48,349,648, and 48,217,723
  non-API bytes. The initial +140,512-byte (0.29%) regression exceeded 46 MiB by 115,152 bytes;
  consolidating completed site evidence and repeated module guidance removed 131,925 bytes. The
  final +8,587 bytes (0.02%) leaves 16,773 bytes of headroom, while English/Chinese search changed
  from 5,552,901/6,041,625 to 5,551,470/6,039,478 bytes. Representation **improved** with no
  threshold change. Main came from the Linux CI artifact and the candidate from the local macOS
  complete build, which limits machine-level comparison; both used the same dependency set and
  non-API accounting. Keep central contracts searchable and consolidate repetition before Phase 2.
- **2026-08-23, Animation Phase 5:** on one complete 100-version API corpus, main/candidate
  non-API output was 47,678,608/47,827,249 bytes (+148,641, 0.31%) and search was
  5,369,073/5,841,187 versus 5,400,738/5,876,861 bytes. Final evidence-inclusive output was
  47,840,947 bytes. The bilingual Q3-contract growth **regressed** but was accepted, moving only
  non-API from 45.5 to 46 MiB. Reusing one API corpus is the limitation; another ceiling failure
  requires consolidation or representation work.
- **2026-08-23, Animation Phase 4:** main/unconsolidated/final output was
  47,667,169/47,810,162/47,678,361 bytes; consolidation saved 131,801 bytes and final search was
  5,369,073/5,841,187. Representation **improved** without moving a threshold. The corpus
  limitation is unchanged; unresolved requirements remain in owning manuals.
- **2026-08-18, plan search partition:** main was 45,313,029 bytes. Candidate output/search fell
  from 48,070,239 and 6,575,126/7,037,032 to 46,680,423 and
  5,887,279/6,335,029, a 2.89% output and about 10% search reduction. The result was **mixed**;
  non-API moved to 45.5 MiB, search stayed at 6.25 MiB. API history was not rebuilt; the next action
  was plan consolidation.
- **2026-08-18 to 2026-08-17, ledger partitions:** lazy evidence changed
  45,041,594 main to 45,298,674 accepted bytes after saving 144,489 bytes; manual-review evidence
  changed 44,793,209 main and 45,212,251 initial candidate to 44,941,342 accepted bytes. Results
  were **mixed** and **improved** respectively; compared non-API/search trees were complete but API
  history was not rebuilt. The resulting action was structural plan/index segmentation.
- **2026-08-16, observed-property contracts:** main/candidate was
  43,622,588/44,251,626 bytes (+629,038, 1.44%), with search
  5,592,645/6,015,718 versus 5,732,917/6,165,632. The result **regressed** but was accepted after
  duplicate-route review; non-API moved to 43 MiB and the next failure required partitioning.
- **2026-08-15 to 2026-08-14:** native contracts changed 42,829,400 to 43,024,465 bytes
  (+195,065, 0.46%) and moved non-API to 42 MiB; the Demo-plan pair changed 39.999791 to
  40.427350 MiB and moved it to 41 MiB. Both accepted durable-contract regressions, used complete
  non-API trees without rebuilt API history, and required representation review at the next limit.
- **2026-08-06, complete-history baseline:** 69 immutable and 9 unpublished current trees passed
  source, route, locale, search, redirect, and 310-page accessibility checks. Total/non-API was
  316.3/32.9 MiB, API trees averaged 3.6 MiB, largest JavaScript was 650 KiB, and build time was
  24.2 seconds. Later checkpoints supersede its size thresholds, not its reconstruction proof.

</div>
