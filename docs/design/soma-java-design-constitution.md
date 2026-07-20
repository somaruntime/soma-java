# SOMA Java 设计宪法

类型：Design

状态：正式

Owner：SOMA Java 总体设计原则

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：产品边界、设计优先级和所有实现必须遵守的系统级不变量

非事实范围：模块内算法、代码位置、验证结果和 release readiness

最后审查日期：2026-07-19

## 1. 定位

SOMA Java 是 Java 8 annotation schema 与进程内 columnar runtime state 系统。Schema 经过编译期验证和规范化，生成 schema-specific facade；runtime kernel 不解释 application schema object graph。

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

### 2.2 存储与 identity

- 所有 live row 都 packed 在 `[0, size)`；
- keyed table 拥有稳定业务 identity；dense table 不拥有稳定 row identity；
- Index 只是当前 table state 内的物理位置，删除、replace 或 compaction 后可以变化；
- keyed 和 dense 删除均使用 swap-remove/tail-fill，不保证物理遍历顺序；
- `@SomaKey` 是 primary unique identity；`@SomaUnique` 是 secondary exact unique access；`@SomaIndex` 是 secondary non-unique exact access；
- V1 不提供 range index、maintained order 或读时全表重建的 dirty selector。

### 2.3 Ownership 与执行

- child table 由唯一 parent row 独占，形成无环 ownership forest；
- 不允许 share、reparent、dangling child 或绕过 parent 的 owned-child release；
- 一个 root ownership aggregate 只允许单 owner、同步、非并发访问；
- SOMA 不提供跨 root/table transaction，application 负责业务提交、回滚或重建。

### 2.4 正确性与边界

- mutation 成功后所有 storage、locator、exact access、ownership 和 epoch 必须一致；
- expected failure 不得留下部分可见提交；internal invariant failure 必须 fail fast；
- materialization 只产生 detached object graph，并受显式或 plan-default budget 约束；
- lifecycle、compatibility 和错误必须可以通过结构化字段判断，不依赖 message parsing；
- runtime 不产生隐藏 I/O、全局 logger 配置或隐式持久化。

### 2.5 性能形状

- runtime hot storage/path 不得退化为 schema object、DTO、Java Collection graph、reflection、metadata interpreter、Java Stream、boxing tuple 或 per-row polymorphic dispatch；
- exact access 在 mutation boundary 增量维护，读取不触发全表 sort/rebuild；
- Row Pipeline stage 只处理当前候选 Index，terminal 不得静默扩展回全表；
- steady-state allocation、retained scratch、GC、working-set bytes 和 boundary materialization 必须分别可观察；
- 性能优化改变语义、API、determinism、ownership、failure 或兼容性前，先修改对应 Design Owner。

## 3. 产品边界

V1 只承诺 Java 8 进程内使用，不包含 Python、C ABI、native runtime、FFI、持久化、分布式、并发 table access、查询语言或 application 级事务。

专用 priority queue、event heap、grid adapter、solver policy 和 domain cache 可以由 application 持有。SOMA 只吸收被多个目标场景证明为稳定、通用且能保持上述不变量的能力。

## 4. 设计决策规则

- 新能力必须先说明它服务的 Blueprint 和 Access Pattern；
- schema 只固化稳定语义和稳定 access path，不固化可变策略；
- 优化必须比较 end-to-end cost，不能只转移 allocation、rebuild 或一致性成本；
- 当前实现与 Design 不一致时，记录 Conformance，不反向降低目标以合理化 shortcut；
- 重大长期设计变化在 Temporary 中独立形成候选，验证并获授权后再原子切换；
- 当前 slice 必须是最终架构的有效子集，不能依赖未来 migration/rewrite 才成立。
