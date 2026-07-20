# 测试与 evidence

类型：Engineering

状态：正式

Owner：SOMA Java 测试/evidence 过程

事实范围：测试选择、外部 consumer、golden、invariant 和 evidence 完整性规则

非事实范围：Design 语义本身和当前测试结果

最后审查日期：2026-07-19

## 1. 原则

测试验证 Design，而不是只冻结当前实现。每个长期 capability 应优先组合：

- compile-time positive/negative fixture；
- generated/schema/public golden；
- runtime invariant 与 failure-path test；
- 普通外部 Maven consumer compile/run；
- 代表性 scenario；
- 与性能相关时的 correctness-guarded benchmark lane。

单个层次通常不足以证明跨 compiler/generated/runtime 的能力。

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
- callback/resource/allocation failure atomicity；
- materialization budget、deep graph 和 all-or-nothing；
- stats reset/current/high-water self-consistency。

## 4. Evidence 质量

Evidence 必须可追踪到 Design capability、commit、命令和 artifact。Report 只使用 validator 接受的结构化 artifact或可复现直接输出；不得把“测试类存在”“脚本打印 ok”或无 checksum 的手工摘录当成充分证据。

发现 flaky、环境跳过或网络依赖失败时，明确标记未验证；不能把以前的 passed 自动外推到新 commit。
