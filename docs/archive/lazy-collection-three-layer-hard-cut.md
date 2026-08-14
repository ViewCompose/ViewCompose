# Lazy Collection Three-Layer Hard Cut Plan

## Status

Completed on 2026-08-14. The architecture contract is accepted in
[ADR-0012](../architecture/decisions/0012-lazy-collection-logical-and-physical-ownership.md),
implemented throughout Lazy List, Grid, Pager, and TabRow, and validated by unit, connected-device,
and macrobenchmark gates.

## Maven release changesets

- `release/changes/20260814-lazy-collection-three-layer-ownership.json`

## Objective

Separate logical snapshots, key-owned sessions, and physical native presentation throughout lazy
lists and pagers. Preserve native reuse without transferring logical identity, remove callback
identity invalidation, and move TabRow to the ordinary eager keyed-child path.

## Contract checklist

| Contract | Completed result |
| --- | --- |
| Same key, same revisions | No item updater, render, or native patch; newer parent submission is still acknowledged |
| Same key, changed revision | Only that key-owned session recomposes and patches |
| New key, same content type | A new logical session may adopt a reset native tree |
| New content type | Old presentation is released and the tree is rebuilt |
| Environment change | Framework-captured `LocalSnapshot` changes `environmentRevision` |
| Captured ordinary value | Public contract requires State or explicit `contentRevision` |
| AndroidView cross-key reuse | Reset occurs after logical disposal and before bind; missing reset disables reuse |
| Mounted-tree eviction | Renderer-owned bounded cache calls release exactly once and continues cleanup after failures |
| TabRow | Eager keyed parent children; selection invalidates only the previous and next selected tabs |
| Pager | Delayed key-owned pages use native offscreen residency and bounded physical-tree reuse |

## Completed execution

| Phase | Framework work | Evidence | Status |
| --- | --- | --- | --- |
| 1 | Publish ADR, migration contract, and module ownership changes | Documentation structure and translation gates | Complete |
| 2 | Introduce explicit content and environment revisions; remove reference fallbacks | Diff, duplicate submission, State, and environment tests | Complete |
| 3 | Separate key sessions from holder-owned mounted trees; add bounded observable reuse cache | State/effect isolation, eviction, targeted-bind, and leak tests | Complete |
| 4 | Enforce AndroidView reset/release reuse eligibility | Reset ordering, non-resettable fallback, and exactly-once release tests | Complete |
| 5 | Move TabRow to eager keyed children and align Pager with native offscreen policy | Targeted tab invalidation and page residency tests | Complete |
| 6 | Bound speculative preparation and remove remaining list hot-path scans/publication churn | Cost-policy, sticky-header, revision confirmation, and long-fling tests | Complete |
| 7 | Split oversized Diagnostics fixtures and keep benchmark routes stable | Exact-route connected tests and macrobenchmark | Complete |
| 8 | Run quality gates and archive the completed plan | `qaQuick`, docs, translation, device, and benchmark gates | Complete |

## Durable rules

1. No compatibility alias preserves the former `contentToken`; every collection call site uses
   `contentRevision`.
2. Callback reference identity never enters item equality, submission detection, or binding.
3. Logical-session disposal precedes physical-tree reset and new-key binding.
4. Failed composition or rebind never advances the installed semantic revision and remains
   retryable without exposing candidate effects.
5. Mounted trees never enter an opaque system-owned pool. RecyclerView pools only empty Holder
   shells; the framework owns observable physical-tree eviction.
6. Prefetch remains speculative and effect-free. Unknown or over-budget content types are staged
   without synchronous native preparation.
7. TabRow remains an eager parent-tree primitive and must not regress to Lazy Item Session
   ownership.

## Completion evidence

- `./gradlew qaQuick --console=plain --quiet` passed after the final revision-confirmation fix.
- Three connected Demo state, horizontal Pager, and vertical Pager revision tests passed on
  `SM-G991B`.
- `DemoInteractionBenchmark.diagnosticsTabSwitchThenImmediateLongFling` passed five iterations on
  `SM-G991B`. Every iteration switched among Renderer, Gaps, and Theme, immediately flung to the
  bottom, and returned to Chapter Pages.
- The benchmark recorded median 3,002 frames. CPU frame time was 2.84 ms at P50, 6.02 ms at P90,
  7.17 ms at P95, and 15.73 ms at P99; frame overrun was -2.55 ms at P50, 1.41 ms at P90, 2.04 ms
  at P95, and 12.81 ms at P99.
- Deterministic controller tests verify stable snapshots perform zero updater/render work while
  acknowledging the newer parent submission; rolled-back frames alone remain retryable.
- `verifyDocumentationStructure`, translation verification, and `git diff --check` passed during
  finalization.
