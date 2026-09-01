import {createReadStream, createWriteStream} from 'node:fs';
import {
  appendFile,
  chmod,
  lstat,
  mkdir,
  open,
  readFile,
  realpath,
  rename,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises';
import {homedir, platform} from 'node:os';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {createInterface} from 'node:readline/promises';
import {randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {evaluateScreenshotRepairCandidate} from './screenshot-repair-candidate-evaluator.mjs';
import {
  fingerprintSourceBytes,
  reconstructGeneratedCandidate,
} from './screenshot-source-edit.mjs';
import {secureReplaceSource} from './screenshot-source-secure-backend.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {validateKotlin} from './static-validator.mjs';

const contractRoot = new URL('../contracts/', import.meta.url);
const [requestSchema, journalSchema, receiptSchema] = await Promise.all([
  readFile(new URL('screenshot-source-application-request.schema.json', contractRoot), 'utf8').then(JSON.parse),
  readFile(new URL('screenshot-source-application-journal.schema.json', contractRoot), 'utf8').then(JSON.parse),
  readFile(new URL('screenshot-source-application-receipt.schema.json', contractRoot), 'utf8').then(JSON.parse),
]);
const SHA256 = /^[a-f0-9]{64}$/u;

export class ScreenshotSourceTransactionError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotSourceTransactionError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new ScreenshotSourceTransactionError(code, message);
}

function canonicalFingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return fingerprintRepairValue(copy);
}

function contained(root, candidate) {
  const path = relative(root, candidate);
  return path === '' || (!isAbsolute(path) && path !== '..' && !path.startsWith(`..${sep}`));
}

export function defaultScreenshotSourceStateRoot() {
  if (platform() === 'darwin') {
    return resolve(homedir(), 'Library/Application Support/ViewCompose/ai-tooling/screenshot-repair');
  }
  if (platform() === 'win32') {
    const local = process.env.LOCALAPPDATA;
    if (!local || !isAbsolute(local)) {
      fail('VC-AI-SOURCE-APPLICATION-STATE-UNSAFE', 'LOCALAPPDATA is unavailable.');
    }
    return resolve(local, 'ViewCompose/ai-tooling/screenshot-repair');
  }
  const state = process.env.XDG_STATE_HOME;
  return state && isAbsolute(state)
    ? resolve(state, 'viewcompose/ai-tooling/screenshot-repair')
    : resolve(homedir(), '.local/state/viewcompose/ai-tooling/screenshot-repair');
}

async function ensureStateRoot(stateRoot, projectRoot) {
  if (!isAbsolute(stateRoot)) {
    fail('VC-AI-SOURCE-APPLICATION-STATE-UNSAFE', 'Recovery state root must be absolute.');
  }
  await mkdir(stateRoot, {recursive: true, mode: 0o700});
  await chmod(stateRoot, 0o700);
  const physical = await realpath(stateRoot);
  const metadata = await lstat(stateRoot);
  if (
    !metadata.isDirectory() ||
    metadata.isSymbolicLink() ||
    contained(await realpath(projectRoot), physical)
  ) {
    fail(
      'VC-AI-SOURCE-APPLICATION-STATE-UNSAFE',
      'Recovery state must be one owner-only physical directory outside the project.',
    );
  }
  return physical;
}

function validateRequest(request) {
  const violations = validateSchemaValue(request, requestSchema);
  if (violations.length > 0 || request.requestFingerprint !== canonicalFingerprint(request, 'requestFingerprint')) {
    fail('VC-AI-SOURCE-APPLICATION-REQUEST-INVALID', 'Prepared request integrity is invalid.');
  }
  const unsigned = structuredClone(request);
  delete unsigned.requestFingerprint;
  unsigned.authorization.confirmationSuffix = '000000000000';
  const expectedSuffix = canonicalFingerprint(unsigned).slice(0, 12).toUpperCase();
  if (request.authorization.confirmationSuffix !== expectedSuffix) {
    fail('VC-AI-SOURCE-APPLICATION-AUTHORIZATION-INVALID', 'Confirmation identity drifted.');
  }
}

async function syncDirectory(path) {
  const handle = await open(path, 'r');
  try {
    await handle.sync();
  } finally {
    await handle.close();
  }
}

async function durableFile(path, bytes) {
  const temporary = `${path}.${randomUUID()}.tmp`;
  const handle = await open(temporary, 'wx', 0o600);
  try {
    await handle.writeFile(bytes);
    await handle.sync();
  } finally {
    await handle.close();
  }
  try {
    await rename(temporary, path);
    await syncDirectory(resolve(path, '..'));
  } finally {
    await rm(temporary, {force: true});
  }
}

