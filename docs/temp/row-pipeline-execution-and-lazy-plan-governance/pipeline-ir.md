# SOMA Candidate Scan IR 与物理执行设计

类型：Temporary

状态：Stage 1 详细设计冻结；已由 `fd82eba` 实现，待正式固化

Owner：SOMA Java Candidate Scan 语义与执行模型

事实范围：Candidate Scan 的语义 IR、terminal-time binding、physical execution、compact representation、failure/stats/resource 协议

非事实范围：正式 runtime protocol Owner、正式性能声明与 release readiness

输入：[Access Model](access-model.md)、[Stage 1 决策](stage-1-decisions.md)、[Stage 1 Evidence](stage-1-evidence.md)

最后审查日期：2026-07-23

## 1. 设计定位

Candidate Scan IR 只实现 Access Model 中的：

```text
CandidateSource Stage* CandidateTerminal
```

PointAccess、KeyTraversal、ColumnTraversal、ColumnView、BulkAccess 和 owner navigation 不进入此 IR。它们可以复用 lifecycle/error/stats 原则，但不共享 generic plan 或 executor。

IR 的职责是把用户 DSL 语义与物理执行分开，同时让 generator 将大部分 Bound/Physical 层编译为 primitive locals 和 straight-line branches。IR 不是 public query object、serialization format、metadata interpreter 或 application SPI。

## 2. 层次责任

```text
Generated Table / Scan facade
  -> Semantic Scan Plan
       source + ordered stages + generation/lifecycle
  -> Evaluation Request
       semantic plan + terminal
  -> Bound Evaluation
       current source/cardinality/traits/resource
  -> Physical Execution
       streaming segments/barriers/specialized terminal
  -> Generated typed executor + runtime primitives
```

| 层 | 拥有 | 不拥有 |
|---|---|---|
| Generated facade | schema-specific methods、callback types、public lifecycle | physical algorithm自由度 |
| Semantic Plan | normalized source、ordered stages、generation | current group/size/epoch |
| Evaluation Request | plan + terminal profile | current resource result |
| Bound Evaluation | terminal-time facts、traits、cardinality、preflight | public naming |
| Physical Execution | segment、barrier、short-circuit、scratch shape | 新 API 语义 |
| Runtime primitives | IndexBuffer、columns、state、exact/locator、failure/stats | schema metadata interpretation |

这些层是设计责任，不要求各自创建 Java object。

## 3. Public facade 投影

### 3.1 Packed source

Table 本身是 Packed CandidateSource：

```text
Table.stage(...)    -> <Type>Scan
Table.terminal(...) -> direct generated executor
```

Zero-stage packed terminal 不创建空 Scan/plan。Table 的首个 stage 完成参数验证后，直接创建包含 Packed source 与 stage 0 的 plan。

### 3.2 Exact source

```text
Table.scanBySelector(values...) -> <Type>Scan
```

Source construction：

- 立即验证 null、finite access value 和其他纯参数规则；
- 立即 flatten/canonicalize selector leaf，并可预计算稳定 hash；
- 不读取 current group、size、row link 或 epoch；
- 不创建匿名 `Source` object。

Terminal 才在 current exact structure 上执行 hash probe、full equality 和 group binding。这样既保持 current facts 的惰性，又不延迟基本 API 参数错误。

### 3.3 Handle lifecycle

`<Type>Scan` 是 public final one-shot handle：

```text
handle = (plan reference, expected generation)
```

- 每条 source 创建独立 plan；
- successful intermediate 返回下一 generation 的新 lightweight handle；
- 旧 handle立即 stale，使用时返回 `pipeline_consumed`；
- validation/allocation failure 在 publish 前发生，旧 handle仍 active；
- terminal begin 后无论 success、empty、resource、callback 或 fatal error，plan consumed；
- 不允许 branch/reuse、同 aggregate reentrancy 或 Table-global/ThreadLocal plan。

## 4. Semantic Scan Plan

概念形态：

```text
ScanPlan {
  SourceKind sourceKind
  normalized source leaves/hash
  ordered StageStorage
  int generation
  Lifecycle state
  static diagnostic identity
}
```

### 4.1 Source descriptor

| Kind | construction-time storage | terminal-time binding | Trait |
|---|---|---|---|
| Packed | table reference | current `[0,size)` | exact size/current sequence |
| ExactGroup | selector leaves + hash | current group/links/count | exact size/exact group |
| ExactUnique bridge | selector leaves + hash | current zero/one row | max one |
| OwnedChild | 已取得的 child Table 作为上述 source 的 table binding | child current facts | child local |

