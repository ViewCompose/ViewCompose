import {verifyAccessibility} from './verify-accessibility.mjs';
import {verifySiteBudgets} from './verify-site-budgets.mjs';
import {verifyVersionedDocumentation} from './verify-versioned-documentation.mjs';

try {
  await verifyVersionedDocumentation();
  await verifyAccessibility();
  await verifySiteBudgets();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
