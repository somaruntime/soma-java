# 当前所有权与 evidence 审计

类型：Temporary

状态：active

Owner：SOMA 参考应用现状审计

事实范围：起始基线上的旧四场景资产、依赖、验证责任、current 引用和待闭合审计项

非事实范围：正式 Design 裁决、最终删除决定、实现授权扩展或最终 Conformance

最后审查日期：2026-07-23

起始基线：commit `93bcfffca440f2cbb84d388a2c45410d95a0fbed`

## 1. 起始状态

- 工作树在专题开始时干净；
- 分支为 `develop`；
- `docs/temp/` 没有 active topic；
- 正式文档把 FJSP、VRP、Simulation、Game 登记为 Blueprint、Design 追踪、Conformance 目标和 G5 canonical scenario；
- `soma-examples` 是单个 JAR 模块，直接包含四个领域包；
- `soma-benchmarks` 依赖 `soma-examples`，component 和 integrated benchmark 均导入领域 schema；
- `scripts/check-examples-phase6.sh` 同时承担场景执行、schema/hash、public facts、Java 8、source-shape 和部分 runtime invariant。

## 2. 资产边界

| 边界 | 当前 Owner/入口 | 审计重点 |
|---|---|---|
| 场景 Blueprint | `docs/blueprints/*-runtime-state-blueprint.md` | 是否属于 SOMA 产品目标，如何原子退役 |
| 产品 Blueprint | `docs/blueprints/soma-java-product-blueprint.md` | FJSP-specific 代码与链接如何中性化 |
| Design 追踪 | `docs/design/README.md` | 删除场景反向追踪但不改变通用 Design |
| current Map/Conformance | `docs/implementation-map/scenario-and-benchmark-map.md`、`docs/conformance/current-conformance.md` | 改为参考应用消费证据 |
| examples 代码 | `soma-examples/src/main/java/.../{fjsp,vrp,simulation,game}` | 区分领域逻辑和 SOMA evidence |
| examples 测试 | `FjspVerificationSuite` + scenario executable assertions | 找到每项 invariant 的替代 Owner |
| examples docs/reports | `soma-examples/docs`、`soma-examples/reports` | current 输出退役、历史 provenance 保留 |
| Gate | `scripts/check-examples-phase6.sh`、`scripts/check.sh` | 拆出 reference-application 与核心 contract Gate |
| benchmark | `soma-benchmarks` + FJSP/component scripts | 中性 schema 与应用自有 integrated evidence |
| golden/schema | examples expected manifest/schema/hash/public facts | 不把删除业务 schema误判为产品兼容性变化 |
| Reports | root current/archive reports | current 导航与历史事实分离 |

## 3. 旧场景当前承担的责任

### 3.1 FJSP

- composite key、primary/unique/exact access；
- owner child candidate-machine input；
- keyed candidate frontier；
- exact-group update/filter/sort/first/remove；
- application indexed machine heap；
- swap-remove deterministic tie-break；
- lifecycle、failure、allocation/GC 和 100k integrated benchmark。

### 3.2 VRP

- definition/assignment/workspace 分离；
- parent-owned route visits；
- dense `replaceAll + sorted` workspace；
- non-empty route rewrite；
- stale version guard；
- exact travel-cost lookup、authoritative-first commit 和 derived-workspace recovery。

### 3.3 Simulation

- definition/state-vector 分离；
- packed dense numeric update；
- application-owned event heap；
- batch trace append/export；
- numeric failure、finite validation 和 no-per-step materialization。

### 3.4 Game

- keyed coordinate/occupancy access；
- selected-scope dense workspace；
- stale generation/revision guard；
- cache rebuild；
- deterministic total order、primitive staging 和 failure recovery。

## 4. 初始责任分类

| 责任 | 初始目标 Owner | 说明 |
|---|---|---|
| primary/unique/exact access | 核心 fixture + scheduler | 产品能力，不随场景删除 |
| Candidate Scan stages/terminals | 核心 fixture + scheduler/simulation | 产品能力 |
| ColumnView/packed numeric access | 核心 fixture + simulation | 产品能力 |
| child ownership/replacement | 核心 child fixture + scheduler | 产品能力 |
| swap-remove/Index 生命周期 | 核心 invariant + 两应用 | 产品能力 |
| materialization/budget | 核心 fixture + 应用边界 | 产品能力 |
| failure/lifecycle | 核心 invariant + 应用 aggregate | 产品能力 |
| component allocation/memory/code size | 中性 benchmark | 产品质量 evidence |
| FJSP/VRP/Game 领域策略 | 正式退役或新应用自有 | 不是 SOMA 产品能力 |
| simulation/game renderer/algorithm | 应用自有或退役 | 不是 SOMA 产品能力 |
| integrated application performance | 两个应用 | 不进入中性 component runner |

## 5. Stage 1 必须完成的引用闭包

需要对以下内容做完整 `rg`/path/owner 审计并逐项登记：

- 四个 Blueprint 文件名及其标题、Owner、链接；
- `FJSP`、`VRP`、`Simulation`、`Game`、`四场景`、`canonical scenario` 的 current 使用；
- examples package/class/schema/table 名；
- benchmark import、lane id、artifact schema 和 validator；
- phase-6 expected schema/hash/public/generated/class manifests；
- root/module POM dependency 和 module ordering；
- current guides、Map、Conformance、Engineering、Reports、README、AGENTS；
- archived Report 和 superseded provenance；
- checker 中硬编码路径、计数和文本 shape；
- `target/` 以外的 committed fixtures 与 accidental `.DS_Store`。

## 6. 审计判定规则

- 不以类名、文件名或静态未使用作为删除依据；
- 一个 domain test 如果同时证明 SOMA invariant，先建立替代 Owner；
- 旧业务 schema/hash 的删除不是 SOMA public API breaking change，但必须关闭 current golden/Report/validator 引用；
- 历史报告不重写当时事实；只在 current index 中退出；
- 若发现旧场景事实已复制进通用 Design，必须判断其是否真正是产品不变量，不能机械删除或机械保留；
- 若两个新应用不能覆盖某项产品责任，必须停止而不是降低 Gate。
