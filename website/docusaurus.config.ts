import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type {Options, ThemeConfig} from '@docusaurus/preset-classic';

const config: Config = {
  title: 'ViewCompose',
  tagline: 'Declarative Android UI, powered by the native View system',
  url: 'https://docs.viewcompose.com',
  baseUrl: '/',
  organizationName: 'ViewCompose',
  projectName: 'ViewCompose',
  trailingSlash: false,
  onBrokenLinks: 'throw',
  future: {
    v4: true,
  },
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },
  markdown: {
    mermaid: true,
  },
  staticDirectories: ['static', 'generated'],
  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          exclude: ['archive/**'],
          editUrl: ({docPath}) =>
            `https://github.com/ViewCompose/ViewCompose/edit/main/docs/${docPath}`,
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Options,
    ],
  ],
  themes: ['@docusaurus/theme-mermaid'],
  themeConfig: {
    image: 'img/social-card.png',
    navbar: {
      title: 'ViewCompose',
      hideOnScroll: true,
      items: [
        {
          type: 'doc',
          docId: 'architecture/overview',
          label: 'Architecture',
          position: 'left',
        },
        {
          type: 'doc',
          docId: 'modules/README',
          label: 'Modules',
          position: 'left',
        },
        {
          to: '/api',
          label: 'API Reference',
          position: 'left',
        },
        {
          href: 'https://github.com/ViewCompose/ViewCompose',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Learn',
          items: [
            {label: 'Architecture', to: '/architecture/overview'},
            {label: 'Guides', to: '/guides/theming'},
            {label: 'Modules', to: '/modules'},
          ],
        },
        {
          title: 'Develop',
          items: [
            {label: 'API Reference', to: '/api'},
            {label: 'Preview tooling', to: '/tooling/preview'},
            {label: 'Contributing', href: 'https://github.com/ViewCompose/ViewCompose/blob/main/CONTRIBUTING.md'},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: 'https://github.com/ViewCompose/ViewCompose'},
            {label: 'Maven Central', href: 'https://central.sonatype.com/namespace/com.viewcompose'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} ViewCompose contributors.`,
    },
    colorMode: {
      defaultMode: 'light',
      respectPrefersColorScheme: true,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['kotlin', 'java', 'groovy'],
    },
  } satisfies ThemeConfig,
};

export default config;
