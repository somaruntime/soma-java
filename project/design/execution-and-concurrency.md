# 执行、并发与并行 Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 pipeline lifecycle、currentness、Table admission、atomic publish、sequential/parallel execution 与 determinism

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 设计目标

本 Design 定义一条逻辑 operation 怎样绑定 Table state、取得资源、执行 callback、
产生结果或原子发布 mutation。它承接 BP-5、BP-6、BP-8 和 BP-9。

用户只看见 `stream()/parallelStream()` 与 synchronous terminal，不直接操作 runtime、
task、lock、lease、version、staging 或 publish protocol。

## 2. Pipeline model

```text
Source
    -> zero or more intermediate operations
        -> one terminal
            -> detached result or Table-local controlled mutation
```

Pipeline 是 lazy、finite、single-source、one-shot：

- 创建时保存 Table identity 和 operation definition，不绑定 payload state；
- 第一个 terminal 成功进入 invocation 后消费 pipeline；
- 第二个 terminal 产生 `STREAM_ALREADY_CONSUMED`；
- terminal 完成后不保留 iterator、live cursor、worker 或 Table lease；
- V1 不提供 async/Future、Publisher、infinite source 或 Pipeline mode switching。

## 3. Terminal-start late binding

Terminal admission 时才绑定 current Table state。因此 Pipeline 创建后、terminal 前
成功发布的 mutation 对该 terminal 可见：

```java
TransportTimeTable.Stream pipeline =
    table.stream().filter(predicate);

table.add(new TransportTime(laterPair, 18L));

long count = pipeline.count();
```

Terminal 运行期间观察固定 SOMA-owned logical state：payload/reference slots、Key、
Index、size、capacity 和 canonical order 不被其他 application operation 改变。实现
不必复制整张 Table；固定性由 Table-local admission 保证。

Ordinary Object referent 的内部 state 不属于该 snapshot，仍由 application 同步。

## 4. Internal state version

V1 内部可以使用 unified `stateVersion` 支撑 currentness：

- successful add、effective update/remove 和 actual capacity growth 各递增一次；
- missing remove、logical no-op update/reserve 和 failed operation 不递增；
- version 不进入普通 API 或 V1 metadata；
- implementation 可以内部拆分 content/access/layout version，但不得改变用户语义。

不存在 public stale/currentness token 或 `CURRENTNESS_FAILURE`。Late binding、admission
和 no-live-handle contract 已消除用户管理 stale iterator 的需要。

## 5. Canonical encounter order

每个 terminal-start state 有一个 canonical Record order：

- whole Table 使用完整 order；
- Index selection 是命中 Record 的 ordered subsequence；
- Field-first 与 Record-first projection 继承来源 order；
- `filter/select/map` 保持 order；
- `skip/limit/findFirst` 消费 order；
- stable `sorted` 对 comparator-equal element 保持 upstream order；
- `distinct` 保留 first encounter value；
- structural remove 可以确定性重排 survivors，但顺序/并行路径必须相同。

Logical order 与 worker scheduling/completion order 无关。Physical array position、hash
bucket 或 work partition 不能成为 public order。

## 6. Table-local admission

用户不管理 lock/lease。每个 Table 使用 fail-fast shared-read/exclusive-write admission：

| Class | Operation | Admission |
|---|---|---|
| Read | `find/get`、Query terminal、`size/capacity`、runtime metadata snapshot | shared |
| Write | `add/reserve`、point update/remove、selection Update/Remove | exclusive |

多个 Read 可以并发；Write 与任何 active Read/Write 冲突。Conflict 立即以
`CONCURRENT_TABLE_OPERATION` fail closed：

- 不阻塞；
- 不隐藏等待；
- 不自动重试；
- 不发布 partial progress。

Pipeline construction 不 admission；terminal 才 admission。一次 parallel terminal
的全部 workers 共用一次 operation admission，不能每个 worker 重新竞争。

不同 Table 独立 admission；不存在 Group lock、multi-Table snapshot 或 cross-Table
transaction。

## 7. Reentrancy

Callback 只能使用当前 Record/Editor/Value View，不得重入来源 Table 的 direct
operation 或启动另一 terminal：

```java
table.stream().forEach(record -> {
    table.add(...); // REENTRANT_TABLE_OPERATION
});
```

当前 terminal 自己控制的 Editor/Field update 合法。Callback 可以访问另一张 Table
的 direct operation 或 sequential terminal，但只取得目标 Table 自身 admission，
可能独立失败，不形成 cross-Table atomicity。

