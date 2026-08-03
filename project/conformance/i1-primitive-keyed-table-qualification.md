# I1 Primitive Keyed Table Qualification

类型：Conformance Evidence

状态：`PASS (I1_SCOPE)`

被测 implementation commit：`424f0c32649acc9b81b346e7d5e34fbdbb34d0eb`

独立审查：`APPROVE`（只读复审，覆盖 I1 exit blockers）

日期：2026-08-03

## 1. 结论

I1 已完成一个真实的、受限的 primitive keyed Table vertical slice。它不是完整 SOMA V1
能力，也不构成百万行性能、兼容性、package 或 release claim。

本 slice 证明：

```text
one @SomaSchema composition
    -> generated Soma / SomaGroup / Table / detached object
        -> two-level paged primitive long storage
            -> long Key lookup and reserve/add/find/get
                -> typed expression and callback filter/count
                    -> point update with structured result/failure
```

正式 Blueprint/Design 未修改。由于正式 generated signature 中 `@SomaField` annotation 与
logical Field marker 使用了同一 FQN，I1 采用 `SomaFieldEndpoint` 作为 generated marker；该局部
实施裁决和其边界记录在 [I1 临时记录](../temp/i1-implementation-decisions.md)，不宣称已改写
正式 Design Owner。

## 2. 资格环境与重放入口

| 项目 | 值 |
|---|---|
| JDK | OpenJDK `1.8.0_502` |
| Maven | `3.9.16` |
| OS/architecture | Darwin arm64 |
| CPU | Apple M5 Pro |
| memory | 48 GiB |
| production artifacts | `soma-runtime` + `soma-processor` |
| independent consumer | `tests/i1/consumer`，不在 Maven reactor |
| qualification script | `./scripts/check-i1.sh` |

脚本先安装两个当前 artifact，再用独立 Java 8 consumer 编译、运行和检查 generated source；
它使用 `-Asoma.fullSourceSet=true`、Java 8 source/target、processorpath/classpath 分离，并在
临时目录中执行 rename、clean regeneration 与 schema 退出 I1 eligibility 的重放。

## 3. 实现范围

### Generated surface

- lazy default Group 与显式 Group；同一 Group 内 Table identity 稳定；
- generated detached `WorkItem`，canonical/no-arg constructor 与 accessor；
- generated `WorkItemTable`、`View`、`Editor extends View`、Selection；
- private nested support constructors 与 composition capability token；
- `long` Key、`long` payload、typed Field endpoint、`SomaExpression` 与 callback
  `SomaPredicate` 两个 distinct overload。

### Storage/public operations

- long zero Key 使用 occupancy 位，不把零当 sentinel；
- two-level paged Chunk directory，Chunk leaf 为 primitive `long[]`，不使用 row boxing；
- sharded open-addressed long-key index，locator 为 long；
- `reserve/add/find/get/size/capacity`；
- typed/callback `filter(...).count()`；
- point update、missing/no-op/duplicate/missing-key structured outcome；
- candidate-root 与 journaled/prevalidated in-place 两条 point publication path；
- StateRoot version 跃迁与 guard release；
- View/Editor callback scope、owner thread、escape 与 JVM `Error` cleanup；
- typed expression owner provenance、foreign expression pre-claim rejection；
- intermediate immediate claim、predecessor reuse/branch rejection。

## 4. 证据结果

`./scripts/check-i1.sh` 最终输出：

```text
I1 primitive keyed Table qualification: PASS
```

脚本声明并实际执行的主要证据包括：

- Java 8 generated surface、`javap` signature 与 private construction negative；
- default/explicit Group identity 与隔离；
- zero Key、tiny Chunk boundary、reserve/add/find/get、detached result；
- typed expression 与 callback filter；
- changed/no-op/missing update、duplicate/missing Key；
- failed callback、JVM `Error` 原样传播后 guard 可继续使用；
- callback escape 与跨线程 View/Editor structured scope failure；
- candidate-root version `1 -> 2` 与 prevalidated in-place version `2 -> 3`；
- foreign expression 在 empty/non-empty consumer 前的 provenance failure，且 receiver 未被消费；
- intermediate claim 后 predecessor terminal/second branch failure；
- generated symbol collision negative (`SOMA-0303`)；
- generated file-set manifest count/hash、两次 clean regeneration deterministic；
- Table rename stale cleanup；schema 退出 I1 eligibility 后 generated API/file-set cleanup；
- generated hot path 静态检查无 reflection/boxing pattern。

同一工作树上重新执行 `./scripts/check-i0.sh` 结果为 `I0 processor harness: PASS`；
`mvn -B -q test` 与 `git diff --check` 通过。

## 5. Gate 定位

| Gate | 当前状态 | 说明 |
|---|---|---|
| G1 | `PASS` | I0 baseline 仍通过；I1 independent consumer 也通过 |
| G2 | `IN_PROGRESS — I1_SCOPE_PASS` | 仅 I1 generated surface，非完整 schema/type matrix |
| G3 | `IN_PROGRESS — I1_SCOPE_PASS` | 仅 primitive long storage/Key，非完整 Index/type breadth |
| G4 | `IN_PROGRESS — I1_SCOPE_PASS` | 仅 typed expression evaluator 与 filter/count，非 planner/oracle |
| G5 | `IN_PROGRESS — I1_SCOPE_PASS` | 仅 point mutation/failure，非 selection mutation/resource admission |
| G6-G9 | `NOT_RUN` | Join/GroupBy/parallel/compression/metadata/scenario 尚未实现 |
| G10 | `IN_PROGRESS — I0_BASELINE_PASS` | I1 不改变 package/release boundary |

## 6. 明确未关闭的范围

I1 不声称：

- String、Enum、其他 primitive、`@SomaValue` flattening、reference payload；
- secondary Index selection、remove、selection mutation；
- 完整 Stream grammar、map/materialization、reference interpreter、optimizer；
- Join、GroupBy、parallel executor、compression、metadata、managed memory admission；
- 百万行 performance qualification、一亿行愿景、真实案例、package、签名或 release。

下一 active slice 是 I2；在 I2 exit evidence、独立审查、Conformance 更新和干净提交前，不进入
I3。I1 的临时记录在本 evidence 晋升后仅作为 provenance 保留，不是新的长期 Design Owner。
