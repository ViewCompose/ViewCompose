---
translation_source: project/documentation-site.md
translation_source_hash: 4214f3e84f6c8250a7c4576304bb456f83aa475bf17814df0ebcc06cc6109786
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
4. `verifyCompleteViewComposeApiDocs` 按 source revision 对不可变发布分组，校验精确条目集合以及
   每个生成文件的大小和 SHA-256。有效组直接复用；陈旧、格式错误、缺项、多项、符号链接或摘要
   不匹配的组会在临时工作区重建，再校验路由、别名、manifest 和固定源码。缺失 revision 只按
   完整 SHA 获取。历史工作区只接收本组发布记录；早于当前构建契约的源码只使用不发布的兼容垫片。
5. 站点生成器读取发布元数据、不可变注册表和 `docs/modules/README.md`，从同一冻结 Git
   revision 生成目录和每个已发布制品/版本的模块手册快照，不维护第二份注册表。无论不可变 API
   产物来自恢复还是重建，生成器都会先按精确完整 SHA 解析每个唯一冻结 revision，再读取快照。
6. Docusaurus type-check 并构建手写文档、站点界面、生成 API、本地化搜索索引和兼容重定向，
   同时输出 `en` 与 `zh-CN` 到 `website/build/`；坏链和坏锚点均为错误。Docusaurus 解析 `slug`
   等展示字段后，Remark Transform 会从浏览器页面 Chunk 中移除 Governance V2 所有权字段与翻译
   审核指纹，并把已验证、指向 `docs/` 外仓库文件的链接改写为 GitHub 源码 URL。源 Markdown
   继续使用仓库相对链接并保持权威，生产与测试源码不再复制进站点产物；生成的 Capability
   Reference 仍是公开关系模型。
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

Governance V2 资产是仓库输入，不是第二份站点注册表：schema 与确定性发现共同输入 compiled
零 Exception strict gate，所有 issue 都会阻断。已提交的
`website/src/data/capability-reference.json` 数据集只能通过
`./gradlew updateDocumentationCapabilityReference` 主动重写；校验会独立派生并逐字节比较预期
模型。本地化 `/reference/` 页面消费这一棵树，`/api/` 则继续提供按产物和版本生成的完整
Dokka 输出。

React、navbar、footer 或 sidebar 新增消息 key 时运行 `npm run write-translations`。它只补充
缺失 JSON，不覆盖已审阅中文。Markdown 镜像、源指纹、必需层级和恢复流程见
[本地化工作流](localization.md)。

## 搜索、重定向与质量预算

每个 Locale 都从渲染文档生成无需凭据的本地搜索索引，并保留页面摘要、标题、公共契约和命令
指南。穷举证据表和日期台账只有在保留相邻可搜索标题与摘要时才能使用
`search-partition-detail`；API 契约、命令参考和面向读者的指南不得使用该分区。搜索 UI 文案继续
由标准 `zh-CN` 消息目录审阅。

当活动计划索引保留可搜索的目的与范围摘要，并且所有长期公共契约和命令仍位于可搜索的 owner 文档
时，体积特别大的临时执行计划保持为仓库专属 production draft。Canonical 索引继续使用仓库相对
源码链接，保证文档图完整；严格 Markdown link hook 只在确认目标包含 `draft: true` 后，于站点构建
期间把链接改写为精确 GitHub 源码 URL。因此读者仍能从公共索引评审目标，同时临时执行状态不会进入
渲染产物、locale fallback、搜索或 sitemap。目标缺失、非 draft 坏链或其他未解析路由仍会使构建失败。

单 Locale 搜索预算为 6.25 MiB。经过审阅的双语架构与公共契约曾把它从 4 逐步调整到 6 MiB；
Lazy Collection 分支先对穷举计划与 Benchmark 明细分区，才形成最终 6.25 MiB 上限。精确转换
证据收敛在下方。再次触顶时必须实施结构化索引分段，不能继续只做内容分区或提高阈值；API 与命令
指南继续参与搜索。

渲染后的代码块仍完整保留在 Owner 页面并保留可编译源码链接，但本地全文搜索只索引周边解释，不再
重复每个代码 Token。精确公共 Symbol 仍可通过模块 API 清单和生成式 Reference 发现。该边界在不
隐藏页面、样例、命令契约或迁移路由的前提下减少双语索引重复；若某条命令或 Symbol 只存在于代码
围栏，应该把名称补入 Owner 正文，而不是让全部代码正文重新进入索引。

兼容重定向保留 `/docs`、`/getting-started`、`/compose-migration`、
`/migrate-from-compose`，以及有效计划归档前已经公开的路径，包括 locale 前缀形式。只为明确
的历史或推广路由增加重定向，权威文档路径仍是唯一真相源。

