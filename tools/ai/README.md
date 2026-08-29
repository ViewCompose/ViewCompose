# ViewCompose AI tooling contracts

This directory owns provider-neutral, process-isolated development-tooling contracts. It is not a
runtime module and must never be added to an application artifact's dependency graph.

The implementation order is fixed by
[`docs/project/plans/ai-verifiable-development-tooling.md`](../../docs/project/plans/ai-verifiable-development-tooling.md):

1. `contracts/` freezes independently versioned schemas.
2. `evaluation/` freezes denominators, expected outcomes, and metric thresholds before a tool is
   implemented.
3. `scripts/verify-phase0.mjs` rejects drift, duplicate IDs, unsafe fixture paths, incomplete metric
   coverage, and incompatible schema declarations.
4. `knowledge/` owns reviewed provider-neutral rules, while `scripts/knowledge-generator.mjs`
   combines those rules with Governance V2, canonical source declarations, and compiled samples.
5. `generated/current-source/` contains the exact versioned Phase 1 bundle. Later validator, CLI,
   MCP, Design IR conversion, and visual adapters may consume it only through the frozen contracts.
6. `scripts/static-validator.mjs` derives its symbol index from that generated bundle and emits the
   frozen result envelope; `scripts/project-analyzer.mjs` owns bounded read-only inventory without
   executing an inspected project's Gradle build.
7. `scripts/compiler-adapter.mjs` compiles accepted source in the fixed
   `:tools:ai-compiler-harness` lane and labels a result `compiled` only after bounded class output
   passes content-addressed integrity checks.
8. `scripts/preview-adapter.mjs` discovers and renders only allowlisted compiled targets through the
   canonical Preview protocol, then labels a result `rendered` only after PNG and render-tree
   containment, structure, size, and fingerprint checks pass.
9. `scripts/ai-tool.mjs` is the internal stdin/stdout CLI over the same provider-neutral static,
   compile, render, and project-analysis core. It validates the frozen envelope and exact Knowledge
   Bundle identity before dispatch and is the transport-parity reference for Phase 3.
10. `scripts/knowledge-retriever.mjs` integrity-checks the complete immutable bundle before exposing
    exact API, component, and sample lookup or deterministic ranked component search. Its input
    schemas are shared catalog data for every transport.
11. `scripts/mcp-server.mjs` exposes that same catalog and dispatcher over local newline-delimited
    stdio. It supports modern MCP `2026-07-28` per-request metadata plus the exact `2025-11-25`
    compatibility handshake, with no network listener or session-derived modern state.

Run the Phase 0 gate with:

```bash
npm --prefix tools/ai run verify
```

The root `qaQuick` lifecycle also runs `verifyAiToolingContracts`. No command in this directory may
execute an inspected project's build scripts, load a model provider, read credentials, or write
outside a tool-owned output directory.

Generate and freshness-check the Phase 1 bundle with:

```bash
npm --prefix tools/ai run generate:knowledge
npm --prefix tools/ai run verify:knowledge
```

The compact generated discovery surface is also copied to `website/static/llms.txt`. Commit
canonical inputs or generator changes first, then generate from that immutable revision and commit
the outputs; never edit generated files manually.

Run the current Phase 2 static and project-security corpus with:

```bash
npm --prefix tools/ai run verify:phase2-static
./gradlew verifyAiStaticTooling
```

The static validator reports only facts it can establish from the generated governed-symbol index.
Supporting public types that are referenced by capability signatures but do not have their own
governed entry are not declared nonexistent merely because they are absent from `symbols.jsonl`.
Static success is evidence level `static`, never `compiled`.

Run the pinned Phase 2 compiler corpus with JDK 21:

```bash
./gradlew :tools:ai-compiler-harness:prepareAiCompilerLane
npm --prefix tools/ai run verify:phase2-compile
```

