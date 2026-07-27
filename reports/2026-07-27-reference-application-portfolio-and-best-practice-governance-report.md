# Reference Application Portfolio 与最佳实践治理报告

类型：Report / Governance

状态：当前

Owner：SOMA reference application portfolio governance

受众：SOMA Java 维护者与 reference application 开发者

适用版本：implementation/evidence candidate `253e383` 与 Stage 6 正式文档切换

输入事实源：专题 immutable commits、三个应用源码/文档/Gate、十一份性能 baseline、Implementation Map、Conformance 与最终综合验证

事实范围：本次治理的裁决、实施、evidence、scope non-regression 与正式收口

非事实范围：SOMA 新产品语义、跨环境性能声明、数据库同步和 release readiness

最后审查日期：2026-07-27

## 1. 意图与结果

本专题把 `soma-examples` 从“两项各自成熟、但 portfolio 责任尚未闭合的参考应用”
治理为三个相互独立的严肃 Java 8 consumer。应用不再按 API 覆盖拼装，而是分别从
自己的 business model 推导状态表示、Access/Transformation、控制流、失败边界和
evidence：

| Application | 当前产品叙事 | 独有 evidence 责任 |
|---|---|---|
| industrial dynamic scheduler | direct Access/Candidate Scan + application frontier/event loop | 完整调度约束、Result validator、100k scale 与持续 mutation |
| grassing individual simulation | packed/exact/batch + deterministic tick systems | AoS逐tick等价、物理顺序独立、lifecycle/fail-stop 与 churn |
| real-time dispatch rule engine | reusable multi-source DataFlow + detached command + Java commit | Join/GroupBy、parallel、budget/cancel、executor ownership 与 plain-Java reference |

三个应用只共享 SOMA public artifacts 和项目级质量标准；没有共享领域 JAR、
Schema、Config、fixture、helper 或 baseline，也不互相 import。

## 2. 实施闭包

Stage 0 在 `b893653` 冻结专题协议和起始事实；Stage 1 在 `da1950e` 冻结三应用
详细设计、不变量 Owner、evidence matrix 和迁移顺序。

- `321a5f2` 完成 grassing 专项复审：Session 对 ordinary/unexpected failure
  fail-stop，完整投影复核移到 test，Result 使用 operation-local accumulator，
  generated companion dependency 得到准确解释；
- `2d3017e` 新增独立 RTD application：Config、detached Scenario、
  Runtime、Rule、Dispatch、Result 分责，输入生成与 live state 分离；
- `6324004` 在 RTD coverage 成立后删除工业调度
  `AssignmentSummaryFlow`，改由 direct ColumnView 单遍
  `AssignmentSummarizer` 推导同一 Result；
- `5d4dc45`、`5818f79`、`78c6361` 建立 RTD measurement、detached-result
  admission 和 problem/benchmark profile 分离；
- `253e383` 固化三应用 correctness/isolation/performance evidence portfolio。

RTD 从 detached snapshot/delta 开始，到 detached command/result 结束，不包含
MES、JDBC/CDC、retry、checkpoint、跨 Table transaction 或 distributed
execution。DataFlow 负责批量推导；应用使用 stable key 完成全批次预检和两个
root 的顺序提交，首个 authoritative write 后的 unexpected failure 采用
fail-stop。

## 3. Evidence 与性能

Portfolio Gate 固定：

```text
Fast  = 三应用 default × 3 forks
Scale = 三应用 large × 3 forks
Soak  = 三应用 long-run × 3 forks
Full  = 九个 workload + baseline architecture
```

baseline architecture 为 `component=2`、`reference-application=9`、
`public-claim=0`。RTD 三份 baseline 由同环境 5-fork 校准，普通 3-fork 均通过：

| Profile | Timing / limit | Caller allocation / limit | Parallel identity |
|---|---:|---:|---:|
| default | `70.98 / 114.38 ms` | `17.22 / 21.52 MB` | `228 tasks / 4 workers` |
| large | `793.82 / 1186.56 ms` | `119.57 / 152.61 MB` | `64 tasks / 4 workers` |
| long-run | `142.83 / 210.29 ms` | `730.15 / 911.26 MB` | `1504 tasks / 4 workers` |

Industrial 删除 DataFlow summary 后，三份旧 baseline 未修改通过；grassing 保留
原 9-fork metric limits，只把早于 generated/runtime v5 的 RuntimePlan identity
迁移到当前 protocol。没有降低 workload、增加普通 fork、放宽阈值或 rebaseline
掩盖回归。全部 artifact 保持 `claimAllowed=false`。

## 4. Scope non-regression

最终核对结论：

- SOMA Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8 定位不变；
- public/generated API、annotation Schema、generated/runtime protocol、
  Access Model、Transformation Model、DataFlow Model 和核心 runtime 语义未修改；
- packed storage、exact access、Index/IndexSnapshot、swap-remove、ownership、
  lifecycle、单 operation 失败原子性和 aggregate fault 边界未弱化；
- industrial/grassing 的 business behavior、canonical journey、Result contract、
  Schema、workload 和性能阈值未缩水；
- RTD 是新增独立 consumer，不复制工业调度领域模型，不把数据库或事务引入 SOMA；
- 三应用 correctness、architecture、isolation、determinism、scale、soak、
  parallel 与 performance evidence Owner 唯一且无覆盖缺口；
- 未引入第三方依赖、跨应用共享领域层、Java Stream hot path、reflection
  interpreter 或 live DTO object graph；
- G0–G5 保持 passed，G6 仍因外部发布事实不足 blocked。

本次变化是 reference application 责任的 additive completion 与内部优化，不是
对既有产品目标打折，也不扩大 release claim。

## 5. 正式固化与退役

长期事实已原子提升到产品 Blueprint、系统架构、Implementation Map、
Conformance、Engineering、三个 application 自有文档、current performance
summary 和本报告。`docs/README.md`、`reports/README.md` 与 checker 已切换为
三应用和九份 application baseline；generated-footprint evidence 修正为四个
surface。旧治理 Report 保留其时点 provenance，不被回写成当前事实。

本专题 Temporary 在正式 Owner、引用闭包和验证完成后删除，不归档。切换期间
工作区出现了一个独立的 Runtime Boundary/Group/Scale Readiness active
Temporary；本专题不拥有、不删除也不提交它。Stage 6 immutable candidate 只包含
本专题事实，因此其中的 `docs/README.md` 恢复“当前没有 active Temporary
topic”；该独立草案及其工作区入口由自身专题另行管理。最终候选在 Azul Zulu
OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、macOS
`26.5.2` / Darwin `25.5.0`、`aarch64` 环境中仅运行一次完整
`./scripts/check.sh`，结果为 `project-check: ok`；文档、reactor、external
consumer、三个 application 的 isolation/correctness、九份 application
baseline、两份 component baseline、generated footprint 和 DataFlow performance
均通过。该结果不处理 G6，也不授权 push、tag、publish 或 release readiness
声明。
