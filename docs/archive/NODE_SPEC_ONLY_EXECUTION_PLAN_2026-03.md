# NodeSpec-Only Migration Execution Plan (2026-03)

## 1. Baseline

Current codebase still has dual-path model (`NodeSpec + Props`):

1. `VNode` contains both `props` and nullable `spec`.
2. `UiTreeBuilder.emit/emitResolved` accept `props`.
3. Renderer binders still keep `node.props[...]` fallback reads.
4. `AnchorTarget` writes anchor metadata through `Props`.
5. Tests and docs still include `Props/TypedPropKeys` assumptions.

## 2. Locked Scope

This execution is hard-cut and breaking:

1. Remove `Props` system entirely.
2. Enforce non-null `VNode.spec` for all nodes.
3. Migrate all DSL/render paths to spec + modifier metadata only.
4. No compatibility aliases, no deprecated bridge layer.

Out of scope:

1. No public compatibility adapter for old imports/usages.
2. No rollback path in code (rollback only via git revert if needed).

## 3. Done Criteria

All of the following must be true:

1. Main source code has zero usage of `Props`, `TypedPropKeys`, `PropKeys`, `node.props`.
2. `VNode` has only non-null `spec` as semantic input.
3. Overlay anchor flow works through modifier metadata, not props.
4. `qaQuick` passes at each step; required `qaFull` checkpoints are executed when device is available.
5. Docs updated to NodeSpec-only policy and plan archived after completion.

## 4. Step Checklist

- [x] Step 1: add execution plan doc and first commit.
- [x] Step 2: hard-cut core model (`VNode`, `UiTreeBuilder`, remove props files, add empty spec).
- [x] Step 3: migrate DSL (`Spacer`, `Surface`, `AnchorTarget`) to spec/modifier-only.
- [x] Step 4: remove renderer fallback props reads and normalize to spec-only.
- [x] Step 5: move overlay anchor metadata application to resolved modifier path.
- [x] Step 6: migrate tests + add regression guard.
- [x] Step 7: update docs, archive this plan, close statuses.

## 5. Commit Log

| Step | Commit | Message | Notes |
|---|---|---|---|
| 1 | `a1bcc8e` | `docs: add node-spec-only migration execution plan` | plan bootstrap |
| 2 | `f085313` | `refactor: remove VNode props and require non-null NodeSpec` | breaking |
| 3 | `d4adb2f` | `refactor: migrate layout DSL from props to spec and modifier metadata` | includes anchor modifier API |
| 4 | `93b4788` | `refactor: make renderer spec-only and remove props fallback paths` | binder + pipeline cleanup |
| 5 | `17755e0` | `refactor: move overlay anchor metadata from props to modifier` | presenter path unchanged |
| 6a | `41d53ef` | `test: migrate unit tests to spec-only vnode model` | direct test migrations |
| 6b | `c0f577b` | `test: add guardrails to prevent props regression` | static guard |
| 7a | `36cfc02` | `docs: codify node-spec-only architecture boundary` | docs update |
| 7b | `30aeffc` | `docs: close and archive node-spec-only migration plan` | archive + index |

## 6. Blocker Log

Use this format for blockers:

1. Time:
2. Current step:
3. Current branch:
4. Last completed commit:
5. Blocker:
6. Verified facts:
7. Resume command:

## 7. QA Checkpoints

1. Every step: `./gradlew qaQuick`
2. Milestones:
   - after Step 4: `./gradlew qaFull`
   - final close: `./gradlew qaFull`
3. If device unavailable:
   - record blocker in this file
   - continue non-device steps
   - re-run blocked `qaFull` before closure

Final `qaFull` status:

1. `2026-03-08` 执行 `./gradlew qaFull` 时失败于 `:app:connectedDebugAndroidTest`，原因：`No online devices found`（设备处于 `OFFLINE`）。
2. 阻塞上下文已记录到 [NODE_SPEC_ONLY_BLOCKER_CONTEXT_2026-03.md](NODE_SPEC_ONLY_BLOCKER_CONTEXT_2026-03.md)。
3. `2026-03-09` 设备恢复后复跑 `./gradlew qaFull` 通过（connected tests `32/32`），该阻塞已解除。
