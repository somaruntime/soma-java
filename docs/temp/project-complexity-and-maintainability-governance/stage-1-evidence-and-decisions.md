# Stage 1 证据与裁决

类型：Temporary

状态：active（Stage 1 已完成）

Owner：SOMA Java 项目复杂度与可维护性治理专题

事实范围：Stage 1 的引用、Owner、职责、变更放大、evidence value 审计和实施裁决

非事实范围：正式长期设计、尚未实施的代码事实和公共契约变化

核对基线：Stage 0.1 commit `eb1a520`

最后审查日期：2026-07-23

## 1. 裁决摘要

| 事项 | 裁决 | 理由 |
|---|---|---|
| D1 历史设计 | 保留原路径，正文压缩为 thin tombstone | 26 个路径仍被历史 Report 引用；路径有 provenance 价值，7,214 行正文不应留在 active checkout |
| D2 Report 拓扑 | 9 个明确被后续实现取代的 root checkpoint 移入 `reports/archive/` | 降低 root active surface；current Gate、当前性能与仍支撑当前实现的报告保持原位 |
| D3 Blueprint 重复 | 不实施内容瘦身 | 长段精确重复很少；丰富 journey、Schema 与 Java 8 示例是 Blueprint 的必要责任 |
| D4 processor/codegen | 拆开 normalized model、emission model、orchestrator 与 artifact emitter | 当前存在反向依赖、职责集中和高 co-change；可在 byte-stable generated output 下内部重构 |
| D5 evidence/test/script | 保留独立 lane，不做通用脚本框架化 | fixture 与脚本分别证明不同 compiler/runtime/consumer 边界；少量 header 重复低于共享故障域成本 |
| D6 复杂度预算 | 采用软触发器与可执行契约 Gate，不设置 LOC 配额 | 结构问题由 Owner、变化原因和变更放大判断；generated footprint 已有硬 Gate |

这些裁决不改变 Blueprint、Design、public/generated API、Schema、runtime protocol 或性能语义。

## 2. 历史设计审计

### 2.1 事实

- tracked `superseded` Design 共 26 份、7,214 行；
- 单个文件为 137–501 行，全部声明一个或多个 current replacement；
- 每条旧路径仍有 6–22 个仓库内引用，主要来自历史 Gate/Governance Report、其他 superseded 文档和历史 checker；
- current Blueprint、Design、Implementation Map、Conformance、Engineering、Guide 与 executable scenario Report 已不依赖旧正文作为 Owner；
- 切换前完整正文可在 commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的同路径文件中恢复。

### 2.2 实施规则

每个旧文件保留：

- `类型：历史设计`、`状态：superseded`、原 Owner；
- current replacement 链接；
- 不再拥有事实的声明；
- 切换前正文 commit 与 `git show <commit>:<path>` 恢复方式。

删除其余正文。模块 `docs/README.md` 不再逐项导航旧契约，只说明 tombstone 与正式入口。checker 通过发现 metadata 而不是维护 26 项硬编码列表，并验证 provenance commit。

这既保留历史链接，又让普通 checkout 不再携带第二套可误读的设计正文。

## 3. Report 拓扑审计

### 3.1 保持 current/root 的报告

- 当前 Goal、performance summary、Access Model governance/performance；
- 四场景采纳、Packed Exact 当前实现链；
- G0、G4、G6、support matrix；
- 当前文档体系与架构治理的直接 provenance。

### 3.2 移入 archive 的 checkpoint

1. `reports/archive/2026-07-10-compiler-integration-spike-report.md`
2. `reports/archive/2026-07-10-implementation-readiness-governance-report.md`
3. `reports/archive/java-v1-g5-examples-benchmark-gate-report.md`
4. `reports/archive/java-v1-phase-0-compiler-build-report.md`
5. `reports/archive/java-v1-phase-0-schema-carrier-design-gap.md`
6. `reports/archive/java-v1-phase-3-access-structures-report.md`
7. `reports/archive/java-v1-phase-4-child-materialization-report.md`
8. `reports/archive/java-v1-phase-5-full-breadth-report.md`
9. `reports/archive/soma-java-v1-topical-governance-report.md`

这些报告分别已被后续 compiler、Access Model、child、breadth、四场景或专题收口 evidence 取代。移动时更新全部仓库内链接；内容不改写，保留原时间点术语和结论。

`reports/README.md` 继续是唯一 current/history 导航；文件位于 archive 不改变其 Report 身份。

## 4. Blueprint 审计

五份产品/场景 Blueprint 共 2,598 行，其中场景 Blueprint 499–597 行。精确长行重复仅集中在表头、阅读约定和两条 Design 入口，不存在可显著降低维护成本的大段复制。

较大篇幅分别承载：

- 使用者目标流程；
- 场景数据角色与 Access Pattern Card；
- annotation schema；
- Java 8 reference path 与 allocation-aware path；
- application-owned queue/heap/transaction/failure boundary；
- 场景证明义务。

