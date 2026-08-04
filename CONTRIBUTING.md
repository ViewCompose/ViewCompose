# Contributing

Thanks for your interest in contributing to ViewCompose.

## Before You Start

1. Open an issue for large changes before implementation.
2. Keep changes small and focused.
3. Respect existing module boundaries and architecture constraints.

## Local Checks

Run these before opening a PR:

```bash
./gradlew qaQuick
./gradlew qaPreview
```

If your change affects UI behavior on device/emulator, also run:

```bash
./gradlew qaFull
```

Navigation changes have an additional API 33/API 35 P0 certification matrix covering real
process-death restoration and platform Predictive Back. Run the merge gate documented in
[`navigation.md`](docs/guides/navigation.md#stage-6-device-validation-and-p0-merge-gate) before opening the PR.

## Coding Expectations

1. Start from the [documentation index](docs/README.md), then follow the
   [architecture](docs/architecture/overview.md), [workflow](docs/project/workflow.md), and
   [documentation governance](docs/project/documentation-governance.md) relevant to the change.
2. Add/update tests for behavioral changes.
3. Apply the documentation change impact matrix before implementation and review.
4. Treat documentation as implementation. New or changed public/protected APIs must include
   complete KDoc/Javadoc, required compiled samples, the owning
   [module documentation](docs/modules/README.md), and affected cross-module guides in the same PR.
   Follow the
   [Source Documentation and API Comment Standard](docs/project/api-documentation-quality.md).
5. If a change has no documentation impact, explain why in the pull request rather than silently
   omitting documentation.

## Pull Request Guidelines

1. Explain what changed and why.
2. List validation commands and results.
3. Declare the documentation impact and list the pages or source comments updated.
4. Include screenshots/gifs for visual UI changes when helpful.
5. Add one immutable `release/changes/<unique>.json` file when a published artifact's production
   source, publication inputs, or compiled API samples change. Use
   `./gradlew verifyViewComposeReleaseIntent` to verify module coverage before opening the PR.

Test-only, Demo, benchmark, and handwritten documentation changes do not request a Maven release
by default. If an automatically detected artifact change is intentionally release-neutral, record
it under `ignored` with a concrete reason. The complete contract is in
[`publishing.md`](docs/project/publishing.md#per-pull-request-release-intent).

## License

By contributing to this repository, you agree that your contributions are
licensed under the MIT License in this repository.
