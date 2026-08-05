---
title: 能力教程
sidebar_position: 2
slug: /tutorials
translation_source: tutorials/README.md
translation_source_hash: 71e358fd93ececa5a78c7f0cbe38ce43b45abd363545865d51cdfb43e7ad07c7
translation_status: current
---

# ViewCompose 能力教程

直接选择你需要的能力。每篇教程都可以独立开始：开头列出全部 Maven 依赖，只使用一个自包含
Activity 文件，而且无需先完成其他章节就能编译运行。

| 我想要…… | 教程 | 额外 ViewCompose 产物 |
| --- | --- | --- |
| 让状态更新界面 | [使用状态与事件](./state-and-events.md) | 无 |
| 排列界面内容 | [使用布局与 Modifier](./layouts-and-modifiers.md) | 无 |
| 接收可编辑文本 | [使用文本输入](./text-input.md) | 无 |
| 显示滚动集合 | [使用 Lazy 列表](./lazy-lists.md) | 无 |
| 跟随明暗模式和语义颜色 | [使用主题](./theming.md) | 无 |
| 在页面之间跳转 | [使用导航](./navigation.md) | `viewcompose-navigation` |
| 显示对话框 | [使用 Overlay](./overlays.md) | `viewcompose-overlay-android` |
| 嵌入原生 View | [使用 AndroidView](./android-view.md) | 无 |
| 让内容以动画显示和隐藏 | [使用 AnimatedVisibility](./animation.md) | `viewcompose-animation` |
| 处理点击和长按 | [使用手势](./gestures.md) | `viewcompose-gesture` |
| 调整大型 Lazy 列表 | [调整 Lazy 列表性能](./lazy-list-performance.md) | 无 |
| 检查 renderer 工作量 | [读取渲染诊断](./render-diagnostics.md) | 无 |

如果还没有创建过 ViewCompose 应用，可以查看[构建第一个应用](./getting-started.md)。它只是可选的
环境搭建帮助，并不是其他能力教程的前置章节。

## 可执行示例契约

[`samples/tutorials`](https://github.com/ViewCompose/ViewCompose/tree/main/samples/tutorials)
模块为每篇教程保留一个独立文件，并且只从 Maven Central 解析 ViewCompose。`verifyTutorialSamples`
会编译该模块、核对中英文代码是否与源码完全一致，并检查每篇教程的依赖声明。
