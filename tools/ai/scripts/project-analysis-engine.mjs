import {readFile} from 'node:fs/promises';
import {diagnostic, loadKnowledgeManifest} from './tool-core.mjs';
import {assertSchemaValue} from './schema-validator.mjs';

const catalogUrl = new URL('../analysis/rules.json', import.meta.url);
const qualityUrl = new URL('../analysis/quality.json', import.meta.url);
const schemaUrl = new URL('../contracts/project-analysis.schema.json', import.meta.url);
const suppressionPattern = /^\s*\/\/\s*viewcompose-ai:suppress-next\s+(VC-AI-[A-Z0-9-]+)\s+--\s+(.+?)\s*$/u;

let contractsPromise;

export async function loadProjectAnalysisContracts() {
  contractsPromise ??= Promise.all([
    readFile(catalogUrl, 'utf8').then(JSON.parse),
    readFile(qualityUrl, 'utf8').then(JSON.parse),
    readFile(schemaUrl, 'utf8').then(JSON.parse),
    loadKnowledgeManifest(),
  ]).then(([catalog, quality, schema, manifest]) => {
    if (quality.catalogVersion !== catalog.catalogVersion) {
      throw new Error('Project-analysis catalog and quality snapshot versions differ.');
    }
    const rules = new Map(catalog.rules.map((rule) => [rule.ruleId, rule]));
    if (rules.size !== catalog.rules.length || quality.rules.some((entry) => !rules.has(entry.ruleId))) {
      throw new Error('Project-analysis catalog rule identities are inconsistent.');
    }
    return {catalog, quality, schema, manifest, rules};
  });
  return contractsPromise;
}

function lineSource(path, line, text) {
  const startColumn = Math.max(1, text.search(/\S/u) + 1);
  return {
    path,
    startLine: line,
    startColumn,
    endLine: line,
    endColumn: startColumn + Math.max(1, text.trim().length),
  };
}

export function parseSuppressionDirectives(source, path) {
  const directives = [];
  source.split('\n').forEach((line, index) => {
    const match = suppressionPattern.exec(line);
    if (!match) return;
    directives.push({
      ruleId: match[1],
      reason: match[2].trim(),
      source: lineSource(path, index + 1, line),
      used: false,
    });
  });
  return directives;
}

function compactRule(rule) {
  return {
    ruleId: rule.ruleId,
    ruleVersion: rule.ruleVersion,
    title: rule.title,
    family: rule.family,
    severity: rule.severity,
    confidence: rule.confidence,
    suppressible: rule.suppressible,
    mechanism: rule.mechanism,
    suggestion: rule.suggestion,
    evidence: rule.evidence,
  };
}

function compactQuality(entry) {
  return {
    ruleId: entry.ruleId,
    ruleVersion: entry.ruleVersion,
    positiveOpportunities: entry.positiveOpportunities,
    eligibleNegativeOpportunities: entry.eligibleNegativeOpportunities,
    unsupportedOpportunities: entry.unsupportedOpportunities,
    precision: entry.precision,
    recall: entry.recall,
  };
}

function frameworkIdentity(manifest) {
  return {
    versionLane: manifest.framework.versionLane,
    identity: manifest.framework.identity,
  };
}

function makeFinding({rule, framework, message, source, artifactId, capabilityId, suppression}) {
  return {
    ruleId: rule.ruleId,
    ruleVersion: rule.ruleVersion,
    severity: rule.severity,
    confidence: rule.confidence,
    message,
    mechanism: rule.mechanism,
    evidence: rule.evidence,
    suggestion: rule.suggestion,
    ...(artifactId ? {artifactId} : {}),
    ...(capabilityId ? {capabilityId} : {}),
    framework,
    source,
    suppression: suppression ?? {state: 'none'},
  };
}

function compareSource(left, right) {
  return left.source.path.localeCompare(right.source.path) ||
    left.source.startLine - right.source.startLine ||
    left.source.startColumn - right.source.startColumn ||
    left.ruleId.localeCompare(right.ruleId);
}

