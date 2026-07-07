# Runtime correctness model

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文定义 `soma_java` V1 runtime correctness model。它把项目级架构中的 `TableStore` 组合模型落成可审查、可测试、可进入 gate evidence 的不变量、状态机、错误语义和 oracle 口径。

本文不替代 owner 契约：

- 项目级架构以 `docs/architecture-design.md` 为准；
- runtime component contract 以 `soma-runtime-core/docs/runtime-core-contract.md` 为准；
- generated API / Row Pipeline 以 `docs/row-pipeline-api-contract.md` 为准；
- processor/codegen golden 以 `soma-processor/docs/processor-codegen-contract.md` 为准；
- gate evidence 以 `docs/validation-gates.md` 为准。

本文只定义 correctness 要求，不定义性能 claim。性能与 benchmark 口径见 `docs/runtime-performance-model.md`。

## 2. Correctness boundary

V1 correctness 的基本单位是一张 generated table instance：

```text
XxxTable
  -> XxxTableStore
       -> TableLayout
       -> RowSpace
            -> KeySpace        // keyed table only
       -> ColumnStore
       -> AccessStructures
       -> AccessPath
       -> MutationCoordinator
       -> LifecycleState
```

单张 table 必须维护自己的 storage、identity、sidecar、lifecycle 和 typed error 语义。V1 不提供跨 table transaction。FJSP 中 `Operation` assignment、`Machine` availability 与 `MachineCandidate` frontier 删除/刷新等连续 mutation 属于 solver/application loop 的一致性责任，不是 SOMA runtime atomicity 承诺。

换言之，SOMA runtime 保证每一次单表 mutation 完成后该 table 内部不变量成立；跨 `OperationStateTable`、`MachineStateTable`、`MachineCandidateTable`、`JobStateTable`、`MaterialStateTable` 的 commit 顺序、失败处理、补偿策略和可观测 artifact 必须由 solver loop 明确拥有。

## 3. Core invariants

### 3.1 RowSpace invariants

`RowSpace` 拥有 row membership 和当前有效 `RowSlot`。

不变量：

- 每个 live row 有且只有一个 valid `RowSlot`；
- `RowSlot` 是当前 packed storage 中的位置，不是 stable business identity；
- dense table public row index 映射到当前 `RowSlot`，structural mutation 后旧 row index 可能失效；
- delete、swap-remove、row move、clear、replaceAll 后，所有依赖 slot 的组件必须同步维护或标记 dirty；
- `RowSpace` 不持有 field payload，不拥有 key/index/order policy。

### 3.2 KeySpace invariants

`KeySpace` 只存在于 keyed table，维护 primary identity：

```text
RowKey leaf values -> KeySpace -> RowSlot
```

不变量：

- 每个 live key 精确映射到一个 valid `RowSlot`；
- duplicate key 必须在 public mutation 成功前被拒绝；
- missing key 必须返回可区分 typed error 或 empty result，取决于 API；
- delete、row move、replaceAll、clear 后 `KeySpace` 不得指向 deleted/stale slot；
- primary key lookup 不作为普通 secondary index sidecar 处理；
- key field 不生成 mutable setter，identity change 必须通过 delete + insert 表达。

### 3.3 ColumnStore and bitmap invariants

`ColumnStore` 持有 table leaf payload。

不变量：

- 每个 leaf column length 与 current row capacity / row length 策略一致；
- live row 的 required field payload 必须可读；
- optional field 使用 presence bitmap + payload column；
- optional bit `1` 表示 present，bit `0` 表示 absent；
- optional payload、bitmap、`presentCount` 或等价 metadata 必须一致；
- absent value getter 必须返回 typed absent error，不能静默返回 Java primitive default；
- setter 必须设置 presence bit 并写入 payload；
- clear 必须清除 presence bit，后续 value getter 按 absent 处理；
- DTO materialization 时 absent optional materialize 为 `null`。

### 3.4 AccessStructures invariants

`AccessStructures` 是从 `RowSpace` + `ColumnStore` 派生出的访问结构。

不变量：

- secondary index、unique index、order sidecar 不持有 primary identity；
- selector field mutation、insert、delete、row move、replaceAll、clear 后，sidecar 必须 eager maintain 或标记 dirty；
- dirty sidecar 在被 `AccessPath` 用作 source 前必须 rebuild；
- unique index duplicate 必须返回可区分 typed error；
- order sidecar 是 row permutation，不改变 physical column storage order；
- sidecar handle、hash bucket、order array 不进入 public/generated API。

### 3.5 AccessPath invariants

`AccessPath` 只决定 Row Pipeline terminal 开始时的初始 `RowSequence`。

不变量：

- default scan path 遍历当前 packed rows；
- index path 从 maintained secondary/unique sidecar 产生候选 rows；
- order path 从 maintained order sidecar 产生 ordered rows；
- dynamic sorted path 产生本次 terminal 的临时 row permutation；
- `AccessPath` 不改变 storage 本体；
- generated `findByXxx(...)` / `byXxx(...)` 返回同一套 Row Pipeline，不暴露 sidecar。