每个 selector 由 generated private source-plan subtype保存自己的 primitive/reference leaf；不使用 `Object[]` selector carrier。Hash 命中后始终执行完整 value equality。

Source 参数若包含 String 等 reference leaf，terminal `finally` 清除 strong reference；primitive leaf无需清理。Plan 不缓存跨 mutation group或Index。

### 4.2 Stage descriptor

| Kind | payload | cardinality | sequence | barrier |
|---|---|---|---|---|
| Filter | typed Predicate reference | exact → upper bound | stable subsequence | 否 |
| Skip | non-negative long | 收紧 | 丢弃前缀 | 否 |
| Limit | non-negative long | 收紧上界 | 保留前缀 | 否，可短路 |
| Sort | typed Comparator reference | 不变 | stable comparator order | 是 |

Descriptor 保存用户声明顺序和 callback attribution。Fusion 不允许重排、合并 Predicate 或删除 repeated Sort。

### 4.3 Compact stage storage

Inline capacity 固定为 3：

```text
StageStorage {
  byte kind0, kind1, kind2
  Object callback0, callback1, callback2
  long argument0, argument1, argument2
  int stageCount

  byte[] overflowKinds
  Object[] overflowCallbacks
  long[] overflowArguments
}
```

规则：

- Filter/Sort 使用 callback slot；Skip/Limit 使用 long argument；未使用 slot 为 null/0；
- 不为 inline stage 创建数组；
- 第 4 个 stage 才创建 overflow，capacity 以 bounded checked growth扩展；
- overflow 从 stage 0 起连续复制到三组数组，避免每次执行分裂两套循环；也可以保留 inline + tail，只要 benchmark/code-size更优且语义相同；
- primitive count 不装箱；callback cast由 generated kind branch静态限定；
- 不创建独立 `seen[]`；Skip/Limit evaluation 使用 primitive locals或 consumed plan 中的 remaining argument；
- terminal结束时清空 callback arrays/fields。

“从 stage 0 连续复制”与“inline + overflow tail”是等语义 micro-layout 选择，Stage 2 可以用 code-size/allocation evidence 二选一，不得改变 capacity 3 和三组 storage 上限。

## 5. Intermediate append 协议

每次 stage append：

```text
validate callback/count
  -> verify handle generation and active plan
  -> compute next generation/capacity
  -> allocate prospective handle/storage/overflow
  -> write stage payload
  -> publish stageCount and generation last
  -> return next handle
```

保证：

- null callback、negative count、consumed handle 在任何 plan mutation 前失败；
- OOME/checked growth failure 发生在 publish 前时，旧 handle仍可执行；
- publish 后旧 generation失败，新 handle拥有唯一 terminal权；
- single-owner同步语义下不增加锁/CAS；concurrent use本身是非法 caller behavior；
- plan 不引用 previous handle，不形成 linked per-stage object graph。

## 6. Evaluation Request

Terminal descriptor：

| Family | Terminal | Output cardinality | 关键要求 |
|---|---|---:|---|
| Probe | count | scalar | final cardinality |
| Probe | anyMatch/noneMatch | scalar | terminal predicate short-circuit |
| Borrow | forEach | 0 | callback sequence |
| Current Index | findIndex/requireIndex | 0..1/1 | first/best-one，不复制 snapshot |
| Snapshot | indexSnapshot | 0..M | detached Index copy |
| Materialize | findFirst/firstOrThrow | 0..1/1 | budgeted detached carrier |
| Materialize | fetchAll | 0..M | budgeted detached list/graph |
| Mutate | update | result | freeze + staged publish |
| Mutate | remove | result | freeze + swap-remove publish |

Terminal profile同时声明：ordered consumption、streaming/full-candidate、maximum needed、materialization budget、mutation preflight、empty/failure和stats profile。

## 7. Terminal-time binding

固定顺序：

1. 验证 handle generation，立即把 plan 标为 terminal-consumed；
2. 验证 table/aggregate active、ownership、compatibility和non-reentrancy；
3. begin operation，绑定 current source；
4. 得到 exact size/upper bound/max-one/current sequence traits；
5. 按 stage传播 cardinality和barrier信息；
6. 完成 candidate/sort/update/materialization resource preflight；
7. 选择 physical shape并执行；
8. success/failure发布logical stats；
9. `finally` reset table scratch并清除plan reference payload。

