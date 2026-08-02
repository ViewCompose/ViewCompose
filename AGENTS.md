# Repository Instructions

These rules apply to AI-assisted work in this repository:

1. Start documentation discovery from [`docs/README.md`](docs/README.md).
2. Follow [`docs/project/documentation-governance.md`](docs/project/documentation-governance.md)
   when creating, moving, or updating documentation.
3. Read the owning entry in [`docs/modules/README.md`](docs/modules/README.md) before changing a
   published module, and update its module manual once available.
4. Apply the documentation change impact matrix to code changes. Public API changes require source
   KDoc/Javadoc and the owning module documentation; a `No documentation impact` conclusion needs a
   written rationale.
5. Do not add Markdown files to the repository root unless the documented root allowlist and its
   automated guard are intentionally changed together.
6. Treat `docs/archive/` as historical evidence, not current requirements.
7. Update the relevant active document in the same change as an architecture, behavior, release,
   or workflow change.
8. Keep framework concepts separate from module-specific dependency, compatibility, and API
   contracts. New published modules must enter the module catalog and documentation tree.
9. Use repository-relative links and never commit local absolute paths.
10. Treat English as the canonical documentation source. When canonical public documentation
    changes, update the Chinese mirror, explicitly mark an existing tracked mirror stale, or record
    why the page is not translated according to the language policy. Never update only a stored
    translation fingerprint.
11. Run `./gradlew verifyDocumentationStructure`; it is also part of `qaQuick`.

If an active document conflicts with code or tests, verify the implementation and correct the
document rather than creating a parallel explanation.
