import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type {Options, ThemeConfig} from '@docusaurus/preset-classic';
import {createLocalizedMarkdownLinkResolver} from './src/config/localizedMarkdownLinks';

const siteDir = __dirname;
const docsDir = `${siteDir}/../docs`;
const locales = ['en', 'zh-CN'];
const resolveLocalizedMarkdownLink = createLocalizedMarkdownLinkResolver({
  siteDir,
  docsDir,
  locales,
  defaultLocale: 'en',
  trailingSlash: true,
});

const config: Config = {
  title: 'ViewCompose',
  tagline: 'Declarative Android UI, powered by the native View system',
  url: 'https://docs.viewcompose.com',
  baseUrl: '/',
  organizationName: 'ViewCompose',
  projectName: 'ViewCompose',
  // GitHub Pages distinguishes `foo.html` from a sibling `foo/` directory. Module manuals need
  // both `/modules/<artifact>` and `/modules/<artifact>/<version>`, so directory output is the
  // only static layout that can serve both paths without one shadowing the other.
  trailingSlash: true,
  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',
  future: {
    v4: true,
  },
  storage: {
    type: 'localStorage',
    // Locale builds use different base URLs. A fixed namespace keeps one color-mode choice shared
    // across those builds instead of deriving a different storage key for each locale.
    namespace: 'viewcompose-docs',
  },
  i18n: {
    defaultLocale: 'en',
    locales,
    localeConfigs: {
      en: {
        label: 'English',
        htmlLang: 'en',
      },
      'zh-CN': {
        label: '简体中文',
        htmlLang: 'zh-CN',
      },
    },
  },
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: resolveLocalizedMarkdownLink,
    },
  },
  plugins: [
    [
      '@docusaurus/plugin-content-pages',
      {
        id: 'released-module-manual-pages',
        path: 'src/generated/moduleManualPages',
        routeBasePath: '/',
        showLastUpdateAuthor: false,
        showLastUpdateTime: false,
      },
    ],
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {
            from: '/docs',
            to: '/documentation',
          },
          {
            from: '/getting-started',
            to: '/tutorials/getting-started',
          },
          {
            from: ['/compose-migration', '/migrate-from-compose'],
            to: '/migration',
          },
          {
            from: [
              '/tutorials/task-list-foundations',
              '/tutorials/task-list-input-and-lists',
              '/tutorials/task-list-theme-and-navigation',
              '/tutorials/task-list-overlays-and-android-views',
              '/tutorials/task-list-animation-and-gestures',
              '/tutorials/task-list-performance-and-diagnostics',
            ],
            to: '/tutorials',
          },
          {
            from: [
              '/project/plans/image-loading-pipeline-generalization',
              '/project/plans/maven-dependency-contract-convergence',
              '/project/plans/android-views-performance-control',
            ],
            to: '/project/plans',
          },
        ],
      },
    ],
  ],
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
          editLocalizedFiles: true,
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
  themes: [
    '@docusaurus/theme-mermaid',
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: 'filename',
        indexDocs: true,
        indexBlog: false,
        indexPages: false,
        docsRouteBasePath: '/',
        docsDir: '../docs',
        language: ['en', 'zh'],
        highlightSearchTermsOnTargetPage: true,
        explicitSearchResultPath: true,
        searchResultLimits: 8,
      },
    ],
  ],
  themeConfig: {
    image: 'img/social-card.png',
    navbar: {
      title: 'ViewCompose',
      hideOnScroll: true,
      items: [
        {
          type: 'doc',
          docId: 'tutorials/getting-started',
          label: 'Get started',
          position: 'left',
        },
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
          type: 'localeDropdown',
          position: 'right',
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
            {label: 'Get started', to: '/tutorials/getting-started'},
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
