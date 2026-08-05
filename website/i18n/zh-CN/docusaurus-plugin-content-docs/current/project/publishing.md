---
translation_source: project/publishing.md
translation_source_hash: 7db010cd0c1dc5cb64dabc5f412278b1bed5801ccdcde0f155b34f8be4c37ab9
translation_status: current
---

# ViewCompose 发布流程

本文定义 ViewCompose Maven 制品与 Android Studio 插件的本地发布契约。远程 Maven Central 和
JetBrains Marketplace 上传与本地准备刻意分离，使任何不可逆发布前都能检查产物。

## Maven identity 与版本模型

公共 Maven namespace 为：

```text
com.viewcompose
```

每个发布模块在 `gradle/viewcompose-publishing.properties` 独立拥有版本和不可变 API source
revision。相同版本值不组成原子发布列车：改变一项只发布该制品，以及 dependency metadata 必须
指向新版本的制品。

对应 `module.<artifact>.sourceRevision` 是完整 40 位 commit SHA。先在一个提交冻结模块源码，
再在第二个仅元数据提交中同时更新版本和 source revision。该两步规则避免 self-reference hash，
并确保 Dokka 行链接解析到与发布模块完全一致的不可变源码。

Central Portal 验证 `viewcompose.com` 与 `com.viewcompose` 所有权。Maven Central 发布不可变，
首次上传前必须审查 namespace 与 coordinate，之后不能视为临时值。

## Maven 发布标签

每次发布到 Maven Central 都必须有不可变 Git tag。由于模块独立演进，ViewCompose 不使用仓库级
`v<version>` tag 表示 Maven 发布。每个已发布制品使用以下格式单独打 tag：

```text
maven/<artifact-id>/<version>
```

例如：

```text
maven/viewcompose-runtime/0.1.0-alpha02
maven/viewcompose-navigation-core/0.2.0
maven/viewcompose-navigation/0.2.0
```

同一个 Central deployment 的多个 tag 可以指向同一个仅元数据 release commit。tag 必须指向该
release commit，而不是冻结源码的 commit，因为前者才是包含已发布 version 和 `sourceRevision`
的精确仓库状态。签名注释必须记录 artifact、version 与冻结 source revision，使两个提交都可审计。

注释必须恰好包含一个 `sourceRevision=<完整小写 40 位 SHA>` token。token 可以像下方示例一样位于
说明句中，也可以单独占一行；规划器接受这两种布局，但会拒绝缺失、格式错误、大写或重复 token。
该语法兼容已经发布的行内注释，同时不会放宽未来 tag 的来源校验。

仅在 Central Portal 把 deployment 标记为 `Published` 后创建并推送 signed annotated tag；完成后
才能开始下一次发布或修改发布元数据：

```bash
git tag -s "maven/viewcompose-runtime/0.1.0-alpha02" \
  <release-metadata-commit> \
  -m "Maven Central: viewcompose-runtime 0.1.0-alpha02; sourceRevision=<frozen-source-commit>"
git push origin "refs/tags/maven/viewcompose-runtime/0.1.0-alpha02"
git ls-remote --exit-code origin \
  "refs/tags/maven/viewcompose-runtime/0.1.0-alpha02"
```

每个制品 tag 都存在于远端并解析到预期 release commit 后，发布流程才算完成。禁止移动、删除或
复用已发布 tag；禁止给 dirty worktree、后续文档提交，或入库元数据与已发布制品不一致的提交打
release tag。Central 发布失败时不得创建最终 release tag。

### 首次 Maven Central 发布记录

首次 Maven Central 发布从提交
`dc07ff6189eeab89644e3f9f792e1d7316240812`（`build: prepare Maven Central publishing`）发布了
所有登记制品的 `0.1.0-alpha01`。当时没有创建 Maven 专用 tag。2026-08-04，根据已合并发布分支
和本地发布时间线重建出 release checkout 后，为每个登记制品补建了带签名的 annotated tag，格式为
`maven/<artifact-id>/0.1.0-alpha01`。每个 tag 都指向该提交，并在注释中记录
`sourceRevision=dc07ff6189eeab89644e3f9f792e1d7316240812` 和 `provenance=retrospective`。