async function durableJson(path, value) {
  await durableFile(path, `${JSON.stringify(value, null, 2)}\n`);
}

async function existingOrWriteJson(path, value) {
  try {
    const existing = JSON.parse(await readFile(path, 'utf8'));
    if (JSON.stringify(existing) !== JSON.stringify(value)) {
      fail('VC-AI-SOURCE-APPLICATION-REPLAY', 'Prepared request storage conflicts with existing state.');
    }
    return;
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  await durableJson(path, value);
}

function transactionDirectory(stateRoot, request) {
  return resolve(
    stateRoot,
    request.project.physicalRootFingerprint,
    request.requestFingerprint,
  );
}

export async function storePreparedSourceApplication(bundle, {
  stateRoot = defaultScreenshotSourceStateRoot(),
  projectRoot,
} = {}) {
  validateRequest(bundle?.request);
  const root = await ensureStateRoot(stateRoot, projectRoot);
  const directory = transactionDirectory(root, bundle.request);
  await mkdir(directory, {recursive: true, mode: 0o700});
  await chmod(directory, 0o700);
  await existingOrWriteJson(resolve(directory, 'bundle.json'), bundle);
  return {
    requestFingerprint: bundle.request.requestFingerprint,
    confirmationSuffix: bundle.request.authorization.confirmationSuffix,
    stateRootFingerprint: fingerprintRepairValue({physicalStateRoot: root}),
  };
}

async function locateBundle(requestFingerprint, projectRoot, stateRoot) {
  if (!SHA256.test(requestFingerprint ?? '')) {
    fail('VC-AI-SOURCE-APPLICATION-REQUEST-INVALID', 'Request fingerprint is invalid.');
  }
  const root = await ensureStateRoot(stateRoot, projectRoot);
  const rootFingerprint = fingerprintRepairValue({physicalRoot: await realpath(projectRoot)});
  const directory = resolve(root, rootFingerprint, requestFingerprint);
  if (!contained(root, directory)) {
    fail('VC-AI-SOURCE-APPLICATION-STATE-UNSAFE', 'Transaction state escapes its root.');
  }
  const bundle = JSON.parse(await readFile(resolve(directory, 'bundle.json'), 'utf8'));
  validateRequest(bundle.request);
  if (
    bundle.request.requestFingerprint !== requestFingerprint ||
    bundle.request.project.physicalRootFingerprint !== rootFingerprint
  ) {
    fail('VC-AI-SOURCE-APPLICATION-ROOT-DRIFT', 'Stored request does not bind this project.');
  }
  return {root, directory, bundle};
}

async function exactTarget(projectRoot, request, expectedSha256, expectedFileKey) {
  const root = await realpath(projectRoot);
  const target = resolve(root, request.project.relativePath);
  if (!contained(root, target)) {
    fail('VC-AI-SOURCE-APPLICATION-PATH-INVALID', 'Target escapes its project root.');
  }
  let cursor = root;
  for (const segment of request.project.relativePath.split('/')) {
    cursor = resolve(cursor, segment);
    const metadata = await lstat(cursor);
    if (metadata.isSymbolicLink()) {
      fail('VC-AI-SOURCE-APPLICATION-SYMLINK', 'Target path contains a symbolic link.');
    }
  }
  const metadata = await stat(target);
  const key = `dev:${metadata.dev}:ino:${metadata.ino}`;
  if (
    !metadata.isFile() ||
    metadata.nlink !== 1 ||
    (expectedFileKey && key !== expectedFileKey)
  ) {
    fail('VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT', 'Target file identity changed.');
  }
  const bytes = await readFile(target);
  const sha256 = fingerprintSourceBytes(bytes);
  if (expectedSha256 && sha256 !== expectedSha256) {
    fail('VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT', 'Target source bytes changed.');
  }
  return {root, target, bytes, key, sha256};
}

async function readOptionalJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch (error) {
    if (error?.code === 'ENOENT') return null;
    throw error;
  }
}

async function pathExists(path) {
  try {
    await lstat(path);
    return true;
  } catch (error) {
    if (error?.code === 'ENOENT') return false;
    throw error;
  }
}

function journalEntry(request, transactionId, sequence, previous, state, observedSha256, recovery, evidence) {
  const entry = {
    schemaVersion: 1,
    kind: 'screenshot-source-application-journal-entry',
    transactionId,
    requestFingerprint: request.requestFingerprint,
    sequence,
    previousEntryFingerprint: previous,
    state,
    recordedAt: new Date().toISOString(),
    source: {
      relativePath: request.project.relativePath,
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      observedSha256,
    },
    recovery,
    evidence,
  };
  entry.entryFingerprint = canonicalFingerprint(entry);
  if (validateSchemaValue(entry, journalSchema).length > 0) {
    fail('VC-AI-SOURCE-APPLICATION-JOURNAL-INVALID', 'Transaction journal entry is invalid.');
  }
  return entry;
}

