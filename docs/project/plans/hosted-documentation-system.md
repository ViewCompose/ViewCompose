# Hosted Documentation System

## Status

Active.

## Scope

Build the public ViewCompose documentation pipeline defined by
[ADR-0001](../../architecture/decisions/0001-hosted-documentation-platform.md): Docusaurus site,
Dokka API reference, module-derived navigation, pull-request verification, GitHub Pages deployment,
custom domain, and independently versioned module documentation.

## Non-goals

- interactive code execution or server-side rendering;
- authentication, comments, analytics, or paid infrastructure;
- rewriting all existing documents before the platform can publish them;
- committing generated site or Dokka HTML.

## Current baseline

- `docs/` is governed, link-checked, and consumed directly by Docusaurus.
- every published Maven artifact is present in the generated site catalog.
- Dokka 2 generates all 25 versioned API trees through one root Gradle task.
- GitHub Actions verifies pull requests and can deploy the production artifact from `main`.
- the first measured full build takes about one minute for API generation and produces a 104 MB
  site on the current development machine.

## Completion criteria

1. `docs.viewcompose.com` serves the production site over HTTPS.
2. pull requests verify site build, links, and changed-module API generation.
3. all published modules have an API entry and a module manual or an explicit planned state.
4. module releases can preserve their own manual and API version without versioning the whole site.
5. English canonical pages and the Chinese locale structure can be built independently.
6. size, build-time, accessibility, and broken-link gates block regressions.

## Ordered work

1. **Foundation — completed**
   - record the platform ADR;
   - add Docusaurus site source and local build;
   - derive the module catalog from publishing metadata;
   - add selected-module Dokka assembly;
   - add PR verification and guarded Pages deployment.
2. **API completeness — in progress**
   - generate every published artifact — completed;
   - establish the API documentation quality standard and audit path — completed;
   - improve KDoc coverage and immutable source links — in progress;
   - connect Maven release versions and stable aliases.
3. **Module manuals — pending**
   - establish the module page template;
   - document foundation modules first, followed by feature and tooling families;
   - generate navigation and compatibility data.
4. **Learning and migration — pending**
   - add getting started and first application tutorials;
   - add Compose concept comparison and migration paths;
   - compile all non-trivial samples.
5. **Production hardening — in progress**
   - add Chinese locale structure — completed;
   - deploy and verify the custom domain — completed;
   - add search, redirects, accessibility checks, and size budgets — pending.

## Validation

- `./gradlew verifyDocumentationStructure`
- selected-module and complete-catalog Dokka generation
- Docusaurus type check and production build
- pull-request workflow dry run
- production URL and custom-domain verification

## Last verified

2026-08-02.

## Next action

Follow the [API documentation completeness plan](./api-documentation-completeness.md): repair the
`viewcompose-runtime` baseline first, then advance through the core UI dependency chain while source
links and strict per-module gates are added.
