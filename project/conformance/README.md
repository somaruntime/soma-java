# SOMA Java Conformance

类型：Conformance Entry

状态：Active

正式事实源：是

Owner：SOMA Java Blueprint、Design、实现与验证的一致性状态

最后审查日期：2026-08-01

## 1. 文档责任

本目录记录“当前 checkout 是否符合正式 Blueprint/Design”，以及哪些结论已有证据、
哪些仍未实现或未验证。Conformance 不拥有产品语义，不授权 implementation，也不能
把 feasibility spike 外推为 production/release claim。

权威上游：

- [SOMA Java V1 产品蓝图](../blueprint/README.md)
- [Design 总览](../design/README.md)

## 2. 当前总体状态

```text
Formal Blueprint/Design       active baseline
Production compiler/runtime   absent
Production generated API      absent
Production build/reactor      absent
Correctness/performance Gate  absent
Package/release qualification absent
```

当前 active checkout 只有正式 Blueprint/Design/Conformance 与历史 P2 feasibility
evidence。没有
production source、artifact、可用 consumer API、benchmark、Examples、CI/release
workflow 或 release readiness。

## 3. Conformance matrix

| Surface | Design Owner | 当前实现事实 | 状态 |
|---|---|---|---|
| Schema/annotation/compiler | [Schema 与编译生成](../design/schema-and-generation.md) | 无 production processor；P2 仅证明部分 Java 8 shape/build boundary 可行 | NOT_IMPLEMENTED |
| Group/Table/storage/Key/Index | [数据模型与存储](../design/data-model-and-storage.md) | 无 production storage/runtime | NOT_IMPLEMENTED |
| Generated logical API | [逻辑层 API](../design/logical-api.md) | 无 production generated API；P2 证明 selected signatures/mechanisms | NOT_IMPLEMENTED |
| Pipeline/concurrency/parallel | [执行、并发与并行](../design/execution-and-concurrency.md) | 无 production execution engine/scheduler | NOT_IMPLEMENTED |
| Result/failure | [结果与失败](../design/results-and-failures.md) | 无 production carrier/runtime mapping；P2 固定最小 carrier shape | NOT_IMPLEMENTED |
| Build integration | [Schema 与编译生成](../design/schema-and-generation.md) | full-regeneration contract 已设计；无 production Maven/IDE integration | NOT_IMPLEMENTED |
| Performance | Blueprint BP-8/BP-10 | 无 production implementation，不可 profile | NOT_EVALUABLE |
| Release | Project/release process 尚未准入 | 无 artifact、qualification 或 workflow | NOT_QUALIFIED |

`NOT_IMPLEMENTED` 不是 Design contradiction；它表示当前 clean-slate checkout 尚未进入
实施阶段。任何 README/Guide/Release 不得把 P2 evidence 改写为已实现 capability。

## 4. 已有正式 evidence record

- [P2 Generated API Java 8 可行性](p2-generated-api-feasibility.md)：记录 2026-08-01
  validation spike 的 scope、环境、结果、限制和 promotion 去向。
- [正式 Blueprint/Design 晋升审查](formal-documentation-promotion.md)：记录三份
  Temporary source 到唯一正式 Owner 的逐章 replacement closure。

## 5. Production implementation 前置 Gate

在创建 production module/type/API 前，至少需要：

1. capability 与 consumer、Owner、lifecycle、failure boundary；
2. module/artifact/dependency surface admission；
3. 对应 Design 中列出的 compile/runtime evidence plan；
4. implementation 与测试的双向 traceability；
5. active Conformance 记录，不把 known deviation 隐藏在代码或 README；
6. 明确 Product Owner implementation authorization。

完整证据清单见 [V1 Implementation Conformance Gates](v1-implementation-gates.md)。

## 6. Release claim boundary

正式 Design 只说明系统“应当是什么”。只有 production code、tests、reference
scenario、performance、packaging、provenance 和 release Gate 全部通过，才可以声明
可用性、兼容性、支持矩阵或 release readiness。
