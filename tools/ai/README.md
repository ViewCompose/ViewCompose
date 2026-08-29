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
4. Later directories may implement the knowledge generator, validator, CLI, MCP, Design IR
   conversion, and visual adapters only against these contracts.

Run the Phase 0 gate with:

```bash
npm --prefix tools/ai run verify
```

The root `qaQuick` lifecycle also runs `verifyAiToolingContracts`. No command in this directory may
execute an inspected project's build scripts, load a model provider, read credentials, or write
outside a tool-owned output directory.

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
