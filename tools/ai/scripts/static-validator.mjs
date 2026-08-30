import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {diagnostic, sourceLocation, toolResult, utf8Bytes} from './tool-core.mjs';

const symbolsPath = fileURLToPath(
  new URL('../generated/current-source/symbols.jsonl', import.meta.url),
);
const kotlinKeywords = new Set([
  'catch',
  'class',
  'constructor',
  'do',
  'else',
  'for',
  'fun',
  'if',
  'interface',
  'object',
  'return',
  'super',
  'this',
  'throw',
  'try',
  'when',
  'while',
]);

let symbolIndexPromise;

function maskNonCode(source) {
  const characters = source.split('');
  const mask = (index) => {
    if (characters[index] !== '\n' && characters[index] !== '\r') characters[index] = ' ';
  };
  let index = 0;
  while (index < source.length) {
    if (source.startsWith('//', index)) {
      while (index < source.length && source[index] !== '\n') {
        mask(index);
        index += 1;
      }
      continue;
    }
    if (source.startsWith('/*', index)) {
      let depth = 0;
      while (index < source.length) {
        if (source.startsWith('/*', index)) {
          depth += 1;
          mask(index);
          mask(index + 1);
          index += 2;
        } else if (source.startsWith('*/', index)) {
          depth -= 1;
          mask(index);
          mask(index + 1);
          index += 2;
          if (depth === 0) break;
        } else {
          mask(index);
          index += 1;
        }
      }
      continue;
    }
    if (source.startsWith('"""', index)) {
      for (let count = 0; count < 3; count += 1) mask(index + count);
      index += 3;
      while (index < source.length && !source.startsWith('"""', index)) {
        mask(index);
        index += 1;
      }
      for (let count = 0; count < 3 && index < source.length; count += 1) {
        mask(index);
        index += 1;
      }
      continue;
    }
    if (source[index] === '"' || source[index] === '\'') {
      const quote = source[index];
      mask(index);
      index += 1;
      while (index < source.length) {
        const character = source[index];
        mask(index);
        index += 1;
        if (character === '\\' && index < source.length) {
          mask(index);
          index += 1;
        } else if (character === quote || character === '\n') {
          break;
        }
      }
      continue;
    }
    index += 1;
  }
  return characters.join('');
}

export function loadValidatorIndex() {
  symbolIndexPromise ??= readFile(symbolsPath, 'utf8').then((content) => {
    const symbols = content.trimEnd().split('\n').map(JSON.parse);
    const byImport = new Map();
    const bySimpleName = new Map();
    for (const symbol of symbols) {
      const importName = `${symbol.namespace}.${symbol.simpleName}`;
      const importEntries = byImport.get(importName) ?? [];
      importEntries.push(symbol);
      byImport.set(importName, importEntries);
      const simpleEntries = bySimpleName.get(symbol.simpleName) ?? [];
      simpleEntries.push(symbol);
      bySimpleName.set(symbol.simpleName, simpleEntries);
    }
    return {symbols, byImport, bySimpleName};
  });
  return symbolIndexPromise;
}

function parseKotlinSurface(source) {
  const packageName = /^\s*package\s+([A-Za-z0-9_.]+)/mu.exec(source)?.[1] ?? '';
  const exactImports = new Map();
  const wildcardImports = new Set();
  for (const match of source.matchAll(/^\s*import\s+(com\.viewcompose\.[A-Za-z0-9_.*]+)(?:\s+as\s+([A-Za-z0-9_]+))?/gmu)) {
    const imported = match[1];
    if (imported.endsWith('.*')) {
      wildcardImports.add(imported.slice(0, -2));
    } else {
      exactImports.set(match[2] ?? imported.split('.').at(-1), {
        imported,
        offset: match.index + match[0].indexOf(imported),
      });
    }
  }
  const declaredFunctions = new Set(
    [...source.matchAll(/\bfun\s+(?:[A-Za-z0-9_.<>?]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(/gu)]
      .map((match) => match[1]),
  );
  return {packageName, exactImports, wildcardImports, declaredFunctions};
}

function resolvesUnqualified(name, surface, index) {
  const exactImport = surface.exactImports.get(name);
  if (exactImport) {
    return index.byImport.has(exactImport.imported);
  }
  const candidates = index.bySimpleName.get(name) ?? [];
  return candidates.some((candidate) =>
    candidate.namespace === surface.packageName || surface.wildcardImports.has(candidate.namespace),
  );
}

function findMatchingParen(source, openOffset) {
  let depth = 0;
  let quote = null;
  let escaped = false;
  for (let index = openOffset; index < source.length; index += 1) {
    const character = source[index];
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (character === '\\') {
        escaped = true;
      } else if (character === quote) {
        quote = null;
      }
      continue;
    }
    if (character === '"' || character === '\'') {
      quote = character;
    } else if (character === '(') {
      depth += 1;
    } else if (character === ')') {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

export async function validateKotlin({
  source,
  path = 'Snippet.kt',
  requestId = 'validate-code',
  maxInputBytes = 4 * 1024 * 1024,
} = {}) {
  const started = performance.now();
  if (typeof source !== 'string' || source.length === 0) {
    return toolResult({
      requestId,
      tool: 'validate_code',
      status: 'invalid',
      level: 'static',
      diagnostics: [diagnostic({
        code: 'VC-AI-INPUT-INVALID',
        severity: 'error',
        message: 'Kotlin source must be a non-empty string.',
        nextAction: 'Submit one bounded Kotlin source file.',
      })],
      elapsedMs: performance.now() - started,
    });
  }
  if (utf8Bytes(source) > maxInputBytes) {
    return toolResult({
      requestId,
      tool: 'validate_code',
      status: 'limited',
      level: 'static',
      diagnostics: [diagnostic({
        code: 'VC-AI-INPUT-LIMIT',
        severity: 'error',
        message: `Kotlin source exceeds the ${maxInputBytes}-byte validation limit.`,
        nextAction: 'Submit a smaller source file or raise the bounded request limit.',
      })],
      elapsedMs: performance.now() - started,
      truncated: true,
    });
  }

  const index = await loadValidatorIndex();
  const codeSource = maskNonCode(source);
  const surface = parseKotlinSurface(codeSource);
  const diagnostics = [];
  const matchedSymbolIds = new Set();

  for (const {imported, offset} of surface.exactImports.values()) {
    const matches = index.byImport.get(imported);
    const simpleName = imported.split('.').at(-1);
    if (!matches && index.bySimpleName.has(simpleName)) {
      diagnostics.push(diagnostic({
        code: 'VC-AI-UNKNOWN-SYMBOL',
        severity: 'error',
        message: `The selected Knowledge Bundle does not contain ${imported}.`,
        nextAction: 'Use the exact current symbol from symbols.jsonl or select another framework lane.',
        source: sourceLocation(source, path, offset, imported.length),
      }));
    } else if (matches) {
      matches.forEach((symbol) => matchedSymbolIds.add(symbol.symbolId));
    }
  }

  for (const match of codeSource.matchAll(/(?<![.A-Za-z0-9_])([A-Za-z_][A-Za-z0-9_]*)\s*\(/gu)) {
    const name = match[1];
    if (
      kotlinKeywords.has(name) ||
      surface.declaredFunctions.has(name) ||
      !index.bySimpleName.has(name) ||
      resolvesUnqualified(name, surface, index)
    ) {
      continue;
    }
    diagnostics.push(diagnostic({
      code: 'VC-AI-UNKNOWN-SYMBOL',
      severity: 'error',
      message: `${name} is a ViewCompose symbol, but no imported or package-local overload is available in this scope.`,
      nextAction: 'Retrieve the symbol declaration and call it through its documented receiver or component parameter.',
      source: sourceLocation(source, path, match.index, name.length),
      capabilityId: index.bySimpleName.get(name)[0].capabilityId,
      artifactId: index.bySimpleName.get(name)[0].artifactId,
    }));
  }

  if (resolvesUnqualified('Image', surface, index)) {
    for (const match of codeSource.matchAll(/(?<![.A-Za-z0-9_])Image\s*\(/gu)) {
      const openOffset = codeSource.indexOf('(', match.index);
      const closeOffset = findMatchingParen(codeSource, openOffset);
      if (closeOffset < 0) continue;
      const argumentsText = codeSource.slice(openOffset + 1, closeOffset);
      if (!/\bcontentDescription\s*=/u.test(argumentsText)) {
        const imageSymbol = index.bySimpleName.get('Image').find(
          (symbol) => symbol.capabilityId === 'image.foundation',
        );
        diagnostics.push(diagnostic({
          code: 'VC-AI-A11Y-IMAGE-DESCRIPTION',
          severity: 'warning',
          message: 'ViewCompose Image must declare contentDescription, including an explicit null for decorative content.',
          nextAction: 'Set contentDescription to meaningful text, or explicitly set null only when the image is decorative.',
          source: sourceLocation(source, path, match.index, 'Image'.length),
          capabilityId: imageSymbol?.capabilityId,
          artifactId: imageSymbol?.artifactId,
        }));
      }
    }
  }

  const uniqueDiagnostics = [...new Map(
    diagnostics.map((entry) => [
      `${entry.code}:${entry.source?.startLine ?? 0}:${entry.source?.startColumn ?? 0}`,
      entry,
    ]),
  ).values()].sort((left, right) =>
    (left.source?.startLine ?? 0) - (right.source?.startLine ?? 0) ||
    (left.source?.startColumn ?? 0) - (right.source?.startColumn ?? 0) ||
    left.code.localeCompare(right.code),
  );
  return toolResult({
    requestId,
    tool: 'validate_code',
    status: uniqueDiagnostics.length === 0 ? 'success' : 'invalid',
    level: 'static',
    diagnostics: uniqueDiagnostics,
    data: {
      path,
      symbolIndexCount: index.symbols.length,
      matchedSymbolIds: [...matchedSymbolIds].sort(),
    },
    elapsedMs: performance.now() - started,
  });
}
