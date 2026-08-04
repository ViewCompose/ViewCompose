# Samples

`samples/` contains small Android applications and compile-only comparison modules that back
capability tutorials and migration guides. These modules are not published Maven artifacts and
must use only public ViewCompose APIs.

| Module | Purpose | Documentation |
| --- | --- | --- |
| `:samples:counter` | Minimal Activity, state, layout, text, and button path | [Build your first application](../docs/tutorials/getting-started.md) |
| `:samples:tutorials` | One self-contained Activity file per capability, resolved from published Maven artifacts | [Capability tutorials](../docs/tutorials/README.md) |
| `:samples:compose-migration` | Compiled Compose/ViewCompose pairs for state, layout and locals, Activity hosting and Android View interop, and Navigation 2 | [Migrate from Jetpack Compose](../docs/migration/README.md) |

Sample rules:

1. keep each application or comparison module focused on one learning outcome;
2. do not depend on the large `:app` demo or its internal scaffolding;
3. resolve public tutorial applications through published Maven coordinates rather than local
   ViewCompose project dependencies;
4. compile every sample from `qaQuick`;
5. run applicable application behavior assertions from `qaFull` on a device or emulator;
6. keep tutorial and migration Markdown snippets identical to their marked source regions through
   `verifyTutorialSamples` and `verifyMigrationPairedSamples`;
7. update the owning canonical English page and required Chinese mirror in the same change that
   alters documented behavior.