async function appendJournal(path, entry) {
  await appendFile(path, `${JSON.stringify(entry)}\n`, {encoding: 'utf8', mode: 0o600});
  const handle = await open(path, 'r');
  try {
    await handle.sync();
  } finally {
    await handle.close();
  }
}

async function readJournal(path, request) {
  let text;
  try {
    text = await readFile(path, 'utf8');
  } catch (error) {
    if (error?.code === 'ENOENT') return [];
    throw error;
  }
  const entries = text.trim() === '' ? [] : text.trim().split('\n').map((line) => JSON.parse(line));
  let previous = null;
  for (let index = 0; index < entries.length; index += 1) {
    const entry = entries[index];
    if (
      validateSchemaValue(entry, journalSchema).length > 0 ||
      entry.entryFingerprint !== canonicalFingerprint(entry, 'entryFingerprint') ||
      entry.requestFingerprint !== request.requestFingerprint ||
      entry.sequence !== index ||
      entry.previousEntryFingerprint !== previous
    ) {
      fail('VC-AI-SOURCE-APPLICATION-JOURNAL-INVALID', 'Transaction journal chain is invalid.');
    }
    previous = entry.entryFingerprint;
  }
  return entries;
}

function gate(status, passed, total, evidenceFingerprint) {
  return {status, passed, total, evidenceFingerprint};
}

async function verifyApplied(bundle, source, {
  validate = validateKotlin,
  evaluate = evaluateScreenshotRepairCandidate,
  signal,
} = {}) {
  const staticResult = await validate({
    source: source.toString('utf8'),
    path: bundle.request.project.relativePath,
    requestId: `source-application-${bundle.request.requestFingerprint}`,
    maxInputBytes: 1024 * 1024,
  });
  const candidate = await evaluate({
    resolutionResult: bundle.repairedResolution,
    generationRequest: bundle.generationRequest,
    previewBindings: bundle.previewBindings,
    pixelReference: bundle.pixelReference,
  }, {signal});
  const byName = new Map((candidate?.evaluation?.gates ?? []).map((item) => [item.name, item]));
  const identity = (value) => SHA256.test(value?.evidenceFingerprint ?? '')
    ? value.evidenceFingerprint
    : fingerprintRepairValue(value ?? null);
  const mapped = {
    static: gate(staticResult?.status === 'success' ? 'passed' : 'failed', staticResult?.status === 'success' ? 1 : 0, 1, identity(staticResult)),
    compilation: gate(byName.get('compilation')?.status ?? 'error', byName.get('compilation')?.passed ?? 0, byName.get('compilation')?.total ?? 1, identity(byName.get('compilation'))),
    render: gate(byName.get('render')?.status ?? 'error', byName.get('render')?.passed ?? 0, byName.get('render')?.total ?? 1, identity(byName.get('render'))),
    semanticGeometry: gate(
      byName.get('semantics')?.status === 'passed' && byName.get('structure')?.status === 'passed' ? 'passed' : 'failed',
      (byName.get('semantics')?.passed ?? 0) + (byName.get('structure')?.passed ?? 0),
      (byName.get('semantics')?.total ?? 1) + (byName.get('structure')?.total ?? 1),
      fingerprintRepairValue({semantics: byName.get('semantics'), structure: byName.get('structure')}),
    ),
    eligiblePixels: gate(
      byName.get('exact-pixels')?.status ?? 'not-applicable',
      byName.get('exact-pixels')?.mismatchedPixels === 0
        ? byName.get('exact-pixels')?.comparedPixels ?? 0
        : 0,
      byName.get('exact-pixels')?.comparedPixels ?? 0,
      identity(byName.get('exact-pixels')),
    ),
  };
  const verified = Object.values(mapped).every((item) =>
    ['passed', 'not-applicable'].includes(item.status));
  const evidence = {
    status: verified ? 'verified' : 'failed',
    sourceSha256: fingerprintSourceBytes(source),
    ...mapped,
  };
  evidence.evidenceFingerprint = canonicalFingerprint(evidence);
  return evidence;
}