### 3.6 DTO and buffer invariants

DTO 是 detached materialized copy。

不变量：

- `fetch(key)`、`fetchAt(rowIndex)`、`findFirst()`、`firstOrThrow()`、`fetchAll()` 返回 detached DTO；
- 修改 DTO 不写回 table；
- table 后续 mutation 不改变已返回 DTO；
- Row cursor 只在 callback 调用期间有效，不允许逃逸；
- `RowIndexBuffer` 只对生成时 table epoch 有效；
- `KeyBuffer` 持有 stable materialized key values。

## 4. State machines

### 4.1 TableStore lifecycle

```text
created/open
  -> traversing          // terminal running
  -> mutating            // public mutation running
  -> open
  -> released
```

规则：

- released table 的读写必须返回 table released 类 typed error；
- release 后 active ColumnView 的读取应返回 released view 或 table released 语义，具体错误优先级必须由 runtime-core 固化；
- V1 table 是 synchronous single-owner object，不承诺 cross-thread concurrent read/write safety；
- nested structural mutation 必须被拒绝，而不是形成 undefined behavior。

### 4.2 Public mutation state machine

```text
precheck lifecycle / active view / schema compatibility
  -> prepare capacity and row sequence
  -> apply RowSpace / ColumnStore / KeySpace changes
  -> maintain or dirty AccessStructures
  -> bump epoch and stats
  -> success
```

Expected typed runtime errors 应尽量在 visible state 改变前被发现。对于 duplicate key、missing key、view pinned、table released、schema/runtime mismatch、invalid row index、invalid selector 等 expected error，public mutation 必须保持 table externally unchanged，并且不提升 `storeEpoch`。

对于 arbitrary Java callback exception，V1 不承诺跨已处理 rows 的 rollback。已经通过 mutable cursor 完成的 row update 可以保留，但 table internal invariants 必须保持一致；sidecar 必须被同步维护或标记 dirty；异常应传播给调用方。若 runtime 无法保持内部一致性，必须进入 internal invariant violation error path，不能继续静默服务。

Allocation failure 或 memory limit exceeded 必须返回可区分 typed error。结构性 mutation 应采用 staging、pre-allocation、rollback 或等价机制，避免 public table 暴露半写入状态。

### 4.3 Row Pipeline lifecycle

```text
lazy plan
  -> terminal running
  -> consumed
```

规则：

- intermediate operation 只记录 traversal plan，不扫描 table、不复制 row、不 acquire ColumnView；
- terminal 基于执行时 table current state；
- terminal 开始前固定本次 candidate `RowSequence` 或 traversal plan；
- mutation terminal 内 update selector/filter/order 字段，不允许同一 row 因重新进入 source/filter/sort 而重复执行；
- consumed pipeline 再执行 terminal 必须返回 typed runtime error；
- callback 中不允许对同一 table 调用 structural mutation API；
- callback 可以读取其他 table；修改其他 table 由 application 保证不会形成 mutation cycle。

Terminal traversal freeze 不是 snapshot isolation。它只保证本次 terminal 的 candidate rows 不因同一次 terminal 内的 mutation 被重复纳入或漏掉。

### 4.4 ColumnView lifecycle

```text
not acquired
  -> live(viewEpoch)
  -> released
```

规则：

- acquire 增加 active view count，并记录 `viewEpoch`；
- release/close idempotent；
- release 后读取返回 released_view；
- `viewEpoch != storeEpoch` 返回 stale_view；
- table structural mutation 遇到 active view 返回 view_pinned，除非实现能证明 storage address / length / layout 不变；
- ColumnView 强持有 owner table 或 storage owner，避免 use-after-release 语义；
- ColumnView 不承诺 snapshot isolation。

### 4.5 Sidecar lifecycle

```text
clean
  -> dirty
  -> rebuilding
  -> clean
```

规则：

- insert/delete/replaceAll/clear/row move 可以让 sidecar dirty；
- 修改 selector 字段可以让对应 index/order sidecar dirty；
- terminal 使用 dirty sidecar 前必须 rebuild；
- rebuild 应基于当前 `RowSpace` 和 `ColumnStore`；
- rebuild 后 sidecar 不得包含 deleted row 或 stale slot；
- stats 应能记录 dirty/rebuild 事件或等价 maintenance cost hint。

## 5. Error taxonomy

V1 correctness 要求 runtime errors 至少区分：

