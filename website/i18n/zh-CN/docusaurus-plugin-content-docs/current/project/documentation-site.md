---
translation_source: project/documentation-site.md
translation_source_hash: 6e06cabf83fdb946006dc47498fcc69fb7df40cc009aafd94f172fc3de095d05
translation_status: current
---

# 文档站点运维

## 目的

本文是 ViewCompose 托管文档系统的运维指南。内容规则以[文档治理规范](./documentation-governance.md)
为准，平台选择与取舍记录在
[ADR-0001](../architecture/decisions/0001-hosted-documentation-platform.md)。

## 构建流水线

生产产物通过七个明确阶段组装：

1. `verifyDocumentLanguages` 检查权威页与本地化页的标题和叙述符合目录语言，并确认每个有效
   公共页面都有必需 locale 镜像。
2. `verifyDocumentationStructure` 检查源码位置、目录一致性、可达性和仓库链接。
3. `verify:translations` 检查必需中文覆盖、英文源指纹、显式过期状态和过期警告。
4. `verifyCompleteViewComposeApiDocs` 按 source revision 对不可变发布注册表分组，在临时工作区
   重建每个 revision，运行当前维护的 Dokka 工具，把每个制品/版本树复制到忽略目录
   `website/generated/api/`，并验证完整 manifest、不可变路由、别名和固定源码链接。列在
   `release.unpublishedModules` 中的产物只从工作树生成可变 `current` API，不伪造不可变版本路由。
   若冻结 revision 早于依赖契约注册表，临时文档工作区只为配置当前 Dokka 工具生成空契约行；
   编译仍以该 revision 的 Gradle 构建为准，这些临时契约不会参与发布。
5. 站点生成器读取发布元数据、不可变注册表和 `docs/modules/README.md`，从同一冻结 Git
   revision 生成目录和每个已发布制品/版本的模块手册快照，不维护第二份注册表。
6. Docusaurus type-check 并构建手写文档、站点界面、生成 API、本地化搜索索引和兼容重定向，
   同时输出 `en` 与 `zh-CN` 到 `website/build/`；坏链和坏锚点均为错误。
7. 构建 wrapper 验证跨语言站点外壳行为，审核 Docusaurus 自有 HTML 无障碍，并限制构建时间、
   总产物、JavaScript、CSS 和各语言搜索索引。Dokka HTML 由 API 生成器独立保证完整性，不混入
   站点模板无障碍门禁。

从仓库根目录运行完整本地验证：

```bash
./gradlew verifyDocumentationStructure verifyCompleteViewComposeApiDocs
cd website
npm ci
npm run test:scripts
npm run verify:languages
npm run verify:translations
npm run typecheck
npm run build
```

`npm run build` 包含无障碍和预算门禁；`npm run verify:site` 可在不重建时复查现有
`website/build/`。

本地迭代可用 `-PviewComposeDocsModules=artifact-a,artifact-b` 限制 Dokka 制品集合，生产构建
不得使用该捷径。

React、navbar、footer 或 sidebar 新增消息 key 时运行 `npm run write-translations`。它只补充
缺失 JSON，不覆盖已审阅中文。Markdown 镜像、源指纹、必需层级和恢复流程见
[本地化工作流](localization.md)。

## 搜索、重定向与质量预算

英文和简体中文都在构建时生成本地全文索引；部署后无需托管服务、凭据、分析或网络请求。
搜索 UI 文案来自标准 `zh-CN` 消息目录。

