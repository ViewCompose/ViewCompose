import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import {translate} from '@docusaurus/Translate';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

function Home(): ReactNode {
  const pillars = [
    {
      eyebrow: translate({id: 'homepage.pillar.native.eyebrow', message: 'Native engine'}),
      title: translate({
        id: 'homepage.pillar.native.title',
        message: 'Android Views without XML ceremony',
      }),
      description: translate({
        id: 'homepage.pillar.native.description',
        message:
          'Build declarative UI while retaining platform controls, accessibility, input, lifecycle, and interoperability.',
      }),
      href: '/architecture/overview',
    },
    {
      eyebrow: translate({
        id: 'homepage.pillar.artifacts.eyebrow',
        message: 'Independent artifacts',
      }),
      title: translate({
        id: 'homepage.pillar.artifacts.title',
        message: 'Adopt only the modules you need',
      }),
      description: translate({
        id: 'homepage.pillar.artifacts.description',
        message:
          'Core, Android integrations, optional capabilities, and tooling evolve through explicit dependency boundaries.',
      }),
      href: '/modules',
    },
    {
      eyebrow: translate({
        id: 'homepage.pillar.runtime.eyebrow',
        message: 'Inspectable runtime',
      }),
      title: translate({
        id: 'homepage.pillar.runtime.title',
        message: 'Preview and diagnose the real View tree',
      }),
      description: translate({
        id: 'homepage.pillar.runtime.description',
        message:
          'Static previews, source mapping, recomposition diagnostics, layout inspection, and performance tooling share one model.',
      }),
      href: '/tooling/preview',
    },
  ];

  return (
    <Layout
      title={translate({
        id: 'homepage.meta.title',
        message: 'Declarative Android UI on the native View system',
      })}
      description={translate({
        id: 'homepage.meta.description',
        message: 'ViewCompose is a modular declarative UI framework powered by Android Views.',
      })}>
      <main>
        <section className={styles.hero}>
          <div className={styles.heroGlow} aria-hidden="true" />
          <div className={styles.heroInner}>
            <p className={styles.kicker}>
              {translate({
                id: 'homepage.hero.kicker',
                message: 'Declarative Android UI · Native View engine',
              })}
            </p>
            <Heading as="h1" className={styles.heroTitle}>
              {translate({id: 'homepage.hero.title', message: 'Compose the interface.'})}
              <span>
                {translate({id: 'homepage.hero.titleAccent', message: 'Keep the platform.'})}
              </span>
            </Heading>
            <p className={styles.heroCopy}>
              {translate({
                id: 'homepage.hero.description',
                message:
                  'ViewCompose brings state-driven, modular UI construction to the Android View system—with explicit runtime contracts and first-class tooling.',
              })}
            </p>
            <div className={styles.heroActions}>
              <Link className="button button--primary button--lg" to="/architecture/overview">
                {translate({
                  id: 'homepage.hero.architectureAction',
                  message: 'Explore the architecture',
                })}
              </Link>
              <Link className="button button--secondary button--lg" to="/modules">
                {translate({id: 'homepage.hero.modulesAction', message: 'Browse modules'})}
              </Link>
            </div>
            <div
              className={styles.installation}
              aria-label={translate({
                id: 'homepage.installation.ariaLabel',
                message: 'Maven coordinate example',
              })}>
              <span>
                {translate({id: 'homepage.installation.label', message: 'implementation'})}
              </span>
              <code>"com.viewcompose:viewcompose-widget-core:0.1.0-alpha01"</code>
            </div>
          </div>
        </section>

        <section className={styles.pillars} aria-labelledby="documentation-pillars">
          <div className={styles.sectionHeading}>
            <p>
              {translate({id: 'homepage.pillars.eyebrow', message: 'Designed as a system'})}
            </p>
            <Heading as="h2" id="documentation-pillars">
              {translate({
                id: 'homepage.pillars.title',
                message: 'One model from DSL to Android View',
              })}
            </Heading>
          </div>
          <div className={styles.cardGrid}>
            {pillars.map((pillar) => (
              <Link className={clsx('card', styles.pillarCard)} to={pillar.href} key={pillar.title}>
                <span>{pillar.eyebrow}</span>
                <Heading as="h3">{pillar.title}</Heading>
                <p>{pillar.description}</p>
                <strong>
                  {translate({id: 'homepage.pillars.readMore', message: 'Read more →'})}
                </strong>
              </Link>
            ))}
          </div>
        </section>

        <section className={styles.referenceBand}>
          <div>
            <p>
              {translate({id: 'homepage.reference.eyebrow', message: 'Versioned reference'})}
            </p>
            <Heading as="h2">
              {translate({
                id: 'homepage.reference.title',
                message: 'Kotlin and Java API documentation, artifact by artifact.',
              })}
            </Heading>
          </div>
          <Link className="button button--outline button--lg" to="/api">
            {translate({id: 'homepage.reference.action', message: 'Open API Reference'})}
          </Link>
        </section>
      </main>
    </Layout>
  );
}

export default Home;
