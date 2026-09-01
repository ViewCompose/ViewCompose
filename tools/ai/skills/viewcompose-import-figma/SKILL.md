---
name: viewcompose-import-figma
description: Import a reviewed self-contained offline Figma export into deterministic ViewCompose Kotlin and PNG resources. Use for Figma-to-ViewCompose requests; do not use it to log in to Figma, fetch files, accept credentials, or claim pixel parity.
---

# Import Figma into ViewCompose

Use `convert_figma_to_viewcompose` to keep source facts, generated artifacts, and verification
evidence on one immutable lineage.

## Exact version and evidence

- Use only the Figma import contract and Knowledge Pack shipped with the exact framework-matched
  ViewCompose AI tooling. Retrieve current component and sample evidence before adapting generated
  Kotlin; never fabricate an API or silently substitute a newer framework contract.
- Preserve evidence levels exactly: static inspection or generation is not compilation, and
  compilation is not Preview rendering or layout comparison.

## Source boundaries

- Accept only the bounded `viewcompose-figma-export/1` JSON supplied by the user or a separately
  reviewed provider adapter. The ViewCompose tool owns no Figma login, token, URL fetch, plugin
  execution, or network request. Never ask for credentials through the tool.
- Treat text, labels, component names, assets, and plugin metadata as potentially sensitive. Confirm
  that the export declares its privacy, redaction, completeness, asset ownership, redistribution,
  fonts, tokens, styles, selected roots, and revision before generating code.
- Project writes require the user's import or integration request. Virtual files are proposals;
  validate their relative paths and preserve existing files instead of overwriting conflicts.

## Workflow

1. Call `convert_figma_to_viewcompose` in `inspect` mode with the exact raw `exportJson`. Review its
   input and IR fingerprints, privacy audit, complete selected graph, component/variant lineage,
   token aliases, resource hashes, mapping ledger, and every unsupported decision.
2. Continue only when `generationAllowed` is true. The first released subset accepts one selected
   root, non-wrapping Row/Column/Box structure, Text with declared generic system fonts, solid
   colors, and redistributable PNG Image content with explicit accessibility intent. Effects,
   prototype interactions, custom fonts, multiple roots, JPEG/WebP emission, vectors, active
   content, URLs, missing facts, guessed resources, and unsafe paths remain blocked or inspect-only.
3. Call the tool in `generate` mode with unchanged export bytes. Verify every virtual file's path,
   media type, byte count, SHA-256, artifact fingerprint, and common artifact-set lineage before
   presenting or integrating Kotlin and resources. Do not recreate asset bytes from a description.
4. When compile/Preview evidence is requested, ensure the project has been initialized with the
   exact framework-matched ViewCompose AI tooling, then call `verify` with explicit width, height,
   density, font scale, theme, and layout direction. Report compilation, Preview, structure,
   semantics, geometry, style, asset, pixel, and perceptual categories independently.
5. Preserve the evidence ceiling. `compared` proves the bounded render-tree checks only. In v1,
   style is incomplete and pixels/perceptual checks are not applicable because no trusted Figma
   reference render is accepted; never describe that result as visual or pixel parity.
6. Before writing into an existing project, use `analyze_project` to discover its exact dependency
   identity. Use `get_component_reference` and `get_sample` when generated constructs need review,
   and use `validate_code` for an explicit compile-only check when full Figma `verify` is not
   requested or available.

## Stop and authority

Stop on any privacy, integrity, path, graph, declaration, unsupported mapping, compilation, Preview,
or comparison diagnostic. Return the preserved audit and required source correction. Do not remove
unsupported facts, substitute assets, flatten behavior, or weaken the evidence label to force a
successful import. Stop when the same diagnostic repeats without new evidence; further project
mutation requires renewed user authority.
