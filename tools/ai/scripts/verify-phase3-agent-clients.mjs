#!/usr/bin/env node
import {mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  AGENT_CLIENT_PROFILES,
  installAgentClientSkills,
  renderAgentClientConfig,
} from './agent-client-integration.mjs';
import {assertSchemaValue} from './schema-validator.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const schemaPath = resolve(aiRoot, 'contracts/agent-client-integration.schema.json');
const examplePath = resolve(aiRoot, 'contracts/examples/agent-client-integration.json');
const mcpServerPath = resolve(aiRoot, 'scripts/mcp-server.mjs');
const sourceRoot = resolve(aiRoot, '../..');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function verifyProfileContract(contract) {
  const ids = Object.keys(AGENT_CLIENT_PROFILES);
  if (JSON.stringify(contract.clients.map((client) => client.id)) !== JSON.stringify(ids)) {
    throw new Error('Agent client order or membership drifted from the executable profiles.');
  }
  for (const client of contract.clients) {
    const profile = AGENT_CLIENT_PROFILES[client.id];
    if (
      client.displayName !== profile.displayName ||
      client.config.format !== profile.configFormat ||
      client.config.projectPath !== profile.configPath ||
      client.skills.projectPath !== profile.skillRoot ||
      client.config.generatorArguments[2] !== client.id ||
      client.skills.installArguments[2] !== client.id
    ) {
      throw new Error(`Agent client profile drifted for ${client.id}.`);
    }
    const config = renderAgentClientConfig(client.id, sourceRoot, {
      nodeExecutable: process.execPath,
      mcpServerPath,
    });
    if (profile.configFormat === 'json') {
      const parsed = JSON.parse(config);
      if (
        parsed.mcpServers?.viewcompose?.command !== process.execPath ||
        parsed.mcpServers?.viewcompose?.args?.[0] !== mcpServerPath ||
        parsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_SOURCE_ROOT !== sourceRoot
      ) throw new Error(`Generated JSON MCP configuration drifted for ${client.id}.`);
    } else if (
      !config.includes('[mcp_servers.viewcompose]') ||
      !config.includes('[mcp_servers.viewcompose.env]') ||
      !config.includes(JSON.stringify(mcpServerPath)) ||
      !config.includes(JSON.stringify(sourceRoot))
    ) {
      throw new Error('Generated Codex TOML configuration drifted.');
    }
  }
}

export async function verifyAgentClientIntegration() {
  const [schema, contract, manifest] = await Promise.all([
    readJson(schemaPath),
    readJson(examplePath),
    readJson(resolve(aiRoot, 'skills/manifest.json')),
  ]);
  assertSchemaValue(contract, schema, 'Agent client integration example');
  verifyProfileContract(contract);
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-clients-')));
  let conflictRejected = false;
  let relativeRejected = false;
  let symlinkRejected = false;
  try {
    for (const client of contract.clients) {
      const projectRoot = resolve(temporary, client.id);
      await mkdir(projectRoot);
      const first = await installAgentClientSkills({client: client.id, projectRoot, aiRoot});
      if (
        JSON.stringify(first.installed) !== JSON.stringify(manifest.skills.map((skill) => skill.id)) ||
        first.unchanged.length !== 0
      ) throw new Error(`Initial Skill installation drifted for ${client.id}.`);
      for (const skill of manifest.skills) {
        const installed = await readFile(resolve(projectRoot, first.skillRoot, skill.id, 'SKILL.md'));
        const canonical = await readFile(resolve(aiRoot, skill.path));
        if (!installed.equals(canonical)) throw new Error(`Installed Skill bytes drifted for ${skill.id}.`);
      }
      const second = await installAgentClientSkills({client: client.id, projectRoot, aiRoot});
      if (second.installed.length !== 0 || second.unchanged.length !== manifest.skills.length) {
        throw new Error(`Idempotent Skill installation drifted for ${client.id}.`);
      }
    }

    const conflictRoot = resolve(temporary, 'conflict');
    await mkdir(conflictRoot);
    const installed = await installAgentClientSkills({client: 'codex', projectRoot: conflictRoot, aiRoot});
    await writeFile(
      resolve(conflictRoot, installed.skillRoot, installed.installed[0], 'SKILL.md'),
      'conflict\n',
    );
    try {
      await installAgentClientSkills({client: 'codex', projectRoot: conflictRoot, aiRoot});
    } catch (error) {
      conflictRejected = /Refusing to overwrite conflicting Skill bytes/u.test(error.message);
    }
    try {
      await installAgentClientSkills({client: 'codex', projectRoot: 'relative', aiRoot});
    } catch (error) {
      relativeRejected = /absolute path/u.test(error.message);
    }
    const physical = resolve(temporary, 'physical');
    const linked = resolve(temporary, 'linked');
    await mkdir(physical);
    await symlink(physical, linked, 'dir');
    try {
      await installAgentClientSkills({client: 'cursor', projectRoot: linked, aiRoot});
    } catch (error) {
      symlinkRejected = /symbolic link/u.test(error.message);
    }
    if (!conflictRejected || !relativeRejected || !symlinkRejected) {
      throw new Error('Agent Skill installer did not fail closed for every safety denominator.');
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
  return {
    profiles: contract.clients.length,
    skills: manifest.skills.length,
    installedSkillComparisons: contract.clients.length * manifest.skills.length,
    idempotentReinstalls: contract.clients.length,
    safetyRejections: 3,
  };
}

async function main() {
  const result = await verifyAgentClientIntegration();
  process.stdout.write(
    `Verified ViewCompose AI agent clients: ${result.profiles}/3 profiles, ` +
    `${result.installedSkillComparisons}/${result.installedSkillComparisons} exact Skill copies, ` +
    `${result.idempotentReinstalls}/3 idempotent reinstalls, and ` +
    `${result.safetyRejections}/3 safety rejections.\n`,
  );
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose AI agent client verification failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