本次修正同时删除了与 Maven 发布无关的 `navigation-demo-20260727`、
`navigation-demo-20260727-r2`、`navigation-demo-20260727-r3` 和 `v0.1.0` 仓库 tag；它们都不代表
Maven Central 发布。仅当独立制品来源证据能定位到唯一 release commit 时，才允许补建历史 release
tag，且注释必须声明该记录由历史重建。禁止把补建 tag 默认为原始发布时创建的 tag。

### 已登记的首次发布

制品必须在首次 Central 发布前登记，但最终 release tag 只能在 Central 显示 `Published` 后创建。
该临时状态记录在 `gradle/viewcompose-publishing.properties` 的 `release.unpublishedModules` 中；只有
这个显式集合内的制品可以暂时没有 Maven release tag。对于这些制品，规划器会从仓库起点扫描
Changeset，要求直接发布声明，并推荐已经登记的初始版本与 source revision，不会提前升级版本或重复
追加文档历史。

首个 signed tag 推送后，应在下一次仓库改动中把制品移出 `release.unpublishedModules`。以下状态都会
使规划失败：未标记制品缺少 tag、已标记制品已经存在 tag，或入库版本元数据领先于最新 tag。这样可
区分真正的首次发布、未拉取 tag 和过期发布状态。

## 每个 PR 的发布意图

ViewCompose 为每个 PR 保存一份不可变 Changeset，而不维护一份共享可变的“已改模块”清单。发布
制品的生产改动必须新增 `release/changes/<unique>.json` 才算完整。机器可读 schema 位于
`release/changes.schema.json`。

```json
{
  "schemaVersion": 1,
  "summary": "Correct saved-state restoration after process recreation.",
  "changes": [
    { "artifact": "viewcompose-runtime", "impact": "fix" }
  ],
  "ignored": [
    {
      "artifact": "viewcompose-widget-core",
      "reason": "Only a test fixture changed; no published source or metadata changed."
    }
  ]
}
```

直接影响只能是 `breaking`、`feature` 或 `fix`。贡献者不得填写 `dependency`；当独立发布的下游
需要指向依赖的新版本时，由规划器自动推导。`ignored` 是对自动识别制品的审阅例外，必须说明具体
理由。对于 `build.gradle.kts` 等语义不明确的根构建输入，可以用 `shared` 记录其不影响发布的理由；
若确有影响，则 Changeset 必须声明受影响制品。

自动归属覆盖每个登记制品的 `src/main`、`src/commonMain`、`src/androidMain`、`src/jvmMain`、
`src/release`、影响发布的模块构建文件，以及会进入 API 文档的 `src/test/samples`。普通单元/
instrumentation 测试、Demo、benchmark 和手写文档默认不触发 Maven 发布。根构建文件与 version
catalog 无法只根据路径安全推导影响，因此必须显式声明意图。

Changeset 只允许追加。合并后禁止修改、重命名、删除或复用。即使 PR 使用 squash、rebase 或
fixup，发布单元仍是合并后的 PR，而不是中间 commit。允许保守地多声明一个自动归属未识别的制品，
但不得漏掉已检测制品。

```bash
./gradlew verifyViewComposeReleaseIntent
```

该命令默认相对 `origin/main` 的 merge base 校验。CI 通过
`VIEWCOMPOSE_RELEASE_BASE_REVISION` 传入 PR base SHA，本地特殊比较可使用
`-PviewComposeReleaseBaseRevision=<commit>`。任务已进入 `qaQuick`，也会拒绝修改已有 Changeset。

## 确定性的独立发布规划

规划必须在已拉取完整 tag、工作区干净，且 GPG keyring 信任 ViewCompose 发布公钥的 checkout 上运行：

```bash
git fetch origin main --tags
./gradlew planViewComposeRelease
```

对于每个已发布制品，规划器选择符合 `maven/<artifact-id>/<version>` 的最高语义版本 tag，验证
签名并读取唯一且严格的 `sourceRevision` token。这个不可变 revision，而不是可变的当前发布元数据，
才是该制品的比较边界。显式首次发布使用已登记的 source revision 和上文的仓库历史规则。规划器随后：

