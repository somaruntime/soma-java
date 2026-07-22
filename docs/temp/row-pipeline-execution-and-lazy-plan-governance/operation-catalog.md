# SOMA Candidate Scan 操作目录与优化合法性

类型：Temporary

状态：Stage 1 冻结

Owner：SOMA Java Candidate Scan 语义目录

事实范围：current/target source、stage、terminal descriptor与optimizer legality

非事实范围：当前已经实施的目标API、具体benchmark收益与正式Design

输入：[Access Model](access-model.md)、[Stage 1 决策](stage-1-decisions.md)、[Pipeline IR](pipeline-ir.md)

最后审查日期：2026-07-22

## 1. Descriptor 最小字段

每个Candidate operation必须可描述：

```text
source kind + normalized parameters
ordered stages
terminal kind
cardinality effect/requirement
sequence effect/requirement
callback kind/attribution
short-circuit/barrier
result/detach/mutation boundary
empty/failure/resource/stats/lifecycle
```

这些是设计责任，不要求runtime为每项创建对象。

## 2. Source catalog

| ID | Access Pattern | Current API | Target API | Cardinality/sequence |
|---|---|---|---|---|
| `SRC-PACKED` | `AP-01` | `rows()` + Table forwarding | Table本身 | terminal-time `[0,size)`，physical sequence |
| `SRC-GROUP` | `AP-05` | `findByX -> *Rows` | `scanByX -> *Scan` | `0..M` exact group current sequence |
| `SRC-UNIQUE` | `AP-04` bridge | `findByX -> *Rows` | `scanByX -> *Scan` | `0..1` current Index |
| `SRC-CHILD` | `AP-06` | child Table facade | 保持 | 先owner bind，再使用child Packed/Exact source |

PrimaryKey、SecondaryUnique point和current Index direct access不包装为synthetic source。`@SomaUnique`的canonical point family登记在[API覆盖矩阵](access-api-coverage.md)，Scan只作显式bridge。

## 3. Stage catalog

| ID | API | Cardinality | Sequence | Callback | Physical eligibility |
|---|---|---|---|---|---|
| `STG-FILTER` | filter | exact → upper bound | stable subsequence | read Cursor | same-loop fusion |
| `STG-SKIP` | skip(n) | drop prefix | remaining sequence | none | remaining counter |
| `STG-LIMIT` | limit(n) | upper bound n | prefix | none | cooperative stop |
| `STG-SORT` | sorted(c) | unchanged | stable comparator order | two read Cursors | barrier/full sort/arg-min |

Intermediate参数在调用时验证；success后old handle consumed，failure前old handle保持active。

## 4. Terminal catalog

| ID | Target API | Result/empty | Consumption | Allocation/mutation boundary |
|---|---|---|---|---|
| `TRM-COUNT` | count | long/0 | final cardinality | no detached result |
| `TRM-ANY` | anyMatch | false on empty | first true stop | terminal predicate Cursor |
| `TRM-NONE` | noneMatch | true on empty | first true stop | terminal predicate Cursor |
| `TRM-VISIT` | forEach | void | ordered final sequence | callback-scoped Cursor |
| `TRM-FIND-INDEX` | findIndex | -1 on empty | first/best-one | current Index scalar |
| `TRM-REQUIRE-INDEX` | requireIndex | typed missing | first/best-one | current Index scalar |
| `TRM-SNAPSHOT` | indexSnapshot | empty/single/multi | complete final sequence | detached Index copy |
| `TRM-FIND` | findFirst([budget]) | Optional carrier | first/best-one | budgeted single materialization |
| `TRM-REQUIRE` | firstOrThrow([budget]) | carrier/typed missing | first/best-one | budgeted single materialization |
| `TRM-ALL` | fetchAll([budget]) | detached List | complete final sequence | budgeted graph materialization |
| `TRM-UPDATE` | update | UpdateResult | freeze all final candidates | staged atomic publish |
| `TRM-REMOVE` | remove | RemoveResult | freeze all final candidates | preflight + swap-remove |

Table直接提供同一terminal给Packed source；Scan提供给已有stage/exact source。它们是不同source binding，不是两套terminal语义。

## 5. Non-Scan access catalog