Source/plan construction不保留 current epoch假象。Snapshot currentness与ColumnView pin由各自公开契约管理。

### 7.1 Traits/cardinality

```text
exactSizeKnown + exactSize
upperBoundKnown + upperBound
maxOne
currentSequence
exactGroup
childLocal
```

- Sort不改变数量；
- Filter清除exact、保留upper bound；
- Skip/Limit以checked arithmetic更新exact/upper bound；
- terminal maximum用于short-circuit/arg-min，但不回写public语义；
- candidate count超过runtime `int` scratch边界时在callback/mutation前fail closed。

## 8. Physical execution

```text
source bind
  -> fused pre-barrier segment
  -> [freeze + stable sort barrier]
  -> fused post-barrier segment
  -> ... repeated barriers if declared
  -> terminal executor
```

### 8.1 Streaming shape

无 Sort 且 terminal 不要求完整 freeze时：

- Packed按 current Index递增；Exact按current group link；
- Filter/Skip/Limit按声明顺序在同一 generated loop执行；
- count/match/forEach/findIndex/single materialize可cooperative stop；
- 不为每个 candidate创建对象或iterator；Cursor在operation内复用；
- logical stats按reference evaluator计数。

### 8.2 Barrier shape

Sort 前 candidate写入 table-local `IndexBuffer`，仅保存current Index：

- candidate buffer按terminal/resource upper bound准入；
- stable merge sort使用table-local sort scratch；
- sort后 Filter/Skip/Limit只处理已排序候选；
- repeated Sort逐个执行，保留stable composition；
- stage永不把exact group扩展回全表。

### 8.3 stable arg-min

当且仅当：

- terminal maximum为1；
- 只有一个需建立first的Sort；
- Sort后只存在不改变first语义的Limit；
- mutation/snapshot/bulk materialization不要求完整候选；

可用stable arg-min：单次线性遍历，comparator `< 0` 才替换best，compare为0保留upstream first。`findIndex/requireIndex` 是主要hot terminal；single materialization在确定Index后再物化。

不实现k>1 top-k。其他shape回退full stable sort。

### 8.4 exact max-one

ExactUnique bridge在bind后最多一项：

- Sort不调用comparator；
- Skip/Limit/filter仍按声明语义；
- Probe/Index/materialize/update/remove使用single candidate path；
- canonical point API不创建Scan，直接使用同一exact structure。

## 9. Terminal specialization

| Terminal | Fast path | 必须保持 |
|---|---|---|
| packed source count | direct `size()` logical shortcut | lifecycle、stats |
| exact source count | group cardinality shortcut | full equality、logical stats |
| any/none | streaming stop | predicate order/failure |
| forEach | reusable Cursor | callback escape/reentrancy |
| find/require Index | streaming first或arg-min | current Index contract |
| indexSnapshot | empty shared/single inline/multi copied array | detached copy/currentness |
| findFirst/firstOrThrow | Index first后budget materialize | empty/failure/budget |
| fetchAll | final sequence后budget materialize | detached order/graph |
| update | freeze、load scratch、callbacks、validate、publish | all-or-nothing |
| remove | freeze、preflight、swap-remove/relocate | exactly-once target set |

Sort不能仅因count结果与顺序无关而完全删除；Comparator failure仍属于声明过的semantic Sort。Pure Skip/Limit normalization不跳过source/lifecycle validation。

## 10. Callback 与 failure

### 10.1 Cursor

- read Cursor在callback前绑定current Index，callback后失效；
- UpdateCursor读取原/当前 staged值，setter只写update scratch；
- comparator使用两个独立read Cursor；
- Cursor不提供advance、Index escape或table mutation能力；
- escape、nested access和released table返回typed failure。

### 10.2 调用规则

- Filter predicate对到达stage的candidate至多一次；
- terminal predicate按final sequence至short-circuit；
- Consumer/Updater每个final candidate恰好一次；
- Comparator pair/count/order算法相关，但stable结果与first-on-equal固定；
- callback不能依赖副作用次数、嵌套访问aggregate或修改来源Table；
- 实际callback抛出的SomaRuntimeException原样传播，普通RuntimeException包装并保留cause，fatal Error不伪装为recoverable。

### 10.3 mutation failure