单 Locale 搜索索引预算为 5.5 MiB。加入可搜索的[多设计系统架构标准](../architecture/design-systems.md)、
ADR-0005 及包含大量证据的
[有效执行计划](https://docs.viewcompose.com/project/plans/multi-design-system-high-fidelity)后，实测英文
索引约 4.1 MiB、中文约 4.4 MiB，因此预算首次从 4 MiB 上调。完整 One UI 与 Overlay 架构记录
以及新增的九份中文镜像进入索引后，完整构建实测英文约 4.4 MiB、中文约 4.7 MiB，因此经审查的
上限调整为 5 MiB。加入 Host 所有的 Android 资源环境与事务式 Effect 生命周期契约后，实测
英文索引约 4.7 MiB、中文约 5.1 MiB，因此经审查的上限调整为 5.5 MiB。保持这些 Runtime 核心
契约可搜索具有直接读者价值，优先级高于按路径排除。后续提升仍必须提供新的测量结果与读者价值
说明；普通文档增长不会自动放宽预算。

兼容重定向保留 `/docs`、`/getting-started`、`/compose-migration` 和
`/migrate-from-compose`，包括 locale 前缀形式。只为明确的历史或推广路由增加重定向，权威
文档路径仍是唯一真相源。

版本化阈值位于 `website/site-budgets.json`。不可变 Dokka 产物只以 `/api/**` 为权威路径；
Docusaurus 完成各 locale 构建后，受支持的构建入口会删除 `/zh-CN/api/**` 等带 locale 前缀的
静态副本。中文页面直接链接权威 API 树，因此这些副本只增加存储，并不提供本地化内容或受支持
路由。

预算模型把预期的发布历史增长与真正的回归分开：非 API 产物上限为 40 MiB；不可变
artifact/version 树与未发布制品的工作树 `current` Dokka 共用 API 树预算，平均上限为
4.5 MiB，任一单独树不得超过 24 MiB。只有 manifest 与重定向别名使用独立的 1 MiB 路由
配额。其他上限为 Docusaurus 构建 120 秒、JavaScript 总计 8 MiB/单文件 768 KiB、CSS
128 KiB、各 locale 搜索索引 5.5 MiB。门禁也会拒绝任何带 locale 前缀的 API 副本。提高阈值
必须附有读者或发布价值的测量说明。

无障碍检查覆盖站点自有英文与本地化页面，检查文档语言、title/main landmark、标题顺序、
accessible name、图片替代文本、表头、iframe title 和重复 ID；重定向 stub 与 Dokka 生成页
不在范围内。改变 Dokka 模板时单独审查生成 API 无障碍，不得削弱当前门禁。

站点外壳检查要求两种语言的主页使用同一个显式浏览器存储 namespace，确保切换语言时保留读者
选择的亮色或暗色模式；同时拒绝在任一主页重新出现已删除的独立 Maven 坐标横幅。

## 发布版本与别名

不可变 API 路径为 `/api/<artifact>/<version>/`。`current` 跟随仓库当前登记版本；产物首次发布
前，`current` 直接包含从工作树生成的 Dokka，且不存在版本化路由。`latest` 只为稳定版本生成，
alpha、beta、RC、snapshot、preview、development 和 EAP 不得成为 `latest`。

不可变模块手册快照路径为 `/modules/<artifact>/<version>`；无版本路径继续指向当前维护手册。
历史手册只生成权威英文快照，包括等价的 `zh-CN` 路由，避免 locale 路径冒充未经审阅的历史
翻译。

每个 `module.<artifact>.version` 对应一个 40 位 Git SHA
`module.<artifact>.sourceRevision`。只追加的
`gradle/viewcompose-documentation-releases.properties` 保存每个制品/版本/revision。Dokka
把模块根目录映射到不可变 revision，输出门禁拒绝缺失或可移动的源码链接。

由于记录提交会改变元数据提交，发布分两步：先在一个提交冻结模块源码、注释、编译样例和手册；
再用仅含元数据的发布提交追加历史记录并更新版本/revision。冻结提交必须推送且在 Git 历史可达。

`release.retiredModules` 让被替代坐标继续保留在不可变文档历史中，但不会重新进入活动模块目录；
API 首页会把它们列入独立的 Retired 历史分组。`release.unpublishedModules` 只允许包含尚未首发的
活动产物；API 首页会链接其工作树 `current` 输出并标记为 unreleased。首次发布时，必须在追加
第一条不可变文档记录的同一个元数据变更中把它移出该列表。

`verifyAssembledViewComposeApiDocs` 验证本地选择的子集；部署与完整目录 CI 必须使用
`verifyCompleteViewComposeApiDocs`，后者拒绝 partial selection。站点还验证两个 locale 的
所有 API 和模块手册路由。当前模块均为预发布版本，因此不生成 `latest`。

生成 HTML、目录和手册快照都不提交。干净 checkout 从注册表记录的不可变 revision 重建完整
历史，因此文档工作流 checkout 完整 Git 历史，不使用 shallow clone。

每次模块发布：

1. 在一个提交冻结待发布源码、源码注释、编译样例和模块手册；
2. 在仅含元数据的提交追加注册表并更新发布版本与 `sourceRevision`；
3. 发布前运行 publishing 配置门禁、完整 API 验证器和生产站点构建。

## 持续集成与部署

`.github/workflows/documentation.yml` 构建受影响 PR，但不部署。推送到 `main` 或在 `main`
手工运行时，生成完整站点并通过受保护的 `github-pages` environment 部署。

部署任务只有在正式域名冒烟验证通过后才算成功。该验证会访问英文和简体中文模块目录，以及
所有当前模块手册；它还会验证两个目录和一个代表性当前手册的不带尾斜杠形式，以保护 GitHub
Pages 的兼容行为。验证会拒绝 HTTP 错误、以 HTTP 200 返回的 Docusaurus“找不到页面”、未由
主文档插件渲染的页面，以及缺少任一当前模块链接的目录。验证会为 CDN 传播进行短暂重试，若
仍失败则令受保护的 Pages environment 失败，而不会把损坏的发布标记为成功。

仓库 Pages source 必须设置为 **GitHub Actions**。入库 `CNAME` 声明
`docs.viewcompose.com`；DNS 将 `docs` CNAME 指向 `viewcompose.github.io`，GitHub 验证域名后
再启用 HTTPS 强制。

Maven Central、签名、域名注册商、分析或搜索管理凭据均不得入库。部署使用 GitHub 短期 Pages
identity token。

## 故障恢复

- 源验证失败时修复权威文档或目录，不削弱门禁。
- 发布历史失败时追加缺失的不可变记录或修正未发布元数据，不重写已发布制品/版本。
- Dokka 失败时用制品子集复现并修复源码/API 配置。
- Docusaurus 坏链/锚点保持严格；只有生成的静态 API 链接享受明确豁免。
- 预算失败时区分非 API 产物、API 树平均值、单个不可变或未发布 `current` API 树、路由开销
  和 locale 重复副本；修复回归，或记录并审查确有必要的阈值变化。不得恢复会因合法追加
  不可变发布记录而失败的固定总产物上限。
- 无障碍失败时修复页面或主题，不削弱门禁。
- 语言或翻译验证失败时先审阅和同步中文语义，再更新指纹；必需页面不得过期。
- 语言放置验证失败时修正文叙述位置或缺失的必需镜像；真实外语 UI 字面量使用代码格式，不得
  削弱分类器。
- 部署失败时保留上一次 Pages 版本，检查设置和 environment 后再重跑。
- Pages 产物健康但自定义域名失败时，单独诊断 DNS 与域名验证。

## 最近验证

2026-08-06：干净完整历史构建从冻结 revision 重建 69 个不可变制品版本，并从工作树生成 9 个
未发布 `current` API 树；不可变源码链接、manifest、退役历史、current/unreleased 与仅稳定版
`latest` 验证均通过。生产站点验证 69 个英文模块手册快照、69 个 `zh-CN` 英文回退快照、语言
放置、80 个最新中文镜像、本地搜索、兼容重定向和 310 个站点自有无障碍页面。产物总计
316.3 MiB，非 API 产物 32.9 MiB，78 个 API 树平均 3.6 MiB，路由开销低于显示精度
0.1 MiB，最大 JavaScript 650 KiB，完整站点构建 24.2 秒。
