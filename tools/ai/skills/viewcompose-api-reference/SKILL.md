---
name: viewcompose-api-reference
description: Retrieve exact ViewCompose APIs, component contracts, dependencies, and compiled samples when answering framework usage questions. Do not use it for generic Android APIs or for implementing unrelated UI frameworks.
---

# ViewCompose API Reference

Answer from the immutable ViewCompose Knowledge Bundle, with enough identity for the caller to
verify every fact.

## Exact version and evidence

- Select the exact framework version lane and identity exposed by the available ViewCompose tools.
  Never substitute `latest`, silently downgrade, or combine `current-source` and released facts.
- Use `get_api_reference` for an exact symbol, capability, or artifact identifier.
- Use `search_component` for intent or partial-name discovery, then resolve the selected result with
  `get_component_reference`. Do not treat ranked search text as the final API contract.
- Use `get_sample` when usage shape matters. Preserve whether the returned region is compiled code
  or a non-executable evidence outline.
- Report the artifact coordinate/version, capability, exact signature and defaults, applicable
  rules, sample identity, and bundle fingerprint. Label the result as knowledge evidence only.

## Stop and authority

This workflow is read-only. Do not edit project files or claim compilation, rendering, runtime
behavior, deprecation, or migration fidelity from retrieval alone. If an identifier remains
ambiguous, present the bounded candidates and request the receiver or symbol choice only when that
choice materially changes the answer. Stop when the same unresolved result repeats without new
evidence, and stop when the same diagnostic repeats without new evidence. Never fabricate an API
to complete the response.