版本化阈值位于 `website/site-budgets.json`。不可变 Dokka 只以 `/api/**` 为权威路径；受支持
构建会删除 Locale API 副本和冗余社交卡片，因为本地化页面使用权威 API 树和同一个绝对社交卡 URL。

不可变模块手册快照以只读静态 HTML 保留本地化路由、服务端内容、样式、链接和色彩模式初始化，
但不保留重复 Hydration Script 或路由 Chunk；当前手册仍可 Hydration。门禁强制整页导航、静态
Marker 和 Script 删除。构建后 Dokka 压缩只删除生成缩进，逐字节保留字面量元素正文；不可变源码
Manifest 与缓存完整性仍位于上游。

预算模型把合法发布历史增长与真正回归分开。当前上限为：非 API 产物 47.1 MiB、
API 树平均 4.5 MiB/单树 24 MiB、API 路由开销 1 MiB、JavaScript 总计 8 MiB/单文件
768 KiB、CSS 128 KiB、单 Locale 搜索索引 6.25 MiB，以及 Docusaurus 构建 120 秒。
仍然禁止生成带 Locale 前缀的 API 副本。

上限从 41 MiB 调整到 46.9 MiB 前均经过成对归因和内容收敛。2026-08-30 的一次已审阅例外把它
调整为 47.1 MiB：必需的顶级双语“AI 接入”章节已从两条路由收敛为一条，但仍超过此前上限。
棘轮从 47.1 MiB 重新生效：新增其他路由前必须通过结构优化恢复容量；只能删除重复部署表示，不能
删除当前契约或有效发布历史。已有受测试保护的 Transform 会移除无用 Locale 副本、仅机器读取的
治理/翻译 Front Matter、不可变手册 Hydration 和生成缩进，同时保持路由与可读内容不变。历史
同语料测量及限制保留在下方源码中，不再重复进入公共运维契约。

无障碍检查覆盖站点自有英文与本地化页面，检查文档语言、title/main landmark、标题顺序、
accessible name、图片替代文本、表头、iframe title 和重复 ID；重定向 stub 与 Dokka 生成页
不在范围内。改变 Dokka 模板时单独审查生成 API 无障碍，不得削弱当前门禁。

站点外壳检查要求两种语言的主页使用同一个显式浏览器存储 namespace，确保切换语言时保留读者
选择的亮色或暗色模式；同时拒绝在任一主页重新出现已删除的独立 Maven 坐标横幅。同一门禁还会检查
最终打包的样式表，禁止在 `.navbar` 根节点上设置滤镜、变换、containment 或相关属性，因为它们会把
Docusaurus 的 fixed 移动端侧栏和遮罩限制在导航栏高度内。同一限制也适用于导航栏伪元素：部分
浏览器的合成顺序会把定位滤镜层绘制到普通流中的菜单按钮与品牌标题上方，同时仍显示定位的搜索框。
因此导航栏只使用普通背景，不再增加模糊图层。

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
名信任。由于生成器指纹包含实际 Java 与 Node runtime，工作流固定它们的完整发行版本，而不使用
浮动 major selector；变更任一版本都属于显式缓存迁移。装配器逐组验证，并在 Job Summary 输出
hit、partial、miss、recovery、生成组、无效组、
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

当前生产契约提供 133 个不可变 API 版本、模块手册和中文回退路由。协同发布收尾审计 522 页，
总输出 468.9 MiB、非 API 为 46.7/46.9 MiB；两个精确缓存命中运行复用 `6/6` 组，分别耗时
`47.3 s` 和 `43.0 s`。缓存正确性与延迟为**无实质变化**，新增历史与更窄余量为**混合**。
后续同语料收敛把非 API 从 `49,208,553` 降到 `49,086,492` 字节（`-122,061`，
`-0.248%`），在 524 个无障碍页面下属于表示**改善**。这些本地/托管观察环境不一致，不构成
稳态基准；本地缓存还缺六组，完整缓存 CI 仍是版本路由验收门禁。详细证据见
[PR 门禁计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/pull-request-gate-scaling-and-build-logic-modularization.md)
和 [Governance V2 归档](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/documentation-system-governance-v2.md)。

