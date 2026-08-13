## Summary

- What changed?
- Why is this the correct boundary and design?

## Validation

- [ ] Relevant unit/integration tests
- [ ] `./gradlew verifyDocumentationStructure`
- [ ] `./gradlew qaQuick` when applicable

List the commands run and any validation that could not run.

## Documentation impact

Select the applicable impact and explain it below. `No documentation impact` requires a concrete
rationale.

- [ ] New/changed public API and canonical KDoc/Javadoc land together; all elements and applicable
      Q2/Q3 contracts are documented
- [ ] Required compiled `@sample` functions and the owning module documentation were updated
- [ ] Owning module documentation or module catalog changed
- [ ] Guide, tutorial, architecture, migration, or tooling documentation changed
- [ ] ADR added or superseded
- [ ] No documentation impact

Documentation changes or rationale:

Localization impact:

- [ ] Required Chinese mirror updated and reviewed; canonical fingerprint is current
- [ ] Page is deliberately English-only under the language policy
- [ ] No user-visible language content changed
- [ ] `npm run verify:languages` and `npm run verify:translations` pass

## Compatibility and migration

- Does this change public behavior, dependencies, compatibility, defaults, or lifecycle semantics?
- If users must act, where is the migration path documented?

## Application-process tooling impact

- [ ] No application-process development tooling changed
- [ ] Concrete implementation remains in a Tooling module and runtime ownership is neutral
- [ ] Activation requires optional artifact + debuggable process + explicit request
- [ ] Inactive-path tests prove zero recurring listener, traversal, serialization, and report writes
- [ ] Release classpath exclusion and any required same-device Debug benchmark are recorded

Evidence or no-impact rationale:

## Maven release intent

- [ ] Added one immutable `release/changes/<unique>.json` file for every changed published artifact
- [ ] Classified detected artifacts as `breaking`, `feature`, `fix`, or explicitly ignored with a reason
- [ ] Shared build input changes declare affected artifacts or a concrete no-release reason
- [ ] No Maven-published artifact or release input changed

Changeset files or no-release rationale:
