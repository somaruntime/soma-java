# SOMA Canonical Logical IR 与执行引擎 S4 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / S4_CLOSED / S5_READY`

日期：2026-08-11

Owner：S4 Relation与Group family的implementation fact、exit evidence、性能边界与S5准入状态

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 结论

S4通过。GroupBy与binary Relation普通terminal已经进入Canonical语义、terminal-start binding、
Normalized rewrite、PhysicalPlan、conservative admission与ExecutionFrame主路径；Java generated facade只保留
validation、one-shot claim与lowering。Relation的两张Table在同一个Group operation guard下绑定，hash、
Index lookup cursor、matched-side state与Group state只在admission后创建。

本slice没有改变public/generated API、Join kind、null-never-match、outer missing、duplicate Cartesian、
canonical order、Group key/aggregate、callback、failure或resource合同，没有新增dependency、artifact、SPI、
JSON/Workflow或SOMA Engine实现。

## 2. Implementation fact

### 2.1 Group

- `GeneratedGrouping`成为最小one-shot Java facade；原facade内约600行semantic/execution实现退出；
- `CanonicalGroupOperation`拥有source、typed key、aggregate、value mapper与terminal事实，不持有root、
  cursor、lease、hash storage或result buffer；
- GroupBy复用canonical Row source、binding、resource admission与execution frame；`GroupState`及其key/value
  storage只在lease成功后建立；
- production与reference消费同一Bound Row semantics，前者使用canonical physical row execution，后者使用
  独立canonical scan/reference grouping算法。

### 2.2 Relation

- `CanonicalRelationOperation`拥有two-table identity、equality fields、Join kind、typed/callback filters、
  execution request与terminal；
- `BoundCanonicalRelationOperation`在terminal start一次性绑定两张current roots；
- `NormalizedRelationOperation`只拥有经过证明的Inner typed-filter pushdown prefix与residual；
- `PhysicalRelationPlan`拥有`NESTED_CROSS / RIGHT_INDEX_LOOKUP / RIGHT_HASH`、access、build side与
  conservative temporary estimate；cursor、right hash和matched-right state不进入Canonical或Bound；
- production执行使用Index lookup或right hash，reference继续使用独立nested-loop oracle，不读取production
  algorithm/access/build-side decision；
- relation mapped/primitive的旧内部`Plan`已退出，剩余类型明确为Java facade pipeline capture；terminal以
  `MAP_SOURCE/PRIMITIVE_SOURCE`进入同一two-root lifecycle，再由既有specialized kernel消费。

### 2.3 有界迁移桥

Semi/Anti Join之后的left `ReadStream`仍通过`executeLeft -> BoundRowPlan`桥接旧Row adapter。它不参与普通
Relation terminal、Group、mapped或primitive主路径，不拥有第二套Join semantic/algorithm；该桥明确由S5
layer closure删除。S4不把迁移桥误称为最终结构，也不为它增加兼容层。

## 3. Evidence

- runtime suite覆盖所有Join kind、null、outer missing、Cartesian、canonical order、pushdown/residual、
  Index/hash、Group key/order/aggregate、resource/failure与optimized/reference differential；
- processor suite覆盖Java 8 generated relation/group/mapped/primitive consumer和112 Tables、448 Fields、
  224 Indexes stress；
- `mvn -q -pl soma-runtime test`、`mvn -q -pl soma-processor -am test`、clean reactor/examples/benchmarks
  generation与`git diff --check`通过；
- 冻结的Engineering Plan SHA-256保持
  `eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d`。

## 4. Fixed-host performance guard

Corretto 8、6 GiB SOMA budget、P8的1M `frontier-relation`复核全部fingerprint `PASS`：

| Operation | 正式baseline | S4 recheck median | 结论 |
|---|---:|---:|---|
| Join count | 167.008 ms | 168.680 ms | 等价 |
| filtered Join | 295.181 ms | 302.216 ms | 同一等级 |
| Semi / Anti | 127.151 / 180.234 ms | 146.172 / 172.033 ms | 同一算法与复杂度 |
| low GroupBy | 23.513 ms | 15.910 ms | 改善 |
| high GroupBy | 63.842 ms | 48.353 ms | 改善 |

首次/复核Join count分别为218.154/168.680 ms，表明fixed-host存在明显扰动；没有完整pair materialization、
per-row canonical node或新分配等级，因此不继续以重复运行替代因果分析。

## 5. Exit 与 claim boundary

S4 Exit：`PASS`。S5可以激活并删除`executeLeft/BoundRowPlan`桥、old optimizer/executor adapter与剩余layer
inversion；在S5关闭前不得激活S6。

本记录不证明S5或最终qualification完成，不证明一亿行或跨硬件新性能，不授权Release、Package
publication、签名或正式发布声明。
