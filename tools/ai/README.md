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
does not return the oversized inventory. The current analyzer returns an inventory and framework
signals only; dependency resolution, migration findings, and code mutation remain unsupported.

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
