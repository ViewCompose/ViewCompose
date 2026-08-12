# Architecture Decisions

Architecture decision records preserve decisions that are expensive to reverse, affect multiple
modules, or establish public contracts. They explain why a design was selected while the current
architecture pages describe how the system works now.

## Accepted decisions

- [ADR-0001: Hosted documentation platform](./0001-hosted-documentation-platform.md)
- [ADR-0002: Five-layer runtime module architecture](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003: Public package ownership and platform handles](./0003-public-package-ownership-and-platform-handles.md)
- [ADR-0004: Design-system resolution boundary](./0004-design-system-resolution-boundary.md)
- [ADR-0005: Design-system host and component backend boundary](./0005-design-system-host-and-component-backend-boundary.md)
- [ADR-0006: Root-scoped overlay backend selection](./0006-root-scoped-overlay-backend-selection.md)
- [ADR-0007: Host-owned Android resource environment](./0007-host-owned-android-resource-environment.md)
- [ADR-0008: Transactional effect lifecycle](./0008-transactional-effect-lifecycle.md)

## Rules

1. Use the next four-digit number and a lowercase kebab-case title.
2. Accepted records are not rewritten to hide historical trade-offs.
3. A changed decision receives a new ADR that explicitly supersedes the previous one.
4. Update current architecture and module documentation when a decision changes implementation.