2026-08-29，独立双语 XML 迁移路由产生 49,373,569 个非 API 字节，超过不变上限 195,354.6
字节。撤回与已链接本地工具契约重复的路由，并收敛本运维页后，同一语料降至 49,171,339 字节：
相对被拒候选减少 `202,230` 字节（`-0.4096%`），相对无新增路由的 49,195,449 字节尝试减少
`24,110` 字节（`-0.0490%`）。一次构建留下 6,875.4 字节余量，审计 526 页；已接受的本地热构建
耗时 `34.2–59.8 s`。
表示结论为**改善**，路由、搜索契约与工具行为**无实质变化**。这只是本地热构建；未来新增
独立路由前必须先恢复其已测容量。

2026-08-30，必需的顶级双语“AI 接入”章节把概述和接入步骤收敛为一条路由后，产生 49,238,608
个非 API 字节。上一次通过的完整站点产物为 49,042,390 字节，因此该章节增加 196,218 字节
（`+0.4001%`），体积结论为**回归**；功能、版本历史、无障碍、语言、翻译和路由检查均保持成功。
因此上限经审阅调整为 47.1 MiB，留下 149,321.6 字节余量。该证据只覆盖一次本地生产构建，并且
测量未压缩输出，而不是传输体积、运行时间或查询延迟；不据此宣称性能改善。下一步保持该上限、
复用单条 AI 路由，并在增加其他 AI 文档页面前恢复已测容量。

