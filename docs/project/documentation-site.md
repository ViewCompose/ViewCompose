# Documentation Site Operations

## Purpose

This page is the operating guide for the hosted ViewCompose documentation system. Content rules
remain authoritative in [Documentation Governance](./documentation-governance.md), while platform
selection and trade-offs are recorded in
[ADR-0001](../architecture/decisions/0001-hosted-documentation-platform.md).

## Build pipeline

The production artifact is assembled in four explicit stages:

1. `verifyDocumentationStructure` validates source placement, catalog parity, reachability, and
   repository links.
2. `assembleViewComposeApiDocs` runs Dokka 2 for each published artifact and copies the result to
   ignored, versioned paths under `website/generated/api/`.
3. the website catalog generator reads publishing metadata and `docs/modules/README.md`; it does
   not maintain a second module registry.
4. Docusaurus type-checks and builds the handwritten documents, site presentation, and generated
   API output into `website/build/`.

Run the complete local verification from the repository root:

```bash
./gradlew verifyDocumentationStructure assembleViewComposeApiDocs
cd website
npm ci
npm run typecheck
npm run build
```

During local iteration, `-PviewComposeDocsModules=artifact-a,artifact-b` limits Dokka assembly to an
explicit subset. A production build never uses this shortcut.

## API versions and aliases

Immutable API trees use `/api/<artifact>/<version>/`. The mutable `current` alias follows the
version currently registered by the repository. The `latest` alias is generated only for stable
versions; alpha, beta, release-candidate, snapshot, preview, development, and EAP versions must not
silently become `latest`.

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
- If Docusaurus reports a broken link, preserve strict checking; generated static API links are the
  only links explicitly exempted from its route graph.
- If deployment fails after a successful build, keep the last Pages deployment live and rerun only
  after checking repository Pages settings and the `github-pages` environment.
- If the custom domain fails while the Pages artifact is healthy, diagnose DNS and domain
  verification separately from the documentation build.

## Last verified

2026-08-02: all 25 published artifacts generated successfully; the production site built at about
104 MB. One existing unresolved KDoc symbol link in `viewcompose-host-android` remains API quality
work and does not invalidate the generated tree.
