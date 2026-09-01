#!/usr/bin/env node
import {realpathSync} from 'node:fs';
import {isAbsolute, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  applyPreparedSourceApplication,
  inspectPreparedSourceApplication,
  recoverPreparedSourceApplication,
  rollbackPreparedSourceApplication,
} from './screenshot-source-transaction.mjs';

const SHA256 = /^[a-f0-9]{64}$/u;
const COMMANDS = new Set(['show', 'status', 'apply', 'recover', 'rollback']);

export function parseScreenshotSourceRepairArguments(arguments_, cwd = process.cwd()) {
  const values = [...arguments_];
  const command = values.shift();
  const requestFingerprint = values.shift();
  if (!COMMANDS.has(command) || !SHA256.test(requestFingerprint ?? '')) {
    throw new Error(
      'Usage: viewcompose-repair <show|status|apply|recover|rollback> <request-fingerprint> [--project-root <absolute-path>] [--pretty]',
    );
  }
  let projectRoot = cwd;
  let projectRootProvided = false;
  let pretty = false;
  while (values.length > 0) {
    const flag = values.shift();
    if (flag === '--pretty' && !pretty) {
      pretty = true;
    } else if (flag === '--project-root' && values.length > 0 && !projectRootProvided) {
      projectRoot = values.shift();
      projectRootProvided = true;
    } else {
      throw new Error(`Unsupported argument ${flag ?? '<missing>'}; approval bypasses are forbidden.`);
    }
  }
  if (!isAbsolute(projectRoot)) {
    throw new Error('The project root must be absolute.');
  }
  return {command, requestFingerprint, projectRoot: realpathSync(projectRoot), pretty};
}

export async function runScreenshotSourceRepairCli(arguments_, options = {}) {
  const parsed = parseScreenshotSourceRepairArguments(arguments_, options.cwd);
  const input = {
    requestFingerprint: parsed.requestFingerprint,
    projectRoot: parsed.projectRoot,
  };
  let result;
  switch (parsed.command) {
    case 'show':
    case 'status':
      result = await inspectPreparedSourceApplication(input, options);
      if (parsed.command === 'status') {
        result = {
          schemaVersion: result.schemaVersion,
          status: result.status,
          requestFingerprint: result.requestFingerprint,
          project: result.project,
          receiptFingerprint: result.receiptFingerprint,
        };
      }
      break;
    case 'apply':
      result = await applyPreparedSourceApplication(input, options);
      break;
    case 'recover':
      result = await recoverPreparedSourceApplication(input, options);
      break;
    case 'rollback':
      result = await rollbackPreparedSourceApplication(input, options);
      break;
    default:
      throw new Error('Unreachable source-repair command.');
  }
  return {result, pretty: parsed.pretty};
}

async function main() {
  const {result, pretty} = await runScreenshotSourceRepairCli(process.argv.slice(2));
  process.stdout.write(`${JSON.stringify(result, null, pretty ? 2 : 0)}\n`);
}

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    const code = typeof error?.code === 'string'
      ? error.code
      : 'VC-AI-SOURCE-APPLICATION-CLI-REJECTED';
    process.stderr.write(`${code}: ${error.message}\n`);
    process.exitCode = 2;
  });
}