export async function confirmFromControllingTty({operation, suffix, summary}) {
  if (platform() === 'win32') {
    fail('VC-AI-SOURCE-APPLICATION-TTY-REQUIRED', 'The v1 attended host requires a POSIX controlling TTY.');
  }
  const input = createReadStream('/dev/tty');
  const output = createWriteStream('/dev/tty');
  const reader = createInterface({input, output});
  try {
    output.write(`${summary}\nType ${operation.toUpperCase()} ${suffix} to continue: `);
    const answer = await reader.question('');
    return answer === `${operation.toUpperCase()} ${suffix}`;
  } catch {
    fail('VC-AI-SOURCE-APPLICATION-TTY-REQUIRED', 'A controlling TTY is required.');
  } finally {
    reader.close();
    input.destroy();
    output.end();
  }
}

function receiptFor({request, operation, status, transactionId, approvalSuffix, approvedAt, terminalSha256, evidence, stateRoot, journal, linkedReceipt, recoveryDurable = true}) {
  const committed = ['applied-verified', 'applied-validation-failed', 'applied-evidence-error', 'rolled-back'].includes(status);
  const receipt = {
    schemaVersion: 1,
    kind: 'screenshot-source-application-receipt',
    operation,
    status,
    transactionId,
    requestFingerprint: request.requestFingerprint,
    project: {
      physicalRootFingerprint: request.project.physicalRootFingerprint,
      relativePath: request.project.relativePath,
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      terminalSha256,
    },
    approval: {
      channel: 'controlling-tty',
      confirmedSuffix: approvalSuffix,
      approvedAt: approvedAt ?? new Date().toISOString(),
      singleUse: true,
    },
    atomicReplace: {
      backend: 'directory-handle-atomic-v1',
      committed,
      directorySynced: committed,
      terminalReread: true,
    },
    evidence,
    recovery: {
      stateRootFingerprint: fingerprintRepairValue({physicalStateRoot: stateRoot}),
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      durable: recoveryDurable,
    },
    rollback: {
      eligible: operation === 'apply' && committed,
      requiredCurrentSha256: operation === 'apply'
        ? request.edit.candidate.sha256
        : request.project.preimage.sha256,
      linkedReceiptFingerprint: linkedReceipt ?? null,
    },
    journalChainFingerprint: journal.entryFingerprint,
  };
  receipt.receiptFingerprint = canonicalFingerprint(receipt);
  if (validateSchemaValue(receipt, receiptSchema).length > 0) {
    fail('VC-AI-SOURCE-APPLICATION-RECEIPT-INVALID', 'Terminal receipt violates v1.');
  }
  return receipt;
}

function validateReceipt(receipt, request, operation) {
  if (
    !receipt ||
    validateSchemaValue(receipt, receiptSchema).length > 0 ||
    receipt.receiptFingerprint !== canonicalFingerprint(receipt, 'receiptFingerprint') ||
    receipt.requestFingerprint !== request.requestFingerprint ||
    receipt.operation !== operation
  ) {
    fail('VC-AI-SOURCE-APPLICATION-RECEIPT-INVALID', 'Stored terminal receipt is invalid.');
  }
  return receipt;
}

function errorEvidence(sourceSha256, status = 'error') {
  const gateStatus = status === 'conflict' ? 'conflict' : 'error';
  const evidence = {
    status,
    sourceSha256,
    static: gate(gateStatus, 0, 1, '0'.repeat(64)),
    compilation: gate(gateStatus, 0, 1, '0'.repeat(64)),
    render: gate(gateStatus, 0, 1, '0'.repeat(64)),
    semanticGeometry: gate(gateStatus, 0, 1, '0'.repeat(64)),
    eligiblePixels: gate(gateStatus, 0, 0, '0'.repeat(64)),
  };
  evidence.evidenceFingerprint = canonicalFingerprint(evidence);
  return evidence;
}

