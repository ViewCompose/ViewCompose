import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import {translate} from '@docusaurus/Translate';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import modules from '@site/src/generated/moduleCatalog.json';
import styles from './styles.module.css';

type ModuleEntry = {
  artifact: string;
  version: string;
  family: string;
  role: string;
  manual: string;
};

const groupedModules = (modules as ModuleEntry[]).reduce<Record<string, ModuleEntry[]>>(
  (groups, module) => {
    (groups[module.family] ??= []).push(module);
    return groups;
  },
  {},
);

function ApiReference(): ReactNode {
  return (
    <Layout
      title={translate({id: 'api.meta.title', message: 'API Reference'})}
      description={translate({
        id: 'api.meta.description',
        message: 'Versioned Kotlin and Java API reference for ViewCompose modules.',
      })}>
      <main className={styles.page}>
        <header className={styles.header}>
          <p>{translate({id: 'api.header.eyebrow', message: 'Generated with Dokka'})}</p>
          <Heading as="h1">
            {translate({id: 'api.header.title', message: 'API Reference'})}
          </Heading>
          <span>
            {translate({
              id: 'api.header.description',
              message:
                'Kotlin KDoc and Java Javadoc are published independently for every Maven artifact.',
            })}
          </span>
        </header>

        <div className={styles.groups}>
          {Object.entries(groupedModules).map(([family, entries]) => (
            <section key={family} aria-labelledby={`family-${family.replaceAll(' ', '-')}`}>
              <Heading as="h2" id={`family-${family.replaceAll(' ', '-')}`}>
                {family}
              </Heading>
              <div className={styles.moduleGrid}>
                {entries.map((module) => (
                  <article className={styles.moduleCard} key={module.artifact}>
                    <div>
                      <Heading as="h3">{module.artifact}</Heading>
                      <code>{module.version}</code>
                    </div>
                    <p>{module.role}</p>
                    <Link
                      to={`/api/${module.artifact}/${module.version}/`}
                      data-noBrokenLinkCheck>
                      {translate(
                        {id: 'api.module.openReference', message: 'Open {version} reference →'},
                        {version: module.version},
                      )}
                    </Link>
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      </main>
    </Layout>
  );
}

export default ApiReference;
