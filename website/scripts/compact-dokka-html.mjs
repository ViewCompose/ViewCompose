import {readFile, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir, collectFiles} from './site-quality-lib.mjs';

const preservedElementPattern = /<(pre|script|style|textarea)\b/iu;

export function compactDokkaHtml(source) {
  const lines = source.split(/\r?\n/u);
  let preservedElement;
  const compacted = lines.map((line) => {
    if (preservedElement) {
      if (new RegExp(`</${preservedElement}\\s*>`, 'iu').test(line)) {
        preservedElement = undefined;
      }
      return line;
    }

    const compactedLine = line.trimStart();
    const opening = preservedElementPattern.exec(compactedLine)?.[1]?.toLowerCase();
    if (opening && !new RegExp(`</${opening}\\s*>`, 'iu').test(compactedLine)) {
      preservedElement = opening;
    }
    return compactedLine;
  });
  return compacted.join('\n');
}

export async function compactDokkaHtmlTree({
  apiDirectory = resolve(buildDir, 'api'),
} = {}) {
  const htmlFiles = await collectFiles(apiDirectory, (path) => path.endsWith('.html'));
  let originalBytes = 0;
  let compactedBytes = 0;
  for (const path of htmlFiles) {
    const source = await readFile(path, 'utf8');
    const compacted = compactDokkaHtml(source);
    originalBytes += Buffer.byteLength(source);
    compactedBytes += Buffer.byteLength(compacted);
    if (compacted !== source) await writeFile(path, compacted, 'utf8');
  }
  const savedBytes = originalBytes - compactedBytes;
  console.log(
    `Dokka HTML compaction completed: ${htmlFiles.length} files, ` +
      `${savedBytes.toLocaleString('en-US')} bytes removed without changing preserved content.`,
  );
  return {files: htmlFiles.length, originalBytes, compactedBytes, savedBytes};
}

