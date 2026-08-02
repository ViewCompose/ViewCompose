import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const pillars = [
  {
    eyebrow: 'Native engine',
    title: 'Android Views without XML ceremony',
    description:
      'Build declarative UI while retaining platform controls, accessibility, input, lifecycle, and interoperability.',
    href: '/architecture/overview',
  },
  {
    eyebrow: 'Independent artifacts',
    title: 'Adopt only the modules you need',
    description:
      'Core, Android integrations, optional capabilities, and tooling evolve through explicit dependency boundaries.',
    href: '/modules',
  },
  {
    eyebrow: 'Inspectable runtime',
    title: 'Preview and diagnose the real View tree',
    description:
      'Static previews, source mapping, recomposition diagnostics, layout inspection, and performance tooling share one model.',
    href: '/tooling/preview',
  },
];

function Home(): ReactNode {
  return (
    <Layout
      title="Declarative Android UI on the native View system"
      description="ViewCompose is a modular declarative UI framework powered by Android Views.">
      <main>
        <section className={styles.hero}>
          <div className={styles.heroGlow} aria-hidden="true" />
          <div className={styles.heroInner}>
            <p className={styles.kicker}>Declarative Android UI · Native View engine</p>
            <Heading as="h1" className={styles.heroTitle}>
              Compose the interface.
              <span>Keep the platform.</span>
            </Heading>
            <p className={styles.heroCopy}>
              ViewCompose brings state-driven, modular UI construction to the Android View system—
              with explicit runtime contracts and first-class tooling.
            </p>
            <div className={styles.heroActions}>
              <Link className="button button--primary button--lg" to="/architecture/overview">
                Explore the architecture
              </Link>
              <Link className="button button--secondary button--lg" to="/modules">
                Browse modules
              </Link>
            </div>
            <div className={styles.installation} aria-label="Maven coordinate example">
              <span>implementation</span>
              <code>"com.viewcompose:viewcompose-widget-core:0.1.0-alpha01"</code>
            </div>
          </div>
        </section>

        <section className={styles.pillars} aria-labelledby="documentation-pillars">
          <div className={styles.sectionHeading}>
            <p>Designed as a system</p>
            <Heading as="h2" id="documentation-pillars">
              One model from DSL to Android View
            </Heading>
          </div>
          <div className={styles.cardGrid}>
            {pillars.map((pillar) => (
              <Link className={clsx('card', styles.pillarCard)} to={pillar.href} key={pillar.title}>
                <span>{pillar.eyebrow}</span>
                <Heading as="h3">{pillar.title}</Heading>
                <p>{pillar.description}</p>
                <strong>Read more →</strong>
              </Link>
            ))}
          </div>
        </section>

        <section className={styles.referenceBand}>
          <div>
            <p>Versioned reference</p>
            <Heading as="h2">Kotlin and Java API documentation, artifact by artifact.</Heading>
          </div>
          <Link className="button button--outline button--lg" to="/api">
            Open API Reference
          </Link>
        </section>
      </main>
    </Layout>
  );
}

export default Home;