这些内容正是 Blueprint 的使用者视角责任。Stage 2 不修改 Blueprint；未来只在同一规范性事实发生实际漂移时回到唯一 Design Owner，不做篇幅驱动的集中化。

## 5. Processor/codegen 审计

### 5.1 集中与变更证据

- `DenseTableSourceGenerator`：4,432 行；
- `SomaProcessor`：3,054 行；
- 合计 7,486 行，占 annotations + processor + runtime 主源码约 45.7%；
- 22 个相关历史提交中，14 个同时修改 processor 与 generator，7 个只修改 generator，1 个只修改 processor；
- `SomaProcessor`、`DenseExactIndexSourceEmitter` 都依赖 `DenseTableSourceGenerator` 的 nested spec/helper，导致 orchestrator 反向拥有 normalized/emission model 和跨 emitter support；
- generator 同时拥有 Cursor、Batch、Scan、Table、ownership、materialization、selector、key、mutation 与 spec model。

### 5.2 目标内部结构

```text
SomaProcessor
  -> normalized schema model
  -> admission / artifact plan
  -> dense codegen model
  -> DenseTableSourceGenerator
       -> auxiliary facade emitter
       -> table emitter
       -> exact-index emitter
       -> shared selector/source support
  -> deterministic output publisher
```

实施责任：

- schema/value/table normalized model 离开 `SomaProcessor` 主类；
- Table/Field/Child/Selector emission spec 离开 generator orchestrator；
- Cursor/UpdateCursor/Batch/Mutator/KeyTraversal/Scan 进入 auxiliary emitter；
- main Table source进入 table emitter；
- selector binding/comparison/change/unique support成为 emitter 共享内部组件；
- exact-index emitter 不再静态依赖 generator orchestrator；
- generator 只负责 deterministic artifact orchestration。

不建立通用模板语言、AST DSL、反射或 metadata interpreter。内部类路径不是兼容面，但生成内容必须 byte-stable。

### 5.3 Byte-stable 基线

Stage 1 在 Zulu JDK 8 clean generated examples 上记录：

- generated Java source：222 个；
- source bytes：3,374,809；
- per-file SHA-256 manifest：`/tmp/soma-complexity-stage1-generated.sha256`；
- manifest SHA-256：`2ce2b6cd4e5da117b311a3c0719f5843140042fb6a0f8d1d269707c334de4897`。

Stage 3 候选必须重新 clean generate 并与该 manifest 完全一致；现有 schema/hash、`javap` golden、external consumer、code-size 和 full Gate 仍全部执行。

## 6. Evidence/test/script 审计

- 169 个 compiler fixture Java 文件约 5,573 行，分别覆盖正向 schema、negative diagnostic、Unicode、modifier、selector、key、child 与 external Maven consumer；
- test/example/benchmark 代码约占 tracked Java 的另一半，但分别承担 correctness、compatibility、场景和性能证明；
- 29 个 shell 文件共 3,857 行；最大脚本主要是有大量独立 negative case 的 compiler/codegen Gate，不是重复 wrapper；
- 多个脚本重复 Java 8 preflight 与 evidence-directory header，但保持单 lane 可独立执行和定位。

因此不合并 fixture，不把 Gate 压成一个通用参数化 runner，也不抽取会让全部 lane 共享失败的 shell framework。Stage 4 只允许：

- 为新的 processor/codegen 内部分层补充最小 source-shape/byte-stability 防回归；
- 删除经证明已被同一 Gate 完全覆盖的重复检查；
- 不改变现有 correctness 或 compatibility coverage。

## 7. 复杂度软触发器

后续维护审查在出现下列任一信号时触发，不自动判失败：

- 一个实现文件同时拥有三个以上稳定、可独立变化的关注点；
- normalizer/model/emitter/orchestrator 发生反向依赖；
- 一项能力变更需要同步修改三个以上没有共同 Owner 的实现位置；
- 同一规范性事实存在两个 current 定义；
- generated source/class、nested type 或 clean compile time 接近既有 Gate；
- evidence lane 只有重复形状而没有独立 failure/consumer/measurement 问题。

硬 Gate 继续只约束真实契约：public/schema/hash/golden、generated footprint、external consumer、correctness、allocation与性能 evidence。不得用结构阈值迫使功能缩水。

## 8. Stage 2–4 实施顺序

1. 先形成 tombstone 与 Report archive candidate，验证全部链接、metadata 和 current Owner；
2. 提交 immutable docs candidate；
3. 先分离 codegen model/support，再分离 artifact emitters，逐 slice clean generate；
4. 每个 code slice 比较 222-file SHA manifest并运行 processor/codegen专项 Gate；
5. 最终判断是否需要最小 evidence/checker补充；无证据则明确不改；
6. Stage 5 原子固化 Implementation Map、Engineering/Conformance、Governance Report并退役 Temporary。