| Pattern | Target shape | Scan边界 |
|---|---|---|
| current Index point | `fetchAt/mutateAt`、ColumnView | 不建plan |
| PrimaryKey point | contains/findIndex/requireIndex/find/fetch/mutate/delete | 不建plan |
| SecondaryUnique point | containsBy/findIndexBy/requireIndexBy/findBy/fetchBy/mutateBy/deleteBy | canonical unique path |
| Key access | `*KeyTraversal` | terminal-only、one-shot |
| Column traversal | typed `*ColumnTraversal` | terminal-only、one-shot |
| sparse gather | IndexSnapshot + ColumnView | explicit caller read batch |
| Batch/bulk | addBatch/replaceAll/clear | independent stage/publish |
| child replacement | replaceChildren | owner point + detached Batch |
| full aggregate materialization | Table materialize | ownership graph/budget boundary |

## 6. Optimizer legality matrix

`Accepted`表示Stage 2可以实施；`Current`表示已有且必须迁移；`Rejected`表示本专题内禁止。

| Rewrite/specialization | 最小条件 | 必须保持 | 状态 |
|---|---|---|---|
| Table zero-stage direct terminal | Packed source、无stage | lifecycle/logical stats/terminal semantics | Accepted |
| exact direct binding | maintained exact + full equality | group sequence/failure/stats | Current |
| Filter/Skip/Limit same-loop | declared order | predicate/failure/sequence | Current |
| no-sort streaming | terminal无需freeze | short-circuit/stats | Current |
| exact empty/max-one path | bind traits可信 | validation/lifecycle | Accepted |
| source-only count metadata | 无callback/stage | logical scanned/matched | Accepted |
| adjacent pure Skip/Limit normalize | checked arithmetic | final sequence/logical stats | Accepted |
| stable full sort | barrier candidates frozen | comparator attribution/tie | Current |
| stable arg-min k=1 | singleSort + compatible tail + max1 terminal | first-on-equal/failure/stats | Current/Accepted for Index terminal |
| k>1 top-k | any | — | Rejected |
| eliminate Sort before count | would suppress all comparator calls | callback failure | Rejected |
| reorder/combine Filter | any | declared callback order | Rejected |
| delete/reorder repeated Sort | any | stable composition | Rejected |
| snapshot zero-copy IndexBuffer | any | detached ownership | Rejected |
| streaming live update/remove | any | candidate freeze/atomicity | Rejected |
| cache group/Index across terminal | any | currentness | Rejected |
| per-stage node/Sink graph | any | allocation/typed executor | Rejected |
| Table-global/ThreadLocal plan | any | independent chain/lifecycle | Rejected |

## 7. Reference execution

Oracle reference model：

```text
bind source into logical candidate sequence
for stage in declaration order:
  Filter -> stable select
  Skip   -> remove prefix
  Limit  -> keep prefix
  Sort   -> stable sort
apply terminal
```

Reference可以使用detached Java structures，仅用于testkit differential；它不能进入production runtime。Optimized executor必须在result、sequence、callback约束、failure、stats和mutation事实上等价。

## 8. Callback/failure matrix

| Operation | Invocation guarantee | Failure |
|---|---|---|
| Filter | 到达stage的candidate至多一次，声明顺序 | `scan.filter.predicate` |
| any/none | final sequence至short-circuit | terminal predicate attribution |
| forEach | 每个final candidate一次 | consumer attribution；外部副作用不回滚 |
| Comparator | algorithm-dependent pair/count；stable result | comparator attribution；不可完全elide Sort |
| Updater | frozen final candidate每项一次 | scratch丢弃，table不partial publish |
| Remove | 无user callback | preflight failure保持旧table |

## 9. Stats profile

| Terminal | scanned | matched | changed |
|---|---|---|---|
| count | reference source pull | final count | 0 |
| any/none | 至short-circuit source pull | 到达terminal并测试数 | 0 |
| forEach/materialize/snapshot | reference source pull | final consumed数 | 0 |
| find/require Index/materialize | 至first所需source pull；Sort时为完整barrier input | 0/1 | 0 |
| update | selection source pull | frozen candidate数 | actual committed changes |
| remove | selection source pull | frozen candidate数 | removed count |

Metadata shortcut写入reference logical counts，physical work只进入benchmark。

## 10. Stage 1 结论

Catalog已经从“把所有shape称为Pipeline”收敛为Candidate Scan目录，并为Point、Traversal、View、Bulk和Ownership保留独立路径。所有optimizer项都有Accepted/Current/Rejected结论，没有实施时再临时发明的Pending项。