The preparation task resolves only the harness's fixed classpath. The compiler request itself runs
Gradle offline with a fixed task, Android 36/JVM 11 lane, one GiB heap, two workers, and no daemon,
build cache, or configuration cache. Requests may select only generated stable IDs and the current
`viewcompose-ui-foundation` artifact allowlist; they cannot supply a dependency coordinate, Gradle
task, project path, output path, or build script. Content-addressed inputs are immutable, cached
class output is re-fingerprinted before reuse, and timeouts, cancellation, output limits, compiler
diagnostics, and cache poisoning use stable result codes. Android resource fixtures and additional
artifact lanes remain unsupported in this slice.

After the repository Preview lane is prepared, run the Phase 2 render corpus with:

```bash
./gradlew qaPreview
npm --prefix tools/ai run verify:phase2-render
```

The first render lane supports the compiled `samples.counter.CounterPreview` target, its declared
light/dark variants, and the catalog's fixed viewport, density, font scale, locale, and layout
direction. Requests cannot select a project, Gradle task, source path, worker class, dependency, or
output path. Discovery and render use fixed offline Gradle plans on JDK 21. The adapter validates
protocol identity, descriptor/variant identity, source containment, response correlation, exact
artifact filenames, symbolic-link absence, PNG chunk structure and dimensions, render-tree JSON,
hard byte limits, and a combined output fingerprint. Cached artifacts receive the same checks as a
new render; poisoned cache entries fail closed.

Project analysis accepts one absolute root, rejects path escape and all requested build execution,
never follows symbolic links, excludes common build output and secret-bearing files, and enforces
fixed hard caps above request-level file, byte, depth, timeout, and output limits. Output truncation
does not return the oversized inventory. Without executing Gradle, the current analyzer derives
exact ViewCompose coordinates and current-bundle version disposition, governed imports, owning
artifacts and capabilities, Android SDK declarations, Preview sources, and Android XML or Jetpack
Compose migration candidates. It reports unresolved namespaces, unknown artifacts, missing exact
artifact declarations, and version-lane differences as structured findings. Regex-derived facts do
not claim transitive dependency resolution, version-catalog alias resolution, deprecation state, or
migration fidelity, and the analyzer never mutates code.

Invoke the Phase 2 internal CLI by writing exactly one frozen request envelope to stdin:

```bash
npm --silent --prefix tools/ai run tool -- --pretty < request.json
```

It currently dispatches `get_api_reference`, `get_component_reference`, `search_component`,
`get_sample`, `validate_code` (`static` or `compile`), `render_preview`, `diagnose_layout`, and
`analyze_project`. The request must name the exact `framework.versionLane` and
`framework.identity` from `generated/current-source/manifest.json`; input, output, and timeout
limits are mandatory and are propagated to the underlying adapter. Stdout contains only one JSON
result. Operational errors use stderr and exit code 2. The CLI accepts no project-selected command,
Gradle task, classpath, dependency coordinate, output directory, network endpoint, or credential.

Run the frozen deterministic retrieval corpus directly or through the root quality gate:

```bash
npm --prefix tools/ai run verify:phase3-retrieval
./gradlew verifyAiRetrieval
```

Every retrieval request selects `versionLane: "current-source"`; released lanes are not guessed.
`get_api_reference` requires one exact symbol, capability, or artifact identifier.
`get_component_reference` resolves overload parameters and defaults, artifact/capability versions,
applicable reviewed rules, and the declared sample; ambiguous receiver families require an exact
`symbolId` or receiver. `get_sample` preserves the distinction between compiled source and an
explicitly non-executable evidence outline. `search_component` supports bounded artifact, artifact
version, capability, and kind filters and uses a stable lexical score with deterministic tie breaks.
No retrieval result reads canonical source files outside the integrity-checked bundle.

Run the deterministic layout-diagnosis corpus directly or through the root quality gate:

```bash
npm --prefix tools/ai run verify:phase3-layout
./gradlew verifyAiLayoutDiagnosis
```

`diagnose_layout` accepts the same allowlisted target and bounded configuration as
`render_preview`; it never accepts a caller-selected render-tree path. After rendering, it reopens
only the derived content-addressed tree and rechecks every path segment, byte count, and SHA-256.
The tool maps Preview protocol v1 facts for zero size, clipping, text ellipsis, and text-content
clipping to stable source-aware codes. It also preserves bounded renderer warnings, returns at most
100 findings, and fails closed on an unknown diagnostic kind or changed evidence. A clean result
means only that this renderer reported no structured layout diagnostic or warning; it is not a
pixel, accessibility, overlap, or design-intent claim.

