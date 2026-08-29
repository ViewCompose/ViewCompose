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
`analyze_project`, plus `convert_xml_to_viewcompose` (`generate`, `compile`, or `render`). The
request must name the exact `framework.versionLane` and
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

Run the bounded XML migration gates with:

```bash
npm --prefix tools/ai run verify:phase4-design-ir
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase4-project-context
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase4-layout-dependencies
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase4-xml
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase4-generated-preview
./gradlew verifyAiDesignIr verifyAiXmlProjectContext verifyAiXmlLayoutDependencies \
  verifyAiXmlMigration verifyAiGeneratedPreview
```

`convert_xml_to_viewcompose` accepts only the compatible frozen Android XML layout v1 and v2
subsets documented by `evaluation/fixtures/xml/subset-contract.json` and
`evaluation/fixtures/xml/subset-v2-contract.json`. Its arguments select exactly one input form:

- Source form supplies `source`, optional logical `path`, and `mode`; `render` also requires
  `previewBindings`.
- Project form supplies an absolute `projectRoot`, project-relative `layoutPath`, ordered
  `resourceRoots`, optional ordered `sourceRoots`, and `mode`; `render` also requires
  `previewBindings`.

Project form implements only the additional subset frozen by
`evaluation/fixtures/xml/project-context-contract.json`. It resolves default `string` and `dimen`
definitions plus explicit style-parent chains from the named roots and returns a bounded lexical
Kotlin/Java call-site inventory. It never chooses a build variant, runs inspected-project Gradle
logic, follows symbolic links, or claims call-site completeness. Qualified resources are inventory
evidence only; themes, aliases, implicit style parents, resource conflicts, formatted resources,
and unsafe or missing defaults fail closed. The returned `projectContext` and migration report use
project-relative paths and fingerprints and contain no raw application source.

Project form also resolves the explicit-root layout graph frozen by
`evaluation/fixtures/xml/layout-dependency-contract.json`. Unqualified `@layout/name` includes use
the first declared default `layout/` root. Ordinary included roots remain nodes, while an included
`merge` root contributes its ordered children at the include position. Every graph edge and IR node
retains its original project-relative file and line. Source-only includes, standalone merge roots,
missing layouts, cycles, include overrides, symbolic links, and dependency ceilings fail closed;
the tool never performs AGP variant or resource merging.

Layout v2 adds `FrameLayout` as ordered-overlay `Box`, `ImageView` as `Image`, and
`android:visibility`. Drawable references become caller-owned `ImageSource` parameters; the tool
does not invent an `R` class or resource ID. Image descriptions must be a non-empty literal, a
string resource, or explicit `@null` decoration. Omission returns
`VC-AI-XML-ACCESSIBILITY-REQUIRED` and no Kotlin. `fitCenter`, `centerCrop`, `fitXY`, and
`centerInside` map to the corresponding ViewCompose image scales. Other image attributes and scale
types remain unsupported.

`generate` parses the selected source into typed Design IR and returns deterministic ViewCompose
Kotlin plus resource/state bindings and a mandatory call-site review checklist. It never invokes
Gradle. `compile` performs the same steps and then enters the fixed hermetic compiler; callers must
select this deeper mode explicitly. `render` accepts only Kotlin emitted by that same conversion,
requires one explicit ordered value for every generator-reported parameter, creates a deterministic
zero-argument Preview wrapper, and uses only `:tools:ai-preview-harness`. The request, generated
Kotlin, wrapper, framework bundle, configuration, compiler lane, and render lane are
content-addressed. The harness is offline and cannot select or execute the inspected project's
build, task, dependencies, resources, scripts, or output paths.

Generated Preview v1 supports exact `String` values, fresh `TextFieldState` values with explicit
initial text, and exact embedded PNG bytes for `ImageSource`. An embedded image provides canonical
base64, decoded byte count, SHA-256, and dimensions; the adapter validates bounded PNG chunks and
CRC values, then stages one immutable tool-owned `R.drawable` resource by full hash. It accepts no
asset path, URL, URI, project resource ID, XML/vector drawable, alternate media type, or network
load. Missing, extra, duplicate, reordered, source-mismatched, type-mismatched, or asset-invalid
bindings fail before Gradle. A successful render returns the request, generated-source, wrapper,
asset, PNG, render-tree, render-output, and layout-comparison fingerprints at `compared` evidence
after every required comparison check passes. Custom Views, Data Binding, unknown
attributes/elements/namespaces, unsupported values, `DOCTYPE`/entities, malformed XML, duplicate
IDs, and limit violations return localized diagnostics and no Kotlin.
String resources remain explicit caller `String` bindings, drawable resources remain caller
`ImageSource` bindings, and `TextFieldState` remains caller-owned; the tool does not invent
listeners or rewrite ViewBinding/application call sites.

