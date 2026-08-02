# ADR-0001: Hosted Documentation Platform

## Status and date

Accepted — 2026-08-02.

## Context

ViewCompose needs one public documentation system for framework principles, tutorials, Compose
migration, Android Studio tooling, and generated Kotlin/Java API reference. Public Maven artifacts
own independent versions, so module manuals and API reference cannot assume a single repository
release train.

Documentation changes must stay in the same pull request as the code they describe. Generated HTML
must not be committed, and the hosting layer must remain replaceable without changing public URLs.
The initial project should have no recurring infrastructure cost beyond the existing domain.

## Decision

1. Handwritten source remains under the repository's `docs/` tree.
2. Docusaurus 3 builds the public site from that source and provides navigation, internationalized
   content, version-aware documentation, and extensible UI components.
3. Dokka 2 generates Kotlin/Java API reference per Maven artifact and version. Each module pins a
   full immutable source commit beside its version so generated line links never follow `main`.
   Dokka HTML is the hosted format; generated output is not checked in.
4. GitHub Actions verifies pull requests and assembles production output. Only `main` can deploy.
5. GitHub Pages hosts the static result at `docs.viewcompose.com`.
6. The site source lives under `website/`; generated API output lives under ignored build or
   generated directories.
7. Published artifact metadata and `docs/modules/README.md` drive module navigation so site
   configuration does not duplicate the module registry.
8. Public routes follow the generator-neutral contract in the documentation governance rules.
9. Search starts with a local/static-capable fallback and may switch to the free Algolia DocSearch
   program after the production domain is available.

## Alternatives considered

### VitePress

VitePress offers a smaller build, built-in local search, and good internationalization. It was not
selected because independent documentation versioning would require a larger custom subsystem.

### Material for MkDocs

Material for MkDocs offers an excellent writing experience and browser search. Its versioning and
multi-language model rely on additional projects and tools, which increases coordination cost for
independently versioned modules.

### Dedicated hosted documentation service

A hosted service could reduce initial setup, but would separate documentation changes from code
review, introduce service-specific storage and billing, and make the module version model less
controllable.

## Consequences and trade-offs

- The repository gains a Node-based website toolchain in addition to Gradle.
- Site builds must remain below GitHub Pages deployment and size limits.
- Dokka output is cached or generated incrementally so ordinary documentation changes do not rebuild
  every historical API version.
- Docusaurus module configuration must be generated from the canonical catalog rather than copied by
  hand.
- A custom domain keeps future hosting migration transparent to readers.
- Dynamic server-side features are out of scope; the deployed product remains static.

## Affected modules and contracts

All artifacts registered in `gradle/viewcompose-publishing.properties` participate in generated API
reference. Documentation structure, release workflows, and source-comment quality gates are also
affected.

## Validation and rollout

1. Build the Docusaurus site locally and on pull requests.
2. Generate Dokka HTML for a selected artifact set, then for the complete published catalog.
3. Verify immutable version routes, current/stable-latest aliases, source links, complete module
   catalog parity, and site size.
4. Enable Pages deployment from `main` only.
5. Configure and verify `docs.viewcompose.com` after the first successful Pages deployment.
6. Add per-module release snapshots and API retention after the current documentation site is
   stable.
