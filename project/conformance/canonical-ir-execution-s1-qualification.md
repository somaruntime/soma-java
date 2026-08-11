# SOMA Canonical Logical IR 与执行引擎 S1 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / S1_CLOSED / S2_READY`

日期：2026-08-11

Owner：Canonical IR/Execution S1最小Row纵向闭环的implementation fact、exit evidence、性能边界与
S2准入状态

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 结论

S1通过。Sequential Table scan、typed filter、count、exact Key/secondary Index substitution与
IndexSelection count已经形成可执行的最小纵向闭环：

```text
Java facade lowering
    -> CanonicalRowOperation
        -> terminal-start BoundCanonicalRowOperation
            +-- ReferenceCanonicalRowInterpreter
            +-- NormalizedCanonicalRow
                    -> CanonicalRowPhysicalPlan + ResourceEstimate
                        -> temporary admission
                            -> CanonicalRowExecutionFrame
                                -> specialized scan/Key/Index count kernel
```

该闭环没有改变public/generated application API、结果、order、null、failure、resource或release合同，
没有新增dependency、artifact、SPI、JSON/Workflow或SOMA Engine实现。S1关闭后，S2可以成为唯一active
slice。

## 2. Baseline 与变更范围

实施从clean `develop` checkout开始：

```text
activation HEAD: 397492f283e7b175f595d154968fbf69d4fab3c4
Java: Amazon Corretto 1.8.0_502
Maven: 3.9.16
```

冻结Engineering Plan保持原SHA-256：

```text
eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d
```

实施授权和active slice由Conformance拥有，没有通过改写冻结计划破坏Baseline Freeze。

## 3. Implementation fact

### 3.1 Canonical identity 与 literal

- `CanonicalTableIdentity`复用composition capability、Table ordinal与compiled layout descriptor；
- `TypedLiteral`是immutable field-width leaf snapshot，primitive保持unboxed，reference保持正式
  String-content / Enum-identity / Object-identity语义；
- canonical predicate不再持有`GeneratedProbe`或`GeneratedTable`；
- Key/Index hash、equality、order与IN membership直接消费`TypedLiteral`，没有第二份probe copy；
- generated body只增加Table ordinal到既有internal linkage，public/generated declaration不变。

### 3.2 Layer ownership

| Layer | 当前S1 Owner | 明确不拥有 |
|---|---|---|
| Canonical | source、typed predicates、terminal、compact identity/literal | StateRoot、Index、cursor、locator buffer |
| Bound | terminal-start immutable root、layout、operation/provenance | physical access decision、scratch |
| Normalized | adjacent AND、constant/null/single-IN normalization | Index container、cursor、membership |
| Physical | scan/Key/Index decision与conservative temporary estimate | actual allocation、lease、worker |
| ExecutionFrame | lease成功后的Index cursor与IN membership | long-lived semantic fact |
| Reference | 同一Bound semantics的canonical locator scan | Normalized/Physical/Index decision |

S1准入集合进入`CanonicalQueryOperation`后不再调用旧Row optimizer/executor；尚未迁移的callback、
parallel、stateful、materialization、Selection、Mapped/Primitive、Relation/Group能力仍由后续S2-S4
拥有，不被本记录误称为已迁移。`LogicalRowPlan`当前只作为Java facade lowering input，S2负责完成
完整Row/Field carrier replacement；它不参与S1 production physical decision。

## 4. Correctness、failure 与 resource evidence

- construction完成IN defensive snapshot/dedup；normalization完成empty/single IN、non-null Field的
  `isNull/isNotNull`、boolean constant与adjacent AND；
- Key、secondary Index与IndexSelection的optimized/reference exact count一致，reference始终按bound
  root canonical locator scan，不读取sidecar posting order；
- generated endpoint先验证probe owner/Field/sealed state，再创建typed literal；foreign endpoint仍返回
  stable structured invalid argument；
- Canonical与literal按composition/Table/Field identity自校验，physical Index source还校验Index对应Field；
- Query lifecycle保持guard -> bind -> normalize/plan/estimate -> lease -> frame -> execute -> release；cursor与
  membership只在lease成功后创建；
- no-IN scan/count为zero temporary reservation；IN membership使用既有conservative checked estimate；
- full `GeneratedTableTest`与repository Java 8 reactor通过，未出现failed-state、provenance或resource
  regression。

## 5. Java 8 / generated evidence

执行：

```text
mvn -q -pl soma-runtime test -Dtest=GeneratedTableTest        PASS
mvn -q test                                                   PASS
git diff --check                                              PASS
```

Full reactor重放processor、runtime与generated consumer；generated stress fixture：

```text
Tables                 112
Fields                 448
Indexes                224
Generated source       22,724,887 bytes
Generated classes      2,581
Max methods            290
Max constant pool      1,760
```

Processor source/public/verbose-ABI、negative capability与full-regeneration tests均PASS。Reviewed generated
delta只包含internal `GeneratedGroup.createTable(..., tableOrdinal, layout)` linkage及deterministic ordinal
literal；没有application-visible signature delta。

## 6. Fixed-host performance guard

环境沿用正式performance-frontier的Corretto 8、`-Xms2g -Xmx8g`、6 GiB SOMA budget、P16、3个
fresh JVM；每项独立correctness/fingerprint均PASS。本记录只重取S1改变的`frontier-source`：

| Operation | 正式治理前baseline | S1 median | 结论 |
|---|---:|---:|---|
| 10K Table typed filter | 0.425 ms | 0.444 ms | +0.019 ms；fixed-cost微秒级波动，无复杂度变化 |
| 10K exact Index count | 0.011 ms | 0.016 ms | 绝对差0.005 ms；无scan回退 |
| 1M Table typed filter | 19.451 ms | 17.468 ms | 吞吐改善约10% |
| 1M exact Index count | 0.238 ms | 0.148 ms | exact sidecar path保持并改善 |

1M callback filter为18.164 ms，证明typed path没有通过错误跳过predicate获得结果；所有expected
fingerprint均PASS。S1没有改变10M/FJSP、Relation、Group、Mutation或stateful kernel，因此不重复相应
重型证据。

## 7. Bounded review 与坏味道检查

实施完成后执行了与编码路径分离的fresh-diff结构复核：

- 冻结Plan fingerprint曾因activation metadata改写而漂移；已恢复冻结原文，将current授权移回
  Conformance唯一Owner；
- 初版`TypedLiteral`按整Table layout分配，违反field-width合同；已替换为只按目标Field leaves分配；
- scan path曾无条件分配Index cursor；已改为只有Index access path在lease后分配；
- nullable/IN/boolean normalization与canonical owner invariant已经补齐；
- no public API、new dependency/artifact/SPI、reflection、boxing universal executor、O(N) Canonical node或
  future SOMA Engine placeholder。

未发现遗留P0/P1。当前有意保留的旧family边界由S2-S4拥有，不构成S1集合内的第二physical truth。

## 8. Exit 与 claim boundary

S1 Exit：`PASS`。允许声称SOMA已经以最小Row family证明Canonical -> Bound -> Reference / Normalized ->
Physical -> Admission -> Frame -> specialized execution主线可行，并保持Java 8与性能边界。

本记录不证明完整Row/Field、Mapped/Primitive、Relation/Group、parallel统一已经完成；不证明10M或
一亿行新性能；不授权Release、Package publication、签名或正式发布声明。

下一步：激活S2，完整迁移Row、Field、terminal与Selection；在S2 exit之前不得并行激活S3。
