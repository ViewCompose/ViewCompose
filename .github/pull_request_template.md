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
