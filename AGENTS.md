# Repository Instructions

These rules apply to AI-assisted work in this repository:

1. Start documentation discovery from [`docs/README.md`](docs/README.md).
2. Follow [`docs/project/documentation-governance.md`](docs/project/documentation-governance.md)
   when creating, moving, or updating documentation.
3. Do not add Markdown files to the repository root unless the documented root allowlist and its
   automated guard are intentionally changed together.
4. Treat `docs/archive/` as historical evidence, not current requirements.
5. Update the relevant active document in the same change as an architecture, behavior, release,
   or workflow change.
6. Use repository-relative links and never commit local absolute paths.
7. Run `./gradlew verifyDocumentationStructure`; it is also part of `qaQuick`.

If an active document conflicts with code or tests, verify the implementation and correct the
document rather than creating a parallel explanation.
