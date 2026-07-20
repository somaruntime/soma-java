# Security model

类型：历史设计
状态：superseded
Owner：根项目协调层
当前取代者：[兼容性、安全与版本](design/compatibility-security-and-versioning.md)、[测试与 evidence](engineering/testing-and-evidence.md)、[Release 治理](engineering/release-governance.md)
事实范围：SOMA Java trust boundary、protected assets、compile/runtime abuse cases、resource exhaustion、diagnostic exposure 和 supply-chain obligations
非事实范围：业务授权、application data classification、漏洞报告联系人、具体修复结果和第三方 scanner 配置
最后审查日期：2026-07-10

> 本文只保留切换前的历史设计上下文，不再拥有当前事实；当前工作必须使用上方取代者。

## 1. 目标

SOMA Java 是进程内 compiler + runtime library，不是 security sandbox。本文定义实现必须防止的 library-level corruption/abuse，以及明确由 application/build environment 负责的边界。

Security correctness 不等于业务数据合法性。Application 仍负责 authentication、authorization、tenant isolation、domain validation 和 sensitive-data policy。

## 2. Protected assets

SOMA 需要保护：

- generated source/class 的完整性和可复现性；
- schema/hash/compatibility identity 的真实性；
- TableStore authoritative facts 和 ownership graph；
- primary key locator/exact index与base fact的一致性；
- lifecycle boundary，避免 stale/use-after-release/borrow violation；
- process availability，避免无界 allocation、深递归和 collision amplification；
- diagnostics，避免默认泄露完整业务 payload；
- release artifact 和 dependency supply chain。

SOMA 不持有 network credential、database password、encryption key 或 persistent data file。

## 3. Trust boundaries

```text
application Java source / annotations
  -> compiler integration + processor
  -> generated source/resources/classes
  -> application-supplied runtime data/callbacks/config
  -> generated API
  -> runtime-core authoritative state
  -> detached materialization / diagnostics
```

边界原则：

- schema source 是受构建权限控制但仍需校验的输入；
- annotation string/name/selector/default 不能直接拼接成未转义 Java source/path；
- generated code 是 build artifact，不是可信手写白名单；
- runtime values、batch size、key/selector distribution、child cardinality和callback都可能造成异常或资源压力；
- detached object/DTO 离开 runtime 后由 application security policy 管理。

## 4. Compile-time security

Processor/compiler integration 必须防止：

- generated package/type/member name source injection；
- path traversal 或写出 compiler-managed output location；
- 利用 logical name 覆盖 service/resource/internal artifact；
- duplicate generated type/resource 冲突被静默覆盖；
- unsupported compiler 下生成 partial/mutable effective type；
- processor 从网络、环境 secret 或非声明文件读取不确定输入；
- local absolute path、username、timestamp 进入 generated source/metadata；
- compiler internal exception 被吞掉后继续发布 artifact。

所有 source/resource 通过 compiler `Filer` 或受控 build output 创建。Name/path normalization 后仍必须验证 Java identifier/package/resource constraints；失败使用 structured diagnostics 并阻止 codegen。

Compiler plugin/processor 不执行 annotation 指定的 arbitrary class、script、expression 或 callback。

## 5. Runtime integrity

Runtime 必须：

- 在 visible mutation 前校验 key、unique、selector、capacity 和 ownership precondition；
- expected failure 保留旧 visible facts；
- 防御 wrong-owner/dangling/released child handle 和 impossible ownership cycle；
- structural mutation 尊重 active borrow/view pin；
- release 后稳定拒绝访问；
- checked arithmetic 处理 size/capacity/byte estimate/counter；
- object/reference column 在 remove/clear/replacement 后清除 dead reference；
- internal invariant violation fail fast，不能继续返回看似合法结果。

Runtime internal handle、RowSlot、bucket、bitmap、exact-index group/link和allocator detail不进入public API，避免调用方绕过invariant。

## 6. Resource exhaustion and denial of service

以下输入需要显式 guard：

- negative/overflow/超大 capacity 或 batch size；
- primary/exact hash collision、probe、rehash amplification；
- 极深/极宽 child ownership graph；
- recursive materialization table/row/leaf/allocation explosion；
- 大量小 child instance 和 over-reserved capacity；
- unbounded string/reference retention；
- mutation-heavy exact-index write amplification与replaceAll fresh-build峰值；
- dynamic sort/scratch high-water retention；
- user callback 的长时间执行、递归或异常。

应对机制：

