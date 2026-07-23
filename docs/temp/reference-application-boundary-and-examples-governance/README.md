# SOMA 参考应用边界与 `soma-examples` 重构治理

类型：Temporary

状态：active

Owner：SOMA 参考应用边界治理

事实范围：本专题的意图、完整目标、授权边界、非回归约束、阶段、停止条件与完成标准

非事实范围：当前正式 SOMA Design、精确 public/generated API、当前实现结论、G6 或 release readiness

最后审查日期：2026-07-23

起始基线：commit `93bcfffca440f2cbb84d388a2c45410d95a0fbed`

正式事实源：否；本目录只拥有专题期间的候选设计、迁移账本与实施协议

## 1. 意图

当前 FJSP、VRP、Simulation、Game 最初是辅助 SOMA 设计的探索场景，后来逐渐进入正式 Blueprint、Design 追踪、Conformance、G5、benchmark 和 current Report，形成领域场景与 SOMA 产品之间的所有权耦合。

本专题不是把“四个内嵌场景”机械替换成“两个新的内嵌场景”，而是：

1. 解除领域场景对 SOMA 产品目标和长期规范性设计的所有权；
2. 将 `soma-examples` 重构为两个独立参考应用的聚合边界；
3. 让参考应用只消费 SOMA public artifacts，并以真实应用证据反馈产品；
4. 完整保留当前 public/generated API、Schema、Access Model、runtime 语义、性能边界和 G0–G5 evidence；
5. 在替代证据闭合后原子退役旧四场景，不留下平行 Owner、悬空引用或伪外部 consumer。

代码和文档体量下降是边界纠正的结果，不以 LOC、文件数或静态“未使用”扫描作为删除依据。

## 2. 完整目标

### 2.1 产品与文档边界

- SOMA 根级 Blueprint 只拥有 SOMA 的目标用户、目标体验和产品形态；
- SOMA Design 只拥有 Schema、Access Model、storage、ownership、lifecycle、failure、materialization、compatibility 和 performance 等通用设计；
- FJSP、VRP、Simulation、Game 不再作为 SOMA Blueprint、Design、Conformance 或产品能力；
- 两个新参考应用分别拥有自己的应用 Blueprint、Design、领域正确性、测试和 Report；
- 应用文档明确声明不拥有 SOMA 产品规范性事实；
- SOMA 可以记录应用消费了哪些 public capability，但不接管应用领域算法。

### 2.2 目标模块

`soma-examples` 成为聚合模块，只包含：

```text
soma-examples/
├── pom.xml
├── industrial-dynamic-scheduler/
│   ├── pom.xml
│   ├── docs/
│   └── src/
└── grassing-individual-simulation/
    ├── pom.xml
    ├── docs/
    └── src/
```

两个应用必须独立构建、运行和测试，只依赖 SOMA public artifacts，不访问 processor/runtime internal package，不依赖 `soma-testkit`、test-only bypass 或 reactor-only classpath，不建立没有证据支持的 `examples-common`，并保持 Java 8 / Azul Zulu JDK 8。

两个应用还必须把输入生成与运行时状态分开：

- 版本化配置文件拥有问题规模、初始状态参数、seed 和运行参数；
- generator 只根据配置生成 detached problem/initial-state input，不持有也不修改 SOMA runtime；
- bootstrap 先校验输入，再一次性装载 authoritative runtime state；
- solver/simulator hot loop 只消费已装载状态，不反向调用 generator；
- correctness、default、large 和 long-run 使用独立配置，同一配置与 seed 必须产生相同输入摘要；
- CLI 可以覆盖配置项，但必须输出最终生效配置和输入摘要，避免不可重放的隐式状态。

### 2.3 参考应用

- 工业动态调度引擎必须覆盖 flexible machine、precedence、release/material readiness、sequence-dependent setup、calendar/maintenance、transport、secondary resource、due date/priority、application event queue、incremental frontier、动态 dispatch/commit/release、完整 validator 和 realistic long-run evidence；
- 个体生态仿真必须独立实现 grasser–grass 模型，覆盖 growth、metabolism、reproduction、grassing、searching、birth/death/mode transition、显式 system 顺序、deterministic random、headless oracle、packed/group/column/bulk/remove 与 long-run evidence；
- 第二个应用只参考 `https://git.ufz.de/oesa/ecs-tutorial` 的模型目标，不引入 Artemis-odb，不把 SOMA 扩展成 ECS。

### 2.4 Evidence

删除旧四场景前，每一项旧责任必须裁决为：

- 核心 fixture 接管；
- 新参考应用接管；
- 中性 component benchmark 接管；
- 证明为旧场景领域事实后正式退役。

Point、Candidate、Column、Key、Bulk、Ownership、IndexSnapshot、materialization、failure、lifecycle 和性能机械形状必须继续有唯一 evidence Owner。`soma-benchmarks` 最终不得依赖领域 example schema；integrated benchmark 由应用各自拥有。

## 3. 非目标

- 不把 SOMA 改造成 ECS framework、调度器、仿真器或游戏引擎；
- 不增加 `World`、动态 Component、Aspect subscription、System scheduler 或依赖注入；
- 不修改 public/generated API、annotation Schema、Access Model、Index 生命周期、ownership、lifecycle 或失败原子性；
- 不引入 Artemis-odb 或其他第三方依赖；
- 不支持 Java 11 或其他 JDK distribution；
- 不处理 G6、SCM、签名、publishing 或 release readiness；
- 不证明 SOMA 普遍优于 Artemis、普通对象或其他存储；
- 不为了减少文件数合并不同 failure domain 的测试、fixture 或 benchmark；
- 默认不增加第三个正式参考应用。

