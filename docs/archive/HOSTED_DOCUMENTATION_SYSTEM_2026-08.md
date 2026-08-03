# Hosted Documentation System

## Status

Completed.

## Scope

Build the public ViewCompose documentation pipeline defined by
[ADR-0001](../architecture/decisions/0001-hosted-documentation-platform.md): Docusaurus site,
Dokka API reference, module-derived navigation, pull-request verification, GitHub Pages deployment,
custom domain, and independently versioned module documentation.

## Non-goals

- interactive code execution or server-side rendering;
- authentication, comments, analytics, or paid infrastructure;
- rewriting all existing documents before the platform can publish them;
- committing generated site or Dokka HTML.

## Completed baseline

- `docs/` is governed, link-checked, and consumed directly by Docusaurus.
- every published Maven artifact is present in the generated site catalog.
- Dokka 2 generates all 25 versioned API trees through one root Gradle task.
- all 25 artifacts have strict source documentation, bilingual module manuals, immutable source
  links, and verified version/current/latest route policy.
- the Compose migration section has one consolidated matrix plus dedicated state, layout,
  host/lifecycle, and navigation paths in canonical English and required current Chinese mirrors.
- local English and Chinese search, compatibility redirects, site-page accessibility checks, and
  explicit build-time and output-size budgets run in the production build.
- GitHub Actions verifies pull requests and can deploy the production artifact from `main`.
- the final measured Docusaurus build took about ten seconds and produced a 204.1 MiB bilingual
  site including all versioned API trees on the development machine used for completion.

## Completion criteria

1. `docs.viewcompose.com` serves the production site over HTTPS.
2. pull requests verify site build, links, and changed-module API generation.
3. all published modules have an API entry and an available module manual.
4. module releases can preserve their own manual and API version without versioning the whole site.
5. English canonical pages and the Chinese locale structure can be built independently.
6. size, build-time, accessibility, and broken-link gates block regressions.

## Completed work

1. **Foundation**
   - recorded the platform ADR;
   - added Docusaurus site source and local build;
   - derived the module catalog from publishing metadata;
   - added selected-module Dokka assembly;
   - added pull-request verification and guarded Pages deployment.
2. **API completeness**
   - generated every published artifact;
   - established the API documentation quality standard and audit path;
   - improved KDoc coverage and immutable source links;
   - connected Maven release versions and stable aliases; stable aliases remain absent while every
     registered version is a prerelease.
3. **Module manuals**
   - established the module page template;
   - documented foundation, feature, integration, optional backend, and tooling families;
   - generated navigation and compatibility data.
4. **Learning and migration**
   - added getting started and first application tutorials;
   - added the canonical-English Compose comparison and migration paths;
   - kept migration code anchored to repository samples compiled by `qaQuick`;
   - added the required Chinese migration mirrors and translation-policy entries.
5. **Production hardening**
   - added Chinese locale structure;
   - deployed and verified the custom domain;
   - added local search and compatibility redirects;
   - added Docusaurus-owned page accessibility checks;
   - added build-time, output, JavaScript, CSS, and search-index budgets.

## Validation

- `./gradlew verifyDocumentationStructure`
- `./gradlew verifyCompleteViewComposeApiDocs`
- Docusaurus type check and production build
- translation, accessibility, size, and build-time gates
- pull-request workflow dry run
- production URL and custom-domain verification

## Last verified

2026-08-03: the canonical-English Compose migration overview and four focused pages have reviewed,
required Chinese mirrors. Translation freshness, local search, compatibility redirects, 132
site-page accessibility checks, 204.1 MiB output, a 650 KiB largest JavaScript asset, and an
approximately ten-second Docusaurus build all passed their production gates alongside document
structure, complete 25-module Dokka, type checking, both-locale build, and `qaQuick`.

## Maintenance handoff

Revalidate the migration baselines when any listed upstream or ViewCompose version changes. Keep
Chinese mirrors current, review budget changes with measurements, and treat Dokka-generated page
accessibility as a separate template-level follow-up rather than weakening the site-owned gate.
