---
translation_source: project/documentation-site.md
translation_source_hash: 51872d016ff16c63c132be3915a785333a5c16b61a58502ddfac8ffd7dcb4998
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
4. `verifyCompleteViewComposeApiDocs` 按 source revision 对不可变发布注册表分组，为维护中的
   生成器输入计算指纹，并验证逐 revision 完整性记录；该记录包含精确条目集合，以及每个生成文件
   的大小和 SHA-256。有效的恢复组直接复用；缺失、陈旧、格式错误、多文件、少文件、符号链接或
   摘要不匹配的组会先删除并在临时工作区重建，再继续校验路由、别名、manifest 和固定源码链接。
   缺失的冻结提交只按完整 SHA 补取，绝不替换为可移动引用。历史工作区只接收其 source revision
   组对应的发布记录；早于当前构建契约的 revision 只获得不会进入发布产物的临时配置垫片。
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

质量报告位于 `build/reports/documentation/site-quality-report.json`，不进入部署/预算树；复查
`website/build/` 会重现构建结果。

本地迭代可用 `-PviewComposeDocsModules=artifact-a,artifact-b` 限制 Dokka 制品集合，生产构建
不得使用该捷径。

`build/versioned-api-cache/integrity-manifest.json` 是生成的缓存状态，不是第二份发布注册表，也不是
可部署 API 资源。完整缓存键由
逐 revision 指纹派生；每个 revision 指纹覆盖不可变的产物/版本/源码三元组集合和当前生成器实现。
别名及未发布工作树 `current` 明确不参与不可变复用，每次装配都会重建。
`VIEWCOMPOSE_API_DOCS_MAX_PARALLEL_REVISIONS` 只接受 `1` 或 `2`；在获得可接受的托管 runner
进程树内存测量、确认两个 2 GiB Gradle/Dokka 进程可以并行前，CI 固定为 `1`。

Governance V2 资产是仓库输入，不是第二份站点注册表：Phase 0A 冻结 schema，Phase 0B 通过
compiled quality owner 报告，Phase 2 阻止新债务。已提交的
`website/src/data/capability-reference.json` 数据集只能通过
`./gradlew updateDocumentationCapabilityReference` 主动重写；校验会独立派生并逐字节比较预期
模型。本地化 `/reference/` 页面消费这一棵树，`/api/` 则继续提供按产物和版本生成的完整
Dokka 输出。

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
路由。共享社交卡片同样使用唯一根路径绝对 URL，受支持构建会删除其 Locale 副本。

预算模型把合法发布历史增长与真正回归分开。当前上限为：非 API 产物 46.9 MiB、
API 树平均 4.5 MiB/单树 24 MiB、API 路由开销 1 MiB、JavaScript 总计 8 MiB/单文件
768 KiB、CSS 128 KiB、单 Locale 搜索索引 6.25 MiB，以及 Docusaurus 构建 120 秒。
仍然禁止生成带 Locale 前缀的 API 副本。

非 API 上限从 41 MiB 升至 46.9 MiB 前均经过成对归因和内容收敛。2026-08-26 Governance V2
门禁变更的首次同语料构建为 49,185,235 B，超出上限 7,020.6 B（+0.0143%）；在保留有效棘轮
契约的前提下合并重复叙述并删除未使用的 Locale 社交卡片副本后，减少 769,236 B（-1.5640%）
至 48,415,999 B，留下 762,215.4 B（1.5499%）余量，结论为 `improved`。该测量只覆盖一次本地
生产构建；停止条件不变：不得提高阈值，下次失败仍须改善表示。不得仅为回收预算而删除当前公共
契约或合法不可变 API 历史。

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

`.github/workflows/documentation.yml` 对每个 PR 都保持存在。独立影响规划 Job 只配置
`tools/viewcompose-quality-build`，在 Job Summary 公布源码归属分类，并且只有文档、`website`、发布
模块生产输入或保守全量回退才选择高成本文档子任务。稳定的 `Build documentation` Context 是
`always()` 结果门面：只有规划成功且明确未选择子任务时，跳过才成功；规划失败或已选子任务失败仍会
阻断。推送到 `main` 或在 `main` 手工运行时始终选择完整子任务，只有它验证过的 Pages 产物才能通过
受保护的 `github-pages` environment 部署。

已选文档子任务会在恢复 `website/generated/api` 前计算不可变生成器指纹和完整历史指纹。PR 只读
缓存，只有成功的 `main` 子任务可以写入。主键按运行唯一，因此损坏恢复后可以用同一指纹的新键替代
旧归档；有序恢复前缀先匹配同一完整指纹，再匹配同一生成器产生的最新缓存。任何恢复都不能只凭键
名信任：装配器逐组验证，并在 Job Summary 输出 hit、partial、miss、recovery、生成组、无效组、
并行度和耗时。源码/语言/翻译门禁只运行一次，目录只生成一次，随后 CI 调用 prepared type-check 与
站点构建入口，避免 npm 生命周期钩子重复同一批预构建工作。缓存服务恢复或保存失败时会降级为完整
生成或跳过写入，不会绕过校验器，也不会阻断原本有效的 Pages 产物。

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
- API 缓存组完整性失败时保留自动逐组重建；不得手工修改 manifest、接受只命中键名的结果、从 PR
  保存缓存或绕过完整 API 校验。恢复成功的 `main` 会为同一指纹写入更新的唯一键。
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

- **2026-08-26，不可变 API 缓存本地验收：**完整 100 条可部署历史由五个源码 revision 组成，
  占用 `427 MiB`；非部署完整性状态占用 `6.7 MiB`。冷启动以串行方式在 `411.7 s` 内生成全部五组，
  完整 Gradle 校验耗时 `6 min 58 s`。相同输入复跑时对 26,096 个不可变文件做完整性校验，复用
  `5/5` 组且没有启动历史
  Gradle/Dokka，装配耗时 `2.1 s`，完整校验耗时 `5.42 s`，减少 `98.7%`。31 条记录的大组生成期间，
  抽样到的活动进程 RSS 约为 `1.75 GiB`；这只是本地时间点采样而非托管 runner 峰值测量，因此并发度
  保持 `1`。随后在双 revision 的 `viewcompose-image-glide` 故障用例中故意修改一个生成 HTML；
  下一次运行复用有效组，仅拒绝并在 `32.2 s` 内重建损坏组，并通过既有 manifest、路由、别名和
  不可变源码检查。本地缓存结论为 **improved**；下一步验收托管缓存的恢复、保存和命中行为。

- **2026-08-25，Governance V2 Phase 0A：**首版双语契约候选超过不变的 46.9 MiB 非 API
  上限 42,041 字节。收敛重复规范并把生成质量报告移出部署树后降至 49,175,712 字节，余量
  2,502 字节；构建和构建后复查均通过。结论为**混合**：首版表示回退，收敛与可重复校验在不
  删除契约、不提额的情况下纠正了问题。本地构建不覆盖部署/CDN/网络行为和独立预算的 API
  产物；Phase 0B 必须复用编译模型并守住上限。

Git 历史保存早期 Paging 和站点检查点；它们不授权删除当前契约、无证据提额或重新扩张副本。

</div>
