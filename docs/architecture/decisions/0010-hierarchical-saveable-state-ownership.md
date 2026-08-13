# ADR-0010: Hierarchical saveable-state ownership

- Status: Accepted
- Date: 2026-08-13

## Context

The Android host installs one `SaveableStateRegistry` for each saved-state owner. Delayed ViewCompose
containers create independent child `RenderSession` instances for lazy items, Pager pages, tabs,
and overlay surfaces. Those sessions previously inherited the same flat host registry.

Each child composer starts its automatic key sequence at the same structural path. Two visible
children could therefore both register `auto:root:0:1`; equal explicit keys had the same problem.
The registry correctly rejected the second provider, but the rejection happened after the native
frame was mounted, producing a committed-frame failure during scrolling. Adding explicit Demo keys,
overwriting providers, or unregistering state on detach would hide one symptom while preserving
cross-child aliasing, restoration corruption, or state loss.

Stable collection keys already identify logical children for diffing and recycling. Saveable-state
ownership must use the same logical hierarchy instead of making every child composer share one
global string-key namespace.

## Decision

1. A host `SaveableStateRegistry` remains the root persistence boundary. Navigation entries and
   other saved-state owners continue to receive distinct root registries.
2. Every framework container that creates independent child compositions remembers one internal
   saveable-state holder in its parent composition. The holder owns child registries by stable
   logical identity: lazy item key, resolved Pager page key, tab key, or overlay surface identity.
3. A child composition receives its child registry through the captured Local snapshot. Automatic
   and explicit `rememberSaveable` keys are local to that child registry. Nested containers repeat
   the rule, creating a hierarchy rather than a flattened encoded key.
4. Detaching or recycling a child closes its registry lease and retains the child's last saved map
   in the parent holder. Reattaching the same logical key restores that map. Reordering does not
   transfer state between keys.
5. The parent holder saves child maps as one value through its own transactional
   `rememberSaveable` registration. A logical key must be accepted by the host registry when that
   scope contains saved state. Unsupported keys fail with a scoped diagnostic instead of being
   hashed into an ambiguous string.
6. Only one active presentation of a logical child owns persistence. A concurrent renderer-created
   replica, such as a detached pinned-header presentation, starts from the owner's current snapshot
   but is non-owning and cannot overwrite the logical child's saved state.
7. Duplicate providers inside one child registry remain an error. The hierarchy does not weaken
   the invariant that two `rememberSaveable` calls in the same composition scope need distinct
   explicit or structural identity.
8. Container-holder creation participates in the parent composition transaction. A failed parent
   frame cannot publish a candidate holder, child scope, restored-value claim, or child update.

## Public API and compatibility impact

No new application-facing API is introduced. This is a Q3 behavioral correction to
`rememberSaveable` ownership in `viewcompose-ui-foundation`:

- explicit keys are unique within their logical composition scope rather than across every child
  session sharing one host owner;
- lazy keys continue to be required and stable; unkeyed Pager pages use their resolved position,
  so state follows position until the caller supplies a stable page key;
- explicit root-composition keys and the Android Bundle bridge format remain unchanged;
- a container holder occupies one parent automatic saveable slot, so later automatic keys in that
  structural scope may shift and are not a persistence compatibility surface;
- previously persisted child values written through the defective flat namespace are not migrated.

The child-state reset and automatic-key shift are an intentional hard cut. The old representation
could already alias unrelated children, so a compatibility reader could not determine safe
ownership. Applications that require a durable root identity across framework upgrades use an
explicit `rememberSaveable` key.

## Consequences

- Sibling and nested child sessions can use identical automatic or explicit local keys safely.
- Child state survives holder recycling, keyed reorder, and host recreation without coupling the
  platform-neutral module to Android Bundle types.
- Container declaration position still identifies the parent holder. Without a compiler transform,
  callers must keep conditional container call sites structurally stable or wrap them in `key`.
- A child key that is suitable for RecyclerView identity but not saveable by the installed host is
  valid until the child actually registers saveable state; it then fails explicitly.
- Secondary visual replicas do not become a second source of persisted business state.

## Rejected alternatives

### Prefix every provider key with a hash of the item key

Rejected because arbitrary Kotlin keys have no collision-free, platform-neutral string encoding.
Hash collisions would turn a correctness defect into a rarer correctness defect, and explicit keys
would still require interception.

### Add explicit keys only in the Demo

Rejected because each child composer can generate the same explicit key. The defect belongs to the
framework ownership boundary, not the example page.

### Let the root registry accept or overwrite duplicate providers

Rejected because save order would choose an arbitrary child and restoration could move state to a
different item. Duplicate registration remains a useful invariant violation within one scope.

### Dispose item state whenever a holder detaches

Rejected because RecyclerView detach is a presentation event, not logical removal. It would lose
state during ordinary scrolling and prefetch churn.

## Validation

The architecture requires deterministic tests for:

1. sibling automatic and explicit keys;
2. keyed reorder, detach, recycle, and reattach;
3. nested lazy/Pager/tab scopes and overlay surfaces;
4. host save and recreation while children are attached or retained;
5. concurrent pinned-header replicas;
6. failed parent and child composition transactions;
7. unsupported saveable scope keys and duplicate providers inside one child scope.
