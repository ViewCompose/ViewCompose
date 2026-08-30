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

Prepare the repository Preview lane and run the Phase 2 render corpus with:

```bash
./gradlew qaPreview
npm --prefix tools/ai run verify:phase2-render
```

`qaPreview` resolves the fixed counter target's Android runtime, worker-host, and variant-specific
runner classpaths before the separate verifier enters offline Gradle. The
generated-Preview distribution gate applies the same preparation boundary to
`:tools:ai-preview-harness`; neither offline tool invocation may fill a missing dependency cache.

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
sRGB RGBA. Outputs above 4,096 decoded bytes use Paeth filter type 4 and the repository-owned
`fixed-huffman-distance-one-v1` DEFLATE encoder so native zlib versions cannot change large-image
identity; smaller fixtures retain the frozen filter-0/zlib-level-9 encoding. Every result carries
exact content and canonical-result
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

Screenshot Kotlin generation v1 is implemented as the public `generate_screenshot_viewcompose`
tool. It accepts only a
resolved screenshot inference result whose exact result and Design IR fingerprints match the
request and whose mechanical code-generation gate is true. The frozen mapping turns caller-owned
text state into `TextFieldState`, keyboard actions into
`(TextFieldImeAction) -> Boolean`, focus changes into `(Boolean) -> Unit`, and button clicks into
`() -> Unit`; it never accepts callback source, expressions, or guessed resources. Component roles
and visible labels use the real `Button`, `TextField`, and `Text` behavior. Because ViewCompose has
no public traversal-index modifier, the report preserves every reviewed accessibility decision and
requires the resolved ascending traversal to equal generated hierarchy order instead of inventing
an API. The exact wireframe Kotlin golden compiles in the pinned JDK 21/Kotlin UI Foundation lane.
`generate` mode returns deterministic static source and its complete mapping report; `compile` mode
passes only that source and the fixed artifact/capability selection to the hermetic compiler.
`render` mode requires the exact explicit Preview bindings and enters the fixed packaged
released-artifact Layoutlib harness. `compare` mode performs that same render and then reopens only
its verified render tree, maps the exact resolved Design IR, and upgrades evidence to `compared`
only when every supported check passes. `compare-pixels` additionally requires one canonical screenshot
preprocessing request/result pair and runs only after the semantic and structural comparison
passes.

The screenshot generated-Preview implementation identifies `sourceKind: "screenshot"`, uses the dedicated
`GeneratedScreenshotPreview` target and `AI/Screenshot` group, and requires the exact state/event
parameter order reported by the screenshot generator. `TextFieldState` receives only explicit
initial text; `() -> Unit` and `(Boolean) -> Unit` receive fixed no-op callbacks; and
`(TextFieldImeAction) -> Boolean` receives one explicit Boolean return. No binding accepts lambda
source or project code. Missing, reordered, source-mismatched, type-mismatched, or source-bearing
callbacks fail before the fixed Preview harness can run. Successful rendering returns exact
generated-source, wrapper, PNG, render-tree, and aggregate output fingerprints at `rendered`
evidence. Screenshot layout comparison v1 exposes that next evidence boundary: the accepted
resolved Design IR and render tree must match all 27 supported
identity, structure, semantic, and containment/order checks before evidence may become `compared`.
Changed text or structure returns category-specific diagnostics and keeps the accepted render
fingerprint rather than producing an aggregate score. The comparator deliberately excludes the
text-field placeholder, source screenshot regions, exact
source geometry, pixels, style, typography, accessibility traversal, state mutation, and event
behavior because the current evidence cannot compare them.

