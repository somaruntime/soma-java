# 目标架构

类型：Temporary

状态：active

Owner：SOMA 参考应用目标架构

事实范围：`soma-examples` 聚合、独立 consumer、文档所有权、构建隔离、benchmark 与 Gate 的候选目标结构

非事实范围：SOMA public/generated API、runtime 机制、正式模块契约或当前实现状态

最后审查日期：2026-07-23

## 1. 责任拓扑

```text
SOMA Product Blueprint
  -> SOMA Design
      -> annotations / processor / runtime-core
      -> core fixtures and neutral component benchmarks

soma-examples aggregator
  -> industrial-dynamic-scheduler
      -> application Blueprint / Design / code / tests / integrated evidence
  -> grassing-individual-simulation
      -> application Blueprint / Design / code / tests / integrated evidence

reference applications --consume--> immutable SOMA public artifacts
reference evidence ----informs----> SOMA Conformance / Governance Report
```

参考应用不进入 SOMA Blueprint → Design 追踪。SOMA 可以在 Guide、Implementation Map 或 Report 中导航其代码和消费证据，但不能把应用领域事实提升为产品规范。

## 2. 输入生成与运行时边界

两个应用采用相同的责任方向，但不共享领域代码：

```text
versioned *.properties
  -> ConfigLoader + validation
  -> detached Generator
  -> immutable/detached Problem or InitialState
  -> RuntimeBootstrap
  -> SOMA authoritative state
  -> solver/simulator loop
```

- 配置使用 Java 8 标准库可读取的版本化 `.properties`，不为配置引入第三方依赖；
- `correctness`、`default`、`large`、`long-run` 分别拥有受版本控制的配置；
- generator 不引用 generated Table/facade，也不进入 solver/simulator loop；
- runtime bootstrap 不随机生成领域事实，只校验并装载 detached input；
- CLI 可选择配置文件并覆盖显式参数，启动时必须打印最终配置、seed 和稳定 input checksum；
- 同一配置、seed 和 generator version 必须产生相同 input checksum；runtime checksum 另行记录，二者不能混为一个指标；
- 测试分别覆盖 config parsing、generation determinism、bootstrap projection 和 runtime behaviour。

这条边界使问题规模与初始状态可调，同时防止测试数据构造逻辑成为 runtime 的隐藏组成部分。

## 3. Maven 拓扑

目标 `soma-examples/pom.xml` 为 `packaging=pom` 聚合器。两个子项目必须具有独立 consumer POM，并能够在根 reactor 之外，通过已安装到隔离 repository 的 SOMA candidate 构建。

候选依赖边界：

- compile/runtime：`soma-annotations`、`soma-runtime-core`；
- compile-time provided：`soma-processor`；
- 禁止：`soma-testkit`、processor/runtime internal package、其他 example；
- 禁止隐藏依赖 root reactor output；
- 不引入第三方 dependency；
- Java source/target 和 class major 固定为 Java 8。

是否继承 root parent 由 Stage 1 以 artifact isolation 证明裁决；最终结果不能依赖未发布的 parent/pluginManagement 才能作为普通 consumer 工作。

## 4. 文档拓扑

每个应用本地至少拥有：

- `docs/README.md`：应用入口、构建、运行、验证；
- `docs/blueprint.md`：应用目标、用户旅程、领域成功标准；
- `docs/design.md`：领域模型、算法 shell、SOMA projection、failure/lifecycle；
- `docs/validation.md`：oracle、invariant、workload、benchmark claim。

所有文档声明：

```text
对 SOMA 产品规范性：否
```

SOMA 根级 `docs/blueprints/` 最终只保留产品 Blueprint；应用文档不复制 SOMA Design，只链接其使用的 public capability。

## 5. 构建隔离

新增 Gate 必须执行：

```text
clean immutable candidate
  -> install annotations / runtime / processor and required parent artifacts
     into an evidence-local Maven repository
  -> invoke each application with a separate Maven process
  -> prevent reactor classpath/source leakage
  -> run application correctness and manifest checks
```

Gate 至少验证：

- resolved dependencies 来自 evidence-local repository；
- application source 不 import internal/testkit/other-example；
- generated source、schema/hash、class major 52 可重放；
- clean and repeat generation byte-stable；
- ordinary command documented by application works。

## 6. Benchmark 拓扑

`soma-benchmarks` 只拥有 runtime/component 问题：

- Candidate source/stage/terminal allocation；
- exact index cardinality/memory；
- packed/dense/column operations；
- generated footprint/code size；
- stats/plan overhead。

它使用自己拥有的中性 schema，不依赖应用领域类型。

两个应用各自拥有：

- correctness-guarded integrated workload；
- warmup、fork、measurement、environment metadata；
- allocation、GC、throughput/latency；
- deterministic checksum/result；
- `claimAllowed=false` 默认边界，除非另有正式 evidence 治理。

## 7. Gate 拓扑

根 `./scripts/check.sh` 最终按责任编排：

1. docs/scope；
2. core Maven/public/compiler/runtime/external consumer；
3. neutral component benchmark；
4. industrial scheduler isolated consumer；
5. grassing simulation isolated consumer；
6. application long-run/performance smoke；
7. package/security existing lanes；
8. `git diff --check`。

G5 的长期含义调整为：完整 Access Model/API mapping、普通 artifact consumer 和两个独立参考应用 journey 可执行。它不声明 SOMA 拥有调度或生态仿真能力。

## 8. 删除后的边界

最终不存在：

- `ScenarioSuite`；
- FJSP/VRP/Simulation/Game root Blueprint；
- 四领域共享 example JAR；
- `soma-benchmarks -> soma-examples` dependency；
- current docs 中的“四场景产品能力”；
- 旧 phase-6 hard-coded schema/generated count；
- 新应用之间的 shared domain framework；
- generator 与 SOMA runtime state 的双向依赖；
- 将应用 Blueprint 反向追踪到 SOMA Design 的 formal link。
