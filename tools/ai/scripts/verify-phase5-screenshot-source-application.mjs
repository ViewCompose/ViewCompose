#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {canonicalJson} from './screenshot-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const contractRoot = resolve(aiRoot, 'contracts');
const exampleRoot = resolve(contractRoot, 'examples');
const fixturePath = resolve(
  aiRoot,
  'evaluation/fixtures/visual/screenshot-source-application-contract.json',
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function setPath(value, path, replacement) {
  const copy = structuredClone(value);
  let target = copy;
  for (const segment of path.slice(0, -1)) target = target[segment];
  target[path.at(-1)] = structuredClone(replacement);
  return copy;
}

function requireSchema(value, schema, label) {
  const violations = validateSchemaValue(value, schema);
  if (violations.length > 0) {
    throw new Error(`${label} violates its frozen schema: ${violations.slice(0, 3).join('; ')}`);
  }
}

function requireUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} must be unique.`);
}

export async function verifyPhase5ScreenshotSourceApplication() {
  const [fixture, requestSchema, journalSchema, receiptSchema, request, journal, receipt] =
    await Promise.all([
      readJson(fixturePath),
      readJson(resolve(contractRoot, 'screenshot-source-application-request.schema.json')),
      readJson(resolve(contractRoot, 'screenshot-source-application-journal.schema.json')),
      readJson(resolve(contractRoot, 'screenshot-source-application-receipt.schema.json')),
      readJson(resolve(exampleRoot, 'screenshot-source-application-request.json')),
      readJson(resolve(exampleRoot, 'screenshot-source-application-journal.json')),
      readJson(resolve(exampleRoot, 'screenshot-source-application-receipt.json')),
    ]);

  if (
    fixture.contractId !== 'viewcompose-attended-screenshot-source-application-v1' ||
    canonicalJson(fixture.schemas) !== canonicalJson([
      'screenshot-source-application-request-v1',
      'screenshot-source-application-journal-v1',
      'screenshot-source-application-receipt-v1',
    ])
  ) {
    throw new Error('Screenshot source-application contract identity drifted.');
  }
  requireSchema(request, requestSchema, 'Source-application request example');
  requireSchema(journal, journalSchema, 'Source-application journal example');
  requireSchema(receipt, receiptSchema, 'Source-application receipt example');
  if (
    request.requestFingerprint !== fingerprintWithout(request, 'requestFingerprint') ||
    journal.entryFingerprint !== fingerprintWithout(journal, 'entryFingerprint') ||
    receipt.receiptFingerprint !== fingerprintWithout(receipt, 'receiptFingerprint')
  ) {
    throw new Error('Source-application examples are not bound to their canonical content.');
  }
  const lifetime = (Date.parse(request.expiresAt) - Date.parse(request.createdAt)) / 1000;
  if (lifetime <= 0 || lifetime > fixture.limits.maxRequestLifetimeSeconds) {
    throw new Error('Source-application request lifetime exceeds the frozen bound.');
  }
  if (
    journal.requestFingerprint !== request.requestFingerprint ||
    receipt.requestFingerprint !== request.requestFingerprint ||
    journal.transactionId !== receipt.transactionId ||
    request.project.preimage.sha256 !== receipt.project.preimageSha256 ||
    request.edit.candidate.sha256 !== receipt.project.candidateSha256
  ) {
    throw new Error('Source-application request, journal, and receipt lineage diverged.');
  }

  requireUnique(fixture.schemaRejections.map((item) => item.id), 'Schema rejection ids');
  requireUnique(fixture.runtimeRejections.map((item) => item.id), 'Runtime rejection ids');
  requireUnique(fixture.runtimeRejections.map((item) => item.code), 'Runtime rejection codes');
  requireUnique(fixture.crashBoundaries, 'Crash boundaries');
  for (const mutation of fixture.schemaRejections) {
    const mutated = setPath(request, mutation.path, mutation.value);
    if (validateSchemaValue(mutated, requestSchema).length === 0) {
      throw new Error(`Schema mutation ${mutation.id} was accepted.`);
    }
    if (!/^VC-AI-SOURCE-(?:APPLICATION|ROLLBACK)-[A-Z0-9-]+$/u.test(mutation.code)) {
      throw new Error(`Schema mutation ${mutation.id} has an invalid diagnostic code.`);
    }
  }
  if (
    fixture.runtimeRejections.some((item) => item.sourceWrites !== 0) ||
    canonicalJson(fixture.recoveryOutcomes) !== canonicalJson([
      'NOT_APPLIED',
      'APPLIED_UNVERIFIED',
      'APPLIED_CONFLICT',
    ]) ||
    fixture.limits.maxFiles !== 1 ||
    fixture.limits.maxEdits !== 1 ||
    fixture.limits.mcpSourceWrite !== false ||
    fixture.limits.networkAccess !== false ||
    fixture.limits.providerCalls !== false ||
    fixture.limits.consumerBuildExecution !== false
  ) {
    throw new Error('Screenshot source-application safety denominator drifted.');
  }

  return {
    schemas: 3,
    positiveExamples: 3,
    schemaRejections: fixture.schemaRejections.length,
    runtimeRejections: fixture.runtimeRejections.length,
    crashBoundaries: fixture.crashBoundaries.length,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotSourceApplication().then((result) => {
    process.stdout.write(
      `Verified screenshot source-application freeze: ${result.schemas}/3 schemas, ` +
      `${result.positiveExamples}/3 examples, ${result.schemaRejections} schema rejections, ` +
      `${result.runtimeRejections} runtime rejections, and ` +
      `${result.crashBoundaries} crash boundaries.\n`,
    );
  }).catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