Generated layout comparison v1 accepts only the exact Design IR and content-addressed render tree
produced inside the same conversion request. It reopens and hashes the render tree, requires unique
normalized node keys, and checks observable structure and semantics plus zero-tolerance integer
geometry for declared dp, match-parent, padding, containment, and column order in the single frozen
Preview configuration. It permits only the current one-child `TextField` wrapper. A mismatch
returns reason-coded findings and retains only `rendered` evidence. Placeholder rendering,
state/event behavior, traversal, style, typography, pixels, touch targets, and other device
configurations remain explicit non-claims.

Screenshot preprocessing v1 is implemented as the public `prepare_screenshot` tool. It accepts only one
embedded, canonical-base64 PNG with declared byte count, SHA-256, dimensions, density, font scale,
locale, layout direction, color space, alpha mode, orientation, system-bar insets, and source-pixel
crop. It accepts no path, URL, URI, credential, network transfer, or provider execution. A completed
privacy review is mandatory; provider transfer and persistence are false, logs are metadata-only,
and sensitive content is removed only through explicit caller rectangles in cropped-output pixel
coordinates. The deterministic output strips ancillary PNG metadata, keeps 8-bit straight-alpha
sRGB RGBA, uses filter type 0 and zlib level 9, and carries exact content and canonical-result
fingerprints. The adapter reproduces the checked-in crop-and-redaction golden, decodes PNG filter
types 0 through 4, and fails closed on changed identity/CRC, unsupported PNG format, invalid bounds,
external references, provider transfer, limits, and cancellation. It does not infer UI, call a
model, or convert the image to Design IR.

Screenshot-to-Design-IR inference v1 keeps generation provider-neutral and exposes no model or
provider adapter. The public `validate_screenshot_inference` tool is an offline import boundary for
an externally produced raw result. It accepts the original `prepare_screenshot` request, a compact
inference declaration, and the raw result; the adapter reruns deterministic preprocessing and
reconstructs the exact inference request, so callers do not duplicate the preprocessed PNG in the
tool input. It then checks every schema, fingerprint, lineage link, approved-input identity,
node/evidence region, confidence-specific question, forbidden default, unsupported semantic, and
authorization record before importing Design IR. A caller path, URL, URI, credential, changed
preprocessing identity, invented behavior or expression, or unreviewed provider transfer fails
closed. Unknown text, state, resources, behavior, and accessibility remain placeholder bindings,
blocked unsupported semantics, and explicit questions; code generation stays disabled while any
blocking question exists. Provider-produced results additionally require an immutable provider and
model identity plus an explicit consent receipt bound to the exact preprocessed input and approved
purpose. The validator selects or executes no provider, opens no network connection, and persists
neither the screenshot nor the raw inference result.

Screenshot inference resolution v1 is implemented as the public `resolve_screenshot_inference`
tool. It accepts one unchanged `validated-screenshot-inference` import and typed human answers bound
to its exact validation identity, question ID, node, pixel region, required action, reviewer, and
review receipt. Content decisions may set only literal, input-profile, or caller-owned state values;
behavior decisions name caller callback bindings without callback source. Accessibility review
records role, label source, traversal position, and decorative status for every node, and the
adapter persists all of those decisions in Design IR. Expressions, guessed resources, unknown or
moved questions, changed lineage, incomplete coverage, incompatible component fields/events, and
partial accessibility review fail closed. The resolved golden has no remaining questions,
unsupported semantics, or placeholder bindings, so it becomes eligible for a future
screenshot-specific generator. That eligibility is not a compilation, render, or visual-parity
claim. The adapter calls no provider, opens no network connection, and executes no answer content.

Screenshot Kotlin generation v1 is contract-frozen but is not yet a public tool. It accepts only a
resolved screenshot inference result whose exact result and Design IR fingerprints match the
request and whose mechanical code-generation gate is true. The frozen mapping turns caller-owned
text state into `TextFieldState`, keyboard actions into
`(TextFieldImeAction) -> Boolean`, focus changes into `(Boolean) -> Unit`, and button clicks into
`() -> Unit`; it never accepts callback source, expressions, or guessed resources. Component roles
and visible labels use the real `Button`, `TextField`, and `Text` behavior. Because ViewCompose has
no public traversal-index modifier, the report preserves every reviewed accessibility decision and
requires the resolved ascending traversal to equal generated hierarchy order instead of inventing
an API. The exact wireframe Kotlin golden compiles in the pinned JDK 21/Kotlin UI Foundation lane.
Rendering and visual parity remain explicit non-claims until a later gate.

Run the local MCP server and its protocol/parity gate with:

```bash
npm --silent --prefix tools/ai run mcp
npm --prefix tools/ai run verify:phase3-mcp
npm --prefix tools/ai run verify:phase5-screenshot
npm --prefix tools/ai run verify:phase5-screenshot-inference
npm --prefix tools/ai run verify:phase5-screenshot-resolution
npm --prefix tools/ai run verify:phase5-screenshot-generation
./gradlew verifyAiMcp
./gradlew verifyAiScreenshotPreprocessing
./gradlew verifyAiScreenshotInference
./gradlew verifyAiScreenshotResolution
./gradlew verifyAiScreenshotGeneration
```

