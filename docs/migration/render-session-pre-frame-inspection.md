---
schema_version: 2
document_id: migration.render-session-pre-frame-inspection
doc_type: migration
owner:
  kind: capability
  id: diagnostics.session-inspection
version_lane: released
capability_ids:
  - diagnostics.session-inspection
artifact_ids:
  - viewcompose-ui-foundation
sample_ids:
  - tooling.diagnostics-session-inspection
source_state: Custom inspection tooling exhaustively handles the three original RenderSessionInspectionPolicy values and can register only after a successful native frame.
target_state: Custom inspection tooling handles TrackSessionBeforeFirstFrame for explicitly armed one-frame capture while retaining the ordinary post-frame policies.
---

# Migrate pre-frame render-session inspection

`RenderSessionInspectionPolicy` adds `TrackSessionBeforeFirstFrame`. This is a source-breaking Alpha
change for Kotlin callers that exhaustively evaluate the enum with `when`; add the new branch before
recompiling against the updated UI Foundation artifact. Ordinary applications that do not install
custom `RenderSessionInspectionTooling` need no change.

Use the new policy only after an explicit tooling request selects one candidate logical Session.
The Session calls `register` immediately before its initial frame with an empty `sourceCandidates`
list. A timing capture started synchronously from that registration attaches to the entering frame
and does not request a nested structural render. The registration remains responsible for disposal
even if the initial frame rolls back.

Keep the existing branches for their original purposes:

- `Ignore` installs no inspection state;
- `TrackSession` registers after the first successful native frame without source capture; and
- `TrackSessionAndCaptureSources` registers after the first successful native frame with bounded
  source candidates.

Do not use the pre-frame policy as a general eager-registration mode. The adapter remains confined
to the platform render thread, must make an armed decision without application-key retention, and
must not add recurring observers, traversal, timing, or report work to the inactive path. The
compiled `renderSessionInspectionToolingSample` demonstrates a one-shot `LazyItem` arm alongside
the ordinary policies.