Screenshot pixel comparison is implemented as the public `compare-pixels` mode. Its eligibility
gate requires a reproducible canonical screenshot reference with no redactions, a full viewport
crop, exact dimensions and device configuration, and a passing semantic/structural comparison from
the same render. It reopens the contained rendered PNG, verifies its byte identity, strictly decodes
both images as bounded non-interlaced 8-bit RGBA, and reports exact pixel ratio, mismatched pixels,
RGBA mean absolute error, RGBA root mean square error, and maximum channel delta separately. The
same zero-tolerance pass emits a separate content-addressed localization result with the global
mismatch bounds, deepest-containing Design IR node ownership, stable depth/ID tie-breaking, and
an explicit count for pixels outside every mapped node. Bounds use left/top inclusive and
right/bottom exclusive viewport coordinates. Localization does not infer a source patch or repair
value from a pixel position. The
checked-in 16×24 inference wireframe is intentionally ineligible because it does not share the
1079×2339 render viewport or density and contains a redaction. No aggregate or perceptual
similarity score is produced.

Bounded screenshot repair has a provider-offline internal orchestrator but is not yet a public tool
mode. It allows at most five reason-coded attempts over typed Design IR patches and evaluates
candidates in fixed `safety` → `compilation` → `render` → `semantics` → `structure` →
`exact-pixels` order. A
candidate cannot be accepted if it regresses a previously passed gate, repeats a candidate or change
fingerprint, or fails to strictly improve the first failing gate. Pixel evidence cannot override an
earlier failure, thresholds and references cannot be relaxed automatically, and exhaustion returns
a structured incomplete result instead of claiming convergence. Arbitrary Kotlin/project edits,
provider calls, network access, perceptual scoring, and accessibility completeness are outside this
contract.

The orchestrator accepts only bounded `replace-field`, `replace-modifier-argument`,
`replace-node-kind`, or `reorder-children` operations. It rejects executable Design IR values,
duplicate operation targets, oversized candidates, invalid or repeated fingerprints, and candidate
evidence that runs a later gate after an earlier failure. The internal patch applier binds each patch
to an exact resolved screenshot Design IR fingerprint, requires existing nodes/fields/modifier
arguments or an exact child permutation, rejects no-op operations, and revalidates the complete
candidate before publishing its canonical fingerprint. The internal candidate evaluator rebinds the
patched IR to a content-addressed generation request, runs the hermetic released-artifact compiler
and Preview as separate gates, categorizes semantic versus structural checks from the accepted render,
and enters exact pixels only after both categories pass. Each accepted candidate is also retained
inside one evaluator session as a bounded, immutable, content-addressed evidence record containing
the exact Design IR, candidate evaluation, gate diagnostic codes, structured layout/pixel
comparisons, and bounded pixel localization. The record excludes generated Kotlin and PNG bytes,
and callers receive clones rather than mutable session state. The internal proposal boundary now
accepts only two complete content-addressed records from the same base resolution and exact pixel
reference. No MCP/CLI caller can activate repair yet.

Screenshot repair proposal v1 implements one deliberately narrow mode: roll one changed
`properties` field back to the exact typed value retained by an integrity-verified, strictly better
baseline candidate. The proposer verifies the evidence and every nested Design IR, layout, pixel,
localization, gate, lineage, and exact-reference binding before inspecting differences. The current
candidate must pass safety, compilation, render, semantics, and structure; fail exact pixels on the
same denominator; localize at least one mismatched pixel to the changed node; and differ from the
baseline in exactly that one non-expression field. Localization never supplies a value. Novel
mismatches, multiple field changes, unlocalized changes, modifier/structure/behavior changes, and
caller-supplied targets return no eligible proposal. The real `Hello` regression produces the
baseline `Welcome` patch, which then passes the typed applier and all six released-artifact gates with
zero mismatched pixels. This remains an internal verifier capability; no CLI/MCP repair mode is
activated.

