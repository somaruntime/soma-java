# SOMA Java I1 Primitive Keyed Table Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I1 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2-G5 I1_SCOPE_PASS / IN_PROGRESS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G6-G9 NOT_RUN`

正式事实源：是（I1 implementation、primitive keyed Table纵向闭环与资格证据）

Owner：SOMA Java I1 primitive keyed Table current executable fact

资格日期：2026-08-04

## 1. 结论

I1 已从真实`.schema`输入建立第一条完整的Java 8 production纵向闭环：

```text
schema
    -> generated Soma / SomaGroup / detached object / Table / View / Editor / Field
        -> paged PLAIN long storage + primitive Key
            -> reserve / add / find / get
                -> typed filter / count
                    -> point update
                        -> structured failure + atomic publication
```

本slice满足
[Implementation Plan](../engineering/v1-implementation-plan.md#5-i1--primitive-keyed-table-vertical-slice)
的I1 Exit：公开generated surface、paged Chunk directory、long-domain size/capacity、Group guard、
one-shot pipeline、callback scope token、primitive Key、candidate-root swap与prevalidated final-commit
两条publication mechanism均已形成production code和failed-state证据。

I1没有外推I2-I8能力：全部Field type、Index、remove/Selection mutation、完整IR/reference
interpreter/optimizer、Join/GroupBy、parallel、compression、metadata、million-row performance、CI/package
与release qualification仍未成立。

## 2. 冻结输入与提交

| 项目 | 冻结事实 |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| I1 base | `1242e5121f980e4169779b2bd04782d392d0242d` |
| I1 implementation commit | `3ef250b7ca50ebc4e598f0e09a02d5e9d44876bf` |
| Production topology | 恰好`io.github.somaruntime.soma:soma-runtime`与`io.github.somaruntime.soma:soma-processor` |
| Historical qualification | I1 时点的 `scripts/qualify-i1.sh`；slice-only replay 已在交付导向治理中退役 |
| Current cumulative route | module tests、current cumulative consumer、`build-support/qualification/artifact-build.sh`、`scripts/check.sh` |
| Dirty-state policy | 最终资格在implementation bytes暂存后执行；随后的implementation commit未改变这些bytes；本记录与状态路由由独立Conformance commit拥有 |
| Generated output policy | generated source/class只存在于`target/`或资格临时目录，不提交 |

I1没有新增production artifact、third-party dependency或predecessor/compatibility surface。正式
Signature继续唯一使用`@SomaField` annotation与`SomaFieldEndpoint<R,V>` generic marker；不存在旧名
alias。

## 3. 资格环境与入口

| 维度 | 值 |
|---|---|
| OS / arch | macOS `26.6` / `arm64` |
| Machine continuity | 与I0资格相同的18 logical processor、48 GiB机器 |
| JDK / javac | Amazon Corretto `1.8.0_502-b07` / `javac 1.8.0_502` |
| Maven | Apache Maven `3.9.16` |

Canonical command：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
    ./scripts/check.sh
```

最终结果：

```text
soma-runtime tests:    18 run, 0 failures, 0 errors, 0 skipped
soma-processor tests:  15 run, 0 failures, 0 errors, 0 skipped
I0 regression:         ok
I1 consumers:          6 compiled/running journeys, all ok
full regeneration:     initial Alpha -> clean rename Gamma, stale source/class/manifest absent
compile-negative:      13 independently asserted forbidden surfaces
generated javap:       full public surface golden, Java class major version 52
bytecode scan:         no primitive wrapper boxing/unboxing or java.lang.reflect in admitted path
i1-qualification:      ok
git diff --check:      pass
```

## 4. Generated与compiler证据

Processor只在composition的全部Table均为“一个direct long Key、至少一个long payload、无Index”时
生成I1公开纵向surface；其他合法schema仍只生成I0 internal linkage，等待I2扩展，不能伪装为已支持
能力。

I1证明：

- `Soma`只拥有configuration、default/explicit Group与default Table accessor；class initialization
  本身不freeze；configure-first、class-load-then-configure与default-first均有独立consumer；
- 每个Group中同类型Table是single identity；default与explicit Group隔离；application不能直接构造
  `Soma`、`SomaGroup`、Table、View或伪造generated capability；
- detached object、Table、View、Editor、Selection及全部typed Field endpoint的公开surface由统一
  `javap` golden冻结；Key不进入Editor；I2+ surface有compile-negative；
- `GeneratedNames`是当前generated naming唯一实现Owner；`GeneratedSymbolTable`在任何source写入前
  完成exact、NFC/case-folded与有限FQN占用检查；
- runtime/JDK基础设施与detached object引用使用FQN，`Optional`、`GeneratedLong`、`Object`、
  `String`、`Long`、`View`、`Consumer`、`Override`等application名称不能shadow generated依赖；
- 两次独立javac生成source逐字节一致；golden SHA-256与真实Maven clean rename/delete fixture通过；
- I1五个generated source的任一点写失败都不能开始success manifest publication。

Generated source golden：

| Source | SHA-256 |
|---|---|
| `Entity.java` | `94ece19ddab27dcb905829d95ede8d184986e03195335495e25ab75ac09d80b5` |
| `EntityTable.java` | `9720373c1d328c55421bbce736b7d7e210faaddd2a62bdc2bda121cfc62b86af` |
| `Soma.java` | `6f2519af66361c95b824c74eb9003719d679488b73896bd88ebec6b4a3a7ba19` |
| `SomaGroup.java` | `7fa1106febd2e32c4d708704ed76d7b7f73467218735e218cf3e4b1c1d9c266b` |

## 5. Storage、Key与resource证据

第一条storage不是single Java array或boxed universal engine：

- Table size/capacity与row locator使用`long`；physical chunk与paged directory使用bounded `int`
  局部索引；tiny-Chunk测试跨越多个Chunk和directory boundary；
- PLAIN storage以primitive `long[][]`承载多列；Key使用primitive sharded open-addressing结构；zero
  Key合法、duplicate稳定失败、missing find/get语义分离；
- `StateRoot`同时发布payload directory、Key index、size/capacity、state version与managed-byte
  accounting；读路径只观察最近一次完整root；
- retained与temporary memory由global memory manager显式准入，checked arithmetic fail closed；paged
  directory节点与chunk引用计入保守结构预算；
- reserve/add/update在预算或注入失败时root、version、payload、Key可见性与accounting保持不变；
- missing point update即使global temporary budget已被占满仍返回`matched=0/changed=0`，不申请
  candidate peak且不执行callback；命中路径在callback前完成worst-case peak admission。

I1证明long-domain architecture与局部boundary mechanism，不声称已分配near-`Long.MAX_VALUE`内存，
也不替代I2的全部primitive/reference/Value/Index/million-row Gate。

## 6. Query、mutation与failure证据

- typed long Field支持`eq/ne/lt/le/gt/ge/between/in`及`and/or/not`；`in` defensive snapshot、empty
  `in`、逆`between`与foreign Table expression均有证据；
- pipeline在terminal开始时绑定current root；intermediate成功后parent永久被claim，validation失败前
  不消费receiver；terminal成功或失败后均不能重放；
- `count()`在I1 primitive path无reflection/boxing；callback filter、parallel、remove与Selection
  update明确不存在；
- point update missing不调用callback；no-op保持version；changed update通过prevalidated bounded
  final commit发布；escaped Editor稳定失败为`CALLBACK_SCOPE_VIOLATION`；
- application callback抛出的checked/unchecked exception统一映射为`CALLBACK_FAILED`并保留cause；
  application重放SOMA failure也不能注入当前runtime provenance；
- structured failure carrier、operation、code与UpdateResult均由internal shared factory创建，公开
  constructor为private；validation/resource/callback失败没有partial publication。

## 7. Artifact fingerprint

以下是最终I1 canonical qualification生成的local artifact fingerprint，不是release、签名或remote
publication：

| Artifact | SHA-256 |
|---|---|
| `soma-runtime-1.0.0-SNAPSHOT.jar` | `621dc5876a5d4528ef37e869ed3bec8ee3689cac5f518ffd912d28faa2083ea1` |
| `soma-runtime-1.0.0-SNAPSHOT-sources.jar` | `5362a265a37042a9d40d0362db0879e1e197b1769c4f5e26d57a13c5e22bb260` |
| `soma-runtime-1.0.0-SNAPSHOT-javadoc.jar` | `827e707243f1d42c29bc1befeaaebcfc0383202cddbb4a2b70b0b03296392d46` |
| `soma-processor-1.0.0-SNAPSHOT.jar` | `1542a8642d070e3340475a31faa3e06eafceb93a69a32e7911c4efce3b33ba46` |
| `soma-processor-1.0.0-SNAPSHOT-sources.jar` | `27b8556f79f7ba393ff94d52b5fdf14a313b12d70a483db51691779e644fa374` |
| `soma-processor-1.0.0-SNAPSHOT-javadoc.jar` | `99de7289ad45c270071be77f4e8f7eb9c389c1e77de7a19628122686274b52cc` |

## 8. 独立审查

| Reviewer route | Scope | Verdict |
|---|---|---|
| `runtime_audit` | StateRoot、Key、paged storage、Group guard、callback、resource、failed-state | `PASS`；P0/P1全部关闭 |
| `compiler_audit` | model、naming/symbol preflight、generation atomicity、FQN shadow、diagnostics | `PASS`；P1/P2全部关闭 |
| `product_evidence_audit` | I1 Exit、positive/negative、javap、regeneration、bytecode与claim boundary | `PASS`；无剩余阻塞项 |

审查发现均在最终canonical qualification前关闭；没有用反复审查替代新evidence。

## 9. Gate disposition与保留边界

| Gate | I1 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | 完整I0 regression仍通过；production topology未变化 |
| G2 | `I1_SCOPE_PASS / IN_PROGRESS` | 第一套公开generated long-keyed surface成立；全部type/Index/naming breadth属于I2 |
| G3 | `I1_SCOPE_PASS / IN_PROGRESS` | paged PLAIN long storage、primitive Key、atomic StateRoot成立；全部storage breadth属于I2 |
| G4 | `I1_SCOPE_PASS / IN_PROGRESS` | typed filter/count与one-shot lifecycle成立；完整IR/reference/optimizer/materialization属于I3 |
| G5 | `I1_SCOPE_PASS / IN_PROGRESS` | point update、scope、structured failure与bounded peak机制成立；Selection mutation与exhaustive fault/resource属于I4 |
| G6-G9 | `NOT_RUN` | 对应production surface尚未进入active slice |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | I1未改变dependency/artifact topology；CI/package/release qualification属于I8 |

两项信任/极值边界不构成I1 blocker：generated PRIVATE `MethodHandles.Lookup`证明lexical owner，
不是Java 8 security sandbox；故意forge/修改generated artifact或依赖`.internal`属于unsupported
boundary。`stateVersion == Long.MAX_VALUE`时changed/no-op precedence与完整Group GC accounting、OOME
cleanup、exhaustive fault matrix由I4统一关闭，I1不作过度声明。

I1关闭后没有active slice；下一项只允许从I2 schema/type/storage breadth开始。
