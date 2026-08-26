# System navigation delivery history

This file preserves the temporary delivery sequence that previously occupied the public
Navigation guide. It is historical evidence, not a current API or acceptance contract.

The completed sequence was:

1. establish the platform-neutral route, immutable back-stack, transaction, lifecycle-plan, and
   persistence kernels;
2. add destination Android ownership and retained child render sessions;
3. mount the transactional `NavHost`, then add motion, restore, system Back, and predictive Back;
4. verify the initial Android 13 and Android 15 device matrix;
5. extend the same model with nested graph ownership, retained tab stacks, strict deep links, and
   adaptive native View panes;
6. close the public acceptance Demo and device journeys without introducing a parallel navigation
   path.

The durable result is now owned by:

- [Navigation runtime architecture](../architecture/navigation.md) for transaction, ownership,
  lifecycle, restoration, Back, and motion invariants;
- [Configure a production navigation host](../guides/navigation.md) for the concrete application
  task and failure checks;
- [Navigation Android](../modules/viewcompose-navigation-android/README.md) and
  [Navigation Core](../modules/viewcompose-navigation-core/README.md) for artifact-specific APIs,
  compatibility, and verification evidence.

Historical branch status, pending rebase language, and numbered Stage 1–10 implementation detail
were removed from active documentation when Governance V2 Phase 3 began. Git history remains the
exhaustive source for that temporary sequence.
