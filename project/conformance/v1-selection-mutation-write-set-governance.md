# SOMA V1 Selection Mutation Write-Set 与原地提交治理

类型：Conformance / Governance Closure

状态：`PASS / OWNER_PROMOTED / TEMPORARY_RETIRED`

基线：`4001777`

日期：2026-08-12

## 1. 结论

本专题在不改变public/generated API、artifact topology、dependency、Table-local all-or-nothing、
zero-publication、canonical order与compression transparency的前提下，关闭了普通Selection
mutation按touched Chunk复制全部leaf array的坏味道。

- PLAIN non-indexed Selection update使用columnar `SelectionWriteSet`，只暂存实际changed leaves；
- PLAIN Selection remove使用frozen dense move plan，并从`final locator -> old source locator`投影
  构造replacement Key/Index；
- final commit只执行prepared primitive/reference writes、row moves、reference clear和descriptor publish；
- indexed update与encoded/overlay update/remove保留representation-aware candidate path；
- logical no-op不复制Chunk、不发布version；
- 未新增public surface、dependency、module、rollback journal、MVCC、delta state或场景特供路径。

稳定合同已经晋升到Storage、Execution和Core abstractions；本记录拥有实施与证据，不能覆盖这些
正式Design Owner。

## 2. 实施机制

### 2.1 Update

Editor callback读取bound root并写复用staging cursor。每行callback结束后只比较non-Key leaf，按需
创建typed value column与changed bitmap。全部callback完成后：

```text
PLAIN + unchanged Index
    -> construct committed header
        -> fault boundary
            -> apply changed leaves in place
                -> publish header/version

indexed or encoded change
    -> materialize touched candidate from write set
        -> finish representation/rebuild required sidecar
            -> candidate root swap
```

### 2.2 Remove

Frozen Selection首先形成严格确定的`hole <- tail survivor`计划。PLAIN路径建立一个长度为new size
的primitive source-locator映射，Key/Index在old authoritative payload上按该映射构造最终locator
membership；之后final commit只移动行、清理trailing reference并发布replacement sidecar/header。

该映射只是operation-scoped preparation，不是Table state、reverse Index或第二套membership truth。
Encoded/overlay路径继续构造final candidate并从candidate payload重建sidecar。

## 3. Correctness 与 failure evidence

`GeneratedTableTest`新增并重放以下主路径：

- PLAIN update/remove发布一次generation且directory/Chunk identity保持，直接证明没有candidate copy；
- update no-op零publication；
- callback failure、resource rejection及`BEFORE_KEY_REBUILD`、`BEFORE_SIDECAR_ACCOUNTING`、
  `BEFORE_CANDIDATE_PUBLISH`、`BEFORE_FINAL_COMMIT` fault全部在payload write前退出；
- update引用/primitive leaf、remove deterministic dense compaction、tail reference clear、Key与Index
  canonical membership/accounting；
- AUTO encoded update/remove继续走candidate fallback；
- 既有randomized point mutation、query/reference、compression与managed-memory suite未回归。

定向runtime结果：`64 tests / 0 failure / 0 error`。完整仓库结果见第5节。

## 4. Fixed-host A/B

主机/JDK、1M rows、5个fresh JVM、`-Xms2g -Xmx8g -XX:+UseParallelGC`、6 GiB managed budget、
`soma-off`均一致。基线从独立detached worktree `4001777`重放；candidate来自本专题最终worktree。

| 1M frontier mutation | Baseline | Final | Delta |
|---|---:|---:|---:|
| Selection update median | 32.303 ms | 31.108 ms | -3.7% |
| Selection update allocated | 80,079,760 B | 6,061,520 B | -92.4% |
| Selection remove median | 166.527 ms | 168.381 ms | +1.1% |
| Selection remove allocated | 225,995,344 B | 155,804,936 B | -31.1% |

两侧fingerprint相同；+1.1%属于本轮fixed-host噪声范围，不构成可重复性能退化。结构性分配下降直接
证明touched-Chunk全leaf candidate copy已退出正常PLAIN路径。AUTO仍可能因encoded representation
进入candidate，其时间不应被误写成PLAIN write-set收益。

同参数Grassing headless应用（10K cells、1K initial grassers、100 ticks、3 JVM、OFF）结果：

| Metric | Baseline | Final | Delta |
|---|---:|---:|---:|
| Kernel | 395.592 ms | 365.206 ms | -7.7% |
| Metabolism | 85.992 ms | 69.115 ms | -19.6% |
| Grazing | 96.195 ms | 89.207 ms | -7.3% |

两侧最终fingerprint均为`1157302845660161904`。

## 5. Qualification

- `mvn -pl soma-runtime -Dtest=GeneratedTableTest test`：`PASS`；
- 1M `frontier-mutation` OFF/AUTO correctness与fingerprint：`PASS`；
- Grassing headless correctness/fingerprint：`PASS`；
- `./scripts/check.sh`：`PASS`；
- `./scripts/qualify.sh`：`PASS`，包含全量测试、benchmark、三个Example、local package、
  SBOM与provenance；
- `git diff --check`、正式链接与Temporary replacement closure：`PASS`。

本机benchmark只证明本次fixed-host相对变化，不是跨硬件SLA或正式release授权。

## 6. Boundary

- indexed Selection update当前仍使用candidate+sidecar rebuild；未来只有独立profile证明值得，才可
  准入多记录incremental prepared sidecar，不预建generic batch abstraction；
- Selection remove已消除Column candidate copy，但replacement Key/Index仍是线性构造；这符合本次
  large structural mutation边界，不宣称变为point-like复杂度；
- temporary admission对opaque callback仍允许conservative upper bound；只有全PLAIN且没有mutable
  secondary Index的update可以按write-set worst-case替代整root candidate预算；
- 没有授权publication、GitHub Release/Package、签名或正式release声明。