Screenshot repair authorization v1 is implemented as an internal validator before the proposer can
enter an execution workflow. It requires two separate purpose-bound attestations: an identified
reviewer accepts one exact baseline evidence fingerprint at one immutable Git commit after visual
and semantic review, and an identified approver binds one exact proposal, current evidence, and
change fingerprint to a single application with unattended execution disabled. The same record
also binds the canonical pixel-reference identity and denies credentials, provider calls, network
access, and non-metadata logs. The validator re-runs the bounded proposer over the complete baseline
and current evidence, requires the supplied proposal to reproduce exactly, validates both
attestations and their content addresses, and returns a separate validation result whose policy fixes
`executionAuthorized` to `false`. Review receipt values are opaque content addresses; v1 does not
authenticate a person or receipt, decide whether the baseline is trustworthy, provide revocation,
or execute the patch. Those remain host responsibilities.

Screenshot repair host grant v1 implements the internal trust callback without activating repair
execution. A grant request binds the exact validated authorization, evidence, proposal, change,
pixel reference, candidate Design IR, and immutable baseline source revision. The adapter accepts
only a direct host handle registered in process; its callback is retained privately, so serializing
the handle loses all authority. Files, stdin, CLI or MCP arguments, and network payloads cannot
inject a decision. The adapter validates the decision schema, content address, trust domain,
reviewer and receipt identities, active revocation records, unique host proof receipts, atomic
single-use reservation, and complete repair lineage before returning a grant. A file-backed test
host proves concurrent and cross-instance replay denial through one exclusive durable reservation.
The checked-in grant remains synthetic, credentials remain out of band, and there is no production
host integration, patch executor, public repair mode, or unattended execution.

Screenshot repair execution outcome v1 now has an internal attended executor without public
activation. The host-grant adapter marks only its direct returned object with a private process-local
capability; serialization loses that authority, and consumption is atomic and single use. The
executor accepts exactly that grant, its bound input Design IR, and the exact authorized typed patch,
then applies only an in-memory Design IR change. It sends a fingerprint-only draft to a separately
registered trusted-host terminal callback and exposes an applied outcome only after the returned
receipt passes schema, integrity, lineage, reservation, trust-domain, and effect checks.

Every `applied`, `failed`, `cancelled`, or `indeterminate` outcome consumes attempt one of one and
forbids reuse or retry. Only `applied` with a `committed` effect exposes the result Design IR and
typed-patch output fingerprints; failure and cancellation expose no output. The trusted host may
downgrade an uncertain effect to `indeterminate`. If the callback fails or returns an invalid
receipt, the adapter returns a schema-checked non-authorizing recording failure with unknown effect,
no output, and no retry. Persistent source writes, raw Design IR transport to the host, caller-
supplied outcomes, public mode, credentials, providers, and tool network access remain forbidden.
A production durable terminal store, atomic effect/receipt persistence, authentication, recovery,
and CLI/MCP repair activation remain future host responsibilities.

The package also includes a local file-backed terminal reference store for the internal callback.
It requires an explicit private `0700` directory, writes a complete `0600` temporary record, calls
`fsync`, and publishes it without overwrite through one atomic hard link. Repeating the same draft
returns the exact existing outcome; a conflicting draft for the reservation fails closed and never
replaces it. A new store instance can reconcile the outcome read-only by reservation receipt, with
schema, fingerprint, file type, permissions, trust-domain, and receipt binding revalidated and no
patch re-execution. This reference store provides deterministic local durability, not reviewer or
receipt authentication, multi-host consensus, arbitrary-filesystem portability, or a production
recovery service.

Applied-result handoff v1 is implemented internally without public activation. It retains only the
exact process-local Design IR produced by a successful attended patch when the original trusted
host also supplies direct read-only reconciliation. Before delivery it reopens and exactly
revalidates the durable terminal record, then returns one immutable content-addressed receipt beside
the same frozen Design IR object. Successful delivery clears the retained result reference and is
single use under concurrency. Serialized outcomes or host handles, non-applied states, receipt or
result drift, non-durable hosts, duplicate delivery, result persistence, application source writes,
and public CLI/MCP repair remain rejected.

Run the local MCP server and its protocol/parity gate with:

```bash
npm --silent --prefix tools/ai run mcp
npm --prefix tools/ai run verify:phase3-mcp
npm --prefix tools/ai run verify:phase5-screenshot
npm --prefix tools/ai run verify:phase5-screenshot-inference
npm --prefix tools/ai run verify:phase5-screenshot-resolution
npm --prefix tools/ai run verify:phase5-screenshot-generation
npm --prefix tools/ai run verify:phase5-screenshot-render
npm --prefix tools/ai run verify:phase5-screenshot-comparison
npm --prefix tools/ai run verify:phase5-screenshot-pixel-comparison
npm --prefix tools/ai run verify:phase5-screenshot-repair
npm --prefix tools/ai run verify:phase5-screenshot-repair-candidate
npm --prefix tools/ai run verify:phase5-screenshot-repair-proposer
npm --prefix tools/ai run verify:phase5-screenshot-repair-authorization
npm --prefix tools/ai run verify:phase5-screenshot-repair-host-grant
npm --prefix tools/ai run verify:phase5-screenshot-repair-execution-outcome
npm --prefix tools/ai run verify:phase5-screenshot-repair-applied-result-handoff
./gradlew verifyAiMcp
./gradlew verifyAiScreenshotPreprocessing
./gradlew verifyAiScreenshotInference
./gradlew verifyAiScreenshotResolution
./gradlew verifyAiScreenshotGeneration
./gradlew verifyAiScreenshotRender
./gradlew verifyAiScreenshotComparison
./gradlew verifyAiScreenshotPixelComparison
./gradlew verifyAiScreenshotRepair
./gradlew verifyAiScreenshotRepairCandidate
./gradlew verifyAiScreenshotRepairProposer
./gradlew verifyAiScreenshotRepairAuthorization
./gradlew verifyAiScreenshotRepairHostGrant
./gradlew verifyAiScreenshotRepairExecutionOutcome
./gradlew verifyAiScreenshotRepairAppliedResultHandoff
```

