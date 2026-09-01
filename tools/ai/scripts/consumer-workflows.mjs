import {lstat, readFile} from 'node:fs/promises';
import {relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {TOOL_NAMES} from './tool-catalog.mjs';

const defaultAiRoot = fileURLToPath(new URL('../', import.meta.url));
const defaultExpectedPath = resolve(
  defaultAiRoot,
  'evaluation/fixtures/workflows/consumer-skills.json',
);
const defaultManifestPath = resolve(defaultAiRoot, 'skills/manifest.json');
const evidenceLevels = ['knowledge', 'static', 'compiled', 'rendered', 'compared'];
const mutationPolicies = new Set([
  'read-only',
  'user-requested-project-writes',
  'read-only-unless-fix-requested',
]);
const providerNames = /\b(?:chatgpt|claude|codex|cursor|gemini)\b/iu;
const localAbsolutePath = /(?:^|[\s(])\/(?:Users|home|private|tmp)\//mu;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function unique(values, label) {
  if (!Array.isArray(values) || new Set(values).size !== values.length) {
    throw new Error(`${label} must be a unique array.`);
  }
  return values;
}

function normalizedWorkflow(entry) {
  if (!isObject(entry) || typeof entry.id !== 'string') throw new Error('Workflow entry is invalid.');
  return {
    id: entry.id,
    requiredTools: entry.requiredTools,
    conditionalTools: entry.conditionalTools,
    minimumEvidence: entry.minimumEvidence,
    maximumEvidence: entry.maximumEvidence,
    mutationPolicy: entry.mutationPolicy,
  };
}

function validateWorkflow(entry, {requiresPath}) {
  if (!/^[a-z][a-z0-9-]{2,63}$/u.test(entry.id ?? '')) {
    throw new Error(`Invalid consumer workflow id: ${entry.id ?? '<missing>'}.`);
  }
  const required = unique(entry.requiredTools, `${entry.id}.requiredTools`);
  const conditional = unique(entry.conditionalTools, `${entry.id}.conditionalTools`);
  const overlap = required.filter((tool) => conditional.includes(tool));
  if (overlap.length > 0) throw new Error(`${entry.id} repeats tools across required and conditional sets.`);
  for (const tool of [...required, ...conditional]) {
    if (!TOOL_NAMES.includes(tool)) throw new Error(`${entry.id} references unknown tool ${tool}.`);
  }
  const minimum = evidenceLevels.indexOf(entry.minimumEvidence);
  const maximum = evidenceLevels.indexOf(entry.maximumEvidence);
  if (minimum < 0 || maximum < minimum || maximum > evidenceLevels.indexOf('compared')) {
    throw new Error(`${entry.id} has an invalid consumer evidence range.`);
  }
  if (!mutationPolicies.has(entry.mutationPolicy)) {
    throw new Error(`${entry.id} has an invalid mutation policy.`);
  }
  if (requiresPath && entry.path !== `skills/${entry.id}/SKILL.md`) {
    throw new Error(`${entry.id} must use its independently installable SKILL.md path.`);
  }
}

export function compareWorkflowContracts(expected, manifest) {
  if (
    expected?.schemaVersion !== 1 ||
    manifest?.schemaVersion !== 1 ||
    expected.workflowSetId !== manifest.workflowSetId ||
    expected.versionLane !== 'current-source' ||
    manifest.versionLane !== expected.versionLane ||
    JSON.stringify(expected.sharedInvariants) !== JSON.stringify(manifest.sharedInvariants)
  ) throw new Error('Consumer workflow set identity or shared invariants drifted.');
  unique(expected.sharedInvariants, 'expected.sharedInvariants');
  const expectedWorkflows = unique(expected.workflows.map((entry) => entry.id), 'expected workflows');
  const actualSkills = unique(manifest.skills.map((entry) => entry.id), 'consumer skills');
  if (JSON.stringify(expectedWorkflows) !== JSON.stringify(actualSkills)) {
    throw new Error('Consumer skill order or membership drifted from the frozen workflow set.');
  }
  let exactMatches = 0;
  for (let index = 0; index < expected.workflows.length; index += 1) {
    const expectedEntry = expected.workflows[index];
    const actualEntry = manifest.skills[index];
    validateWorkflow(expectedEntry, {requiresPath: false});
    validateWorkflow(actualEntry, {requiresPath: true});
    if (JSON.stringify(normalizedWorkflow(expectedEntry)) === JSON.stringify(normalizedWorkflow(actualEntry))) {
      exactMatches += 1;
    }
  }
  return {
    workflows: expected.workflows.length,
    exactMatches,
    exactMatchRatio: exactMatches / expected.workflows.length,
  };
}

function frontMatter(markdown) {
  const match = /^---\n([\s\S]*?)\n---\n/u.exec(markdown);
  if (!match) throw new Error('SKILL.md requires YAML frontmatter.');
  const fields = new Map();
  for (const line of match[1].split('\n')) {
    const field = /^([a-z_]+):\s+(.+)$/u.exec(line);
    if (field) fields.set(field[1], field[2].trim());
  }
  return {fields, body: markdown.slice(match[0].length)};
}

export function validateSkillMarkdown(skill, markdown) {
  if (typeof markdown !== 'string' || Buffer.byteLength(markdown, 'utf8') > 16 * 1024) {
    throw new Error(`${skill.id} SKILL.md is empty or exceeds 16 KiB.`);
  }
  const {fields, body} = frontMatter(markdown);
  if (fields.get('name') !== skill.id) throw new Error(`${skill.id} frontmatter name drifted.`);
  const description = fields.get('description');
  if (!description || description.length > 1024 || !description.includes('ViewCompose')) {
    throw new Error(`${skill.id} requires one discriminating ViewCompose description.`);
  }
  if (!body.includes('## Exact version and evidence') || !body.includes('## Stop and authority')) {
    throw new Error(`${skill.id} omits the shared evidence or authority boundary.`);
  }
  for (const tool of [...skill.requiredTools, ...skill.conditionalTools]) {
    if (!body.includes(`\`${tool}\``)) throw new Error(`${skill.id} does not explain ${tool}.`);
  }
  const invariantPatterns = [
    /exact (?:framework|version)/iu,
    /evidence/iu,
    /(?:fabricat|guess|substitute)/iu,
    /same\s+diagnostic\s+repeats\s+without\s+new\s+evidence/iu,
    /(?:read-only|authoriz|authority)/iu,
  ];
  if (invariantPatterns.some((pattern) => !pattern.test(body))) {
    throw new Error(`${skill.id} does not express every shared safety invariant.`);
  }
  if (providerNames.test(markdown)) throw new Error(`${skill.id} is provider-specific.`);
  if (localAbsolutePath.test(markdown)) throw new Error(`${skill.id} contains a local absolute path.`);
}

function isWithin(parent, child) {
  const path = relative(parent, child);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !path.startsWith(sep));
}

async function readContainedSkill(path, aiRoot) {
  const candidate = resolve(aiRoot, path);
  if (!isWithin(resolve(aiRoot, 'skills'), candidate)) throw new Error(`Skill path escapes: ${path}.`);
  let current = aiRoot;
  for (const segment of relative(aiRoot, candidate).split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    const metadata = await lstat(current);
    if (metadata.isSymbolicLink()) throw new Error(`Skill path traverses a symbolic link: ${path}.`);
  }
  if (!(await lstat(candidate)).isFile()) throw new Error(`Skill path is not a file: ${path}.`);
  return readFile(candidate, 'utf8');
}

export async function verifyConsumerWorkflows({
  aiRoot = defaultAiRoot,
  expectedPath = defaultExpectedPath,
  manifestPath = defaultManifestPath,
} = {}) {
  const expected = JSON.parse(await readFile(expectedPath, 'utf8'));
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  const comparison = compareWorkflowContracts(expected, manifest);
  for (const skill of manifest.skills) {
    validateSkillMarkdown(skill, await readContainedSkill(skill.path, aiRoot));
  }
  return comparison;
}