async function settleAppliedCandidate({
  located,
  projectRoot,
  transactionId,
  recovery,
  journal,
  journalPath,
  approval,
  verify,
  signal,
}) {
  const request = located.bundle.request;
  const beforeEvidence = await exactTarget(projectRoot, request, request.edit.candidate.sha256);
  let evidence;
  let status;
  try {
    evidence = await verify(located.bundle, beforeEvidence.bytes, {signal});
    const afterEvidence = await exactTarget(projectRoot, request, request.edit.candidate.sha256);
    if (afterEvidence.sha256 !== beforeEvidence.sha256) {
      status = 'applied-conflict';
      evidence = {...evidence, status: 'conflict'};
      evidence.evidenceFingerprint = canonicalFingerprint(evidence, 'evidenceFingerprint');
    } else {
      status = evidence.status === 'verified' ? 'applied-verified' : 'applied-validation-failed';
    }
  } catch {
    const source = await exactTarget(projectRoot, request, null);
    status = source.sha256 === request.edit.candidate.sha256
      ? 'applied-evidence-error'
      : 'applied-conflict';
    evidence = errorEvidence(
      source.sha256,
      status === 'applied-conflict' ? 'conflict' : 'error',
    );
  }
  const state = {
    'applied-verified': 'APPLIED_VERIFIED',
    'applied-validation-failed': 'APPLIED_VALIDATION_FAILED',
    'applied-evidence-error': 'APPLIED_EVIDENCE_ERROR',
    'applied-conflict': 'APPLIED_CONFLICT',
  }[status];
  journal = journalEntry(
    request,
    transactionId,
    journal.sequence + 1,
    journal.entryFingerprint,
    state,
    evidence.sourceSha256,
    recovery,
    {
      status: evidence.status,
      sourceSha256: evidence.sourceSha256,
      evidenceFingerprint: evidence.evidenceFingerprint,
    },
  );
  await appendJournal(journalPath, journal);
  const receipt = receiptFor({
    request,
    operation: 'apply',
    status,
    transactionId,
    approvalSuffix: request.authorization.confirmationSuffix,
    approvedAt: approval.approvedAt,
    terminalSha256: evidence.sourceSha256,
    evidence,
    stateRoot: located.root,
    journal,
  });
  await durableJson(resolve(located.directory, 'apply-receipt.json'), receipt);
  return receipt;
}

async function acquireLock(directory) {
  const path = resolve(directory, 'transaction.lock');
  try {
    const handle = await open(path, 'wx', 0o600);
    await handle.writeFile(`${JSON.stringify({pid: process.pid, createdAt: new Date().toISOString()})}\n`);
    await handle.sync();
    return {path, handle};
  } catch (error) {
    if (error?.code === 'EEXIST') {
      fail('VC-AI-SOURCE-APPLICATION-CONCURRENT', 'A source transaction already owns this request.');
    }
    throw error;
  }
}

async function clearStaleLock(directory) {
  const path = resolve(directory, 'transaction.lock');
  const lock = await readOptionalJson(path);
  if (!lock) return;
  if (!Number.isInteger(lock.pid) || typeof lock.createdAt !== 'string') {
    fail('VC-AI-SOURCE-APPLICATION-LOCK-INVALID', 'Transaction lock is not trustworthy.');
  }
  try {
    process.kill(lock.pid, 0);
    fail('VC-AI-SOURCE-APPLICATION-CONCURRENT', 'A live process still owns this request.');
  } catch (error) {
    if (error instanceof ScreenshotSourceTransactionError) throw error;
    if (error?.code !== 'ESRCH') {
      fail('VC-AI-SOURCE-APPLICATION-CONCURRENT', 'Transaction lock ownership is uncertain.');
    }
  }
  await rm(path);
  await syncDirectory(directory);
}

function approvalRecord(request, operation, suffix) {
  const value = {
    operation,
    requestFingerprint: request.requestFingerprint,
    suffix,
    approvedAt: new Date().toISOString(),
  };
  value.approvalFingerprint = canonicalFingerprint(value);
  return value;
}

function validateApproval(value, request, operation, suffix) {
  if (
    !value ||
    value.operation !== operation ||
    value.requestFingerprint !== request.requestFingerprint ||
    value.suffix !== suffix ||
    value.approvalFingerprint !== canonicalFingerprint(value, 'approvalFingerprint') ||
    !Number.isFinite(Date.parse(value.approvedAt))
  ) {
    fail('VC-AI-SOURCE-APPLICATION-AUTHORIZATION-INVALID', 'Durable attended approval is invalid.');
  }
  return value;
}

async function releaseLock(lock) {
  await lock.handle.close().catch(() => {});
  await rm(lock.path, {force: true});
}

