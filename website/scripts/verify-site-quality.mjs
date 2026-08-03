import {verifyAccessibility} from './verify-accessibility.mjs';
import {verifySiteBudgets} from './verify-site-budgets.mjs';

try {
  await verifyAccessibility();
  await verifySiteBudgets();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
