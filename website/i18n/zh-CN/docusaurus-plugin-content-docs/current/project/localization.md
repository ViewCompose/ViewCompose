---
translation_source: project/localization.md
translation_source_hash: 6d8561bf634ecc448cdb278867bd6ecf3547cd476f6cd400e986962a0a637d7e
translation_status: current
---

# 文档本地化工作流

本文是 ViewCompose 中英文文档维护的执行契约。权威语言和强制规则由
[文档治理规范](https://docs.viewcompose.com/project/documentation-governance#language-policy)定义。

## 源文件布局

英文是权威源，保存在 `docs/`。简体中文镜像使用 Docusaurus 标准目录：

```text
docs/<path>.md
website/i18n/zh-CN/docusaurus-plugin-content-docs/current/<path>.md
```

英文站点发布在 `/`，中文站点发布在 `/zh-CN/`。两个语言使用相同相对路径和文档 ID，
确保切换语言时能保持阅读上下文。导航栏、页脚和 React 页面文案放在 Docusaurus 生成的
其他 `website/i18n/zh-CN/` 翻译文件中。

中文镜像只有在目标页面也存在中文镜像时才使用相对 Markdown 链接。尚未翻译的目标要通过
英文权威完整 URL 显式链接，例如 `https://docs.viewcompose.com/architecture/overview`。这样
链接在 GitHub 上也能使用，严格链接检查仍然有效，读者也会明确进入英文源。目标的中文镜像
经过审阅并加入后，再替换为对应的本地化相对链接。

英文权威文档始终保留仓库相对 Markdown 链接。在本地化构建中，Docusaurus 可能同时渲染尚未
翻译的英文回退页面和已翻译目标；站点的 Markdown 解析器只把这种已经验证的跨边界链接改写为
目标公共路由，不会忽略未知坏链或降低严格校验等级。

## 页面 Front Matter

每个中文 Markdown 镜像都必须声明：

```yaml
---
translation_source: project/localization.md
translation_source_hash: <sha256-of-canonical-source>
translation_status: current
---
```

`translation_source` 是相对于 `docs/` 的路径；`translation_source_hash` 记录译者实际审阅
的英文内容；`translation_status` 只能是 `current` 或 `stale`。

不得为了消除构建错误而机械更新指纹。必须先阅读英文变化、同步中文语义、验证链接和示例，
然后才能记录新指纹。

## 必须翻译与持续跟踪的页面

`website/i18n/translation-policy.json` 保存必须提供中文镜像的机器可读页面列表。必须翻译
的页面需要始终存在、保持最新并能成功构建。调整该列表时，权威页面、中文镜像和策略验证
必须一起更新。

其他有效公共页面只要已经存在中文镜像，就进入持续跟踪。初期允许某个普通页面没有中文
翻译，但已有翻译必须保持最新或明确标记为过期，禁止静默漂移。

生成的 API Reference、归档材料和临时执行计划默认只使用英文。中文指南应链接到生成的 API
Reference，而不是复制整套符号文档。

## 将普通翻译标记为过期

普通中文镜像无法随英文同步时：

1. 将 `translation_status` 设置为 `stale`；
2. `translation_source_hash` 保留最后一次实际审阅的英文指纹；
3. 紧跟 Front Matter 添加可见提示：

```md
:::warning 翻译状态
本中文页面落后于权威英文页面，请以英文版本中的最新契约为准。
:::
```

必须翻译的页面不能使用这一豁免。

## 变更流程

每次修改权威公共文档时：

1. 先更新并验证英文源；
2. 判断页面属于必须翻译、持续跟踪还是仅英文；
3. 必须翻译的页面要在同一 PR 中更新和审阅中文镜像；
4. 普通镜像要么同步更新，要么在同一 PR 中明确标记过期；
5. 在 PR 模板中说明本地化影响；
6. 运行翻译校验和双语站点构建。

紧急正确性和安全修复仍然先修改英文。无法在紧急 PR 中同步普通翻译时，应将它标记过期，
不能继续把已知不准确的内容呈现为最新状态。

## 命令

在 `website/` 下执行：

```bash
npm run write-translations
npm run verify:translations
npm run typecheck
npm run build
```

`write-translations` 补充缺失的 Docusaurus JSON 文案，不覆盖已审阅翻译。
`verify:translations` 校验源映射、必需覆盖率、指纹、状态和过期提示。
`build` 同时生成 `en` 与 `zh-CN` 站点，并继续严格检查损坏链接。

仓库级文档位置和链接仍由以下命令检查：

```bash
./gradlew verifyDocumentationStructure
```

## 审查清单

- 技术行为和术语与权威页面一致。
- 代码、命令、坐标、标识符和 URL 没有被错误翻译。
- 相对链接能在本地化路由中解析。
- 截图已经本地化，或明确不依赖语言。
- 源指纹对应实际审阅过的英文内容。
- 过期翻译包含可见警告，并且不是必须翻译的页面。
- 两种语言都能成功构建。

## AI 辅助翻译

AI 可以起草或更新翻译，但必须遵循相同的页面级工作流。修改镜像前先阅读当前英文源、已有
翻译和本文。保留技术标识符，并根据代码或测试验证示例。不得因为重新生成了指纹，就宣称
翻译已经是最新状态。