## 4. 非回归约束

1. 正式 Blueprint、Design、Conformance 和 Engineering 在最终切换前保持稳定；
2. 每个实施 slice 必须能够独立保留，不能依赖未来重写才正确；
3. 新应用不得通过内部 API、共享源码、root reactor classpath 或 testkit 伪装普通 consumer；
4. 旧场景在替代 evidence 审查通过前保持可执行，不能先删除再补证据；
5. generated Java、Schema/hash、public manifest、runtime protocol 和 external consumer 不得因本专题改变；
6. benchmark 必须有 correctness guard、稳定多 fork evidence 和明确 claim boundary；
7. 历史 Governance Report 保留 provenance，但不得继续成为 current Owner；
8. 数据生成、配置解析和运行时状态不能混成同一生命周期；运行时结果不能依赖 generator 的可变内部状态；
9. G6 保持 blocked，不把两个参考应用的本机结果外推为发布支持矩阵。

## 5. 阶段

### Stage 0：专题协议与不可变基线

建立本目录、登记 active Temporary、记录 starting commit、运行文档与相称 Gate，并提交专题协议。

### Stage 1：现状审计与详细设计

完成旧 Blueprint、代码、测试、fixture、benchmark、checker、Report 和引用闭包审计；完成目标拓扑、两个应用、artifact isolation、evidence replacement 和删除策略的详细设计。

### Stage 2：构建与 benchmark 边界

建立 `soma-examples` 聚合与独立 consumer 构建；增加 isolated Maven repository Gate；将 component benchmark 从旧领域 schema 解耦。旧场景仍保持当前 Gate。

### Stage 3：工业动态调度参考应用

实现完整领域闭环、SOMA runtime projection、deterministic validator、small oracle、realistic workload、failure/lifecycle 和多 fork evidence。

### Stage 4：个体生态仿真参考应用

实现独立 Java 8 grasser–grass kernel、确定性随机、AoS/领域 oracle、SOMA runtime projection、long-run invariant、可选 renderer 与多 fork evidence。

### Stage 5：Evidence replacement 与原子切换

逐项证明替代 Owner 后，一次性切换正式 Blueprint/Design/Map/Conformance/Engineering/G5/current Report，删除旧四场景 Blueprint、代码、测试、golden、脚本和失效导航；历史材料只保留 provenance。

### Stage 6：正式收口

运行全部专项 Gate、artifact-isolation、完整 `./scripts/check.sh` 和 `git diff --check`；形成正式 Governance Report，完成 scope non-regression，原子固化长期事实，删除 Temporary，恢复无 active topic，提交全部修改并确认工作树干净。

## 6. 授权

专题获准修改与本专题直接相关的 Temporary、正式文档、Report、Maven 拓扑、`soma-examples`、`soma-benchmarks`、fixture、golden、脚本、checker、Implementation Map、Conformance、Engineering 和 G5 表述；可以自主拆分、合并、重命名或删除旧场景内部实现并进行阶段性 Git 提交。

未经新授权不得 push、发布或处理 G6。

## 7. 必须停止并请求决定

出现以下任一情况必须暂停：

- public/generated API 或 annotation Schema 需要不兼容变化；
- Access Model、Index、ownership、lifecycle 或失败原子性需要变化；
- 需要引入第三方依赖；
- 需要把 SOMA 扩展成通用 ECS、调度器或仿真框架；
- 需要删除产品能力或弱化 G0–G5；
- 两个应用无法替代旧场景承担的产品 evidence；
- 需要增加第三个正式参考应用；
- 需要支持其他 JDK distribution；
- 需要扩大到发布、签名、SCM 或 G6；
- 用户已有修改与本专题无法安全共存。

Conformance 发现本身不扩大授权。

## 8. 完成标准

Goal 只有在以下条件全部满足后才能完成：

- 根级 Blueprint/Design 不再拥有旧四场景；
- 两个参考应用具有独立完整的领域闭环和 artifact-isolation 构建；
- 两个应用不依赖 internal API、testkit、Artemis 或 reactor 捷径；
- 两个应用均有版本化配置、detached input generator、独立 bootstrap 和可重放输入摘要；
- `soma-benchmarks` 与领域 examples 解耦；
- 所有旧 SOMA evidence 责任已有替代 Owner 或正式退役裁决；
- public/generated API、Schema/hash、runtime 语义和性能没有缩水；
- 两个应用的 correctness、long-run 和多 fork evidence 通过；
- current docs、代码、golden、脚本和导航不存在旧场景残留；
- historical Report 不冒充 current Owner；
- 全部专项 Gate、`./scripts/check.sh` 和 `git diff --check` 通过；
- 正式 Governance Report、scope non-regression、长期事实固化和 Temporary 退役完成；
- `docs/README.md` 恢复“当前没有 active Temporary topic”；
- 全部修改已提交，`git status --short` 为空，G6 状态不变。

## 9. 专题文档

- [当前所有权与 evidence 审计](current-ownership-and-evidence-audit.md)
- [目标架构](target-architecture.md)
- [工业动态调度详细设计](industrial-dynamic-scheduler-design.md)
- [个体生态仿真详细设计](grassing-individual-simulation-design.md)
- [迁移与验收账本](migration-and-acceptance-ledger.md)
