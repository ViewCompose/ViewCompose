import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  buildKnowledgeBundle,
  generatedDirectory,
  verifyKnowledgeBundle,
  writeKnowledgeBundle,
} from './knowledge-generator.mjs';

const command = process.argv.slice(2);
const modulePath = fileURLToPath(import.meta.url);

async function main() {
  if (command.length !== 1 || !['--write', '--verify'].includes(command[0])) {
    throw new Error('Usage: node scripts/generate-knowledge.mjs --write|--verify');
  }
  if (command[0] === '--write') {
    const bundle = await buildKnowledgeBundle();
    await writeKnowledgeBundle(bundle);
    console.log(
      `Generated AI Knowledge Bundle ${bundle.manifest.bundleFingerprint}: ` +
        `${bundle.manifest.counts.capabilities} capabilities, ` +
        `${bundle.manifest.counts.symbols} symbols, ${bundle.manifest.counts.samples} samples, ` +
        `${bundle.manifest.counts.rules} rules in ${generatedDirectory}.`,
    );
    return;
  }
  const manifest = await verifyKnowledgeBundle();
  console.log(
    `Verified AI Knowledge Bundle ${manifest.bundleFingerprint} for ${manifest.source.revision}.`,
  );
}

if (process.argv[1] && resolve(process.argv[1]) === modulePath) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