Update/remove在任何可能发布的动作前完成source binding、candidate freeze、resource/unique/exact/ownership preflight。Callback failure、unique conflict或allocation rejection均保持旧stable table facts；application外部副作用不由SOMA回滚。

## 11. Stats 与 diagnostics

- `scanned`/`matched`采用[Stage 1 决策](stage-1-decisions.md#51-logical-stats)的semantic reference定义；
- physical shortcut可以少做工作，但不能伪造logical result；
- comparison count、allocation和physical loops进入benchmark artifact，不扩展TableStats；
- Scan operation label使用预生成`scan.*` literal；source path静态标识Packed或selector name；
- terminal hot path不拼接String或调用payload `toString()`；
- consumed plan清除callback、reference selector和table strong reference，但保留bounded static diagnostic identity以支持重复使用失败。

## 12. Key/Column 邻接实现

KeyTraversal与ColumnTraversal不进入ScanPlan：

```text
generated traversal handle
  -> one terminal
  -> terminal-time current table facts
  -> typed straight-line loop/materializer
  -> consumed
```

ColumnTraversal/ColumnView的operation、callback、presence和getter label由generator传入String literal。删除`AbstractColumnPipeline`构造时拼接和`ColumnViewOperations.Cache`动态字段cache；typed factory不解析metadata。

## 13. Generated code structure

每张table按需生成：

- public `<Type>Scan` final handle；
- public `<Type>Cursor` / `<Type>UpdateCursor` interfaces；
- Scan nested/private Plan、StageStorage、Packed/selector source-plan subtype；
- typed executor methods仍落在Table/Scan的generated code；
- selector source subtype只保存该selector leaf，不生成generic parameter array；
- 无selector table不生成selector subtype；dense/keyed不因统一而多生成无用primary能力。

Source specialization数量大致替代当前anonymous Source classes；不能无界复制terminal executor。Shared generated helper只抽取等语义codegen模板，不引入runtime reflection或virtual Sink graph。

Codegen必须验证：`*Scan/*Cursor/*UpdateCursor/*KeyTraversal`、selector point/scan methods、nested names、constant-pool、method bytecode、parameter slots和source size collision/admission。

## 14. Java Stream 借鉴边界

| Java Stream 思想 | SOMA 对应 | SOMA 差异 |
|---|---|---|
| lazy intermediate | Semantic Scan Plan | current Table source在terminal绑定 |
| source/stage/terminal | Candidate algebra | Pipeline不是全部Access Model |
| one-shot | generation handle | typed lifecycle error、single-owner |
| characteristics | traits/cardinality | exact group/max-one/child-local |
| stateless/stateful | fused segment/Sort barrier | primitive IndexBuffer |
| cancellation | cooperative primitive stop | stats/failure统一发布 |
| terminal specialization | Index/snapshot/materialize/update/remove executor | schema-specific generated code |
| primitive specialization | typed columns/Cursor | 无generic Stream<T> storage |

不复制AbstractPipeline链、Sink graph、Spliterator、parallel、Collector、Node、generic map/reduce或per-stage object。

## 15. Stage 2 验证义务

- descriptor oracle：source/stage/terminal与Access Pattern映射；
- lifecycle：old-handle alias、validation/allocation failure、terminal failure、Traversal one-shot；
- source：Packed、group、unique、collision/full equality、child；
- sequence：filter/skip/limit、stable repeated sort、arg-min first-on-equal；
- terminal：Probe、Index、snapshot、materialize、update/remove；
- failure：callback、reentrant、resource、unique、released/stale；
- randomized differential：reference evaluator vs optimized executor；
- allocation：zero-stage Packed、exact source、stage 1/2/3/4/5/16、Index/snapshot/materialize分离；
- memory：IndexBuffer/sort/update retained current/high-water；
- codegen：source/class bytes、nested class count、javac、constant-pool；
- scenario：FJSP/VRP/Simulation/Game checksum与API可读性；
- compatibility：protocol v4 mismatch、schema hash不变、external consumer clean migration。

## 16. Stage 1 结论

Candidate Scan 的semantic、binding、physical、representation和failure/stats责任已经闭合。
Stage 2 已在不重新发明产品语义的前提下实现，并仅在既定边界内以 evidence 调整
micro-layout。

本文件仍是 Temporary 设计，不替代正式 Owner；实现与 allocation 事实由
[Stage 2 Evidence](stage-2-evidence.md)承担。
