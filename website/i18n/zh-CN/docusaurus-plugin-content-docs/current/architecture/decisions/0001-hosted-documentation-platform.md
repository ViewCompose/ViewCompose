---
translation_source: architecture/decisions/0001-hosted-documentation-platform.md
translation_source_hash: 1a4ceab3a7410f235dc64e43dd13db28675d8a6cfe954b32767e3a3f676e9f36
translation_status: current
---

# ADR-0001：托管文档平台

## 状态与日期

已接受 — 2026-08-02。

## 背景

ViewCompose 需要一套统一的公开文档系统，承载框架原理、教程、Compose 迁移、Android Studio
工具和自动生成的 Kotlin/Java API Reference。公开 Maven 制品各自拥有独立版本，因此模块手册
和 API Reference 不能假设整个仓库共用一条发布列车。

描述代码的文档必须与代码在同一 PR 中修改。生成的 HTML 不提交到仓库；托管层必须可以替换，
且替换时不改变公共 URL。初始方案除现有域名外不应产生持续基础设施成本。

## 决策

1. 手写文档源继续放在仓库 `docs/` 目录。
2. Docusaurus 3 从该目录构建公开站点，提供导航、国际化内容、版本化文档和可扩展 UI 组件。
3. Dokka 2 按 Maven 制品和版本生成 Kotlin/Java API Reference。每个模块版本绑定完整且不可变
   的源码提交，使生成的行链接永远不跟随 `main`。托管格式为 Dokka HTML，生成结果不入库。
4. GitHub Actions 验证 PR 并组装生产产物，只有 `main` 可以部署。
5. GitHub Pages 在 `docs.viewcompose.com` 托管静态结果。
6. 站点源码放在 `website/`；生成的 API 输出放在被忽略的构建或生成目录。
7. 已发布制品元数据与 `docs/modules/README.md` 驱动模块导航，站点配置不复制模块注册表。
8. 公共路由遵循文档治理规范中的生成器无关契约。
9. 搜索先使用可本地静态运行的方案；生产域名可用后，可切换到免费的 Algolia DocSearch。

## 评估过的替代方案

### VitePress 方案

VitePress 构建更轻量，内置本地搜索且国际化体验良好。但独立文档版本需要更大的自定义子系统，
因此未选择。

### Material for MkDocs 方案

Material for MkDocs 写作体验和浏览器搜索都很优秀，但版本化与多语言模型依赖额外项目和工具，
会增加独立模块版本之间的协调成本。

### 专用托管文档服务

托管服务可以降低初始搭建成本，但会把文档变化与代码审查分离，引入服务专属存储和计费，并
削弱对模块版本模型的控制。

## 结果与取舍

- 仓库在 Gradle 之外增加一套 Node 站点工具链。
- 站点构建必须保持在 GitHub Pages 的部署与体积限制内。
- Dokka 输出需要缓存或增量生成，普通文档变化不应重建所有历史 API 版本。
- Docusaurus 模块配置必须从权威目录生成，不能手工复制。
- 自定义域名让未来的托管迁移对读者透明。
- 不支持动态服务端功能，部署产物始终是静态站点。

## 受影响的模块与契约

`gradle/viewcompose-publishing.properties` 中登记的所有制品都参与 API Reference 生成。文档
结构、发布工作流和源码注释质量门禁也受到影响。

## 验证与落地

1. 在本地和 PR 上构建 Docusaurus 站点。
2. 先为选定制品生成 Dokka HTML，再覆盖完整发布目录。
3. 验证不可变版本路由、`current`/稳定版 `latest` 别名、源码链接、完整模块目录一致性和站点体积。
4. 仅允许 `main` 部署 Pages。
5. 第一次 Pages 部署成功后配置并验证 `docs.viewcompose.com`。
6. 当前文档站稳定后加入逐模块发布快照和 API 历史保留。