The preferred protocol follows the
[MCP `2026-07-28` specification](https://modelcontextprotocol.io/specification/2026-07-28): clients
may call `server/discover` and every request must carry `io.modelcontextprotocol/protocolVersion` and
`io.modelcontextprotocol/clientCapabilities` in `params._meta`. For clients that have not yet
migrated, the same process accepts only the frozen `2025-11-25` `initialize`/`initialized`
lifecycle; it never silently downgrades either era. `tools/list` returns thirteen tools in stable
order: the four retrieval tools, `validate_code`, `render_preview`, `diagnose_layout`, and
`analyze_project`, followed by `convert_xml_to_viewcompose`, `prepare_screenshot`, and
`validate_screenshot_inference`, then `resolve_screenshot_inference` and
`generate_screenshot_viewcompose`.

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

## Common AI agent onboarding

The package exposes one client-neutral lifecycle command for Codex, Claude Code, and Cursor. The
primary consumer path is one transactional project operation:

```bash
viewcompose-agent init --client <codex|claude-code|cursor> \
  --project-root <physical-absolute-consumer-project-root>
```

`init` merges only the `viewcompose` MCP entry into `.codex/config.toml`, `.mcp.json`, or
`.cursor/mcp.json` and copies the six exact canonical `SKILL.md` files. Codex and Cursor use
`.agents/skills`; Claude Code uses `.claude/skills`. All configuration and Skill surfaces are
preflighted before writes; atomic configuration replacement and Skill rollback prevent a partial
install. Existing unrelated JSON/TOML content is preserved. Exact reinitialization is idempotent,
while invalid JSON, conflicting MCP definitions or Skill bytes, relative roots, symbolic-link
boundaries, unknown clients, and implicit home-directory selection fail closed.

Inspect the installed state and its honest capability boundary with:

```bash
viewcompose-agent doctor --client <codex|claude-code|cursor> \
  --project-root <physical-absolute-consumer-project-root>
```

The default result is `project-bound-ready` when the exact configuration and Skills are present,
JDK 17 or 21 is available, and Android SDK platform 36 is installed. Knowledge/generation and
compilation/Preview/layout readiness are reported separately. `VIEWCOMPOSE_PROJECT_ROOT` is always
bound to the explicit physical consumer root; an optional `VIEWCOMPOSE_SOURCE_ROOT` remains only for
contributor compatibility. The installed package does not infer either root from parent directories.

`uninstall` removes only an exact package-owned MCP definition and exact canonical Skill bytes. It
preserves unrelated configuration and fails closed after user edits. `config` and `install-skills`
remain lower-level compatibility commands; they are not the public quick-start path. None of these
commands authenticates or launches a proprietary client or opens a network connection.

Run the dedicated gate with:

```bash
npm --prefix tools/ai run verify:phase3-agent-clients
./gradlew verifyAiAgentClients
```

## Release distribution

Build the dependency-free npm tarball and its deterministic sidecars with:

```bash
npm --prefix tools/ai run package:distribution
```

The command writes an ignored `tools/ai/build/distribution/` directory containing the `.tgz`, an
exact per-file `manifest.json`, and `SHA256SUMS`. The package contains the thirteen-tool CLI/MCP core,
the `viewcompose-agent` onboarding command, six consumer skills, the immutable Knowledge Bundle, the
consumer execution contract, a content-addressed Gradle harness and wrapper, an SPDX 2.3 package
record, the MIT license, and a reviewed empty runtime-dependency license inventory. The license
inventory records the distributed Gradle Wrapper as an Apache-2.0 development tool. The package
contains no `node_modules`, Android SDK, JDK, provider adapter, network listener, or model.

Verify reproducibility, inventory, offline lifecycle, installed CLI compilation, and both supported
MCP protocol versions with:

```bash
JAVA_HOME=<jdk-21-home> npm --prefix tools/ai run verify:phase3-distribution
./gradlew verifyAiDistribution
./gradlew verifyAiToolingRelease
```

The public consumer installs the pinned GitHub asset directly, without a checkout or build:

```bash
npm install --global --ignore-scripts \
  https://github.com/ViewCompose/ViewCompose/releases/download/ai-tooling-v0.2.0/viewcompose-ai-tooling-0.2.0.tgz
```

Install and uninstall one exact local artifact in an isolated prefix without contacting a registry:

```bash
npm install --global --prefix <install-prefix> --offline --ignore-scripts \
  tools/ai/build/distribution/viewcompose-ai-tooling-0.2.0.tgz
<install-prefix>/bin/viewcompose-mcp
npm uninstall --global --prefix <install-prefix> --offline --ignore-scripts \
  @viewcompose/ai-tooling
```

All knowledge, static, analysis, XML, and screenshot-generation modes need no ViewCompose source
checkout. Compile-mode `validate_code` and the compile/render/compare modes owned by XML and
screenshot generation use the packaged Gradle 9.3.1 harness, exact released ViewCompose Maven
coordinates, AGP 9.1.1, Kotlin 2.2.10, Android 36, and JVM target 11. JDK 17 or 21 and SDK platform 36
are host prerequisites. The first request may resolve the pinned Gradle distribution and Maven
dependencies; subsequent requests use the integrity-checked OS user cache. The consumer root is
read-only and its wrapper, settings, plugins, tasks, and build scripts are never executed. Direct
`render_preview` and `diagnose_layout` still require a separately allowlisted fixed target; generated
XML and screenshot results expose their own Preview and layout-diagnosis evidence. The package never
searches arbitrary parent directories or silently upgrades static evidence.

Release `0.2.0` still packages `current-source` knowledge and does not inspect a consumer project's
independently versioned ViewCompose dependencies. Its fixed Harness coordinates prove only the
compile/render lane they execute; they do not convert the current-source Knowledge Bundle into a
released project profile. Never infer compatibility for that already-published package.

`framework-project-profile.mjs` is the dependency-free read-only detector for that boundary. It
accepts exact Gradle coordinate literals, used default version-catalog libraries/bundles, and
dependency lock records; distinguishes a dependency-free project from imports with missing
identity; and returns `resolved`, `empty`, `unresolved`, or `conflict` without executing consumer
Gradle.

The next package now generates a consumer-selectable `released` Knowledge Pack from immutable
per-Artifact publication history. Its content-addressed framework profile records 38 published
coordinates; 30 own machine-readable API knowledge. `viewcompose-agent init` detects the consumer
Artifact subset before any project write, selects only an exact released profile, and stores
`VIEWCOMPOSE_FRAMEWORK_PROFILE` in the MCP entry. Knowledge retrieval, static validation, project
analysis, generated Kotlin compilation, and generated Preview requests then load that same bundle.
An empty project selects the index's newest stable profile; unresolved, conflicting, and unsupported
dependencies fail before configuration or Skill bytes change. Source-bound contributor profiles
select `current-source` explicitly. Automatic Release discovery and transactional package migration
remain disabled until their candidate-integrity and rollback gates pass.

Tags matching `ai-tooling-v*` enter `.github/workflows/ai-tooling-release.yml`. The workflow validates
the tag against the frozen release contract, repeats the complete distribution gate from a clean
checkout, refuses an existing Release, creates GitHub build-provenance attestations for the exact
tarball, manifest, and checksum list, and then publishes those three assets. It never publishes a
mutable `latest` selector and requires no npm-registry account. The package layout follows npm's
tarball installation contract, and its SBOM uses the
[SPDX 2.3 specification](https://spdx.github.io/spdx-spec/v2.3/).

### Release-gate acceptance evidence

On 2026-08-30, the first cold `prepareAiPreviewLane` run changed from 0/1 successful executions
(missing producer JAR) to 1/1 after declaring the exact transformed classpath inputs: 184 tasks,
170 executed, 14 up-to-date, and 23 seconds. That is a `+100` percentage-point normalized cold-start
change and is **improved**. The subsequent complete release gate passed 186 tasks (3 executed and
183 up-to-date) in 3 minutes 28 seconds. It reproduced 2/2 packages, completed 1/1 isolated offline
install/uninstall lifecycle, verified 3/3 installed Agent profiles and 18/18 Skill copies, exercised
2/2 MCP protocol versions, and retained compile, Preview, layout, screenshot, and XML evidence.

The first result measures a local macOS cold producer graph; the complete gate reused those newly
built Android artifacts, so neither number predicts a hosted Linux runner's uncached duration.
Conclusion: **improved** cold release readiness with no material runtime behavior change. Next
action: require the tag-triggered Linux workflow and verify the published Release attestations.

On 2026-08-31, Node 25.6.0 reproduced the first released profile twice with 38 published Artifact
identities, 30 knowledge-owning Artifacts, 70 capabilities, 531 symbols, 187 samples, and 10 rules.
The profile ID is
`895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064`; its Knowledge Bundle
fingerprint is
`9ee4560b30f2d26378314d5b8c8acf20343662f5a8c1d5bfc0442944c4d09660`. The installed-distribution
gate then passed 2/2 reproducible builds, 3/3 exact version-bound Agent profiles, 18/18 Skill copies,
2/2 MCP protocol versions, and the existing compile, generated Preview, XML, layout, screenshot,
and exact-pixel denominators. Relative to the unbound `current-source` package, this is **improved**
framework-compatibility evidence with **no material Android runtime behavior change**. The pack uses
one release anchor only after proving that every included Artifact's `src/main` Git tree is identical
to its own recorded release revision; it does not yet cover every historical version vector or
custom dependency-resolution scheme. The next action is checksummed matching-Release discovery and
side-by-side transactional upgrade.

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
