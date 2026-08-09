# SOMA Java I7 Compression 与 Metadata Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I7 COMPLETED`

Gate disposition：`G1-G8 PASS`；`G9 NOT_RUN`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`

正式事实源：是（I7 compression、metadata/explain与generated surface current executable fact）

Owner：SOMA Java I7 implementation 与 qualification evidence

资格日期：2026-08-09

## 1. 结论

I7 在既有paged Table、atomic `StateRoot`、typed query、Index、Join与parallel路径上建立了
representation-independent Chunk seam：

```text
TableChunk
    -> PLAIN active tail
    -> immutable ENCODED complete Chunk
    -> ENCODED + bounded sparse overlay
```

`SomaCompression.AUTO`为默认策略，`OFF`保持PLAIN。AUTO只在同步operation boundary处理本次刚
sealed或受mutation影响的Chunk；boolean采用bit packing，收益成立的integral采用RLE，String/Enum
采用dictionary，ordinary Object reference保持PLAIN。更新后的payload、Key、Index、compression
representation与managed accounting由同一个candidate `StateRoot`一次发布。

同时，generated `Soma`、`SomaGroup`、Table与每个logical Field endpoint都提供detached immutable
`_metadata()`；`_explain()`在既有logical/physical plan信息之外显示effective compression、encoded
Chunk数量与representation/plain-equivalent bytes。metadata不暴露codec、physical leaf、Chunk、
array、Executor或mutable runtime handle，且freeze前读取`Soma._metadata()`不会触发配置冻结。

## 2. 实施边界

- storage访问统一经过`TableChunk`，Key/Index/query/Join不依赖Java array identity；
- complete Chunk按leaf选择精确表示，active tail保持PLAIN；
- encoded mutation进入bounded row overlay，超过internal density threshold同步materialize并重新评估；
- add/update/remove只完成刚sealed或affected Chunk，不运行background compressor或全Table重压缩；
- AUTO只在representation estimate小于PLAIN时采用encoded candidate；
- public配置只提供`AUTO/OFF`，不暴露codec、threshold或storage backend；
- off-heap、mmap、Loader、background compression和public codec selection仍不属于V1。

## 3. Canonical qualification

Implementation commit：`609a1c0`（`feat: complete I7 compression and metadata`）

正式入口：

```sh
./scripts/qualify-i7.sh
```

最终结果：

```text
Java/Javac:                    Amazon Corretto 1.8.0_502, class major 52
soma-runtime tests:            55 run, 0 failures/errors/skips
soma-processor tests:          34 run, 0 failures/errors/skips
Java 8 I5 breadth consumer:    PASS
Java 8 I6 custom/common pool:  PASS
Java 8 I7 consumer journey:    PASS
forward/reverse generation:    identical
four-level metadata javap:     PASS
artifact/internal leakage:     PASS
i7-qualification:              PASS
git diff --check:              PASS
```

I7 consumer在独立JVM中证明：freeze前metadata无副作用；显式AUTO配置、default/explicit Group身份、
32,768行完整Chunk压缩、Table/Field summary、Index/Field查询、explain、encoded sparse update与remove
后的tail materialization均按Java 8 public API工作。runtime targeted evidence另证明`OFF`保持PLAIN，
以及AUTO路径下Key、Index、point update、indexed update与remove语义不变。

112-Table generated surface profile为22,724,885 bytes generated source、2,581 class files、
32,000,486 class bytes、最大methods 290、constant-pool entries 1,760。该数据只证明I7 generated
surface仍处于Java 8结构边界内，不是G9性能claim。

## 4. Gate disposition 与下一项

| Gate | I7 disposition | 边界 |
|---|---|---|
| G1-G2 | `PASS` | clean Java 8 reactor、full regeneration、最终generated surface与artifact成立 |
| G3 | `PASS` | PLAIN/encoded/overlay、Key/Index与accounting同一StateRoot发布 |
| G4-G7 | `PASS` | query、mutation、Group/Join、parallel在encoded representation上回归通过 |
| G8 | `PASS` | AUTO/OFF、normal codec路径、overlay、四级metadata与explain成立 |
| G9 | `NOT_RUN` | three scenarios、benchmark、profile与approved threshold属于I8 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | 无新dependency/artifact；完整package、workflow与release qualification属于I8 |

I7关闭后没有active slice；下一项只允许从I8 product qualification开始。I7不声明百万行性能、
compression ratio、稳定codec选择、release readiness或remote artifact publication。
