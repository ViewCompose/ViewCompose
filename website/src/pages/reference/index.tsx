import {useMemo, useState, type ReactNode} from 'react';
import Link from '@docusaurus/Link';
import {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import catalogData from '@site/src/data/capability-reference.json';
import styles from './styles.module.css';

type RelatedDocument = {
  documentId: string;
  documentType: string;
  path: string | null;
};

type ReferenceSample = {
  exceptionId?: string;
  sampleClass?: string;
  sampleId?: string;
  versionLane?: string;
};

type ReferenceEntry = {
  artifact: string;
  capabilityId?: string;
  deprecated?: boolean;
  kind: string;
  namespace: string;
  overloadCount: number;
  receiver?: string;
  referenceId?: string;
  relatedDocuments?: RelatedDocument[];
  sample?: ReferenceSample;
  symbol: string;
};

type ReferenceArtifact = {
  apiReference: string;
  artifact: string;
  moduleManual: string;
  version: string | null;
  versionState: string;
};

type ReferenceGroup = {
  entries: ReferenceEntry[];
  entryCount: number;
  groupId: string;
};

type ReferenceCatalog = {
  artifacts: ReferenceArtifact[];
  groups: ReferenceGroup[];
  summary: {
    artifactCount: number;
    entryCount: number;
    groupCount: number;
    ownedEntryCount: number;
  };
};

const catalog = catalogData as ReferenceCatalog;
const artifactsById = new Map(catalog.artifacts.map((artifact) => [artifact.artifact, artifact]));

function groupLabel(groupId: string): string {
  switch (groupId) {
    case 'layout':
      return translate({id: 'reference.group.layout', message: 'Layout'});
    case 'appearance':
      return translate({id: 'reference.group.appearance', message: 'Appearance'});
    case 'input':
      return translate({id: 'reference.group.input', message: 'Input and semantics'});
    case 'gesture':
      return translate({id: 'reference.group.gesture', message: 'Gesture and scroll'});
    case 'animation':
      return translate({id: 'reference.group.animation', message: 'Animation'});
    case 'content':
      return translate({id: 'reference.group.content', message: 'Content'});
    case 'actions':
      return translate({id: 'reference.group.actions', message: 'Actions'});
    case 'collections':
      return translate({id: 'reference.group.collections', message: 'Collections'});
    case 'feedback':
      return translate({id: 'reference.group.feedback', message: 'Feedback and overlays'});
    case 'navigation':
      return translate({id: 'reference.group.navigation', message: 'Navigation'});
    case 'design-systems':
      return translate({id: 'reference.group.designSystems', message: 'Design systems'});
    case 'integrations':
      return translate({id: 'reference.group.integrations', message: 'Integrations'});
    case 'android-interop':
      return translate({id: 'reference.group.androidInterop', message: 'Android interop'});
    case 'tooling':
      return translate({id: 'reference.group.tooling', message: 'Tooling'});
    default:
      return translate({id: 'reference.group.general', message: 'General DSL'});
  }
}

function kindLabel(kind: string): string {
  switch (kind) {
    case 'modifier':
      return translate({id: 'reference.kind.modifier', message: 'Modifier'});
    case 'component':
      return translate({id: 'reference.kind.component', message: 'Component'});
    case 'host':
      return translate({id: 'reference.kind.host', message: 'Host'});
    case 'integration':
      return translate({id: 'reference.kind.integration', message: 'Integration'});
    case 'tooling':
      return translate({id: 'reference.kind.tooling', message: 'Tooling'});
    default:
      return translate({id: 'reference.kind.dsl', message: 'DSL'});
  }
}

function documentTypeLabel(documentType: string): string {
  switch (documentType) {
    case 'tutorial':
      return translate({id: 'reference.document.tutorial', message: 'Tutorial'});
    case 'guide':
      return translate({id: 'reference.document.guide', message: 'Guide'});
    case 'architecture':
      return translate({id: 'reference.document.architecture', message: 'Architecture'});
    case 'module':
      return translate({id: 'reference.document.module', message: 'Module'});
    case 'tooling':
      return translate({id: 'reference.document.tooling', message: 'Tooling'});
    default:
      return translate({id: 'reference.document.migration', message: 'Migration'});
  }
}

function matches(entry: ReferenceEntry, query: string): boolean {
  if (query.length === 0) return true;
  return [entry.symbol, entry.artifact, entry.namespace, entry.receiver, entry.kind]
    .filter(Boolean)
    .some((value) => value!.toLowerCase().includes(query));
}

function EntryCard({entry}: {entry: ReferenceEntry}): ReactNode {
  const artifact = artifactsById.get(entry.artifact)!;
  const name = entry.symbol.substring(entry.symbol.lastIndexOf('.') + 1);
  return (
    <article className={styles.entryCard}>
      <div className={styles.entryHeading}>
        <Heading as="h3">
          <code>{name}</code>
        </Heading>
        <span>{kindLabel(entry.kind)}</span>
      </div>
      <code className={styles.symbol}>{entry.symbol}</code>
      <dl className={styles.metadata}>
        <div>
          <dt>{translate({id: 'reference.entry.artifact', message: 'Artifact'})}</dt>
          <dd>{entry.artifact}</dd>
        </div>
        <div>
          <dt>{translate({id: 'reference.entry.version', message: 'Version'})}</dt>
          <dd>
            {artifact.versionState === 'next'
              ? translate({id: 'reference.entry.next', message: 'next'})
              : artifact.version ?? artifact.versionState}
          </dd>
        </div>
        <div>
          <dt>{translate({id: 'reference.entry.overloads', message: 'Overloads'})}</dt>
          <dd>{entry.overloadCount}</dd>
        </div>
      </dl>
      <div className={styles.links}>
        <Link to={artifact.apiReference} data-noBrokenLinkCheck>
          {translate({id: 'reference.entry.api', message: 'API / KDoc'})}
        </Link>
        <Link to={artifact.moduleManual}>
          {translate({id: 'reference.entry.manual', message: 'Module manual'})}
        </Link>
        {(entry.relatedDocuments ?? [])
          .filter((document) => document.path !== null)
          .map((document) => (
            <Link key={document.documentId} to={document.path!}>
              {documentTypeLabel(document.documentType)}
            </Link>
          ))}
      </div>
    </article>
  );
}

function CapabilityReference(): ReactNode {
  const [search, setSearch] = useState('');
  const [selectedGroup, setSelectedGroup] = useState(catalog.groups[0].groupId);
  const query = search.trim().toLowerCase();
  const visibleGroups = useMemo(
    () =>
      catalog.groups
        .filter((group) => query.length > 0 || group.groupId === selectedGroup)
        .map((group) => ({...group, entries: group.entries.filter((entry) => matches(entry, query))}))
        .filter((group) => group.entries.length > 0),
    [query, selectedGroup],
  );
  const resultCount = visibleGroups.reduce((count, group) => count + group.entries.length, 0);

  return (
    <Layout
      title={translate({id: 'reference.meta.title', message: 'Capability Reference'})}
      description={translate({
        id: 'reference.meta.description',
        message: 'Source-derived ViewCompose DSL, Modifier, component, integration, and tooling index.',
      })}>
      <main className={styles.page}>
        <header className={styles.header}>
          <p>{translate({id: 'reference.header.eyebrow', message: 'Generated from production source'})}</p>
          <Heading as="h1">
            {translate({id: 'reference.header.title', message: 'Capability Reference'})}
          </Heading>
          <span>
            {translate({
              id: 'reference.header.description',
              message:
                'Browse application-facing DSL, Modifier, component, integration, host, and tooling entries by the user capability they serve.',
            })}
          </span>
        </header>

        <section className={styles.summary} aria-label={translate({id: 'reference.summary.label', message: 'Catalog summary'})}>
          <div><strong>{catalog.summary.entryCount}</strong><span>{translate({id: 'reference.summary.entries', message: 'entries'})}</span></div>
          <div><strong>{catalog.summary.artifactCount}</strong><span>{translate({id: 'reference.summary.artifacts', message: 'artifacts'})}</span></div>
          <div><strong>{catalog.summary.groupCount}</strong><span>{translate({id: 'reference.summary.groups', message: 'capability groups'})}</span></div>
          <div><strong>{catalog.summary.ownedEntryCount}</strong><span>{translate({id: 'reference.summary.owners', message: 'structured owners'})}</span></div>
        </section>

        <aside className={styles.migrationNote}>
          <strong>{translate({id: 'reference.migration.title', message: 'Governance V2 migration status'})}</strong>
          <span>
            {translate({
              id: 'reference.migration.description',
              message:
                'The symbol tree, grouping, versions, overloads, and counts are source-owned and freshness-gated. Exact capability, sample, and related-document links appear as their structured owners migrate.',
            })}
          </span>
        </aside>

        <div className={styles.searchPanel}>
          <label htmlFor="capability-reference-search">
            {translate({id: 'reference.search.label', message: 'Search the catalog'})}
          </label>
          <input
            id="capability-reference-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={translate({
              id: 'reference.search.placeholder',
              message: 'Symbol, artifact, namespace, or kind',
            })}
          />
          <span>
            {translate(
              {id: 'reference.search.results', message: '{count} matching entries'},
              {count: resultCount},
            )}
          </span>
        </div>

        <nav className={styles.groupNav} aria-label={translate({id: 'reference.groups.label', message: 'Capability groups'})}>
          {catalog.groups.map((group) => (
            <button
              key={group.groupId}
              type="button"
              aria-pressed={query.length === 0 && selectedGroup === group.groupId}
              onClick={() => {
                setSearch('');
                setSelectedGroup(group.groupId);
              }}>
              {groupLabel(group.groupId)} <span>{group.entryCount}</span>
            </button>
          ))}
        </nav>

        <div className={styles.groups}>
          {visibleGroups.map((group) => (
            <section key={group.groupId} id={`reference-${group.groupId}`}>
              <div className={styles.groupHeading}>
                <Heading as="h2">{groupLabel(group.groupId)}</Heading>
                <span>{group.entries.length}</span>
              </div>
              <div className={styles.entryGrid}>
                {group.entries.map((entry) => <EntryCard key={entry.symbol} entry={entry} />)}
              </div>
            </section>
          ))}
          {visibleGroups.length === 0 ? (
            <p className={styles.empty}>
              {translate({id: 'reference.search.empty', message: 'No catalog entry matches this search.'})}
            </p>
          ) : null}
        </div>
      </main>
    </Layout>
  );
}

export default CapabilityReference;