export async function applyPreparedSourceApplication({requestFingerprint, projectRoot} = {}, {
  stateRoot = defaultScreenshotSourceStateRoot(),
  confirm = confirmFromControllingTty,
  replaceSource = secureReplaceSource,
  verify = verifyApplied,
  signal,
  failpoint = () => {},
} = {}) {
  const located = await locateBundle(requestFingerprint, projectRoot, stateRoot);
  const {request} = located.bundle;
  const rollbackReceipt = await readOptionalJson(resolve(located.directory, 'rollback-receipt.json'));
  if (rollbackReceipt) {
    validateReceipt(rollbackReceipt, request, 'rollback');
    await exactTarget(projectRoot, request, request.project.preimage.sha256);
    fail('VC-AI-SOURCE-APPLICATION-REPLAY', 'This single-use request was already rolled back.');
  }
  const storedReceipt = await readOptionalJson(resolve(located.directory, 'apply-receipt.json'));
  if (storedReceipt) {
    validateReceipt(storedReceipt, request, 'apply');
    await exactTarget(projectRoot, request, storedReceipt.project.terminalSha256);
    return storedReceipt;
  }
  if (Date.now() > Date.parse(request.expiresAt)) {
    fail('VC-AI-SOURCE-APPLICATION-EXPIRED', 'Prepared request expired before authorization.');
  }
  const initial = await exactTarget(projectRoot, request, request.project.preimage.sha256, request.project.fileIdentity.key);
  reconstructGeneratedCandidate(initial.bytes, request.edit);
  const summary = [
    'ViewCompose attended screenshot repair',
    `Target: ${request.project.relativePath}`,
    `Request: ${request.requestFingerprint}`,
    `Pre-apply evidence: ${request.lineage.preApplyEvidenceFingerprint}`,
    '',
    request.edit.diff.text,
    'Validation failure will not roll back automatically.',
  ].join('\n');
  if (!await confirm({operation: 'apply', suffix: request.authorization.confirmationSuffix, summary})) {
    fail('VC-AI-SOURCE-APPLICATION-AUTHORIZATION-DENIED', 'Exact attended confirmation was not provided.');
  }
  const lock = await acquireLock(located.directory);
  try {
    if (
      await pathExists(resolve(located.directory, 'journal.jsonl')) ||
      await pathExists(resolve(located.directory, 'approval.json')) ||
      await pathExists(resolve(located.directory, 'request.json')) ||
      await pathExists(resolve(located.directory, 'preimage.kt')) ||
      await pathExists(resolve(located.directory, 'candidate.kt'))
    ) {
      fail(
        'VC-AI-SOURCE-APPLICATION-RECOVERY-REQUIRED',
        'Durable transaction state already exists; reconcile it before retrying.',
      );
    }
    const current = await exactTarget(projectRoot, request, request.project.preimage.sha256, request.project.fileIdentity.key);
    const candidate = reconstructGeneratedCandidate(current.bytes, request.edit);
    const preimagePath = resolve(located.directory, 'preimage.kt');
    const candidatePath = resolve(located.directory, 'candidate.kt');
    const approval = approvalRecord(request, 'apply', request.authorization.confirmationSuffix);
    await durableJson(resolve(located.directory, 'approval.json'), approval);
    await durableFile(preimagePath, current.bytes);
    await durableFile(candidatePath, candidate);
    await durableJson(resolve(located.directory, 'request.json'), request);
    await failpoint('after-recovery-files-fsync');
    const transactionId = fingerprintRepairValue({requestFingerprint, approval: request.authorization.confirmationSuffix});
    const recovery = {
      requestSha256: request.requestFingerprint,
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      durable: true,
    };
    const journalPath = resolve(located.directory, 'journal.jsonl');
    let journal = journalEntry(request, transactionId, 0, null, 'PREPARED', request.project.preimage.sha256, recovery, null);
    await appendJournal(journalPath, journal);
    await failpoint('after-prepared-journal-fsync');
    journal = journalEntry(request, transactionId, 1, journal.entryFingerprint, 'APPLYING', request.project.preimage.sha256, recovery, null);
    await appendJournal(journalPath, journal);
    await failpoint('after-applying-journal-fsync');
    await exactTarget(projectRoot, request, request.project.preimage.sha256, request.project.fileIdentity.key);
    await replaceSource({
      projectRoot: current.root,
      relativePath: request.project.relativePath,
      expectedSha256: request.project.preimage.sha256,
      candidatePath,
      candidateSha256: request.edit.candidate.sha256,
      temporaryName: `.viewcompose-${request.nonce}.tmp`,
    }, {signal});
    await failpoint('after-atomic-replace');
    journal = journalEntry(request, transactionId, 2, journal.entryFingerprint, 'APPLIED_UNVERIFIED', request.edit.candidate.sha256, recovery, null);
    await appendJournal(journalPath, journal);
    await failpoint('after-applied-unverified-journal-fsync');
    return settleAppliedCandidate({
      located,
      projectRoot,
      transactionId,
      recovery,
      journal,
      journalPath,
      approval,
      verify,
      signal,
    });
  } finally {
    await releaseLock(lock);
  }
}

