#!/usr/bin/env node
import {mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  AGENT_CLIENT_PROFILES,
  diagnoseAgentClient,
  initializeAgentClient,
  installAgentClientSkills,
  renderAgentClientConfig,
  uninstallAgentClient,
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
      client.config.projectBoundArguments[2] !== client.id ||
      client.config.sourceBoundArguments[2] !== client.id ||
      client.lifecycle.initArguments[2] !== client.id ||
      client.lifecycle.doctorArguments[2] !== client.id ||
      client.lifecycle.uninstallArguments[2] !== client.id
    ) {
      throw new Error(`Agent client profile drifted for ${client.id}.`);
    }
    const projectRoot = '/workspace/viewcompose-consumer';
    const standalone = renderAgentClientConfig(client.id, projectRoot, {
      nodeExecutable: process.execPath,
      mcpServerPath,
    });
    const sourceBound = renderAgentClientConfig(client.id, projectRoot, {
      sourceRoot,
      nodeExecutable: process.execPath,
      mcpServerPath,
    });
    if (profile.configFormat === 'json') {
      const standaloneParsed = JSON.parse(standalone);
      const parsed = JSON.parse(sourceBound);
      if (
        standaloneParsed.mcpServers?.viewcompose?.command !== process.execPath ||
        standaloneParsed.mcpServers?.viewcompose?.args?.[0] !== mcpServerPath ||
        standaloneParsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_PROJECT_ROOT !== projectRoot ||
        parsed.mcpServers?.viewcompose?.command !== process.execPath ||
        parsed.mcpServers?.viewcompose?.args?.[0] !== mcpServerPath ||
        parsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_PROJECT_ROOT !== projectRoot ||
        parsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_SOURCE_ROOT !== sourceRoot
      ) throw new Error(`Generated JSON MCP configuration drifted for ${client.id}.`);
    } else if (
      !standalone.includes('[mcp_servers.viewcompose]') ||
      !standalone.includes('[mcp_servers.viewcompose.env]') ||
      !standalone.includes(JSON.stringify(projectRoot)) ||
      !sourceBound.includes('[mcp_servers.viewcompose.env]') ||
      !sourceBound.includes(JSON.stringify(mcpServerPath)) ||
      !sourceBound.includes(JSON.stringify(sourceRoot))
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
      const first = await initializeAgentClient({
        client: client.id,
        projectRoot,
        aiRoot,
        nodeExecutable: process.execPath,
        mcpServerPath,
      });
      if (
        first.mode !== 'project-bound' ||
        first.config.status !== 'installed' ||
        JSON.stringify(first.skills.installed) !== JSON.stringify(manifest.skills.map((skill) => skill.id)) ||
        first.skills.unchanged.length !== 0
      ) throw new Error(`Initial standalone lifecycle drifted for ${client.id}.`);
      for (const skill of manifest.skills) {
        const installed = await readFile(resolve(projectRoot, first.skills.path, skill.id, 'SKILL.md'));
        const canonical = await readFile(resolve(aiRoot, skill.path));
        if (!installed.equals(canonical)) throw new Error(`Installed Skill bytes drifted for ${skill.id}.`);
      }
      const doctor = await diagnoseAgentClient({
        client: client.id,
        projectRoot,
        aiRoot,
        nodeExecutable: process.execPath,
        mcpServerPath,
      });
      if (
        doctor.status !== 'project-bound-ready' ||
        doctor.capabilities.compilationPreviewAndLayout !== 'project-bound-ready'
      ) throw new Error(`Standalone doctor evidence drifted for ${client.id}.`);
      const second = await initializeAgentClient({
        client: client.id,
        projectRoot,
        aiRoot,
        nodeExecutable: process.execPath,
        mcpServerPath,
      });
      if (
        second.config.status !== 'unchanged' ||
        second.skills.installed.length !== 0 ||
        second.skills.unchanged.length !== manifest.skills.length
      ) {
        throw new Error(`Idempotent lifecycle drifted for ${client.id}.`);
      }
      const removed = await uninstallAgentClient({
        client: client.id,
        projectRoot,
        aiRoot,
        nodeExecutable: process.execPath,
        mcpServerPath,
      });
      if (removed.config.status !== 'removed' || removed.skills.removed.length !== manifest.skills.length) {
        throw new Error(`Uninstall lifecycle drifted for ${client.id}.`);
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
    standaloneDoctors: contract.clients.length,
    cleanUninstalls: contract.clients.length,
    safetyRejections: 3,
  };
}

async function main() {
  const result = await verifyAgentClientIntegration();
  process.stdout.write(
    `Verified ViewCompose AI agent clients: ${result.profiles}/3 profiles, ` +
    `${result.installedSkillComparisons}/${result.installedSkillComparisons} exact Skill copies, ` +
    `${result.idempotentReinstalls}/3 idempotent reinstalls, and ` +
    `${result.standaloneDoctors}/3 standalone doctors, ` +
    `${result.cleanUninstalls}/3 clean uninstalls, and ` +
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