| Error | 典型触发 | Correctness expectation |
|---|---|---|
| duplicate key | batch import / insert duplicate key | table unchanged for expected duplicate error |
| missing key | `fetch(key)` / required lookup missing | no generic exception |
| empty required result | `firstOrThrow()` on empty pipeline | no DTO materialization |
| absent optional value | optional value getter on absent field | no primitive default fallback |
| invalid selector | invalid generated/runtime selector | processor/runtime typed diagnostic |
| stale view | ColumnView epoch mismatch | view remains released/invalid for read path |
| view pinned | structural mutation with active view | mutation rejected or delayed before visible state change |
| released view | read after view release | distinct from stale view |
| table released | access after table release | no use-after-release semantics |
| pipeline consumed | terminal called twice | no repeated traversal |
| nested structural mutation | callback calls same-table structural mutation | mutation rejected |
| allocation failure | reserve/addBatch/replaceAll cannot allocate | no half-written public table |
| memory limit exceeded | configured limit exceeded | no hidden partial success |
| internal invariant violation | impossible state detected | fail fast; do not continue silently |

## 6. Differential oracle

Correctness evidence 应使用分层 oracle，而不是只依赖 FJSP E2E。

### 6.1 Reference model

测试可以使用简单 reference model：

- keyed table：`Map<Key, DTO>`；
- dense table：`List<DTO>`；
- index source：对 reference rows 现场 filter；
- unique source：filter 后断言 0/1；
- order source：对 reference rows 现场 sort；
- dynamic sort：对 candidate rows 现场 sort；
- DTO materialization：copy reference DTO。

Reference model 只用于 tests/oracle，不进入 runtime 实现，不改变 V1 不是 `List<DTO>` / `Map<Key, DTO>` hot storage 的架构事实。

### 6.2 Component invariant checker

G3 runtime tests 应提供 invariant checker，至少检查：

- live row count、capacity、valid slot；
- all leaf column length / row length；
- optional bitmap、payload、present count；
- `KeySpace` key set 与 live keyed rows；
- secondary index / unique index candidate set；
- order sidecar permutation；
- table epoch、active view count、released flag；
- runtime stats 的基本单调性或事件记录。

### 6.3 FJSP oracle

FJSP scenario 覆盖真实 vertical slice：

- `Job`、`Operation`、`Material`、`Machine` keyed entity state；
- `ProcessingTime`、`SetupTime` keyed lookup table；
- `MachineCandidate` keyed runtime frontier；
- `ProcessingTime.findByOperation(operationKey)` release-time grouped index source；
- `Machine.byAvailableTime()` order source；
- `MachineCandidate.findByMachine(machineId).update(...)` indicator refresh；
- `MachineCandidate.findByMachine(machineId).sorted(comparator).firstOrThrow()` dynamic dispatch selection；
- `MachineCandidate.findByOperation(operationKey).remove()` frontier cleanup；
- assignment mutation、machine availability mutation 和 material/job ready state 推进；
- DTO export boundary。

FJSP oracle 证明 example path 可观察，不替代 G3 runtime invariant。

## 7. FJSP lookup semantics

FJSP canonical scenario 采用以下业务口径：

- `ProcessingTime` 缺失某个 operation-machine pair，表示该机器不是该 operation 的候选；
- `ProcessingTime.findByOperation(operationKey)` 返回 empty rows，表示当前 released operation 没有可行候选机器，由 solver core 解释；
- `MachineCandidate` row 存在表示该 `(MachineId, OperationKey)` 仍在 runtime frontier 中；row 缺失不等同于 runtime error，可能表示未 release、已分配或业务不可行；
- `fetch(operationMachineKey)` 或 `firstOrThrow()` 用于 required lookup，缺失时必须返回 typed missing / empty required result；
- `SetupTime` 在 canonical FJSP 中是 required setup matrix lookup；除业务规则明确 setup 为 `0` 的首工序/无 last setup family case 外，缺失 row 表示输入或模型不完整；
- runtime 不解释“不可行”或“输入不完整”，只提供 empty result 与 typed missing error。

## 8. Gate mapping

| Gate | Correctness responsibility |
|---|---|
| G1 | annotation/type/key/index/order/optional validation；invalid selector compile failure |
| G2 | normalized model golden；schema hash golden；generated table/batch/row/mutator/ColumnView/grouped source golden |
| G3 | component invariant；cross-component invariant；batch/import/replaceAll；duplicate/missing key；row move；sidecar dirty/rebuild；ColumnView stale/released/view_pinned |
| G4 | Java 8 generated API/package smoke；schema hash/runtime compatibility metadata |
| G5 | FJSP frontier E2E observable path；index/order source；dynamic sort；Row Pipeline update/remove terminal；ColumnView；typed lifecycle errors |
| G6 | release readiness report only引用 G1-G5 正式 evidence，不直接把 scenario review 当 runtime correctness evidence |

## 9. Non-goals

V1 correctness model 不承诺：

- cross-table transaction；
- snapshot isolation；
- thread-safe table；
- rollback for arbitrary user callback exception；
- arbitrary join consistency；
- schema migration correctness；
- Java Stream compatibility；
- production concurrency semantics。