export async function recoverPreparedSourceApplication({requestFingerprint, projectRoot} = {}, {
  stateRoot = defaultScreenshotSourceStateRoot(),
  verify = verifyApplied,
  signal,
} = {}) {
  const located = await locateBundle(requestFingerprint, projectRoot, stateRoot);
  const request = located.bundle.request;
  const rollbackReceipt = await readOptionalJson(resolve(located.directory, 'rollback-receipt.json'));
  if (rollbackReceipt) {
    validateReceipt(rollbackReceipt, request, 'rollback');
    await exactTarget(projectRoot, request, rollbackReceipt.project.terminalSha256);
    return rollbackReceipt;
  }
  const applyReceiptPath = resolve(located.directory, 'apply-receipt.json');
  const existing = await readOptionalJson(applyReceiptPath);
  if (existing) {
    validateReceipt(existing, request, 'apply');
    await exactTarget(projectRoot, request, existing.project.terminalSha256);
    return existing;
  }
  await clearStaleLock(located.directory);
  const lock = await acquireLock(located.directory);
  try {
    const replay = await readOptionalJson(applyReceiptPath);
    if (replay) {
      validateReceipt(replay, request, 'apply');
      await exactTarget(projectRoot, request, replay.project.terminalSha256);
      return replay;
    }
    const approval = validateApproval(
      await readOptionalJson(resolve(located.directory, 'approval.json')),
      request,
      'apply',
      request.authorization.confirmationSuffix,
    );
    const storedRequest = await readOptionalJson(resolve(located.directory, 'request.json'));
    if (storedRequest && JSON.stringify(storedRequest) !== JSON.stringify(request)) {
      fail('VC-AI-SOURCE-APPLICATION-RECOVERY-INVALID', 'Durable request changed.');
    }
    const preimagePath = resolve(located.directory, 'preimage.kt');
    const candidatePath = resolve(located.directory, 'candidate.kt');
    const [preimage, candidate] = await Promise.all([
      readFile(preimagePath).catch((error) => error?.code === 'ENOENT' ? null : Promise.reject(error)),
      readFile(candidatePath).catch((error) => error?.code === 'ENOENT' ? null : Promise.reject(error)),
    ]);
    const recoveryDurable = Boolean(
      storedRequest &&
      preimage &&
      candidate &&
      fingerprintSourceBytes(preimage) === request.project.preimage.sha256 &&
      fingerprintSourceBytes(candidate) === request.edit.candidate.sha256
    );
    const current = await exactTarget(projectRoot, request, null);
    const transactionId = fingerprintRepairValue({
      requestFingerprint,
      approval: request.authorization.confirmationSuffix,
    });
    const recovery = {
      requestSha256: request.requestFingerprint,
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      durable: recoveryDurable,
    };
    const journalPath = resolve(located.directory, 'journal.jsonl');
    const entries = await readJournal(journalPath, request);
    let journal = entries.at(-1) ?? null;
    if (!journal) {
      journal = journalEntry(
        request,
        transactionId,
        0,
        null,
        'PREPARED',
        current.sha256,
        recovery,
        null,
      );
      await appendJournal(journalPath, journal);
    }
    if (current.sha256 === request.project.preimage.sha256) {
      journal = journalEntry(
        request,
        transactionId,
        journal.sequence + 1,
        journal.entryFingerprint,
        'NOT_APPLIED',
        current.sha256,
        recovery,
        null,
      );
      await appendJournal(journalPath, journal);
      const receipt = receiptFor({
        request,
        operation: 'apply',
        status: 'not-applied',
        transactionId,
        approvalSuffix: request.authorization.confirmationSuffix,
        approvedAt: approval.approvedAt,
        terminalSha256: current.sha256,
        evidence: null,
        stateRoot: located.root,
        journal,
        recoveryDurable,
      });
      await durableJson(applyReceiptPath, receipt);
      return receipt;
    }
    if (current.sha256 === request.edit.candidate.sha256) {
      if (!recoveryDurable) {
        fail(
          'VC-AI-SOURCE-APPLICATION-RECOVERY-INVALID',
          'Candidate is present without complete durable recovery bytes.',
        );
      }
      if (journal.state !== 'APPLIED_UNVERIFIED') {
        journal = journalEntry(
          request,
          transactionId,
          journal.sequence + 1,
          journal.entryFingerprint,
          'APPLIED_UNVERIFIED',
          current.sha256,
          recovery,
          null,
        );
        await appendJournal(journalPath, journal);
      }
      return settleAppliedCandidate({
        located,
        projectRoot,
        transactionId,
        recovery,
        journal,
        journalPath,
        approval,
        verify,
        signal,
      });
    }
    const evidence = errorEvidence(current.sha256, 'conflict');
    journal = journalEntry(
      request,
      transactionId,
      journal.sequence + 1,
      journal.entryFingerprint,
      'APPLIED_CONFLICT',
      current.sha256,
      recovery,
      {
        status: evidence.status,
        sourceSha256: evidence.sourceSha256,
        evidenceFingerprint: evidence.evidenceFingerprint,
      },
    );
    await appendJournal(journalPath, journal);
    const receipt = receiptFor({
      request,
      operation: 'apply',
      status: 'applied-conflict',
      transactionId,
      approvalSuffix: request.authorization.confirmationSuffix,
      approvedAt: approval.approvedAt,
      terminalSha256: current.sha256,
      evidence,
      stateRoot: located.root,
      journal,
      recoveryDurable,
    });
    await durableJson(applyReceiptPath, receipt);
    return receipt;
  } finally {
    await releaseLock(lock);
  }
}

