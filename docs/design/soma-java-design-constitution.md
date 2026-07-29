# SOMA Java 设计宪法

类型：Design

状态：正式

Owner：SOMA Java 总体设计原则

设计层次：`D0` 系统原则

主要关注点：产品边界、设计优先级与系统级不变量

上位设计：无；直接服务 Blueprint

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：产品边界、设计优先级和所有实现必须遵守的系统级不变量

非事实范围：模块内算法、代码位置、验证结果和 release readiness

最后审查日期：2026-07-29

## 1. 定位

SOMA Java 是面向 Java 8 的 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 高性能运行时状态计算库。Schema 经过编译期验证和规范化，生成 schema-specific storage、access 与 transformation facade；runtime kernel 不解释 application schema object graph。

SOMA 同时拥有 packed runtime-state plane 和围绕该状态的 typed local-compute plane。Annotation 是当前 Schema authoring surface，不是产品本质；产品不因增加 Transformation/DataFlow 而变成通用查询、DataFrame 或分布式计算平台。

SOMA 的设计同时使用两个互补视角：`State / Owner + Capability + Plan /
Lifecycle` 解释核心抽象为何存在、谁拥有事实以及怎样演进；`Logical semantics ->
Java carrier/generated capability -> JVM/OS/CPU physical strategy` 解释一次能力怎样
从易用、类型安全的逻辑契约降低为高性能执行。前者不能被数据库分类替代，后者也
不能把 primitive carrier 或某个物理算法误当成逻辑类型。

SOMA 的 canonical 系统模型由三个正交轴共同定义：

- `State / Owner`：Group、root ownership aggregate 与 parent-owned child；
- `Capability`：封闭且 compiler-bound 的 runtime 能力族；
- `Plan / Lifecycle`：Descriptor、mutable-before-freeze Plan、Effective Metadata、
  Definition、Template、one-shot Invocation 与 detached Observation。

任一轴都不能单独冒充完整产品。Capability 可以在内部局部替换，但 V1 不建立开放
SPI/ServiceLoader/plugin registry；binding 只发生在编译期、plan/create、compile/
bind 或 operation boundary，逐 row hot loop 必须保持 specialized。

设计优先级如下：

1. 目标场景和语义正确性；
2. 单一事实源、ownership 和 failure 后可推理性；
3. 类型安全、编译期诊断和稳定公共契约；
4. packed、primitive、局部且 allocation-bounded 的 hot path；
5. 可验证性、可观测性和普通 Java 8 consumer 可用性。

任何局部便利或 benchmark 优化都不得反转这些优先级。

## 2. 系统级不变量

### 2.1 事实与表示

- runtime table storage 是其 live table facts 的唯一 Owner；
- schema object、materialized row、DTO、Java Collection graph、index、stats 和 report 都不是平行 live facts；
- `@SomaTable` 类型描述 row schema 和 detached materialization shape，不是 live row object；
- application data role 与 table kind 是不同维度：input/working/result 不决定 keyed/dense，keyed/dense 也不决定 ownership。
- Logical Definition、Template、Invocation result、Delta 和 diagnostics 都是明确生命周期的语义或派生对象，不能成为与 Table 并行的 live fact source。
- Descriptor/Plan/Effective/Runtime Metadata 与 payload 分离；Metadata 描述事实与
  策略，不保存 row、candidate、scratch、result 或 live membership registry。

### 2.2 存储与 identity

- 所有 live row 都形成 `[0, size)` 的连续物理集合；
- keyed table 拥有稳定业务 identity；dense table 不拥有稳定 row identity；
- Index 只是当前 table state 内的物理位置，结构变化后可以改变；物理遍历顺序不是业务契约；
- primary identity 与 secondary exact access 是不同责任，不能用二级访问路径冒充稳定 identity；
- V1 schema storage kind 封闭为 primitive-backed scalar、白名单 String
  reference-backed immutable scalar、compiler-flattened `@SomaValue` 与
  parent-owned child；任意 object/array/DTO/Collection graph 不得进入 live field；
- primitive/enum/date/time/instant 等 logical type 通过 generated type-specific
  operation capability 暴露合法运算；内部可以共享 primitive carrier，但 carrier
  不得把不合法运算泄漏为 public logical API，也不引入 `SomaInt`/`SomaLong`
  row wrapper；
- 声明的读取能力不得依赖隐藏的全表重建；跨 operation 的持久业务顺序由 application 拥有。

具体 annotation 语义由 [Schema 与生成 API](schema-and-generated-api.md)拥有；packed relocation、exact structure 和显式排序机制由 [Table、存储与访问](table-storage-and-access.md)拥有。

Point、Candidate、Column、Key、Bulk 与 Ownership 的完整访问语义由 [Access Model 与 Candidate Scan](access-model-and-candidate-scan.md)拥有；Pipeline 不代表整个产品访问模型。