The preferred protocol follows the
[MCP `2026-07-28` specification](https://modelcontextprotocol.io/specification/2026-07-28): clients
may call `server/discover` and every request must carry `io.modelcontextprotocol/protocolVersion` and
`io.modelcontextprotocol/clientCapabilities` in `params._meta`. For clients that have not yet
migrated, the same process accepts only the frozen `2025-11-25` `initialize`/`initialized`
lifecycle; it never silently downgrades either era. `tools/list` returns twelve tools in stable
order: the four retrieval tools, `validate_code`, `render_preview`, `diagnose_layout`, and
`analyze_project`, followed by `convert_xml_to_viewcompose`, `prepare_screenshot`, and
`validate_screenshot_inference`, then `resolve_screenshot_inference`.

Every `tools/call` creates the same immutable request envelope used by the CLI. MCP returns that
provider-neutral result unchanged as `structuredContent` and as serialized text for compatibility.
Tool argument failures remain actionable tool errors, while malformed requests and unknown tools
remain JSON-RPC errors. Progress is emitted only for a caller-provided token. Cancellation aborts
the underlying bounded execution and suppresses every later response or progress message. The
stdio boundary accepts at most 4 MiB per message and four concurrent calls, writes only MCP JSON to
stdout, uses content-free stderr diagnostics, and opens no socket. The source-tree commands above
remain the contributor entrypoints; the versioned local distribution below is the consumer
boundary.

## Consumer Agent workflows

Six client-neutral consumer skills live below `skills/`:

- `viewcompose-api-reference` retrieves exact APIs and compiled samples without writing files.
- `viewcompose-create-screen` retrieves before implementation and requires compile-backed delivery.
- `viewcompose-convert-xml` prefers explicit project evidence when a layout lives in the scoped
  project, preserves standalone pasted-source migration, requires compile-backed integration, and
  uses generated render mode only with exact explicit bindings.
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

The gate checks the frozen six-workflow denominator, known tool names, evidence ordering, stable
skill paths and frontmatter, safety boundaries, path containment, provider neutrality, and a 16 KiB
entrypoint limit.

## Local distribution

Build the dependency-free npm tarball and its deterministic sidecars with:

```bash
npm --prefix tools/ai run package:distribution
```

The command writes an ignored `tools/ai/build/distribution/` directory containing the `.tgz`, an
exact per-file `manifest.json`, and `SHA256SUMS`. The package contains the nine-tool CLI/MCP core,
six consumer skills, the immutable Knowledge Bundle, an SPDX 2.3 package record, the MIT license,
and a reviewed empty runtime-dependency license inventory. It intentionally contains no
`node_modules`, Gradle project, Android SDK, JDK, provider adapter, network listener, or model.

Verify reproducibility, inventory, offline lifecycle, installed CLI compilation, and both supported
MCP protocol versions with:

```bash
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase3-distribution
./gradlew verifyAiDistribution
```

Install and uninstall one exact local artifact in an isolated prefix without contacting a registry:

```bash
npm install --global --prefix <install-prefix> --offline --ignore-scripts \
  tools/ai/build/distribution/viewcompose-ai-tooling-0.1.0.tgz
<install-prefix>/bin/viewcompose-mcp
npm uninstall --global --prefix <install-prefix> --offline --ignore-scripts \
  @viewcompose/ai-tooling
```

`get_api_reference`, `get_component_reference`, `search_component`, `get_sample`, static
`validate_code`, `analyze_project`, and both source and explicit-project generate modes of
`convert_xml_to_viewcompose` need no ViewCompose source checkout. Project generation reads only the
caller-supplied bounded project roots. Compile-mode `validate_code`, compile-mode
and render-mode `convert_xml_to_viewcompose`, `render_preview`, and `diagnose_layout` remain
source-bound: set
`VIEWCOMPOSE_SOURCE_ROOT` to the absolute root of the matching ViewCompose checkout and provide the
pinned JDK/Android/Gradle offline lane. The adapter requires the exact Knowledge Bundle source
revision to be present in that checkout's Git ancestry and rejects missing wrapper/settings files,
symbolic-link replacements, and mismatched history before Gradle. The package never searches
arbitrary parent directories or silently converts static evidence into compiled/rendered evidence.

The checked-in package is a local artifact rather than an npm-registry publication. `SHA256SUMS` is
an unsigned integrity record; artifact signing and public-registry release policy remain Phase 6
operations work. The package layout follows npm's local tarball installation contract, and its SBOM
uses the [SPDX 2.3 specification](https://spdx.github.io/spdx-spec/v2.3/).

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