任何 SOMA callback 内启动 parallel terminal 都禁止，即使目标是另一张 Table；违反
时为 `NESTED_PARALLEL_OPERATION`。这避免 shared pool starvation、resource
amplification 和 nested failure arbitration。

## 8. Table-local mutation protocol

Selection Update/Remove 是 whole-selection all-or-nothing：

```text
success -> final selection becomes visible once
failure -> zero records published
```

内部 protocol：

```text
one exclusive Write admission and terminal-start binding
    -> invocation/state/resource preflight
        -> freeze final selection
            -> candidate/worker-local staging
                -> deterministic merge
                    -> schema/Key/Index validation
                        -> one atomic publish
```

Worker/callback 在 publish 前不得写 authoritative payload、Key 或 Index。Pool
rejection、callback、validation、resource 或 worker failure 丢弃 staging。Publish
开始前必须完成所有可恢复 validation，不能产生 partial structured outcome。

Point add/update/remove 遵守相同 Table-local publication boundary；多个 repeated add
仍是多个独立 operations。

## 9. Logical no-op

Update 在 terminal-start logical equality 下计算 `changed`：

- callback 修改后恢复原值是 no-op；
- all controlled slots unchanged 时不 publish、不递增 stateVersion；
- opaque Object slot 使用 reference identity；referent internal mutation 不计入；
- Key 不参与 normal update；rekey 使用 remove + add。

## 10. Sequential execution

`stream()` 严格在调用线程执行 callback，不使用 SOMA parallel pool。Sequential
callback 按 canonical encounter order 调用；`forEach` side effect 因此有确定顺序。

Sequential implementation 仍必须 checked resource/arithmetic、one-shot、admission、
staging、failure mapping 和 worker-free completion；不能把“单线程”解释为弱化
correctness contract。

## 11. Parallel configuration

所有 Table、IndexSelection 和 Field 提供显式 `parallelStream()`。并行 resource 是
ClassLoader-scoped、library-wide internal Owner，不属于 composition/Group/Table/
Pipeline，也不形成 public `SomaExecutionRuntime`。

V1 只接受 `ForkJoinPool`：

```java
ForkJoinPool somaPool = new ForkJoinPool(8);
Soma.setParallelExecutor(somaPool);
```

所有 generated composition 的 `Soma` 入口代理到同一个 ClassLoader-wide setting。
所有 parallel terminal 共享 effective pool；SOMA 不为每个 Stream 创建/销毁 pool。

Pool state machine：

```text
UNINITIALIZED
    -> setParallelExecutor(custom) -> CUSTOM_FIXED
    -> first parallel terminal     -> COMMON_POOL_FIXED
```

- custom pool 必须在第一次 parallel terminal 前设置；
- 未设置时第一次 parallel terminal 固定 `ForkJoinPool.commonPool()`；
- fixed 后重复设置同一 instance 是 idempotent no-op；
- fixed 后设置不同 instance、`null` 或 reset 为
  `PARALLEL_CONFIGURATION_CONFLICT`；
- V1 不支持 runtime replacement；
- application-owned pool 由 application shutdown；SOMA 不关闭；
- common pool 由 JVM 管理。

未来 replacement 若有真实需求，必须是独立 quiescent maintenance protocol，不能把
普通 setter 变成 hot swap。

## 12. Bounded participation

Effective pool parallelism 记为 `P`：

- 一次 terminal active SOMA callback 不超过 `P`；
- 调用线程参与时计入 P，不能形成 `P + 1`；
- `P == 1` 合法；
- 小 selection 或成本模型判断不值得时可以只用一个 participant；
- `parallelStream()` 表达“最多 P”，不承诺多线程或加速；
- task fan-out 相对 P 有界，不为每个 Record 创建 task；
- 不建立无界 per-terminal queue；
- SOMA 不直接创建 worker thread，lifecycle 由 effective ForkJoinPool 管理。

Custom pool 用于与 application 其他 CPU work 隔离。Common pool 下 SOMA 只能约束
自身 callbacks，不能控制 pool 中其他 application task。

## 13. Synchronous terminal and quiescence

所有 terminal 同步完成：

1. admission/state binding；
2. resource preflight；
3. execute/cancel/merge；
4. optional atomic publish；
5. wait for all workers quiescent；
6. return result or throw primary failure。

Terminal 返回或失败后不得仍有 callback 在后台运行。V1 不返回 Future，也不把
quiescence 交给用户管理。

## 14. Callback contract

Parallel callback 可以并发且 thread identity、invocation/completion order 无语义。
Predicate、mapper、comparator、updater 和 consumer 必须：

