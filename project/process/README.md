# Process 导航

类型：Process 入口

状态：正式

Owner：SOMA Java 过程治理

事实范围：可靠推进项目所需的文档、构建、测试、benchmark 和 release 过程

非事实范围：产品语义、当前能力和某次验证结果

最后审查日期：2026-07-30

## 过程体系目标

SOMA V1 的过程体系负责把产品意图变成可持续交付的闭环：

```text
Blueprint / Design / Capability
        -> compiler / runtime / DataFlow / examples
        -> test / consumer / benchmark / qualification evidence
        -> compatibility / security / package / release decision
        -> Conformance 与下一次受控演进
```

Maven lifecycle是构建与artifact graph的权威；脚本只承担跨Maven边界的
admission、编排和evidence适配；CI按变更风险选择成比例反馈；Report与
Conformance只陈述已形成的当前证据。过程体系应同时满足：

- 产品事实从Blueprint到release claim可追踪，任何缺口有唯一Owner；
- Capability可以内部替换，但public/generated/schema/runtime语义不漂移；
- Fast、Full和Qualification证据分层，失败关闭且成本与风险相称；
- source、artifact、support、安全、版本和撤回路径达到可发布产品要求；
- 测试、benchmark、脚本、文档和Example围绕核心抽象与叙事增长，replacement
  同时退役predecessor，不累计治理噪声。

下列文档分别拥有闭环中的过程细节，不在本入口重复定义。

- [文档治理](documentation-governance.md)
- [开发与维护指南](development-guide.md)
- [构建与验证](build-and-validation.md)
- [GitHub 私有仓库与 Codex Cloud 开发](github-and-cloud-development.md)
- [测试与 evidence](testing-and-evidence.md)
- [Validation Gate 治理](validation-gates.md)
- [Benchmark 治理](benchmark-governance.md)
- [Release 治理](release-governance.md)

Process 可以规定过程和证据门槛，但不得静默改变 Blueprint 或 Design。