function inventoryFindings(inventory, contracts, framework) {
  const result = [];
  for (const entry of inventory.unknownImports) {
    result.push(makeFinding({
      rule: contracts.rules.get('VC-AI-PROJECT-UNKNOWN-IMPORT'),
      framework,
      message: `Import ${entry.importName} does not resolve to a namespace in the active exact Knowledge profile.`,
      source: {path: entry.path, startLine: entry.startLine, startColumn: entry.startColumn},
    }));
  }
  for (const entry of inventory.dependencies.filter((dependency) => dependency.expectedCurrentVersion === null)) {
    result.push(makeFinding({
      rule: contracts.rules.get('VC-AI-PROJECT-UNKNOWN-ARTIFACT'),
      framework,
      message: `Dependency com.viewcompose:${entry.artifactId} is absent from the active exact Knowledge profile.`,
      artifactId: entry.artifactId,
      source: {path: entry.path, startLine: entry.startLine, startColumn: entry.startColumn},
    }));
  }
  for (const artifact of inventory.artifacts.filter((entry) => !entry.declared)) {
    const usage = inventory.imports.find((entry) => entry.artifactIds.includes(artifact.artifactId));
    if (!usage) continue;
    result.push(makeFinding({
      rule: contracts.rules.get('VC-AI-ARTIFACT-REQUIRED'),
      framework,
      message: `Imported symbols are owned by ${artifact.artifactId}, but no exact dependency was found in the inspected files.`,
      artifactId: artifact.artifactId,
      source: {path: usage.path, startLine: usage.startLine, startColumn: usage.startColumn},
    }));
  }
  for (const entry of inventory.dependencies.filter(
    (dependency) => dependency.state === 'different-from-current-bundle',
  )) {
    result.push(makeFinding({
      rule: contracts.rules.get('VC-AI-PROJECT-VERSION-LANE'),
      framework,
      message: `Dependency com.viewcompose:${entry.artifactId}:${entry.version} differs from the active exact Knowledge profile.`,
      artifactId: entry.artifactId,
      source: {path: entry.path, startLine: entry.startLine, startColumn: entry.startColumn},
    }));
  }
  return result;
}

function applyImageSuppressions({opportunities, directives, unsupported, contracts, framework}) {
  const findings = [];
  const imageRule = contracts.rules.get('VC-AI-A11Y-IMAGE-DESCRIPTION');
  const orderedOpportunities = [...opportunities].sort(compareSource);
  const orderedDirectives = [...directives].sort((left, right) =>
    left.source.path.localeCompare(right.source.path) ||
    left.source.startLine - right.source.startLine ||
    left.source.startColumn - right.source.startColumn,
  );
  for (const directive of orderedDirectives) {
    const selectedRule = contracts.rules.get(directive.ruleId);
    if (!selectedRule || !selectedRule.suppressible) {
      unsupported.push({
        kind: 'invalid-suppression',
        reason: selectedRule
          ? `Rule ${directive.ruleId} cannot be suppressed.`
          : `Rule ${directive.ruleId} is absent from the active catalog.`,
        source: directive.source,
      });
    }
  }
  for (const opportunity of orderedOpportunities) {
    const directive = orderedDirectives.find((candidate) =>
      !candidate.used &&
      candidate.ruleId === imageRule.ruleId &&
      candidate.source.path === opportunity.source.path &&
      (candidate.source.startLine < opportunity.source.startLine ||
        (candidate.source.startLine === opportunity.source.startLine &&
          candidate.source.startColumn < opportunity.source.startColumn)),
    );
    if (directive) directive.used = true;
    if (!opportunity.missingContentDescription) continue;
    findings.push(makeFinding({
      rule: imageRule,
      framework,
      message: 'ViewCompose Image must declare contentDescription, including an explicit null for decorative content.',
      artifactId: opportunity.artifactId,
      capabilityId: opportunity.capabilityId,
      source: opportunity.source,
      suppression: directive ? {
        state: 'suppressed',
        reason: directive.reason,
        directive: directive.source,
      } : {state: 'none'},
    }));
  }
  return findings;
}