{/* 历史测量台账保留在源码中；上方精简摘要是公开表示。

- **2026-08-28，协同发布静态历史验收：**冻结后的 127 条发布历史首次生成了 `50.0 MiB` 非 API
  产物、`8.5 MiB` JavaScript、`25.4 MiB` 的
  `viewcompose-ui-foundation/0.1.0-alpha02` API 树，以及 `24.2 MiB` 的
  `viewcompose-ui-contract/0.1.0-alpha05` API 树，因此不变的站点预算正确拒绝了该构建。硬切把
  全部 127 个 API 版本和 254 个本地化历史手册路由保留为完整静态 HTML，同时精确删除 254 个
  重复 Hydration Chunk。安全 Dokka 缩进压缩处理了 26,892 个 HTML 文件，把它们从
  322,180,811 字节降至 275,273,983 字节，减少 46,906,828 字节（`14.56%`）。最终双语生产
  构建以 491,291,403 总字节、48,523,762 非 API 字节（`46.3 MiB`）、6,936,903 JavaScript
  字节（`6.6 MiB`）、23,270,465 字节 UI Foundation（`22.2 MiB`）和 21,856,201 字节 UI
  Contract（`20.8 MiB`）通过；它审计 510 个站点页面，保留 127 对不可变 API/手册和 127 个中文
  回退路由，并在 `36.3 s` 完成 Docusaurus Wrapper。结论为 **improved**：相对被拒绝的产物，
  非 API 大约降低 `7.4%`、JavaScript 总量降低 `22.4%`、UI Foundation 降低 `12.6%`、UI
  Contract 降低 `14.0%`，没有提高任何阈值，也没有删除发布历史。该证据只覆盖一次本地生产
  构建；托管 Runner 与已部署路由是下一验收动作，当前交互式手册不属于静态历史边界。

- **2026-08-28，代码块搜索分区验收：**导入的 Lazy-list Tail 收敛及其双语公共契约在把渲染代码块
  纳入本地全文搜索时生成 49,626,056 个非 API 字节，超过不变的 46.9 MiB 上限 447,842 字节。
  在同一源码语料上，仅从索引排除渲染代码块正文后，英文搜索索引从 5,739,133 降至 5,455,358
  字节，中文搜索索引从 6,294,002 降至 5,997,618 字节；完整非 API 产物减少 580,119 字节
  （`1.1690%`）至 49,045,937 字节，留下 132,277 字节余量。456 个双语页面、渲染样例、可编译
  源码链接、无障碍检查、路由与不变预算均仍在范围内。记录本条证据后，最终构建生成 49,058,278
  个非 API 字节，留下 119,936 字节，并在 `30.6 s` 内完成 Docusaurus Wrapper；英文与中文索引
  分别为 5,457,126 和 5,999,575 字节。结论为 **improved**。该测量仅覆盖一次本地生产构建，不对
  托管构建或查询延迟作结论；以后仅出现在代码内的标识符也必须进入可搜索 Owner 正文，现有体积
  门禁则负责检测分区失效。

- **2026-08-26，不可变 API 缓存本地验收：**完整 100 条可部署历史由五个源码 revision 组成，
  占用 `427 MiB`；非部署完整性状态占用 `6.7 MiB`。冷启动以串行方式在 `411.7 s` 内生成全部五组，
  完整 Gradle 校验耗时 `6 min 58 s`。相同输入复跑时对 26,096 个不可变文件做完整性校验，复用
  `5/5` 组且没有启动历史
  Gradle/Dokka，装配耗时 `2.1 s`，完整校验耗时 `5.42 s`，减少 `98.7%`。31 条记录的大组生成期间，
  抽样到的活动进程 RSS 约为 `1.75 GiB`；这只是本地时间点采样而非托管 runner 峰值测量，因此并发度
  保持 `1`。随后在双 revision 的 `viewcompose-image-glide` 故障用例中故意修改一个生成 HTML；
  下一次运行复用有效组，仅拒绝并在 `32.2 s` 内重建损坏组，并通过既有 manifest、路由、别名和
  不可变源码检查。本地缓存结论为 **improved**；下一步验收托管缓存的恢复、保存和命中行为。

- **2026-08-26，不可变 API 缓存托管验收：**首次完整 `main` 运行按预期未命中，五个历史组装配
  耗时 `1139.4 s`，完整 API 步骤耗时 `21 min`；随后成功构建、上传、保存 `39.3 MB` 缓存并部署。
  缓存进入索引后，`main` 精确复跑在 `7 s` 内恢复缓存，校验并复用 `5/5` 组，生成组和无效组均为
  零，装配耗时 `5.7 s`，完整 API 步骤耗时 `2 min 9 s`，减少 `89.8%`。不可变缓存结论为
  **improved**。该热运行同时暴露了独立限制：版本化手册生成曾隐式依赖冷 API 重建来获取其他路径
  不可达的冻结提交。生成器现会在读取快照前解析每个唯一完整 SHA。首次修正运行选中了 Temurin
  `17.0.20+1`，而种子缓存使用 `17.0.20+8`；正确产生的不同生成器指纹触发了 `1175.9 s` 冷重建，
  随后的目录生成与完整站点构建均通过。工作流现固定 Temurin `17.0.20+8` 与 Node `24.19.0`；
  固定版本后的复跑约 `4 s` 恢复精确 `cb67…/ab01…` 种子，在 `5.5 s` 内复用 `5/5` 组，生成组与
  无效组均为零，完整 API 步骤约 `1 min 58 s`。随后版本化手册生成在没有冷重建的情况下约 `1 s`
  完成，完整生产站点任务以 `6 min 33 s` 通过。修正结论为 **improved**；Phase 4 验收完成。

- **2026-08-26，Governance V2 Navigation/Theming 观察：**PR #176 的成功文档 Child Job 在
  `5 min 21 s` 内完成，其中源码与翻译校验 `72 s`、完整版本化 API 生成与校验 `112 s`、目录生成
  `7 s`、类型检查 `2 s`、Docusaurus 构建 `53 s`。与 #174 的 `5 min 10 s` 文档 Child Job
  相比，端到端耗时变化 `+3.55%`；单个恢复状态不同的托管样本结论为 **no material change**。
  Docusaurus 步骤只占 Child Job 时间的 `16.5%`，因此证据不支持把替换 Website 技术栈作为主要
  延迟措施。#176 首次运行因移动后的锚点失败，严格链接门禁正确拒绝；修复后的复跑通过，因此
  正确性结论为 **improved**。随后本地 Theming 验收构建审计 448 个双语站点页面，受预算约束的
  Docusaurus Wrapper 用时 `60.0 s`，完整外围 npm Lifecycle 用时 `82.73 s`。局限是单个内容 PR
  无法建立 P50/P95 或缓存命中率。继续收集 Phase 6 语料，并优先优化源码校验、不可变 API 复用、
  重复 Gradle 配置和环境/依赖恢复，再重新考虑 Docusaurus、React 或 Node 迁移。

- **2026-08-26，Governance V2 Theming 后续观察：**PR #177 的成功文档 Child Job 用时
  `4 min 47 s`，比 #176 快 `34 s`（`-10.6%`）。源码与翻译校验 `68 s`、完整版本化 API 生成与
  校验 `89 s`、目录生成 `1 s`、类型检查 `2 s`、Docusaurus 构建 `46 s`。Docusaurus 仍只占
  Child Job 时间的 `16.0%`，再次确认它不是主要阶段。本次托管样本结论为 **improved**；但恢复
  状态不同，且稳定后只有两个内容 PR，因此 P50/P95 与缓存命中结论仍为 **inconclusive**。继续
  收集语料，不进行 Website 技术栈迁移。

- **2026-08-26，Governance V2 Text Input 托管后续观察：**PR #178 的成功文档 Child Job
  用时 `4 min 34 s`，比 #177 快 `13 s`（`-4.5%`）。源码与翻译校验 `65 s`、完整版本化 API
  生成与校验 `82 s`、目录生成 `1 s`、类型检查 `2 s`、Docusaurus 构建 `42 s`。Docusaurus
  只占 Child Job 时间的 `15.3%`，仍是少数阶段。本次托管样本结论为 **improved**；稳定后只有
  三个内容 PR，因此 P50/P95 与缓存命中结论仍为 **inconclusive**。保持现有技术栈并继续收集
  语料。

- **2026-08-26，Governance V2 Lazy Collections 托管后续观察：**PR #179 的成功文档
  Child Job 用时 `5 min 12 s`。源码与翻译校验 `77 s`、完整版本化 API 生成与校验 `115 s`、
  目录生成 `1 s`、类型检查 `2 s`、Docusaurus 构建 `53 s`。Docusaurus 占 Child Job 时间的
  `17.0%`，仍是少数阶段。受影响 `qaQuick` 候选以 `5 min 32 s` 通过 `1,176` 个可执行任务；
  完整 Shadow 以 `9 min 8 s` 通过 `2,342` 个，因此选择范围缩小 `49.8%`，观察耗时降低
  `39.4%`，且结论相同。串行 Shadow 观察仍延长必需关键路径，因此总体结论为 **mixed**；
  范围选择和正确性为 **improved**。这只是稳定后的第四个内容样本；在满足语料要求之前，
  保留当前 Website 技术栈和 Shadow 对照。

- **2026-08-28，文档/Tutorial-sample 语料验收：**11 个可比较的 PR（#177、#178、#179、#180、
  #182、#183、#184、#185、#203、#204、#205）都选择相同的 1,176 任务候选、不含发布产物，
  并选择文档 Child Job；每个候选都与随后 2,342 任务的完整 Shadow 得到相同成功结论。重建的
  无 Shadow required critical path 近邻秩 P50 为 `6 min 22 s`、P95 为 `7 min 17 s`；所有文档
  Child Job 都精确命中 `5/5` 个不可变 API 缓存组，生成组与无效组均为零，命中率为 `11/11`
  （`100%`）。范围与缓存复用结论为 **improved**，正确性为 **no material change**；硬切后的
  时延仍为 **inconclusive**，直到首个符合条件的托管运行记录实际路径。本次只为该精确类别启用
  无 Shadow 模式，不迁移 Website 技术栈；源码校验、API 复用和完整站点均保持不变。

- **2026-08-28，模块文档/编译样例语料验收：**11 个范围可收敛的 PR（#186、#187、#188、
  #189、#190、#191、#194、#195、#198、#199、#200）只修改文档/治理记录、发布模块的
  `src/test/samples`、受限的 Tutorial 或 Counter sample 源码、仅追加 Changeset、中文镜像和生成的
  能力目录；11 个候选都与完整 Shadow 得到相同成功结论。重建的无 Shadow 执行 P50/P95 为
  `8 min 5 s`/`10 min 4 s`，端到端 P50/P95 为 `9 min 13 s`/`11 min 18 s`。每个文档 Child Job
  都复用 `5/5` 个不可变 API 组，生成组和无效组均为零。另一组 11 个成功 `main` 样本的完整
  `qaQuick` P95 为 `20 min 41 s`，比 Phase 0 低 `16.2%`。范围、缓存复用和时延结论为
  **improved**，正确性为 **no material change**。本次只对这一精确路径类别移除完整 Shadow；模块
  生产源码、构建脚本、普通测试、代码删除/重命名和敏感工具仍保留 Shadow。Website 技术栈迁移
  仍无证据支持。

- **2026-08-28，硬切后缓存失效控制：**#219 和 #220 都精确复用 `5/5` 个 API 组，生成组和
  无效组均为零，文档 Child Job 分别用时 `5 min 23 s` 和 `6 min 2 s`。相对 #219，#220 增加
  `39 s`（`+12.1%`），不同内容之间的结论为 **no material change**。#221 修改受维护的发布生成器，
  因此正确复用 `0/5` 个组、以 `1070.8 s` 重建全部五组，Child Job 用时 `23 min 23 s`
  （`+334.4%`）。#222 把注册表扩展为六组，复用 `5/6`、以 `275.0 s` 生成一组，并以
  `10 min 12 s` 完成（`+89.5%`）。#223 把六个首发制品转入不可变历史，复用 `5/6`、以
  `258.5 s` 重建扩展组，并以 `7 min 55 s` 完成（`+47.1%`）。所有运行的无效组均为零，生产
  站点都满足不变预算；随后 #223 的 `main` 工作流完成构建、部署和线上模块路由验证。缓存判别和
  部署正确性为 **no material change**；有意的完整或局部失效成本高于精确复用，因此时延结论为
  **mixed**。这些工具/发布输入彼此异构，输出分别包含 100、127 和 133 个版本，归一化 Child Job
  时间不能代表稳态性能。工具或历史漂移仍必须只重建其指纹实际失效的组。

- **2026-08-28，硬切后前两次精确命中观察：**#225 和 #226 都恢复并验证了 `6/6` 个不可变 API
  组，生成组和无效组均为零。缓存工作分别用时 `7.1 s` 和 `4.5 s`，文档 Child Job 分别为
  `3 min 58 s` 和 `3 min 54 s`，生产站点 Wrapper 分别为 `47.3 s` 和 `43.0 s`。两个站点都保持
  469.0 MiB 总输出和 46.7/46.9 MiB 非 API 输出，API、JavaScript、CSS、无障碍和路由预算均未
  改变。从 #225 到 #226，文档 Child 耗时变化 `-1.7%`，站点耗时变化 `-9.1%`，取整后的体积没有
  变化。这两次硬切后样本的精确命中率为 `100%`，缓存正确性和精确命中行为结论为
  **no material change**；由于 Runner 与依赖状态不同，站点时延也为 **no material change**。
  这证明两个已验收无 Shadow 类别都保持文档完整性，但尚不能构成 P50/P95 语料。后续只从自然出现
  的合格工作中继续收集，并在任何已验证指纹失配时保持按组重建；仍没有理由迁移网站技术栈。

- **2026-08-26，Governance V2 Text Input 本地验收：**首次四页面任务拆分虽然构建成功，但生成
  49,245,936 个非 API 字节，超过不变的 46.9 MiB 上限 67,722 字节。把相邻的编辑/IME 与富文本/
  Receive Content 任务收敛到两个 Guide 后，四个任务边界仍然保留，生成产物减少 161,958 字节
  （`-0.33%`）至 49,083,978 字节，留下 94,236 字节余量。最终构建审计 452 个双语页面，
  Docusaurus Wrapper 用时 `60.0 s`。首版表示的结论为 **mixed**，修正后为 **improved**。本地
  样本不覆盖托管缓存与环境准备；上限保持不变，下一个内容切片继续使用同一停止条件。

- **2026-08-26，Governance V2 Lazy Collections 本地验收：**首版表示超过不变上限 9,033
  字节。收敛重复 Pager 和模块拥有的细节后，非 API 产物降至 49,168,958 字节，留下 9,256
  字节；454 个双语页面以 `36.6 s` 通过。修正后结论为 **improved**，但余量很小，因此下一个
  内容切片必须先删除或收敛现有产物，再增加新路由。

- **2026-08-26，Governance V2 Focus/Nested Scroll 本地验收：**复用现有 Modifier Architecture
  路由避免了新增页面，但首版扩展表示仍超过不变上限 21,103 字节。删除重复 Pager 代码展示并
  保留其任务契约，同时收敛架构说明后，非 API 产物降至 49,165,583 字节，留下 12,631 字节。
  修正构建审计 454 个双语页面，Docusaurus 用时 `51.7 s`。修正后结论为 **improved**；能力、
  编译区域、路由、语言、无障碍和预算门禁均通过，且未改变上限。余量仍很窄，因此 Shadows
  切片必须继续遵循先收敛、后扩展的规则。

- **2026-08-26，Governance V2 Shadows 本地验收：**硬切保留现有路由，把绘制平面所有权放入
  Modifier Architecture，将后端、缓存与诊断契约集中到 Android 阴影模块手册，并减少 Guide
  中的重复细节。第一次双语构建完成英文产物后，正确拒绝了两个相对路径多深入一层的中文链接。
  修正链接后，最终完整构建审计 454 个页面，生成 49,136,607 个非 API 字节；纳入本段验收证据
  后，在不变上限下留下 41,607 字节，Docusaurus 用时 `42.9 s`。结论为 **improved**：债务和
  生成大小同时下降，且
  没有新增路由或弱化门禁。这项本地观察不替代真机阴影保真或性能证据；Overlays 切片必须继续
  保持相同的结构化预算纪律。

- **2026-08-26，Governance V2 Overlays 本地验收：**本次硬切保留所有现有路由，将应用任务与
  ADR/模块 Runtime 契约分开，登记 21 个公开入口与八个可编译 Region，并把 Governance V2 债务
  从 625 降至 590。完整构建仍审计 454 个页面，产生 49,142,652 字节非 API 输出，在不变上限下
  留出 35,562 字节，并在 `33.4 s` 内完成 Docusaurus。结果为 **improved**，且没有改变网站技术栈、
  路由数、框架行为或预算。本次观察复用既有 Overlay 行为证据；Image Loading 切片必须继续遵守
  相同 Stop Condition。

- **2026-08-26，Governance V2 Image Loading 本地验收：**本次硬切保留 Guide、Migration、Coil
  与 Glide 路由，登记四个公开入口和九个 Sample Decision，并把 Governance V2 债务从 590 降到
  571。完整构建审计 454 个页面，产生 49,151,753 字节非 API 输出，在不变上限下剩余 26,461
  字节，并在 `34.6 s` 内完成 Docusaurus。结果为 **improved**，且没有改变 Website Stack、路由
  数量、Framework 行为或 Budget。本次观察验证文档所有权与构建输出，不代表设备、网络或图片
  解码器性能证据。

- **2026-08-26，Governance V2 Modifier Architecture 本地验收：**本次硬切保留 Architecture
  与 Tutorial 路由，登记 51 个 Modifier/Gesture 入口和九个可编译 Sample Decision，用生成式
  Reference 所有权替换剩余手写 API 清单，并把 Governance V2 债务从 571 降到 516。完整构建
  审计 454 个页面，产生 49,095,993 字节非 API 输出，在不变上限下剩余 82,221 字节，并在
  `28.0 s` 内完成 Docusaurus。结果为 **improved**：结构化覆盖提高，同时重复说明和生成体积
  下降，且没有改变 Website Stack、路由数量、Framework 行为或 Budget。本次观察只验证文档
  结构与输出，并复用既有 Modifier Contract 和 Renderer 证据。

- **2026-08-26，Governance V2 ConstraintLayout 模块本地验收：**本次硬切保留模块路由，
  为全部 43 个公开 Core/Helper DSL 入口及五个可编译模块 Sample Decision 登记所有权，删除重复
  的阶段流水账，并把 Governance V2 债务从 516 降到 468。第一次完整构建正确拒绝了两个不会被
  部署的相对 Archive 链接；改用仓库既有的不可变历史链接形式后，最终含验收证据的完整构建审计
  454 个页面，产生 48,982,759 字节非 API 输出，在不变上限下留下 195,455 字节，并在 `25.2 s`
  内完成 Docusaurus。结果为 **improved**：结构化所有权增加，生成输出减少，且没有改变 Website Stack、
  路由、Framework 行为或 Budget。本次观察复用既有 ConstraintLayout 正确性、设备、视觉和性能
  证据，不提出新的 Runtime 结论。

- **2026-08-26，Governance V2 预览工具链本地验收：**本次硬切保留工具页和五个模块路由，
  登记全部 62 个公开预览入口和九个可编译 Sample Decision，删除重复的 Protocol 与设备检查器
  说明，并把 Governance V2 债务从 468 降到 390。最终含验收证据的完整构建审计 454 个页面，
  产生 48,647,612 字节非 API 输出，在不变上限下留下 530,602 字节，并在 `27.8 s` 内完成
  Docusaurus。结果为 **improved**：结构化所有权和可编译 Workflow 覆盖增加，生成输出减少，
  且没有改变 Website Stack、路由、Framework 行为或 Budget。本次观察复用既有 Protocol、
  Runner、设备诊断与 Paparazzi 证据，不提出新的 Runtime、视觉或性能结论。

- **2026-08-26，Governance V2 第三方集成本地验收：**本次硬切保留 CameraX、Google Maps、
  Media3 与旧版 ExoPlayer 模块路由，登记全部 32 个公开入口和九个可编译 Sample Decision，并把
  Governance V2 债务从 390 降到 345。最终含验收证据的完整构建审计 454 个页面，产生
  48,715,878 字节非 API 输出，在不变上限下留下 462,336 字节，并在 `27.4 s` 内完成
  Docusaurus。相比预览工具链批次增加 68,266 字节（`0.1403%`），原因是现在会生成四份结构化
  模块契约和完整可编译样例。结果为 **improved**：精确所有权与双语可执行覆盖增加，构建仍远低于
  不变 Budget，且没有改变 Website Stack、路由、Framework 行为或已发布产物。本次观察复用既有
  模块与设备证据，不提出新的 Runtime、网络、功耗或性能结论。

- **2026-08-27，Governance V2 Animation 本地验收：**本次硬切保留两个模块、Tutorial、
  Migration 与 ADR 路由，登记七个此前无所有者的组合、内容、可见性和布局运动入口，以及 19 个
  可编译或显式不可执行的 Sample Decision，并把 Governance V2 债务从 345 降到 313。最终含
  证据构建审计 454 个页面，产生 48,745,500 字节非 API 输出，在不变上限下留下 432,714 字节，
  并在 `25.3 s` 内完成 Docusaurus。相比第三方集成批次，生成的非 API 输出增加 29,622 字节
  （`0.0608%`），本地构建时间减少 `7.6%`；对于单个纯内容样本，站点结果为 **no material
  change**，精确所有权与双语样例覆盖则为 **improved**。现有 Website Stack、路由数量、
  Framework 行为和已发布产物均未改变。本次观察只验证文档结构与输出，复用既有 Animation
  行为证据，不提出新的运动保真或性能结论。

- **2026-08-25，Governance V2 Phase 0A：**首版双语契约候选超过不变的 46.9 MiB 非 API
  上限 42,041 字节。收敛重复规范并把生成质量报告移出部署树后降至 49,175,712 字节，余量
  2,502 字节；构建和构建后复查均通过。结论为**混合**：首版表示回退，收敛与可重复校验在不
  删除契约、不提额的情况下纠正了问题。本地构建不覆盖部署/CDN/网络行为和独立预算的 API
  产物；Phase 0B 必须复用编译模型并守住上限。

- **2026-08-28，协调发布文档收尾：**六个首发制品从 `current` 转入不可变
  `0.1.0-alpha01` 历史后，完整校验器复用 `5/6` 个源码修订组，重建扩展后的 `2d37ff2e`
  组，并且没有拒绝任何缓存组。生产构建提供 133 个 API 版本、133 份模块手册和 133 条中文
  回退路由，审计 522 个站点页面，总输出为 468.9 MiB、非 API 输出为 46.7 MiB；JavaScript
  保持在 6.6/8.0 MiB，最大分块为 650/768 KiB，CSS 为 112/128 KiB，API 树平均为
  3.2/4.5 MiB，站点 Wrapper 用时 `38.4 s`。相较发布前 127 个版本、510 个页面、
  468.5 MiB/46.3 MiB、`42.5 s` 的对照，不可变覆盖增加六个版本（`+4.7%`）和 12 个页面
  （`+2.4%`），按取整摘要计算的总输出与非 API 输出各增加约 0.4 MiB，Wrapper 用时减少
  `4.1 s`（`-9.6%`）。结论为**混合**：发布路由覆盖和耗时改善，但体积余量缩小，且所有门禁
  仍未超限。本次只是一次使用取整体积摘要的本地热构建对比，不能代表托管缓存、部署、CDN 或
  延迟表现。下一步是在合并后验证托管构建与路由；后续内容增长必须先恢复非 API 余量，而不是
  提高上限。

- **2026-08-29，仓库源码链接外部化：**ViewModel Scoped Owner 候选暴露出一个问题：合法的
  双语契约与编译证据仍会把非 API 输出从主线对照的 49,177,136 字节增加到 49,309,510 字节，
  超过不变的 46.9 MiB 上限 131,296 字节。新的 Before-default Remark Transform 让源 Markdown
  继续使用仓库相对链接接受校验，但在产物中输出 GitHub 链接，不再把关联生产与测试文件复制到
  `assets/files`。在同一主线语料上，首个修正候选产生 47,961,611 个非 API 字节，比对照减少
  1,215,525 字节（`2.47%`），留下 1,216,603 字节余量。全部 77 项文档脚本测试、TypeScript、
  结构与翻译门禁、133 个 API 版本、133 份模块手册、524 页无障碍审计、站点外壳与不变体积预算
  均通过；完整站点 Wrapper 用时 `51.2 s`。结论为 **improved**：源码证据仍可访问，同时移除一份
  冗余部署表示，且不改变路由、API 或预算。本次单次本地热构建不能代表托管延迟；下一步是验证
  CI 精确缓存命中与托管路由。

- **2026-08-30，AI 工具链 PR 预算恢复：**首次托管 #253 站点构建产生 49,245,936 字节
  非 API 输出，超过不变的 46.9 MiB 上限 67,722 字节。对唯一的 1200×630 社交卡 PNG 做无损
  重编码后，解码 RGB 像素逐字节一致，文件从 761,036 降至 608,989 字节（`-152,047`、
  `-20.0%`）。相同语料的本地重建随后审计 528 个页面，产生 49,042,390 字节非 API 输出，
  留出 135,824 字节余量，并在 `29.3 s` 内完成 Docusaurus 部分。结论为 **improved**：路由、
  内容、图片像素、API 历史和预算都没有改变，仅缩小了一份部署表示。托管与本地 Node/平台输出
  存在差异，因此不能把构建总差值 203,546 字节全部归因于图片；下一步是托管复跑，并且必须在
  同一上限下独立通过。

Git 历史保存早期 Paging 和站点检查点；它们不授权删除当前契约、无证据提额或重新扩张副本。

*/}
