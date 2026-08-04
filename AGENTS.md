# Repository Instructions

These rules apply to AI-assisted work in this repository:

1. Start documentation discovery from [`docs/README.md`](docs/README.md).
2. Follow [`docs/project/documentation-governance.md`](docs/project/documentation-governance.md)
   when creating, moving, or updating documentation.
3. Read the owning entry in [`docs/modules/README.md`](docs/modules/README.md) before changing a
   published module, and update its module manual once available.
4. Before implementing a new or changed public/protected API, assign its Q level and identify every
   applicable contract field. The same change must include canonical-English KDoc/Javadoc,
   compiled Q3 samples, and owning-module documentation required by the
   [`Source Documentation and API Comment Standard`](docs/project/api-documentation-quality.md).
   Never add placeholder comments or defer documentation to a cleanup task; a `No documentation
   impact` conclusion needs a written rationale.
5. Do not add Markdown files to the repository root unless the documented root allowlist and its
   automated guard are intentionally changed together.
6. Treat `docs/archive/` as historical evidence, not current requirements.
7. Update the relevant active document in the same change as an architecture, behavior, release,
   or workflow change.
8. Keep framework concepts separate from module-specific dependency, compatibility, and API
   contracts. New published modules must enter the module catalog and documentation tree.
9. Use repository-relative links and never commit local absolute paths.
10. Keep titles, headings, and narrative prose in English under `docs/` and in Simplified Chinese
    under the matching `zh-CN` locale path. Preserve code, identifiers, commands, URLs, and real UI
    literals exactly, formatting a foreign-language prose literal as inline code. Every active
    handwritten public page requires a current Chinese mirror in the same change. Never update only
    a stored translation fingerprint.
11. Run `./gradlew verifyDocumentationStructure`; it is also part of `qaQuick`.
12. Durable implementation comments explain reasons, invariants, lifecycle/concurrency constraints,
    or platform workarounds. Do not add bilingual duplicate comments or narrate code line by line.
13. Every pull request that changes a published artifact's production source, publication inputs,
    or compiled API samples must add one immutable `release/changes/<unique>.json` file. Classify
    each detected artifact as `breaking`, `feature`, `fix`, or explicitly ignored with a concrete
    reason. Never hand-write `dependency`; release planning derives reverse-dependency propagation.

If an active document conflicts with code or tests, verify the implementation and correct the
document rather than creating a parallel explanation.
