# SOMA Java I5 GroupBy 与 Relation Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I5 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2 I5_SCOPE_PASS / IN_PROGRESS`；
`G3 I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS`；`G4-G6 PASS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G7-G9 NOT_RUN`

正式事实源：是（I5 GroupBy、binary Equality/Cross Join 与 G6 current executable fact）

Owner：SOMA Java I5 implementation 与 qualification evidence

资格日期：2026-08-09

## 1. 结论

I5 在I3 authoritative logical plan/reference oracle与I4 Group operation guard之上建立了第一套
单进程关系计算闭环：

```text
Table / Selection
    -> GroupBy(key)
        -> one typed aggregate
            -> detached typed result

Table + Table in one SomaGroup
    -> typed on(...).and(...)
        -> Inner / Left / Full / Semi / Anti
            -> right Index lookup or right Hash
                -> canonical ordered result
```

显式`crossJoin(other,maxOutputRows)`也已成立。I5没有引入self/multi-way/Right/range/as-of Join，
没有引入parallel、compression、benchmark、Example、CI/package或release surface；这些仍属于I6-I8。

## 2. 实施结构

### 2.1 GroupBy

- `GeneratedGrouping`拥有Group logical terminal与production hash grouping；key首次遇见顺序是结果顺序；
- primitive key使用unboxed result/consumer family，reference key使用typed generic result；所有结果均detached；
- count、sum、min、max、average与summaryStatistics复用同一aggregate Owner；integer sum使用exact
  signed-128 accumulation，floating sum复用固定1024-element deterministic tree；
- `scripts/generate-grouped-api.py`是shared Grouped result grammar的唯一机械Owner，qualification先执行
  `--check`，不手工维护平行API家族；
- correctness oracle使用同一个bound logical plan，但以reference row traversal与线性group lookup执行，
  不作为production fallback。

### 2.2 Relation

- generated Table只生成与其他Table的typed `join/crossJoin`入口；Pair/Condition/Matched/Outer的实现由
  shared generic carrier拥有，避免每个Table pair复制整套执行代码；
- `GeneratedRelation`拥有binary relation plan、terminal-start双root binding、kind/filter与production
  execution；同一Group operation guard覆盖两个Table；
- 单一right Key/Index条件优先使用已发布Index，其他条件使用order-preserving right Hash；二者都按
  left order、同一left的right order发布，Full最后发布unmatched right；
- Inner Join只下推callback barrier之前的pure typed predicate；callback和未证明等价的Outer rewrite
  保留为residual；`_explain()`展示Index/Hash与pushdown数量；
- independent nested-loop reference oracle完整执行原始logical filters，永不被optimizer当作失败fallback；
- Semi/Anti通过`RELATION_LEFT` source重新进入既有left `ReadStream`，没有建立第二套row query engine；
- Pair是borrowed callback-scoped view；matched `select`先形成detached typed tuple，Outer保持显式
  `hasLeft/hasRight` missing语义。

## 3. Canonical qualification

Implementation commit：`6ac3256`（`feat: complete I5 group and relation execution`）

正式入口：

```sh
./scripts/qualify-i5.sh
```

最终结果：

```text
Java/Javac:                 Amazon Corretto 1.8.0_502, class major 52
soma-runtime tests:         51 run, 0 failures/errors/skips
soma-processor tests:       32 run, 0 failures/errors/skips
grouped API generator:      deterministic --check PASS
Java 8 I5 consumer:         PASS
forward/reverse generation: identical
Join type-state negatives:  self/Pair materialize/Outer select rejected
generated javap boundary:   PASS, no public internal type
artifact topology/leakage:  runtime+processor only, no JUnit leakage
i5-qualification:           PASS
git diff --check:           PASS
```

Java 8 consumer覆盖正常产品路径：primitive/reference/null Group key；count/sum/min/max/average/summary；
single/compound Equality condition；Inner/Left/Full/Semi/Anti；duplicate Cartesian与canonical order；
Outer missing；Semi/Anti left ReadStream；typed filter/pushdown/Index explain；matched select；null-never-match；
以及受`maxOutputRows`约束的Cross Join。

112-Table generated surface profile在本机约为21.7 MB generated source、2,469 class files、29.8 MB
class bytes、约5.1秒，最大methods 288、constant-pool entries 1,748。它证明shared carrier消除了早期
per-pair wrapper造成的二次膨胀；该数据不是G9或对外性能claim。

## 4. Reference differential 与独立审查

runtime差分使用同一immutable bound state、两个独立算法：

- Group：hash grouping对照linear reference grouping；
- Relation：right Index lookup对照nested-loop reference；
- Inner typed right predicate同时验证pushdown/residual与完整locator order。

唯一一个bounded只读reviewer只检查正常路径与架构不变量。首次审查发现reference Inner path错误跳过
已标记为pushdown的typed filter；主实现将reference发射改为完整执行logical filters，并增加
Inner + typed predicate + Index lookup差分。reviewer复核为`PASS`。没有开启第二轮广域审查，也没有把
低概率误用或机械覆盖率作为I5关闭条件。

## 5. Gate disposition 与下一项

| Gate | I5 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | clean Java 8 reactor、两个artifact、full regeneration回归通过 |
| G2 | `I5_SCOPE_PASS / IN_PROGRESS` | Group/Relation generated surface成立；I6-I7 surface待实现 |
| G3 | `I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS` | I5复用StateRoot/Group accounting；compression属于I7 |
| G4 | `PASS` | Relation/Group reference与optimized differential闭合 |
| G5 | `PASS` | I5未改变mutation/failure Owner，I0-I4 regression通过 |
| G6 | `PASS` | GroupBy、Equality/Cross Join与typed result正式成立 |
| G7-G9 | `NOT_RUN` | 对应production surface尚未建立 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | 无新dependency/artifact；完整package/release属于I8 |

I5关闭后没有active slice；下一项只允许从I6 bounded parallel execution开始。