export async function rollbackPreparedSourceApplication({requestFingerprint, projectRoot} = {}, {
  stateRoot = defaultScreenshotSourceStateRoot(),
  confirm = confirmFromControllingTty,
  replaceSource = secureReplaceSource,
  signal,
} = {}) {
  const located = await locateBundle(requestFingerprint, projectRoot, stateRoot);
  const request = located.bundle.request;
  const existingRollback = await readOptionalJson(resolve(located.directory, 'rollback-receipt.json'));
  if (existingRollback) {
    validateReceipt(existingRollback, request, 'rollback');
    await exactTarget(projectRoot, request, request.project.preimage.sha256);
    return existingRollback;
  }
  const applyReceipt = validateReceipt(
    await readOptionalJson(resolve(located.directory, 'apply-receipt.json')),
    request,
    'apply',
  );
  if (!applyReceipt.rollback.eligible) {
    fail('VC-AI-SOURCE-ROLLBACK-RECEIPT-INVALID', 'Apply receipt cannot authorize rollback.');
  }
  const suffix = fingerprintRepairValue({
    operation: 'rollback',
    applyReceiptFingerprint: applyReceipt.receiptFingerprint,
  }).slice(0, 12).toUpperCase();
  if (!await confirm({
    operation: 'rollback',
    suffix,
    summary: `Rollback ${request.project.relativePath}\nApply receipt: ${applyReceipt.receiptFingerprint}`,
  })) {
    fail('VC-AI-SOURCE-ROLLBACK-AUTHORIZATION-DENIED', 'Exact rollback confirmation was not provided.');
  }
  const lock = await acquireLock(located.directory);
  try {
    const approval = approvalRecord(request, 'rollback', suffix);
    await durableJson(resolve(located.directory, 'rollback-approval.json'), approval);
    await exactTarget(projectRoot, request, request.edit.candidate.sha256);
    const preimagePath = resolve(located.directory, 'preimage.kt');
    const preimage = await readFile(preimagePath);
    if (fingerprintSourceBytes(preimage) !== request.project.preimage.sha256) {
      fail('VC-AI-SOURCE-ROLLBACK-RECOVERY-INVALID', 'Recovery preimage changed.');
    }
    const transactionId = applyReceipt.transactionId;
    const recovery = {
      requestSha256: request.requestFingerprint,
      preimageSha256: request.project.preimage.sha256,
      candidateSha256: request.edit.candidate.sha256,
      durable: true,
    };
    const journalPath = resolve(located.directory, 'rollback-journal.jsonl');
    let journal = journalEntry(request, transactionId, 0, null, 'ROLLING_BACK', request.edit.candidate.sha256, recovery, null);
    await appendJournal(journalPath, journal);
    await replaceSource({
      projectRoot: await realpath(projectRoot),
      relativePath: request.project.relativePath,
      expectedSha256: request.edit.candidate.sha256,
      candidatePath: preimagePath,
      candidateSha256: request.project.preimage.sha256,
      temporaryName: `.viewcompose-${request.nonce}.tmp`,
    }, {signal});
    await exactTarget(projectRoot, request, request.project.preimage.sha256);
    journal = journalEntry(request, transactionId, 1, journal.entryFingerprint, 'ROLLED_BACK', request.project.preimage.sha256, recovery, null);
    await appendJournal(journalPath, journal);
    const receipt = receiptFor({
      request,
      operation: 'rollback',
      status: 'rolled-back',
      transactionId,
      approvalSuffix: suffix,
      approvedAt: approval.approvedAt,
      terminalSha256: request.project.preimage.sha256,
      evidence: null,
      stateRoot: located.root,
      journal,
      linkedReceipt: applyReceipt.receiptFingerprint,
    });
    await durableJson(resolve(located.directory, 'rollback-receipt.json'), receipt);
    return receipt;
  } finally {
    await releaseLock(lock);
  }
}

export const SOURCE_TRANSACTION_HELPER_PATH = fileURLToPath(
  new URL('../harness/source-repair/ViewComposeSourceRepairHost.java', import.meta.url),
);