function profileMatch(inventory) {
  if (inventory.dependencies.length === 0) return 'empty';
  if (inventory.dependencies.some((entry) => entry.state === 'unresolved-expression')) return 'unresolved';
  if (inventory.dependencies.some(
    (entry) => entry.state !== 'current-bundle' || entry.expectedCurrentVersion === null,
  )) return 'different';
  return 'exact';
}

export function projectAnalysisDiagnostics(findings, contracts) {
  const unsuppressed = findings.filter((entry) => entry.suppression.state === 'none');
  const diagnostics = unsuppressed
    .filter((entry) => entry.ruleId !== 'VC-AI-PROJECT-VERSION-LANE')
    .map((entry) => diagnostic({
      code: entry.ruleId,
      severity: entry.severity,
      message: entry.message,
      nextAction: entry.suggestion,
      artifactId: entry.artifactId,
      capabilityId: entry.capabilityId,
      source: entry.source,
    }));
  const versions = unsuppressed
    .filter((entry) => entry.ruleId === 'VC-AI-PROJECT-VERSION-LANE')
    .map((entry) => /:([^:]+) differs from/u.exec(entry.message)?.[1])
    .filter(Boolean);
  if (versions.length > 0) {
    const rule = contracts.rules.get('VC-AI-PROJECT-VERSION-LANE');
    diagnostics.push(diagnostic({
      code: rule.ruleId,
      severity: rule.severity,
      message: `Detected released dependency versions outside the current-source bundle: ${[...new Set(versions)].sort().join(', ')}.`,
      nextAction: 'Select exact released Knowledge Bundles before validating those APIs.',
    }));
  }
  return diagnostics.slice(0, 200);
}

export async function buildProjectAnalysis({
  inventory,
  scan,
  imageOpportunities,
  suppressionDirectives,
  unsupported,
}) {
  const contracts = await loadProjectAnalysisContracts();
  const framework = frameworkIdentity(contracts.manifest);
  const mutableUnsupported = [...unsupported];
  const findings = [
    ...inventoryFindings(inventory, contracts, framework),
    ...applyImageSuppressions({
      opportunities: imageOpportunities,
      directives: suppressionDirectives,
      unsupported: mutableUnsupported,
      contracts,
      framework,
    }),
  ].sort(compareSource);
  const uniqueUnsupported = [...new Map(mutableUnsupported.map((entry) => [
    `${entry.kind}:${entry.source.path}:${entry.source.startLine}:${entry.source.startColumn}`,
    entry,
  ])).values()].sort((left, right) =>
    left.source.path.localeCompare(right.source.path) ||
    left.source.startLine - right.source.startLine ||
    left.source.startColumn - right.source.startColumn ||
    left.kind.localeCompare(right.kind),
  ).slice(0, 200);
  const ruleIds = new Set(findings.map((entry) => entry.ruleId));
  contracts.catalog.rules.forEach((rule) => ruleIds.add(rule.ruleId));
  const analysis = {
    schemaVersion: 1,
    profile: {...framework, match: profileMatch(inventory)},
    scan: {...scan, unsupported: uniqueUnsupported},
    catalog: {
      catalogVersion: contracts.catalog.catalogVersion,
      rules: contracts.catalog.rules.filter((rule) => ruleIds.has(rule.ruleId)).map(compactRule),
    },
    quality: {
      qualityVersion: contracts.quality.qualityVersion,
      corpusVersion: contracts.quality.corpusVersion,
      rules: contracts.quality.rules.filter((entry) => ruleIds.has(entry.ruleId)).map(compactQuality),
    },
    summary: {
      total: findings.length,
      unsuppressed: findings.filter((entry) => entry.suppression.state === 'none').length,
      suppressed: findings.filter((entry) => entry.suppression.state === 'suppressed').length,
      unsupported: uniqueUnsupported.length,
    },
    findings,
  };
  assertSchemaValue(analysis, contracts.schema, 'analyze_project analysis payload');
  return {analysis, diagnostics: projectAnalysisDiagnostics(findings, contracts)};
}
