# Active Execution Plans

This directory contains multi-step work that is currently active and must survive across sessions.

## Active plans

- [General image loading pipeline and Glide adapter](./image-loading-pipeline-generalization.md) —
  generalize local/remote/custom image sources, add renderer-owned request disposal, migrate Coil,
  and add an optional Glide integration.

Completed tutorial, language-consistency, migration-sample, hosted-documentation, and
version-retention plans are retained in the
[archive](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md).

Before adding a plan, read [Documentation governance](../documentation-governance.md). A plan must
have a clear completion condition, be updated during implementation, and move to
[`docs/archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)
when complete.