1. 读取比较边界到 `HEAD` 之间新增的 Changeset；
2. 确认每条影响发布的直接路径都有声明；
3. 取制品直接影响的最高等级；
4. 从 Gradle `api`、`implementation`、`compileOnly`、`runtimeOnly` project dependency 推导当前依赖图；
5. 向所有已发布反向依赖传递 `dependency` 发布；
6. 生成确定性的 `build/release-plan.json` 和 `build/release-plan.md`，区分直接变化与依赖传播。

规划只推荐版本，不静默决定版本。稳定版本中，`fix` 和 `dependency` 增加 patch，`feature` 增加
minor，`breaking` 在 `1.0` 后增加 major、在 `0.x` 增加 minor。预发布版本无论影响等级都增加
当前数字 channel，例如从 `0.1.0-alpha01` 到 `0.1.0-alpha02`。release owner 必须审阅并确认每个
精确版本。

源码提交审阅并冻结后，只应用已确认计划：

```bash
./gradlew prepareViewComposeRelease \
  -PviewComposeReleaseVersions=viewcompose-runtime=0.1.0-alpha02,viewcompose-ui-contract=0.1.0-alpha02
```

确认的制品集合必须与计划完全一致，且所有版本必须前进。任务只更新选定模块在
`gradle/viewcompose-publishing.properties` 中的版本，把 `sourceRevision` 固定为干净的规划提交，
并向 `gradle/viewcompose-documentation-releases.properties` 追加不可变记录。审阅该 diff 后，以仅
元数据 release commit 提交。发布选择必须与 `build/release-plan.json` 一致；Central 显示
`Published` 后，再按前文规则创建每制品签名 tag。

2026-08-04 的 backfill Changeset 一次性记录了首个 Central 边界之后、此流程建立之前影响发布的
改动。它只是迁移记录，不能作为以后在合并后补写发布意图的先例。

## 依赖形态

feature 制品传递暴露其平台无关 core：

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha01")
}
```

core 制品也可由 Kotlin/JVM 模块独立使用：

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha01")
}
```

Gradle Module Metadata 保留 `api`/`implementation` variant；同时为其他构建工具生成 Maven POM。
每个制品发布 sources JAR 供 IDE 导航，并发布 javadoc JAR 满足仓库要求。

## 本地 Maven 工作流

只验证元数据而不编译：

```bash
./gradlew verifyViewComposePublishingConfiguration
```

发布所有登记模块到 `build/maven-repository`：

```bash
./gradlew cleanViewComposeLocalRepository publishViewComposeToLocalRepository
```

只发布当前独立演进的制品：

```bash
./gradlew publishSelectedViewComposeToLocalRepository \
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation
```

选择性发布不会删除 repository，因此可以解析已 staged 的独立版本。全模块任务用于 snapshot QA；
公开 stable release 必须显式选择模块，避免重新上传未变化的不可变版本。

```bash
./gradlew verifySelectedViewComposeLocalRepository \
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation

./gradlew inspectViewComposeLocalRepository

./gradlew verifyViewComposeLocalRepository

./gradlew verifyViewComposePublishedConsumption
```

这些命令分别发布并验证独立集合、检查已有 repository、验证主制品/sources/docs/POM/checksum/
stable signature/feature-to-core dependency，以及构建一个 Android feature consumer 和一个纯 JVM
core consumer。完整发布任务不属于 `qaQuick`；日常 QA 只运行廉价 coordinate/version 验证。

## 版本 override 与签名

入库版本是真相源。CI dry run 可在不编辑文件时 override 一个模块：

```bash
./gradlew publishViewComposeToLocalRepository \
  -PviewComposeVersion.viewcompose-navigation=0.2.0-SNAPSHOT
```

可用 `-PviewComposeGroup=...` 验证 namespace。特殊文档 dry run 可用
`-PviewComposeSourceRevision.<artifact>=<full-commit-sha>` override source；公开发布元数据必须入库，
不得依赖 override。

本地 stable release 使用机器 GPG keyring 与 OS pinentry；公钥必须发布到 Central 支持的 keyserver，
私钥路径和密码不存项目。CI 使用内存 PGP：

