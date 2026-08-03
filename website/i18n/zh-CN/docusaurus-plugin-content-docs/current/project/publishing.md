---
translation_source: project/publishing.md
translation_source_hash: 48b6db629d2c760bf31c73ebed265e317ccadf4a0f2c64c39906e24e5569425a
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
7. 运行 `prepareMarketplaceRelease`，在目标 Android Studio 安装 ZIP 并做 Preview smoke test。
8. 首个插件版本人工上传；批准后再启用 token 自动化。
