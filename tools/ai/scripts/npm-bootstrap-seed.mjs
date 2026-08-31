#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {cp, lstat, mkdir, readFile, readdir, writeFile} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

export const STABLE_VERSION = '0.4.0';
export const BOOTSTRAP_VERSION = '0.4.0-bootstrap.0';

export const VERSION_REPLACEMENTS = Object.freeze(new Map([
  ['contracts/ai-tooling-release.schema.json', 3],
  ['contracts/bootstrap.schema.json', 1],
  ['contracts/consumer-project-execution.schema.json', 1],
  ['contracts/examples/agent-client-integration.json', 1],
  ['contracts/examples/ai-tooling-release.json', 3],
  ['contracts/examples/bootstrap.json', 1],
  ['contracts/examples/consumer-project-execution.json', 1],
  ['distribution.json', 1],
  ['package.json', 1],
  ['sbom.spdx.json', 4],
  ['third-party-licenses.json', 1],
]));

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function contained(root, candidate) {
  const path = relative(resolve(root), resolve(candidate));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

async function metadataOrNull(path) {
  try {
    return await lstat(path);
  } catch (error) {
    if (error?.code === 'ENOENT') return null;
    throw error;
  }
}

async function regularFiles(root, current = root) {
  const files = [];
  for (const name of (await readdir(current)).sort()) {
    const path = resolve(current, name);
    if (!contained(root, path)) throw new Error(`Package path escapes its root: ${path}`);
    const metadata = await lstat(path);
    if (metadata.isSymbolicLink()) throw new Error(`Package contains a symbolic link: ${path}`);
    if (metadata.isDirectory()) {
      files.push(...await regularFiles(root, path));
    } else if (metadata.isFile()) {
      files.push(path);
    } else {
      throw new Error(`Package contains a non-regular entry: ${path}`);
    }
  }
  return files;
}

async function snapshot(root) {
  const entries = new Map();
  for (const path of await regularFiles(root)) {
    const relativePath = relative(root, path).split(sep).join('/');
    const content = await readFile(path);
    entries.set(relativePath, {content, sha256: sha256(content)});
  }
  return entries;
}

function occurrences(value, needle) {
  return value.split(needle).length - 1;
}

function requireSameInventory(stable, seed) {
  const stablePaths = [...stable.keys()].sort();
  const seedPaths = [...seed.keys()].sort();
  if (JSON.stringify(stablePaths) !== JSON.stringify(seedPaths)) {
    throw new Error('Bootstrap seed file inventory differs from the frozen stable package.');
  }
}

function verifyIdentity(seedRoot, seed) {
  const packageMetadata = JSON.parse(seed.get('package.json').content.toString('utf8'));
  const distribution = JSON.parse(seed.get('distribution.json').content.toString('utf8'));
  if (
    packageMetadata.name !== '@viewcompose/ai-tooling' ||
    packageMetadata.version !== BOOTSTRAP_VERSION ||
    packageMetadata.private === true ||
    packageMetadata.scripts !== undefined ||
    packageMetadata.publishConfig?.access !== 'public'
  ) {
    throw new Error('Bootstrap seed npm metadata violates the frozen public package boundary.');
  }
  if (
    distribution.package?.name !== '@viewcompose/ai-tooling' ||
    distribution.package?.version !== BOOTSTRAP_VERSION
  ) {
    throw new Error('Bootstrap seed distribution identity is inconsistent.');
  }
  for (const [path, expected] of VERSION_REPLACEMENTS) {
    const content = seed.get(path)?.content.toString('utf8');
    if (content === undefined || occurrences(content, BOOTSTRAP_VERSION) !== expected) {
      throw new Error(`Bootstrap seed version replacement count drifted for ${path}.`);
    }
    JSON.parse(content);
  }
  return {root: seedRoot, files: seed.size};
}

export async function verifyBootstrapSeed({stableRoot, seedRoot}) {
  const stable = await snapshot(resolve(stableRoot));
  const seed = await snapshot(resolve(seedRoot));
  requireSameInventory(stable, seed);
  for (const [path, stableEntry] of stable) {
    const seedEntry = seed.get(path);
    const expectedReplacements = VERSION_REPLACEMENTS.get(path);
    if (expectedReplacements === undefined) {
      if (seedEntry.sha256 !== stableEntry.sha256) {
        throw new Error(`Bootstrap seed changed a non-version file: ${path}`);
      }
      continue;
    }
    const stableText = stableEntry.content.toString('utf8');
    if (occurrences(stableText, STABLE_VERSION) !== expectedReplacements) {
      throw new Error(`Frozen stable version occurrence count drifted for ${path}.`);
    }
    const expected = Buffer.from(stableText.replaceAll(STABLE_VERSION, BOOTSTRAP_VERSION));
    if (!seedEntry.content.equals(expected)) {
      throw new Error(`Bootstrap seed contains a non-version change in ${path}.`);
    }
  }
  return verifyIdentity(resolve(seedRoot), seed);
}

export async function createBootstrapSeed({stableRoot, seedRoot}) {
  const source = resolve(stableRoot);
  const target = resolve(seedRoot);
  const sourceMetadata = await lstat(source);
  if (!sourceMetadata.isDirectory() || sourceMetadata.isSymbolicLink()) {
    throw new Error('Stable package root must be a physical directory.');
  }
  if (source === target || contained(source, target) || contained(target, source)) {
    throw new Error('Bootstrap seed roots must be separate, non-nested directories.');
  }
  if (await metadataOrNull(target)) throw new Error('Bootstrap seed output already exists.');
  const stable = await snapshot(source);
  for (const [path, expected] of VERSION_REPLACEMENTS) {
    const entry = stable.get(path);
    if (!entry || occurrences(entry.content.toString('utf8'), STABLE_VERSION) !== expected) {
      throw new Error(`Frozen stable version occurrence count drifted for ${path}.`);
    }
  }
  await mkdir(dirname(target), {recursive: true});
  await cp(source, target, {recursive: true, errorOnExist: true, force: false, preserveTimestamps: true});
  for (const [path] of VERSION_REPLACEMENTS) {
    const targetPath = resolve(target, path);
    const content = await readFile(targetPath, 'utf8');
    await writeFile(targetPath, content.replaceAll(STABLE_VERSION, BOOTSTRAP_VERSION));
  }
  return verifyBootstrapSeed({stableRoot: source, seedRoot: target});
}

function parseArguments(arguments_) {
  const [mode, ...rest] = arguments_;
  const values = new Map();
  for (let index = 0; index < rest.length; index += 2) {
    const name = rest[index];
    const value = rest[index + 1];
    if (!name?.startsWith('--') || value === undefined) {
      throw new Error('Usage: npm-bootstrap-seed.mjs <create|verify> --stable-root <path> --seed-root <path>');
    }
    values.set(name, value);
  }
  if (!['create', 'verify'].includes(mode) || !values.has('--stable-root') || !values.has('--seed-root')) {
    throw new Error('Usage: npm-bootstrap-seed.mjs <create|verify> --stable-root <path> --seed-root <path>');
  }
  return {mode, stableRoot: values.get('--stable-root'), seedRoot: values.get('--seed-root')};
}

async function main() {
  const arguments_ = parseArguments(process.argv.slice(2));
  const result = arguments_.mode === 'create'
    ? await createBootstrapSeed(arguments_)
    : await verifyBootstrapSeed(arguments_);
  process.stdout.write(`${JSON.stringify({
    stableVersion: STABLE_VERSION,
    bootstrapVersion: BOOTSTRAP_VERSION,
    changedFiles: [...VERSION_REPLACEMENTS.keys()],
    ...result,
  }, null, 2)}\n`);
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose npm bootstrap seed rejected the package: ${error.message}\n`);
    process.exitCode = 2;
  });
}
