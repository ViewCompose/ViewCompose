---
translation_source: project/localization.md
translation_source_hash: ac87e9242a6289506f136628ca672331cc0eda1cee27a63055651c3ff4a07d73
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

权威页面的标题、各级标题和叙述正文使用英文；中文镜像中的对应内容使用简体中文。代码块、
行内标识符、命令、URL 和真实 UI 文案保留准确源语言。叙述中出现外语 UI 字面量时用行内代码
标记，不得用未标记的外语句子充当示例。历史归档和临时执行计划不进入 locale 镜像树。

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

## 必须翻译与仅英文页面

`website/i18n/translation-policy.json` 保存所有有效手写公共页面的机器可读列表。列表中的
中文镜像必须存在、使用中文叙述、保持最新并能成功构建。新增、移动或删除公共页面时，必须
同时更新英文源、中文镜像、策略与验证。

生成的 API Reference、不可变历史模块手册快照、归档材料、临时执行计划和不作为用户指南
发布的内部证据只使用英文。中文指南应链接生成的 API Reference，而不是复制整套符号文档。
不得依赖 locale fallback 临时发布缺少已审阅中文镜像的有效手写页面。

## 恢复过期翻译

验证器仍识别显式 `stale` 标记以支持历史恢复，但所有有效公共页面都是必需页面，不能以该状态
合并。修复期间：

1. 将 `translation_status` 设置为 `stale`；
2. `translation_source_hash` 保留最后一次实际审阅的英文指纹；
3. 紧跟 Front Matter 添加可见提示：

```md
:::warning 翻译状态
本中文页面落后于权威英文页面，请以英文版本中的最新契约为准。
:::
```

合并前必须更新中文语义、把 `translation_status` 改回 `current`，并记录实际审阅的权威指纹。
不得把该标记作为必需页面的豁免。

## 变更流程

每次修改权威公共文档时：

1. 先更新并验证英文源；
2. 判断页面属于公共页面还是明确仅英文；
3. 每个公共页面都在同一 PR 中更新并审阅中文镜像；
4. 验证叙述使用目录语言，字面量保持精确；
5. 在 PR 模板中说明本地化影响；
6. 运行语言分类、翻译校验和双语站点构建。

紧急正确性和安全修复仍在变更内部先修改英文。公共页面缺失中文镜像、镜像过期或已知不准确时
不得合并。

## 命令

在 `website/` 下执行：

```bash
npm run write-translations
npm run verify:languages
npm run verify:translations
npm run typecheck
npm run build
```

`write-translations` 补充缺失的 Docusaurus JSON 文案，不覆盖已审阅翻译。
`verify:languages` 检查权威页面中的中文叙述、中文镜像中的英文标题或英文主导正文，以及其他
语言放置错误，同时忽略代码和显式标记的字面量。
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
- 权威页面与中文镜像的标题和叙述符合各自目录语言。
- 两种语言都能成功构建。

## AI 辅助翻译

AI 可以起草或更新翻译，但必须遵循相同的页面级工作流。修改镜像前先阅读当前英文源、已有
翻译和本文。保留技术标识符，并根据代码或测试验证示例。不得因为重新生成了指纹，就宣称
翻译已经是最新状态。