- create/mutation 前 memory estimate 和 overflow-safe validation；
- bounded `MaterializationBudget`；
- runtime plan load/capacity/scratch/storage limit；
- primary/exact collision/probe/rehash/group/storage high-water stats；
- all-or-nothing publish；
- 对可预估/可控 resource failure 返回明确 resource error；raw `OutOfMemoryError` 作为 JVM fatal error 原样传播，但 group staging 必须避免 partial publish，绝不能捕获后继续伪装成功或静默损坏。

SOMA V1 不提供 wall-clock timeout、preemption 或 callback sandbox。Application 对不可信 callback、总进程 heap、CPU budget 和任务超时负责。

## 7. Hashing and adversarial keys

- hash collision 后必须执行 canonical full-key equality；
- collision 不能导致错误命中、duplicate 漏检或 locator corruption；
- probing/load/rehash strategy 属于 versioned runtime plan，并可诊断；
- claim-grade/security-sensitive workload 必须包含 collision fixture；
- V1 不承诺 cryptographic hash table 或 resistance to hostile multi-tenant internet input；
- application 若直接接收攻击者控制的海量 key，必须在 SOMA 外层做配额、rate limit 或 isolation。

Schema hash 使用 cryptographic digest 是 compatibility identity，不是 digital signature 或 artifact authenticity proof。

## 8. Callback boundary

Row Pipeline filter/update/comparator 是 application code：

- runtime 不捕获后伪装成 SOMA internal error；
- callback exception 按 public error contract 保留 cause/ownership boundary；
- mutation terminal 必须遵守 correctness model 的 visible failure semantics；
- callback 不得接收 internal storage handle；
- escaped cursor/mutator access 被 lifecycle guard 拒绝；
- comparator 内 cross-table access、materialization 或 allocation 是 application responsibility，benchmark 不得把它隐藏为 runtime kernel 成本。

## 9. Diagnostic and privacy boundary

默认 error/stats/context 可以包含：

- schema/table/field/selector/path；
- code/category；
- count/limit/capacity/index；
- compatibility identity；
- exception type/cause chain。

默认不得自动 dump：

- 全量 row/table；
- arbitrary `toString()` payload；
- secrets、credential、environment variable；
- local filesystem absolute path；
- unbounded key/string content。

需要展示 key/value 时应由 generated safe formatter 使用长度限制、escaping/redaction；application 决定是否记录更完整业务值。Runtime 不直接写 stdout/stderr 或全局 logger。

## 10. Persistence and network boundary

SOMA runtime 不提供：

- network listener/client；
- remote code execution；
- persistence/serialization/replay format；
- encrypted storage；
- authentication/authorization；
- multi-tenant isolation。

Materialized Object 被 application 映射为 JSON/protobuf/database/wire 后，安全责任属于 adapter/application；不得把该格式称为 SOMA secure persistence format。

## 11. Supply chain

- production runtime baseline 不引入第三方 dependency；
- compiler/build/test dependency 也必须 pin 版本并审查 license/security/transitive graph；
- release artifact 必须从 clean tagged commit 构建并记录 checksum；
- source/javadoc/binary/metadata 使用同一 revision；
- CI secret 不进入 generated artifact/log；
- dependency repository、publishing credential 和 signing material 由 release environment 管理，不提交仓库；
- release 前必须有 dependency/license scan 和 provenance/checksum evidence。

## 12. Vulnerability handling boundary

公开 release 前必须提供 repository-level `SECURITY.md`，包含真实、私密、可操作的报告渠道、支持版本和响应预期。该社区文件不是设计事实源；在负责人/渠道未确定前不得用 placeholder 地址冒充完成，也不得公开发布 artifact。

安全缺陷修复可能打破兼容性，但必须记录 affected versions、impact、workaround、fixed version 和 migration。不得覆盖已发布 artifact。

## 13. Evidence obligations

至少覆盖：

- identifier/package/resource injection negative fixture；
- duplicate output/path escape rejection；
- unsupported compiler fail closed；
- capacity/checked-arithmetic boundaries；
- primary/exact hash collision、same-hash full-equality与rehash cases；
- deep/wide child and materialization budget boundaries；
- wrong-owner/dangling/released handle；
- callback exception and no-partial-visible-state case；
- diagnostic redaction/size limit；
- dependency/license/security scan before public release。

## 14. 非目标

本文不把 SOMA 定义为 sandbox、secure database、cryptographic library、multi-tenant service、untrusted-code executor 或 business authorization framework。
