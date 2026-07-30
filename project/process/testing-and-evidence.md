# 测试与 evidence

类型：Process

状态：正式

Owner：SOMA Java 测试/evidence 过程

事实范围：测试选择、外部 consumer、golden、invariant 和 evidence 完整性规则

非事实范围：Design 语义本身和当前测试结果

最后审查日期：2026-07-29

## 1. 原则

生产代码自身承担正确性，测试证明这些防线有效，而不是把全部压力交给洪水式 case 或只冻结当前实现。每个长期 capability 应先识别关键抽象和不变量，为其指定唯一 Owner，并在事实产生处通过类型系统、不可变对象、静态工厂、one-shot Builder 或真实边界校验排除非法状态。

测试优先组合：

- compile-time positive/negative fixture；
- generated/schema/public golden；
- runtime invariant 与 failure-path test；
- 普通外部 Maven consumer compile/run；
- 代表性 scenario；
- 与性能相关时的 correctness-guarded benchmark lane。

单个层次通常不足以证明跨 compiler/generated/runtime 的能力。

同一种 null、lifecycle、ownership 或非法状态只在其 Owner 的代表性边界集中验证，不为每个 forwarding method 重复。Property test 和 reference differential 覆盖通用 operator/ordering/absence 语义；组合测试只选择具有新语义的代表，另保留少量 canonical end-to-end 与性能非回归证据。

## 2. Golden 与 fixture

- Golden 只覆盖需要治理的 public/generated/schema surface；
- 更新 golden 前必须确认是被批准的 contract 变化，而不是让测试迁就实现；
- Negative fixture 必须验证 stable diagnostic category/code/location，而不依赖易变 prose；
- Unicode、hash、生成顺序和 artifact 输出必须在 clean/repeat run 中确定；
- Fixture 不获得 production-only bypass 或不同语义。

## 3. Invariant

Runtime tests 应覆盖 stable state 和 failure boundary，特别是：

- packed range、primary locator、exact links 和 compaction repair；
- unique/key conflict、floating canonicalization；
- child ownership forest、cascade、stale handle/view/snapshot；
- Access Model source/cardinality、ordered stage、stable sort、scalar Index、Unique point/Scan bridge；
- Candidate Scan/Traversal one-shot、old-handle alias、terminal failure consumption、consumed-plan reference cleanup；
- callback/resource/allocation failure atomicity；
- materialization budget、deep graph 和 all-or-nothing；
- stats reset/current/high-water self-consistency。
- Definition Builder、Template/Invocation one-shot、source/parameter/output identity；
- Shape/lineage/operator legality、outer absence、Join multiplicity、Group/Partition order；
- sequential/parallel result、order、failure 和 Effect identity；
- Delta duplicate/presence/epoch/resource preflight 与 safe-point one publish。
- Descriptor/Plan/Effective/Runtime Metadata identity、freeze、projection Owner和
  post-release snapshot；
- SomaGroup multi-schema/multi-instance slot、attach rollback、parent/root ledger、
  cross-Group guard、fault/degraded/release/version；
- String payload/Key/Unique/Index/Group/Join、optional/empty、equal-value
  different-object no-op、`@SomaValue` String leaf、clear/release/reference GC；
- flat/head-tail Segment publication、compact locator collision/rehash/growth 与
  closed Candidate shape differential；
- unknown high-expansion bound在source enumeration/callback前fail closed；
- Segment/Morsel/Execution Vector direct/parallel deterministic differential、
  task bound、cancel/deadline visibility与executor ownership；
- Eager/callback Candidate/Value/Group/Join/Window full/early-stop/exception/
  conflict/non-escape/use-after/cleanup/resource differential。

Candidate/DataFlow executor优化必须用reference evaluator或等价oracle覆盖声明顺序、absence、callback/failure和Effect语义；source/terminal shortcut不得只靠benchmark结果证明正确。测试不冻结 private helper、internal node 或 backing-array layout，除非该形状本身是明确的 footprint/compatibility contract。Generated命名切换还必须同时验证current public tokens与旧canonical token absence，历史Report/superseded文档除外。

所有 public/generated breaking replacement 必须在同一 evidence set 中证明 new
surface可用与old canonical token/path缺失。不得用compatibility adapter让两套
Definition lifecycle、Object value protocol、plan entry或Metadata Owner永久并列。

## 4. Evidence 质量

Evidence 必须可追踪到 Design capability、commit、命令和 artifact。Report 只使用 validator 接受的结构化 artifact或可复现直接输出；不得把“测试类存在”“脚本打印 ok”或无 checksum 的手工摘录当成充分证据。

发现 flaky、环境跳过或网络依赖失败时，明确标记未验证；不能把以前的 passed 自动外推到新 commit。

编排器可以复用同一run中已通过的reactor、installed artifact和benchmark classes，
但consumer Gate仍必须检查实际输入存在、从普通Maven解析路径构建，并执行自己的
oracle。共享准备减少重复构建，不合并Capability、failure domain或evidence
Owner；找不到prepared输入必须fail closed。

Performance baseline 还必须验证 measurement/baseline/result 三种 artifact 的
schema、exact record shape、`claimAllowed=false`、fork 连续性、workload identity
和环境稳定性。环境不匹配只能在这些检查之后得到 `not-applicable`；invalid
artifact 不得借环境差异逃逸。Comparator 的 negative paths 至少覆盖 pass、
metric failure、environment mismatch、claim、shape、fork、identity 和样本稳定性。

## 5. 复杂度防回归

LOC、文件数和类数只用于发现异常，不是删除、合并或拆分的验收配额。出现以下信号时应进行责任审查：

- 一个实现 Owner 同时承载三个以上稳定且可独立变化的关注点；
- normalizer、model、emitter 与 orchestrator 出现反向依赖；
- 一项能力变更需要同步修改三个以上没有共同 Owner 的位置；
- 同一规范事实存在两个 current 定义；
- generated footprint、class size、clean compile 或 Gate 成本接近既有上限；
- evidence lane 只有相似外形，却不能说明独立 failure、consumer 或 measurement 问题。

审查必须回到 Blueprint、Design、Owner、变化原因和 evidence value；软信号不自动判失败，也不得迫使功能或 Gate 缩水。Compiler/codegen 的 normalized model、codegen model、orchestrator 和 emitter 责任边界由既有 codegen admission Gate 检查；fixture、scenario、benchmark 和脚本只在能降低共享机制成本且不合并独立证据域时才整合。

Benchmark lane 的 manifest/identity/validation、typed workload、evidence assembly 与 aggregation 应保持可独立审查；兼容 facade 可以编排和转发，但不成为第二事实 Owner。Generated footprint 同时保留固定 candidate 回归 Gate 与 artifact/schema 归一化诊断：诊断用于定位增长来源，不替代既有基线、阈值或 compiler admission，也不得外推为容量或性能 claim。
