import assert from 'node:assert/strict';
import {readdir, readFile} from 'node:fs/promises';
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

test('Gradle User Home cache has one owner and explicit branch writes', async () => {
  const workflowDirectory = resolve(repositoryRoot, '.github/workflows');
  const workflowNames = (await readdir(workflowDirectory)).filter((name) => /\.ya?ml$/u.test(name));
  const cachePolicy =
    "cache-read-only: ${{ github.ref != format('refs/heads/{0}', " +
    'github.event.repository.default_branch) }}';

  for (const workflowName of workflowNames) {
    const workflow = await readFile(resolve(workflowDirectory, workflowName), 'utf8');
    const setupGradleCount = workflow.split('uses: gradle/actions/setup-gradle@v6').length - 1;
    const explicitPolicyCount = workflow.split(cachePolicy).length - 1;

    assert.doesNotMatch(workflow, /cache:\s*gradle/u);
    assert.equal(
      explicitPolicyCount,
      setupGradleCount,
      `${workflowName} must make every setup-gradle cache write policy explicit`,
    );
  }
});

test('documentation CI restores only main-owned API output and prepares the site once', async () => {
  const workflow = await readFile(
    resolve(repositoryRoot, '.github/workflows/documentation.yml'),
    'utf8',
  );
  const packageJson = JSON.parse(
    await readFile(resolve(repositoryRoot, 'website/package.json'), 'utf8'),
  );
  const documentationWork = sliceBetween(workflow, '  buildDocumentation:', '  build:');

  assert.match(documentationWork, /node website\/scripts\/versioned-api-cache\.mjs plan/u);
  assert.match(documentationWork, /uses: actions\/cache\/restore@v5/u);
  assert.match(documentationWork, /uses: actions\/cache\/save@v5/u);
  assert.match(documentationWork, /continue-on-error: true[\s\S]*actions\/cache\/restore@v5/u);
  assert.match(documentationWork, /website\/generated\/api[\s\S]*build\/versioned-api-cache/u);
  assert.match(
    documentationWork,
    /github\.ref == 'refs\/heads\/main'[\s\S]*save_required == 'true'/u,
  );
  assert.match(documentationWork, /VIEWCOMPOSE_API_DOCS_MAX_PARALLEL_REVISIONS: "1"/u);
  assert.match(documentationWork, /npm run generate:catalog/u);
  assert.match(documentationWork, /npm run typecheck:prepared/u);
  assert.match(documentationWork, /npm run build:prepared/u);
  assert.ok(
    documentationWork.indexOf('npm run build:prepared') <
      documentationWork.indexOf('uses: actions/cache/save@v5'),
    'main cache writes must follow a successful production site build',
  );
  assert.ok(
    documentationWork.indexOf('uses: actions/upload-pages-artifact@v5') <
      documentationWork.indexOf('uses: actions/cache/save@v5'),
    'main cache writes must follow a successful Pages artifact upload',
  );
  assert.doesNotMatch(documentationWork, /run: npm run typecheck\s*$/mu);
  assert.doesNotMatch(documentationWork, /run: npm run build\s*$/mu);
  assert.equal(packageJson.scripts['typecheck:prepared'], 'tsc');
  assert.equal(packageJson.scripts['build:prepared'], 'node scripts/build-site.mjs');
  assert.match(packageJson.scripts['prepare:site'], /verify:languages/u);
  assert.match(packageJson.scripts['prepare:site'], /verify:translations/u);
});

test('required contexts remain always-reported facades around classified child work', async () => {
  const ciWorkflow = await readFile(resolve(repositoryRoot, '.github/workflows/ci.yml'), 'utf8');
  const documentationWorkflow = await readFile(
    resolve(repositoryRoot, '.github/workflows/documentation.yml'),
    'utf8',
  );
  const requiredContract = JSON.parse(
    await readFile(
      resolve(
        repositoryRoot,
        'tools/viewcompose-quality-build/phase0/fixtures/required-check-contract.json',
      ),
      'utf8',
    ),
  );
  const qaQuickWork = sliceBetween(ciWorkflow, '  qaQuickWork:', '  qaQuick:');
  const qaQuickFacade = sliceBetween(ciWorkflow, '  qaQuick:', '  qaPreviewWork:');
  const qaPreviewWork = sliceBetween(ciWorkflow, '  qaPreviewWork:', '  qaPreview:');
  const qaPreviewFacade = ciWorkflow.slice(ciWorkflow.indexOf('  qaPreview:'));
  const documentationWork = sliceBetween(
    documentationWorkflow,
    '  buildDocumentation:',
    '  build:',
  );
  const documentationFacade = sliceBetween(documentationWorkflow, '  build:', '  deploy:');

  assert.deepEqual(
    requiredContract.branchProtection.requiredContexts.map(({context}) => context),
    ['qaQuick', 'Build documentation'],
  );
  assert.match(ciWorkflow, /\.\/gradlew -p tools\/viewcompose-quality-build planPullRequestImpact/u);
  assert.match(
    documentationWorkflow,
    /\.\/gradlew -p tools\/viewcompose-quality-build planPullRequestImpact/u,
  );
  assert.match(qaQuickWork, /if: needs\.classify\.outputs\.qa_quick == 'true'/u);
  assert.match(qaQuickWork, /name: Run affected qaQuick candidate/u);
  assert.match(qaQuickWork, /if: needs\.classify\.outputs\.full_fallback != 'true'/u);
  assert.match(qaQuickWork, /qaAffected/u);
  assert.match(qaQuickWork, /name: Run complete qaQuick shadow/u);
  assert.match(qaQuickWork, /name: Report affected versus complete qaQuick/u);
  assert.ok(
    qaQuickWork.indexOf('qaAffected') < qaQuickWork.indexOf('qaQuick\n'),
    'affected verification must run before the complete shadow gate',
  );
  assert.match(qaPreviewWork, /if: needs\.classify\.outputs\.qa_preview == 'true'/u);
  assert.match(
    documentationWork,
    /if: needs\.classify\.outputs\.documentation == 'true'/u,
  );
  for (const [facade, name, child] of [
    [qaQuickFacade, 'qaQuick', 'qaQuickWork'],
    [qaPreviewFacade, 'qaPreview', 'qaPreviewWork'],
    [documentationFacade, 'Build documentation', 'buildDocumentation'],
  ]) {
    assert.match(facade, new RegExp(`name: ${name}`, 'u'));
    assert.match(facade, /if: always\(\)/u);
    assert.match(facade, /- classify/u);
    assert.match(facade, new RegExp(`- ${child}`, 'u'));
    assert.match(facade, /if \[\[ "\$PLAN_RESULT" != "success" \]\]/u);
    assert.match(facade, /"\$SELECTED" == "true" && "\$WORK_RESULT" != "success"/u);
    assert.match(facade, /"\$SELECTED" != "true" && "\$WORK_RESULT" != "skipped"/u);
  }
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
