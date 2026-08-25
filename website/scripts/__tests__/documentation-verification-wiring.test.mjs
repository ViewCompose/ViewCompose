import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

function sliceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(start, -1, `Missing start marker: ${startMarker}`);
  assert.notEqual(end, -1, `Missing end marker: ${endMarker}`);
  return source.slice(start, end);
}

test('keeps translation freshness inside the canonical repository documentation gate', async () => {
  const buildScript = await readFile(resolve(repositoryRoot, 'build.gradle.kts'), 'utf8');
  const qualityPlugin = await readFile(
    resolve(
      repositoryRoot,
      'tools/viewcompose-quality-build/src/main/kotlin/com/viewcompose/quality/' +
        'ViewComposeQualityPlugin.kt',
    ),
    'utf8',
  );
  const lifecycleTasks = await readFile(
    resolve(
      repositoryRoot,
      'tools/viewcompose-quality-build/src/main/kotlin/com/viewcompose/quality/' +
        'LifecycleQualityTasks.kt',
    ),
    'utf8',
  );
  const structureTask = sliceBetween(
    qualityPlugin,
    'project.tasks.register<VerifyDocumentationStructureTask>("verifyDocumentationStructure")',
    'project.tasks.register<VerifyDslApiContractsTask>("verifyDslApiContracts")',
  );
  const quickGate = sliceBetween(
    lifecycleTasks,
    'tasks.register("qaQuick")',
    'tasks.matching { task -> task.name == "publishViewComposeToLocalRepository" }',
  );

  assert.match(
    qualityPlugin,
    /project\.tasks\.register<Exec>\("verifyDocumentationTranslations"\)/u,
  );
  assert.match(structureTask, /"verifyDocumentationScripts"/u);
  assert.match(structureTask, /"verifyDocumentLanguages"/u);
  assert.match(structureTask, /"verifyDocumentationTranslations"/u);
  assert.match(quickGate, /dependsOn\("verifyDocumentationStructure"\)/u);
  assert.doesNotMatch(buildScript, /tasks\.register\("verifyDocumentationStructure"\)/u);
  assert.doesNotMatch(buildScript, /tasks\.register\("qaQuick"\)/u);
});

test('documentation CI delegates translation freshness to the canonical Gradle gate', async () => {
  const workflow = await readFile(
    resolve(repositoryRoot, '.github/workflows/documentation.yml'),
    'utf8',
  );

  assert.match(workflow, /\.\/gradlew verifyDocumentationStructure --stacktrace/u);
  assert.doesNotMatch(workflow, /npm run verify:translations/u);
});

test('site quality report stays outside the deployable and budgeted site tree', async () => {
  const buildScript = await readFile(
    resolve(repositoryRoot, 'website/scripts/build-site.mjs'),
    'utf8',
  );

  assert.match(
    buildScript,
    /resolve\(websiteRoot, '\.\.', 'build', 'reports', 'documentation'\)/u,
  );
  assert.doesNotMatch(buildScript, /resolve\(buildDir, 'site-quality-report\.json'\)/u);
});
