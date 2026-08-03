# Runnable Samples

`samples/` contains small Android applications that back progressive tutorials. These applications
are not published Maven artifacts and must use only public ViewCompose APIs.

| Module | Purpose | Tutorial |
| --- | --- | --- |
| `:samples:counter` | Minimal Activity, state, layout, text, and button path | [Build your first application](../docs/tutorials/getting-started.md) |

Sample rules:

1. keep each application focused on one end-to-end learning outcome;
2. do not depend on the large `:app` demo or its internal scaffolding;
3. compile the application and its test source from `qaQuick`;
4. run behavior assertions from `qaFull` on a device or emulator;
5. update the owning English and Chinese tutorial in the same change that alters visible behavior.