```text
VIEWCOMPOSE_SIGNING_KEY
VIEWCOMPOSE_SIGNING_PASSWORD
```

stable 必须签名，`-SNAPSHOT` 本地 QA 可不签名，secret 不得入库。

Central Portal uploader 从私有 Gradle properties 读取 token，只放用户级
`~/.gradle/gradle.properties` 或 CI secret：

```text
mavenCentralUsername=<generated token username>
mavenCentralPassword=<generated token password>
```

CI 使用 `ORG_GRADLE_PROJECT_mavenCentralUsername` 与
`ORG_GRADLE_PROJECT_mavenCentralPassword`，绝不提交仓库。

选定模块改为 stable 版本并完成本地检查后，创建人工管理的 Central deployment：

```bash
./gradlew publishSelectedViewComposeToMavenCentral \
  -PviewComposePublishModules=viewcompose-runtime,viewcompose-navigation-core
```

该任务没有全模块默认值，拒绝 `-SNAPSHOT`，上传为 user-managed deployment。点击 Portal 的
Publish 前检查 Central validation；公开发布与 Gradle upload 刻意分离。

## Android Studio 插件

Marketplace 插件版本由同一 properties 中 `plugin.viewcompose-studio.version` 独立管理，可用
`-PviewComposeStudioPluginVersion=...` override。

准备并验证不上传的安装 ZIP：

```bash
cd tools/viewcompose-studio-plugin
./gradlew prepareMarketplaceRelease
```

首个版本只支持 Android Studio build family `261`，并声明
`com.intellij.modules.androidstudio`，避免 Marketplace 向 IntelliJ IDEA 展示。lower bound 与
`untilBuild = 261.*` 都明确，防止把未测试未来平台误标兼容。`prepareMarketplaceRelease` 验证
本地 Quail 2 Patch 1、当前 Quail 3 和该窗口内最新 Quail 4 Canary；首次下载后复用 Gradle IDE
cache。

产物写入 `build/distributions/`。Marketplace 发布与签名只读取：

```text
JETBRAINS_MARKETPLACE_TOKEN
JETBRAINS_CERTIFICATE_CHAIN
JETBRAINS_PRIVATE_KEY
JETBRAINS_PRIVATE_KEY_PASSWORD
```

也支持 JetBrains 标准别名 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`、
`PUBLISH_TOKEN`。本地可把 `chain.crt` 与 `private.pem` 放在私有默认目录
`~/.config/viewcompose/marketplace-signing/`。自定义位置只在用户级 Gradle properties 保存绝对路径：

```text
viewComposeMarketplaceCertificateChainFile=/absolute/private/path/chain.crt
viewComposeMarketplacePrivateKeyFile=/absolute/private/path/private.pem
viewComposeMarketplacePrivateKeyPassword=<private key password, only when encrypted>
```

人工上传前构建、签名并验证作者签名：

```bash
cd tools/viewcompose-studio-plugin
./gradlew prepareSignedMarketplaceRelease
```

首个版本必须在 Marketplace 人工上传接受初审；批准后后续版本可运行 `./gradlew publishPlugin`。
`-PviewComposeMarketplaceChannels=default,eap` 选择 channel，默认 `default`。

## 首次公开发布清单

1. 确认 Central 的 `com.viewcompose` namespace 仍已验证。
2. 在审阅提交冻结选定模块源码。
3. 在仅元数据提交把每个版本和 `sourceRevision` 更新为冻结提交。
4. 运行 `qaQuick`、`verifyCompleteViewComposeApiDocs`、
   `verifyViewComposePublishedConsumption` 和相关 release test。
5. 强制 PGP 签名并检查每个 POM、sources JAR、javadoc JAR 和 checksum。
6. 上传 Central staging deployment，并从 staging repository 验证消费。
7. Central 显示 `Published` 后，为每个已发布制品创建、推送并远端验证一个 signed
   `maven/<artifact-id>/<version>` tag。
8. 运行 `prepareMarketplaceRelease`，在目标 Android Studio 安装 ZIP 并做 Preview smoke test。
9. 首个插件版本人工上传；批准后再启用 token 自动化。
