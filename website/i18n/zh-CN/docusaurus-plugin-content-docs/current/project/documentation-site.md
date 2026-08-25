---
translation_source: project/documentation-site.md
translation_source_hash: c97e493b68a712a8171ec4b0986b8e70007d6739941ecda66c9d9b0a2400e2ea
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
   重建每个 revision，运行当前 Dokka 工具，并验证全部 manifest、路由、别名和固定源码链接。
   缺失的冻结提交只按完整 SHA 补取，绝不替换为可移动引用。未发布产物只有工作树 `current`；早于
   当前构建契约的 revision 只获得不会进入发布产物的临时配置垫片。
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

搜索索引保留页面摘要、标题、公共契约和命令指南。穷举式缺陷证据表和按日期记录的测量台账
仍会完整渲染并可直接链接，但使用 `search-partition-detail`，避免重复的历史明细主导本地索引。
每个排除块都必须保留相邻且可搜索的标题与摘要；API 契约、命令参考和面向读者的指南不得使用
该分区。

当活动计划索引保留可搜索的目的与范围摘要，并且所有长期公共契约和命令仍位于可搜索的 owner 文档
时，体积特别大的临时执行计划保持为仓库专属 production draft。Canonical 索引继续使用仓库相对
源码链接，保证文档图完整；严格 Markdown link hook 只在确认目标包含 `draft: true` 后，于站点构建
期间把链接改写为精确 GitHub 源码 URL。因此读者仍能从公共索引评审目标，同时临时执行状态不会进入
渲染产物、locale fallback、搜索或 sitemap。目标缺失、非 draft 坏链或其他未解析路由仍会使构建失败。

单 Locale 搜索预算为 6.25 MiB。经过审阅的双语架构与公共契约曾把它从 4 逐步调整到 6 MiB；
Lazy Collection 分支先对穷举计划与 Benchmark 明细分区，才形成最终 6.25 MiB 上限。精确转换
证据收敛在下方。再次触顶时必须实施结构化索引分段，不能继续只做内容分区或提高阈值；API 与命令
指南继续参与搜索。

兼容重定向保留 `/docs`、`/getting-started`、`/compose-migration`、
`/migrate-from-compose`，以及有效计划归档前已经公开的路径，包括 locale 前缀形式。只为明确
的历史或推广路由增加重定向，权威文档路径仍是唯一真相源。

版本化阈值位于 `website/site-budgets.json`。不可变 Dokka 产物只以 `/api/**` 为权威路径；
Docusaurus 完成各 locale 构建后，受支持的构建入口会删除 `/zh-CN/api/**` 等带 locale 前缀的
静态副本。中文页面直接链接权威 API 树，因此这些副本只增加存储，并不提供本地化内容或受支持
路由。

预算模型把合法发布历史增长与真正回归分开。当前上限为：非 API 产物 46.9 MiB、
API 树平均 4.5 MiB/单树 24 MiB、API 路由开销 1 MiB、JavaScript 总计 8 MiB/单文件
768 KiB、CSS 128 KiB、单 Locale 搜索索引 6.25 MiB，以及 Docusaurus 构建 120 秒。
仍然禁止生成带 Locale 前缀的 API 副本。

非 API 上限从 41 MiB 演进到 46.9 MiB 的每一步，都先通过成对构建把增长归因到长期双语契约，
并通过表示审查移除可避免的重复。下方把已完成测量收敛为紧凑记录，不再在有效契约中重复每个
执行阶段。任何阈值调整都必须提供同语料的绝对值和归一化结果、读者或发布价值、结论、限制以及
下一项停止条件。

达到 46.9 MiB 边界后，失败分支必须先合并已完成证据或改变站点表示。不得仅为回收预算而删除当前
公共 API、架构、迁移、教程或模块契约；合法不可变 API 历史继续受独立的单树预算约束。

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

历史手册指向另一个已发布模块的相对链接会改写为该模块的版本化路由；指向临时执行计划的链接
则固定到 GitHub 上的手册 Source Revision，确保计划完成或归档后不会破坏不可变手册快照。

只追加的发布注册表把每个版本与完整不可变源码 SHA 配对；缺失或可移动链接会失败。先冻结源码和
手册，再在第二个提交追加注册表/版本元数据。冻结 SHA 必须保持可达，不能替换为 squash 提交。

`release.retiredModules` 在活动目录外保留被替代历史；`release.unpublishedModules` 只允许首发前
的工作树 `current`，追加第一条不可变记录时必须移除对应产物。

`verifyAssembledViewComposeApiDocs` 接受本地显式子集；部署使用完整验证器并检查两个 Locale 的
全部 API/手册路由。当前预发布模块不生成 `latest`。

生成产物不提交。干净 Checkout 从注册 Revision 恢复历史，只补取缺失的精确 SHA，不依赖临时分支。

每次模块发布：

1. 在一个提交冻结待发布源码、源码注释、编译样例和模块手册；
2. 在仅含元数据的提交追加注册表并更新发布版本与 `sourceRevision`；
3. 发布前运行 publishing 配置门禁、完整 API 验证器和生产站点构建。

## 持续集成与部署

`.github/workflows/documentation.yml` 构建受影响 PR，但不部署。推送到 `main` 或在 `main`
手工运行时，生成完整站点并通过受保护的 `github-pages` environment 部署。

部署只有在正式域名冒烟测试访问两个 Locale 的目录、全部当前手册和代表性无尾斜杠路由后才成功。
HTTP、渲染 Not Found、错误插件或目录缺项在有界 CDN 重试后仍会令部署失败。

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

<div className="search-partition-detail">

- **2026-08-25，Paging Phase 6 Demo/文档 Slice：**在同一份本地 macOS Lockfile 上，Phase 5
  基线、未收敛 Phase 6 候选与加入本条证据前的收敛候选，其非 API 产物分别为 49,161,510、
  49,231,869 和 49,090,456 字节。首个候选超过未调整的 46.9 MiB 上限 53,655 字节；把执行计划
  中重复的已交付签名和逐阶段证据迁移到所属模块手册、Lazy Collections 指南、生成 API 树与 Git
  历史后，相对基线减少 71,054 字节（-0.145%），测得余量为 87,758 字节。结果为**混合**：首版
  表示方式发生回退，收敛后的表示方式同时改善了读者指引与存储量。加入本条证据记录后，语言、
  翻译、路由、无障碍和全部预算仍通过。本次同主机对照不衡量部署传输、CDN 压缩、真实数据库/
  网络行为；生成 API 产物继续由
  独立预算约束。Phase 7 与归档工作不得重新扩张已完成契约的副本。

这里只展开最新的同语料决策；Git 历史保留 2026-08-06 至 Phase 0 检查点被后续结论取代的
检查点明细。那些测量建立了不可变 API 历史重建、双语搜索与无障碍门禁、计划和证据分段，
并要求非 API 上限从 41 MiB 演进到 46.9 MiB 前先做重复内容收敛。历史中既有长期契约增长
获接受，也有表示方式改进；它们都不授权删除当前契约或绕过下一次停止条件。

</div>