Run the local MCP server and its protocol/parity gate with:

```bash
npm --silent --prefix tools/ai run mcp
npm --prefix tools/ai run verify:phase3-mcp
./gradlew verifyAiMcp
```

The preferred protocol follows the
[MCP `2026-07-28` specification](https://modelcontextprotocol.io/specification/2026-07-28): clients
may call `server/discover` and every request must carry `io.modelcontextprotocol/protocolVersion` and
`io.modelcontextprotocol/clientCapabilities` in `params._meta`. For clients that have not yet
migrated, the same process accepts only the frozen `2025-11-25` `initialize`/`initialized`
lifecycle; it never silently downgrades either era. `tools/list` returns eight tools in stable
order: the four retrieval tools, `validate_code`, `render_preview`, `diagnose_layout`, and
`analyze_project`.

Every `tools/call` creates the same immutable request envelope used by the CLI. MCP returns that
provider-neutral result unchanged as `structuredContent` and as serialized text for compatibility.
Tool argument failures remain actionable tool errors, while malformed requests and unknown tools
remain JSON-RPC errors. Progress is emitted only for a caller-provided token. Cancellation aborts
the underlying bounded execution and suppresses every later response or progress message. The
stdio boundary accepts at most 4 MiB per message and four concurrent calls, writes only MCP JSON to
stdout, uses content-free stderr diagnostics, and opens no socket. The current server is still an
internal repository tool rather than an installed or semantically versioned distribution.

## Consumer Agent workflows

Five client-neutral consumer skills live below `skills/`:

- `viewcompose-api-reference` retrieves exact APIs and compiled samples without writing files.
- `viewcompose-create-screen` retrieves before implementation and requires compile-backed delivery.
- `viewcompose-review` keeps review read-only unless the caller also asks for a fix.
- `viewcompose-debug-layout` uses only allowlisted Preview and structured layout evidence.
- `viewcompose-validate` requires hermetic compilation and renders only covered allowlisted targets.

Each folder is independently installable and contains only `SKILL.md`; the repository does not add
provider-specific metadata or root aliases. `skills/manifest.json` freezes required and conditional
tools, evidence bounds, mutation policy, exact version selection, and the repeated-diagnostic stop
condition. These skills orchestrate the existing deterministic tools; they do not contain a second
API reference, execute a project-selected build, add conversion claims, or grant project writes
beyond the user's request.

Run the workflow contract gate with:

```bash
npm --prefix tools/ai run verify:phase3-workflows
./gradlew verifyAiConsumerWorkflows
```

The gate checks the frozen five-workflow denominator, known tool names, evidence ordering, stable
skill paths and frontmatter, safety boundaries, path containment, provider neutrality, and a 16 KiB
entrypoint limit. This source tree is not yet an installed distribution; installation, checksums,
SBOM/license review, uninstallation, and client compatibility evidence belong to the packaging
slice.

## Version lanes

- `current-source` identifies one exact repository revision and is never a synonym for latest.
- `released` identifies exact ViewCompose artifact versions and coordinates.
- The knowledge-bundle, tool-envelope, Design IR, and evaluation schemas use independent integer
  majors. A consumer rejects an unsupported newer major; it never guesses or silently downgrades.

## Evidence levels

The accepted evidence order is `knowledge`, `static`, `compiled`, `rendered`, and `compared`. Each
level includes the evidence from earlier levels. A shallower result cannot claim a deeper status.

## Adding a fixture

1. Put bounded input below `evaluation/fixtures/` or reference an existing canonical compiled
   sample with a repository-relative path.
2. Add one stable case ID to `evaluation/corpus.json`.
3. Attach every metric that uses the case as part of its denominator.
4. State the exact expected outcome and evidence level.
5. Run the Phase 0 gate. Unsupported behavior is an expected result, not an omitted fixture.
