# SOMA Java I6 Bounded Parallel Execution Qualification

类型：Conformance / Implementation Slice Qualification

状态：`PASS`

Slice：`I6 COMPLETED`

Gate disposition：`G1 PASS`（regression）；`G2 I6_SCOPE_PASS / IN_PROGRESS`；
`G3 I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS`；`G4-G7 PASS`；
`G10 I0_SCOPE_PASS / IN_PROGRESS`；`G8-G9 NOT_RUN`

正式事实源：是（I6 bounded parallel execution 与 G7 current executable fact）

Owner：SOMA Java I6 implementation 与 qualification evidence

资格日期：2026-08-09

## 1. 结论

I6 在I3-I5已经成立的typed logical plan、optimized sequential execution、Group guard与resource
admission之上建立了显式、同步、有界的parallel execution：

```text
reusable source
    -> parallel() mode marker
        -> terminal-start StateRoot binding and admission
            -> caller + at most P-1 ForkJoin drainers
                -> canonical ordinal merge
                    -> unchanged sequential logical result
```

Application可以在one-time `SomaConfiguration`中提供自己的`ForkJoinPool`；未配置时使用
`ForkJoinPool.commonPool()`。SOMA不为operation创建或关闭pool，不接受generic Executor adapter，
也不提供hidden sequential fallback。

I6没有引入compression、正式metadata carrier、benchmark、Example、CI/release workflow或remote
package；这些仍属于I7-I8。

## 2. 实施结构

- `parallel()`是immutable plan mode，不建立第二套query API或第二套logical semantics；Table、Field、
  IndexSelection、Mapped/primitive Stream与Relation carrier保持generated covariant type-state；
- `ParallelRowScheduler`只调度canonical contiguous locator ranges；caller直接参与，最多提交`P-1`
  个长生命周期drainer，不创建per-record task；
- operation-local range queue与start gate防止partial submission执行数据工作；drainer以
  `PENDING/RUNNING/DONE/CANCELLED`生命周期保证terminal返回前quiescent；
- range结果按ordinal合并后进入既有optimized executor，因此encounter order、numeric tree、mutation
  publication与structured failure合同不改变；
- worker只执行可证明安全的typed predicate prefix；opaque callback、Comparator、materialization与
  generated borrowed View仍在calling thread按canonical顺序运行；
- parallel source buffer在数据工作前进入既有Group temporary-memory admission；shutdown/rejection、
  interrupt与nested parallel terminal使用稳定structured failure，且不切换到其他执行结果。

## 3. Canonical qualification

Implementation commit：`2d53336`（`feat: complete I6 bounded parallel execution`）

正式入口：

```sh
./scripts/qualify-i6.sh
```

最终结果：

```text
Java/Javac:                 Amazon Corretto 1.8.0_502, class major 52
soma-runtime tests:         53 run, 0 failures/errors/skips
soma-processor tests:       33 run, 0 failures/errors/skips
Java 8 I5 breadth consumer: PASS
Java 8 custom-pool consumer: PASS
Java 8 common-pool consumer: PASS
forward/reverse generation: identical
generated parallel surface: PASS
artifact/internal leakage:  PASS
i6-qualification:           PASS
git diff --check:           PASS
```

Java 8 consumer覆盖正常产品路径：application-owned pool与common-pool fallback、Table/Selection/
ReadStream/Field/Index/Map/primitive/Join的`parallel()`类型形态、sequential/parallel count与array顺序
等价、calling-thread `forEachOrdered`、parallel Selection atomic update、explain mode与shutdown
structured failure。

runtime forced multi-Chunk fixture证明custom pool确实接收bounded drainer，同时callback仍由caller
执行。112-Table generated surface profile为22,641,675 bytes generated source、2,581 class files、
31,877,089 class bytes、最大methods 289、constant-pool entries 1,755；该数据只证明I6 generated
surface没有突破Java 8边界，不是G9或对外性能claim。

## 4. 架构核对边界

本轮按Product Owner的效率修正采用单一主路径审核，不再以多轮sub-agent穷举异常误用。最终核对只
覆盖会改变产品架构或正常执行语义的合同：

- pool ownership与no per-operation pool；
- caller participation、`P-1`提交上限与terminal quiescence；
- StateRoot binding、resource admission与Group guard不被绕过；
- canonical merge与sequential/parallel逻辑结果一致；
- application callback不被worker隐式speculate；
- unavailable pool使用structured failure且不fallback。

上述核对与canonical qualification均为`PASS`。I6不据此声称每个stage必然并行，也不建立吞吐或
规模性能结论。

## 5. Gate disposition 与下一项

| Gate | I6 disposition | 边界 |
|---|---|---|
| G1 | `PASS` | clean Java 8 reactor、两个artifact、full regeneration回归通过 |
| G2 | `I6_SCOPE_PASS / IN_PROGRESS` | generated parallel surface与Java 8 consumer成立；I7 metadata surface待实现 |
| G3 | `I4_ACCOUNTING_SCOPE_PASS / IN_PROGRESS` | parallel scratch进入accounting；compression属于I7 |
| G4-G6 | `PASS` | I6复用既有IR、mutation、Group/Join语义并通过回归 |
| G7 | `PASS` | explicit bounded scheduling、pool ownership、order与failure边界成立 |
| G8-G9 | `NOT_RUN` | 对应production surface尚未建立 |
| G10 | `I0_SCOPE_PASS / IN_PROGRESS` | 无新dependency/artifact；完整package/release属于I8 |

I6关闭后没有active slice；下一项只允许从I7 compression、metadata/explain与surface closure开始。
