---
translation_source: architecture/decisions/README.md
translation_source_hash: bbaa1e74fa22cea15aa6d7d739608ffe4eb04eefa66c96db596ba68faa8bf6c5
translation_status: current
---

# 架构决策记录

架构决策记录保存难以逆转、影响多个模块或建立公共契约的决策。它们解释为什么选择某种设计；
当前架构页面则说明系统现在如何工作。

## 已接受的决策

- [ADR-0001：托管文档平台](./0001-hosted-documentation-platform.md)
- [ADR-0002：五层运行时模块架构](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003：公开包所有权与平台 Handle](./0003-public-package-ownership-and-platform-handles.md)
- [ADR-0004：设计系统解析边界](./0004-design-system-resolution-boundary.md)

## 规则

1. 使用下一个四位编号和小写 kebab-case 标题。
2. 不得重写已接受记录来隐藏历史取舍。
3. 决策改变时新增 ADR，并明确声明替代此前记录。
4. 决策改变实现时，同步更新当前架构和模块文档。