- thread-safe；
- non-interfering；
- 若要确定结果，对相同 input deterministic；
- 不依赖调用次数、thread identity 或 wall-clock completion order；
- 不让 callback-scoped Record/Editor/View 逃逸。

SOMA atomicity 不包含 callback 对日志、network、file 或 ordinary referent 的 external
side effect。Parallel update callback 应只通过 Editor/Field updater 表达 Table
change。

## 15. Sequential/parallel equivalence

对相同 terminal-start state 和 deterministic callback，顺序与并行必须产生相同：

- logical Query result；
- canonical order/materialization；
- Table mutation/survivor order；
- checked arithmetic result/overflow；
- 非资源型 primary failure；
- no-partial-publication guarantee。

Reduction 使用与 worker completion 无关的 canonical merge plan。Parallel short
circuit 可以 speculative evaluation，但 canonical encounter order 中的 decisive
frontier 决定 result/failure；frontier 之后 speculative failure 不能覆盖顺序语义。

Parallel `forEach` external side-effect order 不保证。需要 ordered side effect 时使用
sequential `stream().forEach`；V1 不提供 `forEachOrdered`。

## 16. Parallel failure arbitration

Execution 中多个 worker 失败时不选择 wall-clock first：

- Record/Field work 选择 canonical encounter position 最早的有效 failure；
- sort/merge 等无单一 element position 的 phase 使用固定 phase/work-unit order；
- remaining work best-effort cancel；
- terminal 等全部 worker quiescent 后抛一个 primary failure；
- 不附加时序不稳定的 worker suppressed failure。

Cross-category phase precedence 与 public mapping 由
[结果与失败 Design](results-and-failures.md)拥有。

## 17. Pool unavailable

Custom pool fixed 后若 shutdown、terminating、reject 或不能完成 admission，产生
`PARALLEL_EXECUTOR_UNAVAILABLE`：

- 不 fallback common pool；
- 不静默 sequential；
- 不自动 retry；
- mutation zero publication。

Common pool fallback 只发生在第一次 `UNINITIALIZED -> COMMON_POOL_FIXED`。

## 18. Resource and arithmetic boundary

执行层对以下值使用 checked arithmetic：

- selection/materialization cardinality；
- primitive integer aggregate；
- array length、capacity growth、byte size；
- scratch/task/work-unit sizing；
- canonical merge offsets。

SOMA-owned integer overflow 为 `ARITHMETIC_OVERFLOW`；已知 representation/resource
bound 不可满足为 `RESOURCE_LIMIT_EXCEEDED`。用户 callback 中的 `Math.addExact`
属于 callback exception，由 Failure Design 映射。Application 在调用前使用 Java `+`
产生的 silent wrap 已丢失事实，SOMA 不猜测。

JVM `OutOfMemoryError` 等 `Error` 不包装成普通 structured failure，但若发生在
publish 前仍不得留下 partial Table state。

## 19. Metadata observation

`Soma._metadata()` 可以只读观察 parallel backend/config state；Table metadata 可以
作为 shared Read 获取 self-consistent size/capacity snapshot。Metadata 不能返回 raw
pool、lock、version、task、staging 或 planner，也不能触发 lazy initialization/
Index rebuild。

## 20. 明确排除

- public Execution Runtime；
- arbitrary Executor/ExecutorService；
- per-Group/Table/Stream pool；
- per-pipeline pool create/destroy；
- `parallelStream(int/executor)`；
- Pipeline `.parallel()`/`.sequential()`；
- hidden parallel execution from `stream()`；
- async terminal/Future；
- unbounded tasks or per-Record task；
- blocking contention、automatic retry 或 sequential fallback；
- callback nested parallel；
- cross-Table transaction/snapshot；
- public lock/lease/stateVersion；
- external side-effect rollback guarantee。

## 21. Implementation admission Gates

Production runtime 必须用 deterministic、blocking/concurrent 和 fault-injection tests
证明：

- terminal late binding、one-shot 与 worker quiescence；
- Read/Read、Read/Write、Write/Write fail-fast admission；
- source Table reentrancy 与 cross-Table boundary；
- selection Update/Remove whole-selection atomicity；
- canonical order、stable sort/distinct/remove；
- P==1、小/大 selection、bounded participation/task fan-out；
- custom/common pool freeze、idempotence、conflict、shutdown/rejection；
- sequential/parallel result/mutation/order/failure equivalence；
- short-circuit decisive frontier 与 deterministic worker arbitration；
- callback/Error/resource/overflow zero publication；
- ordinary referent/external side-effect boundary。