数据怎样改变 Shape、cardinality、order 和 lineage 由 [Transformation Model](transformation-model.md)拥有；ad-hoc DSL 与 reusable Typed DataFlow 的执行角色、资源和并行边界由 [DataFlow 执行模型](dataflow-execution-model.md)拥有。两种使用形态必须共享同一语义，specialized fast path 不能形成第二 correctness model。

### 2.3 Ownership 与执行

- child table 由唯一 parent row 独占，形成无环 ownership forest；
- 不允许 share、reparent、dangling child 或绕过 parent 的 owned-child release；
- 一个 root ownership aggregate 只允许单 owner、同步、非并发访问；
- SOMA 不提供跨 root/table transaction，application 负责业务提交、回滚或重建。
- `SomaGroup` 是可选 composition/resource/lifecycle Owner，不是 multi-source
  transaction 或 guard prerequisite；read-only Invocation 可以跨 Group/schema/
  instance，并继续由 Invocation 唯一协调 guards。
- parallel execution 只发生在 application 已独占的 one-shot Invocation 内部，不把 Table 变成 concurrent API，也不授予 worker 长期持有 live state 的权利。

### 2.4 正确性与边界

- mutation 成功后所有 storage、locator、exact access、ownership 和 epoch 必须一致；
- expected failure 不得留下部分可见提交；internal invariant failure 必须 fail fast；
- materialization 只产生 detached object graph，并受显式或 plan-default budget 约束；
- Eager Detached 是默认 Result Delivery；唯一 lazy 形态是同步、one-shot、
  read-only、callback-scoped delivery，不能产生 pull/async/partial result 或绕过
  resource preflight；
- lifecycle、compatibility 和错误必须可以通过结构化字段判断，不依赖 message parsing；
- runtime 不产生隐藏 I/O、全局 logger 配置或隐式持久化。
- 关键抽象必须按构造即正确：不变量由唯一 Owner 在事实产生处通过类型、不可变对象、静态工厂或 one-shot Builder 关闭；可能破坏数据、lineage、lifecycle 或原子性的检查不得依赖可关闭的 assertion。
- 每个核心抽象必须能沿 [Why / Owns / Not / Relationships / Lowering /
  Lifecycle / Resource / Failure / Evidence / Evolution](README.md#4-核心抽象叙事闭环)
  完成可追踪叙事；缺失维度必须有明确的不适用理由，不能由实现偶然性或名称猜测补足。

### 2.5 性能形状

- runtime hot storage/path 不得退化为 schema object、DTO、Java Collection graph、reflection、metadata interpreter、Java Stream、boxing tuple 或 per-row polymorphic dispatch；
- 声明的 exact access 在 mutation 成功时已经 current，读取不触发全表 rebuild；
- 候选操作只处理前序阶段产生的候选，terminal 不得静默扩展回全表；
- steady-state allocation、retained scratch、GC、working-set bytes 和 boundary materialization 必须分别可观察；
- structural bytes 使用可执行 hard ledger；reference-backed String reachable bytes
  只能是 caller-declared versioned estimate，actual dedup/heap 属于 qualification，
  不得用声明冒充 hard cap；
- logical operator 不泄漏 Hash Join、Tree Reduction 等物理策略；adaptive parallel 以 sequential 为语义基准并按有界证据回退；
- numeric common-chain 可以降低为 closed whole-loop kernel；低基数 Bitmap 和
  primitive Join runtime filter 只能由 versioned formula 在适用域内选择，并保留
  authoritative equality、reference fallback、budget 与 Explain；
- 性能优化改变语义、API、determinism、ownership、failure 或兼容性前，先修改对应 Design Owner。

## 3. 产品边界

V1 只承诺 Java 8 进程内使用，不包含 Python、C ABI、native/off-heap runtime、FFI、持久化、分布式、并发 table access、SQL/query language、无限 stream、automatic incremental view maintenance 或 application 级事务。

V1 也不包含 ordinary Iterator/pull cursor/Publisher/async result、dictionary/
character arena String backend、arbitrary object storage 或开放 runtime strategy SPI。

SQL execution/type/constraint 设计可以作为输入，但 V1 不增加 SQL/DDL/DML/DQL
兼容面、foreign key、table reference、runtime schema mutation 或通用数据库 API。

专用 priority queue、event heap、grid adapter、solver policy 和 domain cache 可以由 application 持有。SOMA 只吸收被多个目标场景证明为稳定、通用且能保持上述不变量的能力。

## 4. 设计决策规则

- 新能力必须先说明它服务的 Blueprint、Access Pattern、Logical Shape 与状态变化；
- schema 只固化稳定语义和稳定 access path，不固化可变策略；
- 优化必须比较 end-to-end cost，不能只转移 allocation、rebuild 或一致性成本；
- 当前实现与 Design 不一致时，记录 Conformance，不反向降低目标以合理化 shortcut；
- 重大长期设计变化在 Temporary 中独立形成候选，验证并获授权后再原子切换；
- 当前 slice 必须是最终架构的有效子集，不能依赖未来 migration/rewrite 才成立。
