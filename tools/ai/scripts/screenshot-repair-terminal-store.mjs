import {randomUUID} from 'node:crypto';
import {
  link,
  lstat,
  mkdir,
  open,
  readFile,
  unlink,
} from 'node:fs/promises';
import {homedir} from 'node:os';
import {isAbsolute, parse, resolve} from 'node:path';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairOutcomeHost,
} from './screenshot-repair-execution-adapter.mjs';

const outcomeSchemaPath = new URL(
  '../contracts/screenshot-repair-execution-outcome.schema.json',
  import.meta.url,
);
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const MAX_RECORD_BYTES = 65_536;

let outcomeSchemaPromise;

function loadSchema() {
  outcomeSchemaPromise ??= readFile(outcomeSchemaPath, 'utf8').then(JSON.parse);
  return outcomeSchemaPromise;
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function encodedWithinLimit(value) {
  try {
    const encoded = JSON.stringify(value);
    return typeof encoded === 'string' &&
      Buffer.byteLength(encoded, 'utf8') <= MAX_RECORD_BYTES;
  } catch {
    return false;
  }
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return fingerprintRepairValue(copy);
}

function outcomeDraft(outcome) {
  const draft = structuredClone(outcome);
  delete draft.receipt;
  delete draft.outcomeFingerprint;
  return draft;
}

export class ScreenshotRepairTerminalStoreError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotRepairTerminalStoreError';
    this.code = code;
    this.retryExecution = false;
  }
}

function fail(code, message) {
  throw new ScreenshotRepairTerminalStoreError(code, message);
}

async function ensureStoreRoot(storeRoot) {
  if (typeof storeRoot !== 'string' || storeRoot.length === 0) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-ROOT-INVALID',
      'Terminal storage requires one explicit absolute dedicated directory.',
    );
  }
  const normalized = resolve(storeRoot);
  if (
    !isAbsolute(storeRoot) ||
    normalized !== storeRoot ||
    normalized === parse(normalized).root ||
    normalized === resolve(homedir())
  ) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-ROOT-INVALID',
      'Terminal storage requires one explicit absolute dedicated directory.',
    );
  }
  await mkdir(normalized, {recursive: true, mode: 0o700});
  const info = await lstat(normalized);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o077) !== 0) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-ROOT-UNSAFE',
      'Terminal storage must be a private non-symbolic-link directory.',
    );
  }
  if (typeof process.getuid === 'function' && info.uid !== process.getuid()) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-ROOT-UNSAFE',
      'Terminal storage must be owned by the current process user.',
    );
  }
  return normalized;
}

async function syncDirectory(storeRoot) {
  const handle = await open(storeRoot, 'r');
  try {
    await handle.sync();
  } finally {
    await handle.close();
  }
}

async function readStoredOutcome(path, schema) {
  let info;
  try {
    info = await lstat(path);
  } catch (error) {
    if (error?.code === 'ENOENT') return null;
    throw error;
  }
  if (
    !info.isFile() ||
    info.isSymbolicLink() ||
    info.size <= 0 ||
    info.size > MAX_RECORD_BYTES ||
    (info.mode & 0o077) !== 0
  ) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-RECORD-CORRUPT',
      'The terminal record is not one private bounded regular file.',
    );
  }
  let outcome;
  try {
    outcome = JSON.parse(await readFile(path, 'utf8'));
  } catch {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-RECORD-CORRUPT',
      'The terminal record cannot be parsed as one JSON outcome.',
    );
  }
  if (
    validateSchemaValue(outcome, schema).length > 0 ||
    outcome.outcomeFingerprint !== fingerprintWithout(outcome, 'outcomeFingerprint')
  ) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-RECORD-CORRUPT',
      'The terminal record failed schema or content-address validation.',
    );
  }
  return outcome;
}

function buildOutcome(draft, {storeId, trustDomainId}) {
  const draftFingerprint = fingerprintRepairValue(draft);
  const outcome = {
    ...structuredClone(draft),
    receipt: {
      issuerTrustDomainId: trustDomainId,
      reservationReceipt: draft.lineage.reservationReceipt,
      terminalState: 'recorded',
      outcomeTransport: 'trusted-host-callback-only',
      outcomeReceipt: fingerprintRepairValue({
        schemaVersion: 1,
        purpose: 'screenshot-repair-terminal-outcome',
        storeId,
        trustDomainId,
        reservationReceipt: draft.lineage.reservationReceipt,
        draftFingerprint,
      }),
    },
  };
  if (outcome.receipt.outcomeReceipt === draft.lineage.reservationReceipt) {
    fail(
      'VC-AI-REPAIR-TERMINAL-STORE-RECEIPT-COLLISION',
      'The terminal receipt collides with its reservation receipt.',
    );
  }
  outcome.outcomeFingerprint = fingerprintRepairValue(outcome);
  return outcome;
}

function validateDraft(draft, outcome, schema, trustDomainId) {
  return encodedWithinLimit(draft) &&
    draft?.lineage?.hostTrustDomainId === trustDomainId &&
    SHA256.test(draft?.lineage?.reservationReceipt ?? '') &&
    validateSchemaValue(outcome, schema).length === 0 &&
    outcome.outcomeFingerprint === fingerprintWithout(outcome, 'outcomeFingerprint');
}

function storedOutcomeMatchesStore(outcome, reservationReceipt, trustDomainId) {
  return outcome !== null &&
    outcome.lineage.hostTrustDomainId === trustDomainId &&
    outcome.lineage.reservationReceipt === reservationReceipt &&
    outcome.receipt.issuerTrustDomainId === trustDomainId &&
    outcome.receipt.reservationReceipt === reservationReceipt &&
    outcome.receipt.outcomeReceipt !== reservationReceipt;
}

export async function createFileBackedScreenshotRepairTerminalStore({
  storeRoot,
  storeId,
  trustDomainId,
} = {}) {
  if (!STABLE_ID.test(storeId ?? '') || !STABLE_ID.test(trustDomainId ?? '')) {
    throw new TypeError('Stable store and trust-domain identities are required.');
  }
  const [root, schema] = await Promise.all([
    ensureStoreRoot(storeRoot),
    loadSchema(),
  ]);

  const readTerminalOutcome = async (reservationReceipt) => {
    if (!SHA256.test(reservationReceipt ?? '')) {
      fail(
        'VC-AI-REPAIR-TERMINAL-STORE-RESERVATION-INVALID',
        'Terminal reconciliation requires one exact reservation receipt.',
      );
    }
    const outcome = await readStoredOutcome(
      resolve(root, `${reservationReceipt}.terminal.json`),
      schema,
    );
    if (outcome !== null && !storedOutcomeMatchesStore(
      outcome,
      reservationReceipt,
      trustDomainId,
    )) {
      fail(
        'VC-AI-REPAIR-TERMINAL-STORE-RECORD-CORRUPT',
        'The terminal record does not bind this store trust domain and reservation.',
      );
    }
    return outcome === null ? null : structuredClone(outcome);
  };

  const record = async (draft) => {
    const outcome = buildOutcome(draft, {storeId, trustDomainId});
    if (!validateDraft(draft, outcome, schema, trustDomainId)) {
      fail(
        'VC-AI-REPAIR-TERMINAL-STORE-INPUT-INVALID',
        'The terminal draft is not an exact bounded outcome for this trust domain.',
      );
    }
    const reservationReceipt = draft.lineage.reservationReceipt;
    const target = resolve(root, `${reservationReceipt}.terminal.json`);
    const temporary = resolve(root, `.${reservationReceipt}.${randomUUID()}.tmp`);
    let temporaryCreated = false;
    try {
      const handle = await open(temporary, 'wx', 0o600);
      temporaryCreated = true;
      try {
        await handle.writeFile(`${JSON.stringify(outcome, null, 2)}\n`, 'utf8');
        await handle.sync();
      } finally {
        await handle.close();
      }
      try {
        await link(temporary, target);
        await syncDirectory(root);
      } catch (error) {
        if (error?.code !== 'EEXIST') throw error;
        const existing = await readStoredOutcome(target, schema);
        if (
          !storedOutcomeMatchesStore(existing, reservationReceipt, trustDomainId) ||
          !same(outcomeDraft(existing), draft)
        ) {
          fail(
            'VC-AI-REPAIR-TERMINAL-STORE-CONFLICT',
            'The reservation already has a different terminal outcome and cannot be overwritten.',
          );
        }
        return structuredClone(existing);
      }
      return structuredClone(outcome);
    } finally {
      if (temporaryCreated) {
        try {
          await unlink(temporary);
        } catch (error) {
          if (error?.code !== 'ENOENT') {
            // A leaked private temporary file is safer than weakening a committed terminal record.
          }
        }
      }
    }
  };

  return Object.freeze({
    host: createTrustedScreenshotRepairOutcomeHost({trustDomainId, record}),
    readTerminalOutcome,
  });
}
